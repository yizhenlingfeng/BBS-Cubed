package gbeic.bbsplusplus.client.ui.outline;

import gbeic.bbsplusplus.api.OutlineGradient;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The gradient strip itself: draws the ramp, the colour stops and handles dragging of a stop.
 *
 * <p>This is the outline counterpart of the particle editor's {@code UIGradientEditor}. The
 * particle one is built on {@code Gradient}/{@code Solid}/{@code MolangValue}, so it can't be
 * reused directly; this one edits a plain {@link OutlineGradient} (0..1 positions, packed ARGB
 * colours) in place and reports content changes through {@link #setOnChange(Runnable)}.</p>
 *
 * <p>Only the strip is drawn here. The selected stop's colour swatch lives in
 * {@link UIOutlineGradientEditor}, which also pushes the mutated gradient back into the model.</p>
 */
public class UIOutlineGradientBar extends UIElement
{
    /** Area used for the ramp, one pixel inside the element so the handle border stays visible. */
    private final Area inner = new Area();

    /** Scratch area for stop handles, reused every frame to avoid allocations. */
    private final Area bound = new Area();

    private OutlineGradient gradient;

    /** Index into the stop list; kept in sync with the dragged stop while it is sorted. */
    private int selected;

    /** -1 = idle, 0 = clicked (waiting for the drag threshold), 1 = dragging. */
    private int dragging = -1;

    private int lastX;

    private Runnable onChange;
    private Runnable onSelect;

    public UIOutlineGradientBar()
    {
        this.h(UIConstants.CONTROL_HEIGHT);

        this.context((menu) ->
        {
            menu.icon(MenuVerb.ADD, this::addStop)
                .label(UIKeys.SNOWSTORM_LIGHTING_CONTEXT_ADD_STOP);
            menu.icon(MenuVerb.REMOVE, this::removeStop)
                .label(UIKeys.SNOWSTORM_LIGHTING_CONTEXT_REMOVE_STOP)
                .enabled(this.gradient != null && this.gradient.size() > 1);
        });
    }

    public void setOnChange(Runnable onChange)
    {
        this.onChange = onChange;
    }

    public void setOnSelect(Runnable onSelect)
    {
        this.onSelect = onSelect;
    }

    public OutlineGradient getGradient()
    {
        return this.gradient;
    }

    /** True while a stop is being dragged; a sync pass must not overwrite the working copy. */
    public boolean isDragging()
    {
        return this.dragging != -1;
    }

    /** Replaces the edited ramp. Equal content is ignored so a sync pass can't reset the drag. */
    public void setGradient(OutlineGradient value)
    {
        if (value == null)
        {
            this.gradient = null;
            this.selected = 0;

            return;
        }

        if (OutlineGradient.equal(this.gradient, value))
        {
            return;
        }

        this.gradient = value.copy();
        this.selected = MathUtils.clamp(this.selected, 0, this.gradient.size() - 1);

        this.select();
    }

    public int getSelectedColor()
    {
        if (this.gradient == null || this.gradient.isEmpty())
        {
            return 0xFFFFFFFF;
        }

        int index = MathUtils.clamp(this.selected, 0, this.gradient.size() - 1);

        return this.gradient.getStops().get(index).argb;
    }

    /** Called by the colour swatch next to the bar. */
    public void setSelectedColor(int argb)
    {
        if (this.gradient == null || this.gradient.isEmpty())
        {
            return;
        }

        int index = MathUtils.clamp(this.selected, 0, this.gradient.size() - 1);

        this.gradient.getStops().get(index).argb = argb;

        this.changed();
    }

    private void select()
    {
        if (this.onSelect != null)
        {
            this.onSelect.run();
        }
    }

    private void changed()
    {
        this.select();

        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    private int stopX(int index)
    {
        OutlineGradient.Stop stop = this.gradient.getStops().get(index);

        return this.inner.x + (int) (this.inner.w * MathUtils.clamp(stop.pos, 0F, 1F));
    }

    private Area fillBound(int index)
    {
        int x = this.stopX(index);

        this.bound.set(x - 3, this.inner.ey() - 7, 6, 10);

        return this.bound;
    }

    private void addStop()
    {
        if (this.gradient == null)
        {
            return;
        }

        float x = (this.getContext().mouseX - this.inner.x) / (float) Math.max(this.inner.w, 1);

        OutlineGradient.Stop stop = this.gradient.addStop(MathUtils.clamp(x, 0F, 1F), 0xFFFFFFFF);

        this.gradient.sort();
        this.selected = this.gradient.getStops().indexOf(stop);

        this.changed();
    }

    private void removeStop()
    {
        if (this.gradient == null || this.gradient.size() <= 1)
        {
            return;
        }

        int index = MathUtils.clamp(this.selected, 0, this.gradient.size() - 1);
        OutlineGradient.Stop stop = this.gradient.getStops().get(index);

        if (!this.gradient.removeStop(stop))
        {
            return;
        }

        this.selected = MathUtils.clamp(index, 0, this.gradient.size() - 1);

        this.changed();
    }

    @Override
    public void resize()
    {
        super.resize();

        this.inner.copy(this.area);
        this.inner.offset(-1);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.gradient != null && this.area.isInside(context))
        {
            for (int i = 0; i < this.gradient.size(); i++)
            {
                if (this.fillBound(i).isInside(context))
                {
                    this.selected = i;
                    this.dragging = 0;
                    this.lastX = context.mouseX;

                    this.select();

                    return true;
                }
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragging != -1)
        {
            this.dragging = -1;

            this.changed();
        }

        return super.subMouseReleased(context);
    }

    private void updateDrag(UIContext context)
    {
        if (this.dragging == 0 && Math.abs(context.mouseX - this.lastX) > 3)
        {
            this.dragging = 1;
        }
        else if (this.dragging == 1 && this.gradient != null && !this.gradient.isEmpty())
        {
            int index = MathUtils.clamp(this.selected, 0, this.gradient.size() - 1);
            OutlineGradient.Stop stop = this.gradient.getStops().get(index);
            float x = (context.mouseX - this.inner.x) / (float) Math.max(this.inner.w, 1);

            stop.pos = MathUtils.clamp(x, 0F, 1F);
            this.gradient.sort();

            /* Sorting can move the dragged stop between two others; follow the object. */
            this.selected = this.gradient.getStops().indexOf(stop);
        }
    }

    @Override
    public void render(UIContext context)
    {
        this.updateDrag(context);

        if (this.gradient == null || this.gradient.isEmpty())
        {
            return;
        }

        this.area.render(context.batcher, Colors.A100);
        context.batcher.iconArea(Icons.CHECKBOARD, this.inner.x, this.inner.y, this.inner.w, this.inner.h);

        int size = this.gradient.size();
        OutlineGradient.Stop first = this.gradient.getStops().get(0);

        if (first.pos > 0F)
        {
            int x1 = this.stopX(0);

            context.batcher.box(this.inner.x, this.inner.y, x1, this.inner.ey(), first.argb);
        }

        for (int i = 0; i < size; i++)
        {
            OutlineGradient.Stop stop = this.gradient.getStops().get(i);
            OutlineGradient.Stop next = i + 1 < size ? this.gradient.getStops().get(i + 1) : stop;

            int x1 = this.stopX(i);
            int x2 = i + 1 < size ? this.stopX(i + 1) : this.inner.ex();

            context.batcher.gradientHBox(x1, this.inner.y, x2, this.inner.ey(), stop.argb, next.argb);
        }

        for (int i = 0; i < size; i++)
        {
            OutlineGradient.Stop stop = this.gradient.getStops().get(i);
            Area area = this.fillBound(i);

            context.batcher.box(area.x, area.y, area.ex(), area.ey(), i == this.selected ? Colors.WHITE : Colors.A100);
            context.batcher.box(area.x + 1, area.y + 1, area.ex() - 1, area.ey() - 1, stop.argb);
        }

        super.render(context);
    }
}
