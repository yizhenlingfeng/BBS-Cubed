package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.ModelFormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 支持列表/网格双模式的伪装分类组件。
 *
 * <p>继承原版 {@link UIFormCategory}，重写布局计算、渲染和点击检测，
 * 使伪装模型可以在列表模式和网格模式之间切换，并支持 Ctrl+滚轮缩放图标大小。</p>
 */
public class UIBBSPPFormCategory extends UIFormCategory
{
    /** 是否为列表模式（false=网格模式） */
    public boolean listMode = false;

    /** 图标缩放百分比，默认100 */
    public int iconScale = 100;

    /** 拖拽中的表单（null 表示未在拖拽） */
    public static Form draggingForm = null;

    /** 上次计算的内容高度，用于检测是否需要重新布局 */
    private int bbsppLastHeight = -1;

    /** 记录当前搜索文本（父类 search 字段为 private） */
    private String bbsppSearch = "";

    private static final int HEADER_H = 20;

    /* 网格模式基础尺寸 */
    private static final int GRID_CELL_W = 60;
    private static final int GRID_CELL_H = 80;

    /* 列表模式基础尺寸 */
    private static final int LIST_CELL_W = 160;
    private static final int LIST_CELL_H = 48;
    private static final int LIST_MAX_COLS = 10;
    private static final int LIST_MIN_COLS = 1;

