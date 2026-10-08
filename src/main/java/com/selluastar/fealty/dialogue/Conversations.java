package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.dialogue.DialogueContext;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.chain.ChainRole;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.story.script.Scripts;
import com.selluastar.fealty.story.script.Texts;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Runs the conversations from {@code fealty/conversations/} in the dialogue box. */
public final class Conversations {
    /** Reply ids inside a conversation. */
    static final String REPLY_PREFIX = "c:";
    static final String END = REPLY_PREFIX + "end";
    /** Reply ids that start an offered conversation. */
    static final String OFFER_PREFIX = "t:";

    private static Map<ResourceLocation, Conversation> loaded = Map.of();
    /** Conversations added in code (tests); data packs win on the same id. */
    private static final Map<ResourceLocation, Conversation> EXTRA = new LinkedHashMap<>();

    private Conversations() {
    }

    public static void apply(Map<ResourceLocation, Conversation> conversations) {
        loaded = Map.copyOf(conversations);
    }

    public static Map<ResourceLocation, Conversation> all() {
        if (EXTRA.isEmpty()) {
            return loaded;
        }
        Map<ResourceLocation, Conversation> all = new LinkedHashMap<>(EXTRA);
        all.putAll(loaded);
        return all;
    }

    public static Optional<Conversation> get(ResourceLocation id) {
        return Optional.ofNullable(all().get(id));
    }

    /** Add (or with null, remove) a conversation without a data pack. */
    public static void define(ResourceLocation id, @Nullable Conversation conversation) {
        if (conversation == null) {
            EXTRA.remove(id);
        } else {
            EXTRA.put(id, conversation);
        }
    }

    // ---- Who speaks ----

