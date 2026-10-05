package com.selluastar.fealty.crime;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/** Positions in a chunk where a player placed a block inside a village. */
public final class PlacedBlocks {
    public static final Codec<PlacedBlocks> CODEC = Codec.LONG.listOf().xmap(PlacedBlocks::new, PlacedBlocks::toList);

    private final LongOpenHashSet positions = new LongOpenHashSet();

    public PlacedBlocks() {
    }

    private PlacedBlocks(List<Long> list) {
        list.forEach(positions::add);
    }

    private List<Long> toList() {
        List<Long> list = new ArrayList<>(positions.size());
        positions.forEach(list::add);
        return list;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public boolean add(BlockPos pos) {
        return positions.add(pos.asLong());
    }

    public boolean remove(BlockPos pos) {
        return positions.remove(pos.asLong());
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }
}
