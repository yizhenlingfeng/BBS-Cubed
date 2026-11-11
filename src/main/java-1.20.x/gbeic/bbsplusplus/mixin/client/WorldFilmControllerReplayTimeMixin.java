package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.clips.ReplayTimeBridge;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.film.WorldFilmController;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Connects camera replay time to the entity update and render passes. */
@Mixin(value = WorldFilmController.class, remap = false)
public abstract class WorldFilmControllerReplayTimeMixin
{
    @Shadow
    protected CameraClipContext context;

    @Shadow
    public int tick;

    @Inject(method = "update", at = @At("HEAD"))
    private void bbspp_cml$beginReplayTime(CallbackInfo ci)
    {
        boolean paused = ((WorldFilmController) (Object) this).paused;
        int timelineTick = paused ? this.tick : this.tick + 1;

        ReplayTimeBridge.begin(this.context == null ? null : this.context.clips, timelineTick, 0F);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void bbspp_cml$endReplayTime(CallbackInfo ci)
    {
        ReplayTimeBridge.clear();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bbspp_cml$beginReplayRender(WorldRenderContext renderContext, CallbackInfo ci)
    {
        boolean paused = ((WorldFilmController) (Object) this).paused;
        float transition = paused || renderContext == null ? 0F : renderContext.tickDelta();

        ReplayTimeBridge.begin(this.context == null ? null : this.context.clips,
            Math.max(0, this.tick), transition);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void bbspp_cml$endReplayRender(WorldRenderContext renderContext, CallbackInfo ci)
    {
        ReplayTimeBridge.clear();
    }
}
