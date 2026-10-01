package bbslezy.camera.clips.screen;

import bbslezy.utils.keyframes.factories.LensRadiusSettingsKeyframeFactory;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class CinematicClip extends CameraClip
{
    public static final double DEFAULT_ABERRATION_ANGLE = 0D;
    public static final double DEFAULT_ABERRATION_DIRECTIONAL = 0D;
    public static final double DEFAULT_ABERRATION_RADIUS = 1D;
    public static final double DEFAULT_ABERRATION_HARDNESS = 1D;
    public static final double DEFAULT_ABERRATION_BALANCE = 0D;
    public static final double DEFAULT_ABERRATION_CENTER_X = 0.5D;
    public static final double DEFAULT_ABERRATION_CENTER_Y = 0.5D;
    public static final double DEFAULT_ABERRATION_GREEN = 0D;
    public static final double DEFAULT_ABERRATION_SPECTRUM = 0D;

    public static final float DEFAULT_LENS_RADIUS = 1F;
    public static final LensRadiusSettings DEFAULT_LENS_RADIUS_SETTINGS = new LensRadiusSettings(DEFAULT_LENS_RADIUS, DEFAULT_LENS_RADIUS);
    public static final double DEFAULT_LENS_HARDNESS = 1D;
    public static final double DEFAULT_LENS_SHARPEN = 1D;
    public static final double DEFAULT_LENS_DISTANCE_FACTOR = 0D;

    /* Vintage & Retro */
    public final KeyframeChannel<Double> vintage = new KeyframeChannel<>("vintage", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> vhs = new KeyframeChannel<>("vhs", KeyframeFactories.DOUBLE);

    /* Film Grain */
    public final KeyframeChannel<Double> grainStrength = new KeyframeChannel<>("grain_strength", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> grainSize = new KeyframeChannel<>("grain_size", KeyframeFactories.DOUBLE);

    /* Lens & Optics */
    public final KeyframeChannel<Double> lensDistortion = new KeyframeChannel<>("lensDistortion", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> lensDistanceFactor = new KeyframeChannel<>("lens_distance_factor", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<LensRadiusSettings> lensRadius = new KeyframeChannel<>("lens_radius", LensRadiusSettingsKeyframeFactory.INSTANCE);
    public final KeyframeChannel<Double> lensHardness = new KeyframeChannel<>("lens_hardness", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> lensSharpen = new KeyframeChannel<>("lens_sharpen", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> radialBlur = new KeyframeChannel<>("radialBlur", KeyframeFactories.DOUBLE);

    /* Chromatic Aberration */
    public final KeyframeChannel<Double> aberration = new KeyframeChannel<>("aberration", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationAngle = new KeyframeChannel<>("aberration_angle", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationDirectional = new KeyframeChannel<>("aberration_directional", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationRadius = new KeyframeChannel<>("aberration_radius", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationHardness = new KeyframeChannel<>("aberration_hardness", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationBalance = new KeyframeChannel<>("aberration_balance", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationCenterX = new KeyframeChannel<>("aberration_center_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationCenterY = new KeyframeChannel<>("aberration_center_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationGreen = new KeyframeChannel<>("aberration_green", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> aberrationSpectrum = new KeyframeChannel<>("aberration_spectrum", KeyframeFactories.DOUBLE);

    /* Atmosphere & Environment */
    public final KeyframeChannel<Double> rain = new KeyframeChannel<>("rain", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> dust = new KeyframeChannel<>("dust", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> lightLeak = new KeyframeChannel<>("lightLeak", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> heatStrength = new KeyframeChannel<>("heat_strength", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> heatSpeed = new KeyframeChannel<>("heat_speed", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> heatScale = new KeyframeChannel<>("heat_scale", KeyframeFactories.DOUBLE);

    public final KeyframeChannel[] channels;

    private ColorEffect colorEffect = new ColorEffect();
    private GrainEffect grainEffect = new GrainEffect();

    public static List<GrainEffect> getGrainEffects(ClipContext context)
    {
        return context.clipData.get("grainEffects", ArrayList::new);
    }

    public CinematicClip()
    {
        this.channels = new KeyframeChannel[] {
            /* Vintage & Retro */
            this.vintage,
            this.vhs,

            /* Film Grain */
            this.grainStrength,
            this.grainSize,

            /* Lens & Optics */
            this.lensDistortion,
            this.lensDistanceFactor,
            this.lensRadius,
            this.lensHardness,
            this.lensSharpen,
            this.radialBlur,

            /* Aberration */
            this.aberration,
            this.aberrationAngle,
            this.aberrationDirectional,
            this.aberrationRadius,
            this.aberrationHardness,
            this.aberrationBalance,
            this.aberrationCenterX,
            this.aberrationCenterY,
            this.aberrationGreen,
            this.aberrationSpectrum,

            /* Atmosphere */
            this.rain,
            this.dust,
            this.lightLeak,
            this.heatStrength,
            this.heatSpeed,
            this.heatScale,
        };

        for (KeyframeChannel channel : this.channels)
        {
            this.add(channel);
        }
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);
        this.migrateLegacyDoubleLensRadius();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void migrateLegacyDoubleLensRadius()
    {
        IKeyframeFactory<?> factory = this.lensRadius.getFactory();

        if (factory != KeyframeFactories.DOUBLE
            && factory != KeyframeFactories.FLOAT
            && factory != KeyframeFactories.INTEGER)
        {
            return;
        }

        KeyframeChannel legacy = (KeyframeChannel) (Object) this.lensRadius;
        List<Float> ticks = new ArrayList<>();
        List<LensRadiusSettings> values = new ArrayList<>();

        for (Object object : legacy.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) object;
            Object raw = keyframe.getValue();
            float radius = raw instanceof Number ? Math.max(0F, ((Number) raw).floatValue()) : DEFAULT_LENS_RADIUS;

            ticks.add(keyframe.getTick());
            values.add(LensRadiusSettings.ofUniform(radius));
        }

        this.lensRadius.removeAll();

        for (int i = 0; i < ticks.size(); i++)
        {
            this.lensRadius.insert(ticks.get(i), values.get(i));
        }
    }

    private static float interpolateOrDefault(KeyframeChannel<Double> channel, float tick, double fallback)
    {
        return channel.isEmpty() ? (float) fallback : (float) (double) channel.interpolate(tick);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        float t = context.relativeTick + context.transition;
        float factor = this.envelope.factorEnabled(this.duration.get(), t);

        this.colorEffect.reset();

        /* 1. Film Grain */
        float gStr = (this.grainStrength.isEmpty() ? 0F : (float) (double) this.grainStrength.interpolate(t)) * 0.25F;

        if (gStr > 0F)
        {
            float gSize = this.grainSize.isEmpty() ? 1F : (float) (double) this.grainSize.interpolate(t) * 0.25F;

            this.grainEffect.strength = gStr * factor;
            this.grainEffect.size = Math.max(0.25F, gSize);
            this.grainEffect.renderOrder = context.count;
            this.grainEffect.layer = this.layer.get();

            getGrainEffects(context).add(this.grainEffect);
        }

        /* 2. Cinematic Shader Effects */
        float ab = (this.aberration.isEmpty() ? 0F : (float) (double) this.aberration.interpolate(t)) * 0.25F;
        float abAngle = interpolateOrDefault(this.aberrationAngle, t, DEFAULT_ABERRATION_ANGLE);
        float abDirectional = interpolateOrDefault(this.aberrationDirectional, t, DEFAULT_ABERRATION_DIRECTIONAL);
        float abRadius = interpolateOrDefault(this.aberrationRadius, t, DEFAULT_ABERRATION_RADIUS);
        float abHardness = interpolateOrDefault(this.aberrationHardness, t, DEFAULT_ABERRATION_HARDNESS);
        float abBalance = interpolateOrDefault(this.aberrationBalance, t, DEFAULT_ABERRATION_BALANCE);
        float abCenterX = interpolateOrDefault(this.aberrationCenterX, t, DEFAULT_ABERRATION_CENTER_X);
        float abCenterY = interpolateOrDefault(this.aberrationCenterY, t, DEFAULT_ABERRATION_CENTER_Y);
        float abGreen = interpolateOrDefault(this.aberrationGreen, t, DEFAULT_ABERRATION_GREEN);
        float abSpectrum = interpolateOrDefault(this.aberrationSpectrum, t, DEFAULT_ABERRATION_SPECTRUM);
        float vh = (this.vhs.isEmpty() ? 0F : (float) (double) this.vhs.interpolate(t)) * 0.25F;
        float ld = (this.lensDistortion.isEmpty() ? 0F : (float) (double) this.lensDistortion.interpolate(t)) * 0.25F;
        float ldf = this.lensDistanceFactor.isEmpty() ? (float) DEFAULT_LENS_DISTANCE_FACTOR : (float) (double) this.lensDistanceFactor.interpolate(t);
        LensRadiusSettings radiusSettings = this.lensRadius.isEmpty()
            ? DEFAULT_LENS_RADIUS_SETTINGS
            : this.lensRadius.interpolate(t);
        float lh = this.lensHardness.isEmpty() ? (float) DEFAULT_LENS_HARDNESS : (float) (double) this.lensHardness.interpolate(t);
        float ls = this.lensSharpen.isEmpty() ? (float) DEFAULT_LENS_SHARPEN : (float) (double) this.lensSharpen.interpolate(t);
        float vt = (this.vintage.isEmpty() ? 0F : (float) (double) this.vintage.interpolate(t)) * 0.25F;
        float rb = (this.radialBlur.isEmpty() ? 0F : (float) (double) this.radialBlur.interpolate(t)) * 0.25F;
        float rn = (this.rain.isEmpty() ? 0F : (float) (double) this.rain.interpolate(t)) * 0.25F;
        float ds = (this.dust.isEmpty() ? 0F : (float) (double) this.dust.interpolate(t)) * 0.25F;
        float ll = (this.lightLeak.isEmpty() ? 0F : (float) (double) this.lightLeak.interpolate(t)) * 0.25F;
        float hs = (this.heatStrength.isEmpty() ? 0F : (float) (double) this.heatStrength.interpolate(t)) * 0.25F;
        float hsp = (this.heatSpeed.isEmpty() ? 1F : (float) (double) this.heatSpeed.interpolate(t)) * 0.25F;
        float hsc = (this.heatScale.isEmpty() ? 1F : (float) (double) this.heatScale.interpolate(t)) * 0.25F;

        float lens = ld * factor;
        float radiusX = Math.max(0F, radiusSettings == null ? DEFAULT_LENS_RADIUS : radiusSettings.x);
        float radiusY = Math.max(0F, radiusSettings == null ? DEFAULT_LENS_RADIUS : radiusSettings.y);
        float hardness = Math.max(0F, Math.min(1F, lh));

        if (lens > 1.0e-6F && Math.abs(ldf) > 1.0e-6F)
        {
            float distance = LensDistortionOverscan.framingDistanceOffset(lens, radiusX, radiusY, hardness) * ldf;

            if (Math.abs(distance) > 1.0e-6F)
            {
                Vector3f rotation = Matrices.rotation(
                    MathUtils.toRad(position.angle.pitch),
                    MathUtils.toRad(-position.angle.yaw - 180)
                );

                position.point.x += rotation.x * distance;
                position.point.y += rotation.y * distance;
                position.point.z += rotation.z * distance;
            }
        }

        if (ab != 0F || vh != 0F || ld != 0F || vt != 0F || rb != 0F || rn != 0F || ds != 0F || ll != 0F || hs != 0F)
        {
            this.colorEffect.hasCinematic = true;
            this.colorEffect.aberration = ab * factor;
            this.colorEffect.aberrationAngle = abAngle;
            this.colorEffect.aberrationDirectional = MathUtils.clamp(abDirectional, 0F, 1F);
            this.colorEffect.aberrationRadius = Math.max(0F, abRadius);
            this.colorEffect.aberrationHardness = MathUtils.clamp(abHardness, 0F, 1F);
            this.colorEffect.aberrationBalance = MathUtils.clamp(abBalance, -1F, 1F);
            this.colorEffect.aberrationCenterX = MathUtils.clamp(abCenterX, 0F, 1F);
            this.colorEffect.aberrationCenterY = MathUtils.clamp(abCenterY, 0F, 1F);
            this.colorEffect.aberrationGreen = Math.max(0F, abGreen);
            this.colorEffect.aberrationSpectrum = MathUtils.clamp(abSpectrum, 0F, 1F);
            this.colorEffect.vhs = vh * factor;
            this.colorEffect.lensDistortion = lens;
            this.colorEffect.lensRadiusX = radiusX;
            this.colorEffect.lensRadiusY = radiusY;
            this.colorEffect.lensHardness = hardness;
            this.colorEffect.lensSharpen = Math.max(0F, ls) * factor;
            this.colorEffect.vintage = vt * factor;
            this.colorEffect.radialBlur = rb * factor;
            this.colorEffect.rain = rn * factor;
            this.colorEffect.dust = ds * factor;
            this.colorEffect.lightLeak = ll * factor;
            this.colorEffect.heatStrength = hs * factor;
            this.colorEffect.heatSpeed = hsp * factor;
            this.colorEffect.heatScale = hsc * factor;
            this.colorEffect.time = t / 20.0F;
            this.colorEffect.layer = this.layer.get();
            this.colorEffect.renderOrder = context.count;

            ColorClip.getEffects(context).add(this.colorEffect);
        }
    }

    @Override
    protected Clip create()
    {
        return new CinematicClip();
    }
}
