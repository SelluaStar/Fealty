package com.selluastar.fealty.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * A way to gain or lose reputation. Register new sources with a {@code DeferredRegister} on
 * {@link #REGISTRY_KEY}; data packs can then tune their amount in {@code fealty/rep_actions/} or
 * {@code fealty/crimes/} using the same id.
 *
 * @param defaultAmount    change applied when no data pack entry overrides it
 * @param crime            whether this is a crime (needs a witness, alerts guards, feeds heat)
 * @param canRaiseNegative whether this source may raise reputation that is below zero.
 *                         By design only redemption quests can.
 */
public record RepSource(int defaultAmount, boolean crime, boolean canRaiseNegative) {
    public static final ResourceKey<Registry<RepSource>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(FealtyApi.id("rep_source"));

    public static RepSource action(int amount) {
        return new RepSource(amount, false, false);
    }

    public static RepSource redemption(int amount) {
        return new RepSource(amount, false, true);
    }

    public static RepSource crime(int amount) {
        return new RepSource(amount, true, false);
    }
}
