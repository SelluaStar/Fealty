package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.network.QuestActionPayload;
import com.selluastar.fealty.quest.type.RestoreElderObjective;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** The trusting elder's screen: redemption quests, restoring broken neighbours, rumours and lordship. */
public final class ElderGiver implements QuestGiver {
    public static final ElderGiver INSTANCE = new ElderGiver();

    private ElderGiver() {
    }

    private static Optional<VillageRecord> village(ServerPlayer player, Entity entity) {
        if (entity instanceof VillageElderEntity elder && elder.village() != null) {
            return FealtyWorldData.get(player.server).village(elder.village());
        }
        return Optional.empty();
    }

    @Override
    public OpenQuestScreenPayload screen(ServerPlayer player, Entity entity) {
        Optional<VillageRecord> village = village(player, entity);
        if (village.isEmpty()) {
            return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), Component.empty(),
                    Component.translatable("fealty.elder.lost"), 0, false, List.of(), List.of());
        }
        VillageRecord record = village.get();
        RepManager.meet(player, record.id());
        int rep = RepManager.getRep(player, record.id());
        RepTier tier = RepManager.tierOf(rep);
        boolean lord = record.lord().isLord(player.getUUID());
        String greetingKey = lord ? "fealty.elder.greet.lord" : "fealty.elder.greet." + tier.id().getPath();
        Component greeting = Component.translatableWithFallback(greetingKey, "", player.getDisplayName(), record.name());

        int limit = FealtyConfig.ELDER_QUESTS_AT_ONCE.get();
        List<OpenQuestScreenPayload.QuestEntry> quests = new ArrayList<>(
                QuestGivers.entries(player, record.id(), QuestManager.offers(player, record), limit));
        List<QuestContext> running = QuestManager.contexts(player, record.id());
        boolean restoring = running.stream().anyMatch(c -> c.definition().pool().equals(RepQuestDefinition.RESTORE));
        if (running.size() < limit && !restoring) {
            Optional<VillageRecord> broken = RestoreElderObjective.brokenNear(player.server, record);
            if (broken.isPresent()) {
                restoreQuest().flatMap(QuestManager::offerEntry).ifPresent(entry -> quests.add(
                        entry.withDescription(Component.translatable("fealty.quest.restore.offer", broken.get().name()))));
            }
        }

        List<ActionEntry> actions = new ArrayList<>();
        ChainManager.rumoursAction(player, record).ifPresent(actions::add);
        LordshipManager.elderActions(player, record, actions);

        return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(),
                Component.translatable("fealty.elder.subtitle", record.name()), greeting, rep, true, quests, actions);
    }

    private static Optional<ResourceLocation> restoreQuest() {
        for (Map.Entry<ResourceLocation, RepQuestDefinition> entry : FealtyDataManager.quests().entrySet()) {
            if (entry.getValue().pool().equals(RepQuestDefinition.RESTORE)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    @Override
    public void handleAction(ServerPlayer player, Entity entity, String action, String argument) {
        Optional<VillageRecord> village = village(player, entity);
        if (village.isEmpty()) {
            return;
        }
        VillageRecord record = village.get();
        switch (action) {
            case QuestActionPayload.ACCEPT -> {
                ResourceLocation id = ResourceLocation.tryParse(argument);
                if (id == null) {
                    return;
                }
                Optional<RepQuestDefinition> def = FealtyDataManager.quest(id);
                boolean offered = QuestManager.offers(player, record).contains(id)
                        || (def.isPresent() && def.get().pool().equals(RepQuestDefinition.RESTORE)
                        && RestoreElderObjective.brokenNear(player.server, record).isPresent());
                if (offered) {
                    QuestManager.accept(player, record.id(), record.id(), id, giverState(entity), FealtyConfig.ELDER_QUESTS_AT_ONCE.get());
                }
            }
            case QuestActionPayload.TURN_IN -> QuestManager.turnIn(player, record.id(), ResourceLocation.tryParse(argument));
            case QuestActionPayload.ABANDON -> QuestManager.abandon(player, record.id(), ResourceLocation.tryParse(argument), true);
            case ChainManager.ACTION_RUMOURS -> ChainManager.startFromElder(player, record);
            default -> LordshipManager.handleElderAction(player, record, action);
        }
    }

    /** Where the quest was given and by whom, so the tracker can point the player back. */
    static CompoundTag giverState(Entity entity) {
        CompoundTag state = new CompoundTag();
        state.put("giver_pos", net.minecraft.nbt.NbtUtils.writeBlockPos(entity.blockPosition()));
        state.putString("giver_name", entity.getDisplayName().getString());
        return state;
    }

    /** Whether the player carries a Royal Writ (shown so the elder can offer fealty). */
    public static boolean hasWrit(ServerPlayer player) {
        return com.selluastar.fealty.util.Inventories.has(player, ModItems.ROYAL_WRIT.get());
    }
}
