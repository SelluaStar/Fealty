package com.selluastar.fealty.registry;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.item.LetterInfo;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Fealty.MOD_ID);

    /** Who a sealed letter is from and to. */
    public static final Supplier<DataComponentType<LetterInfo>> LETTER = COMPONENTS.registerComponentType("letter",
            b -> b.persistent(LetterInfo.CODEC).networkSynchronized(LetterInfo.STREAM_CODEC));

    /** The order a Lord's Horn gives next (index into HornOrder). */
    public static final Supplier<DataComponentType<Integer>> HORN_ORDER = COMPONENTS.registerComponentType("horn_order",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The village a Lord's Horn speaks for. */
    public static final Supplier<DataComponentType<ResourceLocation>> HORN_VILLAGE = COMPONENTS.registerComponentType("horn_village",
            b -> b.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    private ModDataComponents() {
    }
}
