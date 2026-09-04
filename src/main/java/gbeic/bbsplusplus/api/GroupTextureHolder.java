package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.resources.Link;

/**
 * 由 {@code ModelGroupMixin} 通过 duck-typing 实现的接口，
 * 暴露渲染期"骨骼纹理覆盖"的读写入口。
 *
 * <p>该字段是<b>每帧瞬态</b>数据：{@code Model.resetPose()} 每帧渲染前遍历
 * {@code ModelGroup.reset()} 清空之，随后 {@code Model.applyPose}（经 ModelMixin TAIL）
 * 从 {@code PoseTransform} 的骨骼纹理（{@link BoneTextureHolder}）重新填充。
 * 渲染时 {@code CubicVAORendererMixin} 在材质纹理解析处优先取本覆盖。</p>
 *
 * <p>duck 入口：{@code ((GroupTextureHolder) modelGroup).bbspp_cml$getTextureOverride()}。</p>
 */
public interface GroupTextureHolder
{
    Link bbspp_cml$getTextureOverride();

    void bbspp_cml$setTextureOverride(Link texture);
}
