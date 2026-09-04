package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.ui.miniwindow.INonFloatingDockPanel;
import gbeic.bbsplusplus.utils.BonePriority;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Supplier;

/** Transparent dock target through which UIFormEditor's shared model renderer remains visible. */
public class UIAnimationStatePreviewPanel extends UIElement implements INonFloatingDockPanel
{
    private final UIIcon bonePriority;

    public UIAnimationStatePreviewPanel(Supplier<AnimationState> stateSupplier)
    {
        this.bonePriority = new UIIcon(Icons.LIMB, (button) ->
            BonePriority.openMenu(this.getContext(), stateSupplier.get())
        );

        this.bonePriority.relative(this).x(0).y(1F, -20).wh(20, 20);
        this.bonePriority.tooltip(SnowUIKeys.BONE_PRIORITY);
        this.add(this.bonePriority);
    }

    /** Handles monitor controls before the renderer gets a chance to start a Gizmo drag. */
    public boolean clickControls(UIContext context)
    {
        if (context == null
            || context.mouseButton != 0
            || !this.isVisible()
            || !this.isEnabled()
            || !this.bonePriority.isVisible()
            || !this.bonePriority.isEnabled()
            || !this.bonePriority.area.isInside(context))
        {
            return false;
        }

        this.bonePriority.clickItself(context);

        return true;
    }
}