    public UIBBSPPFormCategory(FormCategory category, UIFormList list)
    {
        super(category, list);

        /* 补充 UIModelFormCategory 构造器中添加的"导出模型为 .bbs.json"右键菜单。
         * setupForms() 用本类替换了原生分类对象，子类构造器不会被执行，
         * 导致原生 UIModelFormCategory 中通过 this.context(...) 注册的导出按钮丢失。 */
        this.context((menu) ->
        {
            if (!(this.getContextForm() instanceof ModelForm modelForm) || this.isGroupContext())
            {
                return;
            }

            menu.action(Icons.UPLOAD, UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL, () ->
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    MapType map = CubicLoader.toData(model);

                    try
                    {
                        File path = BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelForm.model.get() + "/exported._bbs.json");

                        IOUtils.writeText(path, DataToString.toString(map, true));

                        UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_TITLE,
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_DESCRIPTION,
                            path.getParentFile()
                        );

                        UIOverlay.addOverlay(this.getContext(), overlayPanel);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });
        });
    }

    @Override
    public void search(String query)
    {
        this.bbsppSearch = query == null ? "" : query;
        super.search(query);
    }

    /**
     * 是否处于「搜索中」。
     *
     * <p><b>必须做 null 判断</b>：父类构造器会经由
     * {@code UIFormCategory.<init> → setCellSize() → relayout() → contentSize()}
     * 虚调用到本类重写的 {@link #contentSize()}，而那一刻子类字段初始化器尚未执行，
     * {@code bbsppSearch} 仍是 null（同理 {@code iconScale} 是 0）。</p>
     */
    private boolean hasSearch()
    {
        return this.bbsppSearch != null && !this.bbsppSearch.isEmpty();
    }

    /**
     * 当前的图标缩放百分比。
     *
     * <p>父类构造期子类字段尚未初始化（{@code iconScale == 0}），此时按默认 100 处理，
     * 否则会按 0% 算出退化的单元格尺寸。</p>
     */
    private int scalePercent()
    {
        return this.iconScale > 0 ? this.iconScale : 100;
    }

    /**
     * 获取当前模式下的单元格宽度。
     */
    private int getCellWidth()
    {
        int scale = this.scalePercent();

        if (this.listMode)
        {
            return Math.max(100, LIST_CELL_W * scale / 100);
        }

        return Math.max(20, GRID_CELL_W * scale / 100);
    }

    /**
     * 获取当前模式下的单元格高度。
     */
    private int getCellHeight()
    {
        int scale = this.scalePercent();

        if (this.listMode)
        {
            return Math.max(32, LIST_CELL_H * scale / 100);
        }

        return Math.max(30, GRID_CELL_H * scale / 100);
    }

    /**
     * 计算当前宽度下的列数。
     * 列表模式：最少1列，最多5列；网格模式：根据宽度自动计算。
     */
    private int getColumns(int contentWidth)
    {
        int cellW = this.getCellWidth();

        if (this.listMode)
        {
            int cols = contentWidth / cellW;
            return Math.max(LIST_MIN_COLS, Math.min(LIST_MAX_COLS, cols));
        }

        return Math.max(1, Math.max(cellW, contentWidth) / cellW);
    }

    @Override
    protected int contentSize()
    {
        int width = this.area.w;
        int maxW = Math.max(GRID_CELL_W, width);
        List<Form> forms = this.getForms();

        if (this.hasSearch() && forms.isEmpty())
        {
            return 0;
        }

        int cols = this.getColumns(maxW);
        int cellH = this.getCellHeight();
        int height = HEADER_H;

        if (!forms.isEmpty() && this.category.visible.get())
        {
            int rows = (int) Math.ceil((double) forms.size() / cols);
            height += rows * cellH;
        }

        return height;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        int mx = context.mouseX - this.area.x;
        int my = context.mouseY - this.area.y - HEADER_H;
        int contentW = this.area.w;
        int cols = this.getColumns(contentW);
        int cellW = this.getCellWidth();
        int cellH = this.getCellHeight();

        /* 点击标题栏：切换展开/折叠 */
        if (my < 0)
        {
            if (mx > 30 && mx < 30 + context.batcher.getFont().getWidth(this.category.getProcessedTitle()))
            {
                this.category.visible.set(!this.category.visible.get());
                return true;
            }

            return super.subMouseClicked(context);
        }

        /* 点击单元格 */
        if (!this.category.visible.get())
        {
            return super.subMouseClicked(context);
        }

        int col = mx / cellW;
        int row = my / cellH;

        if (col < 0 || col >= cols)
        {
            return super.subMouseClicked(context);
        }

        int index = row * cols + col;
        List<Form> forms = this.getForms();

        if (index >= 0 && index < forms.size())
        {
            Form form = forms.get(index);

            if (context.mouseButton == 0)
            {
                /* 左键：选中模型并记录拖拽起点（toggle=true 通知 palette） */
                this.select(form, true);
                draggingForm = form;
                return true;
            }
            else if (context.mouseButton == 1)
            {
                /* 右键：选中模型，但返回 false 不消费事件，让框架显示右键菜单。
                 * 不调用 super.subMouseClicked() 避免使用原版网格尺寸重新计算导致取消选中。 */
                this.select(form, true);
                return false;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        int contentW = this.area.w;
        List<Form> forms = this.getForms();
        int height = this.contentSize();

        /* 搜索无结果时调整高度 */
        if (this.hasSearch() && forms.isEmpty())
        {
            if (this.bbsppLastHeight != height)
            {
                this.bbsppLastHeight = height;
                this.h(height);

                /* 只标记布局失效，不要在这里直接 resize 父容器。
                 * 本方法运行在渲染中途，父容器此刻正遍历子元素；在遍历里再触发一次
                 * resize 会重入列布局，把游标推进两遍，后面的兄弟元素被甩到很下面
                 * （表现为内容区顶部多出一块空白）。BBS 的 UIItemGrid.syncHeight 注释
                 * 里专门警告过这一点，它自己用的就是 invalidateLayout()。 */
                if (this.getParentContainer() != null)
                {
                    this.getParentContainer().invalidateLayout();
                }
            }
            return;
        }

        /* 不调用 super.render(context)，避免父类 UIFormCategory 渲染网格模式导致重叠。
           手动处理 UIElement 层面的基础渲染：tooltip 和子元素。 */
        this.renderBaseElement(context);

        /* 渲染标题 */
        context.batcher.textCard(this.category.getProcessedTitle(),
            this.area.x + 26, this.area.y + 6);

        /* 渲染展开/折叠箭头 */
        if (this.category.visible.get())
        {
            context.batcher.icon(Icons.MOVE_DOWN, this.area.x + 16, this.area.y + 5, 0.5F, 0F);
        }
        else
        {
            context.batcher.icon(Icons.MOVE_UP, this.area.x + 16, this.area.y + 4, 0.5F, 0F);
        }

        if (!forms.isEmpty() && this.category.visible.get())
        {
            int cols = this.getColumns(contentW);
            int cellW = this.getCellWidth();
            int cellH = this.getCellHeight();
            int x = 0;
            int y = HEADER_H;
            int col = 0;

            for (Form form : forms)
            {
                int cellX = this.area.x + x;
                int cellY = this.area.y + y;
                boolean selected = this.selected == form;

                /* 裁剪区域 */
                context.batcher.clip(cellX, cellY, cellX + cellW, cellY + cellH, context);

                /* 选中高亮 */
                if (selected)
                {
                    int color = Colors.A75 | BBSSettings.primaryColor.get();
                    context.batcher.box(cellX, cellY, cellX + cellW, cellY + cellH, color);
                    context.batcher.outline(cellX, cellY, cellX + cellW, cellY + cellH, color, 2);
                }

                if (this.listMode)
                {
                    /* 列表模式：左侧模型预览（正方形），右侧名称和ID */
                    int modelSize = cellH - 8;
                    int modelX = cellX + 4;
                    int modelY = cellY + 4;

                    FormUtilsClient.renderUI(form, context, modelX, modelY, modelX + modelSize, modelY + modelSize);

                    /* 右侧文字 */
                    String name = form.getDisplayName();
                    int textX = modelX + modelSize + 8;
                    int textY = cellY + 6;
                    int maxTextW = cellW - modelSize - 16;

                    if (context.batcher.getFont().getWidth(name) > maxTextW)
                    {
                        name = context.batcher.getFont().limitToWidth(name, maxTextW);
                    }

                    context.batcher.textShadow(name, textX, textY, selected ? Colors.WHITE : Colors.LIGHTEST_GRAY);

                    /* 显示表单 ID */
                    String id = form.getFormId();
                    int idY = textY + context.batcher.getFont().getHeight() + 2;
                    if (context.batcher.getFont().getWidth(id) > maxTextW)
                    {
                        id = context.batcher.getFont().limitToWidth(id, maxTextW);
                    }
                    context.batcher.textShadow(id, textX, idY, Colors.GRAY);
                }
                else
                {
                    /* 网格模式：鼠标悬停时在上方显示ID与路径，否则模型占满单元格 */
                    boolean hovered = context.mouseX >= cellX && context.mouseX < cellX + cellW
                                   && context.mouseY >= cellY && context.mouseY < cellY + cellH;

                    if (hovered)
                    {
                        String modelName = form.getDisplayName();
                        String modelId = form.getFormId();

                        int textH = context.batcher.getFont().getHeight();
                        int idY = cellY + 2;
                        int pathY = idY + textH + 1;
                        int modelTop = pathY + textH + 2;

                        /* 先渲染模型 */
                        FormUtilsClient.renderUI(form, context, cellX, modelTop, cellX + cellW, cellY + cellH);

                        /* 再渲染模型名称（截断到单元格宽度） */
                        if (context.batcher.getFont().getWidth(modelName) > cellW - 4)
                        {
                            modelName = context.batcher.getFont().limitToWidth(modelName, cellW - 4);
                        }
                        int idX = cellX + (cellW - context.batcher.getFont().getWidth(modelName)) / 2;
                        context.batcher.textShadow(modelName, idX, idY, selected ? Colors.WHITE : Colors.LIGHTEST_GRAY);

                        /* 再渲染模型ID（灰色小字，截断到单元格宽度） */
                        if (context.batcher.getFont().getWidth(modelId) > cellW - 4)
                        {
                            modelId = context.batcher.getFont().limitToWidth(modelId, cellW - 4);
                        }
                        int pathX = cellX + (cellW - context.batcher.getFont().getWidth(modelId)) / 2;
                        context.batcher.textShadow(modelId, pathX, pathY, Colors.GRAY);
                    }
                    else
                    {
                        /* 未悬停：模型占满整个单元格 */
                        FormUtilsClient.renderUI(form, context, cellX, cellY, cellX + cellW, cellY + cellH);
                    }
                }

                context.batcher.unclip(context);

                /* 拖拽中的表单绘制半透明遮罩 */
                if (draggingForm == form)
                {
                    context.batcher.box(cellX, cellY, cellX + cellW, cellY + cellH, Colors.A50);
                }

                x += cellW;
                col++;
                if (col >= cols)
                {
                    x = 0;
                    col = 0;
                    y += cellH;
                }
            }
        }

        /* 更新高度 */
        if (this.bbsppLastHeight != height)
        {
            this.bbsppLastHeight = height;
            if (this.getParentContainer() != null)
            {
                this.h(height);

                /* 同上：渲染中途只标记布局失效，避免重入父容器的列布局把内容整体推下去。 */
                this.getParentContainer().invalidateLayout();
            }
        }
    }

    /**
     * 手动执行 UIElement 层面的基础渲染（tooltip、子元素），
     * 绕过 UIFormCategory.render() 避免网格模式重叠。
     */
    private void renderBaseElement(UIContext context)
    {
        /* 处理 tooltip */
        if (this.tooltip != null && this.area.isInside(context))
        {
            context.tooltip.set(context, this);
        }
        else if (!this.isContainer() && this.area.isInside(context))
        {
            context.resetTooltip();
        }

        /* 渲染子元素（UIFormCategory 通常没有子元素，但保持兼容） */
        for (IUIElement child : this.getChildren())
        {
            if (child.isVisible() && child.canBeRendered(context.getViewport()))
            {
                child.render(context);
            }
        }
    }

    /**
     * 判断该分类是否为用户可修改分类（内置分类的模型不可移动）。
     * 用户分类包括 UserFormCategory 和 ModelFormCategory（model 文件夹下的模型分类）。
     */
    public boolean isUserCategory()
    {
        return this.category instanceof UserFormCategory
            || this.category instanceof ModelFormCategory;
    }
}
