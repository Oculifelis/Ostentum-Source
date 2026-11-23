package com.ostentum.mixin;

import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A structure piece's template name ("ship" on an End city's ship), for {@code whip.EndShipLoot}. */
@Mixin(TemplateStructurePiece.class)
public interface TemplatePieceAccess {
    @Accessor("templateName")
    String ostentum$templateName();
}
