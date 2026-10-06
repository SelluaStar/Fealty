package com.selluastar.fealty.api;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A pillager stronghold a village's lord can raid: one of Fealty's pillager camps, or a pillager outpost (vanilla's,
 * or any structure in {@code #fealty:pillager_outposts}).
 *
 * @param structure the structure it belongs to
 * @param razed     whether a lord's warband razed it lately (no garrison, no illager spawns until it is reoccupied)
 * @param captives  villagers known to be held there
 */
public record Stronghold(UUID id, ResourceKey<Level> dimension, BlockPos pos, Kind kind, ResourceLocation structure, Component name,
                         boolean razed, int captives) {
    public enum Kind {
        /** A Fealty pillager camp, with a War Banner that keeps its garrison. */
        CAMP,
        /** A pillager outpost; its defenders are the illagers found there when the battle starts. */
        OUTPOST
    }
}
