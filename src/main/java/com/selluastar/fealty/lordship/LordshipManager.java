package com.selluastar.fealty.lordship;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.event.LordshipEvent;
import com.selluastar.fealty.api.event.TierChangedEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.guard.Garrison;
import com.selluastar.fealty.guard.GarrisonManager;
import com.selluastar.fealty.guard.GuardOrders;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.OpenHallPayload;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Inventories;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Village lordship, unlocked by the Royal Writ. A lord collects tribute, commands the guards with a horn and sets
 * taxes, trading tribute against the village's love. One lord per village; a better-loved rival with a Writ can
 * take it, and a lord who falls below Trusted is renounced.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class LordshipManager {
    public static final ResourceKey<LootTable> TRIBUTE = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("gameplay/tribute"));
    /** A gift of love: a great deal of one good thing, from a village that adores its lord. */
    public static final ResourceKey<LootTable> GIFT_OF_LOVE = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("gameplay/gift_of_love"));
    /** Gifts of love waiting in the treasury at most. */
    private static final int MAX_GIFTS = 3;
    private static final String[] TAX_NAMES = {"none", "light", "fair", "heavy", "crushing"};

    private LordshipManager() {
    }

    public static Component taxName(int level) {
        return Component.translatable("fealty.lord.tax." + TAX_NAMES[Math.max(0, Math.min(4, level))]);
    }

    public static int lordshipCount(MinecraftServer server, UUID player) {
        int count = 0;
        for (VillageRecord record : FealtyWorldData.get(server).villages()) {
            if (record.lord().isLord(player)) {
                count++;
            }
        }
        return count;
    }

    // ---- The elder's screen ----

    public static void elderActions(ServerPlayer player, VillageRecord village, List<ActionEntry> actions) {
        if (village.lord().isLord(player.getUUID())) {
            actions.add(ActionEntry.of(HALL, Component.translatable("fealty.lord.hall")));
            if (!Inventories.has(player, ModItems.LORDS_HORN.get())) {
                actions.add(ActionEntry.of("horn", Component.translatable("fealty.lord.new_horn")));
            }
        } else if (Inventories.has(player, ModItems.ROYAL_WRIT.get())) {
            Component label = Component.translatable("fealty.lord.swear");
            Optional<Component> problem = swearProblem(player, village);
            actions.add(problem.isEmpty() ? ActionEntry.of("swear", label) : ActionEntry.disabled("swear", label, problem.get()));
        }
    }

    public static void handleElderAction(ServerPlayer player, VillageRecord village, String action) {
        switch (action) {
            case "swear" -> trySwear(player, village);
            case HALL -> openHall(player, village);
            case "tax_up" -> adjustTax(player, village, village.lord().taxLevel() + 1);
            case "tax_down" -> adjustTax(player, village, village.lord().taxLevel() - 1);
            case "horn" -> {
                if (village.lord().isLord(player.getUUID()) && !Inventories.has(player, ModItems.LORDS_HORN.get())) {
                    Maps.give(player, new ItemStack(ModItems.LORDS_HORN.get()));
                }
            }
            default -> {
            }
        }
    }

    public static Optional<Component> swearProblem(ServerPlayer player, VillageRecord village) {
        if (village.isBroken()) {
            return Optional.of(Component.translatable("fealty.lord.problem.broken"));
        }
        RepTier tier = RepManager.getTier(player, village.id());
        if (tier.rank() < TierManager.honored().rank()) {
            return Optional.of(Component.translatable("fealty.lord.problem.not_honored", TierManager.honored().displayName()));
        }
        if (lordshipCount(player.server, player.getUUID()) >= FealtyConfig.MAX_LORDSHIPS.get()) {
            return Optional.of(Component.translatable("fealty.lord.problem.too_many", FealtyConfig.MAX_LORDSHIPS.get()));
        }
        UUID current = village.lord().uuid();
        if (current != null && !current.equals(player.getUUID())) {
            int lordRep = RepManager.getRep(player.server, current, village.id());
            boolean lordStillTrusted = RepManager.tierOf(lordRep).rank() >= TierManager.trusted().rank();
            if (lordStillTrusted && RepManager.getRep(player, village.id()) <= lordRep) {
                return Optional.of(Component.translatable("fealty.lord.problem.loved_lord", village.lord().name()));
            }
        }
        return Optional.empty();
    }

    public static void trySwear(ServerPlayer player, VillageRecord village) {
        Optional<Component> problem = swearProblem(player, village);
        if (problem.isPresent()) {
            player.sendSystemMessage(problem.get().copy().withStyle(ChatFormatting.RED));
            return;
        }
        if (!Inventories.takeOne(player, ModItems.ROYAL_WRIT.get())) {
            return;
        }
        swear(player, village, false);
    }

    /** Make the player the village's lord, displacing any current lord. */
    public static void swear(ServerPlayer player, VillageRecord village, boolean byCommand) {
        MinecraftServer server = player.server;
        UUID previous = village.lord().uuid();
        String previousName = village.lord().name();
        village.lord().swear(player.getUUID(), player.getGameProfile().getName(), RepManager.day(server));
        FealtyWorldData.get(server).setDirty();
        if (!Inventories.has(player, ModItems.LORDS_HORN.get())) {
            Maps.give(player, new ItemStack(ModItems.LORDS_HORN.get()));
        }
        boolean usurped = previous != null && !previous.equals(player.getUUID());
        NeoForge.EVENT_BUS.post(new LordshipEvent(server, village.id(), usurped ? LordshipEvent.Type.USURPED : LordshipEvent.Type.SWORN,
                Optional.of(player.getUUID()), Optional.ofNullable(previous)));
        FealtyEvents.fire(player, usurped ? FealtyEvents.USURPED : FealtyEvents.SWORN_LORD);
        broadcast(server, village, Component.translatable("fealty.lord.sworn", village.name(), player.getDisplayName()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("fealty.lord.sworn_self", village.name()).withStyle(ChatFormatting.GOLD));
        if (usurped) {
            ServerPlayer old = server.getPlayerList().getPlayer(previous);
            if (old != null) {
                old.sendSystemMessage(Component.translatable("fealty.lord.usurped", player.getDisplayName(), village.name()).withStyle(ChatFormatting.RED));
                FealtyEvents.fire(old, FealtyEvents.LORDSHIP_LOST);
                FealtyNetwork.syncStanding(old, village.id());
            }
            player.sendSystemMessage(Component.translatable("fealty.lord.usurper", previousName, village.name()));
        }
        FealtyNetwork.syncStanding(player, village.id());
    }

    public static void clearLord(MinecraftServer server, VillageRecord village, @Nullable String reasonKey) {
        UUID previous = village.lord().uuid();
        if (previous == null) {
            return;
        }
        String name = village.lord().name();
        village.lord().clear();
        FealtyWorldData.get(server).setDirty();
        NeoForge.EVENT_BUS.post(new LordshipEvent(server, village.id(), LordshipEvent.Type.LOST, Optional.empty(), Optional.of(previous)));
        ServerPlayer old = server.getPlayerList().getPlayer(previous);
        if (old != null) {
            if (reasonKey != null) {
                old.sendSystemMessage(Component.translatable(reasonKey, village.name()).withStyle(ChatFormatting.RED));
            }
            FealtyEvents.fire(old, FealtyEvents.LORDSHIP_LOST);
            FealtyNetwork.syncStanding(old, village.id());
        }
        broadcast(server, village, Component.translatable("fealty.lord.renounced", village.name(), name));
    }

    private static void broadcast(MinecraftServer server, VillageRecord village, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (village.contains(player.level().dimension(), player.blockPosition())) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static void adjustTax(ServerPlayer player, VillageRecord village, int level) {
        if (!village.lord().isLord(player.getUUID())) {
            return;
        }
        int before = village.lord().taxLevel();
        village.lord().setTaxLevel(level);
        FealtyWorldData.get(player.server).setDirty();
        if (village.lord().taxLevel() != before) {
            player.sendSystemMessage(Component.translatable("fealty.lord.tax_set", village.name(), taxName(village.lord().taxLevel())));
            Feedback.sound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 1.0F);
        }
    }

    /** A lord who falls below Trusted is renounced. */
    @SubscribeEvent
    public static void onTierChanged(TierChangedEvent event) {
        if (!Factions.isVillage(event.getFaction()) || event.isRising()) {
            return;
        }
        Optional<VillageRecord> village = FealtyWorldData.get(event.getPlayer().server).village(event.getFaction());
        if (village.isPresent() && village.get().lord().isLord(event.getPlayer().getUUID())
                && event.getNewTier().rank() < TierManager.trusted().rank()) {
            clearLord(event.getPlayer().server, village.get(), "fealty.lord.lost");
        }
    }

    // ---- Daily: taxes and tribute ----

    /** Tribute gathered in a day at a tax level, in rolls of the tribute table (more villagers, more tribute). */
    public static double tributePerDay(VillageRecord village, int taxLevel) {
        int population = village.garrison().population();
        double tens = population < 0 ? 1.0 : Math.max(0.3, population / 10.0);
        return tens * FealtyConfig.TRIBUTE_ROLLS_PER_10_VILLAGERS.get() * FealtyConfig.taxTributeMultiplier(taxLevel);
    }

    /**
     * The daily chance of a gift of love: likelier the lighter the taxes (none at Crushing), halved for a lord the
     * village only trusts, and half as likely again for one it adores.
     */
    public static double giftChance(int taxLevel, int standing) {
        int rank = RepManager.tierOf(standing).rank();
        double love = standing >= 90 ? 1.5 : rank >= TierManager.honored().rank() ? 1.0 : rank >= TierManager.trusted().rank() ? 0.5 : 0.0;
        return Math.min(1.0, FealtyConfig.giftChance(taxLevel) * love);
    }

    /** The most tribute a treasury holds: a week's worth. */
    private static double treasuryCap(VillageRecord village) {
        return Math.max(8.0, tributePerDay(village, 4) * 7.0);
    }

    public static void onNewDay(MinecraftServer server, long day) {
        FealtyWorldData data = FealtyWorldData.get(server);
        for (VillageRecord village : new ArrayList<>(data.villages())) {
            UUID lord = village.lord().uuid();
            if (lord == null) {
                continue;
            }
            VillageRecord.LordInfo info = village.lord();
            ServerPlayer online = server.getPlayerList().getPlayer(lord);
            // The lord's standing drifts with the taxes.
            int dailyRep = FealtyConfig.taxDailyRep(info.taxLevel());
            long days = Math.max(0, day - info.lastTaxDay());
            info.setLastTaxDay(day);
            if (dailyRep != 0 && days > 0) {
                int amount = (int) Math.max(-1000, Math.min(1000, dailyRep * days));
                if (online != null) {
                    RepManager.change(online, village.id(), amount, RepSources.TAX, true);
                } else {
                    RepManager.changeOffline(server, lord, village.id(), amount);
                    if (RepManager.tierOf(RepManager.getRep(server, lord, village.id())).rank() < TierManager.trusted().rank()) {
                        clearLord(server, village, null);
                        continue;
                    }
                }
            }
            // Tribute is gathered every day, whether or not anyone is there, and waits in the treasury.
            long tributeDays = Math.max(0, Math.min(7, day - info.lastTributeDay()));
            info.setLastTributeDay(day);
            double gathered = tributePerDay(village, info.taxLevel()) * tributeDays;
            if (info.pendingTribute() > 0) {
                // (tribute periods owed under 0.1)
                gathered += info.pendingTribute() * FealtyConfig.TRIBUTE_ROLLS_PER_10_VILLAGERS.get();
                info.setPendingTribute(0);
            }
            if (gathered > 0) {
                info.setTreasury(Math.min(treasuryCap(village), info.treasury() + gathered));
                if (online != null) {
                    Feedback.toast(online, "coin", Component.translatable("fealty.toast.tribute", village.name()),
                            Component.translatable("fealty.toast.tribute.detail", (int) Math.floor(info.treasury())));
                }
            }
            // Now and then, out of love for their lord, the village gathers a gift.
            double chance = giftChance(info.taxLevel(), RepManager.getRep(server, lord, village.id()));
            boolean gifted = false;
            for (long d = 0; d < tributeDays && info.gifts() < MAX_GIFTS; d++) {
                if (server.overworld().getRandom().nextDouble() < chance) {
                    info.setGifts(info.gifts() + 1);
                    gifted = true;
                }
            }
            if (gifted) {
                MailService.fromVillage(server, lord, village, "gift_of_love", List.of(), 20 * 20);
                if (online != null) {
                    Feedback.banner(online, Component.translatable("fealty.banner.gift_of_love"),
                            Component.translatable("fealty.banner.gift_of_love.detail", village.name()), 0xE57399, "heart");
                    Feedback.sound(online, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
                }
            }
            // A report by letter every few days, while tribute waits.
            int interval = FealtyConfig.TRIBUTE_INTERVAL_DAYS.get();
            if (day - info.lastReportDay() >= interval && info.treasury() >= 1.0) {
                info.setLastReportDay(day);
                MailService.fromVillage(server, lord, village, "tribute_report", List.of(), 20 * 20,
                        (int) Math.floor(info.treasury()), taxName(info.taxLevel()));
            }
        }
        data.setDirty();
    }

    /** While a player is in the village: nothing to pay out any more (tribute waits in the treasury). */
    public static void tickVillage(ServerLevel level, VillageRecord village) {
    }

    // ---- The Village Hall ----

    public static final String HALL = "hall";
    private static final int FEAST_COOLDOWN_DAYS = 7;
    private static final int FEAST_FOOD = 16;
    private static final int FEAST_EMERALDS = 8;
    private static final int RECRUIT_COST = 10;

    /** Whether the lord is in their village (collecting tribute, feasts and recruiting are done in person). */
    private static boolean inVillage(ServerPlayer player, VillageRecord village) {
        return village.contains(player.level().dimension(), player.blockPosition());
    }

    /** Open the Village Hall for the lord. */
    public static void openHall(ServerPlayer player, VillageRecord village) {
        if (!village.lord().isLord(player.getUUID())) {
            return;
        }
        MinecraftServer server = player.server;
        long day = RepManager.day(server);
        VillageRecord.LordInfo info = village.lord();
        List<Float> tribute = new ArrayList<>();
        List<Integer> loyalty = new ArrayList<>();
        List<Float> gifts = new ArrayList<>();
        int standing = RepManager.getRep(player, village.id());
        for (int level = 0; level <= 4; level++) {
            tribute.add((float) tributePerDay(village, level));
            loyalty.add(FealtyConfig.taxDailyRep(level));
            gifts.add((float) giftChance(level, standing));
        }
        List<OpenHallPayload.GuardRow> roster = new ArrayList<>();
        for (Garrison.Slot slot : village.garrison().slots()) {
            int state;
            int days = 0;
            if (slot.isDead()) {
                state = 2;
                days = (int) Math.max(0, slot.diedDay() + FealtyConfig.GUARD_RESPAWN_DAYS.get() - day);
            } else if (slot.entity() == null) {
                state = 3;
            } else {
                state = isWithLord(player, slot) ? 1 : 0;
            }
            roster.add(new OpenHallPayload.GuardRow(slot.rank().getSerializedName(), slot.name(), state, days));
        }
        // The village's other guards: iron golems and other mods' guards (Guard Villagers, ...), as last seen.
        for (Garrison.Other other : village.garrison().others()) {
            int state;
            if (other.diedDay() >= 0) {
                state = 2;
            } else if (isWithLord(player, player.serverLevel().getEntity(other.entity()))) {
                state = 1;
            } else {
                state = other.seenDay() >= day - 1 ? 0 : 4;
            }
            roster.add(new OpenHallPayload.GuardRow(other.kind(), other.name(), state, 0));
        }
        int feastCooldown = (int) Math.max(0, info.lastFeastDay() + FEAST_COOLDOWN_DAYS - day);
        int population = Math.max(0, village.garrison().population());
        FealtyNetwork.send(player, new OpenHallPayload(village.id().toString(), village.name(), village.color(), info.name(),
                (int) Math.max(0, day - info.swornDay()), population, village.garrison().allAlive(), village.garrison().allTotal(),
                standing, info.taxLevel(), info.treasury(), tribute, loyalty, feastCooldown,
                inVillage(player, village), roster, RECRUIT_COST, FEAST_FOOD, FEAST_EMERALDS, gifts, info.gifts()));
    }

    private static boolean isWithLord(ServerPlayer player, Garrison.Slot slot) {
        if (slot.entity() == null || !player.level().dimension().equals(slot.lastDimension())) {
            return false;
        }
        return isWithLord(player, player.serverLevel().getEntity(slot.entity()));
    }

    /** Whether a guard (if loaded) is following the player's orders. */
    private static boolean isWithLord(ServerPlayer player, @Nullable Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.hasData(ModAttachments.GUARD_ORDERS)) {
            return false;
        }
        GuardOrders orders = mob.getData(ModAttachments.GUARD_ORDERS);
        return orders.isActive(player.level().getGameTime()) && player.getUUID().equals(orders.leader());
    }

    /** A lord's choice in the hall. */
    public static void hallAction(ServerPlayer player, String villageId, String action, int argument) {
        ResourceLocation id = ResourceLocation.tryParse(villageId);
        Optional<VillageRecord> found = id == null ? Optional.empty() : FealtyWorldData.get(player.server).village(id);
        if (found.isEmpty() || !found.get().lord().isLord(player.getUUID())) {
            return;
        }
        VillageRecord village = found.get();
        boolean near = inVillage(player, village);
        switch (action) {
            case "open" -> {
            }
            case "tax" -> adjustTax(player, village, argument);
            case "collect" -> {
                if (near) {
                    collect(player, village);
                }
            }
            case "feast" -> {
                if (near) {
                    feast(player, village);
                }
            }
            case "recruit" -> {
                if (near) {
                    recruit(player, village, argument);
                }
            }
            default -> {
            }
        }
        openHall(player, village);
    }

    /** The lord takes the tribute waiting in the treasury. */
    private static void collect(ServerPlayer player, VillageRecord village) {
        VillageRecord.LordInfo info = village.lord();
        int rolls = (int) Math.floor(info.treasury());
        int gifts = info.gifts();
        if (rolls <= 0 && gifts <= 0) {
            player.displayClientMessage(Component.translatable("fealty.hall.treasury_empty"), true);
            return;
        }
        info.setTreasury(info.treasury() - rolls);
        info.setGifts(0);
        FealtyWorldData.get(player.server).setDirty();
        int items = roll(player, TRIBUTE, rolls);
        if (rolls > 0) {
            player.sendSystemMessage(Component.translatable("fealty.lord.tribute", village.name(), rolls).withStyle(ChatFormatting.GOLD));
        }
        if (gifts > 0) {
            items += roll(player, GIFT_OF_LOVE, gifts);
            player.sendSystemMessage(Component.translatable("fealty.lord.gifts", village.name(), gifts).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        Feedback.toast(player, "coin", Component.translatable("fealty.toast.tribute_collected"),
                Component.translatable("fealty.toast.tribute_collected.detail", items, village.name()));
        Feedback.sound(player, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
        FealtyEvents.fire(player, FealtyEvents.TRIBUTE_COLLECTED);
    }

    /** Roll a loot table for the player a number of times. @return how many items they got */
    private static int roll(ServerPlayer player, ResourceKey<LootTable> key, int times) {
        ServerLevel level = player.serverLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        Vec3 origin = player.position();
        int items = 0;
        for (int i = 0; i < times; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                items += stack.getCount();
                Maps.give(player, stack);
            }
        }
        return items;
    }

    /** Hold a feast: the village loves its lord a little more, and everyone who comes is welcome. */
    private static void feast(ServerPlayer player, VillageRecord village) {
        long day = RepManager.day(player.server);
        VillageRecord.LordInfo info = village.lord();
        if (day - info.lastFeastDay() < FEAST_COOLDOWN_DAYS) {
            player.displayClientMessage(Component.translatable("fealty.hall.feast_cooldown"), true);
            return;
        }
        int food = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.has(DataComponents.FOOD)) {
                food += stack.getCount();
            }
        }
        if (!player.isCreative() && (food < FEAST_FOOD || player.getInventory().countItem(Items.EMERALD) < FEAST_EMERALDS)) {
            player.displayClientMessage(Component.translatable("fealty.hall.feast_cost", FEAST_FOOD, FEAST_EMERALDS).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.isCreative()) {
            int foodLeft = FEAST_FOOD;
            int emeraldsLeft = FEAST_EMERALDS;
            for (ItemStack stack : player.getInventory().items) {
                if (foodLeft > 0 && stack.has(DataComponents.FOOD)) {
                    int taken = Math.min(foodLeft, stack.getCount());
                    stack.shrink(taken);
                    foodLeft -= taken;
                } else if (emeraldsLeft > 0 && stack.is(Items.EMERALD)) {
                    int taken = Math.min(emeraldsLeft, stack.getCount());
                    stack.shrink(taken);
                    emeraldsLeft -= taken;
                }
            }
        }
        info.setLastFeastDay(day);
        FealtyWorldData.get(player.server).setDirty();
        RepManager.change(player, village.id(), 5, RepSources.FEAST, true);
        ServerLevel level = player.serverLevel();
        for (ServerPlayer guest : level.players()) {
            if (guest != player && village.contains(guest.level().dimension(), guest.blockPosition())) {
                RepManager.meet(guest, village.id());
                RepManager.applySource(guest, village.id(), RepSources.FEAST);
                guest.sendSystemMessage(Component.translatable("fealty.hall.feast_guest", player.getDisplayName(), village.name())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        for (Villager villager : level.getEntitiesOfClass(Villager.class, AABB.of(village.bounds()), Villager::isAlive)) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.4, villager.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
            if (villager.getRandom().nextInt(3) == 0) {
                Speech.say(villager, Component.translatable("fealty.hall.feast_cheer." + villager.getRandom().nextInt(4), player.getDisplayName()));
            }
        }
        broadcast(player.server, village, Component.translatable("fealty.hall.feast_announce", player.getDisplayName(), village.name())
                .withStyle(ChatFormatting.GOLD));
        Feedback.banner(player, Component.translatable("fealty.banner.feast"), Component.translatable("fealty.banner.feast.detail", village.name()),
                village.color(), "crown");
        Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
    }

    /** Pay to swear in a new guard now, in place of one who fell. */
    private static void recruit(ServerPlayer player, VillageRecord village, int index) {
        Optional<Garrison.Slot> slot = village.garrison().slot(index);
        if (slot.isEmpty() || !slot.get().isDead()) {
            return;
        }
        if (!player.isCreative() && player.getInventory().countItem(Items.EMERALD) < RECRUIT_COST) {
            player.displayClientMessage(Component.translatable("fealty.hall.recruit_cost", RECRUIT_COST).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.isCreative()) {
            int left = RECRUIT_COST;
            for (ItemStack stack : player.getInventory().items) {
                if (left > 0 && stack.is(Items.EMERALD)) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
        slot.get().readyNow();
        FealtyWorldData.get(player.server).setDirty();
        GarrisonManager.tickVillage(player.serverLevel(), village);
        player.displayClientMessage(Component.translatable("fealty.hall.recruited"), true);
        Feedback.sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 1.0F);
    }
}
