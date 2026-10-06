package com.selluastar.fealty.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.Campaign;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.OpenHallPayload;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Inventories;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** The Village Hall's War tab: what it shows the lord, and what they can do from it. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class WarTable {
    /** The strongholds last shown to each lord, so a row number picks the same one back. */
    private static final Map<UUID, List<UUID>> SHOWN = new HashMap<>();

    private WarTable() {
    }

    /** Known strongholds within reach of the village, nearest first. */
    public static List<Strongholds.Entry> targets(MinecraftServer server, VillageRecord village) {
        return Strongholds.get(server).near(village.dimension(), village.center(), FealtyConfig.WAR_RANGE.get());
    }

    public static OpenHallPayload.War build(ServerPlayer lord, VillageRecord village) {
        MinecraftServer server = lord.server;
        long day = RepManager.day(server);
        Optional<Campaign> campaign = Campaigns.of(server, lord.getUUID());
        List<Strongholds.Entry> targets = targets(server, village);
        List<OpenHallPayload.StrongholdRow> rows = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        BlockPos center = village.center();
        for (Strongholds.Entry entry : targets) {
            if (rows.size() >= 32) {
                break;
            }
            int dx = entry.pos().getX() - center.getX();
            int dz = entry.pos().getZ() - center.getZ();
            int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            int bearing = (int) Math.round((Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0);
            boolean target = campaign.map(c -> c.stronghold().equals(entry.id())).orElse(false);
            rows.add(new OpenHallPayload.StrongholdRow(entry.name(), entry.kind() == Stronghold.Kind.CAMP ? 0 : 1, distance, bearing,
                    entry.captives(), entry.razedDays(day), target));
            ids.add(entry.id());
        }
        SHOWN.put(lord.getUUID(), ids);
        Component name = Component.empty();
        int phase = 0;
        int distance = 0;
        if (campaign.isPresent()) {
            Optional<Strongholds.Entry> target = Strongholds.get(server).get(campaign.get().stronghold());
            if (target.isPresent()) {
                name = target.get().name();
                distance = lord.level().dimension().equals(target.get().dimension())
                        ? (int) Math.round(Math.sqrt(Strongholds.flatDistSqr(lord.blockPosition(), target.get().pos()))) : -1;
            }
            phase = campaign.get().phase() == Campaign.Phase.BATTLE ? 2 : 1;
        }
        int peace = village.sites().atPeace(day) ? (int) (village.sites().peaceUntil() - day) : 0;
        return new OpenHallPayload.War(rows, Campaigns.guardsReady(village), Campaigns.levySize(village), FealtyConfig.RAID_COST_EMERALDS.get(),
                FealtyConfig.SCOUT_COST_EMERALDS.get(), Campaigns.cooldown(server, village), village.sites().lastScoutDay() >= day,
                name, phase, distance, peace);
    }

    /** Send scouts out: once a day, for a few emeralds. */
    public static void scout(ServerPlayer lord, VillageRecord village) {
        long day = RepManager.day(lord.server);
        if (village.sites().lastScoutDay() >= day) {
            lord.displayClientMessage(Component.translatable("fealty.war.scouted_today"), true);
            return;
        }
        int cost = FealtyConfig.SCOUT_COST_EMERALDS.get();
        if (!lord.isCreative() && cost > 0 && !Inventories.take(lord, Items.EMERALD, cost)) {
            lord.displayClientMessage(Component.translatable("fealty.war.scout_cost", cost).withStyle(ChatFormatting.RED), true);
            return;
        }
        village.sites().setLastScoutDay(day);
        com.selluastar.fealty.rep.FealtyWorldData.get(lord.server).setDirty();
        List<Strongholds.Entry> found = Scouting.scout(lord.serverLevel(), village);
        if (found.isEmpty()) {
            lord.sendSystemMessage(Component.translatable(targets(lord.server, village).isEmpty() ? "fealty.war.scouts_none" : "fealty.war.scouts_nothing_new",
                    village.name()).withStyle(ChatFormatting.GRAY));
        } else {
            lord.sendSystemMessage(Component.translatable("fealty.war.scouts_found", found.size(), village.name()).withStyle(ChatFormatting.GOLD));
            for (Strongholds.Entry entry : found) {
                lord.sendSystemMessage(Component.literal(" - ").append(entry.name()).withStyle(ChatFormatting.GOLD));
            }
        }
        Feedback.sound(lord, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 1.0F);
    }

    /** Raise the warband against the stronghold in a row of the War tab. */
    public static void declare(ServerPlayer lord, VillageRecord village, int row, boolean inVillage) {
        List<UUID> shown = SHOWN.getOrDefault(lord.getUUID(), List.of());
        if (row < 0 || row >= shown.size()) {
            return;
        }
        Optional<Strongholds.Entry> target = Strongholds.get(lord.server).get(shown.get(row));
        if (target.isEmpty()) {
            return;
        }
        Campaigns.declare(lord, village, target.get(), inVillage)
                .ifPresent(problem -> lord.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SHOWN.remove(event.getEntity().getUUID());
    }
}
