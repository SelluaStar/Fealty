package com.selluastar.fealty.war;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** What makes one stronghold harder than others of its kind. Each trait but {@link #NONE} adds a skull of threat. */
public enum StrongholdTrait implements StringRepresentable {
    NONE("none"),
    /** An armoured garrison with enchanted crossbows and axes. */
    VETERANS("veterans"),
    /** An evoker leads them. */
    EVOKER("evoker"),
    /** They keep a ravager. */
    BEASTS("beasts");

    public static final Codec<StrongholdTrait> CODEC = StringRepresentable.fromEnum(StrongholdTrait::values);

    private final String name;

    StrongholdTrait(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int threat() {
        return this == NONE ? 0 : 1;
    }

    public static StrongholdTrait byName(String name) {
        for (StrongholdTrait trait : values()) {
            if (trait.name.equals(name)) {
                return trait;
            }
        }
        return NONE;
    }
}
