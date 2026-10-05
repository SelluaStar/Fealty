package com.selluastar.fealty.dialogue;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What the dialogue box shows: who is speaking, what they say, and how the player can answer. */
public record DialogueNode(Component name, Component subtitle, Component text, List<Option> options) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueNode> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, DialogueNode::name,
            ComponentSerialization.STREAM_CODEC, DialogueNode::subtitle,
            ComponentSerialization.STREAM_CODEC, DialogueNode::text,
            Option.STREAM_CODEC.apply(ByteBufCodecs.list()), DialogueNode::options,
            DialogueNode::new);

    /**
     * A reply the player can pick.
     *
     * @param icon a GUI icon name ({@code fealty:icon/<name>})
     * @param hint shown when the option is disabled, or as a tooltip
     */
    public record Option(String id, Component label, String icon, boolean enabled, Component hint) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Option> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Option::id,
                ComponentSerialization.STREAM_CODEC, Option::label,
                ByteBufCodecs.STRING_UTF8, Option::icon,
                ByteBufCodecs.BOOL, Option::enabled,
                ComponentSerialization.STREAM_CODEC, Option::hint,
                Option::new);

        public static Option of(String id, Component label, String icon) {
            return new Option(id, label, icon, true, Component.empty());
        }

        public static Option disabled(String id, Component label, String icon, Component hint) {
            return new Option(id, label, icon, false, hint);
        }
    }
}
