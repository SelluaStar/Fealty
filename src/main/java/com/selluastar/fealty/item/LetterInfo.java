package com.selluastar.fealty.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** The seal on a courier quest's letter: where it is from, where it goes, and whose quest it is. */
public record LetterInfo(ResourceLocation from, String fromName, ResourceLocation to, String toName, BlockPos toPos,
                         UUID owner, UUID quest) {
    public static final Codec<LetterInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("from").forGetter(LetterInfo::from),
            Codec.STRING.fieldOf("from_name").forGetter(LetterInfo::fromName),
            ResourceLocation.CODEC.fieldOf("to").forGetter(LetterInfo::to),
            Codec.STRING.fieldOf("to_name").forGetter(LetterInfo::toName),
            BlockPos.CODEC.fieldOf("to_pos").forGetter(LetterInfo::toPos),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(LetterInfo::owner),
            UUIDUtil.CODEC.fieldOf("quest").forGetter(LetterInfo::quest)
    ).apply(i, LetterInfo::new));

    public static final StreamCodec<ByteBuf, LetterInfo> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
