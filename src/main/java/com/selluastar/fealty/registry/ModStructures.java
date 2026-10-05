package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.world.BanditCampPiece;
import com.selluastar.fealty.world.BanditCampStructure;
import com.selluastar.fealty.world.HiddenHamletPiece;
import com.selluastar.fealty.world.HiddenHamletStructure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Fealty.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Fealty.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<BanditCampStructure>> BANDIT_CAMP =
            STRUCTURE_TYPES.register("bandit_camp", () -> (StructureType<BanditCampStructure>) () -> BanditCampStructure.CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<HiddenHamletStructure>> HIDDEN_HAMLET =
            STRUCTURE_TYPES.register("hidden_hamlet", () -> (StructureType<HiddenHamletStructure>) () -> HiddenHamletStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> BANDIT_CAMP_PIECE =
            PIECES.register("bandit_camp", () -> (StructurePieceType.ContextlessType) BanditCampPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> HIDDEN_HAMLET_PIECE =
            PIECES.register("hidden_hamlet", () -> (StructurePieceType.ContextlessType) HiddenHamletPiece::new);

    private ModStructures() {
    }
}
