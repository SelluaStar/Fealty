package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.OpenDialoguePayload;
import com.selluastar.fealty.network.OpenScreenPayload;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestGiver;
import com.selluastar.fealty.quest.QuestGivers;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.QuestSync;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Conversations with NPCs. The server decides what an NPC says and which replies the player has; the client's
 * dialogue box only shows them and sends back the chosen reply.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class DialogueService {
    public static final String BYE = "bye";
    /** Replies are only accepted from this close. */
    private static final double REACH = 8.0;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record Session(int entityId, Set<String> options) {
    }

    private DialogueService() {
    }

    /** Start talking to an NPC. @return whether this NPC has anything to say */
    public static boolean open(ServerPlayer player, Entity npc) {
        Optional<DialogueNode> node = build(player, npc, null);
        node.ifPresent(n -> {
            show(player, npc, n);
            Speech.say(npc, n.text());
        });
        return node.isPresent();
    }

    private static Optional<DialogueNode> build(ServerPlayer player, Entity npc, @Nullable Component reply) {
        if (npc instanceof Villager villager) {
            return Optional.of(VillagerDialogue.node(player, villager, reply));
        }
        Optional<QuestGiver> giver = QuestGivers.forEntity(npc);
        return giver.map(g -> GiverDialogue.node(player, npc, g, reply));
    }

    static void show(ServerPlayer player, Entity npc, DialogueNode node) {
        Set<String> options = new HashSet<>();
        node.options().forEach(option -> {
            if (option.enabled()) {
                options.add(option.id());
            }
        });
        SESSIONS.put(player.getUUID(), new Session(npc.getId(), options));
        FealtyNetwork.send(player, new OpenDialoguePayload(npc.getId(), node));
    }

    /** Show the NPC's answer in the dialogue box (and above their head), keeping the conversation open. */
    public static void reply(ServerPlayer player, Entity npc, Component text) {
        build(player, npc, text).ifPresent(node -> show(player, npc, node));
    }

    /** Refresh the dialogue box after something changed, with the NPC's usual greeting. */
    public static void refresh(ServerPlayer player, Entity npc) {
        build(player, npc, null).ifPresent(node -> show(player, npc, node));
    }

    /** Close the dialogue box. */
    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        FealtyNetwork.send(player, new OpenScreenPayload(OpenScreenPayload.CLOSE, ""));
    }

    /** End the session without touching the client's screen (another screen is about to open). */
    public static void end(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void choose(ServerPlayer player, int entityId, String option) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId() != entityId || !session.options().contains(option)) {
            return;
        }
        Entity npc = player.level().getEntity(entityId);
        if (npc == null || !npc.isAlive() || npc.distanceToSqr(player) > REACH * REACH) {
            close(player);
            return;
        }
        if (BYE.equals(option)) {
            Speech.bark(npc, "farewell", player, 0);
            close(player);
            return;
        }
        if (option.startsWith(QUEST_PREFIX)) {
            questOption(player, npc, option.substring(QUEST_PREFIX.length()));
            return;
        }
        if (npc instanceof Villager villager) {
            VillagerDialogue.handle(player, villager, option);
        } else {
            QuestGivers.forEntity(npc).ifPresent(giver -> GiverDialogue.handle(player, npc, giver, option));
        }
    }

    // ---- Replies added by quests ----

    static final String QUEST_PREFIX = "q:";

    /** The replies the player's quests add when talking to this NPC (delivering a message, ...). */
    static List<DialogueNode.Option> questOptions(ServerPlayer player, Entity npc) {
        List<DialogueNode.Option> options = new ArrayList<>();
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            for (DialogueNode.Option option : ctx.definition().objective().dialogueOptions(ctx, npc)) {
                options.add(new DialogueNode.Option(QUEST_PREFIX + ctx.quest().instanceId() + ":" + option.id(), option.label(),
                        option.icon(), option.enabled(), option.hint()));
            }
        }
        return options;
    }

    private static void questOption(ServerPlayer player, Entity npc, String rest) {
        int split = rest.indexOf(':');
        if (split < 0) {
            return;
        }
        UUID instance;
        try {
            instance = UUID.fromString(rest.substring(0, split));
        } catch (IllegalArgumentException e) {
            return;
        }
        String id = rest.substring(split + 1);
        Optional<QuestContext> ctx = QuestManager.byInstance(player, instance);
        Optional<Component> answer = ctx.flatMap(c -> c.definition().objective().onDialogue(c, npc, id));
        if (answer.isPresent()) {
            Speech.say(npc, answer.get());
            reply(player, npc, answer.get());
            QuestSync.syncIfChanged(player);
        } else {
            refresh(player, npc);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}
