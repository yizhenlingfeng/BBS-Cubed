package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrush;
import gbeic.bbsplusplus.client.ui.pose.PoseBoneMarkerRenderer;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseBoneStringList;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让通用列表在实际对象为姿势骨骼列表时接入参数刷和当前 Pose 帧编辑状态。
 *
 * <p>正式版的 {@link UIPoseBoneStringList} 仍继承普通字符串列表，所以注入保持在
 * {@link UIList} 声明方法的通用入口，并严格检查实际列表类型。其它列表虽然共享目标类，
 * 但不会建立编辑状态，也不会改变行为或外观。</p>
 */
@Mixin(UIList.class)
public abstract class UIListPoseParameterBrushMixin<T>
{
    @Shadow
    protected List<T> list;

    @Unique
    private long bbspp$lastPoseBoneWheelTime;

    @Unique
    private double bbspp$poseBoneWheelInterval;

    @Shadow
    protected abstract int getIndexAtCursor(UIContext context);

    /**
     * 注入目标：通用列表处理鼠标点击的最前端。
     * 注入原因：右侧状态菱形必须拥有独立点击区，不能触发普通骨骼选择或参数刷粘贴。
     * 修改后的行为：左键点击状态槽时按当前多选切换骨骼是否跳过本 Pose 帧，并立即消费事件。
     */
    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void bbspp$togglePoseBoneFromStateDiamond(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!((Object) this instanceof UIPoseBoneStringList self) || context.mouseButton != 0)
        {
            return;
        }

        int markerX = self.area.ex() - 12;

        if (!self.area.isInside(context) || context.mouseX < markerX - 7 || context.mouseX > markerX + 7)
        {
            return;
        }

        int index = this.getIndexAtCursor(context);

        if (index < 0 || index >= this.list.size() || !(this.list.get(index) instanceof String bone))
        {
            return;
        }

        IPoseParameterBrush brush = this.bbspp$findParameterBrush();

