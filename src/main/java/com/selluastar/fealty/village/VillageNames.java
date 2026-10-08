package com.selluastar.fealty.village;

import net.minecraft.util.RandomSource;

/** Medieval-sounding names for villages and the people in them. */
public final class VillageNames {
    private static final String[] PREFIXES = {
            "Ash", "Bram", "Oak", "Thorn", "Wolf", "Raven", "Elder", "Mill", "Stone", "Hollow", "Bright", "Marl",
            "Wick", "Fen", "Hart", "Kings", "Queens", "Black", "White", "Red", "Green", "Dun", "Pen", "Cold", "Hay",
            "Barrow", "Ember", "Frost", "Glen", "Iron", "Lark", "Moss", "North", "Oxen", "Pike", "Rook", "Swan",
            "Tall", "Under", "Wester", "Yew", "Alder", "Briar", "Crow", "Dove", "Elm", "Gold", "Holly", "Lynn"
    };
    private static final String[] SUFFIXES = {
            "ford", "shire", "wick", "ton", "bury", "stead", "holm", "field", "dale", "moor", "mere", "haven", "by",
            "thorpe", "worth", "ham", "gate", "brook", "crest", "fell", "cross", "hold", "keep", "march", "well"
    };
    private static final String[] FIRST_NAMES = {
            "Aldric", "Bertram", "Cedric", "Dunstan", "Edmund", "Godfrey", "Harold", "Isolde", "Jocelyn", "Leofric",
            "Matilda", "Osric", "Rowena", "Sigrid", "Tristan", "Ulric", "Wynn", "Aelfric", "Bryony", "Cuthbert",
            "Eadith", "Gisela", "Hilda", "Ivo", "Mildred", "Odo", "Petronella", "Roderick", "Sabine", "Wulfstan"
    };
    private static final String[] EPITHETS = {
            "the Wise", "the Elder", "Greybeard", "the Patient", "the Just", "of the Hall", "the Old", "the Kind"
    };

    private VillageNames() {
    }

    public static String villageName(long seed) {
        RandomSource random = RandomSource.create(seed);
        return PREFIXES[random.nextInt(PREFIXES.length)] + SUFFIXES[random.nextInt(SUFFIXES.length)];
    }

    public static String personName(RandomSource random) {
        return FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
    }

    public static String elderName(RandomSource random) {
        return personName(random) + " " + EPITHETS[random.nextInt(EPITHETS.length)];
    }
}
