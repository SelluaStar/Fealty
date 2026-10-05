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
import com.selluastar.fealty.block.VillageCofferBlockEntity;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Inventories;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
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
            int tax = village.lord().taxLevel();
            actions.add(ActionEntry.disabled("tax_info", Component.translatable("fealty.lord.tax_label", taxName(tax)),
                    Component.translatable("fealty.lord.tax_hint", FealtyConfig.taxTributeMultiplier(tax), FealtyConfig.taxDailyRep(tax))));
            actions.add(tax < 4 ? ActionEntry.of("tax_up", Component.translatable("fealty.lord.tax_up"))
                    : ActionEntry.disabled("tax_up", Component.translatable("fealty.lord.tax_up"), Component.translatable("fealty.lord.tax_max")));
            actions.add(tax > 0 ? ActionEntry.of("tax_down", Component.translatable("fealty.lord.tax_down"))
                    : ActionEntry.disabled("tax_down", Component.translatable("fealty.lord.tax_down"), Component.translatable("fealty.lord.tax_min")));
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
            case "tax_up" -> adjustTax(player, village, 1);
            case "tax_down" -> adjustTax(player, village, -1);
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

    private static void adjustTax(ServerPlayer player, VillageRecord village, int delta) {
        if (!village.lord().isLord(player.getUUID())) {
            return;
        }
        village.lord().setTaxLevel(village.lord().taxLevel() + delta);
        FealtyWorldData.get(player.server).setDirty();
        player.sendSystemMessage(Component.translatable("fealty.lord.tax_set", village.name(), taxName(village.lord().taxLevel())));
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

    public static void onNewDay(MinecraftServer server, long day) {
        FealtyWorldData data = FealtyWorldData.get(server);
        for (VillageRecord village : new ArrayList<>(data.villages())) {
            UUID lord = village.lord().uuid();
            if (lord == null) {
                continue;
            }
            VillageRecord.LordInfo info = village.lord();
            int dailyRep = FealtyConfig.taxDailyRep(info.taxLevel());
            long days = Math.max(0, day - info.lastTaxDay());
            info.setLastTaxDay(day);
            if (dailyRep != 0 && days > 0) {
                int amount = (int) Math.max(-1000, Math.min(1000, dailyRep * days));
                ServerPlayer online = server.getPlayerList().getPlayer(lord);
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
            int interval = FealtyConfig.TRIBUTE_INTERVAL_DAYS.get();
            long owed = (day - info.lastTributeDay()) / interval;
            if (owed > 0) {
                info.setLastTributeDay(info.lastTributeDay() + owed * interval);
                info.setPendingTribute(info.pendingTribute() + (int) Math.min(owed, 30));
            }
        }
        data.setDirty();
    }

    /** While the village is loaded, pay any owed tribute into the coffer (or to the lord, if there is no coffer). */
    public static void tickVillage(ServerLevel level, VillageRecord village) {
        VillageRecord.LordInfo info = village.lord();
        if (info.uuid() == null || info.pendingTribute() <= 0 || !village.dimension().equals(level.dimension())) {
            return;
        }
        double multiplier = FealtyConfig.taxTributeMultiplier(info.taxLevel());
        int periods = info.pendingTribute();
        info.setPendingTribute(0);
        FealtyWorldData.get(level.getServer()).setDirty();
        if (multiplier <= 0) {
            return;
        }
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, AABB.of(village.bounds()),
                v -> v.isAlive() && village.id().equals(FactionResolver.factionOf(v).orElse(null)));
        int rolls = (int) Math.round(villagers.size() / 10.0 * FealtyConfig.TRIBUTE_ROLLS_PER_10_VILLAGERS.get() * multiplier) * periods;
        rolls = Math.max(periods, Math.min(rolls, 200));
        BlockPos coffer = village.elder().coffer();
        Vec3 origin = coffer != null ? Vec3.atCenterOf(coffer) : Vec3.atCenterOf(village.center());
        LootTable table = level.getServer().reloadableRegistries().getLootTable(TRIBUTE);
        List<ItemStack> tribute = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
            tribute.addAll(table.getRandomItems(params));
        }
        if (tribute.isEmpty()) {
            return;
        }
        ServerPlayer lord = level.getServer().getPlayerList().getPlayer(info.uuid());
        List<ItemStack> leftovers = tribute;
        if (coffer != null && level.isLoaded(coffer) && level.getBlockEntity(coffer) instanceof VillageCofferBlockEntity box) {
            leftovers = box.deposit(tribute);
        }
        if (!leftovers.isEmpty() && lord != null && village.contains(lord.level().dimension(), lord.blockPosition())) {
            leftovers.forEach(stack -> Maps.give(lord, stack));
            leftovers = List.of();
        }
        if (!leftovers.isEmpty()) {
            // Nowhere to put it: keep the debt for next time.
            info.setPendingTribute(1);
        }
        if (lord != null) {
            lord.sendSystemMessage(Component.translatable("fealty.lord.tribute", village.name(), rolls).withStyle(ChatFormatting.GOLD));
            FealtyEvents.fire(lord, FealtyEvents.TRIBUTE_COLLECTED);
        }
    }
}
