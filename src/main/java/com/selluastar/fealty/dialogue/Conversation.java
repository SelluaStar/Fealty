package com.selluastar.fealty.dialogue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.dialogue.DialogueCondition;
import com.selluastar.fealty.api.dialogue.DialogueEffect;
import com.selluastar.fealty.story.script.Texts;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A branching conversation from {@code data/<ns>/fealty/conversations/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "speaker": "example:stranger",
 *   "if": {"not": {"flag": "example:met"}},
 *   "start": "greet",
 *   "nodes": {
 *     "greet": {
 *       "text": "example.dialogue.greet",
 *       "replies": [
 *         {"label": "example.reply.show_letter", "if": {"has_item": "minecraft:paper"}, "hint": "example.hint.need_letter", "goto": "letter"},
 *         {"label": "example.reply.bye", "end": true}
 *       ]
 *     },
 *     "letter": {
 *       "text": "example.dialogue.letter",
 *       "effects": [{"take_item": "minecraft:paper"}, {"set_flag": "example:met"}],
 *       "replies": [{"label": "example.reply.ready", "goto": "greet"}]
 *     }
 *   }
 * }
 * }</pre>
 *
 * The {@code speaker} is who has the conversation: a chain role ({@code <chain namespace>:<role>}, or the role's own
 * full name), an entity type or a villager profession. Without a {@code label} the conversation takes over the
 * dialogue box when the player starts talking to the speaker and {@code if} passes; with one it is offered as a reply
 * among the speaker's usual ones. A node's {@code effects} happen when it is reached, a reply's when it is picked.
 * A reply whose {@code if} fails is shown greyed out with its {@code hint}. {@code end} (or no {@code goto}) ends the
 * conversation and goes back to the speaker's usual dialogue. Texts are translation keys (or text components); a key
 * gets the player's name as {@code %1$s} and the speaker's as {@code %2$s}.
 */
public record Conversation(Optional<ResourceLocation> id, ResourceLocation speaker, Optional<DialogueCondition> condition,
                           Optional<Component> label, int priority, String start, Map<String, Node> nodes) {
    /** The most replies a node may have (what the dialogue box shows without scrolling). */
    public static final int MAX_REPLIES = 6;

    public static final Codec<Conversation> CODEC = RecordCodecBuilder.<Conversation>create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("id").forGetter(Conversation::id),
            ResourceLocation.CODEC.fieldOf("speaker").forGetter(Conversation::speaker),
            DialogueCondition.CODEC.optionalFieldOf("if").forGetter(Conversation::condition),
            Texts.CODEC.optionalFieldOf("label").forGetter(Conversation::label),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Conversation::priority),
            Codec.STRING.fieldOf("start").forGetter(Conversation::start),
            Codec.unboundedMap(Codec.STRING, Node.CODEC).fieldOf("nodes").forGetter(Conversation::nodes)
    ).apply(i, Conversation::new)).validate(Conversation::check);

    private static DataResult<Conversation> check(Conversation conversation) {
        if (!conversation.nodes().containsKey(conversation.start())) {
            return DataResult.error(() -> "start node \"" + conversation.start() + "\" is not one of the nodes");
        }
        for (Map.Entry<String, Node> node : conversation.nodes().entrySet()) {
            for (Reply reply : node.getValue().replies()) {
                if (reply.next().isPresent() && !conversation.nodes().containsKey(reply.next().get())) {
                    return DataResult.error(() -> "node \"" + node.getKey() + "\" goes to \"" + reply.next().get() + "\", which is not one of the nodes");
                }
            }
        }
        return DataResult.success(conversation);
    }

    /** One thing the speaker says, and the player's replies to it. */
    public record Node(Component text, List<DialogueEffect> effects, List<Reply> replies) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                Texts.CODEC.fieldOf("text").forGetter(Node::text),
                DialogueEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Node::effects),
                Reply.CODEC.listOf().validate(replies -> replies.size() <= MAX_REPLIES ? DataResult.success(replies)
                                : DataResult.error(() -> "a node has at most " + MAX_REPLIES + " replies, got " + replies.size()))
                        .optionalFieldOf("replies", List.of()).forGetter(Node::replies)
        ).apply(i, Node::new));
    }

    /** A reply the player can pick. */
    public record Reply(Component label, Optional<DialogueCondition> condition, Optional<Component> hint, Optional<String> next, boolean end,
                        List<DialogueEffect> effects, String icon) {
        public static final Codec<Reply> CODEC = RecordCodecBuilder.create(i -> i.group(
                Texts.CODEC.fieldOf("label").forGetter(Reply::label),
                DialogueCondition.CODEC.optionalFieldOf("if").forGetter(Reply::condition),
                Texts.CODEC.optionalFieldOf("hint").forGetter(Reply::hint),
                Codec.STRING.optionalFieldOf("goto").forGetter(Reply::next),
                Codec.BOOL.optionalFieldOf("end", false).forGetter(Reply::end),
                DialogueEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Reply::effects),
                Codec.STRING.optionalFieldOf("icon", "talk").forGetter(Reply::icon)
        ).apply(i, Reply::new));

        /** Whether picking it ends the conversation. */
        public boolean ends() {
            return end || next.isEmpty();
        }
    }
}
