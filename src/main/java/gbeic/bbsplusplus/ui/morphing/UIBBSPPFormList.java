package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.mixin.UIFormListAccessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import gbeic.bbsplusplus.BBSPlusPlusMod;

/**
 * 改进伪装表单列表, 具有侧边导航栏、首页、排版切换、图标缩放、拖拽移动和模型预览。
 */
public class UIBBSPPFormList extends UIFormList
{
    /** 顶部工具条高度，与原生 {@link UIFormList#BAR_HEIGHT} 对齐。 */
    private static final int BAR_H = UIFormList.BAR_HEIGHT;

    /**
     * 原生状态条高度 —— 即「当前伪装」那一条。
     *
     * <p>原生 {@code UIFormList.renderStatus()} 把它画在 {@code area.y + BAR_HEIGHT} 起的
     * {@link UIFormList#STATUS_HEIGHT} 像素带里，而且绘制在 {@code super.render()} **之前**，
     * 于是任何从它上面盖过去的子元素都会把它压在页面底层。本类原先把侧边栏与内容区
     * 都放在 {@code y = 24}，正好盖掉状态条的 24~36 段，问题即源于此。</p>
     */
    private static final int STATUS_H = UIFormList.STATUS_HEIGHT;

    /**
     * 侧边栏 / 内容区的起始 y。
     *
     * <p>由 {@link #BAR_H} + {@link #STATUS_H} 组成，把状态条那一条完整让出来，
     * 使「当前伪装」显示在模型（表单）栏的正上方，而不是被压在底层。</p>
     */
    private static final int CONTENT_Y = BAR_H + STATUS_H;

    public UIBBSPPCategorySidebar sidebar;
    public UIBBSPPCategoryHome home;
    public UIElement contentArea;
    public UIIcon openModelsBtn;
    public UIIcon layoutToggleBtn;

    /** 自定义分类列表（支持列表/网格模式） */
    private final List<UIBBSPPFormCategory> bbsppCategories = new ArrayList<>();

    public UIBBSPPFormList(IUIFormList palette)
    {
        super(palette);

        this.removeAll();

        // 1. 设置顶部栏
        this.bar.resetFlex();
        this.bar.relative(this).x(0).y(0).w(1F).h(BAR_H).row(4).height(BAR_H);
        this.search.h(18);

        this.openModelsBtn = new UIIcon(
            Icons.FOLDER,
            (b) -> mchorse.bbs_mod.ui.utils.UIUtils.openFolder(BBSMod.getAssetsPath("models"))
        );
        this.openModelsBtn.tooltip(L10n.lang("studio.ui.utility.open_models_folder"));
        this.openModelsBtn.w(20);

        // 排版切换按钮
        this.layoutToggleBtn = new UIIcon(this.getCurrentLayoutIcon(), null);
        this.layoutToggleBtn.callback = (b) -> this.showLayoutMenu();
        this.layoutToggleBtn.tooltip(L10n.lang("bbspp.ui.morph.layout_toggle"));
        this.layoutToggleBtn.w(20);

        // 重新排列顶部栏按钮
        this.bar.removeAll();
        if (this.categoryFilter != null)
        {
            this.bar.add(this.categoryFilter);
        }
        this.bar.add(this.search, this.edit, this.layoutToggleBtn, this.openModelsBtn, this.close);

        // 2. 设置侧边栏
        this.sidebar = new UIBBSPPCategorySidebar(this::onSidebarSelect);
        this.sidebar.relative(this).x(0).y(CONTENT_Y).w(120).h(1F, -CONTENT_Y);

        // 3. 设置内容区域（右侧）
        this.contentArea = new UIElement();

        // 4. 设置首页
        this.home = new UIBBSPPCategoryHome(this::onHomeCategorySelect);

        /* 注意：这里不能用 full(parent)。
         * UIElement.full() 的实现是 relative(p).wh(1F, 1F) —— 它只改宽高，**不会清零 x/y 偏移**。
         * 而 forms 在原生 UIFormList 构造器里已经被设过 xy(0, BAR_HEIGHT + STATUS_HEIGHT)，
         * 那个 y=36 的偏移会原样保留；contentArea 本身又在 y=36，于是 forms 实际落在 72px 处，
         * 表现为「模型栏上方多出一块空白」。所以这里显式把 x/y 一起清掉。 */
        this.home.relative(this.contentArea).x(0).y(0).w(1F).h(1F);

        // 5. 设置表单（原始的 scrollview）
        this.forms.relative(this.contentArea).x(0).y(0).w(1F).h(1F);

        this.contentArea.add(this.home, this.forms);
        this.add(this.bar, this.sidebar, this.contentArea);
    }

