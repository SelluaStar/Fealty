package com.selluastar.fealty.data;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * A faction from {@code data/<ns>/fealty/factions/<id>.json}.
 *
 * <p>{@code "kind": "village"} entries are templates: every structure in {@code structures} becomes its own
 * village faction ({@code village:<dimension>/<x>_<z>}). {@code "kind": "static"} entries are a single faction
 * under their file id, such as {@code fealty:bandits}.
 */
public record FactionDefinition(Kind kind, Component name, Optional<TagKey<Structure>> structures, boolean elder,
                                Optional<Double> renownShare, Optional<Double> renownStartFactor, int priority,
                                Optional<TagKey<EntityType<?>>> members) {

    public static final Codec<FactionDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.optionalFieldOf("kind", Kind.STATIC).forGetter(FactionDefinition::kind),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(FactionDefinition::name),
            TagKey.hashedCodec(Registries.STRUCTURE).optionalFieldOf("structures").forGetter(FactionDefinition::structures),
            Codec.BOOL.optionalFieldOf("elder", false).forGetter(FactionDefinition::elder),
            Codec.doubleRange(-10, 10).optionalFieldOf("renown_share").forGetter(FactionDefinition::renownShare),
            Codec.doubleRange(-10, 10).optionalFieldOf("renown_start_factor").forGetter(FactionDefinition::renownStartFactor),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(FactionDefinition::priority),
            TagKey.hashedCodec(Registries.ENTITY_TYPE).optionalFieldOf("members").forGetter(FactionDefinition::members)
    ).apply(i, FactionDefinition::new));

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
