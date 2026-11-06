package gbeic.bbsplusplus.client.ui.outline;

import gbeic.bbsplusplus.api.OutlineGradient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The compact colour control used by every outline row: an optional "apply to children" icon,
 * a mode icon, then a slot that shows either the colour swatch (solid mode) or a direction
 * button that fills the swatch's place (gradient mode).
 *
 * <p>Solid and gradient share one slot. Both children are always mounted at the slot's exact
 * box and only one of them draws/handles clicks, so toggling the mode never changes the row's
 * child set. That keeps the layout completely still: BBS' row resizers otherwise hand a
 * re-shown child a stale (zero) width and re-pin the whole row, which makes the buttons jump.</p>
 *
 * <p>Turning the gradient on seeds it with the colour the swatch currently shows
 * ({@link OutlineGradient#fromSolid}), so flipping the switch never changes how the outline
 * looks by itself. Turning it off only clears the gradient — the solid colour stored in the
 * holder is left untouched, which keeps a face's "follow the layer colour" sentinel (0).</p>
 */
public class UIOutlineColorControl extends UIElement
{
    private static final int ICON_WIDTH = 16;

    private final IntSupplier getSolidColor;
    private final Supplier<OutlineGradient> getGradient;
    private final Consumer<Integer> setSolidColor;
    private final Consumer<OutlineGradient> setGradient;
    private final Runnable applyToChildren;

    private final UIIcon apply;
    private final UIIcon mode;
    private final SolidSwatch solid;
    private final DirectionButton direction;
    private final Slot slot;

    /** Last gradient axis, so turning the gradient back on restores the previous direction. */
    private OutlineGradient.Axis lastAxis = OutlineGradient.Axis.VERTICAL;

    /** Which of the two slot children is currently live (drawing and taking clicks). */
    private boolean gradientShown;

    private boolean syncing;

    public UIOutlineColorControl(IntSupplier getSolidColor, Supplier<OutlineGradient> getGradient,
                                 Consumer<Integer> setSolidColor, Consumer<OutlineGradient> setGradient)
    {
        this(getSolidColor, getGradient, setSolidColor, setGradient, null);
    }

    /**
     * @param applyToChildren invoked by the leftmost icon; {@code null} hides that icon (the
     *                        keyframe editor has no children to apply to)
     */
    public UIOutlineColorControl(IntSupplier getSolidColor, Supplier<OutlineGradient> getGradient,
                                 Consumer<Integer> setSolidColor, Consumer<OutlineGradient> setGradient,
                                 Runnable applyToChildren)
    {
        this.getSolidColor = getSolidColor;
        this.getGradient = getGradient;
        this.setSolidColor = setSolidColor;
        this.setGradient = setGradient;
        this.applyToChildren = applyToChildren;

        this.h(UIConstants.CONTROL_HEIGHT).row(2);

        this.apply = new UIIcon(Icons.DOWNLOAD, (button) ->
        {
            if (this.applyToChildren != null)
            {
                this.applyToChildren.run();
            }
        });
        this.apply.w(ICON_WIDTH).h(UIConstants.CONTROL_HEIGHT);
        this.apply.tooltip(L10n.lang("bbs.ui.model_editor.outline.apply_children"));

        this.mode = new UIIcon((Supplier<Icon>) this::modeIcon, (button) -> this.toggleMode());
        this.mode.w(ICON_WIDTH).h(UIConstants.CONTROL_HEIGHT);
        this.mode.tooltip(L10n.lang("bbs.ui.model_editor.outline.gradient.mode"));

        this.solid = new SolidSwatch();
        this.solid.withAlpha().direction(Direction.LEFT).h(UIConstants.CONTROL_HEIGHT);

        this.direction = new DirectionButton();

        this.slot = new Slot();
        this.slot.add(this.solid, this.direction);

        this.add(this.apply, this.mode, this.slot);
        this.apply.setVisible(this.applyToChildren != null);

        this.refresh();
    }

    private Icon modeIcon()
    {
        return this.getGradient.get() == null ? Icons.COLOR : Icons.FADING;
    }

    /** The swatch inside this control; callers use it for the existing "apply to children" hooks. */
    public UIColor getSolidColor()
    {
        return this.solid;
    }

    private Icon directionIcon()
    {
        OutlineGradient gradient = this.getGradient.get();

        if (gradient != null)
        {
            this.lastAxis = gradient.getAxis();
        }

        return this.lastAxis == OutlineGradient.Axis.VERTICAL ? Icons.VERTICAL : Icons.HORIZONTAL;
    }

    /** Shows the right slot child and re-reads the colour. Never fires a write. */
    public void refresh()
    {
        OutlineGradient gradient = this.getGradient.get();

        if (gradient != null)
        {
            this.lastAxis = gradient.getAxis();
        }

        this.syncing = true;
        this.solid.setColor(this.getSolidColor.getAsInt());
        this.syncing = false;

        boolean shown = gradient != null;

        if (shown != this.gradientShown)
        {
            this.gradientShown = shown;

            if (shown)
            {
                this.direction.tooltip(L10n.lang("bbs.ui.model_editor.outline.gradient.direction"));
            }
            else
            {
                this.direction.removeTooltip();

                if (this.solid.picker.hasParent())
                {
                    this.solid.picker.removeFromParent();
                }
            }
        }
    }

    private void toggleMode()
    {
        OutlineGradient gradient = this.getGradient.get();

        if (gradient == null)
        {
            this.setGradient.accept(OutlineGradient.fromSolid(this.lastAxis, this.getSolidColor.getAsInt()));
        }
        else
        {
            this.setGradient.accept(null);
        }

        this.refresh();
    }

    private void flipAxis()
    {
        OutlineGradient gradient = this.getGradient.get();

        if (gradient == null)
        {
            return;
        }

        OutlineGradient flipped = gradient.copy();

        flipped.setAxis(flipped.getAxis().flipped());
        this.setGradient.accept(flipped);
        this.refresh();
    }

    /** A fixed-size box that hosts both slot children and gives them the exact same area. */
    private final class Slot extends UIElement
    {
        @Override
        public void resize()
        {
            super.resize();

            solid.area.set(this.area.x, this.area.y, this.area.w, this.area.h);
            direction.area.set(this.area.x, this.area.y, this.area.w, this.area.h);
        }
    }

    /** The colour swatch. Mounted permanently; only draws and accepts clicks in solid mode. */
    private final class SolidSwatch extends UIColor
    {
        SolidSwatch()
        {
            super((argb) ->
            {
                if (!syncing)
                {
                    setSolidColor.accept(argb);
                }
            });
        }

        @Override
        public void render(UIContext context)
        {
            if (!gradientShown)
            {
                super.render(context);
            }
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            return !gradientShown && super.subMouseClicked(context);
        }

        @Override
        protected boolean mouseClickedContextMenu(UIContext context)
        {
            return !gradientShown && super.mouseClickedContextMenu(context);
        }
    }

    /** The gradient direction button. Mounted permanently; only draws and accepts clicks in gradient mode. */
    private final class DirectionButton extends UIIcon
    {
        DirectionButton()
        {
            super((Supplier<Icon>) UIOutlineColorControl.this::directionIcon,
                (button) -> UIOutlineColorControl.this.flipAxis());
        }

        @Override
        protected boolean isAllowed(int mouseButton)
        {
            return gradientShown && super.isAllowed(mouseButton);
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            if (gradientShown)
            {
                super.renderSkin(context);
            }
        }
    }
}
