package com.selluastar.fealty.village;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * How a kind of village is laid out, from {@code data/<ns>/fealty/village_layouts/}: which of its buildings the
 * elder should live in and which are never homes (roads, walls, farms, pens). Buildings are matched by the
 * template they were built from, with globs such as {@code *temple*} or {@code *}{@code /houses/*}.
 *
 * @param structures which structures this layout is for (ids, {@code #tags} or globs)
 * @param exclude    structures it is never for
 * @param mods       mod ids that must all be loaded for this layout to be used
 * @param priority   the highest-priority layout that matches a structure wins
 * @param elder      buildings for the elder, best first
 * @param avoid      buildings that are never the elder's home
 * @param reach      how far from the village centre the elder's building may be, in blocks
 */
public record VillageLayout(List<String> structures, List<String> exclude, List<String> mods, int priority, List<String> elder,
                            List<String> avoid, int reach, StructureMatcher matcher, List<Pattern> elderPatterns,
                            List<Pattern> avoidPatterns) {
    public static final int DEFAULT_REACH = 64;

    public static final Codec<VillageLayout> CODEC = RecordCodecBuilder.create(i -> i.group(
            StructureMatcher.ENTRIES_CODEC.fieldOf("structures").forGetter(VillageLayout::structures),
            StructureMatcher.ENTRIES_CODEC.optionalFieldOf("exclude", List.of()).forGetter(VillageLayout::exclude),
            StructureMatcher.ENTRIES_CODEC.optionalFieldOf("mods", List.of()).forGetter(VillageLayout::mods),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(VillageLayout::priority),
            StructureMatcher.ENTRIES_CODEC.optionalFieldOf("elder", List.of()).forGetter(VillageLayout::elder),
            StructureMatcher.ENTRIES_CODEC.optionalFieldOf("avoid", List.of()).forGetter(VillageLayout::avoid),
            Codec.intRange(16, 256).optionalFieldOf("reach", DEFAULT_REACH).forGetter(VillageLayout::reach)
    ).apply(i, VillageLayout::of));

    /** Used when no layout matches: every building is fair game and only the contents decide. */
    public static final VillageLayout NONE = of(List.of(), List.of(), List.of(), Integer.MIN_VALUE, List.of(), List.of(), DEFAULT_REACH);

    public static VillageLayout of(List<String> structures, List<String> exclude, List<String> mods, int priority, List<String> elder,
                                   List<String> avoid, int reach) {
        return new VillageLayout(structures, exclude, mods, priority, elder, avoid, reach, new StructureMatcher(structures, exclude),
                compile(elder), compile(avoid));
    }

    public boolean matches(Holder<Structure> structure) {
        return matcher.matches(structure);
    }

    /** Where a building's template comes in the elder list (0 is best), or empty if it is not listed. */
    public Optional<Integer> elderRank(ResourceLocation template) {
        String id = template.toString();
        for (int i = 0; i < elderPatterns.size(); i++) {
            if (elderPatterns.get(i).matcher(id).matches()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean avoids(ResourceLocation template) {
        String id = template.toString();
        for (Pattern pattern : avoidPatterns) {
            if (pattern.matcher(id).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compile(List<String> globs) {
        return globs.stream().map(String::trim).filter(g -> !g.isEmpty())
                .map(g -> StructureMatcher.glob(g.contains(":") ? g : "*:" + g)).toList();
    }
}
