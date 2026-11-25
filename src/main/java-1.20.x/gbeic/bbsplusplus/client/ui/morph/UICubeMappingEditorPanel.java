package gbeic.bbsplusplus.client.ui.morph;

import gbeic.bbsplusplus.client.morph.MappingGroup;
import gbeic.bbsplusplus.client.morph.ModelMorphAdapter;
import gbeic.bbsplusplus.client.morph.ModelMorphData;
import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import gbeic.bbsplusplus.client.morph.ModelMorphSkeleton;
import gbeic.bbsplusplus.client.morph.ModelMorphValidation;
import gbeic.bbsplusplus.client.morph.MorphAutoMatch;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
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
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

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
    private final MorphMappingHistory history = new MorphMappingHistory(new MorphMappingHistory.Host()
    {
        @Override
        public ModelMorphData data()
        {
            return UICubeMappingEditorPanel.this.data;
        }

        @Override
        public void apply(ModelMorphData snapshot)
        {
            UICubeMappingEditorPanel.this.restore(snapshot);
        }
    });

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

        this.sourceList = new CubeList(this.sourceSkeleton, true, this.cubeHost(), this::onTreeChanged);
        this.targetList = new CubeList(this.targetSkeleton, false, this.cubeHost(), this::onTreeChanged);
        this.mappingList = new MappingList(this.mappingHost());
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
    /** 两个外侧树列表回查行状态用的 host。 */
    private CubeList.Host cubeHost()
    {
        return new CubeList.Host()
        {
            @Override
            public Set<String> invalidKeys(boolean sourceSide)
            {
                return sourceSide ? UICubeMappingEditorPanel.this.invalidSources : UICubeMappingEditorPanel.this.invalidTargets;
            }

            @Override
            public Set<String> selectedKeys(boolean sourceSide)
            {
                return sourceSide ? UICubeMappingEditorPanel.this.selectedSources : UICubeMappingEditorPanel.this.selectedTargets;
            }

            @Override
            public Set<String> mappedKeys(boolean sourceSide)
            {
                return sourceSide ? UICubeMappingEditorPanel.this.mappedSources : UICubeMappingEditorPanel.this.mappedTargets;
            }
        };
    }

    /** 映射列表读写数据与撤销入口用的 host。 */
    private MappingList.Host mappingHost()
    {
        return new MappingList.Host()
        {
            @Override
            public String sourceModel()
            {
                return UICubeMappingEditorPanel.this.sourceId;
            }

            @Override
            public ModelMorphData data()
            {
                return UICubeMappingEditorPanel.this.data;
            }

            @Override
            public void pushUndo()
            {
                UICubeMappingEditorPanel.this.pushUndo();
            }

            @Override
            public void loadPreset(ModelMorphData loaded)
            {
                UICubeMappingEditorPanel.this.data.setGroups(loaded.groups());
                UICubeMappingEditorPanel.this.markChanged();
            }

            @Override
            public void onPicked(java.util.List<MappingGroup> rows)
            {
                UICubeMappingEditorPanel.this.onMappingPicked(rows);
            }
        };
    }

    private void pushUndo()
    {
        this.history.push();
    }

    private void undo()
    {
        this.history.undo();
    }

    private void redo()
    {
        this.history.redo();
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
}
