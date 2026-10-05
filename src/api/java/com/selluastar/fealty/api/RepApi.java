package com.selluastar.fealty.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Read and change reputation without touching Fealty internals.
 *
 * <p>Reputation is stored per player per faction. Villages use faction ids in the
 * {@code village} namespace (for example {@code village:minecraft/overworld/12_-40});
 * other groups use their own ids such as {@code fealty:bandits}.
 *
 * <p>All methods must be called on the logical server thread.
 */
public interface RepApi {

    // ---- Methods from the design document (stable since 1.0.0) ----

    /** Current reputation of the player with the faction, in the configured range (default -100..100). */
    int getRep(ServerPlayer player, ResourceLocation faction);

    /** The tier the player's reputation with the faction falls in. */
    RepTier getTier(ServerPlayer player, ResourceLocation faction);

    /**
     * Change reputation. Fires {@link com.selluastar.fealty.api.event.RepChangeEvent.Pre}, which other mods
     * may cancel or adjust, then {@link com.selluastar.fealty.api.event.RepChangeEvent.Post}.
     *
     * @param reason a rep source id (see {@link RepSources}); unknown ids are treated as generic changes
     */
    void addRep(ServerPlayer player, ResourceLocation faction, int amount, ResourceLocation reason);

    /** Price multiplier the villager applies to this player (1.0 = normal prices). */
    float priceMultiplier(ServerPlayer player, Villager villager);

    /** Whether the faction is actively hunting the player (heat at or above the wanted threshold). */
    boolean isWanted(ServerPlayer player, ResourceLocation faction);

    // ---- Extensions ----

    /** Reputation of a possibly offline player. */
    int getRep(MinecraftServer server, UUID player, ResourceLocation faction);

    /** Set reputation directly, firing the same events as {@link #addRep}. */
    void setRep(ServerPlayer player, ResourceLocation faction, int value, ResourceLocation reason);

    /**
     * Apply a registered rep source (for example {@link RepSources#GIFT}) using the amount configured
     * for it in data packs, including daily caps and the "negative rep never heals" rule.
     *
     * @return the change that was actually applied
     */
    int applySource(ServerPlayer player, ResourceLocation faction, ResourceLocation source);

    /** The player's global Renown, carried between villages. */
    int getRenown(ServerPlayer player);

    /** The tier the player's Renown falls in. */
    RepTier getRenownTier(ServerPlayer player);

    /** Heat the player has built up with a faction by staying at the lowest tier. */
    int getHeat(ServerPlayer player, ResourceLocation faction);

    /** The faction an entity belongs to (village, wanderers, bandits, ...), if any. */
    Optional<ResourceLocation> getFactionOf(Entity entity);

    /** The village faction that owns this position, if any. */
    Optional<ResourceLocation> getFactionAt(ServerLevel level, BlockPos pos);

    /** The sworn lord of a village faction, if it has one. */
    Optional<UUID> getLord(MinecraftServer server, ResourceLocation village);

    /** All tiers currently loaded from data packs, from lowest to highest. */
    List<RepTier> getTiers();

    /** Look up a tier by id, for example {@link RepTiers#TRUSTED}. */
    Optional<RepTier> getTier(ResourceLocation tierId);
}
