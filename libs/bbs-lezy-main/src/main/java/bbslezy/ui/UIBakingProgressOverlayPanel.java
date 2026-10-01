package bbslezy.ui;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIBakingProgressOverlayPanel extends UIOverlayPanel
{
    private volatile float progress;
    private volatile String status = "";
    private volatile boolean finished;

    public UIBakingProgressOverlayPanel()
    {
        super(L10n.lang("bbslezy.ui.replays.baking_progress"));

        this.close.setVisible(false);

        UIElement bar = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);

                int x = this.area.x + 12;
                int y = this.area.y + 12;
                int w = this.area.w - 24;
                int h = 14;

                context.batcher.box(x, y, x + w, y + h, Colors.A50);
                context.batcher.outline(x, y, x + w, y + h, Colors.A100);

                int filled = (int) (w * Math.max(0F, Math.min(1F, progress)));
                int primary = BBSSettings.primaryColor(Colors.A100);

                if (filled > 0)
                {
                    context.batcher.box(x + 1, y + 1, x + filled - 1, y + h - 1, primary);
                }

                int textY = y + h + 8;
                int textW = context.batcher.getFont().getWidth(status);
                context.batcher.textShadow(status, this.area.mx() - textW / 2F, textY);
            }
        };

        bar.relative(this.content).xy(0, 0).w(1F).h(1F);
        this.content.add(bar);
    }

    public void updateProgress(float progress, String status)
    {
        this.progress = progress;
        this.status = status;
    }

    public void markFinished()
    {
        this.finished = true;
    }

    @Override
    public void close()
    {
        if (this.finished)
        {
            super.close();
        }
    }
}
