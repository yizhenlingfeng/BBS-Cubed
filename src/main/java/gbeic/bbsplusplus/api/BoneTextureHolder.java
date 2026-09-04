package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.resources.Link;

/**
 * 由 {@code PoseTransformMixin} 通过 duck-typing 实现的接口，
 * 暴露运行期"骨骼纹理"链接的读写入口。
 *
 * <p>texture 经 PoseTransformMixin 的 toData/fromData 钩子随 Pose 持久化，
 * copy 钩子确保 Pose 拷贝（含 ModelFormRenderer.getPose 的每帧深拷贝）过程中纹理不丢失。
 * 渲染端经 ModelMixin → GroupTextureHolder → CubicVAORendererMixin 消费。</p>
 *
 * <p>duck 入口：{@code ((BoneTextureHolder) poseTransform).bbspp_cml$getTexture()}。</p>
 */
public interface BoneTextureHolder
{
    Link bbspp_cml$getTexture();

    void bbspp_cml$setTexture(Link texture);
}
