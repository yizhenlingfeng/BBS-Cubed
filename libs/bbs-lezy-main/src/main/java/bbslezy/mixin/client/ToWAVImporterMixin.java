package bbslezy.mixin.client;

import bbslezy.audio.LezyAudioCodecs;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.types.ToWAVImporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ToWAVImporter.class, remap = false)
public abstract class ToWAVImporterMixin
{
    @Inject(method = "canImport", at = @At("HEAD"), cancellable = true)
    private void bbslezy$noAudioConversion(ImporterContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!LezyAudioCodecs.allVideo(context.files))
        {
            cir.setReturnValue(false);
        }
    }
}
