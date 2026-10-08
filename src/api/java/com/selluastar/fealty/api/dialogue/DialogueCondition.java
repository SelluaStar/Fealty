package com.selluastar.fealty.api.dialogue;

import com.mojang.serialization.Codec;

/**
 * A test in a conversation reply ({@code "if"}), a rumour ({@code "requires"}) or a chain step ({@code "unlock"}).
 * In JSON a condition is an object whose one key is its type: {@code {"has_item": "minecraft:paper"}},
 * {@code {"all": [ ... ]}}. Register new types in {@link DialogueConditionType#REGISTRY_KEY}.
 *
 * @since API 1.3.0
 */
public interface DialogueCondition {
    Codec<DialogueCondition> CODEC = KeyedCodec.of(DialogueConditionType.REGISTRY_KEY, DialogueCondition::type,
            DialogueConditionType::codec, "dialogue condition");

    DialogueConditionType<?> type();

    boolean test(DialogueContext ctx);
}
