package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import gbeic.bbsplusplus.client.morph.ModelMorphState;
import mchorse.bbs_mod.forms.forms.ModelForm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 把形变状态挂在模型表单实例上。
 *
 * <p>状态属于表单实例而不是模型资源，因此两个回放实例共用同一份模型资源、
 * 只有一个开启 Morph 时不会互相影响。</p>
 */
@Mixin(value = ModelForm.class, remap = false)
public class ModelFormMorphMixin implements ModelMorphRuntime.IMorphForm
{
    @Unique
    private final ModelMorphState bbsppp$modelMorphState = new ModelMorphState();

    @Override
    public ModelMorphState bbsppp$getModelMorphState()
    {
        return this.bbsppp$modelMorphState;
    }
}
