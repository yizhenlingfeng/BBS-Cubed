package bbslezy.mixin.client;

import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.types.WAVImporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WAVImporter.class, remap = false)
public abstract class WAVImporterMixin
{
    @Inject(method = "canImport", at = @At("HEAD"), cancellable = true)
    private void bbslezy$noWavReencode(ImporterContext context, CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(false);
    }
}
