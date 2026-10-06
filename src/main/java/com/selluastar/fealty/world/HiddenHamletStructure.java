package com.selluastar.fealty.world;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.selluastar.fealty.registry.ModStructures;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** The hidden hamlet where the Keeper lives. Rare, placed on fairly flat dry land. */
public class HiddenHamletStructure extends Structure {
    public static final MapCodec<HiddenHamletStructure> CODEC = simpleCodec(HiddenHamletStructure::new);

    public HiddenHamletStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return FlatGround.find(context, HiddenHamletPiece.SIZE, 6).map(pos -> new GenerationStub(pos, builder ->
                builder.addPiece(new HiddenHamletPiece(context.random(), pos.getX(), pos.getY(), pos.getZ(), Direction.NORTH))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HIDDEN_HAMLET.get();
    }
}
