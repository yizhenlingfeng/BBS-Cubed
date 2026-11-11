package gbeic.bbsplusplus.client.screen;

import gbeic.bbsplusplus.clips.screen.ColorClip;
import gbeic.bbsplusplus.clips.screen.ColorEffect;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.List;

/**
 * 屏幕效果渲染器 —— 消费 {@link ColorClip#getEffects(ClipContext)} 池中由
 * {@code ColorClip} / {@code CinematicClip} 注入的 {@link ColorEffect}。
 *
 * <p>调色/暗角/畸变与全部 cinematic 通道经 {@link ColorGradeRenderer}
 * 全屏 shader pass 处理（CML 同款 GLSL），替代早期"Batcher2D 色块近似"
 * —— 色差/VHS/镜头畸变/径向模糊等逐像素效果只有 shader 能正确呈现。</p>
 *
 * <p>像素顺序对齐 CML：shader pass 先处理 3D 画面（此时 hotbar 等 2D 图元仍在
 * batcher 缓冲中未提交），overlay 色块随后入缓冲、由调用方统一 flush —— 即
 * HUD 与 overlay 不参与调色，与 CML 的实际提交顺序一致。</p>
 *
 * <p>渲染后清空 effect 列表，防跨帧累积（与 CML 一致）。</p>
 */
public class ScreenEffectRenderer
{
    public static void render(Batcher2D batcher, ClipContext context, int screenW, int screenH)
    {
        List<ColorEffect> effects = ColorClip.getEffects(context);

        if (effects.isEmpty())
        {
            return;
        }

        /* Vignette / color grade / cinematic via shader pass */
        ColorGradeRenderer.apply(effects);

        /* Overlay color pass（缓冲，调用方 flush 后盖在效果之上） */
        for (ColorEffect effect : effects)
        {
            if (effect.hasOverlay)
            {
                batcher.box(0, 0, screenW, screenH, effect.overlayColor);
            }
        }

        effects.clear();
    }
}
