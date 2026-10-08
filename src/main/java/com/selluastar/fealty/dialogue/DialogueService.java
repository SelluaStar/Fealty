package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.dialogue.DialogueOption;
import com.selluastar.fealty.api.dialogue.DialogueReply;
import com.selluastar.fealty.api.event.DialogueBuildEvent;
import com.selluastar.fealty.api.event.DialogueEvent;
import com.selluastar.fealty.chatter.Chatter;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.OpenDialoguePayload;
import com.selluastar.fealty.network.OpenScreenPayload;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestGivers;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.QuestSync;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Conversations with NPCs. The server decides what an NPC says and which replies the player has; the client's
 * dialogue box only shows them and sends back the chosen reply. While a conversation (or a quest board opened from
 * one) is open, the NPC stays where it is and faces the player: villagers and traders through the same hold vanilla
 * uses while trading, Fealty's own NPCs through their talk goal.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class DialogueService {
    public static final String BYE = "bye";
    /** Sent by the client when the player closes the dialogue box themselves. */
    public static final String LEAVE = "leave";
    /** Sent by the client when the player closes a quest board opened from (or instead of) a conversation. */
    public static final String LEAVE_BOARD = "leave_board";
    /** Replies are only accepted from this close. */
    private static final double REACH = 8.0;
    /** A conversation left alone this long ends (ticks). */
    private static final long IDLE_TICKS = 20L * 60 * 3;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Who each NPC (by entity id) is talking to. */
    private static final Map<Integer, UUID> LISTENERS = new HashMap<>();

    private static final class Session {
        final int entityId;
        final ResourceKey<Level> dimension;
        Set<String> options = Set.of();
        long lastActive;
        /** Whether the NPC's quest board is open rather than the dialogue box. */
        boolean board;
        /** The conversation ({@code fealty/conversations/}) going on, and its node. */
        @Nullable
        ResourceLocation conversation;
        String node = "";
        /** Conversations offered as replies ({@code t:<n>}). */
        List<ResourceLocation> offered = List.of();
        /** Replies other mods added, with what to call when picked. */
        Map<String, DialogueBuildEvent.Handler> contributed = Map.of();
        /** What the NPC answered while handling an action, to show instead of the greeting. */
        @Nullable
        Component answer;

        Session(int entityId, ResourceKey<Level> dimension) {
            this.entityId = entityId;
            this.dimension = dimension;
        }
    }

    private DialogueService() {
    }

    /**
     * Start talking to an NPC: a conversation from {@code fealty/conversations/} that takes over, else their usual
     * dialogue. {@code DialogueEvent.Open} may keep the box shut. @return whether this NPC has anything to say
     */
    public static boolean open(ServerPlayer player, Entity npc) {
        if (NeoForge.EVENT_BUS.post(new DialogueEvent.Open(player, npc)).isCanceled()) {
            return false;
        }
        Optional<Map.Entry<ResourceLocation, Conversation>> conversation = Conversations.takeover(player, npc);
        if (conversation.isPresent()) {
            Conversations.begin(player, npc, conversation.get().getKey());
            return true;
        }
        Optional<Built> node = build(player, npc, null);
        node.ifPresent(n -> {
            show(player, npc, n, null, "");
            Speech.say(npc, n.node().text());
        });
        return node.isPresent();
    }

    /** A node, with the replies other mods added ({@link DialogueBuildEvent}) and the conversations offered in it. */
    record Built(DialogueNode node, Map<String, DialogueBuildEvent.Handler> contributed, List<ResourceLocation> offered) {
    }

    private static Optional<Built> build(ServerPlayer player, Entity npc, @Nullable Component reply) {
        Optional<Built> built;
        if (npc instanceof Villager villager) {
            built = Optional.of(VillagerDialogue.node(player, villager, reply));
        } else if (npc instanceof Mob guard && GuardManager.talksLikeGuard(npc)) {
            built = Optional.of(contribute(player, guard, GuardDialogue.node(player, guard, reply), reply != null));
        } else {
            built = QuestGivers.forEntity(npc).map(g -> GiverDialogue.node(player, npc, g, reply));
        }
        if (built.isEmpty() && !Conversations.offers(player, npc).isEmpty()) {
            // Someone with nothing to say but the conversations offered.
            built = Optional.of(contribute(player, npc, new DialogueNode(npc.getDisplayName(), subtitle(player, npc),
                    reply != null ? reply : Component.translatable("fealty.dialogue.fallback"),
                    List.of(DialogueNode.Option.of(BYE, Component.translatable("fealty.dialogue.option.bye"), "door"))), reply != null));
        }
        return built.map(b -> withOffers(player, npc, b));
    }

    /**
     * Fire {@link DialogueBuildEvent} for an NPC's usual dialogue (from {@code VillagerDialogue.node},
     * {@code GiverDialogue.node} and for guards): the lines other mods add go under what the NPC says, their replies
     * among the NPC's, remembered with the handler to call when one is picked.
     */
    static Built contribute(ServerPlayer player, Entity npc, DialogueNode node, boolean answer) {
        List<DialogueNode.Option> options = new ArrayList<>(node.options());
        DialogueBuildEvent event = NeoForge.EVENT_BUS.post(new DialogueBuildEvent(player, npc, answer));
        Map<String, DialogueBuildEvent.Handler> contributed = new HashMap<>();
        for (DialogueBuildEvent.Contribution contribution : event.getOptions()) {
            DialogueOption option = contribution.option();
            if (options.stream().noneMatch(o -> o.id().equals(option.id()))) {
                options.add(new DialogueNode.Option(option.id(), option.label(), option.icon(), option.enabled(), option.hint()));
                contributed.put(option.id(), contribution.handler());
            }
        }
        Component text = node.text();
        for (Component line : event.getLines()) {
            text = Component.empty().append(text).append("\n").append(line);
        }
        return new Built(new DialogueNode(node.name(), node.subtitle(), text, options), contributed, List.of());
    }

    /** Add the conversations this NPC offers ({@code fealty/conversations/} with a {@code label}) as replies. */
    private static Built withOffers(ServerPlayer player, Entity npc, Built built) {
        List<Map.Entry<ResourceLocation, Conversation>> offers = Conversations.offers(player, npc);
        if (offers.isEmpty()) {
            return built;
        }
        List<DialogueNode.Option> options = new ArrayList<>(built.node().options());
        List<ResourceLocation> offered = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Conversation> entry : offers) {
            options.add(Conversations.offerOption(player, npc, entry.getValue(), Conversations.OFFER_PREFIX + offered.size()));
            offered.add(entry.getKey());
        }
        DialogueNode node = built.node();
        return new Built(new DialogueNode(node.name(), node.subtitle(), node.text(), options), built.contributed(), offered);
    }

    /** What the box shows under an NPC's name: their faction and the player's standing with it, if they have one. */
    static Component subtitle(ServerPlayer player, Entity npc) {
        if (npc instanceof Villager villager) {
            return VillagerDialogue.subtitle(player, villager);
        }
        return FactionResolver.factionOf(npc).map(faction -> {
            int rep = RepManager.getRep(player, faction);
            RepTier tier = RepManager.tierOf(rep);
            return (Component) Component.translatable("fealty.dialogue.subtitle", Factions.displayName(player.server, faction),
                    tier.displayName().copy().withColor(tier.color()), rep);
        }).orElse(Component.empty());
    }

    static void show(ServerPlayer player, Entity npc, DialogueNode node) {
        show(player, npc, new Built(node, Map.of(), List.of()), null, "");
    }

    /** Show one node of a conversation; its replies keep the order they were written in. */
    static void showConversation(ServerPlayer player, Entity npc, DialogueNode node, ResourceLocation conversation, String name) {
        show(player, npc, new Built(node, Map.of(), List.of()), conversation, name);
    }

    private static void show(ServerPlayer player, Entity npc, Built built, @Nullable ResourceLocation conversation, String name) {
        DialogueNode ordered = conversation == null ? ordered(built.node()) : built.node();
        Set<String> options = new HashSet<>();
        ordered.options().forEach(option -> {
            if (option.enabled()) {
                options.add(option.id());
            }
        });
        Session session = begin(player, npc);
        session.options = options;
        session.board = false;
        session.conversation = conversation;
        session.node = name;
        session.offered = built.offered();
        session.contributed = built.contributed();
        FealtyNetwork.send(player, new OpenDialoguePayload(npc.getId(), ordered));
    }

    /** A conversation ended: back to the NPC's usual dialogue, or the box closes if they have none. */
    static void endConversation(ServerPlayer player, Entity npc) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.conversation = null;
        }
        Optional<Built> node = build(player, npc, null);
        if (node.isPresent()) {
            show(player, npc, node.get(), null, "");
        } else {
            close(player);
        }
    }

    /**
     * Have the NPC answer something in the dialogue box once the action being handled is done (a quest giver's
     * action, where the box would otherwise go back to the greeting).
     */
    public static void answer(ServerPlayer player, Component text) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.answer = text;
        }
    }

    /** The answer left by {@link #answer}, if any (cleared). */
    @Nullable
    static Component takeAnswer(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return null;
        }
        Component answer = session.answer;
        session.answer = null;
        return answer;
    }

    /** The replies the player can pick right now (for tests and debugging). */
    public static Set<String> options(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? Set.of() : Set.copyOf(session.options);
    }

    /** The conversation and node the player is in, as {@code <id>#<node>} (for tests and debugging). */
    public static Optional<String> conversation(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.conversation == null ? Optional.empty() : Optional.of(session.conversation + "#" + session.node);
    }

    /**
     * Keep an NPC's attention without a dialogue box (its quest board is open). The client sends {@link #LEAVE_BOARD}
     * when the board closes.
     */
    public static void hold(ServerPlayer player, Entity npc) {
        Session session = begin(player, npc);
        session.options = Set.of();
        session.board = true;
        session.conversation = null;
    }

    /** Open (or carry on) a session with the NPC, letting go of any other NPC the player was talking to. */
    private static Session begin(ServerPlayer player, Entity npc) {
        if (npc instanceof Villager chatting) {
            // A chat they were in stops; they can still be asked what it was about.
            Chatter.interrupt(chatting);
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId != npc.getId() || !session.dimension.equals(npc.level().dimension())) {
            if (session != null) {
                release(player, session);
            }
            session = new Session(npc.getId(), npc.level().dimension());
            SESSIONS.put(player.getUUID(), session);
        }
        session.lastActive = player.level().getGameTime();
        LISTENERS.put(npc.getId(), player.getUUID());
        if (npc instanceof AbstractVillager trader && trader.getTradingPlayer() == null) {
            // The same hold vanilla uses while trading: the villager stops, keeps close and watches the player.
            trader.setTradingPlayer(player);
        }
        if (npc instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(player);
        }
        return session;
    }

    /** Let go of the NPC: it may wander off again. */
    private static void release(ServerPlayer player, Session session) {
        LISTENERS.remove(session.entityId, player.getUUID());
        ServerLevel level = player.server.getLevel(session.dimension);
        Entity npc = level != null ? level.getEntity(session.entityId) : null;
        if (npc instanceof AbstractVillager trader && trader.getTradingPlayer() == player && !(player.containerMenu instanceof MerchantMenu)) {
            trader.setTradingPlayer(null);
        }
    }

    /** The player this NPC is talking to, if any. */
    public static Optional<ServerPlayer> listener(Entity npc) {
        UUID id = LISTENERS.get(npc.getId());
        if (id == null || !(npc.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Session session = SESSIONS.get(id);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (session == null || session.entityId != npc.getId() || player == null || player.level() != level) {
            return Optional.empty();
        }
        return Optional.of(player);
    }

    /**
     * The player's business first (handing in, a letter, a quest's message), then everything else as the NPC offers
     * it, and the ways to leave last.
     */
    public static DialogueNode ordered(DialogueNode node) {
        List<DialogueNode.Option> options = new ArrayList<>(node.options());
        options.sort(Comparator.comparingInt(DialogueService::rank));
        return new DialogueNode(node.name(), node.subtitle(), node.text(), options);
    }

    private static int rank(DialogueNode.Option option) {
        if (BYE.equals(option.id()) || "door".equals(option.icon())) {
            return 2;
        }
        if (option.id().startsWith(QUEST_PREFIX) || "ready".equals(option.icon()) || "mail".equals(option.icon())) {
            return 0;
        }
        return 1;
    }

    /** Show the NPC's answer in the dialogue box (and above their head), keeping the conversation open. */
    public static void reply(ServerPlayer player, Entity npc, Component text) {
        build(player, npc, text).ifPresent(node -> show(player, npc, node, null, ""));
    }

    /** Refresh the dialogue box after something changed, with the NPC's usual greeting. */
    public static void refresh(ServerPlayer player, Entity npc) {
        build(player, npc, null).ifPresent(node -> show(player, npc, node, null, ""));
    }

    /** Close the dialogue box. */
    public static void close(ServerPlayer player) {
        end(player);
        FealtyNetwork.send(player, new OpenScreenPayload(OpenScreenPayload.CLOSE, ""));
    }

    /** End the session without touching the client's screen (another screen is about to open). */
    public static void end(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            release(player, session);
        }
    }

    public static void choose(ServerPlayer player, int entityId, String option) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId != entityId) {
            return;
        }
        // A screen closing only ends the conversation if it is the screen still open for it (the dialogue box closes
        // after the quest board it opened has taken over, and the other way round).
        if (LEAVE.equals(option) || LEAVE_BOARD.equals(option)) {
            if (session.board == LEAVE_BOARD.equals(option)) {
                end(player);
            }
            return;
        }
        if (!session.options.contains(option)) {
            return;
        }
        Entity npc = player.level().getEntity(entityId);
        if (npc == null || !npc.isAlive() || npc.distanceToSqr(player) > REACH * REACH) {
            close(player);
            return;
        }
        session.lastActive = player.level().getGameTime();
        if (NeoForge.EVENT_BUS.post(new DialogueEvent.Choose(player, npc, option)).isCanceled()) {
            if (session.conversation != null) {
                Conversations.redraw(player, npc, session.conversation, session.node);
            } else {
                refresh(player, npc);
            }
            return;
        }
        if (session.conversation != null) {
            Conversations.choose(player, npc, session.conversation, session.node, option);
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
        DialogueBuildEvent.Handler handler = session.contributed.get(option);
        if (handler != null) {
            contributed(player, npc, option, handler);
            return;
        }
        if (option.startsWith(Conversations.OFFER_PREFIX)) {
            int index = parseIndex(option.substring(Conversations.OFFER_PREFIX.length()));
            if (index >= 0 && index < session.offered.size()) {
                Conversations.begin(player, npc, session.offered.get(index));
            }
            return;
        }
        if (npc instanceof Villager villager) {
            VillagerDialogue.handle(player, villager, option);
        } else if (npc instanceof Mob guard && GuardManager.talksLikeGuard(npc)) {
            GuardDialogue.handle(player, guard, option);
        } else {
            QuestGivers.forEntity(npc).ifPresent(giver -> GiverDialogue.handle(player, npc, giver, option));
        }
    }

    private static int parseIndex(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A reply another mod added was picked: call its handler and do what it says. */
    private static void contributed(ServerPlayer player, Entity npc, String option, DialogueBuildEvent.Handler handler) {
        DialogueReply result;
        try {
            result = handler.choose(player, npc, option);
        } catch (RuntimeException e) {
            Fealty.LOGGER.error("Fealty: the handler for dialogue reply {} failed", option, e);
            result = DialogueReply.refresh();
        }
        if (result == null) {
            result = DialogueReply.refresh();
        }
        switch (result.kind()) {
            case SAY -> {
                Speech.say(npc, result.text());
                reply(player, npc, result.text());
            }
            case REFRESH -> refresh(player, npc);
            case CLOSE -> close(player);
            case END -> end(player);
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

    // ---- Housekeeping ----

    /** Conversations end when the player walks off, the NPC is gone, or nothing has happened for a while. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (SESSIONS.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                LISTENERS.remove(session.entityId, entry.getKey());
                it.remove();
                continue;
            }
            Entity npc = player.level().dimension().equals(session.dimension) ? player.level().getEntity(session.entityId) : null;
            boolean over = npc == null || !npc.isAlive() || npc.distanceToSqr(player) > (REACH + 4) * (REACH + 4)
                    || player.level().getGameTime() - session.lastActive > IDLE_TICKS;
            if (over) {
                it.remove();
                release(player, session);
            } else if (npc instanceof AbstractVillager trader && trader.getTradingPlayer() == null) {
                trader.setTradingPlayer(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        LISTENERS.clear();
    }
}
