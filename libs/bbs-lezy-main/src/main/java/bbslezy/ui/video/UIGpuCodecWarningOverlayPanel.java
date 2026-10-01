package bbslezy.ui.video;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

public class UIGpuCodecWarningOverlayPanel extends UIMessageOverlayPanel
{
    public UIButton cpuRender;
    public UIButton cancel;
    public UIElement bar;

    public UIGpuCodecWarningOverlayPanel(String gpuName, String codecName, Runnable onConfirmCpu)
    {
        super(
            L10n.lang("bbslezy.ui.video.gpu_unsupported_title"),
            L10n.lang("bbslezy.ui.video.gpu_unsupported_message").format(IKey.raw(gpuName), IKey.raw(codecName))
        );

        this.cpuRender = new UIButton(L10n.lang("bbslezy.ui.video.render_cpu"), (b) ->
        {
            this.close();
            if (onConfirmCpu != null)
            {
                onConfirmCpu.run();
            }
        });

        this.cancel = new UIButton(L10n.lang("bbslezy.ui.video.cancel"), (b) -> this.close());

        this.cpuRender.w(110);
        this.cancel.w(80);

        this.bar = UI.row(this.cpuRender, this.cancel);
        this.bar.relative(this.content).x(0.5F).y(1F, -10).w(200).anchor(0.5F, 1F);
        this.content.add(this.bar);

        this.bottom = this.bar;
    }
}
