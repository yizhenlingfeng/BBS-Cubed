package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
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
 * 在 {@link Model#applyPose(Pose)} 末尾补几件 CML 的事：
 * <ul>
 *   <li>骨骼纹理 —— {@code PoseTransform} 上的纹理（{@link BoneTextureHolder}）
 *       传播到对应 {@link ModelGroup} 的渲染期覆盖字段（{@link GroupTextureHolder}）；</li>
 *   <li>附魔光效 —— {@code PoseTransform} 上的光效开关（{@link GlintHolder}）
 *       传播到 {@link ModelGroup}（{@link GroupGlintHolder}）；</li>
 *   <li>中心点 —— pose 的 pivot 加进 {@code group.current}（对齐 CML 的
 *       {@code group.current.translate.add(pivot); group.current.pivot.add(pivot);}，
 *       渲染时经 Transform.setupMatrix 的 ±pivot 平移生效）。</li>
 * </ul>
 * FS 版该方法没有这些逻辑，以 TAIL 二次遍历补齐（骨骼数量级为几十，开销可忽略）。
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
            boolean pivoted = pivot.x != 0F || pivot.y != 0F || pivot.z != 0F;

            /* glint 必须计入这个提前跳过判断，否则"只开光效"的骨骼会被整段忽略。 */
            if (texture == null && !pivoted && !glint)
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
