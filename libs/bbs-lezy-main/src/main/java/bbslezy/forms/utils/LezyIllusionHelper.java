package bbslezy.forms.utils;

import bbslezy.forms.values.ValueIllusion;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.pose.Transform;

public final class LezyIllusionHelper
{
    public static final String ILLUSION_ID = "illusion";
    public static final String ILLUSION_TRANSFORM_ID = "illusion_transform";

    private LezyIllusionHelper()
    {}

    public static ValueIllusion getIllusionValue(Form form)
    {
        if (form == null)
        {
            return null;
        }

        BaseValue value = form.get(ILLUSION_ID);

        if (value instanceof ValueIllusion valueIllusion)
        {
            return valueIllusion;
        }

        return null;
    }

    public static Illusion getIllusion(Form form)
    {
        ValueIllusion value = getIllusionValue(form);

        return value != null ? value.get() : null;
    }

    public static Replay getCurrentReplay()
    {
        try
        {
            return FilmControllerContext.instance.replay;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Resolves illusion from form. On a replay actor, checks:
     * 1. Direct runtime value / property on the form (e.g. keyframe applied).
     * 2. Replay keyframe channel evaluation on current tick.
     * 3. Replay source form (for changes made in form editor before baking/keyframing).
     */
    public static Illusion resolveIllusion(Form form)
    {
        Illusion illusion = getIllusion(form);

        if (illusion != null && illusion.count > 0)
        {
            return illusion;
        }

        Replay currentReplay = getCurrentReplay();

        if (currentReplay != null)
        {
            /* Check if there's a keyframe track on the replay */
            KeyframeChannel<?> channel = currentReplay.properties.get(TrackId.property("", ILLUSION_ID));

            if (channel != null && !channel.isEmpty())
            {
                try
                {
                    float tick = currentReplay.keyframes.x.isEmpty() ? 0F : currentReplay.getTick(0);
                    KeyframeSegment<?> segment = channel.find(tick);

                    if (segment != null && segment.createInterpolated() instanceof Illusion interpolated)
                    {
                        if (interpolated.count > 0)
                        {
                            setIllusion(form, interpolated);

                            return interpolated;
                        }
                    }
                }
                catch (Throwable ignored)
                {}
            }

            /* Check Replay's authored form */
            Form replayForm = currentReplay.form.get();

            if (replayForm != null && replayForm != form)
            {
                Illusion fromReplay = getIllusion(replayForm);

                if (fromReplay != null && fromReplay.count > 0)
                {
                    syncIllusion(replayForm, form);

                    return fromReplay;
                }
            }
        }

        return illusion;
    }

    public static ValueTransform getIllusionTransformValue(Form form)
    {
        if (form == null)
        {
            return null;
        }

        BaseValue value = form.get(ILLUSION_TRANSFORM_ID);

        if (value instanceof ValueTransform valueTransform)
        {
            return valueTransform;
        }

        return null;
    }

    public static Transform getIllusionTransform(Form form)
    {
        ValueTransform value = getIllusionTransformValue(form);

        if (value != null && !value.get().isDefault())
        {
            return value.get();
        }

        Replay currentReplay = getCurrentReplay();

        if (currentReplay != null)
        {
            KeyframeChannel<?> channel = currentReplay.properties.get(TrackId.property("", ILLUSION_TRANSFORM_ID));

            if (channel != null && !channel.isEmpty())
            {
                try
                {
                    float tick = currentReplay.keyframes.x.isEmpty() ? 0F : currentReplay.getTick(0);
                    KeyframeSegment<?> segment = channel.find(tick);

                    if (segment != null && segment.createInterpolated() instanceof Transform interpolated)
                    {
                        return interpolated;
                    }
                }
                catch (Throwable ignored)
                {}
            }

            Form replayForm = currentReplay.form.get();

            if (replayForm != null && replayForm != form)
            {
                return getIllusionTransform(replayForm);
            }
        }

        return value != null ? value.get() : null;
    }

    public static void syncIllusion(Form source, Form target)
    {
        if (source == null || target == null)
        {
            return;
        }

        Illusion illusion = getIllusion(source);

        if (illusion != null)
        {
            setIllusion(target, illusion);
        }

        Transform transform = getIllusionTransform(source);

        if (transform != null)
        {
            setIllusionTransform(target, transform);
        }
    }

    public static void setIllusion(Form form, Illusion illusion)
    {
        if (form == null || illusion == null)
        {
            return;
        }

        ValueIllusion val = getIllusionValue(form);

        if (val == null)
        {
            val = new ValueIllusion(ILLUSION_ID, illusion.copy());
            form.add(val);
        }
        else
        {
            val.set(illusion.copy());
        }
    }

    public static void setIllusionTransform(Form form, Transform transform)
    {
        if (form == null || transform == null)
        {
            return;
        }

        ValueTransform val = getIllusionTransformValue(form);

        if (val == null)
        {
            val = new ValueTransform(ILLUSION_TRANSFORM_ID, new Transform());
            val.set(transform.copy());
            form.add(val);
        }
        else
        {
            val.set(transform.copy());
        }
    }

    public static boolean hasIllusion(Form form)
    {
        Illusion illusion = resolveIllusion(form);

        return illusion != null && illusion.count > 0;
    }
}
