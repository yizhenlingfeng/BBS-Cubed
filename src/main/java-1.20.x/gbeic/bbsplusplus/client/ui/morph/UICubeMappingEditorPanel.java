package gbeic.bbsplusplus.client.ui.morph;

import gbeic.bbsplusplus.client.morph.MappingGroup;
import gbeic.bbsplusplus.client.morph.ModelMorphAdapter;
import gbeic.bbsplusplus.client.morph.MorphScreenPicks;
import gbeic.bbsplusplus.client.morph.ModelMorphData;
import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import gbeic.bbsplusplus.client.morph.ModelMorphSkeleton;
import gbeic.bbsplusplus.client.morph.ModelMorphState;
import gbeic.bbsplusplus.client.morph.ModelMorphValidation;
import gbeic.bbsplusplus.client.morph.MorphAutoMatch;
import gbeic.bbsplusplus.client.texture.ModelBindingGeometry;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelTopBar;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import gbeic.bbsplusplus.client.morph.MorphMappingPresets;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cube 映射可视化编辑器。
 *
 * <p>MC 模型的形变需要「源模型的某个 Cube 对应目标模型的哪些 Cube」这一层信息，
 * 而手写 ID 或 JSON 对普通用户不可用。本面板把两侧模型的骨骼/Cube 树并排展示，
 * 用户在多选列表里点选后再按「建立映射」即可生成 1:1 / 1:N / N:1 三类的映射组，
 * 因此映射关系始终由选择数量推导，不需要用户理解映射类型本身。</p>
 *
 * <p>面板只操作传进来的 {@link ModelMorphData} 工作副本（原地修改），每次改动后调用
 * {@code onChange}，由调用方决定如何写回关键帧。这样编辑器本身不依赖关键帧/轨道实现，
 * 也能用于任何需要编辑映射的入口。</p>
 *
 * <p>N:N 在运行时没有定义（一个 Cube 不能同时分叉又汇聚），所以这里直接拒绝创建，
 * 并提示用户拆成 1:N 与 N:1 —— 而不是让非法数据进入渲染管线后才报错。</p>
 */
public final class UICubeMappingEditorPanel extends UIDashboardPanel
{
    /* ---- 布局常量：全部使用固定像素高度 + 相对宽度，避免 BBS 布局器在不同面板尺寸下的分配差异 ---- */

    private static final int MARGIN = 4;
    private static final int HEADER_H = 20;
    private static final float PREVIEW_ENDPOINT_EPSILON = 1.0E-4F;
    private static final String DOCK_LAYOUT_ID = "bbsppp.morph";
    private static final String SOURCE_PREVIEW_PANEL = "source_preview";
    private static final String SOURCE_TREE_PANEL = "source_tree";
    private static final String TARGET_PREVIEW_PANEL = "target_preview";
    private static final String TARGET_TREE_PANEL = "target_tree";
    private static final String MORPH_PREVIEW_PANEL = "morph_preview";
    private static final String MAPPING_PANEL = "mapping";
    private static final float PREVIEW_PLAYBACK_SPEED = 0.25F;

    /** 两侧可拾取视口占搜索框以下区域的比例；列表至少保留约五行。 */

    /** 与 BBS 字符串列表一致的行高，保证树与映射卡片在视觉上同一节奏。 */
    private static final int ROW_H = UIStringList.DEFAULT_HEIGHT;

    private final ModelMorphData data;
    private final String sourceId;
    private final String targetId;
    private final Runnable onChange;
    private final UIPanelTopBar topBar;
    private final UIElement content;
    private final UIDockLayout dock;
    private final UIElement sourcePreviewPanel;
    private final UIElement sourceTreePanel;
    private final UIElement targetPreviewPanel;
    private final UIElement targetTreePanel;
    private final UIElement morphPreviewPanel;
    private final UIElement mappingPanel;
    private final UIIcon dockLockIcon;
    private final UIIcon dockResetIcon;
    private final UILabel pageTitle;
    private final UIIcon close;

    /** 模型未加载或没有 Cube 时为 null：面板据此显示「找不到模型」而不是崩溃。 */
    private final ModelMorphSkeleton sourceSkeleton;
    private final ModelMorphSkeleton targetSkeleton;

    private final UILabel sourceLabel;
    private final UILabel targetLabel;
    private final UILabel draftSourceLabel;
    private final UILabel draftTargetLabel;
    private final UILabel draftSourceDetailLabel;
    private final UILabel draftTargetDetailLabel;
    private final UILabel draftTypeLabel;
    private final UIButton draftCreateButton;
    private final UIIcon auto;
    private final UIIcon add;
    private final UIIcon clear;
    private final UIIcon remove;

    private final CubeList sourceList;
    private final CubeList targetList;
    private final MappingList mappingList;
    private final UILabel mappingEmptyLabel;
    private final MorphPreview preview;
    private final MorphPreview sourcePicker;
    private final MorphPreview targetPicker;
    private final UILabel summaryLabel;
    private final UILabel detailLabel;
    private final UIToggle allowIncomplete;

    /** 当前选中的映射组 id。用 id 而不是对象引用，因为 setGroups() 会重建映射组副本。 */
    private String selectedGroupId;
    private float progress;

    private ModelMorphValidation.Result validation;
    private long lastPreviewUpdateNanos;

    /** 撤销/重做栈：保存整份映射数据的快照。映射数据很小，快照足够廉价。 */
    private final java.util.Deque<ModelMorphData> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<ModelMorphData> redoStack = new java.util.ArrayDeque<>();

    /** 栈深度上限，防止长时间编辑占满内存。 */
    private static final int UNDO_LIMIT = 64;

    private Set<String> selectedSources = Set.of();
    private Set<String> selectedTargets = Set.of();
    private Set<String> invalidSources = Set.of();
    private Set<String> invalidTargets = Set.of();
    private Set<String> mappedSources = Set.of();
    private Set<String> mappedTargets = Set.of();

    public UICubeMappingEditorPanel(UIDashboard dashboard, ModelMorphData data,
                                    String sourceId, String targetId, Runnable onChange)
    {
        super(dashboard);

        this.topBar = new UIPanelTopBar();
        this.content = new UIElement();
        this.dock = new UIDockLayout();
        this.sourcePreviewPanel = new UIElement();
        this.sourceTreePanel = new UIElement();
        this.targetPreviewPanel = new UIElement();
        this.targetTreePanel = new UIElement();
        this.morphPreviewPanel = new UIElement();
        this.mappingPanel = new UIElement();
        this.pageTitle = UI.label(L10n.lang("bbsppp.ui.cube_mapping.title"));
        this.close = new UIIcon(Icons.CLOSE, (button) -> dashboard.closeThisMenu());
        this.pageTitle.labelAnchor(0F, 0.5F);
        this.close.tooltip(L10n.lang("bbsppp.ui.cube_mapping.close"));
        this.dockLockIcon = new UIIcon(
            () -> this.dock.isLocked() ? Icons.LOCKED : Icons.UNLOCKED,
            (button) -> this.toggleDockLock());
        this.dockResetIcon = new UIIcon(Icons.REFRESH, (button) -> this.dock.resetLayout());

        this.data = data == null ? new ModelMorphData() : data;
        this.sourceId = sourceId == null ? "" : sourceId;
        this.targetId = targetId == null ? "" : targetId;
        this.onChange = onChange;
        this.sourceSkeleton = skeletonOf(this.sourceId);
        this.targetSkeleton = skeletonOf(this.targetId);

        this.sourceLabel = UI.label(IKey.raw(this.modelLabel("bbsppp.ui.cube_mapping.source", this.sourceId)));
        this.targetLabel = UI.label(IKey.raw(this.modelLabel("bbsppp.ui.cube_mapping.target", this.targetId)));
        this.draftSourceLabel = UI.label(IKey.EMPTY);
        this.draftTargetLabel = UI.label(IKey.EMPTY);
        this.draftSourceDetailLabel = UI.label(IKey.EMPTY);
        this.draftTargetDetailLabel = UI.label(IKey.EMPTY);
        this.draftTypeLabel = UI.label(IKey.EMPTY);

        this.auto = new UIIcon(Icons.REFRESH, this::autoMatch);
        this.auto.tooltip(L10n.lang("bbsppp.ui.cube_mapping.auto"));
        this.add = new UIIcon(Icons.LINK, this::createMapping);
        this.add.tooltip(L10n.lang("bbsppp.ui.cube_mapping.add"));
        this.clear = new UIIcon(Icons.TRASH, this::clearMappings);
        this.clear.tooltip(L10n.lang("bbsppp.ui.cube_mapping.clear"));
        this.remove = new UIIcon(Icons.REMOVE, this::removeSelectedMapping);
        this.remove.tooltip(L10n.lang("bbsppp.ui.cube_mapping.remove"));
        this.draftCreateButton = new UIButton(L10n.lang("bbsppp.ui.cube_mapping.add"), (button) -> this.createMapping());
        this.dockLockIcon.tooltip(L10n.lang("bbsppp.ui.cube_mapping.layout_lock"));
        this.dockResetIcon.tooltip(L10n.lang("bbsppp.ui.cube_mapping.layout_reset"));

        this.topBar.actions.action(this.auto).action(this.add).action(this.remove).action(this.clear)
            .action(this.dockResetIcon)
            .action(this.dockLockIcon)
            .common(this.close);
        this.pageTitle.relative(this.topBar).x(8).y(0).h(UIPanelTopBar.HEIGHT);
        this.topBar.add(this.pageTitle);
        this.topBar.relative(this).w(1F).h(UIPanelTopBar.HEIGHT);
        this.content.relative(this).y(UIPanelTopBar.HEIGHT).w(1F).h(1F, -UIPanelTopBar.HEIGHT);
        this.add(this.topBar, this.content);

        this.sourceList = new CubeList(this.sourceSkeleton, true, this::onTreeChanged);
        this.targetList = new CubeList(this.targetSkeleton, false, this::onTreeChanged);
        this.mappingList = new MappingList();
        this.mappingEmptyLabel = UI.label(L10n.lang("bbsppp.ui.cube_mapping.mapping_empty"));

        this.summaryLabel = UI.label(IKey.EMPTY);
        this.detailLabel = UI.label(IKey.EMPTY);

        this.allowIncomplete = new UIToggle(L10n.lang("bbsppp.ui.cube_mapping.allow_incomplete"),
            this.data.allowIncomplete(), (toggle) -> this.setAllowIncomplete(toggle.getValue()));
        this.allowIncomplete.tooltip(L10n.lang("bbsppp.ui.cube_mapping.allow_incomplete.tooltip"));

        this.preview = new MorphPreview();
        this.preview.setPickHandler(this::onPreviewPick);

        /* 左右各一个可拾取 3D 预览：用户对着源模型点选源 Cube、对着目标模型点选目标 Cube。
           与右下角的形变预览分开——那个用来检查过渡效果，这两个用来选块。 */
        this.sourcePicker = new MorphPreview();
        this.sourcePicker.setShowTarget(false);
        this.sourcePicker.setPickHandler(this::onPreviewPick);
        this.targetPicker = new MorphPreview();
        this.targetPicker.setShowTarget(true);
        this.targetPicker.setPickHandler(this::onPreviewPick);

        this.buildLayout();
        this.rebuildMappings();
        this.refreshValidation();
        this.refreshButtons();
        this.refreshPreview();
    }

