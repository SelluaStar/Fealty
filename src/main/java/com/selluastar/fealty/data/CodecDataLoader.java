package com.selluastar.fealty.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.selluastar.fealty.Fealty;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Loads every JSON file in {@code data/<ns>/<directory>/} with a codec and hands the result to a consumer. */
public final class CodecDataLoader<T> extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private final String directory;
    private final Codec<T> codec;
    private final HolderLookup.Provider registries;
    private final Consumer<Map<ResourceLocation, T>> sink;

    public CodecDataLoader(String directory, Codec<T> codec, HolderLookup.Provider registries, Consumer<Map<ResourceLocation, T>> sink) {
        super(GSON, directory);
        this.directory = directory;
        this.codec = codec;
        this.registries = registries;
        this.sink = sink;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        DynamicOps<JsonElement> ops = registries == null ? JsonOps.INSTANCE : RegistryOps.create(JsonOps.INSTANCE, registries);
        Map<ResourceLocation, T> parsed = new LinkedHashMap<>();
        files.forEach((id, json) -> codec.parse(ops, json)
                .resultOrPartial(error -> Fealty.LOGGER.error("Fealty: could not load {} {}: {}", directory, id, error))
                .ifPresent(value -> parsed.put(id, value)));
        sink.accept(parsed);
        Fealty.LOGGER.info("Fealty: loaded {} entries from {}", parsed.size(), directory);
    }
}
