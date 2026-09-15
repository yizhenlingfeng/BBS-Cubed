package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the film editor's bulk Pose insert hotkey to animation states. */
@Mixin(value = UIAnimationStateEditor.class, remap = false)
public abstract class UIAnimationStatePoseInsertMixin
{
    @Shadow
    public UIKeyframeEditor keyframeEditor;

    @Shadow
    public UIFormEditor editor;

    @Shadow
    private AnimationState state;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$registerPoseFrameKey(UIFormEditor editor, CallbackInfo ci)
    {
        UIAnimationStateEditor self = (UIAnimationStateEditor) (Object) this;

        self.keys().register(Keys.FILM_CONTROLLER_INSERT_FRAME, () ->
        {
            if (this.bbspp_cml$insertPoseKeyframesAtCursor())
            {
                UIUtils.playClick();
            }
        }).active(this::bbspp_cml$hasPoseTracks);
    }

    @Unique
    private boolean bbspp_cml$hasPoseTracks()
    {
        if (this.state == null || this.keyframeEditor == null)
        {
            return false;
        }

        for (TrackId track : this.state.properties.tracks.keySet())
        {
            if (track.is(TrackKind.BONE))
            {
                return true;
            }
        }

        return false;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private boolean bbspp_cml$insertPoseKeyframesAtCursor()
    {
        if (!this.bbspp_cml$hasPoseTracks())
        {
            return false;
        }

        float tick = this.editor.getCursor();
        boolean[] inserted = {false};

        BaseValue.edit(this.state.properties, (properties) ->
        {
            for (KeyframeChannel<?> channel : properties.tracks.values())
            {
                if (TrackId.kindOf(channel.getId()) != TrackKind.BONE)
                {
                    continue;
                }

                KeyframeChannel<PoseTransform> poseChannel = (KeyframeChannel<PoseTransform>) channel;
                KeyframeSegment<PoseTransform> segment = poseChannel.find(tick);
                PoseTransform value = segment == null ? new PoseTransform() : segment.createInterpolated();
                int index = poseChannel.insert(tick, value);
                Keyframe<PoseTransform> keyframe = poseChannel.get(index);
                Keyframe<PoseTransform> template = segment == null ? null : segment.a;

                if (template != null && template != keyframe)
                {
                    keyframe.copyOverExtra(template);
                }

                inserted[0] = true;
            }
        });

        return inserted[0];
    }
}
