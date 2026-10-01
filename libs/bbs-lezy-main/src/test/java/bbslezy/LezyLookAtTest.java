package bbslezy;

import bbslezy.ui.LezyLookAt;
import bbslezy.ui.LezyUndoHelper;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LezyLookAtTest
{
    @BeforeEach
    @AfterEach
    public void resetLock()
    {
        LezyUndoHelper.setBatchLock(false);
    }

    @Test
    public void testUndoBatchLockState()
    {
        assertFalse(LezyUndoHelper.isBatchLocked());

        LezyUndoHelper.setBatchLock(true);
        assertTrue(LezyUndoHelper.isBatchLocked());

        LezyUndoHelper.setBatchLock(false);
        assertFalse(LezyUndoHelper.isBatchLocked());
    }

    @Test
    public void testAngleUnwrapping()
    {
        ReplayKeyframes rk = new ReplayKeyframes("test");
        KeyframeChannel<Double> channel = rk.yaw;
        channel.insert(0F, 175D);

        /* Moving from 175° to -175° across the 180° boundary should unwrap to 185°, not take the long 350° way around */
        double unwrapped = LezyLookAt.unwrap(channel, 10F, -175D, false);
        assertEquals(185D, unwrapped, 0.001);

        /* Pitch should not be unwrapped */
        double pitchUnwrapped = LezyLookAt.unwrap(channel, 10F, -175D, true);
        assertEquals(-175D, pitchUnwrapped, 0.001);
    }

    @Test
    public void testAngleUnwrappingNegativeBoundary()
    {
        ReplayKeyframes rk = new ReplayKeyframes("test");
        KeyframeChannel<Double> channel = rk.yaw;
        channel.insert(0F, -170D);

        /* Moving from -170° to 175° should unwrap to -185° */
        double unwrapped = LezyLookAt.unwrap(channel, 10F, 175D, false);
        assertEquals(-185D, unwrapped, 0.001);
    }

    @Test
    public void testComputeAngles()
    {
        ReplayKeyframes src = new ReplayKeyframes("src");
        src.x.insert(0F, 0D);
        src.y.insert(0F, 0D);
        src.z.insert(0F, 0D);

        ReplayKeyframes target = new ReplayKeyframes("target");
        /* Target directly north (-Z in MC coords) */
        target.x.insert(0F, 0D);
        target.y.insert(0F, 0D);
        target.z.insert(0F, -10D);

        double yawNorth = LezyLookAt.compute(0F, src, target, false);
        assertEquals(180D, Math.abs(yawNorth), 0.001);

        /* Target directly south (+Z) */
        target.z.insert(0F, 10D);
        double yawSouth = LezyLookAt.compute(0F, src, target, false);
        assertEquals(0D, yawSouth, 0.001);

        /* Target directly up (+Y): pitch in Minecraft is negative when looking up */
        target.z.insert(0F, 0D);
        target.y.insert(0F, 10D);
        double pitchUp = LezyLookAt.compute(0F, src, target, true);
        assertEquals(-90D, pitchUp, 0.001);

        /* Target directly down (-Y): pitch is positive when looking down */
        target.y.insert(0F, -10D);
        double pitchDown = LezyLookAt.compute(0F, src, target, true);
        assertEquals(90D, pitchDown, 0.001);
    }
}
