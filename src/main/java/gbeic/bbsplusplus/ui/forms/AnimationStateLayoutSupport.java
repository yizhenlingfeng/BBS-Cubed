package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.api.TransparentDockLayout;
import gbeic.bbsplusplus.ui.miniwindow.IMiniWindowDockHost;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.presets.PresetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 动画状态编辑器的可停靠布局、检查器挂载和 Pose 撤销恢复业务。
 * Mixin 只负责稳定注入点，本类可被后续平台源码集直接复用。
 */
public class AnimationStateLayoutSupport
{
    private static final Logger LOGGER = LoggerFactory.getLogger("bbspp/animation_state_layout");

    private final UIFormEditor formEditor;
    private final UIElement statesEditor;
    private final UIAnimationStateEditor statesKeyframes;
    private final UIIcon shiftDuration;

    private UIDockLayout stateDock;
    private AnimationStateLayoutSource stateLayoutSource;
    private UIElement stateTimelinePanel;
    private UIElement stateInspectorPanel;
    private UIAnimationStatePreviewPanel statePreviewPanel;
    private UIKeyframeEditor stateKeyframeEditor;
    private UIIcon stateLayoutLock;
    private UIIcon stateLayoutPresets;
    private UIIcon stateLayoutReset;
    private UICopyPasteController stateLayoutController;
    private UIKeyframeEditor loggedStateKeyframeEditor;
    private boolean poseUndoPending;
    private String poseUndoSheetId;
    private float poseUndoTick;
    private int poseUndoIndex = -1;
    private List<String> poseUndoBones = Collections.emptyList();

    public AnimationStateLayoutSupport(
        UIFormEditor formEditor,
        UIElement statesEditor,
        UIAnimationStateEditor statesKeyframes,
        UIIcon shiftDuration
    )
    {
        this.formEditor = formEditor;
        this.statesEditor = statesEditor;
        this.statesKeyframes = statesKeyframes;
        this.shiftDuration = shiftDuration;
    }

    public void activate()
    {
        UIElement editArea = this.statesKeyframes.editArea;

        editArea.removeFromParent();

        /* The old corner resize handle controls the fixed two-panel layout. */
        for (IUIElement child : new ArrayList<>(this.statesKeyframes.getChildren()))
        {
            if (child instanceof UIDraggable draggable)
            {
                draggable.removeFromParent();
            }
        }

        this.statesKeyframes.full(this.statesEditor);
        this.stateTimelinePanel = new UIElement();
        this.stateInspectorPanel = new UIElement();
        editArea.resetFlex().full(this.stateInspectorPanel);
        this.stateInspectorPanel.add(editArea);

        this.stateLayoutSource = new AnimationStateLayoutSource();
        this.stateDock = new UIDockLayout();
        ((TransparentDockLayout) this.stateDock).bbspp_cml$setTransparentBase(true);
        this.stateDock.relative(this.statesEditor).x(20).w(1F, -20).h(1F);
        this.stateDock
            .source(this.stateLayoutSource)
            .frameless(AnimationStateLayoutSource.PREVIEW)
            .onChanged(this::updateStatePanelInsets)
            .onLayoutSettled(this.stateLayoutSource::save);
        this.statePreviewPanel = new UIAnimationStatePreviewPanel(this.statesKeyframes::getState);
        this.stateDock.addPanel(
            AnimationStateLayoutSource.PREVIEW,
            this.statePreviewPanel,
            Icons.VIDEO_CAMERA,
            SnowUIKeys.ANIMATION_STATE_PANEL_PREVIEW
        );
        this.stateDock.addPanel(
            AnimationStateLayoutSource.TIMELINE,
            this.stateTimelinePanel,
            Icons.GRAPH,
            SnowUIKeys.ANIMATION_STATE_PANEL_TIMELINE
        );
        this.stateDock.addPanel(
            AnimationStateLayoutSource.INSPECTOR,
            this.stateInspectorPanel,
            Icons.PROPERTIES,
            SnowUIKeys.ANIMATION_STATE_PANEL_INSPECTOR
        );
        this.stateDock.mount();
        this.statesEditor.add(this.stateDock);

        this.stateLayoutController = new UICopyPasteController(
            PresetManager.LAYOUTS,
            "_CopyAnimationStateLayout"
        )
            .supplier(this::getStateLayoutPresetData)
            .consumer(this::applyStateLayoutFromPreset);

        this.stateLayoutLock = new UIIcon(
            () -> this.stateDock.isLocked() ? Icons.LOCKED : Icons.UNLOCKED,
            (button) ->
            {
                this.stateDock.toggleLock();
                this.updateStateLayoutTooltip();
            }
        );
        this.stateLayoutLock.relative(this.shiftDuration).y(1F);

        this.stateLayoutPresets = new UIIcon(Icons.LAYOUT, (button) ->
        {
            UIContext context = this.formEditor.getContext();

            if (context != null)
            {
                this.stateLayoutController.openPresets(context, context.mouseX, context.mouseY);
            }
        });
        this.stateLayoutPresets.relative(this.stateLayoutLock).y(1F);
        this.stateLayoutPresets.tooltip(UIKeys.FILM_LAYOUT_PRESETS, Direction.RIGHT);

        this.stateLayoutReset = new UIIcon(
            Icons.REFRESH,
            (button) -> this.resetStateLayout()
        );
        this.stateLayoutReset.relative(this.stateLayoutPresets).y(1F);
        this.stateLayoutReset.tooltip(UIKeys.FILM_LAYOUT_RESET, Direction.RIGHT);

        this.statesEditor.add(
            this.stateLayoutLock,
            this.stateLayoutPresets,
            this.stateLayoutReset
        );
        this.updateStateLayoutTooltip();
        this.syncStateDockVisibility(false);
    }

