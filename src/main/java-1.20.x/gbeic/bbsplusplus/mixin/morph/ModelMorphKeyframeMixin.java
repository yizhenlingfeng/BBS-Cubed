package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.LegacyMorphMapping;
import gbeic.bbsplusplus.client.morph.ModelMorphData;
import gbeic.bbsplusplus.keyframes.IModelMorphKeyframe;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 Model Morph 配置挂到 BBS 原生 Model 关键帧上。
 *
 * <p>与 {@code TextureTweenKeyframeMixin} 完全同一套扩展机制：关键帧数据里的一个子树，
 * 加上复制 / 覆盖额外属性 / equals 三处同步，保证保存、撤销、复制粘贴都正确。</p>
 */
@Mixin(value = Keyframe.class, remap = false)
public class ModelMorphKeyframeMixin implements IModelMorphKeyframe
{
    @Unique
    private ModelMorphData bbsppp$modelMorph = new ModelMorphData();

    @Inject(method = "toData", at = @At("RETURN"), require = 1)
    private void bbsppp$writeModelMorph(CallbackInfoReturnable<BaseType> cir)
    {
        if (!this.bbsppp$isModelTrack() || !(cir.getReturnValue() instanceof MapType map))
        {
            return;
        }

        ModelMorphData data = this.bbsppp$modelMorph;

        if (data == null || data.equals(new ModelMorphData()))
        {
            return;
        }

        map.put(ModelMorphData.DATA_KEY, data.toData());
    }

    @Inject(method = "fromData", at = @At("TAIL"), require = 1)
    private void bbsppp$readModelMorph(BaseType data, CallbackInfo ci)
    {
        this.bbsppp$modelMorph = new ModelMorphData();

        if (!this.bbsppp$isModelTrack() || !(data instanceof MapType map))
        {
            return;
        }

        if (map.has(ModelMorphData.DATA_KEY))
        {
            this.bbsppp$modelMorph = ModelMorphData.fromData(map.get(ModelMorphData.DATA_KEY));
        }
        else if (map.has("bbsppp_model_morph_mode"))
        {
            /* 兼容早期原型：模式 + JSON 映射字符串。 */
            this.bbsppp$modelMorph.setEnabled(map.getInt("bbsppp_model_morph_mode", 0) != 0);
            this.bbsppp$modelMorph.setTextureSync(map.getBool("bbsppp_model_morph_texture_sync", true));
            this.bbsppp$modelMorph.setGroups(LegacyMorphMapping.parse(map.getString("bbsppp_model_morph_mapping", "")));
        }
    }

    @Inject(method = "copy", at = @At("TAIL"), require = 1)
    private void bbsppp$copyModelMorph(Keyframe<?> keyframe, CallbackInfo ci)
    {
        this.bbsppp$copyFrom(keyframe);
    }

    @Inject(method = "copyOverExtra", at = @At("TAIL"), require = 1)
    private void bbsppp$copyOverModelMorph(Keyframe<?> keyframe, CallbackInfo ci)
    {
        this.bbsppp$copyFrom(keyframe);
    }

    @Inject(method = "equals", at = @At("RETURN"), cancellable = true, require = 1)
    private void bbsppp$compareModelMorph(Object obj, CallbackInfoReturnable<Boolean> cir)
    {
        if (!cir.getReturnValue() || !(obj instanceof IModelMorphKeyframe other))
        {
            return;
        }

        cir.setReturnValue(this.bbsppp$modelMorph.equals(other.bbsppp$getModelMorph()));
    }

    @Unique
    private void bbsppp$copyFrom(Keyframe<?> keyframe)
    {
        if (keyframe instanceof IModelMorphKeyframe other && other.bbsppp$getModelMorph() != null)
        {
            this.bbsppp$modelMorph = other.bbsppp$getModelMorph().copy();
        }
        else
        {
            this.bbsppp$modelMorph = new ModelMorphData();
        }
    }

    @Unique
    private boolean bbsppp$isModelTrack()
    {
        Keyframe<?> keyframe = (Keyframe<?>) (Object) this;

        if (keyframe.getParent() == null)
        {
            return false;
        }

        String id = keyframe.getParent().getId();

        return "model".equals(id) || id.endsWith("/model");
    }

    @Override
    public ModelMorphData bbsppp$getModelMorph()
    {
        return this.bbsppp$modelMorph;
    }

    @Override
    public void bbsppp$setModelMorph(ModelMorphData data)
    {
        this.bbsppp$modelMorph = data == null ? new ModelMorphData() : data.copy();
    }
}
