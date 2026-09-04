package gbeic.bbsplusplus.client.screen;

import gbeic.bbsplusplus.api.FormColorProvider;
import gbeic.bbsplusplus.api.TextureGradeProvider;
import gbeic.bbsplusplus.mixin.client.ParticleColorAccessor;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.particle.Particle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/** Transfers one vanilla-particle form's evaluated grade to particles created during its tick. */
public final class VanillaParticleGradeContext
{
    private static final ThreadLocal<Deque<State>> STATES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Particle, State> PARTICLE_STATES = new WeakHashMap<>();

    private VanillaParticleGradeContext()
    {}

    public static void push(VanillaParticleForm form)
    {
        Color color = Color.white();
        Color tint = new Color(1F, 1F, 1F, 0F);
        float whiten = 0F;

        if (form instanceof FormColorProvider provider && provider.bbspp_cml$getColor() != null)
        {
            color = provider.bbspp_cml$getColor().get().copy();
        }

        if (form instanceof TextureGradeProvider provider)
        {
            if (provider.bbspp_cml$getTextureTint() != null)
            {
                tint = provider.bbspp_cml$getTextureTint().get().copy();
            }

            if (provider.bbspp_cml$getTextureWhiten() != null)
            {
                whiten = provider.bbspp_cml$getTextureWhiten().get();
            }
        }

        STATES.get().push(new State(color, tint, clamp(whiten)));
    }

    public static void pop()
    {
        Deque<State> states = STATES.get();

        if (!states.isEmpty())
        {
            states.pop();
        }

        if (states.isEmpty())
        {
            STATES.remove();
        }
    }

    public static void capture(Particle particle)
    {
        Deque<State> states = STATES.get();

        if (!states.isEmpty())
        {
            PARTICLE_STATES.put(particle, states.peek());
        }
    }

    public static void render(Particle particle, VertexConsumer vertices, Camera camera, float tickDelta)
    {
        State state = PARTICLE_STATES.get(particle);

        if (state == null || !(particle instanceof ParticleColorAccessor access))
        {
            particle.buildGeometry(vertices, camera, tickDelta);

            return;
        }

        float red = access.bbspp_cml$getRed();
        float green = access.bbspp_cml$getGreen();
        float blue = access.bbspp_cml$getBlue();
        float alpha = access.bbspp_cml$getAlpha();

        apply(particle, access, state, red, green, blue, alpha);

        try
        {
            particle.buildGeometry(vertices, camera, tickDelta);
        }
        finally
        {
            particle.setColor(red, green, blue);
            access.bbspp_cml$setAlpha(alpha);
        }
    }

    private static void apply(
        Particle particle,
        ParticleColorAccessor access,
        State state,
        float red,
        float green,
        float blue,
        float alpha
    )
    {
        float sourceMax = Math.max(red, Math.max(green, blue));
        float sourceMin = Math.min(red, Math.min(green, blue));
        float sourceLightness = (sourceMax + sourceMin) * 0.5F;
        float tintMax = Math.max(state.tint.r, Math.max(state.tint.g, state.tint.b));
        float tintMin = Math.min(state.tint.r, Math.min(state.tint.g, state.tint.b));
        float tintDelta = tintMax - tintMin;
        float tintLightness = (tintMax + tintMin) * 0.5F;
        float denominator = 1F - Math.abs(2F * tintLightness - 1F);
        float saturation = tintDelta < 0.00001F || denominator < 0.00001F ? 0F : tintDelta / denominator;
        float hue = hue(state.tint, tintMax, tintDelta);
        float chroma = (1F - Math.abs(2F * sourceLightness - 1F)) * saturation;
        float x = chroma * (1F - Math.abs((hue * 6F) % 2F - 1F));
        float m = sourceLightness - chroma * 0.5F;
        float recoloredRed;
        float recoloredGreen;
        float recoloredBlue;

        if (hue < 1F / 6F)
        {
            recoloredRed = chroma;
            recoloredGreen = x;
            recoloredBlue = 0F;
        }
        else if (hue < 2F / 6F)
        {
            recoloredRed = x;
            recoloredGreen = chroma;
            recoloredBlue = 0F;
        }
        else if (hue < 3F / 6F)
        {
            recoloredRed = 0F;
            recoloredGreen = chroma;
            recoloredBlue = x;
        }
        else if (hue < 4F / 6F)
        {
            recoloredRed = 0F;
            recoloredGreen = x;
            recoloredBlue = chroma;
        }
        else if (hue < 5F / 6F)
        {
            recoloredRed = x;
            recoloredGreen = 0F;
            recoloredBlue = chroma;
        }
        else
        {
            recoloredRed = chroma;
            recoloredGreen = 0F;
            recoloredBlue = x;
        }

        float tintStrength = clamp(state.tint.a);

        red = mix(red, recoloredRed + m, tintStrength) * state.color.r;
        green = mix(green, recoloredGreen + m, tintStrength) * state.color.g;
        blue = mix(blue, recoloredBlue + m, tintStrength) * state.color.b;
        red = mix(red, 1F, state.whiten);
        green = mix(green, 1F, state.whiten);
        blue = mix(blue, 1F, state.whiten);

        particle.setColor(clamp(red), clamp(green), clamp(blue));
        access.bbspp_cml$setAlpha(clamp(alpha * state.color.a));
    }

    private static float hue(Color tint, float max, float delta)
    {
        if (delta <= 0.00001F)
        {
            return 0F;
        }

        float hue;

        if (max == tint.r)
        {
            hue = ((tint.g - tint.b) / delta) % 6F;
        }
        else if (max == tint.g)
        {
            hue = (tint.b - tint.r) / delta + 2F;
        }
        else
        {
            hue = (tint.r - tint.g) / delta + 4F;
        }

        hue /= 6F;

        return hue < 0F ? hue + 1F : hue;
    }

    private static float mix(float a, float b, float factor)
    {
        return a + (b - a) * factor;
    }

    private static float clamp(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }

    private record State(Color color, Color tint, float whiten)
    {}
}
