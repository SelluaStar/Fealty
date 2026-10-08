package com.selluastar.fealty.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;

/** What a damaged wall turns into during a rebuild quest. Break it and put the right block back. */
public class RubbleBlock extends Block {
    public static final MapCodec<RubbleBlock> CODEC = simpleCodec(RubbleBlock::new);

    public RubbleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
