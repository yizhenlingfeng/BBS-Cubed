package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.TextureGradeHolder;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds per-bone texture grading to keyframes opened from an expanded Pose track. */
@Mixin(value = UIPoseTransformKeyframeFactory.class, remap = false)
public abstract class UIPoseTransformKeyframeFactoryMixin
{
    @Unique
    private Keyframe<PoseTransform> bbspp_cml$keyframe;

    @Unique
    private UIKeyframes bbspp_cml$editor;

    @Unique
    private UIColor bbspp_cml$textureTint;

    @Unique
    private UITrackpad bbspp_cml$textureWhiten;

    @Unique
    private boolean bbspp_cml$syncing;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addExpandedPoseTextureGrade(
        Keyframe<PoseTransform> keyframe,
        UIKeyframes editor,
        CallbackInfo ci
    )
    {
        TextureGradeHolder grade = (TextureGradeHolder) keyframe.getValue();

        this.bbspp_cml$keyframe = keyframe;
        this.bbspp_cml$editor = editor;
        this.bbspp_cml$textureTint = new UIColor((color) ->
        {
            if (this.bbspp_cml$syncing)
            {
                return;
            }

            Color tint = new Color().set(color);
            TextureGradeHolder current = (TextureGradeHolder) this.bbspp_cml$keyframe.getValue();

            if (tint.a <= 0.0001F
                && current.bbspp_cml$getTextureTint().a <= 0.0001F
                && tint.getRGBColor() != current.bbspp_cml$getTextureTint().getRGBColor())
            {
                tint.a = 1F;
                this.bbspp_cml$textureTint.setColor(tint.getARGBColor());
            }

            this.bbspp_cml$apply((target) -> target.bbspp_cml$setTextureTint(tint));
        }).withAlpha().direction(Direction.LEFT);
        this.bbspp_cml$textureTint.tooltip(
            L10n.lang("bbspp.ui.film.replays.texture_tint_tooltip"));

        this.bbspp_cml$textureWhiten = new UITrackpad((value) ->
        {
            if (!this.bbspp_cml$syncing)
            {
                this.bbspp_cml$apply(
                    (target) -> target.bbspp_cml$setTextureWhiten(value.floatValue()));
            }
        });
        this.bbspp_cml$textureWhiten
            .limit(0D, 1D)
            .increment(0.05D)
            .values(0.05D, 0.01D, 0.1D);
        this.bbspp_cml$textureWhiten.tooltip(
            L10n.lang("bbspp.ui.film.replays.texture_whiten_tooltip"));

        UISection section = new UISection(
            L10n.lang("bbspp.ui.film.replays.texture_grade_section"));
        section.fields.add(
            UI.label(L10n.lang("bbspp.ui.film.replays.texture_tint")),
            this.bbspp_cml$textureTint,
            UI.label(L10n.lang("bbspp.ui.film.replays.texture_whiten")),
            this.bbspp_cml$textureWhiten
        );
        section.setExpanded(false);

        this.bbspp_cml$syncing = true;

        try
        {
            this.bbspp_cml$textureTint.setColor(grade.bbspp_cml$getTextureTint().getARGBColor());
            this.bbspp_cml$textureWhiten.setValue(grade.bbspp_cml$getTextureWhiten());
        }
        finally
        {
            this.bbspp_cml$syncing = false;
        }

        ((UIPoseTransformKeyframeFactory) (Object) this).scroll.add(section);
    }

    @Unique
    private void bbspp_cml$apply(java.util.function.Consumer<TextureGradeHolder> consumer)
    {
        UIPoseTransformKeyframeFactory.UIPoseTransforms.apply(
            this.bbspp_cml$editor,
            this.bbspp_cml$keyframe,
            (transform) -> consumer.accept((TextureGradeHolder) transform)
        );
    }
}
