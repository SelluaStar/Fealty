package com.selluastar.fealty.dialogue;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** What a line's conditions are checked against: who is speaking, to whom, and where. */
public record SpeakerContext(@Nullable ResourceLocation tier, @Nullable ResourceLocation profession, ResourceLocation entityType,
                             boolean lord, boolean broken, boolean night) {

    public static SpeakerContext of(Entity speaker, @Nullable ServerPlayer listener) {
        ResourceLocation tier = null;
        boolean lord = false;
        boolean broken = false;
        Optional<ResourceLocation> faction = FactionResolver.factionOf(speaker);
        if (faction.isPresent() && listener != null) {
            tier = RepManager.getTier(listener, faction.get()).id();
            if (Factions.isVillage(faction.get())) {
                Optional<VillageRecord> village = FealtyWorldData.get(listener.server).village(faction.get());
                lord = village.map(v -> v.lord().isLord(listener.getUUID())).orElse(false);
                broken = village.map(VillageRecord::isBroken).orElse(false);
            }
        }
        ResourceLocation profession = speaker instanceof Villager villager
                ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()) : null;
        boolean night = speaker.level().isNight();
        return new SpeakerContext(tier, profession, BuiltInRegistries.ENTITY_TYPE.getKey(speaker.getType()), lord, broken, night);
    }
}
