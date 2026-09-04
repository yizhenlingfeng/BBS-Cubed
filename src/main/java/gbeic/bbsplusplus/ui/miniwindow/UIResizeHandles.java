package gbeic.bbsplusplus.ui.miniwindow;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import org.lwjgl.glfw.GLFW;

/**
 * 8 向边缘/角缩放手柄,可挂到任意自由定位的 {@link UIElement}(小窗与 UIOverlayPanel)。
 * 通过把宽高改成像素尺寸并调整左/上 offset 实现缩放。
 */
public final class UIResizeHandles
{
    public static final int HANDLE_PX = 6;
    public static final int MIN_W = 160;
    public static final int MIN_H = 80;

    private UIResizeHandles()
    {
    }

    public static void attach(UIElement target)
    {
        attach(target, MIN_W, MIN_H, 0);
    }

    public static void attach(UIElement target, int minW, int minH)
    {
        attach(target, minW, minH, 0);
    }

    /**
     * @param topInset 顶部手柄下移像素(小窗标题栏高度),避免挡住拖动条
     */
    public static void attach(UIElement target, int minW, int minH, int topInset)
    {
        if (target == null)
        {
            return;
        }

        for (Edge edge : Edge.values())
        {
            target.add(createHandle(target, minW, minH, edge, topInset));
        }
    }

    private static UIDraggable createHandle(UIElement target, int minW, int minH, Edge edge, int topInset)
    {
        ResizeState state = new ResizeState();

        UIDraggable handle = new UIDraggable((context) ->
        {
            if (!state.active)
            {
                return;
            }

            applyResize(target, state, context.mouseX, context.mouseY, minW, minH, edge);
            resizeHost(target);
        })
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                boolean handled = super.subMouseClicked(context);

                if (handled)
                {
                    UIElement parent = target.getParent();
                    int parentX = parent != null ? parent.area.x : 0;
                    int parentY = parent != null ? parent.area.y : 0;

                    state.active = true;
                    state.startMouseX = context.mouseX;
                    state.startMouseY = context.mouseY;
                    state.startX = target.area.x - parentX;
                    state.startY = target.area.y - parentY;
                    state.startW = Math.max(target.area.w, 1);
                    state.startH = Math.max(target.area.h, 1);
                }

                return handled;
            }
        };

        handle.enabled(() -> target.isEnabled() && target.isVisible());
        handle.dragEnd(() -> state.active = false);
        handle.cursors(edge.cursor, edge.cursor);
        /* 空 rendering:覆盖 UIDraggable 默认 Scroll.bar 灰条绘制 */
        handle.rendering((context) ->
        {
        });
        place(handle, target, edge, topInset);

        return handle;
    }

    private static void place(UIDraggable handle, UIElement target, Edge edge, int topInset)
    {
        int t = HANDLE_PX;
        int top = Math.max(0, topInset);

        switch (edge)
        {
            /* 顶边/顶角始终在窗口最上方外侧,不占标题栏命中区 */
            case N -> handle.relative(target).x(t).y(-t / 2).w(1F, -t * 2).h(t);
            case S -> handle.relative(target).x(t).y(1F, -t / 2).w(1F, -t * 2).h(t);
            case W -> handle.relative(target).x(-t / 2).y(top + t).w(t).h(1F, -(top + t * 2));
            case E -> handle.relative(target).x(1F, -t / 2).y(top + t).w(t).h(1F, -(top + t * 2));
            case NW -> handle.relative(target).x(-t / 2).y(-t / 2).wh(t, t);
            case NE -> handle.relative(target).x(1F, -t / 2).y(-t / 2).wh(t, t);
            case SW -> handle.relative(target).x(-t / 2).y(1F, -t / 2).wh(t, t);
            case SE -> handle.relative(target).x(1F, -t / 2).y(1F, -t / 2).wh(t, t);
        }
    }

    private static void applyResize(UIElement target, ResizeState state, int mouseX, int mouseY, int minW, int minH, Edge edge)
    {
        int dx = mouseX - state.startMouseX;
        int dy = mouseY - state.startMouseY;
        Flex flex = target.getFlex();

        int newW = state.startW;
        int newH = state.startH;
        int newX = state.startX;
        int newY = state.startY;

        if (edge.resizeW)
        {
            if (edge.fromLeft)
            {
                newW = Math.max(minW, state.startW - dx);
                newX = state.startX + (state.startW - newW);
            }
            else
            {
                newW = Math.max(minW, state.startW + dx);
            }
        }

        if (edge.resizeH)
        {
            if (edge.fromTop)
            {
                newH = Math.max(minH, state.startH - dy);
                newY = state.startY + (state.startH - newH);
            }
            else
            {
                newH = Math.max(minH, state.startH + dy);
            }
        }

        /* 统一成相对父级的绝对像素几何,避免比例宽高 + anchor 居中导致缩放跑偏 */
        UIElement parent = target.getParent();

        if (parent != null)
        {
            flex.relative = parent.area;
        }

        flex.x.set(0, newX);
        flex.y.set(0, newY);
        flex.x.anchor = 0F;
        flex.y.anchor = 0F;
        flex.w.set(0, newW);
        flex.h.set(0, newH);
        flex.w.anchor = 0F;
        flex.h.anchor = 0F;
    }

    private static void resizeHost(UIElement target)
    {
        UIElement parent = target.getParent();

        if (parent != null)
        {
            parent.resize();
        }
        else
        {
            target.resize();
        }
    }

    private enum Edge
    {
        N(false, true, false, true, GLFW.GLFW_VRESIZE_CURSOR),
        S(false, true, false, false, GLFW.GLFW_VRESIZE_CURSOR),
        W(true, false, true, false, GLFW.GLFW_HRESIZE_CURSOR),
        E(true, false, false, false, GLFW.GLFW_HRESIZE_CURSOR),
        NW(true, true, true, true, GLFW.GLFW_RESIZE_NWSE_CURSOR),
        NE(true, true, false, true, GLFW.GLFW_RESIZE_NESW_CURSOR),
        SW(true, true, true, false, GLFW.GLFW_RESIZE_NESW_CURSOR),
        SE(true, true, false, false, GLFW.GLFW_RESIZE_NWSE_CURSOR);

        final boolean resizeW;
        final boolean resizeH;
        final boolean fromLeft;
        final boolean fromTop;
        final int cursor;

        Edge(boolean resizeW, boolean resizeH, boolean fromLeft, boolean fromTop, int cursor)
        {
            this.resizeW = resizeW;
            this.resizeH = resizeH;
            this.fromLeft = fromLeft;
            this.fromTop = fromTop;
            this.cursor = cursor;
        }
    }

    private static final class ResizeState
    {
        boolean active;
        int startMouseX;
        int startMouseY;
        int startX;
        int startY;
        int startW;
        int startH;
    }
}
