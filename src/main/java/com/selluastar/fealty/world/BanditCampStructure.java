package com.selluastar.fealty.world;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.selluastar.fealty.registry.ModStructures;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A bandit camp: tents, a campfire, a lookout and the camp's standard. */
public class BanditCampStructure extends Structure {
    public static final MapCodec<BanditCampStructure> CODEC = simpleCodec(BanditCampStructure::new);

    public BanditCampStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return FlatGround.find(context, BanditCampPiece.SIZE, 5).map(pos -> new GenerationStub(pos, builder ->
                builder.addPiece(new BanditCampPiece(pos.getX(), pos.getZ(), Direction.NORTH))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.BANDIT_CAMP.get();
    }
}
