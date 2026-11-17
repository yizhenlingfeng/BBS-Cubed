package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;

/** Resolves the Model track endpoint used by one Morph keyframe interval. */
public final class ModelMorphKeyframeTargets
{
    private ModelMorphKeyframeTargets()
    {
    }

    /**
     * Find the next non-empty Model key at a later tick.
     * The model ID may equal the current key: that represents a valid same-model cube remap.
     */
    public static String nextModelId(Keyframe<?> keyframe)
    {
        if (keyframe == null || !(keyframe.getParent() instanceof KeyframeChannel<?> channel))
        {
            return "";
        }

        List<? extends Keyframe<?>> keyframes = channel.getKeyframes();

        return nextModelId(keyframes, keyframes.indexOf(keyframe), keyframe.getTick());
    }

    /** Pure traversal rule exposed for regression tests. */
    public static String nextModelId(List<? extends Keyframe<?>> keyframes, int currentIndex, float currentTick)
    {
        if (keyframes == null)
        {
            return "";
        }

        for (int i = Math.max(0, currentIndex + 1); i < keyframes.size(); i++)
        {
            Keyframe<?> next = keyframes.get(i);

            if (next.getTick() <= currentTick)
            {
                continue;
            }

            Object value = next.getValue();

            if (value instanceof String modelId && !modelId.isEmpty())
            {
                return modelId;
            }
        }

        return "";
    }
}
