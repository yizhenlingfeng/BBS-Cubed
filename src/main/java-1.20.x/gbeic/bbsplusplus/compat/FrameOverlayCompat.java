package gbeic.bbsplusplus.compat;

import mchorse.bbs_mod.api.client.events.RegisterFrameOverlaysEvent;

/**
 * 1.20.1 不使用 FrameOverlays 事件，这里保持空实现。
 *
 * <p>1.20 的热键栏与屏幕调色由 {@code BBSRenderingMixin} 在
 * {@code BBSRendering.onWorldRenderEnd} 的 RETURN 处统一处理，它额外覆盖了上游
 * {@code FrameOverlays} 没有覆盖的「录制中」分支。如果这里也注册一个 overlay，
 * 播放与编辑器预览两条路径会在 1.20.1 上被画两遍。</p>
 *
 * <p>1.21.11 的上游已经把录制分支并进 {@code FrameOverlays}，那边才改成注册事件
 * （见 {@code src/main/java-1.21.11/.../compat/FrameOverlayCompat.java}）。</p>
 */
public final class FrameOverlayCompat
{
    private FrameOverlayCompat()
    {}

    public static void register(RegisterFrameOverlaysEvent event)
    {}
}
