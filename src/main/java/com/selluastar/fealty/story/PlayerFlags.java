package com.selluastar.fealty.story;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.registry.ModAttachments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Story state kept on a player (an attachment, copied on death and saved with the player): flags set by conversations,
 * rumours and other mods, the rumours they have heard, and how many conversations in a row a rumour roll missed.
 */
public final class PlayerFlags {
    public static final Codec<PlayerFlags> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("flags", Map.of()).forGetter(f -> Map.copyOf(f.flags)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("heard", List.of()).forGetter(f -> List.copyOf(f.heard)),
            Codec.INT.optionalFieldOf("rumour_misses", 0).forGetter(f -> f.rumourMisses)
    ).apply(i, (flags, heard, misses) -> {
        PlayerFlags f = new PlayerFlags();
        f.flags.putAll(flags);
        f.heard.addAll(heard);
        f.rumourMisses = misses;
        return f;
    }));

    private final Map<ResourceLocation, Integer> flags = new HashMap<>();
    private final Set<ResourceLocation> heard = new HashSet<>();
    private int rumourMisses;

    public static PlayerFlags of(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_FLAGS);
    }

    public static int get(ServerPlayer player, ResourceLocation flag) {
        return of(player).flags.getOrDefault(flag, 0);
    }

    /** Set a flag; 0 clears it. */
    public static void set(ServerPlayer player, ResourceLocation flag, int value) {
        PlayerFlags data = of(player);
        if (value == 0) {
            data.flags.remove(flag);
        } else {
            data.flags.put(flag, value);
        }
        player.setData(ModAttachments.PLAYER_FLAGS, data);
    }

    public static void clear(ServerPlayer player, ResourceLocation flag) {
        set(player, flag, 0);
    }

    public Map<ResourceLocation, Integer> flags() {
        return Map.copyOf(flags);
    }

    public boolean hasHeard(ResourceLocation rumour) {
        return heard.contains(rumour);
    }

    public void markHeard(ResourceLocation rumour) {
        heard.add(rumour);
    }

    public void forget(ResourceLocation rumour) {
        heard.remove(rumour);
    }

    /** Conversations in a row (with a rumour to tell) whose roll came up empty. */
    public int rumourMisses() {
        return rumourMisses;
    }

    public void setRumourMisses(int misses) {
        this.rumourMisses = Math.max(0, misses);
    }
}
