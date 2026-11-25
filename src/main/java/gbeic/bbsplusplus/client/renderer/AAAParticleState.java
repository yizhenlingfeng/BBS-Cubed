package gbeic.bbsplusplus.client.renderer;

import net.minecraft.util.Identifier;

/**
 * Per-tick playback state of an AAA particle effect, used to detect seeking and
 * hard jumps in the timeline so the emitter can be restored.
 */
final class AAAParticleState
{
    boolean wasPlaying;
    Identifier effectId;

    AAAParticleState(boolean wasPlaying, Identifier effectId)
    {
        this.wasPlaying = wasPlaying;
        this.effectId = effectId;
    }
}