    /** Whether an NPC is a conversation's speaker: by chain role, entity type or villager profession. */
    public static boolean speaks(ResourceLocation speaker, Entity npc) {
        if (npc instanceof Villager villager && ChainManager.hasRole(villager)) {
            ChainRole held = villager.getData(ModAttachments.CHAIN_ROLE);
            String role = FealtyDataManager.chain(held.chain()).flatMap(def -> ChainManager.roleOf(villager, def)).orElse(held.role());
            if (role.equals(speaker.toString()) || (role.equals(speaker.getPath()) && held.chain().getNamespace().equals(speaker.getNamespace()))) {
                return true;
            }
        }
        if (speaker.equals(BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()))) {
            return true;
        }
        return npc instanceof Villager villager && speaker.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));
    }

    private static List<Map.Entry<ResourceLocation, Conversation>> forSpeaker(Entity npc, boolean offered) {
        List<Map.Entry<ResourceLocation, Conversation>> found = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Conversation> entry : all().entrySet()) {
            if (entry.getValue().label().isPresent() == offered && speaks(entry.getValue().speaker(), npc)) {
                found.add(entry);
            }
        }
        found.sort(Comparator.comparingInt((Map.Entry<ResourceLocation, Conversation> e) -> -e.getValue().priority())
                .thenComparing(e -> e.getKey().toString()));
        return found;
    }

    /** The conversation that takes over when the player starts talking to this NPC, if any. */
    public static Optional<Map.Entry<ResourceLocation, Conversation>> takeover(ServerPlayer player, Entity npc) {
        DialogueContext ctx = DialogueContext.of(player, npc);
        return forSpeaker(npc, false).stream().filter(e -> Scripts.test(e.getValue().condition(), ctx)).findFirst();
    }

    /** The conversations this NPC offers among their usual replies. */
    public static List<Map.Entry<ResourceLocation, Conversation>> offers(ServerPlayer player, Entity npc) {
        DialogueContext ctx = DialogueContext.of(player, npc);
        return forSpeaker(npc, true).stream().filter(e -> Scripts.test(e.getValue().condition(), ctx)).toList();
    }

    /** Whether this NPC has any conversation for the player now. */
    public static boolean hasFor(ServerPlayer player, Entity npc) {
        return takeover(player, npc).isPresent() || !offers(player, npc).isEmpty();
    }

    static DialogueNode.Option offerOption(ServerPlayer player, Entity npc, Conversation conversation, String id) {
        return DialogueNode.Option.of(id, Texts.say(conversation.label().orElse(Component.empty()), player, npc), "talk");
    }

    // ---- Running one ----

    /** Start a conversation at its first node. */
    public static boolean begin(ServerPlayer player, Entity npc, ResourceLocation id) {
        Optional<Conversation> conversation = get(id);
        conversation.ifPresent(c -> enter(player, npc, id, c, c.start(), true));
        return conversation.isPresent();
    }

    /** Go to a node: its effects happen (unless only drawing it again), and the box shows it. */
    static void enter(ServerPlayer player, Entity npc, ResourceLocation id, Conversation conversation, String name, boolean arrive) {
        Conversation.Node node = conversation.nodes().get(name);
        if (node == null) {
            finish(player, npc);
            return;
        }
        DialogueContext ctx = DialogueContext.of(player, npc);
        if (arrive) {
            Scripts.apply(node.effects(), ctx);
        }
        DialogueNode shown = build(player, npc, node, ctx);
        DialogueService.showConversation(player, npc, shown, id, name);
        if (arrive) {
            Speech.say(npc, shown.text());
        }
    }

    private static DialogueNode build(ServerPlayer player, Entity npc, Conversation.Node node, DialogueContext ctx) {
        List<DialogueNode.Option> options = new ArrayList<>();
        for (int i = 0; i < node.replies().size(); i++) {
            Conversation.Reply reply = node.replies().get(i);
            Component label = Texts.say(reply.label(), player, npc);
            options.add(Scripts.test(reply.condition(), ctx)
                    ? DialogueNode.Option.of(REPLY_PREFIX + i, label, reply.icon())
                    : DialogueNode.Option.disabled(REPLY_PREFIX + i, label, reply.icon(),
                    reply.hint().map(hint -> Texts.say(hint, player, npc)).orElse(Component.empty())));
        }
        if (options.isEmpty()) {
            options.add(DialogueNode.Option.of(END, Component.translatable("fealty.dialogue.option.continue"), "talk"));
        }
        return new DialogueNode(npc.getDisplayName(), DialogueService.subtitle(player, npc), Texts.say(node.text(), player, npc), options);
    }

    /** The player picked a reply. */
    static void choose(ServerPlayer player, Entity npc, ResourceLocation id, String name, String option) {
        Optional<Conversation> conversation = get(id);
        Conversation.Node node = conversation.map(c -> c.nodes().get(name)).orElse(null);
        if (node == null || END.equals(option) || !option.startsWith(REPLY_PREFIX)) {
            finish(player, npc);
            return;
        }
        int index;
        try {
            index = Integer.parseInt(option.substring(REPLY_PREFIX.length()));
        } catch (NumberFormatException e) {
            return;
        }
        if (index < 0 || index >= node.replies().size()) {
            return;
        }
        Conversation.Reply reply = node.replies().get(index);
        DialogueContext ctx = DialogueContext.of(player, npc);
        if (!Scripts.test(reply.condition(), ctx)) {
            // Something changed since the box was drawn.
            enter(player, npc, id, conversation.get(), name, false);
            return;
        }
        Scripts.apply(reply.effects(), ctx);
        if (reply.ends()) {
            finish(player, npc);
        } else {
            enter(player, npc, id, conversation.get(), reply.next().get(), true);
        }
    }

    /** Draw the current node again, without its effects. */
    static void redraw(ServerPlayer player, Entity npc, ResourceLocation id, String name) {
        Optional<Conversation> conversation = get(id);
        if (conversation.isPresent()) {
            enter(player, npc, id, conversation.get(), name, false);
        } else {
            finish(player, npc);
        }
    }

    /** The conversation is over: back to the NPC's usual dialogue, or the box closes if they have none. */
    static void finish(ServerPlayer player, Entity npc) {
        DialogueService.endConversation(player, npc);
    }
}
