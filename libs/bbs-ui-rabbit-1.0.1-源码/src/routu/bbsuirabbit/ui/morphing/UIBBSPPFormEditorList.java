package routu.bbsuirabbit.ui.morphing;

import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;

public class UIBBSPPFormEditorList extends UIBBSPPFormList
{
    public UIBBSPPFormEditorList(IUIFormList palette)
    {
        super(palette);

        this.edit.removeFromParent();
        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).markContainer();
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(256))
        {
            this.palette.exit();
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, 0x88000000);

        super.render(context);
    }
}
