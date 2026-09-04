package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.forms.FluidForm;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.sections.ExtraFormSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把流体伪装加进 form 拾取器的"杂项(extra)"分类 —— 对齐 CML 的
 * {@code ExtraFormSection.initiate()} 中 {@code extra.addForm(fluid)}。
 * TAIL 时 extra 分类已构建完毕，追加进其列表即可显示。
 */
@Mixin(value = ExtraFormSection.class, remap = false)
public abstract class ExtraFormSectionMixin
{
    @Shadow(remap = false)
    private FormCategory extra;

    @Inject(method = "initiate()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addFluidForm(CallbackInfo ci)
    {
        this.extra.addForm(new FluidForm());
    }
}
