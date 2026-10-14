package gbeic.bbsplusplus.client.compat;

import mchorse.bbs_mod.film.replays.Replay;

/**
 * Plain holder for the replay selection captured by
 * {@code TogetherClientNetworkReplaySelectionMixin} (Together compat).
 *
 * <p>This class deliberately lives OUTSIDE the {@code gbeic.bbsplusplus.mixin.*}
 * package: classes under a declared mixin package may not be class-loaded
 * directly, and the snapshot type ends up in the constant pool of the
 * transformed target class, so it must be a normal, loadable class.</p>
 */
public final class TogetherReplaySelectionSnapshot
{
    public final String filmId;
    public final Replay reference;
    public final String signature;
    public final int ordinal;
    public final int index;

    public TogetherReplaySelectionSnapshot(String filmId, Replay reference, String signature, int ordinal, int index)
    {
        this.filmId = filmId;
        this.reference = reference;
        this.signature = signature;
        this.ordinal = ordinal;
        this.index = index;
    }
}
