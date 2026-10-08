package com.selluastar.fealty.crime;

import com.selluastar.fealty.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Remembers blocks players placed inside villages, so taking back your own things is not a crime. */
public final class PlacedBlockTracker {
    private PlacedBlockTracker() {
    }

    public static void markPlaced(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.getData(ModAttachments.PLACED_BLOCKS).add(pos)) {
            chunk.setUnsaved(true);
        }
    }

    public static boolean isPlaced(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.hasData(ModAttachments.PLACED_BLOCKS) && chunk.getData(ModAttachments.PLACED_BLOCKS).contains(pos);
    }

    /** Forget a placed block (it was broken). @return whether it had been placed by a player */
    public static boolean removeIfPlaced(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!chunk.hasData(ModAttachments.PLACED_BLOCKS)) {
            return false;
        }
        boolean removed = chunk.getData(ModAttachments.PLACED_BLOCKS).remove(pos);
        if (removed) {
            chunk.setUnsaved(true);
        }
        return removed;
    }
}
