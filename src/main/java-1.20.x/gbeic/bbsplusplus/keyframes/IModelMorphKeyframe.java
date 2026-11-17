package gbeic.bbsplusplus.keyframes;

import gbeic.bbsplusplus.client.morph.ModelMorphData;

/**
 * 挂在 BBS 原生 Model 关键帧上的 Model Morph 扩展状态。
 *
 * <p>与纹理补间一样，Morph 配置不建立独立轨道，而是作为关键帧自身的扩展字段，
 * 随关键帧一起序列化、复制、粘贴和参与撤销快照比较。</p>
 */
public interface IModelMorphKeyframe
{
    ModelMorphData bbsppp$getModelMorph();

    void bbsppp$setModelMorph(ModelMorphData data);

    default boolean bbsppp$isModelMorphEnabled()
    {
        ModelMorphData data = this.bbsppp$getModelMorph();

        return data != null && data.enabled();
    }
}
