package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.MolangSharedProvider;
import gbeic.bbsplusplus.api.TextureGradeProvider;
import gbeic.bbsplusplus.api.ActionsOverlayProvider;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.values.ValueActionsConfig;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * 给 ModelForm 追加"variable 动作模型互通"开关(molangShared,默认 false):
 * 构造 TAIL 注册进 value 树,随 form 自动序列化/复制;旧数据没有该键时保持
 * 默认 false(隔离),未装本插件的 BBS 读到多余键会忽略,双向兼容。
 * 消费点在 {@code AnimatorMixin}(client)。
 */
@Mixin(value = ModelForm.class, remap = false)
public abstract class ModelFormCMLMixin implements MolangSharedProvider, TextureGradeProvider, ActionsOverlayProvider
{
    @Unique
    private static final String[] bbspp_ACTION_SLOTS = {
        "idle", "running", "sprinting", "crouching", "crouching_idle", "dying", "falling",
        "swipe", "jump", "jump_alt", "hurt", "land", "shoot", "consume", "base_pre", "base_post"
    };

    @Unique
    private ValueActionsConfig bbspp_cml$actionsOverlay;

    @Unique
    private List<ValueActionsConfig> bbspp_cml$additionalActionsOverlays;

    @Unique
    private ValueBoolean bbspp_cml$molangShared;

    @Unique
    private ValueColor bbspp_cml$textureTint;

    @Unique
    private ValueFloat bbspp_cml$textureWhiten;

    @Inject(
        method = "<init>()V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/forms/forms/ModelForm;add(Lmchorse/bbs_mod/settings/values/base/BaseValue;)V",
            ordinal = 6,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void bbspp_cml$registerActionsOverlays(CallbackInfo ci)
    {
        this.bbspp_cml$additionalActionsOverlays = new ArrayList<>();

        if (!CMLSettings.isSnowActionsEnabled())
        {
            return;
        }

        ModelForm form = (ModelForm) (Object) this;

        this.bbspp_cml$actionsOverlay = new ValueActionsConfig(
            "actions_overlay",
            this.bbspp_cml$createEmptyActionsOverlay()
        );
        form.add(this.bbspp_cml$actionsOverlay);

        /* 2.7 起 pose_transform_overlays 拆为 pose_overlays 与 transform_overlays 两个计数；
         * CML 的 actions overlay 作为并行覆盖集，数量与二者之和保持一致。 */
        int overlayCount = (BBSSettings.recordingPoseOverlays == null ? 0 : BBSSettings.recordingPoseOverlays.get())
            + (BBSSettings.recordingTransformOverlays == null ? 0 : BBSSettings.recordingTransformOverlays.get());

        for (int i = 0; i < overlayCount; i++)
        {
            ValueActionsConfig overlay = new ValueActionsConfig(
                "actions_overlay" + i,
                this.bbspp_cml$createEmptyActionsOverlay()
            );

            this.bbspp_cml$additionalActionsOverlays.add(overlay);
            form.add(overlay);
        }
    }

    @Unique
    private ActionsConfig bbspp_cml$createEmptyActionsOverlay()
    {
        ActionsConfig configs = new ActionsConfig();

        for (String key : bbspp_ACTION_SLOTS)
        {
            configs.actions.put(key, new ActionConfig(""));
        }

        return configs;
    }

    @Inject(method = "<init>()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$registerMolangShared(CallbackInfo ci)
    {
        this.bbspp_cml$molangShared = new ValueBoolean("molangShared", false);
        ((ModelForm) (Object) this).add(this.bbspp_cml$molangShared);

        /* Transparent white is a neutral tint; the alpha channel is the effect strength. */
        this.bbspp_cml$textureTint = new ValueColor("texture_tint", new Color(1F, 1F, 1F, 0F));
        this.bbspp_cml$textureWhiten = new ValueFloat("texture_whiten", 0F, 0F, 1F);
        ((ModelForm) (Object) this).add(this.bbspp_cml$textureTint);
        ((ModelForm) (Object) this).add(this.bbspp_cml$textureWhiten);

    }

    @Override
    public ValueBoolean bbspp_cml$getMolangShared()
    {
        return this.bbspp_cml$molangShared;
    }

    @Override
    public ValueColor bbspp_cml$getTextureTint()
    {
        return this.bbspp_cml$textureTint;
    }

    @Override
    public ValueFloat bbspp_cml$getTextureWhiten()
    {
        return this.bbspp_cml$textureWhiten;
    }

    @Override
    public ValueActionsConfig bbspp_cml$getActionsOverlay()
    {
        return this.bbspp_cml$actionsOverlay;
    }

    @Override
    public List<ValueActionsConfig> bbspp_cml$getAdditionalActionsOverlays()
    {
        return this.bbspp_cml$additionalActionsOverlays;
    }
}