    public boolean clickPreviewControls(UIContext context)
    {
        return this.statesEditor.isVisible()
            && this.statesEditor.isEnabled()
            && this.statePreviewPanel != null
            && this.statePreviewPanel.clickControls(context);
    }

    public void prepareStateKeyframeEditor()
    {
        UIElement editArea = this.statesKeyframes.editArea;

        if (this.stateKeyframeEditor != null
            && this.stateKeyframeEditor.editor != null
            && this.stateKeyframeEditor.editor.getParent() == editArea)
        {
            this.stateKeyframeEditor.editor.removeFromParent();
        }

        this.stateKeyframeEditor = null;

        if (editArea.getParent() != this.statesKeyframes)
        {
            editArea.removeFromParent();
            editArea.resetFlex()
                .relative(this.statesKeyframes)
                .x(BBSSettings.editorLayoutSettings.getStateEditorSizeH())
                .wTo(this.statesKeyframes.area, 1F)
                .h(1F);
            this.statesKeyframes.add(editArea);
            this.statesKeyframes.resize();
        }
    }

    public void attachStateKeyframeEditor(UIKeyframeEditor editor)
    {
        if (this.stateKeyframeEditor != null
            && this.stateKeyframeEditor != editor
            && this.stateKeyframeEditor.getParent() == this.stateTimelinePanel)
        {
            this.stateKeyframeEditor.removeFromParent();
        }

        this.stateKeyframeEditor = editor;

        UIElement editArea = this.statesKeyframes.editArea;

        editArea.setVisible(editor != null);
        editArea.setEnabled(editor != null);

        if (editArea.getParent() != this.stateInspectorPanel)
        {
            editArea.removeFromParent();
            this.stateInspectorPanel.add(editArea);
        }

        if (editor != null && editor.getParent() != this.stateTimelinePanel)
        {
            editor.removeFromParent();
            this.stateTimelinePanel.add(editor);
        }

        this.updateStatePanelInsets();
        this.stateTimelinePanel.resize();
        this.stateInspectorPanel.resize();

        if (editor != null)
        {
            editor.target(editArea);
            editor.setVisible(true);
            editor.setEnabled(true);
            editor.setTimelineVisible(true);
            editor.setPropertiesVisible(true);
            editor.view.setVisible(true);
            editor.view.setEnabled(true);
            this.mountStateInspector(editor);
            editor.resize();
        }

        this.syncStateDockVisibility(editor != null);
    }

