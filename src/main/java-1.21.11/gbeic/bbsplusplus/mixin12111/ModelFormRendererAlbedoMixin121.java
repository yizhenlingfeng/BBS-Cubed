package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.grade.BoneMaterialContext121;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 记下「当前材质通道」的材质名与材质链接（施工表 B2-02/B2-03 的输入）。
 *
 * <p>1.21.11 的 {@code ModelInstance#render} 为每个材质各跑一遍：先
 * {@code textureResolver.apply(material)}（最终落到 {@code ModelFormRenderer#albedo(String, Link)}）
 * 绑定该材质的贴图，再遍历整个模型提交一次。逐骨骼的贴图覆盖与 PBR 需要「这个材质的
 * {@link Link} 是什么」才能造出该骨骼的变体贴图，而这个 {@code Link} 只存在于
 * {@code albedo} 的形参里 —— 于是在它的入口把它记进 {@link BoneMaterialContext121}，
 * 同一次材质通道内的逐组提交就能查到。</p>
 *
 * <p>{@code albedo} 有返回值（{@link Texture}），所以 callback 必须是
 * {@link CallbackInfoReturnable}：写成 {@code CallbackInfo} 会在类加载时抛
 * {@code InvalidInjectionException: CallbackInfoReturnable is required}，
 * 而那是<b>启动阶段</b>让整个 {@code ModelFormRenderer} 转换失败、客户端直接崩溃的错误。</p>
 *
 * <p>整材质通道（material 为 {@code ""}）不会调用 {@code albedo}，上下文由
 * {@code ModelGroupSubmitMixin121} 在每个模型渲染开始时清空，因此读到的自然是
 * 「无材质」（贴图沿用调用方绑定的那张）。</p>
 */
@Mixin(value = ModelFormRenderer.class, remap = false)
public class ModelFormRendererAlbedoMixin121
{
    @Inject(method = "albedo", at = @At("HEAD"))
    private void bbspp$captureMaterialContext(String material, Link link, CallbackInfoReturnable<Texture> info)
    {
        BoneMaterialContext121.set(material, link);
    }
}
