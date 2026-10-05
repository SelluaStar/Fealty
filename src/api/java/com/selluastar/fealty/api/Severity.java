package com.selluastar.fealty.api;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * How serious a crime is. Decides how guards respond, and minor crimes cost half the usual reputation.
 */
public enum Severity implements StringRepresentable {
    /** Half penalty; guards warn first, then attack on a repeat offence. */
    MINOR("minor", 0.5F),
    /** Full penalty; guards warn first, then attack on a repeat offence. */
    MODERATE("moderate", 1.0F),
    /** Full penalty; guards attack at once. */
    SEVERE("severe", 1.0F);

    public static final Codec<Severity> CODEC = StringRepresentable.fromEnum(Severity::values);

    private final String name;
    private final float multiplier;

    Severity(String name, float multiplier) {
        this.name = name;
        this.multiplier = multiplier;
    }

    public float multiplier() {
        return multiplier;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
