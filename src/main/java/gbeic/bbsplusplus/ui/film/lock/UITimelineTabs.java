package gbeic.bbsplusplus.ui.film.lock;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 20px-wide vertical tab rail placed on the left edge of the film editor's
 * {@code main} dock panel. The three timeline pages (camera / replay / POV) are
 * stacked inside that same panel and switched through this rail, mirroring the
 * collapsed dock tab strip from the vanilla editor.
 */
public class UITimelineTabs extends UIElement
{
    public static final int WIDTH = 20;

    private final UITabStrip strip;
    private IntConsumer selectHandler;

    public UITimelineTabs()
    {
        this.strip = new UITabStrip(ScrollDirection.VERTICAL);
        this.strip.full(this);
        this.strip.direction(ScrollDirection.VERTICAL);
        this.strip.fixed();
        this.strip.activeEdge(Direction.RIGHT);
        this.strip.background(() -> 0x66000000);
        this.strip.onSelect((index) ->
        {
            if (this.selectHandler != null)
            {
                this.selectHandler.accept(index);
            }
        });

        this.add(this.strip);
    }

    public UIIcon addTab(Icon icon, IKey label)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.strip.select(b));

        button.wh(WIDTH, WIDTH);
        button.tooltip(label, Direction.RIGHT);
        this.strip.addTab(button);

        return button;
    }

    public void clear()
    {
        this.strip.removeAll();
    }

    public void setSelectHandler(IntConsumer handler)
    {
        this.selectHandler = handler;
    }

    public void setActiveSupplier(IntSupplier supplier)
    {
        this.strip.active(supplier);
    }
}
