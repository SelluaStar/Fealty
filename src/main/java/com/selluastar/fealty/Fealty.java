package com.selluastar.fealty;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.selluastar.fealty.api.FealtyApi;
import com.selluastar.fealty.compat.Compat;
import com.selluastar.fealty.config.FealtyClientConfig;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.registry.ModRegistries;
import com.selluastar.fealty.rep.RepApiImpl;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Fealty.MOD_ID)
public final class Fealty {
    public static final String MOD_ID = FealtyApi.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public Fealty(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, FealtyConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, FealtyClientConfig.SPEC);
        ModRegistries.register(modBus);
        modBus.addListener(FealtyNetwork::register);
        FealtyApi.setInstance(new RepApiImpl());
        Compat.init(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
