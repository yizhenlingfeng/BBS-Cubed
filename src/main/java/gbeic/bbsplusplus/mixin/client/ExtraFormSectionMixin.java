package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.forms.FluidForm;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.sections.ExtraFormSection;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把插件注册的伪装表单加进 form 拾取器的"杂项(extra)"分类 —— 对齐 CML 的
 * {@code ExtraFormSection.initiate()} 中 {@code extra.addForm(fluid)}。
 * TAIL 时 extra 分类已构建完毕，追加进其列表即可显示。
 *
 * <p>必须挂在 initiate() 上而不是启动事件里反射注入：BBS 2.7 的启动顺序下，
 * 资源重载监听器（reloadFromResourcePacks → FormCategories.setup()）在
 * ClientLifecycleEvents.CLIENT_STARTED 之后才执行，setup() 会整体重建
 * sections，启动期反射注入的表单会被冲掉（AAA 粒子伪装在伪装页消失的根因）。
 * FluidForm 因挂在本 initiate() 上而幸存；这里让 AAA 粒子与物品喷射走同一机制，
 * 每次重建自动补回。</p>
 */
@Mixin(value = ExtraFormSection.class, remap = false)
public abstract class ExtraFormSectionMixin
{
    @Shadow(remap = false)
    private FormCategory extra;

    @Inject(method = "initiate()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addExtraForms(CallbackInfo ci)
    {
        this.extra.addForm(new FluidForm());

        /* AAA 粒子表单依赖 aaa_particles 前置的 Effekseer 运行时，未装前置时跳过 */
        if (FabricLoader.getInstance().isModLoaded("aaa_particles"))
        {
            this.extra.addForm(new AAAParticleForm());
        }

        this.extra.addForm(new ItemSprayForm());
    }
}
