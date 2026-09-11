package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.api.GroupTextureHolder;
import gbeic.bbsplusplus.api.GroupTextureGradeHolder;
import gbeic.bbsplusplus.api.TextureGradeHolder;
import gbeic.bbsplusplus.client.screen.ModelTextureGradeShader;
import gbeic.bbsplusplus.pbr.render.BonePBRContext;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
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
public class CubicVAORendererMixin
{
    @Shadow(remap = false)
    private ShaderProgram program;

    @Inject(method = "renderGroup", at = @At("HEAD"), require = 1, remap = false)
    private void bbspp_cml$applyTextureGrade(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model, CallbackInfoReturnable<Boolean> cir)
    {
        BonePBRContext.setCurrentBone(group == null ? null : group.id);

        /* 优先使用 Model.applyPose() 传播的 group 级调色/白化；
         * 若未激活（动画器绕过 applyPose 直接设 group.current），
         * 回退到 Transform 上的渲染期调色/白化。 */
        GroupTextureGradeHolder grade = (GroupTextureGradeHolder) group;
        Color tint = grade.bbspp_cml$getTextureTint();
        float whiten = grade.bbspp_cml$getTextureWhiten();

        if (tint.a <= 0.0001F && whiten <= 0.0001F && group.current instanceof TextureGradeHolder renderGrade)
        {
            tint = renderGrade.bbspp_cml$getTextureTint();
            whiten = renderGrade.bbspp_cml$getTextureWhiten();
        }

        /* 附魔光效：与调色/白化同样的"group 级优先，回退到 group.current"取法。 */
        boolean glint = group != null && ((GroupGlintHolder) group).bbspp_cml$getGlint();
        Color glintColor = group != null ? ((GroupGlintHolder) group).bbspp_cml$getGlintColor() : null;

        if (!glint && group != null && group.current instanceof GlintHolder renderGlint)
        {
            glint = renderGlint.bbspp_cml$getGlint();
            glintColor = renderGlint.bbspp_cml$getGlintColor();
        }

        ModelTextureGradeShader.applyGroup(this.program, tint, whiten);

        /* 附魔光效：必须逐组写入（含关闭时的 0）—— 否则同一模型里前一个开了光效的骨骼
         * 会把状态泄漏给后一个。仅限 VAO 渲染；CPU 路径靠 select() 的整模型 fallback。 */
        if (ModelTextureGradeShader.isGlintPerGroup())
        {
            ModelTextureGradeShader.applyGlint(this.program, glint, glintColor);
        }
    }

    @Inject(method = "renderGroup", at = @At("RETURN"), require = 1, remap = false)
    private void bbspp_snow$clearBonePbr(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model, CallbackInfoReturnable<Boolean> cir)
    {
        BonePBRContext.clearCurrentBone();
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
}
