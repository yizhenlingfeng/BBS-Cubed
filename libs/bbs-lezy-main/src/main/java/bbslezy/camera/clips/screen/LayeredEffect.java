package bbslezy.camera.clips.screen;

/**
 * An overlay a camera clip left behind for the end-of-frame pass, tagged with the timeline layer
 * it came from.
 *
 * <p>The layer is what makes a stack of effects mean something. Two Color Grade clips are summed
 * into one grade, which is not what a timeline reads like: the one on the lower track is supposed
 * to be the look of the footage underneath, and the one above it a second pass over that. Ordering
 * the overlays by layer and giving each layer its own pass is what tells those two apart.</p>
 */
public interface LayeredEffect
{
    /** The timeline track this overlay's clip sits on. Lower layers composite first. */
    int layer();

    /** The order the clip was applied in, which is what breaks ties inside one layer. */
    int renderOrder();
}
