package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.TextureGradeProvider;
import gbeic.bbsplusplus.api.GroupTextureGradeHolder;
import gbeic.bbsplusplus.api.TextureGradeHolder;
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

/** Selects the texture-grade shader without depending on ModelFormRenderer's version-specific signature. */
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
        Color fallbackPoseTint = null;
        float fallbackPoseWhiten = 0F;

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
        }

        if (!poseGrade && tintValue.a <= 0.0001F && whitenValue <= 0.0001F)
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

        return ModelTextureGradeShader.select(original, tintValue, whitenValue);
    }
}
