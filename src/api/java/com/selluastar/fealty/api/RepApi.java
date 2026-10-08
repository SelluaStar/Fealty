package com.selluastar.fealty.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
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

    // ---- War (since API 1.1.0) ----

    /** Every pillager stronghold Fealty knows of. */
    List<Stronghold> getStrongholds(MinecraftServer server);

    /** The nearest known stronghold within {@code radius} blocks of a position, in that level. */
    Optional<Stronghold> getNearestStronghold(ServerLevel level, BlockPos pos, double radius);

    /** The raid a lord is leading, if any. */
    Optional<Campaign> getCampaign(MinecraftServer server, UUID lord);

    /** Whether a village is enjoying the peace a razed stronghold won it (no bandit raids, no pillager patrols). */
    boolean isAtPeace(MinecraftServer server, ResourceLocation village);

    /**
     * Have a lord raise their village's warband against a stronghold, as if from the Village Hall (the lord must be in
     * the village and pays as usual; {@code CampaignEvent.Declare} still fires).
     *
     * @return why it could not start, or empty if the raid began
     */
    Optional<Component> declareRaid(ServerPlayer lord, ResourceLocation village, UUID stronghold);

    /** Whether an entity is a villager held captive in a pillager camp. */
    boolean isCaptive(Entity entity);

    /** Whether an entity marches in a lord's warband (their guards with orders, or militia). */
    boolean isWarbandMember(Entity entity);

    /** Whether an entity defends a pillager stronghold (a camp's garrison, or an outpost's illagers once a raid begins). */
    boolean isStrongholdDefender(Entity entity);

    // ---- Stories: flags, roles, villages and chains (since API 1.3.0) ----

    /**
     * A player's flag: 0 when unset. Flags are kept on the player (through death and relogging), are tested by the
     * {@code flag} dialogue condition and set by the {@code set_flag} / {@code clear_flag} effects.
     */
    int getFlag(ServerPlayer player, ResourceLocation flag);

    default boolean hasFlag(ServerPlayer player, ResourceLocation flag) {
        return getFlag(player, flag) != 0;
    }

    /** Set a flag to 1. */
    default void setFlag(ServerPlayer player, ResourceLocation flag) {
        setFlag(player, flag, 1);
    }

    /** Set a flag to a value (0 clears it). */
    void setFlag(ServerPlayer player, ResourceLocation flag, int value);

    void clearFlag(ServerPlayer player, ResourceLocation flag);

    /**
     * Make a villager one of a chain's special villagers, the way Fealty's chains name theirs: it keeps its own name,
     * shown with the title ("Ann the Stranger"), keeps its trade, and holds the role until {@link #clearRole}. A
     * single-giver chain ({@code "giver": "single"}) whose {@code giver_role} this is uses it as its giver.
     *
     * @param title the title shown with its name; null for {@code fealty.chain.role.<role>}
     * @return false if the villager already holds a role in another chain
     */
    boolean assignRole(Villager villager, ResourceLocation chain, String role, @Nullable Component title);

    /** Take a villager's role (and title) away. */
    void clearRole(Villager villager);

    /** Whether a villager holds this role in this chain. */
    boolean hasRole(Villager villager, ResourceLocation chain, String role);

    /** A loaded villager of a village that matches, nearest the village centre first. */
    Optional<Villager> findVillager(MinecraftServer server, ResourceLocation village, Predicate<Villager> predicate);

    /** A village Fealty knows of. */
    Optional<VillageInfo> getVillage(MinecraftServer server, ResourceLocation village);

    /**
     * The nearest village between {@code minDistance} and {@code maxDistance} blocks from {@code origin} (centre to
     * centre, same dimension) that matches. Villages Fealty has not seen yet are looked for too, with the vanilla
     * village structures, when no known one fits.
     */
    Optional<VillageInfo> findVillage(MinecraftServer server, GlobalPos origin, int minDistance, int maxDistance, Predicate<VillageInfo> predicate);

    /**
     * Start a quest chain for a player in a village, as a rumour would (its kind's handler and
     * {@code ChainStageEvent.Start} may refuse it).
     *
     * @return whether it started
     */
    boolean startChain(ServerPlayer player, ResourceLocation chain, ResourceLocation village);

    /** The player's stage in a chain (0 before it starts; see {@code ChainHandler}). */
    int getChainStage(ServerPlayer player, ResourceLocation chain);
}
