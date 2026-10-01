package bbslod;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.BBSApi;
import mchorse.bbs_mod.api.Subscribe;
import bbslezy.actions.LezyDamageActionClip;
import bbslezy.camera.clips.screen.CinematicClip;
import bbslezy.camera.clips.screen.ColorClip;
import bbslezy.camera.clips.screen.LetterboxClip;
import bbslezy.camera.clips.screen.VignetteClip;
import bbslezy.forms.utils.Illusion;
import bbslezy.forms.values.ValueIllusion;
import bbslezy.utils.keyframes.factories.IllusionKeyframeFactory;
import bbslezy.utils.keyframes.factories.LensRadiusSettingsKeyframeFactory;
import mchorse.bbs_mod.api.events.RegisterActionClipsEvent;
import mchorse.bbs_mod.api.events.RegisterCameraClipsEvent;
import mchorse.bbs_mod.api.events.RegisterFormModifiersEvent;
import mchorse.bbs_mod.api.events.RegisterKeyframeFactoriesEvent;
import mchorse.bbs_mod.api.events.RegisterSourcePacksEvent;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.utils.colors.Colors;
/**
 * The common half of the BBS Lezy addon — registered under the {@code bbs-addon} entry point and
 * loaded on both sides.
 *
 * <p>Its client half is {@link BBSLodClient}, and it has to be a separate class: the client events
 * live in BBS's client source set, and a class that so much as mentions one of them cannot be
 * loaded on a dedicated server.</p>
 */
public class BBSLod implements BBSAddonMod
{
    public static final String MOD_ID = "bbslezy";

    @Subscribe
    public void onSourcePacks(RegisterSourcePacksEvent event)
    {
        /* Before anything else: a mismatch here reads as "this addon does not fit this BBS build"
         * rather than as a crash on the first thing the user does. */
        BBSApi.requireVersion(MOD_ID, 2);

        /* Makes this addon's own assets addressable as bbslezy:... links. */
        event.registerAddon(MOD_ID, BBSLod.class);
    }

    @Subscribe
    public void onActionClips(RegisterActionClipsEvent event)
    {
        event.factory.register(Link.bbs("damage"), LezyDamageActionClip.class, new ClipFactoryData(Icons.EXCLAMATION, Colors.RED));
    }


    @Subscribe
    public void onKeyframeFactories(RegisterKeyframeFactoriesEvent event)
    {
        event.register("lens_radius_settings", LensRadiusSettingsKeyframeFactory.INSTANCE);
        event.register("illusion", IllusionKeyframeFactory.INSTANCE);
    }

    @Subscribe
    public void onFormModifiers(RegisterFormModifiersEvent event)
    {
        event.register((form) ->
        {
            form.add(new ValueIllusion("illusion", new Illusion()));
            form.add(new ValueTransform("illusion_transform", new Transform()));
        });
    }
    @Subscribe
    public void onCameraClips(RegisterCameraClipsEvent event)
    {
        event.factory.register(Link.bbs("color"), ColorClip.class, new ClipFactoryData(Icons.IMAGE, 0x4488ff));
        event.factory.register(Link.bbs("letterbox"), LetterboxClip.class, new ClipFactoryData(Icons.FULLSCREEN, 0x222222));
        event.factory.register(Link.bbs("cinematic"), CinematicClip.class, new ClipFactoryData(Icons.CAMERA, 0xff8800));
        event.factory.register(Link.bbs("vignette"), VignetteClip.class, new ClipFactoryData(Icons.CIRCLE, 0x333333));
    }
}
