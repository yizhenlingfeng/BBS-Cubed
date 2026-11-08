package routu.bbsuirabbit.mixin;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = UIDataDashboardPanel.class, remap = false)
public interface UIDataDashboardPanelAccessor
{
    @Accessor(value = "data", remap = false)
    ValueGroup rabbit$getData();
}
