package com.selluastar.fealty.crime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.event.FineEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.SourceSettings;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.FineStatusPayload;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Inventories;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Paying for what you did. A guard who catches a player at a minor or moderate crime demands a fine: paying it (talk
 * to the guard, or press the buttons in chat or the Journal) wins back half of what the crime cost and calls off the
 * watch; refusing, or walking away until time runs out, turns the watch hostile. Elders let a player with a
 * bad name, or with the watch or bounty hunters after them, pay to clear it, a little each day.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Fines {
    public static final String ELDER_ACTION = "fine";
    /** The most reputation one fine paid to an elder wins back. */
    private static final int ELDER_MAX_RESTORE = 20;
    private static final int MAX_COST = 64;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** A guard's fine waiting to be paid. */
    public record Pending(ResourceLocation faction, UUID guard, int cost, int restore, int heat, long deadline) {
    }

    /** What clearing the player's name with an elder costs, and what it does. {@code restore} 0 and no heat means not today. */
    public record Quote(int cost, int restore, boolean callsOffWatch) {
        public boolean available() {
            return restore > 0 || callsOffWatch;
        }
    }

    private Fines() {
    }

    // ---- Guards ----

    /**
     * A guard caught the player at a crime that cost them {@code change}. Demands a fine instead of a bare warning.
     *
     * @return whether a fine was demanded
     */
    public static boolean issue(ServerPlayer player, Mob guard, ResourceLocation faction, ResourceLocation crime, int change) {
        if (!FealtyConfig.FINES.get() || change >= 0) {
            return false;
        }
        int cost = Math.min(MAX_COST, Math.max(2, Math.round(-change * 0.5F)));
        FineEvent.Issued issued = NeoForge.EVENT_BUS.post(new FineEvent.Issued(player, faction, guard, crime, cost));
        if (issued.isCanceled()) {
            return false;
        }
        cost = issued.getCost();
        int heat = FealtyDataManager.source(crime).heat();
        long deadline = player.level().getGameTime() + FealtyConfig.GUARD_FINE_SECONDS.get() * 20L;
        PENDING.put(player.getUUID(), new Pending(faction, guard.getUUID(), cost, -change / 2, heat, deadline));
        guard.getNavigation().moveTo(player, 1.0);
        guard.getLookControl().setLookAt(player);
        Speech.say(guard, Component.translatable("fealty.fine.demand", cost));
        // Pay in conversation with the guard, from these buttons, or in the Journal.
        player.sendSystemMessage(Component.translatable("fealty.fine.issued", guard.getDisplayName(), cost, FealtyConfig.GUARD_FINE_SECONDS.get())
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(buttons(cost));
        sync(player);
        Feedback.sound(player, SoundEvents.VILLAGER_NO, 0.8F, 0.7F);
        return true;
    }

    /** The fine the player owes the guard's village, if any. */
    public static Optional<Pending> owed(ServerPlayer player, Entity guard) {
        Pending fine = PENDING.get(player.getUUID());
        if (fine == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(guard);
        return faction.isPresent() && faction.get().equals(fine.faction()) ? Optional.of(fine) : Optional.empty();
    }

    public static boolean canPay(ServerPlayer player, int cost) {
        return player.isCreative() || Inventories.count(player, Items.EMERALD) >= cost;
    }

    /** What a player owes, for the Journal: the village, the cost and the seconds left to pay. */
    public record Status(String village, int cost, int secondsLeft) {
    }

    /** The fine the player owes, wherever the guard who demanded it is. */
    public static Optional<Status> status(ServerPlayer player) {
        Pending fine = PENDING.get(player.getUUID());
        if (fine == null) {
            return Optional.empty();
        }
        String village = FealtyWorldData.get(player.server).village(fine.faction()).map(VillageRecord::name).orElse("");
        int seconds = (int) Math.max(0, (fine.deadline() - player.level().getGameTime() + 19) / 20);
        return Optional.of(new Status(village, fine.cost(), seconds));
    }

    /** Tell the player's client what they owe (nothing, once paid or refused). */
    private static void sync(ServerPlayer player) {
        Optional<Status> status = status(player);
        FealtyNetwork.send(player, new FineStatusPayload(status.isPresent(), status.map(Status::village).orElse(""),
                status.map(Status::cost).orElse(0), status.map(Status::secondsLeft).orElse(0)));
    }

    /** The chat buttons under a demand for a fine. */
    private static Component buttons(int cost) {
        Component pay = Component.translatable("fealty.fine.button.pay", cost).withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fealty fine pay"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("fealty.fine.button.pay.hint", cost))));
        Component refuse = Component.translatable("fealty.fine.button.refuse").withStyle(st -> st.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fealty fine refuse"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("fealty.fine.button.refuse.hint"))));
        return Component.literal("  ").append(pay).append("  ").append(refuse);
    }

    /** @return whether the fine was paid */
    public static boolean payGuard(ServerPlayer player, Entity guard) {
        Optional<Pending> fine = owed(player, guard);
        return fine.isPresent() && pay(player, fine.get(), guard);
    }

    /** Pay the fine the player owes, wherever the guard is (the Journal's and the chat's buttons). @return whether it was paid */
    public static boolean payPending(ServerPlayer player) {
        Pending fine = PENDING.get(player.getUUID());
        if (fine == null) {
            return false;
        }
        return pay(player, fine, player.serverLevel().getEntity(fine.guard()));
    }

    private static boolean pay(ServerPlayer player, Pending fine, @Nullable Entity guard) {
        if (!player.isCreative() && !Inventories.take(player, Items.EMERALD, fine.cost())) {
            player.displayClientMessage(Component.translatable("fealty.fine.short", fine.cost()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        PENDING.remove(player.getUUID());
        pardon(player, fine.faction(), fine.restore(), fine.heat(), false);
        NeoForge.EVENT_BUS.post(new FineEvent.Paid(player, fine.faction(), guard, fine.cost(), false));
        Feedback.sound(player, SoundEvents.CHAIN_PLACE, 0.8F, 1.6F);
        sync(player);
        return true;
    }

    /** The player will not pay: the watch comes for them. */
    public static void refuse(ServerPlayer player, Entity guard) {
        if (owed(player, guard).isPresent()) {
            refusePending(player);
        }
    }

    /** Refuse the fine the player owes (the Journal's and the chat's buttons). @return whether there was one */
    public static boolean refusePending(ServerPlayer player) {
        Pending fine = PENDING.remove(player.getUUID());
        if (fine == null) {
            return false;
        }
        turnHostile(player, fine);
        return true;
    }

    private static void turnHostile(ServerPlayer player, Pending fine) {
        sync(player);
        NeoForge.EVENT_BUS.post(new FineEvent.Refused(player, fine.faction()));
        ServerLevel level = player.serverLevel();
        PlayerRepData data = RepManager.data(player);
        data.aggroUntil().put(fine.faction(), level.getGameTime() + FealtyConfig.GUARD_AGGRO_TICKS.get());
        FealtyWorldData.get(player.server).setDirty();
        Entity guard = level.getEntity(fine.guard());
        if (guard instanceof Mob mob && mob.isAlive()) {
            mob.setTarget(player);
            Speech.say(mob, Component.translatable("fealty.fine.unpaid_shout"));
        }
        player.sendSystemMessage(Component.translatable("fealty.fine.unpaid").withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
            } else if (player.level().getGameTime() >= entry.getValue().deadline()) {
                it.remove();
                turnHostile(player, entry.getValue());
            }
        }
    }

    // ---- Elders ----

    public static Optional<Quote> quote(ServerPlayer player, VillageRecord village) {
        if (!FealtyConfig.FINES.get() || village.lord().isLord(player.getUUID())) {
            return Optional.empty();
        }
        ResourceLocation id = village.id();
        int rep = RepManager.getRep(player, id);
        int heat = HeatManager.heat(player, id);
        Long aggro = RepManager.data(player).aggroUntil().get(id);
        boolean watch = heat > 0 || aggro != null && aggro > player.level().getGameTime();
        if (rep >= 0 && !watch) {
            return Optional.empty();
        }
        int restore = rep < 0 ? Math.min(Math.min(-rep, ELDER_MAX_RESTORE), dailyRoom(player, id)) : 0;
        int cost = Math.min(MAX_COST, 4 + (restore + 1) / 2 + heat / 2);
        return Optional.of(new Quote(cost, restore, watch));
    }

    /** How much more reputation fines may win back with the village today. */
    private static int dailyRoom(ServerPlayer player, ResourceLocation village) {
        SourceSettings settings = FealtyDataManager.source(RepSources.FINE);
        if (settings.dailyCap() <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, settings.dailyCap() - RepManager.data(player).tally(village, RepSources.FINE, RepManager.day(player.server)).applied());
    }

    /** The elder's "pay a fine" option. */
    public static Optional<ActionEntry> elderAction(ServerPlayer player, VillageRecord village) {
        return quote(player, village).map(quote -> {
            Component label = Component.translatable("fealty.fine.elder_option", quote.cost());
            if (!quote.available()) {
                return ActionEntry.disabled(ELDER_ACTION, label, Component.translatable("fealty.fine.tomorrow"));
            }
            if (!canPay(player, quote.cost())) {
                return ActionEntry.disabled(ELDER_ACTION, label, Component.translatable("fealty.fine.short", quote.cost()));
            }
            return ActionEntry.of(ELDER_ACTION, label);
        });
    }

    public static void payElder(ServerPlayer player, VillageRecord village, Entity elder) {
        Optional<Quote> quote = quote(player, village);
        if (quote.isEmpty() || !quote.get().available()) {
            return;
        }
        if (!player.isCreative() && !Inventories.take(player, Items.EMERALD, quote.get().cost())) {
            player.displayClientMessage(Component.translatable("fealty.fine.short", quote.get().cost()).withStyle(ChatFormatting.RED), true);
            return;
        }
        PENDING.remove(player.getUUID());
        sync(player);
        pardon(player, village.id(), quote.get().restore(), 0, quote.get().callsOffWatch());
        NeoForge.EVENT_BUS.post(new FineEvent.Paid(player, village.id(), elder, quote.get().cost(), true));
        Speech.say(elder, Component.translatable("fealty.fine.elder_paid"));
        Feedback.sound(player, SoundEvents.CHAIN_PLACE, 0.8F, 1.6F);
    }

    /**
     * Win back reputation and call off the watch.
     *
     * @param clearHeat clear all heat with the village (the elder), rather than just this crime's share (a guard)
     */
    private static void pardon(ServerPlayer player, ResourceLocation faction, int restore, int heat, boolean clearHeat) {
        if (restore > 0) {
            RepManager.applyCapped(player, faction, RepSources.FINE, restore);
        }
        PlayerRepData data = RepManager.data(player);
        data.warnedAt().remove(faction);
        data.aggroUntil().remove(faction);
        if (clearHeat) {
            HeatManager.setHeat(player, faction, 0);
        } else if (heat > 0) {
            HeatManager.setHeat(player, faction, HeatManager.heat(player, faction) - heat);
        }
        FealtyWorldData.get(player.server).setDirty();
        for (Mob guard : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64),
                m -> m.isAlive() && m.getTarget() == player && GuardManager.isGuard(m))) {
            if (FactionResolver.factionOf(guard).filter(faction::equals).isPresent()) {
                guard.setTarget(null);
                if (guard instanceof NeutralMob neutral) {
                    neutral.stopBeingAngry();
                }
            }
        }
        player.displayClientMessage(Component.translatable("fealty.fine.paid").withStyle(ChatFormatting.GREEN), true);
    }

    /** Leaving the game does not dodge a fine. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Pending fine = PENDING.remove(event.getEntity().getUUID());
        if (fine != null && event.getEntity() instanceof ServerPlayer player) {
            RepManager.data(player).aggroUntil().put(fine.faction(), player.level().getGameTime() + FealtyConfig.GUARD_AGGRO_TICKS.get());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
