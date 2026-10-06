package com.selluastar.fealty.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;

/** The template name of a plain (non-jigsaw) template piece, used by some structure mods. */
@Mixin(TemplateStructurePiece.class)
public interface TemplateStructurePieceAccessor {
    @Accessor("templateName")
    String fealty$getTemplateName();
}
