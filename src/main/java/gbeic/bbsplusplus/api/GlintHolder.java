package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.utils.colors.Color;

/**
 * 由 {@code PoseTransformMixin} 通过 duck-typing 实现的接口，
 * 暴露运行期"骨骼附魔光效"的读写入口。
 *
 * <p>glint 经 PoseTransformMixin 的 toData/fromData 钩子随 Pose 持久化，
 * copy/equals/identity/lerp/autoLerp/add 钩子确保光效在 Pose 拷贝、
 * 关键帧插值与叠加过程中语义正确（布尔量不可插值，取最近关键帧）。
 * 渲染端经 ModelMixin → GroupGlintHolder → CubicVAORendererMixin 消费。</p>
 *
 * <p>duck 入口：{@code ((GlintHolder) poseTransform).bbspp_cml$getGlint()}。</p>
 */
public interface GlintHolder
{
    boolean bbspp_cml$getGlint();

    void bbspp_cml$setGlint(boolean glint);

    Color bbspp_cml$getGlintColor();

    void bbspp_cml$setGlintColor(Color color);
}
