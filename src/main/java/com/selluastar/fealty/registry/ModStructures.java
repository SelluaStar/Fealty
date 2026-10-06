package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.world.BanditCampPiece;
import com.selluastar.fealty.world.BanditCampStructure;
import com.selluastar.fealty.world.HiddenHamletPiece;
import com.selluastar.fealty.world.HiddenHamletStructure;
import com.selluastar.fealty.world.PillagerCampPiece;
import com.selluastar.fealty.world.PillagerCampStructure;
import com.selluastar.fealty.world.PillagerCastlePiece;
import com.selluastar.fealty.world.PillagerFortPiece;
import com.selluastar.fealty.world.PillagerScoutCampPiece;
import com.selluastar.fealty.world.PillagerStrongholdStructure;

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

    public static final DeferredHolder<StructureType<?>, StructureType<PillagerCampStructure>> PILLAGER_CAMP =
            STRUCTURE_TYPES.register("pillager_camp", () -> (StructureType<PillagerCampStructure>) () -> PillagerCampStructure.CODEC);
    /** Scout camps, forts and castles: one type, the {@code layout} picks which. */
    public static final DeferredHolder<StructureType<?>, StructureType<PillagerStrongholdStructure>> PILLAGER_STRONGHOLD =
            STRUCTURE_TYPES.register("pillager_stronghold", () -> (StructureType<PillagerStrongholdStructure>) () -> PillagerStrongholdStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> BANDIT_CAMP_PIECE =
            PIECES.register("bandit_camp", () -> (StructurePieceType.ContextlessType) BanditCampPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> HIDDEN_HAMLET_PIECE =
            PIECES.register("hidden_hamlet", () -> (StructurePieceType.ContextlessType) HiddenHamletPiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> PILLAGER_CAMP_PIECE =
            PIECES.register("pillager_camp", () -> (StructurePieceType.ContextlessType) PillagerCampPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PILLAGER_SCOUT_CAMP_PIECE =
            PIECES.register("pillager_scout_camp", () -> (StructurePieceType.ContextlessType) PillagerScoutCampPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PILLAGER_FORT_PIECE =
            PIECES.register("pillager_fort", () -> (StructurePieceType.ContextlessType) PillagerFortPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PILLAGER_CASTLE_PIECE =
            PIECES.register("pillager_castle", () -> (StructurePieceType.ContextlessType) PillagerCastlePiece::new);

    private ModStructures() {
    }
}
