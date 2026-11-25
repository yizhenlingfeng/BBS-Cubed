package gbeic.bbsplusplus.client.outline.geometry;

import java.util.Arrays;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Screen frame of the current render pass (camera up/right in model space) and the hash of the
 * frame the cached shells were baked with, extracted from {@link OutlineGeometry}.
 */
public final class OutlineScreenFrame
{
    /*
     * Screen frame of the current render pass, in the same space the shell vertices are emitted in:
     * { upX, upY, upZ, rightX, rightY, rightZ }. Layer ramps run along these instead of the model
     * axes, so the gradient follows the camera; null falls back to the model-space axes.
     */
    private static float[] screenFrame;
    private static long screenFrameHash;

    /** Hash of the frame the cached shells were baked with. */
    public static long screenFrameHash()
    {
        return screenFrameHash;
    }

    /** Records where the camera's screen up/right point in model space. */
    public static void updateScreenFrame(Matrix4f view, Matrix4f base)
    {
        Matrix3f rotation = new Matrix3f(new Matrix4f(view)).mul(new Matrix3f(base)).invert();
        Vector3f up = rotation.transform(new Vector3f(0F, 1F, 0F));
        Vector3f right = rotation.transform(new Vector3f(1F, 0F, 0F));

        up.normalize();
        right.normalize();

        float[] frame = { quantize(up.x), quantize(up.y), quantize(up.z),
            quantize(right.x), quantize(right.y), quantize(right.z) };

        if (Arrays.equals(screenFrame, frame))
        {
            return;
        }

        screenFrame = frame;
        screenFrameHash = hashFrame(frame);
    }

    private static float quantize(float value)
    {
        return Math.round(value * 128F) / 128F;
    }

    private static long hashFrame(float[] frame)
    {
        long hash = 17L;

        for (float value : frame)
        {
            hash = hash * 31L + Float.floatToIntBits(value);
        }

        return hash;
    }
    /** Current screen frame as {upX, upY, upZ, rightX, rightY, rightZ}, or null. */
    static float[] screenFrame()
    {
        return screenFrame;
    }
    private OutlineScreenFrame()
    {
    }
}
