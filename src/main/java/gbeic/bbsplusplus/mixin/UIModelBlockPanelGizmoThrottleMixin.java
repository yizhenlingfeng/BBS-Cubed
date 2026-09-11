package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P3：Gizmo 模板缓冲拾取节流。
 *
 * <p>原版 {@link UIModelBlockPanel#renderGizmoStencilInterface(UIContext)} 每帧都：
 * 绑定全屏 Framebuffer → 重画所有把手 → glReadPixels 回读像素判断悬停。
 * 鼠标静止且相机未动时拾取结果不变，但这一整套 GPU 往返每帧都在跑。</p>
 *
 * <p>本 Mixin 在方法开头检查：鼠标位置和 gizmo 视图矩阵都与上一帧相同，
 * 就直接跳过重绘与回读，复用上一帧的 stencil index 与 framebuffer texture。</p>
 */
@Mixin(value = UIModelBlockPanel.class, remap = true)
public abstract class UIModelBlockPanelGizmoThrottleMixin
{
    @Shadow private Camera gizmoCamera;
    @Shadow private boolean canShowGizmo() { return false; }

    @Unique private int bbspp$lastMouseX = Integer.MIN_VALUE;
    @Unique private int bbspp$lastMouseY = Integer.MIN_VALUE;
    @Unique private final Matrix4f bbspp$lastView = new Matrix4f();
    @Unique private boolean bbspp$hasLast = false;

    @Inject(
        method = "renderGizmoStencilInterface",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void bbspp$skipStaticFrame(UIContext context, CallbackInfo ci)
    {
        if (!canShowGizmo())
        {
            bbspp$hasLast = false;
            return;
        }

        int mx = context.mouseX;
        int my = context.mouseY;
        Matrix4f view = this.gizmoCamera.view;

        boolean mouseSame = bbspp$hasLast && mx == bbspp$lastMouseX && my == bbspp$lastMouseY;
        boolean viewSame = view != null && bbspp$hasLast && view.equals(bbspp$lastView, 1.0e-4f);

        if (mouseSame && viewSame)
        {
            ci.cancel();
            return;
        }

        bbspp$lastMouseX = mx;
        bbspp$lastMouseY = my;
        if (view != null)
        {
            bbspp$lastView.set(view);
        }
        bbspp$hasLast = true;
    }
}
