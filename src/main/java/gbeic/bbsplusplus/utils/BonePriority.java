package gbeic.bbsplusplus.utils;

import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.Map;

/** Controls which keyframe track is preferred when a bone is picked in a viewport. */
public class BonePriority
{
    public static void openMenu(UIContext context, UIFilmPanel panel)
    {
        Replay replay = panel == null ? null : panel.replayEditor.getReplay();

        openMenu(context, replay == null ? null : replay.properties.tracks);
    }

    public static void openMenu(UIContext context, AnimationState state)
    {
        openMenu(context, state == null ? null : state.properties.tracks);
    }

    public static void openMenu(UIContext context, Map<TrackId, KeyframeChannel> channels)
    {
        if (context == null)
        {
            return;
        }

        context.replaceContextMenu((menu) ->
        {
            String current = CMLSettings.bonePriorityTrack.get();

            menu.action(Icons.LIMB, SnowUIKeys.BONE_PRIORITY_EXPANDED_LIMB, CMLSettings.bonePriorityExpandedLimb.get(), () ->
            {
                CMLSettings.bonePriorityExpandedLimb.set(!CMLSettings.bonePriorityExpandedLimb.get());
            });

            menu.action(Icons.REFRESH, SnowUIKeys.BONE_PRIORITY_DEFAULT, current == null || current.isEmpty(), () ->
            {
                CMLSettings.bonePriorityTrack.set("");
            });

            if (channels == null)
            {
                return;
            }

            IKeyframeFactory bonePbr = KeyframeFactories.FACTORIES.get("bone_pbr");

            for (Map.Entry<TrackId, KeyframeChannel> entry : channels.entrySet())
            {
                IKeyframeFactory factory = entry.getValue().getFactory();

                if (factory != KeyframeFactories.POSE && factory != KeyframeFactories.SHAPE_KEYS && factory != bonePbr)
                {
                    continue;
                }

                String key = entry.getKey().toKey();

                menu.action(Icons.POSE, IKey.constant(key), key.equals(current), () ->
                {
                    CMLSettings.bonePriorityTrack.set(key);
                });
            }
        });
    }

    public static String getPriorityTrack()
    {
        String track = CMLSettings.bonePriorityTrack == null ? null : CMLSettings.bonePriorityTrack.get();

        return track == null || track.isEmpty() ? null : track;
    }

    public static boolean isExpandedLimbPriorityEnabled()
    {
        return CMLSettings.bonePriorityExpandedLimb != null && CMLSettings.bonePriorityExpandedLimb.get();
    }
}
