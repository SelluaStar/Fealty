package com.selluastar.fealty.mail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.event.TierChangedEvent;
import com.selluastar.fealty.api.event.WantedLevelEvent;
import com.selluastar.fealty.block.MailboxBlock;
import com.selluastar.fealty.block.MailboxBlockEntity;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.item.LetterInfo;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.MailStatusPayload;
import com.selluastar.fealty.network.OpenMailboxPayload;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModDataComponents;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.VillagerInteractions;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.SitePlanner;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The post. Players write letters at any mailbox, to a village or to another player, with up to three parcels;
 * letters take a while to arrive, depending on distance. Villages answer letters and gifts, thank those they come
 * to trust, warn those they come to hate, and post notices of bounties. Letters wait at any mailbox until read.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class MailService {
    public static final int MAX_SUBJECT = 40;
    public static final int MAX_BODY = 400;
    private static final int INBOX_LIMIT = 60;
    private static final Map<ResourceLocation, Long> MAILBOX_CHECKED = new HashMap<>();
    /** Villagers holding a letter they were just handed, and when they put it away. */
    private static final Map<UUID, HeldLetter> HELD_LETTERS = new HashMap<>();

    private record HeldLetter(ResourceKey<Level> dimension, long until) {
    }

    private MailService() {
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---- Posting letters ----

    /** Put a letter in a player's inbox, arriving after {@code delay} ticks. */
    public static Letter post(MinecraftServer server, UUID recipient, Letter.Kind kind, Component from, @Nullable UUID fromPlayer,
                              @Nullable ResourceLocation fromVillage, Component subject, Component body, List<ItemStack> items, long delay) {
        long now = now(server);
        Letter letter = new Letter(UUID.randomUUID(), kind, from, fromPlayer, fromVillage, recipient, null, subject, body, items, now,
                now + Math.max(0, delay));
        MailData data = MailData.get(server);
        List<Letter> inbox = data.inbox(recipient);
        inbox.add(letter);
        // Make room by dropping the oldest letters that have been read and emptied.
        Iterator<Letter> oldest = inbox.iterator();
        while (inbox.size() > INBOX_LIMIT && oldest.hasNext()) {
            Letter old = oldest.next();
            if (old.isRead() && old.items().isEmpty()) {
                oldest.remove();
            }
        }
        data.setDirty();
        if (delay <= 0) {
            ServerPlayer online = server.getPlayerList().getPlayer(recipient);
            if (online != null) {
                announce(online, letter);
            }
        }
        return letter;
    }

    /** A letter from a village to a player. Subject and text come from {@code fealty.mail.<key>.subject/body}. */
    public static void fromVillage(MinecraftServer server, UUID player, VillageRecord village, String key, List<ItemStack> items,
                                   long delay, Object... args) {
        Component from = village.hasElder() && !village.elder().name().isEmpty() && !village.isBroken()
                ? Component.translatable("fealty.mail.from.elder", village.elder().name(), village.name())
                : Component.translatable("fealty.mail.from.village", village.name());
        Object[] bodyArgs = new Object[args.length + 1];
        bodyArgs[0] = village.name();
        System.arraycopy(args, 0, bodyArgs, 1, args.length);
        post(server, player, Letter.Kind.VILLAGE, from, null, village.id(), Component.translatable("fealty.mail." + key + ".subject", village.name()),
                Component.translatable("fealty.mail." + key + ".body", bodyArgs), items, delay);
    }

    /** How long a letter takes over this distance (ticks). */
    public static long travelTime(double distance) {
        long seconds = FealtyConfig.MAIL_BASE_DELAY.get() + Math.round(distance / 100.0 * FealtyConfig.MAIL_DELAY_PER_100.get());
        return Math.min(seconds, FealtyConfig.MAIL_MAX_DELAY.get()) * 20L;
    }

    /** Who a letter written at a mailbox can go to. */
    public sealed interface Recipient permits ToVillage, ToPlayer {
    }

    public record ToVillage(VillageRecord village) implements Recipient {
    }

    public record ToPlayer(GameProfile profile) implements Recipient {
    }

    /** Work out who a player means: a village they know, or a player by name. */
    public static Optional<Recipient> recipient(ServerPlayer sender, String type, String value) {
        if ("village".equals(type)) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            Optional<VillageRecord> village = id == null ? Optional.empty() : FealtyWorldData.get(sender.server).village(id);
            if (village.isPresent() && RepManager.data(sender).rep().containsKey(id)) {
                return Optional.of(new ToVillage(village.get()));
            }
            return Optional.empty();
        }
        String name = value.trim();
        if (name.isEmpty() || name.equalsIgnoreCase(sender.getGameProfile().getName())) {
            return Optional.empty();
        }
        ServerPlayer online = sender.server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(new ToPlayer(online.getGameProfile()));
        }
        Optional<GameProfile> profile = sender.server.getProfileCache() == null ? Optional.empty() : sender.server.getProfileCache().get(name);
        return profile.filter(p -> FealtyWorldData.get(sender.server).existingPlayer(p.getId()).isPresent()).map(ToPlayer::new);
    }

    /**
     * Send a letter written at a mailbox. Postage is a sheet of paper, and an emerald for each parcel.
     *
     * @return a problem to show the player, or empty if the letter went out
     */
    public static Optional<Component> send(ServerPlayer sender, BlockPos mailbox, Recipient recipient, String subject, String body,
                                           List<ItemStack> parcels) {
        subject = subject.length() > MAX_SUBJECT ? subject.substring(0, MAX_SUBJECT) : subject;
        body = body.length() > MAX_BODY ? body.substring(0, MAX_BODY) : body;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : parcels) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (subject.isBlank() && body.isBlank() && items.isEmpty()) {
            return Optional.of(Component.translatable("fealty.mail.problem.empty"));
        }
        Inventory inventory = sender.getInventory();
        if (!sender.isCreative()) {
            if (inventory.countItem(Items.PAPER) < 1) {
                return Optional.of(Component.translatable("fealty.mail.problem.paper"));
            }
            if (inventory.countItem(Items.EMERALD) < items.size()) {
                return Optional.of(Component.translatable("fealty.mail.problem.postage", items.size()));
            }
            take(inventory, Items.PAPER, 1);
            take(inventory, Items.EMERALD, items.size());
        }
        MinecraftServer server = sender.server;
        Component from = sender.getDisplayName();
        Component subjectText = Component.literal(subject.isBlank() ? "..." : subject);
        Component bodyText = Component.literal(body);
        if (recipient instanceof ToVillage(VillageRecord village)) {
            double distance = village.dimension().equals(sender.level().dimension())
                    ? Math.sqrt(village.center().distSqr(mailbox)) : 2000;
            long now = now(server);
            Letter letter = new Letter(UUID.randomUUID(), Letter.Kind.PLAYER, from, sender.getUUID(), null, null, village.id(),
                    subjectText, bodyText, items, now, now + travelTime(distance));
            MailData data = MailData.get(server);
            data.toVillages().add(letter);
            data.setDirty();
        } else if (recipient instanceof ToPlayer(GameProfile profile)) {
            ServerPlayer online = server.getPlayerList().getPlayer(profile.getId());
            double distance = online != null && online.level().dimension().equals(sender.level().dimension())
                    ? Math.sqrt(online.blockPosition().distSqr(mailbox)) : 1000;
            post(server, profile.getId(), Letter.Kind.PLAYER, from, sender.getUUID(), null, subjectText, bodyText, items, travelTime(distance));
        }
        RepManager.data(sender).addStat("letters_sent", 1);
        Feedback.toast(sender, "mail", Component.translatable("fealty.toast.mail_sent"), recipientName(recipient));
        Feedback.sound(sender, SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.2F);
        return Optional.empty();
    }

    private static Component recipientName(Recipient recipient) {
        return switch (recipient) {
            case ToVillage(VillageRecord village) -> Component.literal(village.name());
            case ToPlayer(GameProfile profile) -> Component.literal(profile.getName());
        };
    }

    private static void take(Inventory inventory, net.minecraft.world.item.Item item, int count) {
        for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
    }

    // ---- Delivery ----

    /** The villager takes a letter from the player and holds it for a moment before putting it away. */
    public static void handOver(Villager villager, ItemStack letter) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, letter);
        villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, villager.getVoicePitch());
        HELD_LETTERS.put(villager.getUUID(), new HeldLetter(villager.level().dimension(), villager.level().getGameTime() + 60));
    }

    private static void putAwayLetters(MinecraftServer server) {
        long now = now(server);
        Iterator<Map.Entry<UUID, HeldLetter>> held = HELD_LETTERS.entrySet().iterator();
        while (held.hasNext()) {
            Map.Entry<UUID, HeldLetter> entry = held.next();
            if (entry.getValue().until() > now) {
                continue;
            }
            held.remove();
            ServerLevel level = server.getLevel(entry.getValue().dimension());
            if (level != null && level.getEntity(entry.getKey()) instanceof Villager villager
                    && villager.getMainHandItem().is(ModItems.SEALED_LETTER.get())) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!HELD_LETTERS.isEmpty()) {
            putAwayLetters(server);
        }
        if (server.getTickCount() % 20 != 3) {
            return;
        }
        long now = now(server);
        MailData data = MailData.get(server);
        for (Map.Entry<UUID, List<Letter>> inbox : data.inboxes().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(inbox.getKey());
            if (player == null) {
                continue;
            }
            for (Letter letter : inbox.getValue()) {
                if (!letter.announced() && letter.arrived(now)) {
                    announce(player, letter);
                    data.setDirty();
                }
            }
        }
        Iterator<Letter> transit = data.toVillages().iterator();
        while (transit.hasNext()) {
            Letter letter = transit.next();
            if (letter.arrived(now) && villageReceives(server, letter)) {
                transit.remove();
                data.setDirty();
            }
        }
    }

    private static void announce(ServerPlayer player, Letter letter) {
        letter.setAnnounced(true);
        Feedback.toast(player, "mail", Component.translatable("fealty.toast.mail"), Component.translatable("fealty.toast.mail.from", letter.from()));
        Feedback.sound(player, SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.8F);
        syncStatus(player);
    }

    /** A village reads a letter: gifts in it count as gifts, other parcels go back, and it writes back. */
    private static boolean villageReceives(MinecraftServer server, Letter letter) {
        UUID senderId = letter.fromPlayer();
        Optional<VillageRecord> village = letter.toVillage() == null ? Optional.empty() : FealtyWorldData.get(server).village(letter.toVillage());
        if (senderId == null) {
            return true;
        }
        ServerPlayer sender = server.getPlayerList().getPlayer(senderId);
        if (sender == null) {
            return false; // wait until the sender is about to hear back
        }
        if (village.isEmpty()) {
            post(server, senderId, Letter.Kind.NOTICE, Component.translatable("fealty.mail.from.post"), null, null,
                    Component.translatable("fealty.mail.returned.subject"), Component.translatable("fealty.mail.returned.body"),
                    letter.items(), 20 * 30);
            return true;
        }
        VillageRecord record = village.get();
        RepManager.meet(sender, record.id());
        boolean hated = RepManager.getTier(sender, record.id()).rank() < TierManager.distrusted().rank();
        List<ItemStack> returned = new ArrayList<>();
        int gifts = 0;
        int gained = 0;
        for (ItemStack stack : letter.items()) {
            if (!hated && VillagerInteractions.isGift(stack)) {
                gifts++;
                gained += RepManager.applySource(sender, record.id(), RepSources.GIFT);
            } else {
                returned.add(stack);
            }
        }
        if (gifts > 0) {
            RepManager.data(sender).addStat("gifts_given", gifts);
        }
        String key = hated ? "reply_hated" : gifts > 0 ? (gained > 0 ? "reply_gift" : "reply_gift_capped") : "reply_letter";
        fromVillage(server, senderId, record, key, returned, travelTime(400), letter.subject());
        return true;
    }

    // ---- Reading ----

    /** The letters that have arrived for a player, newest first. */
    public static List<Letter> arrived(ServerPlayer player) {
        long now = now(player.server);
        List<Letter> letters = new ArrayList<>();
        for (Letter letter : MailData.get(player.server).inbox(player.getUUID())) {
            if (letter.arrived(now)) {
                letters.add(letter);
            }
        }
        letters.sort(Comparator.comparingLong(Letter::deliverAt).reversed());
        return letters;
    }

    public static int unread(ServerPlayer player) {
        int count = 0;
        for (Letter letter : arrived(player)) {
            if (!letter.isRead()) {
                count++;
            }
        }
        return count;
    }

    public static void syncStatus(ServerPlayer player) {
        FealtyNetwork.send(player, new MailStatusPayload(unread(player)));
    }

    @Nullable
    private static Letter find(ServerPlayer player, UUID id) {
        for (Letter letter : arrived(player)) {
            if (letter.id().equals(id)) {
                return letter;
            }
        }
        return null;
    }

    /** Open a mailbox: the player's letters, the villages they could write to, and a courier letter they could post here. */
    public static void openMailbox(ServerPlayer player, BlockPos pos) {
        if (!(player.level().getBlockEntity(pos) instanceof MailboxBlockEntity box)) {
            return;
        }
        Optional<VillageRecord> village = box.village() == null ? Optional.empty() : FealtyWorldData.get(player.server).village(box.village());
        Component title = village.map(v -> Component.translatable("fealty.mail.mailbox.village", v.name()))
                .orElseGet(() -> box.owner() != null ? Component.translatable("fealty.mail.mailbox.owner", box.ownerName())
                        : Component.translatable("block.fealty.mailbox"));
        List<OpenMailboxPayload.LetterView> letters = new ArrayList<>();
        for (Letter letter : arrived(player)) {
            letters.add(new OpenMailboxPayload.LetterView(letter.id(), letter.kind().ordinal(), letter.from(), letter.subject(), letter.body(),
                    List.copyOf(letter.items()), letter.isRead(), (int) Math.max(0, (now(player.server) - letter.deliverAt()) / 1200L)));
        }
        Component postable = village.flatMap(v -> courierLetter(player, v)).map(info -> (Component) Component.literal(info.fromName()))
                .orElse(Component.empty());
        FealtyNetwork.send(player, new OpenMailboxPayload(pos, title, letters, postable));
    }

    /** A courier letter the player carries for this village's mailbox. */
    private static Optional<LetterInfo> courierLetter(ServerPlayer player, VillageRecord village) {
        for (ItemStack stack : player.getInventory().items) {
            LetterInfo info = stack.get(ModDataComponents.LETTER.get());
            if (info != null && info.to().equals(village.id()) && info.owner().equals(player.getUUID())
                    && com.selluastar.fealty.quest.type.CourierObjective.goesToMailbox(player, info)) {
                return Optional.of(info);
            }
        }
        return Optional.empty();
    }

    /** What a player does at a mailbox. */
    public static void action(ServerPlayer player, BlockPos pos, String action, @Nullable UUID letterId) {
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64
                || !(player.level().getBlockEntity(pos) instanceof MailboxBlockEntity box)) {
            return;
        }
        Letter letter = letterId != null ? find(player, letterId) : null;
        MailData data = MailData.get(player.server);
        switch (action) {
            case "read" -> {
                if (letter != null && !letter.isRead()) {
                    letter.setRead(true);
                    data.setDirty();
                }
            }
            case "take" -> {
                if (letter != null) {
                    for (ItemStack stack : letter.items()) {
                        Maps.give(player, stack.copy());
                    }
                    letter.items().clear();
                    letter.setRead(true);
                    data.setDirty();
                    Feedback.sound(player, SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
                }
            }
            case "delete" -> {
                if (letter != null && letter.items().isEmpty()) {
                    data.inbox(player.getUUID()).remove(letter);
                    data.setDirty();
                }
            }
            case "write" -> {
                MailWriteMenu.open(player, pos);
                return;
            }
            case "post" -> {
                Optional<VillageRecord> village = box.village() == null ? Optional.empty() : FealtyWorldData.get(player.server).village(box.village());
                village.flatMap(v -> courierLetter(player, v)).ifPresent(info ->
                        com.selluastar.fealty.quest.type.CourierObjective.postAtMailbox(player, info));
            }
            default -> {
            }
        }
        syncStatus(player);
        openMailbox(player, pos);
    }

    // ---- Letters from villages ----

    /** Villages write to those they come to trust or honour, and warn those they come to hate. */
    @SubscribeEvent
    public static void onTierChanged(TierChangedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!Factions.isVillage(event.getFaction())) {
            return;
        }
        Optional<VillageRecord> village = FealtyWorldData.get(player.server).village(event.getFaction());
        if (village.isEmpty() || village.get().isBroken()) {
            return;
        }
        int rank = event.getNewTier().rank();
        String key = null;
        List<ItemStack> gift = new ArrayList<>();
        if (event.isRising() && rank == TierManager.trusted().rank()) {
            key = village.get().hasElder() ? "trusted_elder" : "trusted";
            gift.add(new ItemStack(Items.EMERALD, 3));
            gift.add(new ItemStack(Items.BREAD, 4));
        } else if (event.isRising() && rank == TierManager.honored().rank()) {
            key = "honored";
            gift.add(new ItemStack(Items.EMERALD, 8));
            gift.add(new ItemStack(Items.CAKE));
        } else if (!event.isRising() && rank == TierManager.hated().rank()) {
            key = "hated";
        }
        if (key != null && RepManager.data(player).setFlag(Fealty.id("mail/" + key + "/" + village.get().id().getPath()))) {
            fromVillage(player.server, player.getUUID(), village.get(), key, gift, travelTime(300));
        }
    }

    /** A bounty notice when a village puts a price on the player's head. */
    @SubscribeEvent
    public static void onWanted(WantedLevelEvent event) {
        if (event.getNewLevel() <= event.getOldLevel() || event.getNewLevel() <= 0) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        Component name = Factions.displayName(player.server, event.getFaction());
        post(player.server, player.getUUID(), Letter.Kind.NOTICE, Component.translatable("fealty.mail.from.notice"), null, null,
                Component.translatable("fealty.mail.wanted.subject"), Component.translatable("fealty.mail.wanted.body." + event.getNewLevel(), name),
                List.of(), 20 * 20);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncStatus(player);
        }
    }

    // ---- Village mailboxes ----

    /** Called every few seconds while a player is in the village: every village has a mailbox, put back once a day if lost. */
    public static void tickVillage(ServerLevel level, VillageRecord record) {
        if (!FealtyConfig.VILLAGE_MAILBOXES.get() || !record.dimension().equals(level.dimension()) || !level.isLoaded(record.center())) {
            return;
        }
        BlockPos pos = record.sites().mailbox();
        if (pos != null && level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.MAILBOX.get())) {
            return;
        }
        long day = RepManager.day(level.getServer());
        Long checked = MAILBOX_CHECKED.get(record.id());
        if (pos != null && checked != null && checked == day) {
            return;
        }
        MAILBOX_CHECKED.put(record.id(), day);
        BlockPos near = record.sites().guardPost() != null ? record.sites().guardPost() : record.center();
        Optional<BlockPos> spot = pos != null && SitePlanner.canPlaceOn(level, pos) ? Optional.of(pos) : SitePlanner.outdoors(level, record, near, 10);
        if (spot.isEmpty()) {
            return;
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
        BlockState state = ModBlocks.MAILBOX.get().defaultBlockState().setValue(MailboxBlock.FACING, facing);
        level.setBlock(spot.get(), state, 3);
        if (level.getBlockEntity(spot.get()) instanceof MailboxBlockEntity box) {
            box.setVillage(record.id());
        }
        record.sites().setMailbox(spot.get());
        FealtyWorldData.get(level.getServer()).setDirty();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MAILBOX_CHECKED.clear();
        HELD_LETTERS.clear();
    }
}
