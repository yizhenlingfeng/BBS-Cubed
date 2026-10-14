package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fix NPE in UIFormEditor.getOrigin() when this.editor is null.
 *
 * In form-grid preview scenarios (FormCellRenderer), a UIFormEditor instance exists
 * but no concrete sub-editor (this.editor) has been opened. The original code falls
 * through to {@code return this.editor.getOrigin(transition)} which NPEs, aborting
 * the entire FBO model preview pass — the cell renders blank.
 *
 * This redirect returns null when editor is null; the caller (UIPickableFormRenderer
 * .renderAxes) already null-checks the returned matrix and skips gizmo drawing, so
 * the model itself still renders into the FBO.
 */
@Mixin(UIFormEditor.class)
public class UIFormEditorGetOriginMixin
{
    @Redirect(
        method = "getOrigin(F)Lorg/joml/Matrix4f;",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/forms/editors/forms/UIForm;getOrigin(F)Lorg/joml/Matrix4f;"
        ),
        remap = false
    )
    private Matrix4f bbsplusplus$safeGetOrigin(UIForm editor, float transition)
    {
        if (editor == null)
        {
            return null;
        }
        return editor.getOrigin(transition);
    }
}
