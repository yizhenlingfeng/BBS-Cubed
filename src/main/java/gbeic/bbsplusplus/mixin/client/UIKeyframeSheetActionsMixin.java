package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = UIKeyframeSheet.class, remap = false)
public abstract class UIKeyframeSheetActionsMixin
{
    @Shadow
    @Final
    public KeyframeChannel<?> channel;

    @Shadow
    @Final
    public BaseValueBasic<?> property;

    @Unique
    private boolean bbspp_cml$removingTimelineSegment;

    @Inject(method = "remove", at = @At("HEAD"), remap = false)
    private void bbspp_cml$removeWholeTimelineSegment(Keyframe<?> keyframe, CallbackInfo ci)
    {
        if (!CMLSettings.isSnowActionsEnabled()
            || this.bbspp_cml$removingTimelineSegment
            || !(keyframe.getValue() instanceof ActionsConfig selected))
        {
            return;
        }

        Map<String, String> clips = new HashMap<>();

        for (Map.Entry<String, ActionConfig> entry : selected.actions.entrySet())
        {
            ActionTimelineConfig timeline = (ActionTimelineConfig) entry.getValue();

            if (timeline.bbspp_cml$isTimelineDriven())
            {
                clips.put(entry.getKey(), timeline.bbspp_cml$getClipId());
            }
        }

        if (clips.isEmpty())
        {
            return;
        }

        boolean overlay = this.property != null && this.property.getId().startsWith("actions_overlay");
        List<Keyframe<?>> markers = new ArrayList<>((List<Keyframe<?>>) (List<?>) this.channel.getKeyframes());

        this.bbspp_cml$removingTimelineSegment = true;

        try
        {
            for (Keyframe<?> marker : markers)
            {
                if (marker == keyframe || !(marker.getValue() instanceof ActionsConfig configs))
                {
                    continue;
                }

                boolean changed = false;

                for (Map.Entry<String, String> clip : clips.entrySet())
                {
                    ActionConfig action = configs.actions.get(clip.getKey());

                    if (action == null
                        || !clip.getValue().equals(((ActionTimelineConfig) action).bbspp_cml$getClipId()))
                    {
                        continue;
                    }

                    if (overlay)
                    {
                        configs.actions.put(clip.getKey(), new ActionConfig(""));
                    }
                    else
                    {
                        configs.actions.remove(clip.getKey());
                    }

                    changed = true;
                }

                if (changed && this.bbspp_cml$isDisposable(configs, overlay))
                {
                    ((UIKeyframeSheet) (Object) this).remove((Keyframe) marker);
                }
            }
        }
        finally
        {
            this.bbspp_cml$removingTimelineSegment = false;
        }
    }

    @Unique
    private boolean bbspp_cml$isDisposable(ActionsConfig configs, boolean overlay)
    {
        for (Map.Entry<String, ActionConfig> entry : configs.actions.entrySet())
        {
            ActionConfig action = entry.getValue();

            if (((ActionTimelineConfig) action).bbspp_cml$isTimelineDriven())
            {
                return false;
            }

            boolean neutral = overlay
                ? (action.name == null || action.name.isEmpty()) && action.isDefault()
                : action.isDefault(entry.getKey());

            if (!neutral)
            {
                return false;
            }
        }

        return true;
    }
}
