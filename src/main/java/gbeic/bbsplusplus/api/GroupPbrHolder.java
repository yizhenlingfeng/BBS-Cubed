package gbeic.bbsplusplus.api;

/**
 * 由 {@code ModelGroupMixin} 通过 duck-typing 实现的接口，
 * 暴露渲染期"逐骨骼 PBR 覆盖"的读写入口。
 *
 * <p>该字段是<b>每帧瞬态</b>数据：{@code Model.resetPose()} 每帧渲染前遍历
 * {@code ModelGroup.reset()} 清空之，随后 {@code ModelMixin}（applyPose TAIL）
 * 从 {@code PoseTransform} 的骨骼 PBR（{@link BonePbrHolder}）重新填充。
 * 渲染时 {@code CubicVAORendererMixin} 在 FormPbr.resolveAlbedo 处优先取本覆盖。</p>
 *
 * <p>duck 入口：{@code ((GroupPbrHolder) modelGroup).bbspp_cml$getSmoothness()}。</p>
 */
public interface GroupPbrHolder
{
    float bbspp_cml$getSmoothness();
    void bbspp_cml$setSmoothness(float value);

    float bbspp_cml$getMetallic();
    void bbspp_cml$setMetallic(float value);

    float bbspp_cml$getSss();
    void bbspp_cml$setSss(float value);

    float bbspp_cml$getEmission();
    void bbspp_cml$setEmission(float value);

    float bbspp_cml$getRelief();
    void bbspp_cml$setRelief(float value);
}
