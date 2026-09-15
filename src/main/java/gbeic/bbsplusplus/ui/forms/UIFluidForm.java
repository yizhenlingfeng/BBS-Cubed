package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.forms.FluidForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import gbeic.bbsplusplus.ui.forms.UIFluidFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIFluidForm extends UIForm<FluidForm>
{
    private UIFluidFormPanel fluidFormPanel;

    public UIFluidForm()
    {
        super();

        this.fluidFormPanel = new UIFluidFormPanel(this);
        this.defaultPanel = this.fluidFormPanel;

        this.registerPanel(this.defaultPanel, FluidUIKeys.FLUID_TITLE, Icons.MATERIAL);
        this.registerDefaultPanels();
    }
}
