package com.selluastar.fealty.api.dialogue;

import com.mojang.serialization.Codec;
import com.selluastar.fealty.api.FealtyApi;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * A kind of {@link DialogueEffect}, registered in {@code fealty:dialogue_effect}. {@code codec} reads the value under
 * the type's key.
 *
 * @since API 1.3.0
 */
public record DialogueEffectType<T extends DialogueEffect>(Codec<T> codec) {
    public static final ResourceKey<Registry<DialogueEffectType<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(FealtyApi.id("dialogue_effect"));
}
