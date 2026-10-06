package com.selluastar.fealty.war;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/** Names for pillager strongholds, the same every time for the same place. */
public final class StrongholdNames {
    private static final String[] FIRST = {
            "Black", "Grim", "Ash", "Raven", "Iron", "Grey", "Bitter", "Crow", "Wolf", "Thorn", "Red", "Hollow", "Cinder",
            "Gallows", "Dread", "Scald", "Rook", "Mire", "Bleak", "Gore"
    };
    private static final String[] SECOND = {
            "fang", "hollow", "watch", "maw", "spur", "ridge", "fell", "mire", "cairn", "brand", "moor", "tooth", "hold",
            "scar", "reach", "gate"
    };

    private StrongholdNames() {
    }

    /** A name such as "Blackfang", seeded by the stronghold's position. */
    public static String stem(BlockPos pos) {
        RandomSource random = RandomSource.create(pos.asLong() * 31L + 7L);
        String first = FIRST[random.nextInt(FIRST.length)];
        return first + SECOND[random.nextInt(SECOND.length)];
    }
}
