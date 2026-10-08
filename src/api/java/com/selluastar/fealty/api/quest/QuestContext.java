package com.selluastar.fealty.api.quest;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * One accepted quest, as its objective sees it.
 *
 * @since API 1.3.0
 */
public interface QuestContext {
    ServerPlayer player();

    /** The quest's id ({@code rep_quests/} file). */
    ResourceLocation questId();

    /** This accepted copy of the quest. */
    UUID instanceId();

    /** Who the quest is handed in to: a village faction, a favor key or a chain step key. */
    ResourceLocation giver();

    /** The faction whose reputation the reward raises. */
    ResourceLocation faction();

    /** The quest's title, for messages. */
    Component title();

    /** Game time the quest was accepted. */
    long startTime();

    /** The objective's own saved progress. Call {@link #dirty()} after changing it. */
    CompoundTag state();

    /** Whether the objective is met and the quest only needs handing in. */
    boolean isReady();

    /** The objective is met: quests that complete on the spot do so now, others wait to be handed in. */
    void setReady();

    /** Save after changing {@link #state()}. */
    void dirty();

    /** The level the player is in. */
    ServerLevel level();

    /** The level the quest takes place in (its village's dimension), falling back to the player's. */
    ServerLevel questLevel();

    MinecraftServer server();

    /** The village faction that gave or rewards the quest, if it is a village. */
    Optional<ResourceLocation> villageId();

    /** The explicit anchor an objective stored under {@code "anchor"}, if any. */
    Optional<BlockPos> storedAnchor();

    /** Where the quest is centred: an explicit anchor, else the village, else the player. */
    BlockPos anchor();

    /** The dimension the quest takes place in. */
    ResourceKey<Level> dimension();

    int getInt(String key);

    void putInt(String key, int value);
}
