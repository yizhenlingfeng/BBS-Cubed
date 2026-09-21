package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.api.GroupPbrHolder;
import gbeic.bbsplusplus.api.GroupTextureHolder;
import gbeic.bbsplusplus.client.screen.ModelTextureGradeShader;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormPbr;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * 在 {@link CubicVAORenderer#renderGroup} 的材质纹理解析处插入骨骼纹理覆盖。
 *
 * <p>FS 的渲染架构按<b>材质名</b>解析纹理（{@code textureResolver.apply(material)}，
 * 每 group 按材质分 VAO 逐一绑定绘制），与 CML 的"每 group 单 VAO + textureOverride
 * 字段"不同。故接线点选在 renderGroup 内<b>唯一一处</b> {@code Function.apply} 调用：
 * 若当前 {@link ModelGroup} 带有骨骼纹理覆盖（{@link GroupTextureHolder}，每帧由
 * ModelMixin 从 Pose 填充），则整组所有材质一律解析为该覆盖纹理 —— 语义与 CML 的
 * "选中骨骼整体换肤"一致；否则回落原 resolver（动画轨道 > 编辑器静态材质 > 模型默认）。</p>
 *
 * <p>局限：resolver 为 null 的调用方（不经 ModelFormRenderer 的裸渲染）不会触发绑定，
 * 覆盖同样不生效 —— 与原版行为一致，主场景（form 渲染）恒传 resolver。</p>
 */
@Mixin(value = CubicVAORenderer.class, remap = false)
public abstract class CubicVAORendererMixin
{
    @Shadow(remap = false)
    private ShaderProgram program;

    @Shadow(remap = false)
    private mchorse.bbs_mod.cubic.ModelInstance model;

    @Shadow(remap = false)
    private Function<String, Link> textureResolver;

    @Inject(method = "renderGroup", at = @At("HEAD"), require = 1, remap = false)
    private void bbspp_cml$applyGroupGlint(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model, CallbackInfoReturnable<Boolean> cir)
    {
        /* 附魔光效：优先使用 Model.applyPose() 传播的 group 级光效；
         * 若未激活（动画器绕过 applyPose 直接设 group.current），
         * 回退到 Transform 上的渲染期光效。 */
        boolean glint = group != null && ((GroupGlintHolder) group).bbspp_cml$getGlint();
        Color glintColor = group != null ? ((GroupGlintHolder) group).bbspp_cml$getGlintColor() : null;

        if (!glint && group != null && group.current instanceof GlintHolder renderGlint)
        {
            glint = renderGlint.bbspp_cml$getGlint();
            glintColor = renderGlint.bbspp_cml$getGlintColor();
        }

        /* 必须逐组写入（含关闭时的 0）—— 否则同一模型里前一个开了光效的骨骼
         * 会把状态泄漏给后一个。仅限 VAO 渲染；CPU 路径靠 select() 的整模型 fallback。 */
        if (ModelTextureGradeShader.isGlintPerGroup())
        {
            ModelTextureGradeShader.applyGlint(this.program, glint, glintColor);
        }
    }

    @Redirect(
        method = "renderGroup",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;",
            remap = false
        ),
        remap = false
    )
    private Object bbspp_cml$resolveBoneTexture(Function<String, Link> resolver, Object material, BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        Link override = ((GroupTextureHolder) group).bbspp_cml$getTextureOverride();

        if (override != null)
        {
            return override;
        }

        return resolver.apply((String) material);
    }

    /**
     * 逐骨骼 PBR 覆盖：当当前 group 带有骨骼级 PBR 值（任一 > 0）时，
     * 用骨骼的五值构造独立的 albedo variant key 并 track，使 Iris 的 PBR
     * 按骨骼值生成 specular/normal 贴图，覆盖材质级 PBR。
     *
     * <p>原生 {@code FormPbr.resolveAlbedo} 按 form identity + material name
     * 构造 variant key；这里在 group 有 PBR 覆盖时追加骨骼五值的量化片段，
     * 确保不同 PBR 值的骨骼拿到不同 GL texture id（Iris 按 id 缓存 PBR holder）。</p>
     */
    @Redirect(
        method = "renderGroup",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/forms/renderers/utils/FormPbr;resolveAlbedo(Lmchorse/bbs_mod/forms/forms/ModelForm;Ljava/lang/String;Lmchorse/bbs_mod/resources/Link;Lmchorse/bbs_mod/graphics/texture/Texture;)Lmchorse/bbs_mod/graphics/texture/Texture;",
            remap = false
        ),
        require = 1,
        remap = false
    )
    private Texture bbspp_cml$resolveBonePbrAlbedo(ModelForm form, String material, Link link, Texture texture, BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        /* 先走原生逻辑（材质级 PBR）。 */
        Texture base = FormPbr.resolveAlbedo(form, material, link, texture);

        /* 逐骨骼 PBR 覆盖：仅在 Iris 启用、link 有效时介入。 */
        if (form == null || link == null || !BBSRendering.isIrisShadersEnabled())
        {
            return base;
        }

        /* 优先使用 Model.applyPose() 传播的 group 级 PBR；
         * 若未激活（动画器绕过 applyPose 直接设 group.current），
         * 回退到 Transform 上的渲染期 PBR（与 glint 的 fallback 同构）。 */
        GroupPbrHolder pbr = (GroupPbrHolder) group;
        float smooth = pbr.bbspp_cml$getSmoothness();
        float metal = pbr.bbspp_cml$getMetallic();
        float sss = pbr.bbspp_cml$getSss();
        float emission = pbr.bbspp_cml$getEmission();
        float relief = pbr.bbspp_cml$getRelief();

        if (smooth <= 0F && metal <= 0F && sss <= 0F && emission <= 0F && relief <= 0F
            && group.current instanceof BonePbrHolder renderPbr)
        {
            smooth = renderPbr.bbspp_cml$getSmoothness();
            metal = renderPbr.bbspp_cml$getMetallic();
            sss = renderPbr.bbspp_cml$getSss();
            emission = renderPbr.bbspp_cml$getEmission();
            relief = renderPbr.bbspp_cml$getRelief();
        }

        if (smooth <= 0F && metal <= 0F && sss <= 0F && emission <= 0F && relief <= 0F)
        {
            return base;
        }

        /* 构造骨骼级 variant key：追加五值的量化片段，确保每个唯一 PBR 组合拿到独立 GL id。 */
        String materialKey = material == null ? "" : material;
        String boneKey = "pbr_bone:" + materialKey + ":" + System.identityHashCode(form)
            + ":" + Math.round(smooth * 255F)
            + ":" + Math.round(metal * 255F)
            + ":" + Math.round(sss * 255F)
            + ":" + Math.round(emission * 255F)
            + ":" + Math.round(relief * 255F);

        if (BBSModClient.getTextures() == null)
        {
            return base;
        }

        Texture variant = BBSModClient.getTextures().getVariant(link, boneKey);

        if (variant == null || variant == BBSModClient.getTextures().getError())
        {
            return base;
        }

        IrisUtils.trackPbrVariant(variant, link, smooth, metal, sss, emission, relief);

        return variant;
    }
}
