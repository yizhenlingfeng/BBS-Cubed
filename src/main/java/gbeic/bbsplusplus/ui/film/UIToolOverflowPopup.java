package gbeic.bbsplusplus.ui.film;

import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;

/**
 * 「工具收纳」按钮悬停后向上展开的工具栏。
 *
 * <p>不是 icons 横条的子元素（那会参与 RowResizer 布局、挤动横条），而是挂在
 * UIFilmPreview 根节点下，用相对定位锚在收纳按钮右上角、向上展开。展开状态
 * 由收纳按钮每帧驱动：鼠标在按钮或展开栏内保持展开，离开约半秒后收起，左键
 * 收纳按钮可固定/解除固定。</p>
 *
 * <p>展开与收起都带动效：整个展开栏从收纳按钮的位置向上滑出、收起时滑回按钮
 * 身后（位移按 smoothstep 缓动），底衬与描边随进度淡入淡出。动画改写的是真实的
 * 相对定位与面积，命中区域与视觉始终一致。</p>
 *
 * <p>展开栏里的按钮是原工具的「镜像」：图标沿用原按钮的 supplier（播放/暂停、
 * 视角等动态图标实时一致），点击通过 {@link UIElement#clickItself} 借用原按钮
 * 的完整点击管线——左键动作、右键菜单与选中状态条都和原按钮一致。</p>
 */
public class UIToolOverflowPopup extends UIElement
{
    /** 鼠标离开展开栏后的宽限期，超过即收起（毫秒）。 */
    private static final long LINGER_MS = 400;

    /** 展开栏与收纳按钮之间的间隙。 */
    private static final int GAP = 4;

    /** 展开/收起动效的时长（秒）。 */
    private static final float ANIM_SECONDS = 0.15F;

    private final UIFilmPreview preview;

    private UIElement anchor;
    private boolean expanded;
    private boolean pinned;
    private long hideAt;

    /** 动画进度：0 收起、1 展开，帧间推进，渲染位置按 smoothstep 缓动。 */
    private float progress;
    private float lastTime;
    private int lastOffset = -1;

    public UIToolOverflowPopup(UIFilmPreview preview)
    {
        this.preview = preview;

        /* 与 icons 横条同款的自适应行：宽度由子按钮之和决定 */
        this.row(0).padding(0).height(0);
        this.row().resize();

        this.setVisible(false);
        this.setEnabled(false);
    }

    /** 绑定锚点（收纳按钮）：展开栏的右上角对齐按钮右上角、向上弹。 */
    public void anchorTo(UIElement anchor)
    {
        this.anchor = anchor;
        this.relative(anchor).x(1F, 0).y(0, -GAP).anchor(1F, 1F);
    }

    public boolean isExpanded()
    {
        return this.expanded;
    }

    public boolean isPinned()
    {
        return this.pinned;
    }

    /** 左键收纳按钮：固定/解除固定（固定时鼠标离开不收起）。 */
    public void togglePinned()
    {
        this.pinned = !this.pinned;

        if (this.pinned)
        {
            this.openPopup();
        }
        /* 解除固定后交给 updateHover：鼠标还在按钮上就保持展开，离开则宽限收起 */
    }

    /** 每帧由收纳按钮驱动：hover 区域 = 收纳按钮 ∪ 展开栏自身。 */
    public void updateHover(UIContext context, Area anchorArea)
    {
        boolean hover = anchorArea.isInside(context.mouseX, context.mouseY)
            || (this.expanded && this.area.isInside(context.mouseX, context.mouseY));

        if (hover)
        {
            this.hideAt = 0L;
            this.openPopup();
        }
        else if (this.expanded && !this.pinned && !context.hasContextMenu())
        {
            /* 右键配置菜单开着时冻结收起计时，避免菜单悬空时展开栏先消失 */
            if (this.hideAt == 0L)
            {
                this.hideAt = System.currentTimeMillis() + LINGER_MS;
            }
            else if (System.currentTimeMillis() >= this.hideAt)
            {
                this.closePopup();
            }
        }

        this.updateAnimation(context);
    }

    /** 整条工具栏被自动隐藏时强制收起（连同固定状态），不做动画直接归零。 */
    public void forceCollapse()
    {
        this.pinned = false;
        this.expanded = false;
        this.progress = 0F;
        this.hideAt = 0L;
        this.setVisible(false);
        this.setEnabled(false);
    }

    /** 重读收纳清单重建镜像按钮。展开瞬间或右键配置变化后调用。 */
    public void rebuild()
    {
        for (IUIElement child : new ArrayList<>(this.getChildren()))
        {
            this.remove(child);
        }

        for (FilmToolbarOverflow.Tool tool : FilmToolbarOverflow.getTools(this.preview))
        {
            if (FilmToolbarOverflow.isHidden(tool.id))
            {
                this.add(new MirrorButton(tool));
            }
        }

        if (this.expanded)
        {
            if (this.getChildren().isEmpty())
            {
                /* 收纳清单被清空（如一键还原）：连同固定状态一起收起，别留下空底衬 */
                this.pinned = false;
                this.closePopup();
            }
            else
            {
                this.resize();
            }
        }
    }

