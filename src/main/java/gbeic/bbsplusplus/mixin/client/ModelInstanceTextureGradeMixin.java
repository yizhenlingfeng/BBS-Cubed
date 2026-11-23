package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.client.screen.ModelTextureGradeShader;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

/** Selects the texture-grade shader for form tinting, whitening and bone glint. */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class ModelInstanceTextureGradeMixin
{
    @Unique
    private long bbspp$glintEpoch = Long.MIN_VALUE;

    @Unique
    private int bbspp$glintPoseVersion = -1;

    @Unique
    private boolean bbspp$hasGlint;

    @Unique
    private Color bbspp$glintColor;

    @Unique
    private float bbspp$glintSpeed = 1F;

    @Unique
    private float bbspp$glintBrightness = 1F;

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

        if (original == BBSShaders.getPickerModelsProgram() || BBSRendering.isIrisWorldShadersEnabled())
        {
            return original;
        }

        ValueColor tint = gbeic.bbsplusplus.api.BBSPPFormValues.getTextureTint(form);
        ValueFloat whiten = gbeic.bbsplusplus.api.BBSPPFormValues.getTextureWhiten(form);
        Color tintValue = tint == null || tint.get() == null ? null : tint.get().copy();
        float whitenValue = whiten == null ? 0F : whiten.get();
        boolean poseGlint = this.bbspp$hasGlint(model);

        if (!poseGlint && (tintValue == null || tintValue.a <= 0.0001F) && whitenValue <= 0.0001F)
        {
            return original;
        }

        if (tintValue == null)
        {
            tintValue = new Color(1F, 1F, 1F, 0F);
        }

        boolean supportsPerGroupGrade = model.model instanceof Model && model.isVAORendered();
        ShaderProgram selected = ModelTextureGradeShader.select(original, tintValue, whitenValue);

        ModelTextureGradeShader.setGlintPerGroup(supportsPerGroupGrade);

        if (selected != original)
        {
            ModelTextureGradeShader.applyGlint(selected, !supportsPerGroupGrade && poseGlint,
                this.bbspp$glintColor, this.bbspp$glintSpeed, this.bbspp$glintBrightness);
        }

        return selected;
    }

    @Unique
    private boolean bbspp$hasGlint(ModelInstance model)
    {
        long epoch = RenderFrame.getEpoch();
        int poseVersion = model.form == null ? 0 : model.form.getPoseVersion();

        if (RenderFrame.isEnabled() && epoch == this.bbspp$glintEpoch && poseVersion == this.bbspp$glintPoseVersion)
        {
            return this.bbspp$hasGlint;
        }

        this.bbspp$glintEpoch = epoch;
        this.bbspp$glintPoseVersion = poseVersion;
        this.bbspp$hasGlint = false;
        this.bbspp$glintColor = null;
        this.bbspp$glintSpeed = 1F;
        this.bbspp$glintBrightness = 1F;

        for (ModelGroup group : model.model.getAllGroups())
        {
            if (((GroupGlintHolder) group).bbspp_cml$getGlint())
            {
                this.bbspp$saveGlint(
                    ((GroupGlintHolder) group).bbspp_cml$getGlintColor(),
                    (GlintSettingsHolder) group
                );
                break;
            }

            if (group.current instanceof GlintHolder glint && glint.bbspp_cml$getGlint())
            {
                GlintSettingsHolder tuning = group.current instanceof GlintSettingsHolder settings ? settings : null;

                this.bbspp$saveGlint(glint.bbspp_cml$getGlintColor(), tuning);
                break;
            }
        }

        return this.bbspp$hasGlint;
    }

    @Unique
    private void bbspp$saveGlint(Color color, GlintSettingsHolder tuning)
    {
        this.bbspp$hasGlint = true;
        this.bbspp$glintColor = color.copy();

        if (tuning != null)
        {
            this.bbspp$glintSpeed = tuning.bbspp_cml$getGlintSpeed();
            this.bbspp$glintBrightness = tuning.bbspp_cml$getGlintBrightness();
        }
    }
}
