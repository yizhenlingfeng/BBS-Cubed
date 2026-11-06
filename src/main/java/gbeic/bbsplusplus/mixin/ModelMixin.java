package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.api.GroupOutlineHolder;
import gbeic.bbsplusplus.api.GroupPbrHolder;
import gbeic.bbsplusplus.api.GroupTextureHolder;
import gbeic.bbsplusplus.api.PivotHolder;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 在 {@link Model#applyPose(Pose)} 末尾把姿势上的逐骨骼设置同步到 {@link ModelGroup}：
 * 纹理、附魔光效、PBR、描边与中心点。{@link ModelGroup#reset()} 会在每帧应用姿势前
 * 清空这些渲染期字段，避免共享 {@link Model} 实例在多个 form 之间串数据。
 */
@Mixin(value = Model.class, remap = false)
public abstract class ModelMixin
{
    @Shadow
    public abstract ModelGroup getGroup(String id);

    @Inject(method = "applyPose(Lmchorse/bbs_mod/utils/pose/Pose;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$applyBoneExtras(Pose pose, CallbackInfo ci)
    {
        if (pose.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform transform = entry.getValue();
            Link texture = ((BoneTextureHolder) transform).bbspp_cml$getTexture();
            Vector3f pivot = ((PivotHolder) transform).bbspp_cml$getPivot();
            boolean glint = ((GlintHolder) transform).bbspp_cml$getGlint();
            BoneOutlineHolder boneOutline = (BoneOutlineHolder) transform;
            boolean outline = boneOutline.bbspp_cml$hasOutlineLayer(0)
                || boneOutline.bbspp_cml$hasOutlineLayer(1);
            boolean pivoted = pivot.x != 0F || pivot.y != 0F || pivot.z != 0F;

            /* PBR：任一值 > 0 即认为该骨骼有 PBR 覆盖。 */
            BonePbrHolder pbr = (BonePbrHolder) transform;
            boolean hasPbr = pbr.bbspp_cml$getSmoothness() > 0F || pbr.bbspp_cml$getMetallic() > 0F
                || pbr.bbspp_cml$getSss() > 0F || pbr.bbspp_cml$getEmission() > 0F
                || pbr.bbspp_cml$getRelief() > 0F;

            /* glint / outline 必须计入这个提前跳过判断，否则"只开光效/描边"的骨骼会被整段忽略。 */
            if (texture == null && !pivoted && !glint && !hasPbr && !outline)
            {
                continue;
            }

            ModelGroup group = this.getGroup(entry.getKey());

            if (group == null)
            {
                continue;
            }

            if (texture != null)
            {
                ((GroupTextureHolder) group).bbspp_cml$setTextureOverride(texture);
            }

            /* 只置 true 不置 false：false 由每帧的 ModelGroup.reset() 负责，
             * 这样共享 Model 实例不会被别的 form 的旧状态污染。 */
            if (glint)
            {
                GroupGlintHolder groupGlint = (GroupGlintHolder) group;

                groupGlint.bbspp_cml$setGlint(true);
                groupGlint.bbspp_cml$setGlintColor(((GlintHolder) transform).bbspp_cml$getGlintColor());

                GlintSettingsHolder tuning = (GlintSettingsHolder) transform;
                GlintSettingsHolder groupTuning = (GlintSettingsHolder) group;

                groupTuning.bbspp_cml$setGlintSpeed(tuning.bbspp_cml$getGlintSpeed());
                groupTuning.bbspp_cml$setGlintBrightness(tuning.bbspp_cml$getGlintBrightness());
            }

            /* PBR：只在有值时写入（全 0 由 reset() 负责清空）。 */
            if (hasPbr)
            {
                GroupPbrHolder groupPbr = (GroupPbrHolder) group;

                groupPbr.bbspp_cml$setSmoothness(pbr.bbspp_cml$getSmoothness());
                groupPbr.bbspp_cml$setMetallic(pbr.bbspp_cml$getMetallic());
                groupPbr.bbspp_cml$setSss(pbr.bbspp_cml$getSss());
                groupPbr.bbspp_cml$setEmission(pbr.bbspp_cml$getEmission());
                groupPbr.bbspp_cml$setRelief(pbr.bbspp_cml$getRelief());
            }

            /* 描边：两层各自独立，只写入开着的那层（关闭由 reset() 负责）。
             * 逐面颜色表是整表拷贝：geometry 层按面索引取色，sentinel 表示跟随该层主色。 */
            if (outline)
            {
                GroupOutlineHolder groupOutline = (GroupOutlineHolder) group;

                for (int layer = 0; layer < 2; layer++)
                {
                    if (!boneOutline.bbspp_cml$hasOutlineLayer(layer))
                    {
                        continue;
                    }

                    groupOutline.bbspp_cml$setOutlineLayer(layer, true);
                    groupOutline.bbspp_cml$setOutlineColor(layer, boneOutline.bbspp_cml$getOutlineColor(layer));
                    groupOutline.bbspp_cml$setOutlineWidth(layer, boneOutline.bbspp_cml$getOutlineWidth(layer));
                    groupOutline.bbspp_cml$setOutlineGlow(layer, boneOutline.bbspp_cml$getOutlineGlow(layer));
                    groupOutline.bbspp_cml$setOutlineVanillaGlow(layer, boneOutline.bbspp_cml$getOutlineVanillaGlow(layer));
                    groupOutline.bbspp_cml$setOutlineFaceCull(layer, boneOutline.bbspp_cml$getOutlineFaceCull(layer));

                    for (int face = 0; face < BoneOutlineHolder.FACE_COUNT; face++)
                    {
                        groupOutline.bbspp_cml$setOutlineFaceColor(layer, face,
                            boneOutline.bbspp_cml$getOutlineFaceColor(layer, face));
                    }
                }

                groupOutline.bbspp_cml$setOutlineDecalSize(boneOutline.bbspp_cml$getOutlineDecalSize());
            }

            if (pivoted)
            {
                /* 只累计进 group.current 的 pivot 字段（渲染期由 ICubicRendererMixin
                 * 在 moveTo/moveBackFromGroupPivot 消费，零旋转缩放时无位移）。
                 * 注意不能照抄 CML 的 translate.add(pivot) —— 那是与 CML 版
                 * translateGroup 公式(用 current.pivot 参与位移)配套的抵消项，
                 * FS 版公式用常量 initial.translate，照抄会变成纯平移。 */
                ((PivotHolder) group.current).bbspp_cml$getPivot().add(pivot);
            }
        }
    }
}
