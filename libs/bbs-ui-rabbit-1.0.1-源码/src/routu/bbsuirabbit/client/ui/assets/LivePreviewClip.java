package routu.bbsuirabbit.client.ui.assets;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;

/**
 * Runs a 3D live preview inside a possibly scrolled cell. UIContext shifts (UIScrollView
 * calls shiftY(scroll)) move the 2D matrix AND the ViewportStack, but UIModelRenderer
 * feeds the raw area to RenderSystem.viewport — inside a shifted context the viewport
 * lands off by the scroll amount and the preview gets clipped away.
 *
 * So: unshift, work in global coords, restore the exact shift afterwards. If the unshift
 * doesn't fully neutralise the context, bail out (false = draw the static sprite).
 */
public final class LivePreviewClip
{
    private LivePreviewClip()
    {}

    /**
     * Sets {@code area} to the cell grown by 14px (hides the particle debug readout) in
     * the space that survives the GL viewport, clips to the real cell, runs the render.
     * Returns false when the cell could not be un-shifted.
     */
    public static boolean render(UIContext context, Area area, int x, int y, int w, int h, Runnable render)
    {
        int ox = context.globalX(0);
        int oy = context.globalY(0);

        if (ox == 0 && oy == 0)
        {
            area.set(x, y - 14, w, h + 28);
            context.batcher.clip(x, y, w, h, context);

            try
            {
                render.run();
            }
            finally
            {
                context.batcher.unclip(context);
            }

            return true;
        }

        /* UIViewportStack.shiftX(n) is "shift += n" and globalX(0) == -shift == ox, so
         * shifting by +ox brings the accumulated shift back to zero */
        context.shiftX(ox);
        context.shiftY(oy);

        if (context.globalX(0) != 0 || context.globalY(0) != 0)
        {
            context.shiftX(-ox);
            context.shiftY(-oy);

            return false;
        }

        int gx = x + ox;
        int gy = y + oy;

        area.set(gx, gy - 14, w, h + 28);
        context.batcher.clip(gx, gy, w, h, context);

        try
        {
            render.run();
        }
        finally
        {
            context.batcher.unclip(context);
            context.shiftX(-ox);
            context.shiftY(-oy);
        }

        return true;
    }
}
