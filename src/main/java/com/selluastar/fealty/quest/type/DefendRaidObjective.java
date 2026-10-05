package com.selluastar.fealty.quest.type;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.GameRules;

/**
 * {@code fealty:defend_raid}: hold the village through a raid. Accepting gives the player a Raid Omen centred on
 * the village, so a vanilla raid begins shortly after; winning it (Hero of the Village) completes the quest.
 */
public record DefendRaidObjective(int omenLevel) implements QuestObjective {
    public static final MapCodec<DefendRaidObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 4).optionalFieldOf("omen_level", 0).forGetter(DefendRaidObjective::omenLevel)
    ).apply(i, DefendRaidObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.DEFEND_RAID.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        Optional<VillageRecord> village = ctx.village();
        if (village.isEmpty() || ctx.level().getDifficulty() == Difficulty.PEACEFUL
                || ctx.level().getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)
                || !ctx.level().dimensionType().hasRaids()) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.defend_raid.no_raid"));
            return false;
        }
        ctx.player().setRaidOmenPosition(village.get().center());
        ctx.player().addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, omenLevel));
        ctx.state().putString("village", village.get().id().toString());
        return true;
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        if (ctx.quest().isReady()) {
            return List.of(Component.translatable("fealty.quest.defend_raid.won"));
        }
        boolean seen = ctx.state().getBoolean("seen_raid");
        return List.of(Component.translatable(seen ? "fealty.quest.defend_raid.fighting" : "fealty.quest.defend_raid.gathering"));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.defend_raid.preview"));
    }

    @Override
    public void tick(QuestContext ctx) {
        if (ctx.quest().isReady()) {
            return;
        }
        Optional<VillageRecord> village = ctx.village();
        if (village.isEmpty() || !village.get().dimension().equals(ctx.level().dimension())) {
            return;
        }
        Raid raid = ctx.level().getRaidAt(village.get().center());
        if (raid != null) {
            if (!ctx.state().getBoolean("seen_raid")) {
                ctx.state().putBoolean("seen_raid", true);
                ctx.dirty();
            }
            if (raid.isLoss()) {
                QuestManager.fail(ctx, RepQuestEvent.Reason.TARGET_LOST);
            }
        } else if (ctx.state().getBoolean("seen_raid")
                || (ctx.level().getGameTime() - ctx.quest().startTime() > 2400 && !ctx.player().hasEffect(MobEffects.RAID_OMEN))) {
            // The raid ended without a victory for this player, or never began.
            QuestManager.fail(ctx, RepQuestEvent.Reason.TARGET_LOST);
        }
    }

    @Override
    public void onRaidVictory(QuestContext ctx, VillageRecord village) {
        if (village.id().toString().equals(ctx.state().getString("village"))) {
            ctx.setReady();
        }
    }
}
