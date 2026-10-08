package com.selluastar.fealty.data;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** Whether villagers trade with a player at a tier. */
public enum TradePolicy implements StringRepresentable {
    /** Villagers refuse to trade unless the player threatens them first. */
    REFUSE_UNLESS_THREATENED("refuse_unless_threatened"),
    /** Villagers trade normally (some high-level trades may still be locked). */
    STANDARD("standard");

    public static final Codec<TradePolicy> CODEC = StringRepresentable.fromEnum(TradePolicy::values);

    private final String name;

    TradePolicy(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