    /** Open as a full-page editor hosted by BBS's dashboard shell. */
    public static void open(ModelMorphData data, String sourceId, String targetId, Runnable onChange)
    {
        UIBaseMenu previousMenu = UIScreen.getCurrentMenu();
        MappingDashboard dashboard = new MappingDashboard(previousMenu);
        UICubeMappingEditorPanel editor = new UICubeMappingEditorPanel(dashboard, data, sourceId, targetId, onChange);

        dashboard.getPanels().registerPanel(editor, L10n.lang("bbsppp.ui.cube_mapping.title"), Icons.POSE);
        dashboard.setEditorPanel(editor);
        UIScreen.open(dashboard);
    }

    static String previewModelId(boolean showTarget, String sourceId, String targetId, float progress,
                                 boolean sourceLoaded, boolean targetLoaded)
    {
        if (showTarget || sourceId == null || sourceId.isEmpty() || !sourceLoaded)
        {
            return targetId;
        }

        return targetLoaded && progress >= 1F - PREVIEW_ENDPOINT_EPSILON ? targetId : sourceId;
    }

    static boolean shouldRefitCamera(String previousSourceId, String previousTargetId,
                                     String sourceId, String targetId)
    {
        return !Objects.equals(previousSourceId, sourceId) || !Objects.equals(previousTargetId, targetId);
    }

    /* ---------- 布局 ---------- */

    private void buildLayout()
    {
        this.buildPreviewPanel(this.sourcePreviewPanel, this.sourcePicker, this.sourceLabel);
        this.buildTreePanel(this.sourceTreePanel, this.sourceList, this.sourceStructureTitle());
        this.buildPreviewPanel(this.targetPreviewPanel, this.targetPicker, this.targetLabel);
        this.buildTreePanel(this.targetTreePanel, this.targetList, this.targetStructureTitle());
        this.buildMorphPreviewPanel(this.morphPreviewPanel, this.preview);
        this.buildMappingColumn(this.mappingPanel);
        this.configureDock();

        this.content.add(this.dock);

        /* 复用 BBS 官方的撤销快捷键捕获器（Ctrl+Z / Ctrl+Y），
           与原版编辑器行为一致。 */
        this.content.add(new mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys(this::undo, this::redo)
            .full(this.content));

    }

    private UILabel sourceStructureTitle()
    {
        return UI.label(L10n.lang("bbsppp.ui.cube_mapping.source_structure"));
    }

    private UILabel targetStructureTitle()
    {
        return UI.label(L10n.lang("bbsppp.ui.cube_mapping.target_structure"));
    }

    private void buildPreviewPanel(UIElement panel, MorphPreview picker, UILabel title)
    {
        this.cardBackground(panel);
        title.relative(panel).x(6).y(2).w(1F, -12).h(HEADER_H);
        picker.relative(panel).x(0).y(HEADER_H).w(1F).h(1F, -HEADER_H);
        panel.add(title, picker);
    }

    private void buildMorphPreviewPanel(UIElement panel, MorphPreview morphPreview)
    {
        this.cardBackground(panel);
        UILabel title = UI.label(L10n.lang("bbsppp.ui.cube_mapping.morph_preview"));

        title.relative(panel).x(6).y(2).w(1F, -12).h(HEADER_H);
        morphPreview.relative(panel).x(0).y(HEADER_H).w(1F).h(1F, -HEADER_H);
        panel.add(title, morphPreview);
    }

    private void buildTreePanel(UIElement panel, CubeList list, UILabel title)
    {
        UITextbox search = new UITextbox(200, list::filter);

        this.cardBackground(panel);
        title.relative(panel).x(6).y(2).w(1F, -12).h(HEADER_H);
        search.placeholder(L10n.lang("bbsppp.ui.cube_mapping.search"));
        search.relative(panel).x(4).y(HEADER_H + MARGIN).w(1F, -8).h(HEADER_H);
        list.relative(panel).x(0).y(HEADER_H * 2 + MARGIN * 2).w(1F)
            .h(1F, HEADER_H * 2 + MARGIN * 2);

        panel.add(title, search, list);
    }

    private void cardBackground(UIElement card)
    {
        card.add(new UIRenderable(context ->
        {
            context.batcher.box(card.area.x, card.area.y, card.area.ex(), card.area.ey(), BBSSettings.deepSurface());
            context.batcher.outline(card.area.x, card.area.y, card.area.ex(), card.area.ey(), Colors.A25);
        }));
    }

    private void configureDock()
    {
        this.dock.relative(this.content).w(1F).h(1F);
        this.dock.source(this.createDockLayoutSource())
            .locked(!BBSSettings.editorLayoutSettings.isDockUnlocked(DOCK_LAYOUT_ID))
            .ensure(this::ensureDockLayoutPanels)
            .onLayoutSettled(this::refreshPreview)
            .addPanel(SOURCE_PREVIEW_PANEL, this.sourcePreviewPanel, Icons.VIDEO_CAMERA,
                L10n.lang("bbsppp.ui.cube_mapping.source"))
            .addPanel(SOURCE_TREE_PANEL, this.sourceTreePanel, Icons.LIST,
                L10n.lang("bbsppp.ui.cube_mapping.source_structure"))
            .addPanel(TARGET_PREVIEW_PANEL, this.targetPreviewPanel, Icons.VIDEO_CAMERA,
                L10n.lang("bbsppp.ui.cube_mapping.target"))
            .addPanel(TARGET_TREE_PANEL, this.targetTreePanel, Icons.LIST,
                L10n.lang("bbsppp.ui.cube_mapping.target_structure"))
            .addPanel(MORPH_PREVIEW_PANEL, this.morphPreviewPanel, Icons.PLAY,
                L10n.lang("bbsppp.ui.cube_mapping.morph_preview"))
            .addPanel(MAPPING_PANEL, this.mappingPanel, Icons.LINK,
                L10n.lang("bbsppp.ui.cube_mapping.mappings"));
        this.dock.mount();
    }

    private EditorLayoutNode ensureDockLayoutPanels(EditorLayoutNode root)
    {
        Set<String> panelIds = new LinkedHashSet<>();

        EditorLayoutNode.collectPanelIds(root, panelIds);

        /* The previous five-panel layout has no morph preview node. Reset it once so the new
           six-panel arrangement starts in the intended three-column grid. */
        return panelIds.contains(MORPH_PREVIEW_PANEL) ? root : defaultDockLayout();
    }

