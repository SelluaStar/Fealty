package com.selluastar.fealty.story.script;

import java.util.List;
import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.dialogue.DialogueCondition;
import com.selluastar.fealty.api.dialogue.DialogueContext;
import com.selluastar.fealty.api.dialogue.DialogueEffect;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageTracker;

import net.minecraft.resources.ResourceLocation;

/** Running conditions and effects from conversations, rumours and chain steps. */
public final class Scripts {
    private Scripts() {
    }

    /** Whether an optional condition passes (no condition always does). A condition that throws counts as failing. */
    public static boolean test(Optional<DialogueCondition> condition, DialogueContext ctx) {
        return condition.isEmpty() || test(condition.get(), ctx);
    }

    public static boolean test(DialogueCondition condition, DialogueContext ctx) {
        try {
            return condition.test(ctx);
        } catch (RuntimeException e) {
            Fealty.LOGGER.error("Fealty: dialogue condition {} failed", condition, e);
            return false;
        }
    }

    /** Apply effects in order; one that throws is logged and skipped. */
    public static void apply(List<DialogueEffect> effects, DialogueContext ctx) {
        for (DialogueEffect effect : effects) {
            try {
                effect.apply(ctx);
            } catch (RuntimeException e) {
                Fealty.LOGGER.error("Fealty: dialogue effect {} failed", effect, e);
            }
        }
    }

    /** The faction a conversation is with: the NPC's, else the village the player stands in. */
    public static Optional<ResourceLocation> faction(DialogueContext ctx) {
        return ctx.npc().flatMap(FactionResolver::factionOf).or(() -> VillageTracker.currentVillage(ctx.player()));
    }

    /** The village a conversation is in, if its faction is one. */
    public static Optional<VillageRecord> village(DialogueContext ctx) {
        return faction(ctx).filter(Factions::isVillage).flatMap(id -> FealtyWorldData.get(ctx.player().server).village(id));
    }
}
