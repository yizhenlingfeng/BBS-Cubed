package bbslod;

import bbslezy.actions.LezyDamageActionClip;
import bbslezy.client.screen.LezyFrameOverlays;
import bbslezy.audio.LezyCopyAudioImporter;
import bbslezy.camera.clips.screen.CinematicClip;
import bbslezy.camera.clips.screen.ColorClip;
import bbslezy.camera.clips.screen.LetterboxClip;
import bbslezy.camera.clips.screen.VignetteClip;
import bbslezy.client.screen.ScreenEffectRenderer;
import bbslezy.ui.film.clips.UICinematicClip;
import bbslezy.ui.film.clips.UIColorClip;
import bbslezy.ui.film.clips.UILetterboxClip;
import bbslezy.ui.film.clips.UIVignetteClip;
import bbslezy.forms.renderers.FormIllusionRenderer;
import bbslezy.ui.forms.editors.panels.UIIllusionFormPanel;
import bbslezy.ui.framework.elements.input.keyframes.factories.UIIllusionKeyframeFactory;
import bbslezy.ui.framework.elements.input.keyframes.factories.UILensRadiusSettingsKeyframeFactory;
import bbslezy.utils.keyframes.factories.IllusionKeyframeFactory;
import bbslezy.ui.LezyIrisHelper;
import bbslezy.utils.keyframes.factories.LensRadiusSettingsKeyframeFactory;
import bbslezy.video.LezyEncoderProbe;
import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import mchorse.bbs_mod.api.client.events.RegisterClipPanelsEvent;
import mchorse.bbs_mod.film.replays.tracks.TrackStyle;
import mchorse.bbs_mod.api.client.events.RegisterFormPanelsEvent;
import mchorse.bbs_mod.api.client.events.RegisterFrameOverlaysEvent;
import mchorse.bbs_mod.api.client.events.RegisterKeyframeEditorsEvent;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.clips.actions.UIDamageActionClip;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.BBSClientReadyEvent;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.api.client.events.RegisterImportersEvent;
import mchorse.bbs_mod.api.client.events.RegisterL10nEvent;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.Collections;

/**
 * The client half of the BBS Lezy addon, registered under {@code bbs-client-addon}.
 */
public class BBSLodClient implements BBSAddonMod
{
    /**
     * Supplies the settings screen's labels from the addon's own strings file, so the two
     * settings read as what they do instead of as their raw ids.
     */
    @Subscribe
    public void onL10n(RegisterL10nEvent event)
    {
        event.l10n.register((lang) -> Collections.singletonList(Link.create(BBSLod.MOD_ID + ":strings/" + lang + ".json")));
    }

    @Subscribe
    public void onClientSettings(RegisterClientSettingsEvent event)
    {
        event.register(Icons.GEAR, BBSLod.MOD_ID, (builder) ->
        {
            LodSettings.register(builder);
        });
    }

    @Subscribe
    public void onImporters(RegisterImportersEvent event)
    {
        event.register(new LezyCopyAudioImporter());
    }

    @Subscribe
    public void onClipPanels(RegisterClipPanelsEvent event)
    {
        event.register(LezyDamageActionClip.class, UIDamageActionClip::new);
        event.register(ColorClip.class, UIColorClip::new);
        event.register(LetterboxClip.class, UILetterboxClip::new);
        event.register(CinematicClip.class, UICinematicClip::new);
        event.register(VignetteClip.class, UIVignetteClip::new);
    }

    @Subscribe
    public void onFrameOverlays(RegisterFrameOverlaysEvent event)
    {
        /* BBS's own image and subtitle renderers go into this pass instead of ahead of it, so a
         * subtitle on an upper track is drawn over the effects of the tracks below it rather than
         * under them. If the registry takeover fails, LezyFrameOverlays automatically falls back
         * to drawing standard effects. */
        LezyFrameOverlays.install();

        event.register((stack, batcher, context) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();

            LezyFrameOverlays.render(stack, batcher, context, w, h);
        });
    }

    @Subscribe
    public void onKeyframeEditors(RegisterKeyframeEditorsEvent event)
    {
        event.register(IllusionKeyframeFactory.INSTANCE, UIIllusionKeyframeFactory::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerIllusionPanel(mchorse.bbs_mod.ui.forms.editors.forms.UIForm uiForm)
    {
        uiForm.registerPanel(new UIIllusionFormPanel(uiForm), L10n.lang("bbslezy.ui.forms.editors.illusion"), Icons.POSE);
    }

    @Subscribe
    public void onFormPanels(RegisterFormPanelsEvent event)
    {
        event.register(BBSLodClient::registerIllusionPanel);
    }

    /**
     * The film and form render events are plain Fabric events rather than the addon bus, because
     * they run every frame — subscribe to them once, from here.
     */
    @Subscribe
    public void onClientReady(BBSClientReadyEvent event)
    {
        LodEngine.register();
        LezyEncoderProbe.startProbeAsync();
        ClientTickEvents.END_CLIENT_TICK.register((client) -> LezyIrisHelper.tick());
        UIKeyframeFactory.register(LensRadiusSettingsKeyframeFactory.INSTANCE, UILensRadiusSettingsKeyframeFactory::new);
        TrackStyle.register("illusion", Icons.POSE, Colors.DEEP_PINK);
        TrackStyle.register("illusion_transform", Icons.ALL_DIRECTIONS, 0xdd66ff);
        FormRenderEvents.AFTER.register(FormIllusionRenderer::render);
    }
}
