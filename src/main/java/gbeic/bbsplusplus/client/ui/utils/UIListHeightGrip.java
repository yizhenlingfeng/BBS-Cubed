package gbeic.bbsplusplus.client.ui.utils;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;
import org.joml.Vector2i;

/**
 * 列表底边的高度抓手。
 * <p>
 * 原版 {@code UIActionsConfigEditor} 里动作与动画两个列表都写死了 {@code h(112)}，且没有可拖动
 * 的分隔条，用户没法把列表拉高去看更长的动作 / 动画名。本控件把一条 6px 高的抓取带骑在列表
 * 底边（内侧 3px + 外侧 3px，外侧正好落在下一行的 margin 里，不抢下层控件的点击）：按住拖动即改
 * 列表高度，双击还原默认值，高度记在 {@code BBSSettings.editorLayoutSettings} 的 layout 配置里
 * （跨界面、跨启动保留）。
 * </p>
 * <p>
 * 抓手是列表所在容器的子控件，但{@link BandResizer}不是 {@code ChildResizer}，父级列布局
 * （{@code AutomaticResizer.getResizers()} 只收集 ChildResizer）会完全忽略它：既不占位、也不
 * 参与内容总高。因此抓手必须在 {@code add()} 之后再设回 {@code band()}——父级会把新子控件
 * 包进 ChildResizer。
 * </p>
 */
public class UIListHeightGrip extends UISplitter
{
    /** 与 BBS 原版一致的默认高度 */
    public static final int DEFAULT_HEIGHT = 112;
    /** 至少留两行，避免拖成一条缝后抓不回来 */
    public static final int MIN_HEIGHT = 56;
    /** 抓取带高度：骑在列表底边上，内侧 3px + 外侧 3px（与 BBS 自带分隔条的 6px 手感一致） */
    public static final int BAND_HEIGHT = 6;

    private static final int FALLBACK_MAX_HEIGHT = 480;
    private static final int BOTTOM_RESERVE = 48;
    private static final int NOTCH_WIDTH = 34;
    /** 悬停时在列表下方间隙里画的小把手高度 */
    private static final int NOTCH_HEIGHT = 3;

    private final UIElement list;
    private final IResizer band = new BandResizer(this);

    public UIListHeightGrip(String key, UIElement list)
    {
        super(key, false, DEFAULT_HEIGHT);

        this.list = list;

        this.range(MIN_HEIGHT, this::getMaxHeight);
        this.measure(list).vertical();
        /* 拖动时用「按下瞬间的列表底边」作参考点，鼠标抓在带子哪一格都零跳变 */
        this.reference(() -> new Vector2i(0, list.area.ey()));
        this.referenceAxis(false, true);
        this.onChange(this::applyHeight);
        this.rendering(this::renderGrip);
        this.enabled(() -> BBSSettings.editorResizablePanels.get());
        this.tooltip(SnowUIKeys.ACTIONS_LIST_RESIZE_HEIGHT);
        /* 列表被滚动出视口时仍要每帧跑拖动回调，否则拖到视口外就卡住 */
        this.noCulling();
    }

    /** 只定位、不占位的布局器，供宿主在每次 {@code add()} 之后重新套用。 */
    public IResizer band()
    {
        return this.band;
    }

    /** 把持久化的高度套到列表上；首次安装与每次拖动都走这里。 */
    public void applyHeight()
    {
        this.list.h(this.getPixels());
        this.list.invalidateLayout();
    }

    /** 上限跟着外层滚动视图走，免得把整页控件挤出可视区 */
    private float getMaxHeight()
    {
        UIElement ancestor = this.list.getParent();

        while (ancestor != null && !(ancestor instanceof UIScrollView))
        {
            ancestor = ancestor.getParent();
        }

        int available = ancestor == null ? FALLBACK_MAX_HEIGHT : ancestor.area.h - BOTTOM_RESERVE;

        return Math.max(MIN_HEIGHT, Math.min(available, FALLBACK_MAX_HEIGHT));
    }

    private void renderGrip(UIContext context)
    {
        Area target = this.list.area;
        int bottom = target.ey();
        boolean dragging = this.isDragging();
        boolean active = dragging || this.area.isInside(context);

        /* 恒画一条 1px 分隔线压在列表底边框上：拖不动时也像原本就有的分栏线 */
        context.batcher.box(target.x, bottom - 1, target.ex(), bottom, BBSSettings.dividerColor());

        if (active && this.isEnabled())
        {
            int half = NOTCH_WIDTH / 2;

            /* 悬停时半透明提示，按住拖动时拉满不透明，手感和滚动条一致 */
            context.batcher.box(
                target.mx() - half, bottom,
                target.mx() + half, bottom + NOTCH_HEIGHT,
                BBSSettings.primaryColor(dragging ? 0xFF000000 : 0xAA000000)
            );
        }
    }

    /**
     * 把抓手贴到列表底边上。实现 {@link IResizer} 而不是走 {@code ChildResizer}，
     * 是为了让列表所在容器的列布局彻底跳过抓手（不占位、不计入内容总高）。
     */
    private static final class BandResizer implements IResizer
    {
        private final UIListHeightGrip grip;

        private BandResizer(UIListHeightGrip grip)
        {
            this.grip = grip;
        }

        @Override
        public void apply(Area area)
        {
            Area target = this.grip.list.area;
            int y = target.ey() - BAND_HEIGHT / 2;

            area.set(target.x, y, target.w, BAND_HEIGHT);
        }
    }
}
