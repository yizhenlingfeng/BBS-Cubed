package gbeic.bbsplusplus.client.particle;

import gbeic.bbsplusplus.api.BBSPPFormValues;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.particle.Particle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.21.11 版原版粒子着色上下文：把一个 {@link VanillaParticleForm} 在 tick 时算出的
 * 颜色/调色/白化，传递给这次 tick 里由它发射出来的原版粒子。
 *
 * <p>与 1.20 版 {@code client.screen.VanillaParticleGradeContext} 的区别：
 * 1.21.11 的 {@code Particle} 已经没有 {@code red/green/blue/alpha} 字段，也没有
 * {@code buildGeometry}；颜色字段搬到了 {@code BillboardParticle}，读取点只剩
 * {@code BillboardParticle.renderVertex}。因此这里只负责「记住状态 + 算颜色」，
 * 真正写字段的动作由 {@code BillboardParticleGradeMixin121} 在 render 头尾做。</p>
 */
public final class VanillaParticleGrade121
{
    private static final ThreadLocal<Deque<State>> STATES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Particle, State> PARTICLE_STATES = new WeakHashMap<>();

    private VanillaParticleGrade121()
    {}

    /** 开始一次 tick：记下这个 form 当前的颜色/调色/白化。 */
    public static void push(VanillaParticleForm form)
    {
        Color color = Color.white();
        Color tint = new Color(1F, 1F, 1F, 0F);
        float whiten = 0F;

        ValueColor colorValue = BBSPPFormValues.getColor(form);

        if (colorValue != null && colorValue.get() != null)
        {
            color = colorValue.get().copy();
        }

        ValueColor tintProperty = BBSPPFormValues.getTextureTint(form);
        ValueFloat whitenProperty = BBSPPFormValues.getTextureWhiten(form);

        if (tintProperty != null && tintProperty.get() != null)
        {
            tint = tintProperty.get().copy();
        }

        if (whitenProperty != null)
        {
            whiten = whitenProperty.get();
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

    /** 粒子被加入世界时调用；只有在某个 form 的 tick 作用域内才记录。 */
    public static void capture(Particle particle)
    {
        Deque<State> states = STATES.get();

        if (!states.isEmpty())
        {
            PARTICLE_STATES.put(particle, states.peek());
        }
    }

    /** 取该粒子在创建时对应的着色状态；没有则返回 null（按原样绘制）。 */
    public static State stateOf(Particle particle)
    {
        return PARTICLE_STATES.get(particle);
    }

    /**
     * 按状态算出新的颜色。
     *
     * @return {@code {red, green, blue, alpha}}，均已 clamp 到 0~1
     */
    public static float[] apply(State state, float red, float green, float blue, float alpha)
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

        return new float[] {clamp(red), clamp(green), clamp(blue), clamp(alpha * state.color.a)};
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

    public record State(Color color, Color tint, float whiten)
    {}
}