    public void keepStateDockContentMounted()
    {
        if (!this.statesEditor.isVisible() || this.stateDock == null)
        {
            return;
        }

        UIKeyframeEditor editor = this.statesKeyframes.keyframeEditor;
        UIElement editArea = this.statesKeyframes.editArea;
        boolean hasEditor = editor != null;

        this.syncStateDockVisibility(hasEditor);

        if (!hasEditor)
        {
            this.stateKeyframeEditor = null;

            return;
        }

        if (this.stateKeyframeEditor != editor
                || editor.getParent() != this.stateTimelinePanel
                || editArea.getParent() != this.stateInspectorPanel)
        {
            this.attachStateKeyframeEditor(editor);
        }

        this.mountStateInspector(editor);
        editor.setVisible(true);
        editor.setEnabled(true);
        editor.setTimelineVisible(true);
        editor.setPropertiesVisible(true);
        editor.view.setVisible(true);
        editor.view.setEnabled(true);

        int top = !this.stateDock.isLocked()
            && !(this.stateDock instanceof IMiniWindowDockHost host
                && host.hostIsFloating(AnimationStateLayoutSource.TIMELINE)) ? 20 : 0;
        boolean wrongBounds = editor.area.x != this.stateTimelinePanel.area.x
            || editor.area.y != this.stateTimelinePanel.area.y + top
            || editor.area.w != this.stateTimelinePanel.area.w
            || editor.area.h != this.stateTimelinePanel.area.h - top;

        if (wrongBounds)
        {
            this.updateStatePanelInsets();
            this.stateTimelinePanel.resize();
            this.stateInspectorPanel.resize();
            editor.resize();
        }

        if (editor != this.loggedStateKeyframeEditor)
        {
            this.loggedStateKeyframeEditor = editor;
            LOGGER.debug(
                "[animation-state-layout] sheets={}, timelinePanel=({},{} {}x{}), editor=({},{} {}x{}, parent={}), inspector=({},{} {}x{}), editArea=({},{} {}x{}, parent={})",
                editor.view.getDopeSheet().getSheets().size(),
                this.stateTimelinePanel.area.x,
                this.stateTimelinePanel.area.y,
                this.stateTimelinePanel.area.w,
                this.stateTimelinePanel.area.h,
                editor.area.x,
                editor.area.y,
                editor.area.w,
                editor.area.h,
                editor.getParent() == null ? "null" : editor.getParent().getClass().getSimpleName(),
                this.stateInspectorPanel.area.x,
                this.stateInspectorPanel.area.y,
                this.stateInspectorPanel.area.w,
                this.stateInspectorPanel.area.h,
                editArea.area.x,
                editArea.area.y,
                editArea.area.w,
                editArea.area.h,
                editArea.getParent() == null ? "null" : editArea.getParent().getClass().getSimpleName()
            );
        }
    }

    private void syncStateDockVisibility(boolean visible)
    {
        if (this.stateDock == null)
        {
            return;
        }

        this.stateDock.setVisible(visible);
        this.stateDock.setEnabled(visible);
        this.statesKeyframes.editArea.setVisible(visible);
        this.statesKeyframes.editArea.setEnabled(visible);
        this.stateLayoutLock.setVisible(visible);
        this.stateLayoutLock.setEnabled(visible);
        this.stateLayoutPresets.setVisible(visible);
        this.stateLayoutPresets.setEnabled(visible);
        this.stateLayoutReset.setVisible(visible);
        this.stateLayoutReset.setEnabled(visible);
    }

    /** Keep the keyframe factory inside the inspector panel's real UI tree. */
    private void mountStateInspector(UIKeyframeEditor editor)
    {
        if (editor == null || editor.editor == null)
        {
            return;
        }

        UIElement editArea = this.statesKeyframes.editArea;
        UIElement factory = editor.editor;

        if (factory.getParent() != editArea)
        {
            factory.removeFromParent();
            factory.resetFlex().full(editArea);
            factory.setVisible(true);
            editArea.add(factory);
        }

        editArea.resize();
    }

