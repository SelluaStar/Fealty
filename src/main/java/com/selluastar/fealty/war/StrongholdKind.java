package com.selluastar.fealty.war;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.village.StructureMatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * A kind of pillager stronghold, from {@code data/<ns>/fealty/stronghold_kinds/}: which structures it covers, how
 * dangerous it is (threat, 1 to 5 skulls, before its trait), who holds it, how many captives it keeps, what razing
 * it wins, how far it menaces villages, and how likely each trait is. Strongholds with a War Banner (Fealty's camps,
 * forts and castles) are manned from {@link #garrison}; for others (outposts) the garrison is whoever is there.
 *
 * @param peaceDays  days of peace razing it wins (-1: the server config's {@code peace_days})
 * @param razeDays   days it stays empty once razed (-1: {@code raze_days})
 * @param spoils     treasury rolls razing it wins (-1: {@code war_spoils})
 * @param menaceRange how far it menaces villages (-1: {@code menace_range})
 * @param radius     how far its ground reaches from its heart
 */
public record StrongholdKind(List<String> structures, int priority, String name, int threat, List<Squad> garrison,
                             Optional<EntityType<?>> captain, int captivesMin, int captivesMax, int spoils, int peaceDays, int razeDays,
                             int menaceRange, int menaceOmen, Map<String, Integer> traits, int radius, StructureMatcher matcher) {

    /** Between {@code min} and {@code max} of one kind of mob. */
    public record Squad(EntityType<?> entity, int min, int max) {
        public static final Codec<Squad> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Squad::entity),
                Codec.intRange(0, 64).fieldOf("min").forGetter(Squad::min),
                Codec.intRange(0, 64).fieldOf("max").forGetter(Squad::max)
        ).apply(i, Squad::new));
    }

    public static final Codec<StrongholdKind> CODEC = RecordCodecBuilder.create(i -> i.group(
            StructureMatcher.ENTRIES_CODEC.fieldOf("structures").forGetter(StrongholdKind::structures),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StrongholdKind::priority),
            Codec.STRING.fieldOf("name").forGetter(StrongholdKind::name),
            Codec.intRange(1, 5).optionalFieldOf("threat", 2).forGetter(StrongholdKind::threat),
            Squad.CODEC.listOf().optionalFieldOf("garrison", List.of()).forGetter(StrongholdKind::garrison),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("captain").forGetter(StrongholdKind::captain),
            Codec.intRange(0, 32).optionalFieldOf("captives_min", 0).forGetter(StrongholdKind::captivesMin),
            Codec.intRange(0, 32).optionalFieldOf("captives_max", 0).forGetter(StrongholdKind::captivesMax),
            Codec.INT.optionalFieldOf("spoils", -1).forGetter(StrongholdKind::spoils),
            Codec.INT.optionalFieldOf("peace_days", -1).forGetter(StrongholdKind::peaceDays),
            Codec.INT.optionalFieldOf("raze_days", -1).forGetter(StrongholdKind::razeDays),
            Codec.INT.optionalFieldOf("menace_range", -1).forGetter(StrongholdKind::menaceRange),
            Codec.intRange(1, 5).optionalFieldOf("menace_omen", 1).forGetter(StrongholdKind::menaceOmen),
            Codec.unboundedMap(Codec.STRING, Codec.intRange(0, 1000)).optionalFieldOf("traits", Map.of("none", 1)).forGetter(StrongholdKind::traits),
            Codec.intRange(8, 128).optionalFieldOf("radius", 20).forGetter(StrongholdKind::radius)
    ).apply(i, StrongholdKind::of));

    public static StrongholdKind of(List<String> structures, int priority, String name, int threat, List<Squad> garrison,
                                    Optional<EntityType<?>> captain, int captivesMin, int captivesMax, int spoils, int peaceDays,
                                    int razeDays, int menaceRange, int menaceOmen, Map<String, Integer> traits, int radius) {
        return new StrongholdKind(structures, priority, name, threat, garrison, captain, captivesMin, Math.max(captivesMin, captivesMax),
                spoils, peaceDays, razeDays, menaceRange, menaceOmen, traits, radius, new StructureMatcher(structures, List.of()));
    }

    public boolean matches(Holder<Structure> structure) {
        return matcher.matches(structure);
    }

    /** Pick a trait for a stronghold at a place, by the kind's weights (the same every time for the same place). */
    public StrongholdTrait rollTrait(BlockPos pos) {
        int total = 0;
        for (int weight : traits.values()) {
            total += weight;
        }
        if (total <= 0) {
            return StrongholdTrait.NONE;
        }
        int pick = RandomSource.create(pos.asLong() * 17L + 3L).nextInt(total);
        for (Map.Entry<String, Integer> entry : traits.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return StrongholdTrait.byName(entry.getKey());
            }
        }
        return StrongholdTrait.NONE;
    }

    /**
     * The garrison a War Banner keeps: so many of each mob, the same every time for the same place, with the trait's
     * extra (an evoker or a ravager). The captain is not included.
     */
    public Map<EntityType<?>, Integer> plan(StrongholdTrait trait, BlockPos pos) {
        Map<EntityType<?>, Integer> plan = new LinkedHashMap<>();
        RandomSource random = RandomSource.create(pos.asLong() * 31L + 11L);
        for (Squad squad : garrison) {
            int count = squad.min() + (squad.max() > squad.min() ? random.nextInt(squad.max() - squad.min() + 1) : 0);
            if (count > 0) {
                plan.merge(squad.entity(), count, Integer::sum);
            }
        }
        if (trait == StrongholdTrait.EVOKER) {
            plan.merge(EntityType.EVOKER, 1, Integer::sum);
        } else if (trait == StrongholdTrait.BEASTS) {
            plan.merge(EntityType.RAVAGER, 1, Integer::sum);
        }
        return plan;
    }

    /** About how many defenders hold it, captain included (0 when the garrison is whoever is there). */
    public int defenders(StrongholdTrait trait, BlockPos pos) {
        int total = captain.isPresent() ? 1 : 0;
        for (int count : plan(trait, pos).values()) {
            total += count;
        }
        return garrison.isEmpty() && captain.isEmpty() ? 0 : total;
    }

    /** How many captives it keeps, the same every time for the same place. */
    public int captives(BlockPos pos) {
        if (captivesMax <= 0) {
            return 0;
        }
        return captivesMin + RandomSource.create(pos.asLong() * 7L + 5L).nextInt(captivesMax - captivesMin + 1);
    }
}
