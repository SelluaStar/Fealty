package com.selluastar.fealty.api.quest;

import java.util.List;

import com.selluastar.fealty.api.FealtyApi;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Something players get quests from. Register one with a {@code DeferredRegister} on {@link #REGISTRY_KEY} to make
 * your own NPCs quest givers: talking to an entity it {@link #appliesTo} opens Fealty's dialogue box with "Any work?"
 * and the quest board. Fealty accepts, hands in and abandons the board's quests itself; you only say what is on it.
 *
 * @since API 1.3.0
 */
public interface QuestGiver {
    ResourceKey<Registry<QuestGiver>> REGISTRY_KEY = ResourceKey.createRegistryKey(FealtyApi.id("quest_giver"));

    /**
     * Whether this giver speaks for an entity. Only asked for entities Fealty has no giver of its own for (its elders,
     * the Keeper, the guild fence and named chain villagers come first).
     */
    boolean appliesTo(Entity entity);

    /** What the board shows this player right now. */
    Board board(ServerPlayer player, Entity npc);

    /** The player pressed one of the board's {@link Board#actions()}. */
    default void onAction(ServerPlayer player, Entity npc, String action) {
    }

    /**
     * A quest board.
     *
     * @param subtitle under the NPC's name
     * @param greeting what the NPC says
     * @param key      the key accepted quests are filed under, and handed in to (one per giver, such as
     *                 {@code yourmod:giver/<uuid>}, or a village faction id to share the elder's)
     * @param faction  whose reputation the quests' rewards raise
     * @param offers   quest ids ({@code rep_quests/} files) offered while there is room
     * @param perGiver how many of them the player may have at once
     * @param actions  extra buttons
     */
    record Board(Component subtitle, Component greeting, ResourceLocation key, ResourceLocation faction, List<ResourceLocation> offers,
                 int perGiver, List<Action> actions) {
        public Board {
            offers = List.copyOf(offers);
            actions = List.copyOf(actions);
            perGiver = Math.max(1, perGiver);
        }

        public static Board of(Component greeting, ResourceLocation key, ResourceLocation faction, List<ResourceLocation> offers) {
            return new Board(Component.empty(), greeting, key, faction, offers, 1, List.of());
        }
    }

    /** A button on the board (and a reply in the dialogue box). */
    record Action(String id, Component label, boolean enabled, Component hint) {
        public static Action of(String id, Component label) {
            return new Action(id, label, true, Component.empty());
        }
    }
}
