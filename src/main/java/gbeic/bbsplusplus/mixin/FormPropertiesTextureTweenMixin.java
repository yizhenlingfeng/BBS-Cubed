package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.texture.TextureTweenContext;
import gbeic.bbsplusplus.keyframes.LegacyTextureTweenTrackMigration;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把纹理补间接入模型原生纹理轨道，并在加载阶段触发旧轨道迁移。
 *
 * <p>原生 {@code texture} 轨道负责保存 Link，补间参数作为关键帧扩展字段挂在同一关键帧上。
 * 属性应用期间通过轨道路径反查所属模型，为运行时纹理生成器提供模型上下文。</p>
 */
@Mixin(value = FormProperties.class, remap = false)
public class FormPropertiesTextureTweenMixin
{
    /**
     * 注入目标：{@code FormProperties.applyProperty(...)} 入口。
     * 注入原因：关键帧段本身无法反查所属模型，而属性路径可以定位原生纹理属性的父模型。
     * 修改行为：模型原生纹理轨道插值前，把目标模型写入线程局部上下文。
     */
    @Inject(method = "applyProperty", at = @At("HEAD"))
    private void bbsppp$beginTextureTweenContext(float tick, Form form, KeyframeChannel<?> channel, float blend, CallbackInfo ci)
    {
        if (!bbsppp$isModelTextureChannel(channel.getId()))
        {
            return;
        }

        BaseValueBasic<?> property = FormUtils.getProperty(form, channel.getId());

        if (property != null && property.getParent() instanceof ModelForm modelForm)
        {
            TextureTweenContext.begin(modelForm);
        }
    }

    /**
     * 注入目标：{@code FormProperties.applyProperty(...)} 返回前。
     * 注入原因：模型上下文只能覆盖当前纹理轨道插值，不能泄漏到后续属性。
     * 修改行为：原生纹理轨道处理完成后立即清理线程局部上下文。
     */
    @Inject(method = "applyProperty", at = @At("RETURN"))
    private void bbsppp$endTextureTweenContext(float tick, Form form, KeyframeChannel<?> channel, float blend, CallbackInfo ci)
    {
        if (bbsppp$isModelTextureChannel(channel.getId()))
        {
            TextureTweenContext.end();
        }
    }

    /**
     * 注入目标：{@link FormProperties#fromData(BaseType)} 完成后。
     * 注入原因：旧工程仍可能包含独立的纹理补间轨道。
     * 修改行为：调用临时迁移器，把旧轨道合并进对应的原生纹理轨道并删除旧轨道。
     */
    @Inject(method = "fromData", at = @At("TAIL"))
    private void bbsppp$migrateLegacyTextureTweenTrack(BaseType data, CallbackInfo ci)
    {
        LegacyTextureTweenTrackMigration.migrate((FormProperties) (Object) this);
    }

    private static boolean bbsppp$isModelTextureChannel(String id)
    {
        return "texture".equals(id) || id.endsWith("/texture");
    }
}
