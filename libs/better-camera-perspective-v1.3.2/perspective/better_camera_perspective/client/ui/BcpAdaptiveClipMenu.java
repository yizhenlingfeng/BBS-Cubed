package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 自适应多列右键剪辑菜单。
 * Ctrl+滚轮切列数（1~5），普通滚轮滚动列表，内容超出可视高度时用原版滚动条。
 */
@Environment(EnvType.CLIENT)
public class BcpAdaptiveClipMenu extends UIContextMenu {
    public static final int CELL_WIDTH = 66;
    public static final int CELL_HEIGHT = 22;
    public static final int MAX_VISIBLE_ROWS = 10;
    public static final int MIN_COLUMNS = 1;
    public static final int MAX_COLUMNS = 5;
    public static final int PADDING = 4;
    public static final int SCROLLBAR_W = 6;

    private final List<ContextAction> actions;
    private final UIScrollView scroll;
    private UIElement columns;
    private int columnCount;

    public BcpAdaptiveClipMenu(List<ContextAction> actions) {
        this.actions = actions;
        int k = (actions.size() + MAX_VISIBLE_ROWS - 1) / MAX_VISIBLE_ROWS;
        this.columnCount = Math.max(MIN_COLUMNS, Math.min(k, MAX_COLUMNS));

        this.scroll = UI.scrollView(PADDING);
        this.scroll.relative(this).wh(1.0F, 1.0F);
        this.add(this.scroll);

        this.buildColumns();
    }

    private int totalRows() {
        return (this.actions.size() + this.columnCount - 1) / this.columnCount;
    }

    private void buildColumns() {
        this.scroll.removeAll();

        this.columns = new UIElement();
        this.columns.row(PADDING);
        this.columns.relative(this.scroll).wh(this.columnCount * CELL_WIDTH, this.totalRows() * CELL_HEIGHT);

        int rows = this.totalRows();
        for (int c = 0; c < this.columnCount; c++) {
            UIElement column = new UIElement();
            column.column(PADDING).vertical();
            column.relative(this.columns).w(CELL_WIDTH).h(1.0F);
            for (int r = 0; r < rows; r++) {
                int index = c * rows + r;
                if (index >= this.actions.size()) break;
                GridActionCell cell = new GridActionCell(this.actions.get(index));
                cell.relative(column).w(1.0F).h(CELL_HEIGHT);
                column.add(cell);
            }
            this.columns.add(column);
        }

        this.scroll.add(this.columns);
        this.scroll.resize();
    }

    @Override
    public boolean isEmpty() {
        return this.actions.isEmpty();
    }

    private int menuWidth() {
        return this.columnCount * CELL_WIDTH + (this.columnCount + 1) * PADDING + SCROLLBAR_W;
    }

    private int menuHeight() {
        int rows = Math.min(this.totalRows(), MAX_VISIBLE_ROWS);
        return rows * CELL_HEIGHT + (rows + 1) * PADDING;
    }

    @Override
    public void setMouse(UIContext context) {
        this.set(context.mouseX(), context.mouseY(), this.menuWidth(), this.menuHeight());
        this.bounds(context.menu.overlay, 5);
    }

    /** Ctrl+滚轮优先切列数，不传给滚动容器；普通滚轮交给 UIScrollView */
    @Override
    protected IUIElement childrenMouseScrolled(UIContext context) {
        if (context.mouseWheel != 0
            && (Window.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL)
                || Window.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL))) {
            int next = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS,
                this.columnCount + (context.mouseWheel > 0 ? 1 : -1)));
            if (next != this.columnCount) {
                this.columnCount = next;
                this.buildColumns();
                this.set(this.area.x, this.area.y, this.menuWidth(), this.menuHeight());
                this.resize();
                if (this.getContext() != null) {
                    this.bounds(this.getContext().menu.overlay, 5);
                }
            }
            return this;
        }
        return super.childrenMouseScrolled(context);
    }
}
