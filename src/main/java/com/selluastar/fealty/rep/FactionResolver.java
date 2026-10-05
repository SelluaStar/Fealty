package com.selluastar.fealty.rep;

import java.util.Optional;

import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

/** Decides which faction an entity belongs to. */
public final class FactionResolver {
    private FactionResolver() {
    }

    public static Optional<ResourceLocation> factionOf(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        if (entity.hasData(ModAttachments.FACTION)) {
            return Optional.of(entity.getData(ModAttachments.FACTION));
        }
        if (entity instanceof Villager villager) {
            return Optional.of(VillageResolver.villageOf(villager).map(VillageRecord::id).orElse(Factions.WANDERERS));
        }
        if (entity instanceof WanderingTrader) {
            return Optional.of(Factions.WANDERERS);
        }
        if (entity.getType().is(FealtyTags.Entities.BANDITS)) {
            return Optional.of(Factions.BANDITS);
        }
        if (entity.getType().is(FealtyTags.Entities.GUARDS) || entity.getType().is(FealtyTags.Entities.VILLAGE_MEMBERS)) {
            return VillageResolver.villageAt(level, entity.blockPosition()).map(VillageRecord::id);
        }
        return Optional.empty();
    }

    /** Whether the entity guards its village. */
    public static boolean isGuard(Entity entity) {
        return entity.getType().is(FealtyTags.Entities.GUARDS);
    }

    /** Whether the entity can witness crimes against its faction. */
    public static boolean canWitness(Entity entity) {
        return entity instanceof Villager || entity.getType().is(FealtyTags.Entities.GUARDS)
                || entity.getType().is(FealtyTags.Entities.VILLAGE_MEMBERS) || entity.getType().is(FealtyTags.Entities.BANDITS)
                || entity.hasData(ModAttachments.FACTION);
    }
}
