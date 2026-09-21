package gbeic.bbsplusplus.api;

/**
 * 由 {@code PoseTransformMixin} 通过 duck-typing 实现的接口，
 * 暴露运行期"逐骨骼 PBR"五个滑条值的读写入口。
 *
 * <p>五个值与本体 {@code FormMaterial} 的 PBR 字段一一对应：
 * smoothness（光泽度）、metallic（金属度）、sss（散射）、
 * pixelEmission（自发光）、relief（凹凸），范围 0~1，全 0 = 无 PBR。</p>
 *
 * <p>经 PoseTransformMixin 的 toData/fromData/copy/equals/identity/lerp/autoLerp/add
 * 钩子随 Pose 持久化与插值。渲染端经 ModelMixin → GroupPbrHolder →
 * CubicVAORendererMixin 消费，覆盖材质级 PBR。</p>
 *
 * <p>duck 入口：{@code ((BonePbrHolder) poseTransform).bbspp_cml$getSmoothness()}。</p>
 */
public interface BonePbrHolder
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
