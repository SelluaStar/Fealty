package com.selluastar.fealty.world;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.selluastar.fealty.registry.ModStructures;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A pillager camp: a palisade, tents, cages of captives and a War Banner. Placed on fairly flat dry land. */
public class PillagerCampStructure extends Structure {
    public static final MapCodec<PillagerCampStructure> CODEC = simpleCodec(PillagerCampStructure::new);

    public PillagerCampStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return FlatGround.find(context, PillagerCampPiece.SIZE, 5).map(pos -> new GenerationStub(pos, builder ->
                builder.addPiece(new PillagerCampPiece(pos.getX(), pos.getZ(), Direction.NORTH))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.PILLAGER_CAMP.get();
    }
}
