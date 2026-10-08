package com.selluastar.fealty.chatter;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * Something villagers talk about, or tell a player who asks.
 *
 * @param lines  what they say to each other in speech bubbles, taking turns (empty for a rumour told to one player)
 * @param tell   what a villager says when asked what the talk was about
 * @param useful real news rather than small talk
 * @param weight how likely it is against the others
 * @param reveal what else happens when the player is told (a stronghold noted, a villager made to glow), or null
 */
public record Topic(ResourceLocation id, List<Component> lines, Component tell, boolean useful, int weight, @Nullable Reveal reveal) {

    /** What telling a player does besides saying it. */
    @FunctionalInterface
    public interface Reveal {
        void run(ServerPlayer player, ServerLevel level, Villager teller);
    }

    public Topic withTell(Component newTell) {
        return new Topic(id, lines, newTell, useful, weight, reveal);
    }
}
