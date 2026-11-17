package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphKeyframeTargets;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UIModelMorphTargetTraversalTest
{
    @Test
    void sameModelAtNextTickIsKeptAsTheMorphTarget()
    {
        List<Keyframe<?>> keys = List.of(
            key(0F, "model/bed"),
            key(20F, "model/bed"),
            key(40F, "model/boat"));

        assertEquals("model/bed", ModelMorphKeyframeTargets.nextModelId(keys, 0, 0F));
    }

    @Test
    void sameTickAndEmptyValuesAreSkippedButSameModelIsNot()
    {
        List<Keyframe<?>> keys = List.of(
            key(0F, "model/bed"),
            key(0F, "model/coincident"),
            key(10F, ""),
            key(20F, "model/bed"),
            key(30F, "model/boat"));

        assertEquals("model/bed", ModelMorphKeyframeTargets.nextModelId(keys, 0, 0F));
    }

    private static Keyframe<String> key(float tick, String model)
    {
        return new Keyframe<>("", null, tick, model);
    }
}