        if (brush != null && brush.bbspp$togglePoseBoneSkipped(bone))
        {
            cir.setReturnValue(true);
        }
    }

    /**
     * 注入目标：通用列表处理滚轮事件的最前端。
     * 注入原因：原版每次只移动 10 像素，不足骨骼列表的一行；固定倍速又会让精细定位变得生硬。
     * 修改后的行为：仅为 Pose 关键帧骨骼列表按连续滚轮事件间隔，从原版 10 像素提升到一行或两行，停顿后恢复原版速度。
     */
    @Inject(method = "subMouseScrolled", at = @At("HEAD"), cancellable = true, remap = true)
    private void bbspp$acceleratePoseBoneWheel(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!((Object) this instanceof UIPoseBoneStringList self) || context.mouseWheel == 0D)
        {
            return;
        }

        /* 只有关键帧 Pose 编辑器存在参数刷宿主，普通表单 Pose 列表保持原版速度。 */
        if (!self.area.isInside(context) || !self.scroll.hasScrollbar() || this.bbspp$findParameterBrush() == null)
        {
            return;
        }

        long now = System.nanoTime();
        double elapsed = this.bbspp$lastPoseBoneWheelTime == 0L
            ? Double.POSITIVE_INFINITY
            : (now - this.bbspp$lastPoseBoneWheelTime) / 1_000_000D;
        double distance = 10D;

        if (elapsed <= 250D)
        {
            this.bbspp$poseBoneWheelInterval = this.bbspp$poseBoneWheelInterval <= 0D
                ? elapsed
                : this.bbspp$poseBoneWheelInterval * 0.5D + elapsed * 0.5D;

            if (this.bbspp$poseBoneWheelInterval < 75D)
            {
                distance = self.scroll.scrollItemSize * 2D;
            }
            else if (this.bbspp$poseBoneWheelInterval < 140D)
            {
                distance = self.scroll.scrollItemSize;
            }
        }
        else
        {
            this.bbspp$poseBoneWheelInterval = 0D;
        }

        this.bbspp$lastPoseBoneWheelTime = now;

        distance *= BBSSettings.scrollingSensitivity.get();

        self.scroll.scrollBy(-Math.copySign(distance, context.mouseWheel));
        context.markUpdateScroll();
        cir.setReturnValue(true);
    }

    /**
     * 注入目标：通用列表把鼠标点击转换为选择之前。
     * 注入原因：骨骼列表的回调只拿到选择结果，无法可靠知道 Shift/Ctrl 操作下这次真正点中的目标骨骼。
     * 修改后的行为：先把实际点击行交给参数刷；点中复制源时保持原选择，其它目标粘贴后继续执行原版选择切换。
     */
    @Inject(method = "applySelectionOnClick", at = @At("HEAD"), cancellable = true, remap = true)
    private void bbspp$applyPoseParameterBrushFromList(int index, CallbackInfo ci)
    {
        if (!((Object) this instanceof UIPoseBoneStringList) || index < 0 || index >= this.list.size())
        {
            return;
        }

        T element = this.list.get(index);
        IPoseParameterBrush brush = this.bbspp$findParameterBrush();

        if (!(element instanceof String bone) || brush == null)
        {
            return;
        }

        if (brush.bbspp$applyParameterBrush(bone) == IPoseParameterBrush.Result.SOURCE)
        {
            ci.cancel();
        }
    }

    /**
     * 注入目标：通用列表完成单行原版绘制之后。
     * 注入原因：原版姿势骨骼列表只显示名称，无法判断当前 Pose 关键帧究竟修改了哪些骨骼。
     * 修改后的行为：非默认骨骼显示橙色菱形，跳过骨骼显示灰色斜线菱形；状态槽悬停可直接切换，参数刷图标仍固定在其左侧。
     */
    @Inject(method = "renderListElement", at = @At("TAIL"), remap = true)
    private void bbspp$renderPoseBoneState(UIContext context, T element, int index, int x, int y,
                                           boolean hover, boolean selected, CallbackInfo ci)
    {
        if (!((Object) this instanceof UIPoseBoneStringList self) || !(element instanceof String bone))
        {
            return;
        }

        IPoseParameterBrush brush = this.bbspp$findParameterBrush();

        if (brush == null)
        {
            return;
        }

        int markerX = x + self.area.w - 12;
        int brushIconX = markerX - 16;
        int markerY = y + self.scroll.scrollItemSize / 2;

        boolean source = brush.bbspp$isParameterBrushSource(bone);
        boolean batch = brush.bbspp$isParameterBrushBatch();
        boolean markerHover = hover && context.mouseX >= markerX - 7 && context.mouseX <= markerX + 7;

        if (source)
        {
            context.batcher.icon(Icons.COPY, Colors.opaque(Colors.GREEN), brushIconX, markerY, 0.5F, 0.5F);
        }
        else if (batch && brush.bbspp$isParameterBrushTarget(bone))
        {
            context.batcher.icon(Icons.PASTE, Colors.opaque(Colors.GREEN), brushIconX, markerY, 0.5F, 0.5F);
        }
        else if (!batch && hover && brush.bbspp$isParameterBrushArmed())
        {
            context.batcher.icon(Icons.PASTE, Colors.opaque(Colors.GREEN), brushIconX, markerY, 0.5F, 0.5F);
        }

        if (brush.bbspp$isPoseBoneSkipped(bone))
        {
            PoseBoneMarkerRenderer.renderSkippedDiamond(context, markerX, markerY);
        }
        else if (brush.bbspp$isPoseBoneModified(bone))
        {
            PoseBoneMarkerRenderer.renderModifiedDiamond(context, markerX, markerY, Colors.opaque(Colors.ORANGE));
        }
        else if (markerHover)
        {
            PoseBoneMarkerRenderer.renderHoverDiamond(context, markerX, markerY);
        }

    }

    @Unique
    private IPoseParameterBrush bbspp$findParameterBrush()
    {
        UIElement element = (UIElement) (Object) this;

        while (element != null)
        {
            if (element instanceof IPoseParameterBrush brush)
            {
                return brush;
            }

            element = element.getParent();
        }

        return null;
    }
}
