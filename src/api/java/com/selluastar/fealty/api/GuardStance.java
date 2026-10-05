package com.selluastar.fealty.api;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** How village guards treat a player, set per tier in data packs. */
public enum GuardStance implements StringRepresentable {
    /** Attack the player on sight. */
    ATTACK_ON_SIGHT("attack_on_sight"),
    /** Follow and warn the player; attack after any witnessed crime. */
    WATCH("watch"),
    /** Ignore the player unless they commit a crime. */
    IGNORE("ignore"),
    /** Greet the player and help them in fights. */
    ASSIST("assist"),
    /** Assist, join nearby fights and escort the player on request. */
    ESCORT("escort");

    public static final Codec<GuardStance> CODEC = StringRepresentable.fromEnum(GuardStance::values);

    private final String name;

    GuardStance(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean assists() {
        return this == ASSIST || this == ESCORT;
    }
}