    private ILayoutSource createDockLayoutSource()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        return new ILayoutSource()
        {
            @Override
            public EditorLayoutNode getRoot()
            {
                return layout.getLayout(DOCK_LAYOUT_ID, UICubeMappingEditorPanel::defaultDockLayout);
            }

            @Override
            public void setRoot(EditorLayoutNode root)
            {
                layout.setLayout(DOCK_LAYOUT_ID, root);
            }

            @Override
            public EditorLayoutNode getDefault()
            {
                return defaultDockLayout();
            }

            @Override
            public Set<String> getHiddenPanels()
            {
                return layout.getHiddenPanels(DOCK_LAYOUT_ID);
            }

            @Override
            public void setHiddenPanels(Set<String> hidden)
            {
                layout.setHiddenPanels(DOCK_LAYOUT_ID, hidden);
            }
        };
    }

    private static EditorLayoutNode defaultDockLayout()
    {
        EditorLayoutNode sourcePair = new EditorLayoutNode.SplitterNode(true, 0.56F,
            new EditorLayoutNode.PanelNode(SOURCE_PREVIEW_PANEL),
            new EditorLayoutNode.PanelNode(SOURCE_TREE_PANEL));
        EditorLayoutNode targetPair = new EditorLayoutNode.SplitterNode(true, 0.56F,
            new EditorLayoutNode.PanelNode(TARGET_PREVIEW_PANEL),
            new EditorLayoutNode.PanelNode(TARGET_TREE_PANEL));
        EditorLayoutNode morphPair = new EditorLayoutNode.SplitterNode(true, 0.56F,
            new EditorLayoutNode.PanelNode(MORPH_PREVIEW_PANEL),
            new EditorLayoutNode.PanelNode(MAPPING_PANEL));
        EditorLayoutNode sourceTarget = new EditorLayoutNode.SplitterNode(false, 0.5F, sourcePair, targetPair);

        return new EditorLayoutNode.SplitterNode(false, 0.667F, sourceTarget, morphPair);
    }

    private void toggleDockLock()
    {
        this.dock.toggleLock();
        BBSSettings.editorLayoutSettings.setDockUnlocked(DOCK_LAYOUT_ID, !this.dock.isLocked());
        this.dock.refreshVisibility();
    }

    /**
     * 一侧的树：搜索框 + 自带滚动的列表。
     *
     * <p>不再套一层 {@code UIScrollView}：{@link UIList} 本身已经是可滚动列表，
     * 嵌套滚动容器会让滚轮事件互相吃掉，并且列表无法撑满剩余高度。</p>
     */
    /**
     * 一侧的树列：搜索框 + 3D 拾取视口 + Cube 列表。
     *
     * <p>3D 视口与列表按<b>比例</b>分配搜索框以下的全部高度，而不是给视口固定像素。
     * 固定像素时窗口一变矮，列表就会被压到只剩一两行——那正是「元素溢出」的成因。</p>
     */
    @Override
    public void resize()
    {
        super.resize();

        /* Let the BBS action bar measure itself first, then give the title the remaining width. */
        int titleWidth = Math.max(0, this.topBar.area.w - this.topBar.actions.getContentWidth() - 16);

        this.pageTitle.w(titleWidth);
        this.pageTitle.resize();
        this.content.resize();

    }

    @Override
    public void update()
    {
        super.update();

        long now = System.nanoTime();

        if (this.lastPreviewUpdateNanos == 0L)
        {
            this.lastPreviewUpdateNanos = now;

            return;
        }

        float delta = Math.min(0.1F, Math.max(0F,
            (now - this.lastPreviewUpdateNanos) / 1_000_000_000F));
        this.lastPreviewUpdateNanos = now;

        if (this.sourceSkeleton == null || this.targetSkeleton == null)
        {
            return;
        }

        this.progress += delta * PREVIEW_PLAYBACK_SPEED;

        if (this.progress >= 1F)
        {
            this.progress -= 1F;
        }

        this.preview.configure(this.sourceId, this.targetId, this.data, this.progress);

        String previewKey = this.currentHighlightKey();
        this.preview.setHighlighted(previewKey == null ? java.util.Set.of() : java.util.Set.of(previewKey));
    }

    private void buildMappingColumn(UIElement column)
    {
        UIElement draft = new UIElement();
        UILabel draftTitle = UI.label(L10n.lang("bbsppp.ui.cube_mapping.create_card"));
        UILabel arrow = UI.label(IKey.raw("→"));

        this.cardBackground(draft);
        draftTitle.relative(draft).x(8).y(4).w(1F, -16).h(HEADER_H);
        this.draftSourceLabel.relative(draft).x(8).y(HEADER_H + 2).w(0.44F, -8).h(18);
        this.draftSourceDetailLabel.relative(draft).x(8).y(HEADER_H + 20).w(0.44F, -8).h(22);
        arrow.relative(draft).x(0.46F).y(HEADER_H + 8).w(24).h(20);
        this.draftTargetLabel.relative(draft).x(0.54F, 8).y(HEADER_H + 2).w(0.46F, -16).h(18);
        this.draftTargetDetailLabel.relative(draft).x(0.54F, 8).y(HEADER_H + 20).w(0.46F, -16).h(22);
        this.draftTypeLabel.relative(draft).x(8).y(HEADER_H + 34).w(0.5F, -12).h(18);
        this.draftCreateButton.relative(draft).x(0.56F, 8).y(HEADER_H + 32).w(0.44F, -16).h(24);
        draft.add(draftTitle, this.draftSourceLabel, this.draftSourceDetailLabel, arrow,
            this.draftTargetLabel, this.draftTargetDetailLabel,
            this.draftTypeLabel, this.draftCreateButton);

        UILabel title = UI.label(L10n.lang("bbsppp.ui.cube_mapping.mappings"));
        title.relative(column).x(6).y(2).w(1F, -12).h(HEADER_H);
        draft.relative(column).x(0).y(HEADER_H).w(1F).h(88);
        this.mappingList.relative(column).x(0).y(HEADER_H + 92).w(1F)
            .h(1F, -(HEADER_H + 92));

        column.add(title, draft, this.mappingList);
        this.mappingEmptyLabel.relative(this.mappingList).x(8).y(24).w(1F, -16).h(20);
        this.mappingList.add(this.mappingEmptyLabel);
    }

    private String modelLabel(String key, String id)
    {
        String prefix = L10n.lang(key).get();

        return id.isEmpty() ? prefix + ": -" : prefix + ": " + id;
    }

    /* ---------- 变更操作：所有修改都必须经过 markChanged() ---------- */

    /**
     * 在修改映射数据<b>之前</b>保存快照。
     *
     * <p>所有改动入口都必须先调用它，否则撤销会跳过那一步。</p>
     */
    private void pushUndo()
    {
        this.undoStack.push(this.data.copy());
        this.redoStack.clear();

        while (this.undoStack.size() > UNDO_LIMIT)
        {
            this.undoStack.removeLast();
        }
    }

    private void undo()
    {
        if (this.undoStack.isEmpty())
        {
            return;
        }

        this.redoStack.push(this.data.copy());
        this.restore(this.undoStack.pop());
    }

    private void redo()
    {
        if (this.redoStack.isEmpty())
        {
            return;
        }

        this.undoStack.push(this.data.copy());
        this.restore(this.redoStack.pop());
    }

    /**
     * 用快照替换当前数据，并刷新所有视图。
     */
    private void restore(ModelMorphData snapshot)
    {
        this.data.setAllowIncomplete(snapshot.allowIncomplete());
        this.data.setGroups(snapshot.groups());
        this.selectedGroupId = null;

        this.markChanged();
    }

    private void markChanged()
    {
        this.rebuildMappings();
        this.refreshValidation();
        this.refreshButtons();
        this.refreshPreview();

        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    private void onTreeChanged()
    {
        this.refreshPreview();
        this.refreshButtons();
    }

    private void autoMatch(UIIcon button)
    {
        if (!button.isEnabled() || this.sourceSkeleton == null || this.targetSkeleton == null)
        {
            return;
        }

        this.pushUndo();

        this.data.setGroups(MorphAutoMatch.suggest(this.sourceSkeleton, this.targetSkeleton));
        this.selectedGroupId = null;
        this.markChanged();
    }

    private void createMapping(UIIcon button)
    {
        this.createMapping();
    }

    private void createMapping()
    {
        if (this.sourceSkeleton == null || this.targetSkeleton == null)
        {
            return;
        }

        Set<String> sources = this.sourceList.pickedCubeKeys();
        Set<String> targets = this.targetList.pickedCubeKeys();

        /* 多对多没有定义：提示用户拆成 1:N + N:1，而不是静默创建一条运行时会被判为无效的映射 */
        if (sources.size() > 1 && targets.size() > 1)
        {
            UIContext context = this.getContext();

            if (context != null)
            {
                context.notifyError(L10n.lang("bbsppp.ui.cube_mapping.many_to_many"));
            }

            return;
        }

        this.pushUndo();

        MappingGroup group = new MappingGroup();

        group.setSources(this.refs(this.sourceSkeleton, sources));
        group.setTargets(this.refs(this.targetSkeleton, targets));

        List<MappingGroup> groups = new ArrayList<>();

        /* 一个 Cube 只能属于一个映射组：先把复用同一批 Cube 的旧组删掉，冲突就永远建不出来 */
        for (MappingGroup existing : this.data.groups())
        {
            if (!reuses(existing, sources, targets))
            {
                groups.add(existing);
            }
        }

        groups.add(group);
        this.data.setGroups(groups);
        this.selectedGroupId = group.id();
        this.sourceList.deselect();
        this.targetList.deselect();
        this.markChanged();
    }

    private void clearMappings(UIIcon button)
    {
        if (!button.isEnabled() || this.data.groups().isEmpty())
        {
            return;
        }

        this.pushUndo();

        this.data.setGroups(List.of());
        this.selectedGroupId = null;
        this.markChanged();
    }

    private void removeSelectedMapping(UIIcon button)
    {
        if (button != null && !button.isEnabled())
        {
            return;
        }

        if (this.selectedGroupId == null)
        {
            return;
        }

        List<MappingGroup> groups = new ArrayList<>();

        for (MappingGroup group : this.data.groups())
        {
            if (!group.id().equals(this.selectedGroupId))
            {
                groups.add(group);
            }
        }

        if (groups.size() == this.data.groups().size())
        {
            return;
        }

        this.pushUndo();
        this.data.setGroups(groups);
        this.selectedGroupId = null;
        this.markChanged();
    }

    private void setAllowIncomplete(boolean value)
    {
        if (this.data.allowIncomplete() == value)
        {
            return;
        }

        this.pushUndo();
        this.data.setAllowIncomplete(value);
        this.markChanged();
    }

    /* ---------- 数据刷新 ---------- */

    private void rebuildMappings()
    {
        List<MappingGroup> groups = new ArrayList<>(this.data.groups());

        /* setList() 不会清理旧下标，先 deselect 否则旧 pick 会错误地高亮新列表里的其它行 */
        this.mappingList.deselect();
        this.mappingList.setList(groups);

        if (this.selectedGroupId != null)
        {
            for (int i = 0; i < groups.size(); i++)
            {
                if (groups.get(i).id().equals(this.selectedGroupId))
                {
                    this.mappingList.setIndex(i);

                    return;
                }
            }
        }

        this.selectedGroupId = null;
    }

    private void refreshValidation()
    {
        /* 关键帧上的 Morph 开关可能还是关的，但编辑时仍应看到映射完成度，所以用一份强制启用的副本校验 */
        ModelMorphData probe = this.data.copy();

        probe.setEnabled(true);

        String actualSource = this.sourceId.isEmpty() ? probe.sourceModel() : this.sourceId;
        String actualTarget = this.targetId.isEmpty() ? probe.targetModel() : this.targetId;

        this.validation = ModelMorphValidation.validate(probe, this.sourceSkeleton, this.targetSkeleton, actualSource, actualTarget);
        this.refreshHighlight();
        this.refreshSummary();
    }

    private void refreshHighlight()
    {
        Set<String> selectedSourceKeys = new LinkedHashSet<>();
        Set<String> selectedTargetKeys = new LinkedHashSet<>();
        MappingGroup selected = this.selectedGroup();

        if (selected != null)
        {
            addKeys(selectedSourceKeys, selected.sources());
            addKeys(selectedTargetKeys, selected.targets());
        }

        Set<String> invalidSourceKeys = new LinkedHashSet<>();
        Set<String> invalidTargetKeys = new LinkedHashSet<>();
        Map<String, Integer> sourceUse = new LinkedHashMap<>();
        Map<String, Integer> targetUse = new LinkedHashMap<>();

        for (MappingGroup group : this.data.groups())
        {
            boolean supported = group.type().isSupported();

            for (MappingGroup.CubeRef ref : group.sources())
            {
                sourceUse.merge(ref.key(), 1, Integer::sum);

                if (!supported)
                {
                    invalidSourceKeys.add(ref.key());
                }
            }

            for (MappingGroup.CubeRef ref : group.targets())
            {
                targetUse.merge(ref.key(), 1, Integer::sum);

                if (!supported)
                {
                    invalidTargetKeys.add(ref.key());
                }
            }
        }

        collectConflicts(sourceUse, this.sourceSkeleton, invalidSourceKeys);
        collectConflicts(targetUse, this.targetSkeleton, invalidTargetKeys);
        collectInvalidSignatures(this.data.groups(), this.sourceSkeleton, invalidSourceKeys, true);
        collectInvalidSignatures(this.data.groups(), this.targetSkeleton, invalidTargetKeys, false);

        this.selectedSources = selectedSourceKeys;
        this.selectedTargets = selectedTargetKeys;
        this.invalidSources = invalidSourceKeys;
        this.invalidTargets = invalidTargetKeys;
        this.mappedSources = this.validation == null ? Set.of() : this.validation.usedSources();
        this.mappedTargets = this.validation == null ? Set.of() : this.validation.usedTargets();
    }

    /**
     * 重复使用（冲突）与引用失效的 Cube 都标红：这两种情况都会让整段形变被判为不可播放，
     * 用户需要一眼看出是哪几个 Cube 出了问题。
     */
    private static void collectConflicts(Map<String, Integer> use, ModelMorphSkeleton skeleton, Set<String> out)
    {
        Map<String, ModelMorphSkeleton.CubeSpec> cubes = skeleton == null ? Map.of() : skeleton.cubesByKey();

        for (Map.Entry<String, Integer> entry : use.entrySet())
        {
            if (entry.getValue() > 1 || !cubes.containsKey(entry.getKey()))
            {
                out.add(entry.getKey());
            }
        }
    }

    private static void collectInvalidSignatures(List<MappingGroup> groups, ModelMorphSkeleton skeleton,
                                                Set<String> out, boolean sourceSide)
    {
        if (skeleton == null)
        {
            return;
        }

        Map<String, ModelMorphSkeleton.CubeSpec> cubes = skeleton.cubesByKey();

        for (MappingGroup group : groups)
        {
            List<MappingGroup.CubeRef> refs = sourceSide ? group.sources() : group.targets();

            for (MappingGroup.CubeRef ref : refs)
            {
                addInvalidSignature(ref, cubes, out);
            }
        }
    }

    private static void addInvalidSignature(MappingGroup.CubeRef ref,
                                            Map<String, ModelMorphSkeleton.CubeSpec> cubes, Set<String> out)
    {
        ModelMorphSkeleton.CubeSpec cube = cubes.get(ref.key());

        if (cube != null && !ref.signature().isEmpty() && !ref.signature().equals(cube.signature()))
        {
            out.add(ref.key());
        }
    }

    private void refreshSummary()
    {
        ModelMorphValidation.Result result = this.validation;

        if (this.sourceSkeleton == null || this.targetSkeleton == null)
        {
            StringBuilder builder = new StringBuilder();

            if (this.sourceSkeleton == null)
            {
                builder.append(L10n.lang("bbsppp.ui.cube_mapping.no_source").get());
            }

            if (this.targetSkeleton == null)
            {
                if (builder.length() > 0)
                {
                    builder.append("    ");
                }

                builder.append(L10n.lang("bbsppp.ui.cube_mapping.no_target").get());
            }

            this.summaryLabel.label = IKey.raw(builder.toString());
            this.detailLabel.label = IKey.EMPTY;
            this.detailLabel.color(Colors.A100 | Colors.RED);

            return;
        }

        String summary = L10n.lang("bbsppp.ui.cube_mapping.summary")
            .format(result.sourceTotal(), result.targetTotal(), result.mappedSourceCount(), result.mappedTargetCount())
            .get();
        String unmapped = L10n.lang("bbsppp.ui.cube_mapping.unmapped").get()
            + ": " + result.unmappedSources().size() + " / " + result.unmappedTargets().size();

        this.summaryLabel.label = IKey.raw(summary + "    " + unmapped);
        this.summaryLabel.color(Colors.WHITE);

        if (!result.errors().isEmpty())
        {
            this.detailLabel.label = L10n.lang("bbsppp.ui.cube_mapping.detail.invalid");
            this.detailLabel.color(Colors.A100 | Colors.RED);
        }
        else
        {
            this.detailLabel.label = result.warnings().isEmpty()
                ? IKey.EMPTY : IKey.raw(localizedWarnings(result));
            this.detailLabel.color(Colors.WHITE);
        }
    }

    private static String localizedWarnings(ModelMorphValidation.Result result)
    {
        List<String> messages = new ArrayList<>();

        if (!result.unmappedSources().isEmpty())
        {
            messages.add(L10n.lang("bbsppp.ui.cube_mapping.detail.unmapped_source")
                .format(result.unmappedSources().size()).get());
        }

        if (!result.unmappedTargets().isEmpty())
        {
            messages.add(L10n.lang("bbsppp.ui.cube_mapping.detail.unmapped_target")
                .format(result.unmappedTargets().size()).get());
        }

        int localizedWarnings = (!result.unmappedSources().isEmpty() ? 1 : 0)
            + (!result.unmappedTargets().isEmpty() ? 1 : 0);

        if (result.warnings().size() > localizedWarnings)
        {
            messages.add(L10n.lang("bbsppp.ui.cube_mapping.detail.warning").get());
        }

        return String.join("    ", messages.subList(0, Math.min(2, messages.size())));
    }

    private void refreshButtons()
    {
        boolean models = this.sourceSkeleton != null && this.targetSkeleton != null;
        int sources = this.sourceList.pickedCubeKeys().size();
        int targets = this.targetList.pickedCubeKeys().size();

        this.auto.setEnabled(models);
        this.clear.setEnabled(!this.data.groups().isEmpty());
        this.remove.setEnabled(this.selectedGroupId != null);
        /* 两侧各选一个以上才可点；多对多的提示交给点击回调，按钮本身保持可点才能给出提示 */
        this.add.setEnabled(models && sources >= 1 && targets >= 1);
        this.draftCreateButton.setEnabled(models && sources >= 1 && targets >= 1);
        this.mappingEmptyLabel.setVisible(this.data.groups().isEmpty());
        this.refreshMappingDraft(sources, targets);
    }

    private void refreshMappingDraft(int sources, int targets)
    {
        this.draftSourceLabel.label = L10n.lang("bbsppp.ui.cube_mapping.selection_source").format(sources);
        this.draftTargetLabel.label = L10n.lang("bbsppp.ui.cube_mapping.selection_target").format(targets);
        this.draftSourceDetailLabel.label = IKey.raw(selectionDetail(this.sourceList.pickedCubeKeys()));
        this.draftTargetDetailLabel.label = IKey.raw(selectionDetail(this.targetList.pickedCubeKeys()));

        String type = sources == 0 || targets == 0 ? "-"
            : sources == 1 && targets == 1 ? "1:1"
            : sources == 1 ? "1:N"
            : targets == 1 ? "N:1" : "N:N";

        this.draftTypeLabel.label = IKey.raw(type);
    }

    private static String selectionDetail(Set<String> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            return "-";
        }

        StringBuilder builder = new StringBuilder();
        int index = 0;

        for (String key : keys)
        {
            if (index++ > 0)
            {
                builder.append(", ");
            }

            builder.append(readableRef(key));

            if (index >= 2 && keys.size() > index)
            {
                builder.append(" …");

                break;
            }
        }

        return builder.toString();
    }

    /**
     * 3D 预览里点中某个 Cube 时，同步到对应一侧的列表选择。
     *
     * <p>这样用户可以直接对着模型点，而不必在几百行同名 Cube 里找。
     * 点空白处则清空该侧选择。</p>
     */
    private void onPreviewPick(boolean targetSide, String cubeKey)
    {
        CubeList list = targetSide ? this.targetList : this.sourceList;

        /* 与 BBS 原版列表一致的多选修饰键语义：
           Shift = 范围选择，Ctrl = 切换单行，否则单选。 */
        boolean additive = mchorse.bbs_mod.graphics.window.Window.isCtrlPressed();
        boolean range = mchorse.bbs_mod.graphics.window.Window.isShiftPressed();

        list.pickByKey(cubeKey, additive, range);

        /* 必须刷新 tint：行底色由 selectedSources/selectedTargets 决定，
           而它们只在 refreshHighlight() 里赋值。 */
        this.refreshHighlight();
        this.onTreeChanged();

        /* 选中变化后必须立刻刷新高亮：两个拾取面板各自高亮本侧选中的 Cube。
           早期只调 onTreeChanged()，而它刷新的是右下角形变预览的高亮，
           两侧拾取面板的框因此从不更新。 */
        this.sourcePicker.setHighlighted(this.sourceList.pickedCubeKeys());
        this.targetPicker.setHighlighted(this.targetList.pickedCubeKeys());
    }

    private void refreshPreview()
    {
        this.preview.configure(
            this.sourceSkeleton == null ? null : this.sourceId,
            this.targetSkeleton == null ? null : this.targetId,
            this.data,
            this.progress
        );

        /* 高亮跟随列表选择：用户无论在列表里选还是对着模型点，都看到同一个框。 */
        String previewKey = this.currentHighlightKey();

        this.preview.setHighlighted(previewKey == null ? java.util.Set.of() : java.util.Set.of(previewKey));

        /* 两个拾取面板各看一侧，高亮只显示本侧选中的块。 */
        this.sourcePicker.configurePicker(this.sourceSkeleton == null ? null : this.sourceId);
        this.targetPicker.configurePicker(this.targetSkeleton == null ? null : this.targetId);
        this.sourcePicker.setHighlighted(this.sourceList.pickedCubeKeys());
        this.targetPicker.setHighlighted(this.targetList.pickedCubeKeys());
    }

    /**
     * 当前该高亮的 Cube key。
     *
     * <p>优先高亮「当前预览那一侧」的选择；该侧没选时退回另一侧，
     * 这样切换预览侧时高亮不会莫名其妙消失。</p>
     */

    private String currentHighlightKey()
    {
        Set<String> primary = this.preview.isShowingTarget()
            ? this.targetList.pickedCubeKeys() : this.sourceList.pickedCubeKeys();
        Set<String> fallback = this.preview.isShowingTarget()
            ? this.sourceList.pickedCubeKeys() : this.targetList.pickedCubeKeys();

        if (!primary.isEmpty())
        {
            return primary.iterator().next();
        }

        return fallback.isEmpty() ? null : fallback.iterator().next();
    }

    /* ---------- 选择与映射辅助 ---------- */

    private void onMappingPicked(List<MappingGroup> rows)
    {
        this.selectedGroupId = rows.isEmpty() ? null : rows.get(0).id();

        /* 选中映射行时把两侧树定位到对应的 Cube：
           否则用户点了一条映射，却不知道该组到底对应树里的哪些行，
           必须自己在长列表里翻找。 */
        if (!rows.isEmpty())
        {
            MappingGroup group = rows.get(0);

            this.selectInList(this.sourceList, group.sources());
            this.selectInList(this.targetList, group.targets());
        }

        this.refreshHighlight();
        this.refreshButtons();

        /* 让 3D 视口高亮该组涉及的块，与树的定位保持一致。 */
        this.sourcePicker.setHighlighted(this.sourceList.pickedCubeKeys());
        this.targetPicker.setHighlighted(this.targetList.pickedCubeKeys());
    }

    /**
     * 在树里选中给定引用对应的 Cube 行。
     */
    private void selectInList(CubeList list, List<MappingGroup.CubeRef> refs)
    {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();

        for (MappingGroup.CubeRef ref : refs)
        {
            keys.add(ref.key());
        }

        list.pickByKeys(keys);
    }

    private MappingGroup selectedGroup()
    {
        if (this.selectedGroupId == null)
        {
            return null;
        }

        for (MappingGroup group : this.data.groups())
        {
            if (group.id().equals(this.selectedGroupId))
            {
                return group;
            }
        }

        return null;
    }

    private List<MappingGroup.CubeRef> refs(ModelMorphSkeleton skeleton, Set<String> keys)
    {
        List<MappingGroup.CubeRef> refs = new ArrayList<>();
        Map<String, ModelMorphSkeleton.CubeSpec> cubes = skeleton.cubesByKey();

        for (String key : keys)
        {
            ModelMorphSkeleton.CubeSpec cube = cubes.get(key);

            if (cube != null)
            {
                refs.add(new MappingGroup.CubeRef(cube.key(), cube.signature()));
            }
        }

        return refs;
    }

    private static boolean reuses(MappingGroup group, Set<String> sources, Set<String> targets)
    {
        for (MappingGroup.CubeRef ref : group.sources())
        {
            if (sources.contains(ref.key()))
            {
                return true;
            }
        }

        for (MappingGroup.CubeRef ref : group.targets())
        {
            if (targets.contains(ref.key()))
            {
                return true;
            }
        }

        return false;
    }

    private static void addKeys(Set<String> out, List<MappingGroup.CubeRef> refs)
    {
        for (MappingGroup.CubeRef ref : refs)
        {
            out.add(ref.key());
        }
    }

    private static ModelMorphSkeleton skeletonOf(String id)
    {
        ModelInstance instance = ModelMorphRuntime.model(id);

        if (instance == null || !(instance.model instanceof Model model))
        {
            return null;
        }

        ModelMorphSkeleton skeleton = ModelMorphAdapter.skeleton(model);

        return skeleton.isEmpty() ? null : skeleton;
    }

    /** Cube 引用在卡片上只显示名字部分：完整路径在两侧树里已经可见，卡片只需要区分对象。 */
    /**
     * 从骨架里的完整 Cube key 提取「短 key」。
     *
     * <p>完整 key 形如 {@code anchor/low_body/body/left_arm:cube_0}，
     * 渲染侧只生成 {@code cube_0}（重名时 {@code cube_0#1}）。</p>
     */
    private static String shortCubeKey(ModelMorphSkeleton.CubeSpec cube, ModelMorphSkeleton.GroupSpec group)
    {
        String name = cube.name();
        int ordinal = cube.ordinal();

        if (name == null || name.isEmpty())
        {
            return "#" + ordinal;
        }

        /* 组内重名时必须带序号，否则与渲染侧生成的 key 对不上。 */
        int sameName = 0;

        for (ModelMorphSkeleton.CubeSpec other : group.cubes())
        {
            if (name.equals(other.name()))
            {
                sameName++;
            }
        }

        return sameName > 1 ? name + "#" + ordinal : name;
    }

    /**
     * 取组路径的最后一段（直接父级组名）。
     *
     * <p>组名在同一模型内唯一，因此不需要完整路径就能唯一识别；完整路径会把列表行撑爆。</p>
     */
    private static String shortGroupName(String path)
    {
        if (path == null || path.isEmpty())
        {
            return "";
        }

        int slash = path.lastIndexOf('/');

        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * 把 Cube 的完整 key 转成可读名称。
     *
     * <p>完整 key 形如 {@code anchor/low_body/body/left_arm:cube_0}，
     * 只取冒号后部分是 {@code cube_0}——所有块都叫这个名字，用户无法区分。
     * 这里保留「直接父级组名 + 块名」，例如 {@code left_arm:cube_0}。</p>
     */
    private static String readableRef(String key)
    {
        if (key == null || key.isEmpty())
        {
            return "-";
        }

        int colon = key.lastIndexOf(':');

        if (colon < 0)
        {
            return shortGroupName(key);
        }

        String group = shortGroupName(key.substring(0, colon));
        String cube = key.substring(colon + 1);

        return group.isEmpty() ? cube : group + ":" + cube;
    }

    /** Cube 名重复时补上序号，否则列表里会出现两个一模一样的行，用户无法区分该点哪个。 */
    private static String cubeText(ModelMorphSkeleton.GroupSpec group, ModelMorphSkeleton.CubeSpec cube)
    {
        String name = cube.name();

        if (name.isEmpty())
        {
            return cube.displayName();
        }

        int count = 0;

        for (ModelMorphSkeleton.CubeSpec other : group.cubes())
        {
            if (other.name().equals(name))
            {
                count++;
            }
        }

        return count > 1 ? name + "#" + cube.ordinal() : name;
    }

    private static int depthOf(String path)
    {
        int depth = 0;

        for (int i = 0; i < path.length(); i++)
        {
            if (path.charAt(i) == '/')
            {
                depth++;
            }
        }

        return depth;
    }

    /* ---------- 键盘 ---------- */

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        /* 子元素优先拿到按键，所以搜索框里的 Delete 不会走到这里；Esc 的关闭逻辑交回基类 */
        if (context.isPressed(Keys.DELETE) && this.selectedGroupId != null)
        {
            this.removeSelectedMapping(null);

            return true;
        }

        return super.subKeyPressed(context);
    }

    /* ---------- 树的单行数据 ---------- */

    /**
     * 树中的一个可见行。骨骼行是分支，Cube 行是叶子；两者共用同一个列表，
     * 由 {@link CubeList#indent(Object)} 与 {@link CubeList#branch(Object)} 表现层级。
     */
    private static final class CubeRow
    {
        private final boolean bone;
        private final int depth;
        private final String path;
        private final String text;
        private final String key;
        private final List<String> cubes;
        private final boolean open;

        private CubeRow(boolean bone, int depth, String path, String text, String key, List<String> cubes, boolean open)
        {
            this.bone = bone;
            this.depth = depth;
            this.path = path;
            this.text = text;
            this.key = key;
            this.cubes = cubes;
            this.open = open;
        }

        private static CubeRow bone(ModelMorphSkeleton.GroupSpec group, int depth, boolean open)
        {
            List<String> keys = new ArrayList<>();

            for (ModelMorphSkeleton.CubeSpec cube : group.cubes())
            {
                keys.add(cube.key());
            }

            /* 只显示<b>直接父级组名</b>，不显示完整嵌套路径。
               形如 anchor/anchor_low/low_body/body/left_arm 的路径会把行撑爆、且绝大部分
               前缀对用户没有意义；组名在同一模型内唯一，因此短名足以识别。
               不再附加 Cube 数量后缀：展开后就能直接数到，后缀只会让行更挤。 */
            String text = shortGroupName(group.path());

            return new CubeRow(true, depth, group.path(), text, group.path(), keys, open);
        }

        private static CubeRow cube(ModelMorphSkeleton.GroupSpec group, ModelMorphSkeleton.CubeSpec cube, int depth)
        {
            return new CubeRow(false, depth, group.path(), cubeText(group, cube), cube.key(), List.of(), false);
        }

        /**
         * 按身份字段比较。
         *
         * <p>必须实现：{@code UIList.setCurrent} 内部用 {@code list.indexOf(element)}，
         * 而它依赖 {@code equals}。不实现时永远返回 -1，选中静默失败——
         * 这正是「3D 里点中了块，列表却毫无反应」的原因。</p>
         */
        @Override
        public boolean equals(Object object)
        {
            if (this == object)
            {
                return true;
            }

            if (!(object instanceof CubeRow other))
            {
                return false;
            }

            return this.bone == other.bone
                && Objects.equals(this.key, other.key)
                && Objects.equals(this.path, other.path);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.bone, this.key, this.path);
        }

        private boolean bone()
        {
            return this.bone;
        }

        private int depth()
        {
            return this.depth;
        }

        private String text()
        {
            return this.text;
        }

        private String key()
        {
            return this.key;
        }

        /** 行的骨骼路径；骨骼行用它把子 Cube 归组，Cube 行用它判断属于哪块骨骼。 */
        private String path()
        {
            return this.path;
        }

        private List<String> cubes()
        {
            return this.cubes;
        }

        private boolean open()
        {
            return this.open;
        }
    }

    /* ---------- 源/目标 Cube 树 ---------- */

    private final class CubeList extends UIList<CubeRow>
    {
        private final ModelMorphSkeleton skeleton;
        private final boolean sourceSide;
        private final Map<String, Boolean> expanded = new LinkedHashMap<>();
        private String query = "";

        private CubeList(ModelMorphSkeleton skeleton, boolean sourceSide, Runnable callback)
        {
            super((rows) -> callback.run());

            this.skeleton = skeleton;
            this.sourceSide = sourceSide;
            this.scroll.scrollItemSize = ROW_H;
            this.multi();
            this.background();

            if (skeleton != null)
            {
                this.rebuild();
            }
        }

        /* ---- 构建与搜索 ---- */

        private void rebuild()
        {
            Set<String> keep = new LinkedHashSet<>(this.pickedCubeKeys());
            List<CubeRow> rows = new ArrayList<>();

            if (this.skeleton != null)
            {
                for (ModelMorphSkeleton.GroupSpec group : this.skeleton.groups())
                {
                    /* 跳过不含 Cube 的组：它们只承载层级关系，用户无法对它们做任何映射操作，
                       列出来只会淹没真正可选的 Cube（大型模型里这类空组往往占绝大多数）。 */
                    if (group.cubes().isEmpty())
                    {
                        continue;
                    }

                    int depth = depthOf(group.path());

                    /* 搜索时强制展开：命中的 Cube 若藏在折叠分支里，用户会以为没搜到。
                     * 折叠箭头也一并按展开状态绘制，否则箭头与可见子行会自相矛盾。 */
                    boolean open = this.isOpen(group.path()) || !this.query.isEmpty();
                    CubeRow bone = CubeRow.bone(group, depth, open);

                    rows.add(bone);

                    if (open)
                    {
                        for (ModelMorphSkeleton.CubeSpec cube : group.cubes())
                        {
                            rows.add(CubeRow.cube(group, cube, depth + 1));
                        }
                    }
                }
            }

            this.deselect();
            this.setList(rows);
            this.restore(keep);
        }

        /**
         * 搜索时强制展开所有骨骼：否则命中的 Cube 可能藏在折叠分支里，用户会以为没搜到。
         */
        @Override
        public void filter(String filter)
        {
            String text = filter == null ? "" : filter;

            if (!text.equals(this.query))
            {
                this.query = text;
                this.rebuild();
            }

            super.filter(text);
        }

        private boolean isOpen(String path)
        {
            Boolean open = this.expanded.get(path);

            return open == null || open;
        }

        private void restore(Set<String> keep)
        {
            if (keep.isEmpty())
            {
                return;
            }

            List<CubeRow> picked = new ArrayList<>();

            for (CubeRow row : this.getList())
            {
                if (!row.bone() && keep.contains(row.key()))
                {
                    picked.add(row);
                }
                else if (row.bone() && !row.cubes().isEmpty() && keep.containsAll(row.cubes()))
                {
                    picked.add(row);
                }
            }

            if (!picked.isEmpty())
            {
                this.setCurrent(picked);
            }
        }

        /* ---- 选择语义 ---- */

        /**
         * 点击骨骼行时整块骨骼一起选：映射通常按骨骼组织（一条腿、一段机身），
         * 逐个 Cube 点选在这种场景下太慢。再点一次则整组取消。
         */
        @Override
        protected void applySelectionOnClick(CubeRow item, int index)
        {
            if (!item.bone())
            {
                super.applySelectionOnClick(item, index);

                return;
            }

            List<CubeRow> group = new ArrayList<>();

            group.add(item);

            for (CubeRow row : this.getList())
            {
                if (!row.bone() && row.path().equals(item.path()))
                {
                    group.add(row);
                }
            }

            boolean allPicked = true;

            for (CubeRow row : group)
            {
                /* 直接用 selection 判断：current.contains(index) 内部走 identity，
                   与 indexOf 的 equals 口径混用是脆弱写法。 */
                if (!this.selection.contains(row))
                {
                    allPicked = false;

                    break;
                }
            }

            if (allPicked)
            {
                this.deselect();
            }
            else
            {
                this.setCurrent(group);
            }
        }

        /**
         * 按 Cube key 选中对应行（供 3D 预览点选使用）。
         *
         * @param cubeKey 目标 Cube 的稳定 key；{@code null} 表示清空选择
         */
        /**
         * 按 Cube key 选中对应行，并把它滚进视野。
         *
         * <p>必须自己滚动：{@code setCurrent} 完全不触碰 {@code scroll}，
         * 而树的行数远多于可见行数，选中行常在视口外——用户会以为「点了没反应」。</p>
         *
         * @return 是否命中
         */
        /**
         * 按 Cube key 选中对应行，并把它滚进视野。
         *
         * @param additive Ctrl 语义：切换该行的选中状态，保留其它已选行
         * @param range    Shift 语义：从当前锚点范围选择到该行
         * @return 是否命中
         */
        private boolean pickByKey(String cubeKey, boolean additive, boolean range)
        {
            if (cubeKey == null || cubeKey.isEmpty())
            {
                this.deselect();

                return false;
            }

            /* 过滤时 visible 下标与 backing 下标不一致，而滚动数学只对未过滤列表成立。 */
            if (!this.query.isEmpty())
            {
                this.filter("");
            }

            int index = this.indexOfCube(cubeKey);

            /* 命中行可能藏在折叠的骨骼下：展开、重建，再找一次。 */
            if (index < 0 && this.expandFor(cubeKey))
            {
                index = this.indexOfCube(cubeKey);
            }

            if (index < 0)
            {
                this.deselect();

                return false;
            }

            /* 与 BBS 原版 UIList#applySelectionOnClick 的语义保持一致：
               Shift 范围选择、Ctrl 切换、否则单选。 */
            if (range)
            {
                this.selection.range(this.getList().get(index), null, this.visible());
                this.scrollIntoView(index);
                this.fireCallback();
            }
            else if (additive)
            {
                this.toggleIndex(index);
                this.scrollIntoView(index);
                this.fireCallback();
            }
            else
            {
                /* pick(int) 内部已 fireCallback()。 */
                this.pick(index);
                this.scrollIntoView(index);
            }

            return true;
        }

        /**
         * 选中一组 Cube key 对应的行（供映射行联动使用）。
         *
         * <p>先清过滤与折叠：命中行若藏在折叠的骨骼下，用户会以为没选中。</p>
         */
        private void pickByKeys(java.util.Collection<String> cubeKeys)
        {
            if (cubeKeys == null || cubeKeys.isEmpty())
            {
                this.deselect();

                return;
            }

            if (!this.query.isEmpty())
            {
                this.filter("");
            }

            java.util.List<CubeRow> rows = this.getList();
            java.util.List<CubeRow> picked = new ArrayList<>();

            for (CubeRow row : rows)
            {
                if (!row.bone() && cubeKeys.contains(row.key()))
                {
                    picked.add(row);
                }
            }

            /* 命中行可能藏在折叠的骨骼下：展开相关骨骼后重建，再找一次。 */
            if (picked.isEmpty())
            {
                boolean expandedAny = false;

                for (CubeRow row : rows)
                {
                    if (row.bone())
                    {
                        for (String key : cubeKeys)
                        {
                            if (row.cubes().contains(key))
                            {
                                this.expanded.put(row.path(), Boolean.TRUE);
                                expandedAny = true;

                                break;
                            }
                        }
                    }
                }

                if (expandedAny)
                {
                    this.rebuild();

                    for (CubeRow row : this.getList())
                    {
                        if (!row.bone() && cubeKeys.contains(row.key()))
                        {
                            picked.add(row);
                        }
                    }
                }
            }

            if (picked.isEmpty())
            {
                this.deselect();

                return;
            }

            this.setCurrent(picked);
            this.scrollIntoView(this.getList().indexOf(picked.get(0)));
        }

        private int indexOfCube(String cubeKey)
        {
            List<CubeRow> rows = this.getList();

            for (int i = 0; i < rows.size(); i++)
            {
                CubeRow row = rows.get(i);

                if (!row.bone() && row.key().equals(cubeKey))
                {
                    return i;
                }
            }

            return -1;
        }

        private boolean expandFor(String cubeKey)
        {
            for (CubeRow row : this.getList())
            {
                if (row.bone() && row.cubes().contains(cubeKey))
                {
                    this.expanded.put(row.path(), Boolean.TRUE);
                    this.rebuild();

                    return true;
                }
            }

            return false;
        }

        private Set<String> pickedCubeKeys()
        {
            Set<String> keys = new LinkedHashSet<>();

            for (CubeRow row : this.getCurrent())
            {
                if (row.bone())
                {
                    keys.addAll(row.cubes());
                }
                else
                {
                    keys.add(row.key());
                }
            }

            return keys;
        }

        /* ---- 树表现 ---- */

        @Override
        protected int indent(CubeRow element)
        {
            return element.depth() * this.indentStep();
        }

        @Override
        protected int indentStep()
        {
            return 10;
        }

        @Override
        protected Boolean branch(CubeRow element)
        {
            return element.bone() ? Boolean.valueOf(element.open()) : null;
        }

        @Override
        protected void toggle(CubeRow element)
        {
            if (!element.bone())
            {
                return;
            }

            this.expanded.put(element.path(), !element.open());
            this.rebuild();
        }

        @Override
        protected String elementToString(UIContext context, int i, CubeRow element)
        {
            return element.text();
        }

        /**
         * 先让基类画行底色（条纹与选中高亮），再叠一层映射状态色。
         *
         * <p>映射状态色在文字之前绘制，因此不会盖住文字；红色优先级最高，
         * 因为冲突/失效是必须处理的问题，而「已映射」只是信息。</p>
         */
        @Override
        public void renderListElement(UIContext context, CubeRow element, int i, int x, int y, boolean hover, boolean selected)
        {
            int height = this.scroll.scrollItemSize;

            RowStyle.row(context.batcher, x, y, this.area.w, height, 0, false, hover, selected);

            int tint = this.tint(element);

            if (tint != 0)
            {
                context.batcher.box(x, y, x + this.area.w, y + height, tint);
            }

            this.renderElementPart(context, element, i, x, y, hover, selected);
        }

        private int tint(CubeRow row)
        {
            Set<String> invalid = this.sourceSide ? UICubeMappingEditorPanel.this.invalidSources : UICubeMappingEditorPanel.this.invalidTargets;
            Set<String> selectedKeys = this.sourceSide ? UICubeMappingEditorPanel.this.selectedSources : UICubeMappingEditorPanel.this.selectedTargets;
            Set<String> mapped = this.sourceSide ? UICubeMappingEditorPanel.this.mappedSources : UICubeMappingEditorPanel.this.mappedTargets;
            List<String> keys = row.bone() ? row.cubes() : List.of(row.key());

            if (containsAny(invalid, keys))
            {
                return Colors.A50 | Colors.RED;
            }

            if (containsAny(selectedKeys, keys))
            {
                return Colors.A50 | BBSSettings.primaryColor.get();
            }

            if (containsAny(mapped, keys))
            {
                return Colors.A25 | BBSSettings.primaryColor.get();
            }

            return 0;
        }

        private static boolean containsAny(Set<String> set, List<String> keys)
        {
            for (String key : keys)
            {
                if (set.contains(key))
                {
                    return true;
                }
            }

            return false;
        }
    }

    /* ---------- 映射卡片列表 ---------- */

    private final class MappingList extends UIList<MappingGroup>
    {
        private MappingList()
        {
            super(UICubeMappingEditorPanel.this::onMappingPicked);

            this.scroll.scrollItemSize = ROW_H;
            this.background();

            /* 右键菜单直接使用 BBS 原版 UIDataContextMenu：
               这正是 poses.json 用的那套组件——文件放在源模型同级，
               内含「预设名 → 映射数据」字典，因此同一源模型可以保存多套方案。 */
            this.context(() -> new mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu(
                MorphMappingPresets.INSTANCE,
                UICubeMappingEditorPanel.this.data.sourceModel(),
                () -> UICubeMappingEditorPanel.this.data.toData(),
                (mapping) ->
                {
                    ModelMorphData loaded = ModelMorphData.fromData(mapping);

                    UICubeMappingEditorPanel.this.pushUndo();
                    UICubeMappingEditorPanel.this.data.setGroups(loaded.groups());
                    UICubeMappingEditorPanel.this.selectedGroupId = null;
                    UICubeMappingEditorPanel.this.markChanged();
                }));
        }

        @Override
        /**
         * 映射行的显示文本。
         *
         * <p>单行放不下 1:N 的完整目标列表（会被截断成 {@code [a, b, c...}），
         * 因此改为两行：首行是类型与源，次行是目标。映射表列宽较窄，
         * 两行比横向挤压更易读。</p>
         */
        protected String elementToString(UIContext context, int i, MappingGroup element)
        {
            StringBuilder builder = new StringBuilder();

            if (!element.isSupported())
            {
                builder.append("! ");
            }

            builder.append(element.type().label()).append("  ");

            int sources = element.sources().size();
            int targets = element.targets().size();

            /* 多源时在首行标出数量，避免用户以为只有第一个源。 */
            builder.append(describe(element.sources()));

            if (sources > 1)
            {
                builder.append(" (").append(sources).append(')');
            }

            builder.append("\n  -> ").append(describe(element.targets()));

            if (targets > 1)
            {
                builder.append(" (").append(targets).append(')');
            }

            return builder.toString();
        }

        private String describe(List<MappingGroup.CubeRef> refs)
        {
            if (refs.isEmpty())
            {
                return "-";
            }

            StringBuilder builder = new StringBuilder();
            boolean bracket = refs.size() > 1;

            if (bracket)
            {
                builder.append('[');
            }

            for (int i = 0; i < refs.size(); i++)
            {
                if (i > 0)
                {
                    builder.append(", ");
                }

                builder.append(readableRef(refs.get(i).key()));
            }

            if (bracket)
            {
                builder.append(']');
            }

            return builder.toString();
        }

        /** 不支持的映射组（历史数据里的 N:N / 空组）用红色左边条标出，避免它们看起来像正常数据。 */
        @Override
        protected int rowColor(MappingGroup element)
        {
            /* RowStyle 只在颜色带 alpha 时才画左边条，因此必须补上不透明度 */
            return element.isSupported() ? 0 : (Colors.A100 | Colors.RED);
        }
    }

    /* ---------- 3D 预览 ---------- */

    /**
     * 形变预览：用一份合成的 {@link ModelForm} 渲染源模型，并把形变状态写到表单上。
     *
     * <p>真正的形变渲染管线读的就是表单上的 {@code ModelMorphState}，
     * 所以这里不需要复制任何渲染逻辑 —— 拖动进度条就是在跑真正的播放路径。</p>
     */
    private static final class MorphPreview extends UIModelRenderer
    {
        private ModelForm form;
        private String modelId;
        private String sourceId;
        private String targetId;
        private ModelMorphData data;
        private ModelMorphData previewData;
        private int previewDataHash = Integer.MIN_VALUE;
        private float progress;

        /** 当前预览的是哪一侧的模型（决定点选落到源还是目标列表）。 */
        private boolean showTarget;

        /** 骨骼快照，供投影拾取的 key 反查与高亮使用。 */
        private ModelMorphSkeleton skeleton;

        /** 点选回调：命中某个 Cube 时通知编辑器。 */
        private java.util.function.BiConsumer<Boolean, String> onPick;

        /** 当前需要高亮的 Cube key 集合（来自列表选择，支持多选）。 */
        private final java.util.Set<String> highlighted = new java.util.LinkedHashSet<>();

        /** 鼠标悬浮命中的 Cube key（预高亮）。 */
        private String hovered;

        /**
         * 渲染时缓存的「投影 × 视图」矩阵与视口。
         *
         * <p>拾取必须在<b>渲染用的同一套矩阵</b>下做：自己用 camera 重新拼一遍很容易
         * 与实际绘制差一个平移或翻转，导致点哪里都选不中。</p>
         */
        private int lastViewportX;
        private int lastViewportY;
        private int lastViewportW;
        private int lastViewportH;
        private boolean hasViewProjection;

        private MorphPreview()
        {
            this.grid = true;
            this.setRotation(25F, -10F);
            this.setDistance(4F);
        }

        private void setPickHandler(java.util.function.BiConsumer<Boolean, String> handler)
        {
            this.onPick = handler;
        }

        private void setHighlighted(java.util.Collection<String> cubeKeys)
        {
            this.highlighted.clear();

            if (cubeKeys != null)
            {
                this.highlighted.addAll(cubeKeys);
            }
        }

        /**
         * 切换预览的是源模型还是目标模型。
         *
         * <p>用户需要能对着源模型点选源 Cube、对着目标模型点选目标 Cube，
         * 两侧的骨骼结构往往完全不同，所以必须能切换。</p>
         */
        private boolean isShowingTarget()
        {
            return this.showTarget;
        }

        private void setShowTarget(boolean showTarget)
        {
            this.showTarget = showTarget;
            this.modelId = null;
        }

        /** 按下时的鼠标位置，用于区分「点击选块」与「拖拽转视角」。 */
        private int pressX = Integer.MIN_VALUE;
        private int pressY = Integer.MIN_VALUE;

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            /* 基类会把按下事件用于开始拖拽（旋转/平移），这里只记录起点，
               真正的拾取放到释放时做，才能区分「点一下」和「拖着转」。 */
            this.pressX = context.mouseX();
            this.pressY = context.mouseY();

            return super.subMouseClicked(context);
        }

        @Override
        public boolean subMouseReleased(UIContext context)
        {
            boolean handled = super.subMouseReleased(context);
            int x = context.mouseX();
            int y = context.mouseY();
            int startX = this.pressX;
            int startY = this.pressY;

            this.pressX = Integer.MIN_VALUE;
            this.pressY = Integer.MIN_VALUE;

            if (startX == Integer.MIN_VALUE || this.skeleton == null)
            {
                return handled;
            }

            /* 移动超过阈值视为拖拽转视角，不做拾取，否则每次转动视角都会误选。 */
            if (Math.abs(x - startX) > 3 || Math.abs(y - startY) > 3)
            {
                return handled;
            }

            if (!this.area.isInside(x, y))
            {
                return handled;
            }

            String picked = this.pickAt(x, y);

            if (this.onPick != null)
            {
                this.onPick.accept(this.showTarget, picked);
            }

            return picked != null || handled;
        }

        /**
         * 在 2D 坐标系下画出选中/悬浮 Cube 的包围框。
         *
         * <p>调用点必须是 {@code super.render()} 之后：BBS 的 {@code UIModelRenderer}
         * 会在渲染模型期间把 3D 矩阵压栈，结束后才恢复 2D。在压栈期间画屏幕坐标的框
         * 会被 3D 矩阵再变换一次，框因此跑到屏幕外——这是「框从不出现」的原因。</p>
         */
        private void renderHighlights(UIContext context)
        {
            if (this.skeleton == null)
            {
                return;
            }

            for (MorphScreenPicks.Box box : MorphScreenPicks.boxes(this, this.modelId).values())
            {
                String key = resolveCubeKey(this.skeleton, box.groupName(), box.cubeKey());

                if (key == null)
                {
                    continue;
                }

                boolean selected = this.highlighted.contains(key);
                boolean hover = key.equals(this.hovered);

                if (!selected && !hover)
                {
                    continue;
                }

                /* 选中用亮黄、悬浮用半透明白——后者与 BBS 原版模型编辑器一致
                   （HOVER_COLOR = Colors.setA(Colors.WHITE, 0.6F)）。 */
                int color = selected ? 0xFFFFC107 : 0x99FFFFFF;

                if (box.hasCorners())
                {
                    this.drawEdges(context, box, color);
                }
                else
                {
                    this.drawBoxRect(context, box, color);
                }
            }
        }

        /**
         * 画出立方体的 12 条真实棱边。
         *
         * <p>比外接矩形更能表达立方体的大小与朝向——矩形只反映屏幕包围盒，
         * 斜视角下会明显大于实际体积，用户无法据此判断选中的是哪一块。</p>
         */
        private void drawEdges(UIContext context, MorphScreenPicks.Box box, int color)
        {
            float[] corners = box.corners();

            for (int[] edge : MorphScreenPicks.Box.EDGES)
            {
                float ax = this.area.x + corners[edge[0] * 2] * this.area.w;
                float ay = this.area.y + corners[edge[0] * 2 + 1] * this.area.h;
                float bx = this.area.x + corners[edge[1] * 2] * this.area.w;
                float by = this.area.y + corners[edge[1] * 2 + 1] * this.area.h;

                this.drawLine(context, ax, ay, bx, by, color);
            }
        }

        /**
         * 用一像素宽的细长矩形近似一条线。
         *
         * <p>BBS 的 {@code Batcher2D} 只有 {@code box} 与 {@code outline}（轴对齐矩形），
         * 没有任意线段 API；而我们在 2D pass 里画，只能用它。用 box 铺线在像素级
         * 足够精确，且与 UI 其它元素共用同一批次。</p>
         */
        private void drawLine(UIContext context, float x1, float y1, float x2, float y2, int color)
        {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);

            if (length < 0.5F)
            {
                return;
            }

            /* 沿线段取样，步长 1px：斜线也能连续。 */
            int steps = (int) Math.ceil(length);
            float stepX = dx / steps;
            float stepY = dy / steps;

            for (int i = 0; i <= steps; i++)
            {
                float px = x1 + stepX * i;
                float py = y1 + stepY * i;

                context.batcher.box(px, py, px + 1F, py + 1F, color);
            }
        }

        /**
         * 没有角点数据时退回外接矩形（兼容旧记录）。
         */
        private void drawBoxRect(UIContext context, MorphScreenPicks.Box box, int color)
        {
            int x1 = Math.round(this.area.x + box.minX() * this.area.w);
            int x2 = Math.round(this.area.x + box.maxX() * this.area.w);
            int y1 = Math.round(this.area.y + box.minY() * this.area.h);
            int y2 = Math.round(this.area.y + box.maxY() * this.area.h);

            if (x2 <= x1 || y2 <= y1)
            {
                return;
            }

            context.batcher.outline(x1, y1, x2, y2, color);
        }

        /**
         * 根据当前鼠标位置更新悬浮高亮。
         *
         * <p>与点击拾取走同一条数据（采集到的屏幕矩形），因此悬浮与点击必然一致。</p>
         */
        private void updateHover(UIContext context)
        {
            this.hovered = null;

            if (!this.area.isInside(context.mouseX(), context.mouseY()) || this.skeleton == null)
            {
                return;
            }

            MorphScreenPicks.Box box = this.pickBox(context.mouseX(), context.mouseY());

            if (box != null)
            {
                this.hovered = resolveCubeKey(this.skeleton, box.groupName(), box.cubeKey());
            }
        }

        /**
         * 拾取面板专用配置：只显示一侧的静态模型，不参与形变。
         *
         * <p>选块需要看清每个方块的真实形状，叠加形变会让轮廓变糊、反而难以点准。</p>
         */
        private void configurePicker(String modelId)
        {
            this.sourceId = modelId;
            this.targetId = null;
            this.data = null;
            this.previewData = null;
            this.progress = 0F;

            if (!Objects.equals(modelId, this.modelId))
            {
                this.modelId = modelId;
                this.skeleton = modelId == null || modelId.isEmpty() ? null : skeletonOf(modelId);
                this.buildForm(modelId);
            }

            this.applyState();
        }

        private void configure(String sourceId, String targetId, ModelMorphData data, float progress)
        {
            boolean modelPairChanged = shouldRefitCamera(this.sourceId, this.targetId, sourceId, targetId);

            this.sourceId = sourceId;
            this.targetId = targetId;
            this.data = data;
            this.progress = progress;

            /* 预览必须能看到形变，即使关键帧上的 Morph 开关还没打开：
               这里用一份强制开启的副本，真实关键帧数据不受影响。 */
            if (data != null && data.hashCode() != this.previewDataHash)
            {
                this.previewDataHash = data.hashCode();
                this.previewData = data.copy();
                this.previewData.setEnabled(true);
            }

            /* 预览跟随当前要看的一侧：源与目标的骨骼结构通常完全不同，
               必须能分别对着看、对着点。源模型缺失时退化成目标模型。 */
            String id = previewModelId(this.showTarget, sourceId, targetId, progress,
                ModelMorphRuntime.model(sourceId) != null, ModelMorphRuntime.model(targetId) != null);

            if (!Objects.equals(id, this.modelId) || modelPairChanged)
            {
                this.modelId = id;
                this.skeleton = id == null || id.isEmpty() ? null : skeletonOf(id);
                /* At 100%, only the displayed endpoint model changes. Keep the user's orbit camera;
                   refit only when the source/target pair itself changes. */
                this.buildForm(id, modelPairChanged);
            }

            this.applyState();
        }

        private void buildForm(String id)
        {
            this.buildForm(id, true);
        }

        private void buildForm(String id, boolean fitCamera)
        {
            this.form = null;

            if (id == null || id.isEmpty() || ModelMorphRuntime.model(id) == null)
            {
                return;
            }

            ModelForm previewForm = new ModelForm();

            previewForm.model.set(id);
            this.form = previewForm;

            if (fitCamera)
            {
                this.fitCamera(previewForm);
            }
        }

        private void applyState()
        {
            if (this.form == null)
            {
                return;
            }

            ModelMorphState state = ModelMorphRuntime.state(this.form);

            if (state == null)
            {
                return;
            }

            /* 拾取面板没有目标模型，天然走清空分支，显示纯模型。 */
            boolean sourceIsDisplayed = this.sourceId != null && this.sourceId.equals(this.form.model.get());

            if (sourceIsDisplayed && this.targetId != null && this.progress > 0F
                && this.progress < 1F - PREVIEW_ENDPOINT_EPSILON)
            {
                state.set(this.sourceId, this.targetId, this.progress,
                    this.previewData == null ? this.data : this.previewData);
            }
            else
            {
                state.clear();
            }
        }

        private void fitCamera(ModelForm previewForm)
        {
            ModelBindingGeometry geometry = ModelBindingGeometry.create(previewForm);

            if (geometry == null)
            {
                this.setPosition(0F, 0.5F, 0F);
                this.setDistance(4F);
                this.setRotation(25F, -10F);

                return;
            }

            Vector3f min = geometry.min();
            Vector3f max = geometry.max();
            Vector3f center = new Vector3f(min).add(max).mul(0.5F);
            float span = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));

            this.setPosition(-center.x, center.y, -center.z);
            this.setDistance(Math.max(1F, span * 1.3F));
            this.setRotation(25F, -10F);
        }

        private String pickAt(int mouseX, int mouseY)
        {
            if (this.modelId == null || this.modelId.isEmpty())
            {
                return null;
            }

            /* 用渲染时记录的真实屏幕矩形做拾取。
               这些矩形由渲染器在 renderCube 阶段用最终模型矩阵投影得到，
               与画面完全一致，不依赖 UI 侧重拼变换链。 */
            MorphScreenPicks.Box box = this.pickBox(mouseX, mouseY);

            if (box == null)
            {
                return null;
            }

            /* 渲染侧和快照侧都使用完整骨骼路径，再配短 Cube key 精确反查。 */
            return resolveCubeKey(this.skeleton, box.groupName(), box.cubeKey());
        }

        /**
         * 把屏幕坐标换算成采集表里的归一化坐标并查询。
         */
        private MorphScreenPicks.Box pickBox(int mouseX, int mouseY)
        {
            if (this.modelId == null || this.modelId.isEmpty() || this.lastViewportW <= 0 || this.lastViewportH <= 0)
            {
                return null;
            }

            /* 采集端已经把 Y 翻成「屏幕向下」的约定（见 CubicCubeRendererMorphMixin：
               screenY = 1 - (ndcY * 0.5 + 0.5)），因此这里<b>不能再翻</b>。
               两边都翻会导致双重翻转，回到垂直镜像——这正是上一轮「修了还是 miss」的原因。 */
            float ndcX = (mouseX - this.lastViewportX) / (float) this.lastViewportW;
            float ndcY = (mouseY - this.lastViewportY) / (float) this.lastViewportH;

            return MorphScreenPicks.pick(this, this.modelId, ndcX, ndcY, 0.01F);
        }

        /**
         * 用「父级组名 + 短 key」在骨架里反查 Cube 的完整 key。
         *
         * <p>组名在同一模型内唯一，因此这一步不会产生歧义。</p>
         */
        private static String resolveCubeKey(ModelMorphSkeleton skeleton, String groupPath, String shortKey)
        {
            if (skeleton == null || groupPath == null || shortKey == null)
            {
                return null;
            }

            for (ModelMorphSkeleton.GroupSpec group : skeleton.groups())
            {
                if (!groupPath.equals(group.path()))
                {
                    continue;
                }

                for (ModelMorphSkeleton.CubeSpec cube : group.cubes())
                {
                    if (shortCubeKey(cube, group).equals(shortKey))
                    {
                        return cube.key();
                    }
                }
            }

            return null;
        }

        @Override
        public void render(UIContext context)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff141414);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A25);

            /* 采集必须<b>围绕</b>本次绘制：renderCube 在这期间被调用，
               把每个 Cube 的屏幕矩形写进记录表。
               矩阵用<b>上一帧</b>缓存的值——相机投影在 super.render() 内部才更新，
               而同一帧内用它采集与绘制是自洽的；跨帧只差一次鼠标移动，
               对拾取没有可感知影响。 */
            /* 矩阵还没算出来（首帧）时不要采集：用单位矩阵投影会得到全错的矩形，
               而且会覆盖掉上一帧的正确数据。 */
            if (this.hasViewProjection && this.camera != null)
            {
                /* 只传 projection：渲染栈里已经有 view 与相机平移了。 */
                MorphScreenPicks.beginCapture(this, this.modelId, this.camera.projection,
                    this.area.x, this.area.y, this.area.w, this.area.h);
            }

            try
            {
                super.render(context);
            }
            finally
            {
                MorphScreenPicks.endCapture();
            }

            /* 悬浮预高亮：UIElement 没有 hover 事件，但 render 每帧执行、
               context 里鼠标坐标实时可用，因此在这里算最直接。 */
            this.updateHover(context);

            /* 高亮框必须画在 super.render() <b>之后</b>：那时 UIModelRenderer 已经
               restoreMatrices() 恢复成 2D 坐标系，屏幕坐标才有效。 */
            this.renderHighlights(context);

            /* 绘制结束后刷新矩阵，供下一帧采集与拾取使用。 */
            if (this.camera != null)
            {
                this.lastViewportX = this.area.x;
                this.lastViewportY = this.area.y;
                this.lastViewportW = this.area.w;
                this.lastViewportH = this.area.h;
                this.hasViewProjection = true;
            }
        }

        @Override
        protected void renderUserModelOverlay(UIContext context)
        {
            /* 刻意不在这里画高亮框。
               BBS 的 UIModelRenderer#renderModel 在 stack.push() 之后、stack.pop() 之前
               调用本方法，此时 MatrixStack 里仍是 3D 投影矩阵；在这里用屏幕坐标
               batcher.outline 画框会被 3D 矩阵再变换一次，框会跑到屏幕外。
               正确位置是 super.render() 之后——那里已 restoreMatrices() 恢复成 2D 坐标系。 */
            super.renderUserModelOverlay(context);
        }

        @Override
        protected void renderUserModel(UIContext context)
        {
            if (this.form == null)
            {
                return;
            }

            FormRenderingContext formContext = new FormRenderingContext()
                .set(FormRenderType.PREVIEW, this.entity, context.batcher.getContext().getMatrices(),
                    LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
                .camera(this.camera)
                .modelRenderer(context.getTick());

            FormUtilsClient.render(this.form, formContext);
        }
    }

    /**
     * A dashboard instance with only this editor panel. Reopening the menu that launched the page
     * keeps the keyframe editor and its working context intact when the user closes the full-screen page.
     */
    private static final class MappingDashboard extends UIDashboard
    {
        private final UIBaseMenu previousMenu;
        private UICubeMappingEditorPanel editorPanel;

        private MappingDashboard(UIBaseMenu previousMenu)
        {
            super();
            this.previousMenu = previousMenu;
        }

        @Override
        protected void registerPanels()
        {
            /* This dashboard is a dedicated host for one editor, so skip the unrelated BBS panels. */
        }

        private void setEditorPanel(UICubeMappingEditorPanel editorPanel)
        {
            this.editorPanel = editorPanel;
        }

        @Override
        public void onOpen(UIBaseMenu oldMenu)
        {
            super.onOpen(oldMenu);
            this.setPanel(this.editorPanel);
        }

        @Override
        protected void closeMenu()
        {
            if (this.previousMenu == null)
            {
                super.closeMenu();

                return;
            }

            if (!this.main.isVisible())
            {
                this.main.setVisible(true);
            }

            UIScreen.open(this.previousMenu);
        }
    }
}
