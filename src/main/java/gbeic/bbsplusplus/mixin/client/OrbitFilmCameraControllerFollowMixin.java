package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.film.controller.OrbitFilmCameraController;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the renderer-derived model anchor, including form and pose transforms, as follow position. */
@Mixin(value = OrbitFilmCameraController.class, remap = false)
public abstract class OrbitFilmCameraControllerFollowMixin
{
    @Shadow
    private final Vector3d anchorPosition = new Vector3d();

    @Shadow
    protected abstract Vector3d getOrbitTarget(float transition);

    @Inject(method = "writeAnchor", at = @At("RETURN"))
    private void bbspp_cml$followRenderedModelPosition(IEntity entity, float transition, CallbackInfo ci)
    {
        Vector3d renderedPivot = this.getOrbitTarget(transition);

        if (renderedPivot != null)
        {
            this.anchorPosition.set(renderedPivot);
        }
    }
}
