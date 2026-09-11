package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.api.GroupTextureGradeHolder;
import gbeic.bbsplusplus.api.TextureGradeHolder;
import gbeic.bbsplusplus.api.TextureGradeProvider;
import gbeic.bbsplusplus.client.screen.ModelTextureGradeShader;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

/**
 * 包住 {@code ModelInstance.render} 的 shader 供给器，决定这一次模型渲染用不用自定义变体
 * （避开 ModelFormRenderer 的版本相关签名）。
 *
 * <p>触发条件有三类：form 级调色/白化、pose 级（逐骨骼）调色/白化、pose 级附魔光效。
 * 前两类原本就有；附魔光效必须并进同一个判断 —— 否则"只开光效"时不会换 shader，
 * 表现为点了按钮完全没反应。</p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class ModelInstanceTextureGradeMixin
{
    @ModifyVariable(
        method = "render",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 1,
        remap = false
    )
    private Supplier<ShaderProgram> bbspp_cml$wrapTextureGradeShader(Supplier<ShaderProgram> program)
    {
        return () -> this.bbspp_cml$selectTextureGradeShader(program.get());
    }

    private ShaderProgram bbspp_cml$selectTextureGradeShader(ShaderProgram original)
    {
        if (original == null)
        {
            return null;
        }

        ModelInstance model = (ModelInstance) (Object) this;
        Form form = model.form;

        /* 纹理调色/白化必须在世界渲染（影片播放/世界中放置的模型方块）下同样生效：
         * 编辑页面是 UI 渲染（renderingWorld=false），原先只在 Iris 光影下跳过世界渲染，
         * 导致"退出编辑后修改仅编辑页面可见"。picker 仍是需排除的（拾取模式要原样输出）。 */
        if (original == BBSShaders.getPickerModelsProgram())
        {
            return original;
        }

        Color tintValue = new Color(1F, 1F, 1F, 0F);
        float whitenValue = 0F;

        if (form instanceof TextureGradeProvider provider)
        {
            ValueColor tint = provider.bbspp_cml$getTextureTint();
            ValueFloat whiten = provider.bbspp_cml$getTextureWhiten();

            if (tint != null && tint.get() != null)
            {
                tintValue.copy(tint.get());
            }
            if (whiten != null)
            {
                whitenValue = whiten.get();
            }
        }

        boolean poseGrade = false;
        boolean poseGlint = false;
        Color fallbackPoseTint = null;
        float fallbackPoseWhiten = 0F;
        Color fallbackPoseGlintColor = null;

        for (ModelGroup group : model.model.getAllGroups())
        {
            GroupTextureGradeHolder grade = (GroupTextureGradeHolder) group;
            Color groupTint = grade.bbspp_cml$getTextureTint();
            float groupWhiten = grade.bbspp_cml$getTextureWhiten();

            /* 动画器绕过 Model.applyPose() 直接设置 group.current 时，
             * 调色/白化存储在 Transform 的渲染期字段中（TransformMixin 实现 TextureGradeHolder）。 */
            if (groupTint.a <= 0.0001F && groupWhiten <= 0.0001F
                && group.current instanceof TextureGradeHolder renderGrade)
            {
                groupTint = renderGrade.bbspp_cml$getTextureTint();
                groupWhiten = renderGrade.bbspp_cml$getTextureWhiten();
            }

            if (groupTint.a > 0.0001F || groupWhiten > 0.0001F)
            {
                poseGrade = true;

                if (fallbackPoseTint == null)
                {
                    fallbackPoseTint = groupTint.copy();
                    fallbackPoseWhiten = groupWhiten;
                }
            }

            /* 附魔光效：只要有<b>任意</b>骨骼开了光效，整个模型就必须换到自定义 shader
             * —— 否则"只开光效、不设调色/白化"时压根不会切换，表现为点了按钮没反应。
             * 顺带记下第一个开光效组的颜色，供 CPU 路径的整模型 fallback 使用。 */
            if (!poseGlint && ((GroupGlintHolder) group).bbspp_cml$getGlint())
            {
                poseGlint = true;
                fallbackPoseGlintColor = ((GroupGlintHolder) group).bbspp_cml$getGlintColor().copy();
            }
            else if (!poseGlint && group.current instanceof GlintHolder renderGlint
                && renderGlint.bbspp_cml$getGlint())
            {
                poseGlint = true;
                fallbackPoseGlintColor = renderGlint.bbspp_cml$getGlintColor().copy();
            }
        }

        if (!poseGrade && !poseGlint && tintValue.a <= 0.0001F && whitenValue <= 0.0001F)
        {
            return original;
        }

        /* Dynamic Geo models (shape keys/CPU mode) are rendered as one combined buffer,
         * so they cannot switch uniforms per group. Use the first active pose grade as a
         * visible whole-model fallback instead of silently rendering a neutral shader. */
        boolean supportsPerGroupGrade = model.model instanceof Model && model.isVAORendered();

        if (!supportsPerGroupGrade && fallbackPoseTint != null)
        {
            if (fallbackPoseTint.a > 0.0001F)
            {
                tintValue.copy(fallbackPoseTint);
            }
            if (fallbackPoseWhiten > 0.0001F)
            {
                whitenValue = fallbackPoseWhiten;
            }
        }

        ShaderProgram selected = ModelTextureGradeShader.select(original, tintValue, whitenValue);

        /* 告诉渲染层这个模型走哪条路：VAO 才能逐组写 uniform；
         * CPU 路径必须跳过逐组写入，否则会把上面的整模型 fallback 覆盖成 0。 */
        ModelTextureGradeShader.setGlintPerGroup(supportsPerGroupGrade);

        if (selected != original)
        {
            /* 基准强度与颜色：VAO 模型下每组的 CubicVAORendererMixin 会立即覆盖它们；
             * 动态 Geo 模型整块绘制、无法逐组改 uniform，这里用"整模型 fallback"
             * （与上方 tint/whiten 的策略一致），否则开了光效会什么都不发生。 */
            ModelTextureGradeShader.applyGlint(selected, !supportsPerGroupGrade && poseGlint, fallbackPoseGlintColor);
        }

        return selected;
    }
}
