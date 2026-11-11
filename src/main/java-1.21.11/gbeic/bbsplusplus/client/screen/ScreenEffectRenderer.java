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
 * <p>1.21.11 版与 1.20 版的差别只在「调色那一步怎么画」：这里交给
 * {@link ScreenGradeRenderer}，它把调色结果渲进离屏纹理、再登记一次 blit；
 * 旧的 {@code ColorGradeRenderer} 是立即模式 GL，在 1.21.11 的 GUI 两阶段合成下
 * 既编不过也画不出来。</p>
 *
 * <p>像素顺序与 CML 保持不变：调色/暗角/畸变与全部 cinematic 通道只作用于
 * 已经渲染好的 3D 画面（此刻 hotbar 还只是 batcher 里待合成的记录，不会进这张离屏
 * 纹理），overlay 色块随后入缓冲、由调用方统一合成 —— 即 HUD 与 overlay 不参与调色。</p>
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

        /* 调色：离屏 pass + 已记录的 blit（这一步也就把 3D 画面盖回屏幕上） */
        ScreenGradeRenderer.apply(batcher, effects);

        /* Overlay 色块：走 GUI 记录路径，合成时压在调色结果之上 */
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
