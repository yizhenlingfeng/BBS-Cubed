package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.ui.utils.camera.OrbitViewportController;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 2.6 中 anchorPosition 上移到 OrbitViewportController，需在声明类上暴露。 */
@Mixin(value = OrbitViewportController.class, remap = false)
public interface OrbitViewportControllerAccessor
{
    @Accessor(value = "anchorPosition", remap = false)
    Vector3d bbspp$getAnchorPosition();
}