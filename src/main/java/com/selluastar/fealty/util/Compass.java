package com.selluastar.fealty.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Compass points for text: "to the north-east", from one place to another. */
public final class Compass {
    private static final String[] POINTS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private Compass() {
    }

    /** The compass point (as a name, {@code "ne"}) of {@code to} seen from {@code from}. */
    public static String point(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        return POINTS[Math.floorMod(Math.round((float) (bearing / 45.0)), 8)];
    }

    /** Its short name for a message, such as "NE" (the {@code fealty.compass.*} lang keys). */
    public static Component name(BlockPos from, BlockPos to) {
        return Component.translatable("fealty.compass." + point(from, to));
    }

    /** Distance across the ground, rounded to the nearest 50 blocks (at least 50), for rumours that are only roughly right. */
    public static int roughDistance(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.max(50, (int) (Math.round(Math.sqrt(dx * dx + dz * dz) / 50.0) * 50));
    }
}
