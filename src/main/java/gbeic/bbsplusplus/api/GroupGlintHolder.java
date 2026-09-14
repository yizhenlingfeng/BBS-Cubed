package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.utils.colors.Color;

/**
 * 由 {@code ModelGroupMixin} 通过 duck-typing 实现的接口，
 * 暴露渲染期"模型组附魔光效覆盖"的读写入口。
 *
 * <p>该字段是<b>每帧瞬态</b>数据：{@code Model.resetPose()} 每帧渲染前遍历
 * {@code ModelGroup.reset()} 清空之，随后 {@code ModelMixin}（applyPose TAIL）
 * 从 {@code PoseTransform} 的骨骼光效（{@link GlintHolder}）重新填充。
 * 渲染时 {@code CubicVAORendererMixin} 优先取本覆盖。</p>
 *
 * <p>duck 入口：{@code ((GroupGlintHolder) modelGroup).bbspp_cml$getGlint()}。</p>
 */
public interface GroupGlintHolder
{
    boolean bbspp_cml$getGlint();

    void bbspp_cml$setGlint(boolean glint);

    Color bbspp_cml$getGlintColor();

    void bbspp_cml$setGlintColor(Color color);
}
