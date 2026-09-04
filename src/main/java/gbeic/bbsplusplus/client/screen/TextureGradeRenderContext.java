package gbeic.bbsplusplus.client.screen;

import gbeic.bbsplusplus.api.TextureGradeProvider;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.ShaderProgram;

import java.util.ArrayDeque;
import java.util.Deque;

/** Keeps nested form renders from leaking texture-grade state into body parts. */
public final class TextureGradeRenderContext
{
    private static final ThreadLocal<Deque<State>> STATES = ThreadLocal.withInitial(ArrayDeque::new);

    private TextureGradeRenderContext()
    {}

    public static void push(Form form, boolean picking)
    {
        STATES.get().push(new State(form, picking));
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

    public static ShaderProgram select(ShaderProgram original)
    {
        Deque<State> states = STATES.get();

        if (states.isEmpty())
        {
            return original;
        }

        State state = states.peek();

        if (state.picking || !(state.form instanceof TextureGradeProvider provider))
        {
            return original;
        }

        return select(original, provider);
    }

    public static ShaderProgram select(ShaderProgram original, Form form)
    {
        if (!(form instanceof TextureGradeProvider provider))
        {
            return original;
        }

        Deque<State> states = STATES.get();

        if (!states.isEmpty() && states.peek().picking)
        {
            return original;
        }

        return select(original, provider);
    }

    private static ShaderProgram select(ShaderProgram original, TextureGradeProvider provider)
    {
        /* 纹理调色/白化需在 Iris 光影的世界渲染（影片播放/世界中放置的 form）下同样生效，
         * 不再因光影跳过 —— 否则修改仅编辑页面（UI 渲染）可见。 */
        if (original == null)
        {
            return original;
        }

        ValueColor tintProperty = provider.bbspp_cml$getTextureTint();
        ValueFloat whitenProperty = provider.bbspp_cml$getTextureWhiten();
        Color tint = tintProperty == null ? null : tintProperty.get();
        float whiten = whitenProperty == null ? 0F : whitenProperty.get();

        if ((tint == null || tint.a <= 0.0001F) && whiten <= 0.0001F)
        {
            return original;
        }

        return ModelTextureGradeShader.select(
            original,
            tint == null ? new Color(1F, 1F, 1F, 0F) : tint,
            whiten
        );
    }

    private record State(Form form, boolean picking)
    {}
}
