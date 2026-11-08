package gbeic.bbsplusplus.timeline.ui;

import gbeic.bbsplusplus.client.ui.utils.UIListHeightGrip;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;

/**
 * 动作页「动作」/「动画」两个列表的底边高度抓手：安装、被清掉后补回、把持久化高度同步给列表。
 * <p>
 * 业务逻辑集中在这里，{@code UIActionsConfigEditorMixin} 只做一行委托。抓手只在列表直接挂在
 * 编辑器上时启用：关键帧弹窗宽版会把两个列表分别塞进 {@code UI.row(UI.column(...), ...)}，
 * 那时列表下方紧挨着速度 / 淡入淡出 / 刻三行，再贴抓取带会压住它们的点击区。
 * </p>
 */
public class ActionsListHeightGrips
{
    private static final String ACTIONS_KEY = "bbspp.actions_list_height";
    private static final String ANIMATIONS_KEY = "bbspp.animations_list_height";

    private final UIActionsConfigEditor editor;
    private final UIElement actions;
    private final UIElement animations;
    private UIListHeightGrip actionsGrip;
    private UIListHeightGrip animationsGrip;

    public ActionsListHeightGrips(UIActionsConfigEditor editor, UIElement actions, UIElement animations)
    {
        this.editor = editor;
        this.actions = actions;
        this.animations = animations;
    }

    public void sync()
    {
        this.actionsGrip = this.sync(this.actionsGrip, ACTIONS_KEY, this.actions);
        this.animationsGrip = this.sync(this.animationsGrip, ANIMATIONS_KEY, this.animations);
    }

    private UIListHeightGrip sync(UIListHeightGrip grip, String key, UIElement list)
    {
        if (list == null || list.getParent() != this.editor)
        {
            if (grip != null && grip.getParent() == this.editor)
            {
                this.editor.remove(grip);
            }

            return grip;
        }

        if (grip == null)
        {
            grip = new UIListHeightGrip(key, list);
        }

        if (grip.getParent() != this.editor)
        {
            this.editor.add(grip);
            /* add() 会把抓手包进父级的 ChildResizer（那会占位并挤掉列布局），立刻换回只定位不占位的 band */
            grip.resizer(grip.band());
            /* 关键帧弹窗重建时会重置列表高度，每次挂回都重新套用一次持久化的值 */
            grip.applyHeight();
        }

        return grip;
    }
}
