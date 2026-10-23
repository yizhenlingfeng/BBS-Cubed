package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.film.TrackDragHost;
import gbeic.bbsplusplus.client.ui.film.TrackOrderStore;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 回放编辑器轨道排序拖动（BBS++ 「解锁轨道排序」功能）。
 *
 * <p>解锁后，在名称列左键按住轨道行拖动即可重排；位移超过阈值才算拖动，
 * 未超过阈值保留原点击行为（在播放头插入关键帧）。折叠箭头与分组头行
 * 的点击保持原折叠行为，不参与拖动。</p>
 *
 * <p>重排规则：只允许在同级（同 parent）且同分组（同 section）内移动；
 * 拖父行时整个子树跟随。拖动中显示插入位置指示线，松手提交并经
 * {@link TrackOrderStore#saveOrder} 持久化。</p>
 */
@Mixin(value = UIKeyframeDopeSheet.class, remap = false)
public abstract class UIKeyframeDopeSheetMixin implements TrackDragHost
{
    @Shadow(remap = false)
    public abstract List<UIKeyframeSheet> getSheets();

    @Shadow(remap = false)
    private UIKeyframes keyframes;

    @Shadow(remap = false)
    private double trackHeight;

    /** 拖动位移阈值：低于它视为普通点击，保留插关键帧行为。 */
    @Unique
    private static final int BBSPP_DRAG_THRESHOLD = 5;

    @Unique
    private boolean bbspp$armed;

    @Unique
    private boolean bbspp$dragging;

    @Unique
    private UIKeyframeSheet bbspp$dragSheet;

    @Unique
    private int bbspp$dragStartY;

    /** 松手时的扁平列表插入位置（-1 = 无有效落点）。 */
    @Unique
    private int bbspp$pendingInsertIndex = -1;

    /** 插入指示线的屏幕 Y（-1 = 不画）。 */
    @Unique
    private int bbspp$insertY = -1;

    @Override
    public void bbspp$cancelTrackDrag()
    {
        this.bbspp$armed = false;
        this.bbspp$dragging = false;
        this.bbspp$dragSheet = null;
        this.bbspp$pendingInsertIndex = -1;
        this.bbspp$insertY = -1;
    }

    /**
     * 解锁状态下拦截名称列点击：命中轨道行时取消原「立即插关键帧」，
     * 改为蓄力拖动；分组头行与折叠箭头仍走原折叠逻辑。
     */
    @Inject(method = "clickSheets", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$armTrackDrag(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!TrackOrderStore.isUnlocked())
        {
            return;
        }

        UIKeyframeDopeSheet self = (UIKeyframeDopeSheet) (Object) this;
        UIKeyframeDopeSheetAccessor access = (UIKeyframeDopeSheetAccessor) self;
        int labelWidth = this.keyframes.getLabelWidth();

        /* 分组头行：保持原折叠切换 */
        for (Map.Entry<UIKeyframeSheet.Section, Integer> entry : access.bbspp$getSectionYCache().entrySet())
        {
            int sectionY = self.getDopeSheetY() + entry.getValue();

            if (context.mouseY >= sectionY && context.mouseY < sectionY + (int) this.trackHeight)
            {
                return;
            }
        }

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (!access.bbspp$isVisible(sheet))
            {
                continue;
            }

            int height = self.getTrackHeight(sheet);
            int y = self.getDopeSheetY(sheet);

            if (context.mouseY >= y && context.mouseY < y + height)
            {
                /* 折叠箭头：保持原折叠切换 */
                if (!sheet.children.isEmpty() && access.bbspp$isFoldToggleHit(context, sheet, y, labelWidth))
                {
                    return;
                }

                /* 与原 clickSheets 一致：命中行即消费该次点击 */
                cir.setReturnValue(true);
                this.bbspp$armed = true;
                this.bbspp$dragging = false;
                this.bbspp$dragSheet = sheet;
                this.bbspp$dragStartY = context.mouseY;
                this.bbspp$pendingInsertIndex = -1;
                this.bbspp$insertY = -1;

                return;
            }
        }
    }

    /** 拖动跟随：超过阈值进入拖动状态并实时计算插入位置。 */
    @Inject(method = "handleMouse", at = @At("HEAD"), remap = false)
    private void bbspp$updateTrackDrag(UIContext context, int lastX, int lastY, CallbackInfo ci)
    {
        if (!this.bbspp$armed || this.bbspp$dragSheet == null)
        {
            return;
        }

        if (!TrackOrderStore.isUnlocked())
        {
            this.bbspp$cancelTrackDrag();

            return;
        }

        if (!this.bbspp$dragging && Math.abs(context.mouseY - this.bbspp$dragStartY) > BBSPP_DRAG_THRESHOLD)
        {
            this.bbspp$dragging = true;
        }

        if (this.bbspp$dragging)
        {
            this.bbspp$updateInsertion(context.mouseY);
        }
    }

    /** 松手提交：拖动则重排并持久化，纯点击则补回原「插关键帧」行为。 */
    @Inject(method = "mouseReleased", at = @At("TAIL"), remap = false)
    private void bbspp$finishTrackDrag(UIContext context, CallbackInfo ci)
    {
        if (!this.bbspp$armed)
        {
            return;
        }

        UIKeyframeSheet sheet = this.bbspp$dragSheet;
        boolean wasDragging = this.bbspp$dragging;
        int pendingInsert = this.bbspp$pendingInsertIndex;

        this.bbspp$cancelTrackDrag();

        if (sheet == null)
        {
            return;
        }

        if (!wasDragging)
        {
            /* 原点击行为：在该轨道的播放头处插入关键帧 */
            ((IUIKeyframeGraph) (Object) this).addKeyframeManually(sheet, this.keyframes.getTick(), null);

            return;
        }

        if (pendingInsert >= 0)
        {
            this.bbspp$commitDrag(sheet, pendingInsert);
        }
    }

    /** 插入指示线画在名称列上。 */
    @Inject(method = "postRender", at = @At("TAIL"), remap = false)
    private void bbspp$renderTrackDragIndicator(UIContext context, CallbackInfo ci)
    {
        if (!this.bbspp$dragging || this.bbspp$insertY < 0)
        {
            return;
        }

        Area area = this.keyframes.area;
        int w = this.keyframes.getLabelWidth();

        context.batcher.box(area.x, this.bbspp$insertY - 1, area.x + w, this.bbspp$insertY + 1, Colors.ACTIVE | Colors.A100);
    }

    /** 找出鼠标 Y 最近的可见同组兄弟行，算出插入位置与指示线 Y。 */
    @Unique
    private void bbspp$updateInsertion(int mouseY)
    {
        UIKeyframeDopeSheet self = (UIKeyframeDopeSheet) (Object) this;
        UIKeyframeDopeSheetAccessor access = (UIKeyframeDopeSheetAccessor) self;
        UIKeyframeSheet dragged = this.bbspp$dragSheet;

        UIKeyframeSheet best = null;
        int bestDist = Integer.MAX_VALUE;
        boolean after = false;

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet == dragged || bbspp$isDescendant(sheet, dragged))
            {
                continue;
            }

            if (!access.bbspp$isVisible(sheet) || sheet.parent != dragged.parent
                || !Objects.equals(sheet.section, dragged.section))
            {
                continue;
            }

            int mid = self.getDopeSheetY(sheet) + self.getTrackHeight(sheet) / 2;
            boolean isAfter = mouseY >= mid;
            int dist = Math.abs(mouseY - mid);

            if (dist < bestDist)
            {
                bestDist = dist;
                best = sheet;
                after = isAfter;
            }
        }

        if (best == null)
        {
            this.bbspp$pendingInsertIndex = -1;
            this.bbspp$insertY = -1;

            return;
        }

        int bestIndex = this.getSheets().indexOf(best);

        this.bbspp$pendingInsertIndex = after ? bbspp$unitEnd(this.getSheets(), best) : bestIndex;
        this.bbspp$insertY = after ? bbspp$unitBottom(access, self, best) : self.getDopeSheetY(best);
    }

    /** 提交重排：把拖动行（连同子树）从列表里搬到插入位置，刷新缓存并保存。 */
    @Unique
    private void bbspp$commitDrag(UIKeyframeSheet dragged, int pendingInsert)
    {
        UIKeyframeDopeSheet self = (UIKeyframeDopeSheet) (Object) this;
        UIKeyframeDopeSheetAccessor access = (UIKeyframeDopeSheetAccessor) self;
        List<UIKeyframeSheet> sheets = this.getSheets();
        List<UIKeyframeSheet> unit = new ArrayList<>();

        bbspp$collectUnit(dragged, unit);

        int insert = pendingInsert;

        for (UIKeyframeSheet member : unit)
        {
            int index = sheets.indexOf(member);

            if (index >= 0 && index < insert)
            {
                insert--;
            }
        }

        sheets.removeAll(unit);
        insert = Math.max(0, Math.min(insert, sheets.size()));
        sheets.addAll(insert, unit);

        access.bbspp$updateScrollSize();
        TrackOrderStore.saveOrder(sheets);
    }

    @Unique
    private static void bbspp$collectUnit(UIKeyframeSheet sheet, List<UIKeyframeSheet> unit)
    {
        unit.add(sheet);

        for (UIKeyframeSheet child : sheet.children)
        {
            bbspp$collectUnit(child, unit);
        }
    }

    @Unique
    private static boolean bbspp$isDescendant(UIKeyframeSheet sheet, UIKeyframeSheet ancestor)
    {
        for (UIKeyframeSheet parent = sheet.parent; parent != null; parent = parent.parent)
        {
            if (parent == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    /** 目标行子树之后的扁平下标（「放到目标行下面」时落在其整个子树之后）。 */
    @Unique
    private static int bbspp$unitEnd(List<UIKeyframeSheet> sheets, UIKeyframeSheet target)
    {
        List<UIKeyframeSheet> unit = new ArrayList<>();

        bbspp$collectUnit(target, unit);

        int end = sheets.indexOf(target) + 1;

        for (UIKeyframeSheet member : unit)
        {
            int index = sheets.indexOf(member);

            if (index >= 0)
            {
                end = Math.max(end, index + 1);
            }
        }

        return end;
    }

    /** 指示线 Y：目标行子树最后一条可见行的底边（子树折叠时即目标行底边）。 */
    @Unique
    private static int bbspp$unitBottom(UIKeyframeDopeSheetAccessor access, UIKeyframeDopeSheet self, UIKeyframeSheet target)
    {
        UIKeyframeSheet last = target;
        List<UIKeyframeSheet> unit = new ArrayList<>();

        bbspp$collectUnit(target, unit);

        for (UIKeyframeSheet member : unit)
        {
            if (access.bbspp$isVisible(member))
            {
                last = member;
            }
        }

        return self.getDopeSheetY(last) + self.getTrackHeight(last);
    }
}
