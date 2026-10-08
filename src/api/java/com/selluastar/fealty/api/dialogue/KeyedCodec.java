package com.selluastar.fealty.api.dialogue;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.selluastar.fealty.api.FealtyApi;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Reads and writes values whose type is the only key of a JSON object, such as {@code {"has_item": "minecraft:paper"}}
 * or {@code {"set_flag": "example:met"}}. A key without a namespace is Fealty's ({@code has_item} is
 * {@code fealty:has_item}). The type is looked up in its registry when the value is read, so types other mods
 * register are found too.
 *
 * @since API 1.3.0
 */
public final class KeyedCodec {
    private KeyedCodec() {
    }

    /**
     * @param registry the registry of types
     * @param typeOf   the type of a value
     * @param codecOf  how a type reads the object's one value
     * @param what     what the values are, for error messages
     */
    public static <V, T> Codec<V> of(ResourceKey<Registry<T>> registry, Function<V, T> typeOf, Function<T, Codec<? extends V>> codecOf,
                                     String what) {
        return new Codec<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <O> DataResult<Pair<V, O>> decode(DynamicOps<O> ops, O input) {
                Optional<MapLike<O>> map = ops.getMap(input).result();
                if (map.isEmpty()) {
                    return DataResult.error(() -> "A " + what + " is an object with one key (its type), got " + input);
                }
                List<Pair<O, O>> entries = map.get().entries().toList();
                if (entries.size() != 1) {
                    return DataResult.error(() -> "A " + what + " has exactly one key (its type), got " + entries.size());
                }
                Pair<O, O> entry = entries.getFirst();
                Optional<String> key = ops.getStringValue(entry.getFirst()).result();
                if (key.isEmpty()) {
                    return DataResult.error(() -> "A " + what + "'s key must be a string");
                }
                ResourceLocation id = key.get().indexOf(':') >= 0 ? ResourceLocation.tryParse(key.get()) : FealtyApi.id(key.get());
                Registry<T> types = lookup(registry);
                T type = id == null || types == null ? null : types.get(id);
                if (type == null) {
                    return DataResult.error(() -> "Unknown " + what + ": " + key.get());
                }
                Codec<V> codec = (Codec<V>) codecOf.apply(type);
                return codec.parse(ops, entry.getSecond()).<Pair<V, O>>map(value -> Pair.of(value, ops.empty()));
            }

            @Override
            @SuppressWarnings("unchecked")
            public <O> DataResult<O> encode(V value, DynamicOps<O> ops, O prefix) {
                T type = typeOf.apply(value);
                Registry<T> types = lookup(registry);
                ResourceLocation id = types == null ? null : types.getKey(type);
                if (id == null) {
                    return DataResult.error(() -> "Unregistered " + what + " type: " + type);
                }
                Codec<V> codec = (Codec<V>) codecOf.apply(type);
                return codec.encodeStart(ops, value).flatMap(encoded -> ops.mapBuilder().add(id.toString(), encoded).build(prefix));
            }

            @Override
            public String toString() {
                return "KeyedCodec[" + registry.location() + "]";
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> lookup(ResourceKey<Registry<T>> key) {
        return (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
    }
}
