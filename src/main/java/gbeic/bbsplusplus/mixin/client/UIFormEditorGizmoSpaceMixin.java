package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BBS 2.7 的 UIPickableFormRenderer.renderAxes 会调用 UIFormEditor.getGizmoSpace()；
 * 预览场景下 UIFormEditor 存在但 this.editor 可能为 null，原实现直接解引用触发 NPE，
 * 整个预览 pass（含 gizmo 与拾取模板缓冲）被丢弃，表现为模型编辑器点不中肢体。
 * 这里在 editor 为 null 时返回默认变换空间。
 */
@Mixin(UIFormEditor.class)
public class UIFormEditorGizmoSpaceMixin
{
    @Redirect(
        method = "getGizmoSpace()Lmchorse/bbs_mod/ui/framework/elements/input/drag/TransformSpace;",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/forms/editors/forms/UIForm;getGizmoSpace()Lmchorse/bbs_mod/ui/framework/elements/input/drag/TransformSpace;"
        ),
        remap = false
    )
    private TransformSpace bbsplusplus$safeGetGizmoSpace(UIForm editor)
    {
        if (editor == null)
        {
            return TransformSpace.LOCAL;
        }

        return editor.getGizmoSpace();
    }
}
