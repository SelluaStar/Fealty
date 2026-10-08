package com.selluastar.fealty.api.dialogue;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Who a condition is checked for or an effect applies to: the player, and the NPC they are talking to (none when a
 * chain step or the API asks).
 *
 * @since API 1.3.0
 */
public record DialogueContext(ServerPlayer player, Optional<Entity> npc) {
    public static DialogueContext of(ServerPlayer player, @Nullable Entity npc) {
        return new DialogueContext(player, Optional.ofNullable(npc));
    }
}