    /**
     * 获取当前排版模式对应的图标。
     */
    private mchorse.bbs_mod.ui.utils.icons.Icon getCurrentLayoutIcon()
    {
        return isListMode() ? Icons.LIST : Icons.LAYOUT;
    }

    private boolean isListMode()
    {
        return BBSAddonsSettings.morphingLayoutMode != null
            && BBSAddonsSettings.morphingLayoutMode.get() == 0;
    }

    private int getIconScale()
    {
        return BBSAddonsSettings.morphingIconScale != null
            ? BBSAddonsSettings.morphingIconScale.get() : 100;
    }

    /**
     * 显示排版切换的右键菜单。
     */
    private void showLayoutMenu()
    {
        this.layoutToggleBtn.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.LIST, L10n.lang("bbspp.ui.morph.layout_list"), () ->
            {
                this.setLayoutMode(0);
            });
            menu.action(Icons.LAYOUT, L10n.lang("bbspp.ui.morph.layout_grid"), () ->
            {
                this.setLayoutMode(1);
            });
        });
    }

    private void setLayoutMode(int mode)
    {
        if (BBSAddonsSettings.morphingLayoutMode != null)
        {
            BBSAddonsSettings.morphingLayoutMode.set(mode);
        }

        this.layoutToggleBtn.both(this.getCurrentLayoutIcon());
        this.updateCategoryLayout();
        this.forms.resize();
    }

    /**
     * 更新所有自定义分类的排版模式和缩放。
     */
    private void updateCategoryLayout()
    {
        boolean listMode = this.isListMode();
        int scale = this.getIconScale();

        for (UIBBSPPFormCategory cat : this.bbsppCategories)
        {
            cat.listMode = listMode;
            cat.iconScale = scale;
        }
    }

    @Override
    public void resize()
    {
        if (this.sidebar != null && this.contentArea != null)
        {
            int sw = this.sidebar.getSidebarWidth();
            this.sidebar.w(sw);
            this.contentArea.relative(this).x(sw).y(CONTENT_Y).w(1F, -sw).h(1F, -CONTENT_Y);
        }

        super.resize();
    }

    private void onSidebarSelect(UIBBSPPCategorySidebar.CategoryItem item)
    {
        this.search.setText("");

        if (item == null || item.isHome)
        {
            this.home.setVisible(true);
            this.forms.setVisible(false);
        }
        else
        {
            this.home.setVisible(false);
            this.forms.setVisible(true);

            this.forms.removeAll();

            // 仅重新添加选中的类别
            for (UIBBSPPFormCategory cat : this.bbsppCategories)
            {
                boolean isSelectedCat = cat.category.visible.getId().equals(item.id);
                if (isSelectedCat)
                {
                    this.forms.add(cat);
                    cat.category.visible.set(true);
                    cat.setVisible(true);
                }
            }
            this.forms.resize();
        }

        this.resize();
    }

    private void onHomeCategorySelect(FormCategory category)
    {
        this.sidebar.select(category.visible.getId());
    }

    @Override
    public void setupForms(FormCategories formsCategories)
    {
        super.setupForms(formsCategories);

        if (this.sidebar == null) return;

        List<UIFormCategory> allCats = ((UIFormListAccessor) this).getCategories();

        // 用自定义分类替换原有分类
        this.bbsppCategories.clear();
        this.forms.removeAll();

        boolean listMode = this.isListMode();
        int scale = this.getIconScale();

        for (UIFormCategory oldCat : allCats)
        {
            UIBBSPPFormCategory newCat = new UIBBSPPFormCategory(oldCat.category, this);
            newCat.listMode = listMode;
            newCat.iconScale = scale;
            newCat.selected = oldCat.selected;
            this.bbsppCategories.add(newCat);
        }

        /* 用新分类替换父类 categories 列表，确保 getSelected() 能找到选中的分类 */
        List<UIFormCategory> parentCats = ((UIFormListAccessor) this).getCategories();
        parentCats.clear();
        parentCats.addAll(this.bbsppCategories);

        String currentSelectionId = MorphingDefaultCategory.get();

        // 填充侧边栏
        this.sidebar.clear();
        this.sidebar.addItem(null, MorphingDefaultCategory.HOME, L10n.lang("bbs.ui.bbspp.morph.home").get(), true);

        for (UIBBSPPFormCategory category : this.bbsppCategories)
        {
            this.sidebar.addItem(category, category.category.visible.getId(), category.category.getProcessedTitle(), false);
        }

        // 填充首页
        this.home.setup(this.toUIFormCategoryList());

        this.sidebar.select(currentSelectionId);

        if (this.sidebar.selected == null)
        {
            this.sidebar.select(MorphingDefaultCategory.HOME);
        }

        this.resize();
    }

    private List<UIFormCategory> toUIFormCategoryList()
    {
        List<UIFormCategory> list = new ArrayList<>();
        list.addAll(this.bbsppCategories);
        return list;
    }

    /**
     * 点击模型时实际调用的方法（而非 setSelected）。
     */
    @Override
    public void selectCategory(UIFormCategory category, Form form, boolean toggle)
    {
        super.selectCategory(category, form, toggle);
    }

    @Override
    public void deselect()
    {
        super.deselect();
    }

    /**
     * 全页面 Ctrl+滚轮缩放图标大小。
     * 在子元素处理滚轮事件之前拦截，确保只要在伪装页面内就能缩放。
     */
    @Override
    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        if (Window.isCtrlPressed() && this.forms.isVisible())
        {
            int currentScale = this.getIconScale();
            int delta = context.mouseWheel > 0 ? 10 : -10;
            int newScale = Math.max(20, Math.min(200, currentScale + delta));

            if (BBSAddonsSettings.morphingIconScale != null)
            {
                BBSAddonsSettings.morphingIconScale.set(newScale);
            }

            this.updateCategoryLayout();
            this.forms.resize();
            return this;
        }

        return super.childrenMouseScrolled(context);
    }

    private String previousSelectionId = "home";

    public void afterSearch(String raw)
    {
        if (this.sidebar == null) return;

        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty())
        {
            this.sidebar.select(this.previousSelectionId);
        }
        else
        {
            if (this.sidebar.selected != null)
            {
                this.previousSelectionId = this.sidebar.selected.id;
            }

            this.home.setVisible(false);
            this.forms.setVisible(true);

            this.forms.removeAll();
            for (UIBBSPPFormCategory cat : this.bbsppCategories)
            {
                this.forms.add(cat);
                cat.setVisible(!cat.getForms().isEmpty());
                if (cat.isVisible())
                {
                    cat.category.visible.set(true);
                }
            }

            this.sidebar.clearSelection();
            this.forms.resize();
            this.resize();
        }
    }

    /**
     * 鼠标释放时处理拖拽移动。
     */
    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (UIBBSPPFormCategory.draggingForm != null)
        {
            Form dragged = UIBBSPPFormCategory.draggingForm;
            UIBBSPPFormCategory.draggingForm = null;

            // 检查是否释放在侧边栏的分类上
            if (this.sidebar != null && this.sidebar.area.isInside(context))
            {
                UIBBSPPCategorySidebar.CategoryItem target = this.sidebar.getItemAt(context.mouseX, context.mouseY);
                if (target != null && !target.isHome && target.uiCategory instanceof UIBBSPPFormCategory targetCat)
                {
                    this.tryMoveFormToCategory(dragged, targetCat);
                }
            }
            return true;
        }

        return super.subMouseReleased(context);
    }

    /**
     * 尝试将模型移动到目标分类。
     */
    private void tryMoveFormToCategory(Form form, UIBBSPPFormCategory targetCategory)
    {
        // 找到源分类
        UIBBSPPFormCategory sourceCategory = null;
        for (UIBBSPPFormCategory cat : this.bbsppCategories)
        {
            if (cat.getForms().contains(form))
            {
                sourceCategory = cat;
                break;
            }
        }

        if (sourceCategory == null || sourceCategory == targetCategory)
        {
            return;
        }

        // 内置模型不可移动
        if (!sourceCategory.isUserCategory())
        {
            this.getContext().notifyError(L10n.lang("bbspp.ui.morph.builtin_cannot_move"));
            return;
        }

        // 目标分类必须是用户分类
        if (!targetCategory.isUserCategory())
        {
            this.getContext().notifyError(L10n.lang("bbspp.ui.morph.target_builtin"));
            return;
        }

        // 执行文件移动
        if (this.moveModelFile(form, sourceCategory, targetCategory))
        {
            // 更新分类数据
            sourceCategory.category.removeForm(form);
            targetCategory.category.addForm(form);

            // 保存用户分类
            FormCategories formCategories = BBSModClient.getFormCategories();
            if (formCategories != null)
            {
                formCategories.getUserForms().writeUserCategories();
            }

            // 刷新界面
            this.setupForms(formCategories);
            this.getContext().notifySuccess(L10n.lang("bbspp.ui.morph.move_success"));
        }
    }

    /**
     * 移动模型文件夹到目标分类对应的本地位置。
     */
    private boolean moveModelFile(Form form, UIBBSPPFormCategory source, UIBBSPPFormCategory target)
    {
        if (!(form instanceof ModelForm modelForm))
        {
            return false;
        }

        String modelId = modelForm.model.get();
        if (modelId == null || modelId.isEmpty())
        {
            return false;
        }

        try
        {
            // 获取模型根目录
            File modelsRoot = BBSMod.getAssetsPath("models");
            File sourceFolder = new File(modelsRoot, modelId);

            if (!sourceFolder.exists() || !sourceFolder.isDirectory())
            {
                return false;
            }

            // 从分类 ID 中提取实际文件夹名（去掉 "模型/" 或 "models/" 前缀）
            String targetCategoryId = target.category.visible.getId();
            String targetFolderName = targetCategoryId;
            if (targetFolderName.startsWith("模型/"))
            {
                targetFolderName = targetFolderName.substring("模型/".length());
            }
            else if (targetFolderName.startsWith("models/"))
            {
                targetFolderName = targetFolderName.substring("models/".length());
            }

            File targetRoot = new File(modelsRoot, targetFolderName);
            if (!targetRoot.exists())
            {
                targetRoot.mkdirs();
            }

            File targetFolder = new File(targetRoot, sourceFolder.getName());

            if (targetFolder.exists())
            {
                this.getContext().notifyError(L10n.lang("bbspp.ui.morph.target_exists"));
                return false;
            }

            // 执行移动（剪切）
            Files.move(sourceFolder.toPath(), targetFolder.toPath(), StandardCopyOption.ATOMIC_MOVE);

            // 更新模型表单中的路径引用
            String newModelPath = targetFolderName + "/" + sourceFolder.getName();
            modelForm.model.set(newModelPath);

            return true;
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("伪装列表：拖拽移动模型失败", e);
            this.getContext().notifyError(L10n.lang("bbspp.ui.morph.move_failed"));
            return false;
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* 拖拽模型时显示跟随鼠标的模型ID */
        if (UIBBSPPFormCategory.draggingForm != null)
        {
            Form dragged = UIBBSPPFormCategory.draggingForm;
            String label = dragged.getDisplayName();
            int textW = context.batcher.getFont().getWidth(label);
            int bx = context.mouseX + 12;
            int by = context.mouseY + 12;

            /* 背景框 */
            context.batcher.box(bx - 4, by - 2, bx + textW + 4, by + context.batcher.getFont().getHeight() + 2, Colors.A75);
            context.batcher.outline(bx - 4, by - 2, bx + textW + 4, by + context.batcher.getFont().getHeight() + 2, Colors.WHITE, 1);

            /* 文字 */
            context.batcher.textShadow(label, bx, by, Colors.WHITE);
        }

        /* 在侧边栏底部渲染表单的显示名称和ID */
        Form selected = this.getSelected();

        if (selected != null && this.sidebar != null && this.sidebar.area.w > 0)
        {
            String displayName = selected.getDisplayName();
            String id = selected.getFormId();
            FontRenderer font = context.batcher.getFont();

            int maxTextW = this.sidebar.area.w - 20;

            String drawName = font.limitToWidth(displayName, maxTextW);
            String drawId = font.limitToWidth(id, maxTextW);

            int w = this.sidebar.area.w - 4;
            int x = this.sidebar.area.x;
            int h = 32;
            int y = this.sidebar.area.ey() - h;

            context.batcher.box(x, y, x + w, y + h, Colors.A75);
            context.batcher.textShadow(drawName, x + 8, y + 6);
            context.batcher.textShadow(drawId, x + 8, y + 18, Colors.LIGHTEST_GRAY);

            if (context.mouseX >= x && context.mouseX <= x + w && context.mouseY >= y && context.mouseY <= y + h)
            {
                if (font.getWidth(displayName) > maxTextW || font.getWidth(id) > maxTextW)
                {
                    int tooltipW = Math.max(font.getWidth(displayName), font.getWidth(id));
                    this.tooltip(mchorse.bbs_mod.l10n.keys.IKey.raw(displayName + "\n" + id), tooltipW, mchorse.bbs_mod.utils.Direction.TOP);
                    context.tooltip.set(context, this);
                    context.tooltip.area.set(x, y, w, h);
                }
            }
            else if (context.tooltip.element == this)
            {
                this.tooltip((mchorse.bbs_mod.ui.framework.tooltips.ITooltip) null);
                context.tooltip.set(context, null);
            }
        }
    }
}
