package com.selluastar.fealty.world;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Pillager strongholds bigger and smaller than the camp: a scout camp, a fort and a castle, picked by the
 * structure's {@code layout}. Each sits on dry, fairly flat land.
 */
public class PillagerStrongholdStructure extends Structure {
    public static final MapCodec<PillagerStrongholdStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Layout.CODEC.fieldOf("layout").forGetter(s -> s.layout)
    ).apply(i, PillagerStrongholdStructure::new));

    private final Layout layout;

    public PillagerStrongholdStructure(StructureSettings settings, Layout layout) {
        super(settings);
        this.layout = layout;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return FlatGround.find(context, layout.size, layout.maxSlope).map(pos -> new GenerationStub(pos, builder ->
                builder.addPiece(layout.piece(pos))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.PILLAGER_STRONGHOLD.get();
    }

    public enum Layout implements StringRepresentable {
        SCOUT_CAMP("scout_camp", PillagerScoutCampPiece.SIZE, 4),
        FORT("fort", PillagerFortPiece.SIZE, 7),
        CASTLE("castle", PillagerCastlePiece.SIZE, 9);

        public static final Codec<Layout> CODEC = StringRepresentable.fromEnum(Layout::values);
        private final String name;
        private final int size;
        private final int maxSlope;

        Layout(String name, int size, int maxSlope) {
            this.name = name;
            this.size = size;
            this.maxSlope = maxSlope;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        StructurePiece piece(BlockPos corner) {
            return switch (this) {
                case SCOUT_CAMP -> new PillagerScoutCampPiece(corner.getX(), corner.getY(), corner.getZ(), Direction.NORTH);
                case FORT -> new PillagerFortPiece(corner.getX(), corner.getY(), corner.getZ(), Direction.NORTH);
                case CASTLE -> new PillagerCastlePiece(corner.getX(), corner.getY(), corner.getZ(), Direction.NORTH);
            };
        }
    }
}
