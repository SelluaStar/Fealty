package com.selluastar.fealty.village;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.selluastar.fealty.Fealty;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Matches structures against a list of entries, so any mod's villages, towns and castles can count as villages.
 * Each entry is a structure id ({@code minecraft:village_plains}), a tag ({@code #minecraft:village}) or a glob on
 * the id ({@code ctov:*}, {@code *:*castle*}). Excluded entries win over included ones.
 */
public final class StructureMatcher {
    /** A single string or a list of strings. */
    public static final Codec<List<String>> ENTRIES_CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
            either -> either.map(List::of, list -> list),
            list -> list.size() == 1 ? Either.<String, List<String>>left(list.getFirst()) : Either.<String, List<String>>right(list));

    private final List<String> include;
    private final List<String> exclude;
    private final List<Predicate<Holder<Structure>>> includeTests;
    private final List<Predicate<Holder<Structure>>> excludeTests;

    public StructureMatcher(List<String> include, List<String> exclude) {
        this.include = List.copyOf(include);
        this.exclude = List.copyOf(exclude);
        this.includeTests = compileAll(include);
        this.excludeTests = compileAll(exclude);
    }

    public List<String> include() {
        return include;
    }

    public List<String> exclude() {
        return exclude;
    }

    public boolean matches(Holder<Structure> structure) {
        for (Predicate<Holder<Structure>> test : excludeTests) {
            if (test.test(structure)) {
                return false;
            }
        }
        for (Predicate<Holder<Structure>> test : includeTests) {
            if (test.test(structure)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of a loose list of entries (from config) matches. */
    public static boolean matchesAny(List<? extends String> entries, Holder<Structure> structure) {
        for (String entry : entries) {
            Predicate<Holder<Structure>> test = compile(entry);
            if (test != null && test.test(structure)) {
                return true;
            }
        }
        return false;
    }

    private static List<Predicate<Holder<Structure>>> compileAll(List<String> entries) {
        List<Predicate<Holder<Structure>>> tests = new ArrayList<>();
        for (String entry : entries) {
            Predicate<Holder<Structure>> test = compile(entry);
            if (test != null) {
                tests.add(test);
            }
        }
        return tests;
    }

    private static Predicate<Holder<Structure>> compile(String raw) {
        String entry = raw.trim();
        if (entry.isEmpty()) {
            return null;
        }
        if (entry.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
            if (tag == null) {
                Fealty.LOGGER.warn("Fealty: invalid structure tag '{}'", raw);
                return null;
            }
            TagKey<Structure> key = TagKey.create(Registries.STRUCTURE, tag);
            return holder -> holder.is(key);
        }
        if (entry.contains("*")) {
            Pattern pattern = glob(entry.contains(":") ? entry : "*:" + entry);
            return holder -> holder.unwrapKey().map(k -> pattern.matcher(k.location().toString()).matches()).orElse(false);
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        if (id == null) {
            Fealty.LOGGER.warn("Fealty: invalid structure id '{}'", raw);
            return null;
        }
        return holder -> holder.is(id);
    }

    private static Pattern glob(String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }
}
