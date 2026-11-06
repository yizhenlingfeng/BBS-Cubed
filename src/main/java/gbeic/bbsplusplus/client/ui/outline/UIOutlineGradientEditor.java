package gbeic.bbsplusplus.client.ui.outline;

import gbeic.bbsplusplus.api.OutlineGradient;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Direction;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One full width gradient editing row: the selected stop's colour swatch followed by
 * {@link UIOutlineGradientBar}.
 *
 * <p>It keeps its own working copy of the ramp. Every edit is turned into a fresh copy and handed
 * to {@code setGradient}, so the model never shares a mutable object with the widget; a sync pass
 * pushing the model value back in is a no-op because {@code UIOutlineGradientBar#setGradient}
 * ignores equal content.</p>
 */
public class UIOutlineGradientEditor extends UIElement
{
    private static final int SWATCH_WIDTH = 20;

    private final Supplier<OutlineGradient> getGradient;
    private final Consumer<OutlineGradient> setGradient;

    private final UIColor stopColor;
    private final UIOutlineGradientBar bar;

    private boolean syncing;

    public UIOutlineGradientEditor(Supplier<OutlineGradient> getGradient, Consumer<OutlineGradient> setGradient)
    {
        this.getGradient = getGradient;
        this.setGradient = setGradient;

        this.h(UIConstants.CONTROL_HEIGHT).row(2).padding(0);

        this.bar = new UIOutlineGradientBar();
        this.bar.setOnSelect(this::syncStopColor);
        this.bar.setOnChange(this::submit);

        this.stopColor = new UIColor((argb) ->
        {
            if (!this.syncing && argb != null)
            {
                this.bar.setSelectedColor(argb);
            }
        });
        this.stopColor.withAlpha().direction(Direction.LEFT).noLabel().h(UIConstants.CONTROL_HEIGHT).w(SWATCH_WIDTH);

        this.add(this.stopColor, this.bar);
    }

    /** Pushes the model's ramp into the widget; used by the sync pass of both editors. */
    public void refresh()
    {
        if (this.bar.isDragging())
        {
            return;
        }

        this.syncing = true;

        this.bar.setGradient(this.getGradient.get());
        this.syncStopColor();

        this.syncing = false;
    }

    private void syncStopColor()
    {
        this.stopColor.setColor(this.bar.getSelectedColor());
    }

    private void submit()
    {
        if (this.syncing)
        {
            return;
        }

        OutlineGradient gradient = this.bar.getGradient();

        if (gradient != null)
        {
            this.setGradient.accept(gradient.copy());
        }
    }
}
