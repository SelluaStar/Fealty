package com.selluastar.fealty.api;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A village Fealty knows of.
 *
 * @param id       its faction id
 * @param hasElder whether it has a living elder
 * @param lord     its sworn lord, if any
 * @param broken   whether it has lost its elder for good (until restored)
 * @since API 1.3.0
 */
public record VillageInfo(ResourceLocation id, String name, ResourceKey<Level> dimension, BlockPos center, BoundingBox bounds,
                          boolean hasElder, Optional<UUID> lord, boolean broken) {
    public GlobalPos position() {
        return GlobalPos.of(dimension, center);
    }
}
