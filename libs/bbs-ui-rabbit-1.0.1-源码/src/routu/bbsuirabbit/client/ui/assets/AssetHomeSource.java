package routu.bbsuirabbit.client.ui.assets;

import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;

/**
 * What one asset workbench home needs from the panel it wraps: the panel itself is the
 * {@link ILandingHost} (titles, recents, picking), the source only says how entries look —
 * an icon, grid cards or list rows, and how to paint a card's thumbnail.
 */
public interface AssetHomeSource
{
    ILandingHost host();

    Icon icon();

    /** true = list rows (audio), false = grid cards with previews. */
    boolean listStyle();

    void renderThumb(UIContext ctx, String id, int x, int y, int w, int h, boolean hovered);

    default void extraMenu(ContextMenuManager menu, String id)
    {}

    /** Path used for the folder tree / breadcrumb; audio strips its {@code <ns>:audio/} prefix. */
    default String displayPath(String id)
    {
        return id;
    }

    /** Ask the source to start lazily preparing an entry's visuals (waveforms). */
    default void requestThumb(String id)
    {}

    /** Per-frame hook (e.g. a shared live preview). */
    default void tick(UIContext context, String hoveredId, int x, int y, int w, int h)
    {}
}
