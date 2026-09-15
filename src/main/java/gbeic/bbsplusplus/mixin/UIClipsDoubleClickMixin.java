package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import gbeic.bbsplusplus.util.DoubleClickHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import gbeic.bbsplusplus.BBSPlusPlusMod;

/**
 * Mixin — 在 {@link UIClips} 时间轴中添加双击剪辑检测。
 * <p>
 * 通过 {@link DoubleClickHelper} 触发剪辑编辑面板的编辑按钮，
 * 从而快速打开对应的关键帧编辑器。
 * </p>
 */
@Mixin(UIClips.class)
public abstract class UIClipsDoubleClickMixin
{
    @Shadow(remap = true)
    private IUIClipsDelegate delegate;

    @Shadow(remap = true)
    private Clips clips;

    @Shadow(remap = true)
    public abstract int fromLayerY(int mouseY);

    @Shadow(remap = true)
    public abstract void setSelected(Clip clip);

    @Shadow(remap = true)
    public abstract boolean hasEmbeddedView();

    /** 上一次点击的 X 坐标 */
    @Unique
    private int bbs_lastClickX;

    /** 上一次点击的 Y 坐标 */
    @Unique
    private int bbs_lastClickY;

    /** 上一次点击的时间戳（毫秒） */
    @Unique
    private long bbs_lastClickTime;

    /**
     * 在 {@code handleLeftClick} 开头检测双击并触发编辑。
     * 2.6 中 setMouse 上移到父类 UITimelineCanvas，点击坐标直接取本方法入参。
     */
    @Inject(
        method = "handleLeftClick",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void onHandleLeftClickHead(UIContext context, int mouseX, int mouseY, boolean ctrl, boolean shift, boolean alt, CallbackInfoReturnable<Boolean> cir)
    {
        long now = System.currentTimeMillis();

        /* 双击检测：同位置 + 500ms 内（先读取上一次点击，再记录本次） */
        boolean isDoubleClick = mouseX == this.bbs_lastClickX
            && mouseY == this.bbs_lastClickY
            && now - this.bbs_lastClickTime < 500L;

        this.bbs_lastClickX = mouseX;
        this.bbs_lastClickY = mouseY;
        this.bbs_lastClickTime = now;

        if (shift || this.hasEmbeddedView() || !isDoubleClick) return;

        /* xAxis 在 2.6 上移到父类 UITimelineCanvas，通过公开 getXAxis() 读取 */
        int tick = (int) Math.floor(((UITimelineCanvas) (Object) this).getXAxis().from(mouseX));
        int layerIndex = this.fromLayerY(mouseY);
        Clip clip = this.clips.getClipAt(tick, layerIndex);

        if (clip == null) return;

        /* 选中剪辑 */
        this.delegate.pickClip(clip);
        this.setSelected(clip);

        /* 通过工具类触发编辑器 */
        if (this.delegate instanceof UIClipsPanel clipsPanel)
        {

            /* 通过反射读取 UIClipsPanel 的私有 panel 字段 */
            UIClip<?> clipPanel = this.bbs_getPanel(clipsPanel);

            if (clipPanel != null)
            {
                DoubleClickHelper.triggerEdit(clipPanel);
            }
        }

        cir.setReturnValue(true);
    }

    /** 通过反射读取 UIClipsPanel 的 {@code panel} 字段 */
    @Unique
    private UIClip<?> bbs_getPanel(UIClipsPanel panel)
    {
        try
        {
            java.lang.reflect.Field field = UIClipsPanel.class.getDeclaredField("panel");
            field.setAccessible(true);
            return (UIClip<?>) field.get(panel);
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("剪辑双击编辑：读取面板字段失败", e);
            return null;
        }
    }
}