package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.screen.ModelTextureGradeShader;
import mchorse.bbs_mod.client.BBSShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebuild the addon shader whenever BBS rebuilds its shader set after a resource reload. */
@Mixin(value = BBSShaders.class, remap = false)
public abstract class BBSShadersTextureGradeMixin
{
    @Inject(method = "setup", at = @At("TAIL"), remap = false)
    private static void bbspp_cml$setupTextureGradeShader(CallbackInfo ci)
    {
        ModelTextureGradeShader.setup();
    }
}
