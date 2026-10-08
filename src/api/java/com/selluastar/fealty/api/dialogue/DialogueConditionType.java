package com.selluastar.fealty.api.dialogue;

import com.mojang.serialization.Codec;
import com.selluastar.fealty.api.FealtyApi;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * A kind of {@link DialogueCondition}, registered in {@code fealty:dialogue_condition}. {@code codec} reads the value
 * under the type's key, so {@code {"yourmod:moon_phase": 4}} reads {@code 4}.
 *
 * <pre>{@code
 * public static final DeferredRegister<DialogueConditionType<?>> CONDITIONS =
 *         DeferredRegister.create(DialogueConditionType.REGISTRY_KEY, MODID);
 * public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<MoonPhase>> MOON_PHASE =
 *         CONDITIONS.register("moon_phase", () -> new DialogueConditionType<>(MoonPhase.CODEC));
 * }</pre>
 *
 * @since API 1.3.0
 */
public record DialogueConditionType<T extends DialogueCondition>(Codec<T> codec) {
    public static final ResourceKey<Registry<DialogueConditionType<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(FealtyApi.id("dialogue_condition"));
}
