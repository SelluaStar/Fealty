package com.selluastar.fealty.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Entry point to the Fealty reputation API.
 *
 * <pre>{@code
 * if (FealtyApi.isAvailable()) {
 *     RepTier tier = FealtyApi.get().getTier(player, village);
 * }
 * }</pre>
 *
 * Other mods should compile against the {@code fealty-api} jar only. The API follows
 * semantic versioning; see {@link #API_VERSION}.
 */
public final class FealtyApi {
    public static final String MOD_ID = "fealty";
    /** Semantic version of this API. Breaking changes bump the major version. */
    public static final String API_VERSION = "1.1.0";

    private static volatile RepApi instance;

    private FealtyApi() {
    }

    /**
     * @return the live API implementation
     * @throws IllegalStateException if Fealty has not finished loading
     */
    public static RepApi get() {
        RepApi api = instance;
        if (api == null) {
            throw new IllegalStateException("Fealty API requested before Fealty finished loading");
        }
        return api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    /** Called once by Fealty itself during mod construction. */
    public static void setInstance(RepApi api) {
        if (instance != null && instance != api) {
            throw new IllegalStateException("Fealty API implementation already set");
        }
        instance = api;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