    private void openPopup()
    {
        if (this.expanded)
        {
            return;
        }

        this.rebuild();

        /* 没有收纳任何工具时不弹空栏：空栏只剩两像素厚的底衬，
         * 会在收纳按钮右上方留下一小块黑点 */
        if (this.getChildren().isEmpty())
        {
            return;
        }

        this.expanded = true;
        this.setVisible(true);
        this.setEnabled(true);
        this.resize();
    }

    private void closePopup()
    {
        this.expanded = false;
        this.hideAt = 0L;

        /* 立刻停止交互，可见性交给收起动画走完（updateAnimation 归零后隐藏） */
        this.setEnabled(false);
    }

    /**
     * 每帧推进展开/收起动效（由收纳按钮的渲染驱动）。
     *
     * <p>进度按时间线性推进、位置按 smoothstep 缓动；收起时整个展开栏滑回收纳
     * 按钮身后，进度归零才真正隐藏。滑出距离多加一个 GAP，让进度为 0 时展开栏
     * 恰好完全落在收纳按钮身上，读起来是「从按钮里长出来」。</p>
     */
    private void updateAnimation(UIContext context)
    {
        float time = context.getTickTransition() / 20F;
        float delta = Math.max(0F, Math.min(0.1F, time - this.lastTime));
        float target = this.expanded ? 1F : 0F;

        this.lastTime = time;

        if (this.progress != target)
        {
            float step = delta / ANIM_SECONDS;

            this.progress = Math.max(0F, Math.min(1F, this.progress + (target > this.progress ? step : -step)));
        }

        if (!this.expanded && this.progress <= 0F)
        {
            if (this.isVisible())
            {
                this.setVisible(false);
                this.lastOffset = -1;
            }

            return;
        }

        if (this.isVisible() && this.anchor != null)
        {
            float eased = this.progress * this.progress * (3F - 2F * this.progress);
            int offset = (int) ((1F - eased) * (this.area.h + GAP));

            if (offset != this.lastOffset)
            {
                this.lastOffset = offset;
                this.relative(this.anchor).x(1F, 0).y(0, -GAP + offset).anchor(1F, 1F);
                this.resize();
            }
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.isVisible())
        {
            /* 半透明底 + 描边，与上下文菜单观感一致；透明度随动效进度淡入淡出 */
            float eased = this.progress * this.progress * (3F - 2F * this.progress);

            context.batcher.box(this.area.x - 2, this.area.y - 2, this.area.ex() + 2, this.area.ey() + 2, Colors.mulA(Colors.A75, eased));
            context.batcher.outline(this.area.x - 2, this.area.y - 2, this.area.ex() + 2, this.area.ey() + 2, Colors.mulA(Colors.A25, eased));
        }

        super.render(context);
    }

    /** 原工具按钮的镜像：点击（含右键）完全走原按钮的管线。 */
    private static class MirrorButton extends UIIcon
    {
        private final FilmToolbarOverflow.Tool tool;

        public MirrorButton(FilmToolbarOverflow.Tool tool)
        {
            super(tool.button::getIcon, null);

            this.tool = tool;
            this.tooltip(tool.label);
        }

        @Override
        protected boolean isAllowed(int mouseButton)
        {
            return mouseButton == 0 || mouseButton == 1;
        }

        @Override
        public void render(UIContext context)
        {
            /* 镜像原按钮的选中高亮（洋葱皮、录制中、飞行中这类状态条） */
            if (this.tool.button.isHighlighted())
            {
                context.batcher.highlight(this.area, Direction.BOTTOM);
            }

            super.render(context);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.isEnabled() && this.area.isInside(context) && this.isAllowed(context.mouseButton))
            {
                /* clickItself 会临时把鼠标坐标映射到原按钮区域内，从而触发原按钮的
                 * 左键动作或右键菜单。但原按钮被收纳后有两道门会短路合成点击：
                 * isVisible 为 false 使 isEnabled() 为 false，且 RowResizer 会把
                 * 隐藏子元素的 area 清成 0×0（clickItself 映射进去的鼠标必然不在
                 * 区域内）。先临时恢复可见并同步重排横条拿回真实 area，再合成
                 * 点击，最后还原——同一调用栈内完成，不产生渲染帧。 */
                UIElement row = this.tool.button.getParent();

                this.tool.button.setVisible(true);

                try
                {
                    if (row != null)
                    {
                        row.resize();
                    }

                    this.tool.button.clickItself(context, context.mouseButton);
                }
                finally
                {
                    this.tool.button.setVisible(false);
                }

                return true;
            }

            return super.subMouseClicked(context);
        }
    }
}
