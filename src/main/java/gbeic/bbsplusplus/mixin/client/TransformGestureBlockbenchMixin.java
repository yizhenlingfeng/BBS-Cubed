package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGesture;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blockbench 风味 Gizmo（BBS 2.7 适配版）。
 *
 * <p>BBS 2.7 将 Gizmo 的模式管理从 {@code Gizmo} 类移到了
 * {@link TransformGesture}：当前操作通过 {@link TransformGesture#getOp()} 读取，
 * 切换通过 {@link TransformGesture#enableMode(TransformOp)} 进行，键盘输入通过
 * {@link TransformGesture#keyPressed(UIContext)} 处理。</p>
 *
 * <p>本 Mixin 实现三项行为：</p>
 * <ol>
 *   <li><b>默认平移模式</b>：{@code getOp()} 返回 null 时兜底为 TRANSLATE。</li>
 *   <li><b>G/S/R 切换/恢复</b>：热键触发 {@code enableMode} 时，若请求模式与
 *       当前相同则恢复到上一个模式，否则记录当前模式并切换。</li>
 *   <li><b>T 键循环</b>：{@code keyPressed} 中拦截 T 键，循环切换
 *       TRANSLATE → SCALE → ROTATE → TRANSLATE。</li>
 * </ol>
 */
@Mixin(value = TransformGesture.class, remap = false)
public abstract class TransformGestureBlockbenchMixin
{
    /** 记录上一个操作模式，用于 G/S/R "再按一次恢复" */
    @Unique
    private TransformOp bbspp$previousMode = null;

    @Inject(method = "getOp", at = @At("TAIL"), cancellable = true, remap = false)
    private void bbspp$defaultTranslate(CallbackInfoReturnable<TransformOp> cir)
    {
        if (BBSAddonsSettings.blockbenchGizmo == null
            || !BBSAddonsSettings.blockbenchGizmo.get())
        {
            return;
        }

        if (cir.getReturnValue() == null)
        {
            cir.setReturnValue(TransformOp.TRANSLATE);
        }
    }

    @Inject(method = "enableMode(Lmchorse/bbs_mod/ui/framework/elements/input/drag/TransformOp;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$toggleOrRestore(TransformOp mode, CallbackInfo ci)
    {
        if (BBSAddonsSettings.blockbenchGizmo == null || !BBSAddonsSettings.blockbenchGizmo.get())
        {
            return;
        }

        // 仅在热键触发时应用切换/恢复逻辑
        TransformGesture self = (TransformGesture) (Object) this;
        if (!self.isHotkeyMode())
        {
            return;
        }

        // gizmoKeepOriginal 开启时，第 2 次按 G/S/R 不恢复，保持原版直接切换行为
        boolean keepOriginal = BBSAddonsSettings.gizmoKeepOriginal != null
            && BBSAddonsSettings.gizmoKeepOriginal.get();

        TransformOp current = self.getOp();

        // 请求的模式与当前相同 → 恢复到上一个模式（除非 keepOriginal）
        if (!keepOriginal && current == mode && bbspp$previousMode != null && bbspp$previousMode != mode)
        {
            TransformOp restore = bbspp$previousMode;
            bbspp$previousMode = current;
            self.enableMode(restore);
            ci.cancel();
            return;
        }

        // 切换到新模式，记录当前为上一个模式
        if (current != null && current != mode)
        {
            bbspp$previousMode = current;
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$cycleWithT(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (BBSAddonsSettings.blockbenchGizmo == null || !BBSAddonsSettings.blockbenchGizmo.get())
        {
            return;
        }

        if (context.getKeyCode() != GLFW.GLFW_KEY_T)
        {
            return;
        }

        TransformGesture self = (TransformGesture) (Object) this;
        TransformOp current = self.getOp();
        TransformOp next;

        if (current == TransformOp.TRANSLATE)
        {
            next = TransformOp.SCALE;
        }
        else if (current == TransformOp.SCALE)
        {
            next = TransformOp.ROTATE;
        }
        else
        {
            next = TransformOp.TRANSLATE;
        }

        bbspp$previousMode = current;
        self.enableMode(next);
        cir.setReturnValue(true);
    }
}