    public void capturePoseBeforeHistoryChange()
    {
        this.poseUndoPending = false;
        this.poseUndoSheetId = null;
        this.poseUndoIndex = -1;
        this.poseUndoBones = Collections.emptyList();

        UIKeyframeEditor editor = this.statesKeyframes.keyframeEditor;

        if (editor == null || !(editor.editor instanceof UIPoseKeyframeFactory poseFactory))
        {
            return;
        }

        Keyframe<?> keyframe = poseFactory.getKeyframe();
        UIKeyframeSheet sheet = editor.view.getGraph().getSheet(keyframe);

        if (keyframe == null || sheet == null)
        {
            return;
        }

        this.poseUndoPending = true;
        this.poseUndoSheetId = sheet.id;
        this.poseUndoTick = keyframe.getTick();
        this.poseUndoIndex = sheet.channel.getKeyframes().indexOf(keyframe);

        if (poseFactory.poseEditor != null && poseFactory.poseEditor.groups != null)
        {
            this.poseUndoBones = new ArrayList<>(
                poseFactory.poseEditor.groups.list.getCurrent()
            );
        }
    }

    @SuppressWarnings("unchecked")
    public void restorePoseAfterHistoryChange()
    {
        if (!this.poseUndoPending)
        {
            return;
        }

        this.poseUndoPending = false;
        UIKeyframeEditor editor = this.statesKeyframes.keyframeEditor;

        if (editor == null || this.poseUndoSheetId == null)
        {
            return;
        }

        UIKeyframeSheet sheet = editor.view.getDopeSheet().getSheet(this.poseUndoSheetId);

        if (sheet == null)
        {
            return;
        }

        List<Keyframe<?>> keyframes = (List<Keyframe<?>>) (List<?>) sheet.channel.getKeyframes();
        Keyframe<?> liveKeyframe = null;

        for (Keyframe<?> keyframe : keyframes)
        {
            if (Math.abs(keyframe.getTick() - this.poseUndoTick) < 0.0001F)
            {
                liveKeyframe = keyframe;

                break;
            }
        }

        if (liveKeyframe == null
            && this.poseUndoIndex >= 0
            && this.poseUndoIndex < keyframes.size())
        {
            liveKeyframe = keyframes.get(this.poseUndoIndex);
        }

        if (liveKeyframe == null)
        {
            return;
        }

        /* Recreate the factory so its Pose, PoseTransform and gizmo all point
         * at the live objects rebuilt by ValueChangeUndo. */
        editor.view.pickKeyframe(liveKeyframe);

        if (editor.editor instanceof UIPoseKeyframeFactory poseFactory
            && poseFactory.poseEditor != null)
        {
            poseFactory.poseEditor.restoreSelection(this.poseUndoBones);
        }

        this.mountStateInspector(editor);
    }

    private void updateStatePanelInsets()
    {
        if (this.stateDock == null)
        {
            return;
        }

        this.layoutStatePanelContent(
            AnimationStateLayoutSource.TIMELINE,
            this.stateTimelinePanel,
            this.stateKeyframeEditor
        );
        this.layoutStatePanelContent(
            AnimationStateLayoutSource.INSPECTOR,
            this.stateInspectorPanel,
            this.statesKeyframes.editArea
        );
    }

    private void layoutStatePanelContent(String panelId, UIElement panel, UIElement content)
    {
        if (panel == null || content == null)
        {
            return;
        }

        boolean floating = this.stateDock instanceof IMiniWindowDockHost host
            && host.hostIsFloating(panelId);
        int top = !this.stateDock.isLocked() && !floating ? 20 : 0;

        content.resetFlex().relative(panel).y(top).w(1F).h(1F, -top);
    }

    private MapType getStateLayoutPresetData()
    {
        MapType data = new MapType();
        EditorLayoutNode root = this.stateDock.getLayoutRoot();

        if (root != null)
        {
            data.put("animation_state_layout", root.toData());
        }

        return data;
    }

    private void applyStateLayoutFromPreset(MapType data, int mouseX, int mouseY)
    {
        BaseType value = data == null ? null : data.get("animation_state_layout");
        EditorLayoutNode root = value == null ? null : EditorLayoutNode.fromData(value);

        if (root == null)
        {
            return;
        }

        this.stateDock.applyLayoutRoot(root);
        this.stateLayoutSource.save();
        this.updateStatePanelInsets();
    }

    private void resetStateLayout()
    {
        if (!this.stateDock.isLocked())
        {
            this.stateDock.toggleLock();
        }

        this.stateDock.resetLayout();
        this.updateStateLayoutTooltip();
    }

    private void updateStateLayoutTooltip()
    {
        this.stateLayoutLock.tooltip(
            this.stateDock.isLocked() ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK,
            Direction.RIGHT
        );
    }

}
