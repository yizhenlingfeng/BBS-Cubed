package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.launcher.Launcher;
import mchorse.bbs_mod.BBSModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The launcher draws at its own, larger scale (setting launcher_scale) on top of BBS's UI scale. */
@Mixin(value = BBSModClient.class, remap = false)
public abstract class BBSModClientLauncherScaleMixin
{
    @Inject(method = "getCustomGUIScale", at = @At("RETURN"), cancellable = true)
    private static void rabbit$launcherScale(CallbackInfoReturnable<Float> info)
    {
        float custom = info.getReturnValue();
        float scaled = Launcher.scale(custom);

        if (scaled != custom)
        {
            info.setReturnValue(scaled);
        }
    }
}
