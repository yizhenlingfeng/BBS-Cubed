package gbeic.bbsplusplus.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.geo.GeoAnimationParser;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.pose.Pose;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class AnimationBakeSnapshotTest
{
    @Test
    void copiedAnimationHasIndependentMolangVariablesAndKeepsInterpolation() throws Exception
    {
        MolangParser sourceParser = new MolangParser();
        MolangParser copyParser = new MolangParser();
        Animation source = GeoAnimationParser.parse(sourceParser, "test", animation("variable.test"));

        source.parts.get("body").rx.get(0).getInterpolation().setInterp(Interpolations.CONST);

        Animation copy = AnimationBakeSnapshot.copyAnimation(source, copyParser);
        sourceParser.setValue("variable.test", 12);
        copyParser.setValue("variable.test", 34);

        assertNotSame(source.parts.get("body").rx.get(0).getValue(), copy.parts.get("body").rx.get(0).getValue());
        assertEquals(12, source.parts.get("body").rx.get(0).getValue().get());
        assertEquals(34, copy.parts.get("body").rx.get(0).getValue().get());
        assertEquals(Interpolations.CONST, copy.parts.get("body").rx.get(0).getInterpolation().getInterp());
    }

    @Test
    void bobjSnapshotCanApplyCopiedAnimationWithoutMutatingSourceModel() throws Exception
    {
        MolangParser parser = new MolangParser();
        BOBJArmature armature = new BOBJArmature("test");

        armature.addBone(new BOBJBone(0, "body", "", new Matrix4f()));
        armature.initArmature();

        BOBJModel sourceModel = new BOBJModel(armature, Collections.emptyList(), true);
        AnimationBakeSnapshot snapshot = AnimationBakeSnapshot.capture(sourceModel);
        IModel copyModel = snapshot.createModel(parser);
        Animation source = GeoAnimationParser.parse(parser, "test", animation("90"));
        Animation copy = AnimationBakeSnapshot.copyAnimation(source, parser);

        copyModel.apply(null, copy, 0F, 1F, 0F, false);
        Pose pose = copyModel.createPose();

        assertEquals(90F, pose.get("body").rotate.x, 0.0001);
        assertEquals(0F, sourceModel.createPose().get("body").rotate.x);
    }

    private static JsonObject animation(String x)
    {
        return JsonParser.parseString("""
            {
              "animation_length": 1.0,
              "bones": {
                "body": {
                  "rotation": ["%s", 0, 0]
                }
              }
            }
            """.formatted(x)).getAsJsonObject();
    }
}
