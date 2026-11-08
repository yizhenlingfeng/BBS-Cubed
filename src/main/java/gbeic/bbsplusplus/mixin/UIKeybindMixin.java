package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.Direction;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * 修复快捷键设置界面中无法使用 ESC 取消绑定的问题。
 *
 * BBS 原版在 escape 模式下（允许 ESC 被绑定为快捷键），
 * 完全禁用了 ESC 取消功能，导致用户没有任何办法取消正在进行的快捷键录入。
 *
 * 修复逻辑：当处于 escape 模式且用户单独按下 ESC（不组合任何修饰键且当前没有其他键被录入）时，
 * 视为"取消绑定"操作，清空快捷键并结束录入。
 * 如果已经有其他键被按住/录入后再按 ESC，则正常将 ESC 作为组合键的一部分。
 */
@Mixin(value = UIKeybind.class, remap = true)
public class UIKeybindMixin
{
    /** 教程描述的提示框宽度（像素），够放一行完整的操作步骤。 */
    private static final int DESCRIPTION_WIDTH = 260;

    @Shadow public KeyCombo combo;
    @Shadow public boolean reading;
    @Shadow public Consumer<KeyCombo> callback;

    /**
     * 给「肢体选择」这类按住式快捷键加上分步教程的悬停描述。
     *
     * <p>BBS 默认把快捷键所在那一行的 tooltip 设成快捷键自己的名字（等于把标题再重复一遍），
     * 这里换成多行用法教程（文案里用 \n 分行，{@code LabelTooltip} 会逐行绘制）；
     * 只针对这个快捷键，其它按键行不受影响。</p>
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void bbspp$limbPickDescription(UIContext context, CallbackInfo ci)
    {
        if (this.combo == null || !SnowUIKeys.IGNORE_PICKING_OVERRIDE_KEY.id.equals(this.combo.id))
        {
            return;
        }

        UIElement self = (UIElement) (Object) this;
        UIElement parent = self.getParent();

        self.tooltip(SnowUIKeys.IGNORE_PICKING_OVERRIDE_DESC, DESCRIPTION_WIDTH, Direction.BOTTOM);

        if (parent != null)
        {
            parent.tooltip(SnowUIKeys.IGNORE_PICKING_OVERRIDE_DESC, DESCRIPTION_WIDTH, Direction.BOTTOM);
        }
    }

    /**
     * 在 subKeyPressed 的最开头拦截。
     * 当正在录入快捷键时，如果单独按下了 ESC（无修饰键、当前组合键列表为空），
     * 则清空快捷键并结束录入——视为"取消"操作。
     */
    @Inject(method = "subKeyPressed", at = @At("HEAD"), cancellable = true)
    private void bbspp$allowEscCancel(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (this.reading
            && context.getKeyAction() == KeyAction.PRESSED
            && context.getKeyCode() == GLFW.GLFW_KEY_ESCAPE
            && this.combo.keys.isEmpty()
            && !Window.isCtrlPressed()
            && !Window.isShiftPressed()
            && !Window.isAltPressed())
        {
            // 单独按 ESC（无修饰键、无其他已录入的键）→ 清空快捷键并结束录入
            this.combo.keys.clear();
            this.reading = false;

            if (this.callback != null)
            {
                this.callback.accept(this.combo);
            }

            cir.setReturnValue(true);
        }
    }
}
