package com.selluastar.fealty.data;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.village.StructureMatcher;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;

/**
 * A faction from {@code data/<ns>/fealty/factions/<id>.json}.
 *
 * <p>{@code "kind": "village"} entries are templates: every structure matching {@code structures} (ids, tags or
 * globs, minus {@code exclude}) becomes its own village faction ({@code village:<dimension>/<x>_<z>}). {@code "kind": "static"} entries are a single faction
 * under their file id, such as {@code fealty:bandits}.
 */
public record FactionDefinition(Kind kind, Component name, Optional<StructureMatcher> structures, boolean elder,
                                Optional<Double> renownShare, Optional<Double> renownStartFactor, int priority,
                                Optional<TagKey<EntityType<?>>> members) {

    public static final Codec<FactionDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.optionalFieldOf("kind", Kind.STATIC).forGetter(FactionDefinition::kind),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(FactionDefinition::name),
            StructureMatcher.ENTRIES_CODEC.optionalFieldOf("structures").forGetter(d -> d.structures().map(StructureMatcher::include)),
            Codec.STRING.listOf().optionalFieldOf("exclude", List.of())
                    .forGetter(d -> d.structures().map(StructureMatcher::exclude).orElse(List.of())),
            Codec.BOOL.optionalFieldOf("elder", false).forGetter(FactionDefinition::elder),
            Codec.doubleRange(-10, 10).optionalFieldOf("renown_share").forGetter(FactionDefinition::renownShare),
            Codec.doubleRange(-10, 10).optionalFieldOf("renown_start_factor").forGetter(FactionDefinition::renownStartFactor),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(FactionDefinition::priority),
            TagKey.hashedCodec(Registries.ENTITY_TYPE).optionalFieldOf("members").forGetter(FactionDefinition::members)
    ).apply(i, (kind, name, structures, exclude, elder, share, start, priority, members) -> new FactionDefinition(kind, name,
            structures.map(list -> new StructureMatcher(list, exclude)), elder, share, start, priority, members)));

    public enum Kind implements StringRepresentable {
        VILLAGE("village"),
        STATIC("static");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
