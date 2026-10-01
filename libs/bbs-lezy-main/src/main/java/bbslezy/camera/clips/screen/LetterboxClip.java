package bbslezy.camera.clips.screen;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

public class LetterboxClip extends CameraClip
{
    public static final double DEFAULT_HEIGHT = 0.48D;
    public static final double DEFAULT_WIDTH = 1.0D;
    private static final Color DEFAULT_COLOR = Color.rgba(Colors.A100);

    /* Static property values (active by default immediately upon adding the clip) */
    public final ValueDouble height = new ValueDouble("height", DEFAULT_HEIGHT);
    public final ValueDouble width = new ValueDouble("width", DEFAULT_WIDTH);
    public final ValueDouble smoothness = new ValueDouble("smoothness", 0D);
    public final ValueColor color = new ValueColor("color", DEFAULT_COLOR.copy());

    /* Optional keyframe channels for animation */
    public final KeyframeChannel<Double> heightChannel = new KeyframeChannel<>("height", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> widthChannel = new KeyframeChannel<>("width", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> smoothnessChannel = new KeyframeChannel<>("smoothness", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Color> colorChannel = new KeyframeChannel<>("color", KeyframeFactories.COLOR);
    public final KeyframeChannel<Double> rotationChannel = new KeyframeChannel<>("rotation", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> zoomChannel = new KeyframeChannel<>("zoom", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> offsetXChannel = new KeyframeChannel<>("offsetX", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> offsetYChannel = new KeyframeChannel<>("offsetY", KeyframeFactories.DOUBLE);

    public final KeyframeChannel[] channels;

    private LetterboxEffect effect = new LetterboxEffect();

    public static List<LetterboxEffect> getEffects(ClipContext context)
    {
        return context.clipData.get("letterboxEffects", ArrayList::new);
    }

    public LetterboxClip()
    {
        this.channels = new KeyframeChannel[] {
            this.heightChannel,
            this.widthChannel,
            this.smoothnessChannel,
            this.colorChannel,
            this.rotationChannel,
            this.zoomChannel,
            this.offsetXChannel,
            this.offsetYChannel,
        };

        this.add(this.height);
        this.add(this.width);
        this.add(this.smoothness);
        this.add(this.color);

        for (KeyframeChannel channel : this.channels)
        {
            this.add(channel);
        }
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        float t = context.relativeTick + context.transition;
        float factor = this.envelope.factorEnabled(this.duration.get(), t);

        /* Height defaults to static property value (0.12) if keyframe track is empty */
        float barH = this.heightChannel.isEmpty()
            ? (float) (double) this.height.get()
            : (float) (double) this.heightChannel.interpolate(t);

        if (barH > 0F)
        {
            float sz = barH * 0.25F;
            float barW = this.widthChannel.isEmpty()
                ? (float) (double) this.width.get()
                : (float) (double) this.widthChannel.interpolate(t);

            float smooth = (this.smoothnessChannel.isEmpty()
                ? (float) (double) this.smoothness.get()
                : (float) (double) this.smoothnessChannel.interpolate(t)) * 0.25F;

            Color col = this.colorChannel.isEmpty()
                ? this.color.get()
                : this.colorChannel.interpolate(t, this.color.get());

            float rot = this.rotationChannel.isEmpty() ? 0F : (float) (double) this.rotationChannel.interpolate(t);
            float zm = this.zoomChannel.isEmpty() ? 1F : (float) (double) this.zoomChannel.interpolate(t);
            float offX = this.offsetXChannel.isEmpty() ? 0F : (float) (double) this.offsetXChannel.interpolate(t);
            float offY = this.offsetYChannel.isEmpty() ? 0F : (float) (double) this.offsetYChannel.interpolate(t);

            this.effect.size = Math.max(0F, sz * factor);
            this.effect.width = barW;
            this.effect.smoothness = smooth;
            this.effect.color = Colors.setA(col.getARGBColor(), 1F);
            this.effect.rotation = rot;
            this.effect.zoom = Math.max(0.01F, zm);
            this.effect.offsetX = offX;
            this.effect.offsetY = offY;
            this.effect.renderOrder = context.count;
            this.effect.layer = this.layer.get();

            getEffects(context).add(this.effect);
        }
    }

    @Override
    protected Clip create()
    {
        return new LetterboxClip();
    }
}
