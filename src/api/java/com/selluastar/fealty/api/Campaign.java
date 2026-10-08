package com.selluastar.fealty.api;

import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

/**
 * A lord's raid on a pillager stronghold, from the day it is declared until it is won or lost.
 *
 * @param lord        the lord leading it
 * @param village     the village whose warband marches
 * @param stronghold  the target ({@link Stronghold#id()})
 * @param declaredDay the Fealty day it was declared
 */
public record Campaign(UUID lord, ResourceLocation village, UUID stronghold, Phase phase, long declaredDay) {
    public enum Phase {
        /** On the way: the warband gathers as the lord nears the stronghold. */
        MARCHING,
        /** Fighting at the stronghold. */
        BATTLE
    }
}
