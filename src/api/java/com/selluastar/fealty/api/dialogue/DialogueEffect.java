package com.selluastar.fealty.api.dialogue;

import com.mojang.serialization.Codec;

/**
 * Something that happens when a conversation reaches a node or a reply ({@code "effects"}), or a rumour is heard
 * ({@code "grants"}). In JSON an effect is an object whose one key is its type: {@code {"give_item": "minecraft:paper"}},
 * {@code {"set_flag": "example:met"}}. Register new types in {@link DialogueEffectType#REGISTRY_KEY}.
 *
 * @since API 1.3.0
 */
public interface DialogueEffect {
    Codec<DialogueEffect> CODEC = KeyedCodec.of(DialogueEffectType.REGISTRY_KEY, DialogueEffect::type, DialogueEffectType::codec,
            "dialogue effect");

    DialogueEffectType<?> type();

    void apply(DialogueContext ctx);
}
