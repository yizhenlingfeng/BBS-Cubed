package gbeic.bbsplusplus.util;

import gbeic.bbsplusplus.api.IBbsppAnimation;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class AnimationBakeSnapshot
{
    private final MapType cubicData;
    private final List<BOBJBoneData> bobjBones;

    private AnimationBakeSnapshot(MapType cubicData, List<BOBJBoneData> bobjBones)
    {
        this.cubicData = cubicData;
        this.bobjBones = bobjBones;
    }

    public static AnimationBakeSnapshot capture(IModel model)
    {
        if (model instanceof Model cubic)
        {
            MapType data = new MapType(false);

            cubic.toData(data);

            return new AnimationBakeSnapshot(data, null);
        }

        if (model instanceof BOBJModel bobj)
        {
            List<BOBJBoneData> bones = new ArrayList<>();

            for (BOBJBone bone : bobj.getArmature().orderedBones)
            {
                bones.add(new BOBJBoneData(bone));
            }

            return new AnimationBakeSnapshot(null, bones);
        }

        return null;
    }

    public IModel createModel(MolangParser parser)
    {
        MolangHelper.registerVars(parser);

        if (this.cubicData != null)
        {
            Model model = new Model(parser);

            model.reload(this.cubicData);

            return model;
        }

        BOBJArmature armature = new BOBJArmature("bbspp-animation-bake");

        for (BOBJBoneData data : this.bobjBones)
        {
            armature.addBone(data.create());
        }

        armature.initArmature();

        return new BOBJModel(armature, Collections.emptyList(), true);
    }

    public static Animation copyAnimation(Animation source, MolangParser parser) throws Exception
    {
        Animation copy = new Animation(source.id, parser);

        copy.setLength(source.getLength());

        if (source instanceof IBbsppAnimation sourceExtension && copy instanceof IBbsppAnimation copyExtension)
        {
            copyExtension.bbspp$setLoop(sourceExtension.bbspp$isLoop());
        }

        for (Map.Entry<String, AnimationPart> entry : source.parts.entrySet())
        {
            AnimationPart sourcePart = entry.getValue();
            AnimationPart copyPart = new AnimationPart(parser);

            for (int i = 0; i < sourcePart.channels.size(); i++)
            {
                KeyframeChannel<MolangExpression> sourceChannel = sourcePart.channels.get(i);
                KeyframeChannel<MolangExpression> copyChannel = copyPart.channels.get(i);

                for (Keyframe<MolangExpression> keyframe : sourceChannel.getKeyframes())
                {
                    MolangExpression expression = parser.parseData(keyframe.getValue().toData());
                    int index = copyChannel.insert(keyframe.getTick(), expression);

                    copyChannel.get(index).copyOverExtra(keyframe);
                }
            }

            copy.parts.put(entry.getKey(), copyPart);
        }

        return copy;
    }

    private static final class BOBJBoneData
    {
        private final int index;
        private final String name;
        private final String parent;
        private final Matrix4f matrix;

        private BOBJBoneData(BOBJBone bone)
        {
            this.index = bone.index;
            this.name = bone.name;
            this.parent = bone.parent;
            this.matrix = new Matrix4f(bone.boneMat);
        }

        private BOBJBone create()
        {
            return new BOBJBone(this.index, this.name, this.parent, new Matrix4f(this.matrix));
        }
    }
}
