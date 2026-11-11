package gbeic.bbsplusplus.compat;

import gbeic.bbsplusplus.client.screen.ScreenEffectRenderer;
import gbeic.bbsplusplus.clips.HotbarClip;
import gbeic.bbsplusplus.clips.HotbarState;
import gbeic.bbsplusplus.clips.screen.ColorClip;
import gbeic.bbsplusplus.ui.film.UIHotbarRenderer;
import mchorse.bbs_mod.api.client.events.RegisterFrameOverlaysEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * 1.21.11 把屏幕叠加层挂到 BBS 的 FrameOverlays 上。
 *
 * <p>1.20 那边是 {@code BBSRenderingMixin} 在 {@code onWorldRenderEnd} 的 RETURN 处硬插，
 * 因为它要覆盖「录制中」这条上游没处理的分支。1.21.11 的上游已经把录制分支也走
 * {@code FrameOverlays.render(...)}，而且外面套了 {@code ImmediateGui.begin()/end()}
 * ——那是 1.21.6 两阶段 GUI 下把图元立刻合进 framebuffer 的唯一办法。所以这里不再写
 * mixin，直接注册一个 overlay family，跟着上游的时机走。</p>
 *
 * <p>画的东西与 1.20 一致：先热键栏，再屏幕调色/叠加层。</p>
 */
public final class FrameOverlayCompat
{
    private FrameOverlayCompat()
    {}

    public static void register(RegisterFrameOverlaysEvent event)
    {
        event.register(FrameOverlayCompat::render);
    }

    private static void render(net.minecraft.client.util.math.MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        if (context == null)
        {
            return;
        }

        List<HotbarState> hotbars = HotbarClip.getHotbars(context);
        boolean hasEffects = !ColorClip.getEffects(context).isEmpty();

        if (hotbars.isEmpty() && !hasEffects)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        /* 顺序必须是「先屏幕效果、后热键栏」。
         *
         * 1.20 那边 Batcher2D 是立即画，ScreenEffectRenderer 抓 framebuffer 时热键栏还没落屏，
         * 所以 HUD 天然不参与调色；1.21.11 的 batcher 只记录、到 ImmediateGui.end() 才按记录
         * 顺序合成，于是记录顺序就等价于 1.20 的绘制顺序：调色的全屏 blit 必须先记录，
         * 热键栏后记录，才能压在调色结果之上。写反了会被那张全屏四边形盖掉。 */
        if (hasEffects)
        {
            ScreenEffectRenderer.render(batcher, context, width, height);
        }

        if (!hotbars.isEmpty())
        {
            UIHotbarRenderer.renderHotbars(stack, batcher, hotbars, 0, 0, width, height);
        }
    }
}
