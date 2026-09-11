package gbeic.bbsplusplus.performance;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.BBSModClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * 集中管理"时机转移"型性能优化：把原本在用户打开面板时一次性完成的重活，
 * 拆分到进入世界后的空闲帧里逐步完成，避免瞬间卡顿。
 *
 * <p>当前包含：
 * <ul>
 *   <li>P0 —— Dashboard 单例预热：进存档后延迟若干 tick 主动 {@code getDashboard()}，
 *       把首次构造 8 个面板的开销从"右键模型方块时"转移到空闲期。</li>
 * </ul>
 * </p>
 */
public final class PerformanceOptimizationManager
{
    private static boolean prewarmScheduled = false;
    private static int prewarmCountdown = -1;

    /** 进入世界后延迟多少 tick 再预热 Dashboard（默认 100 tick ≈ 5 秒）。 */
    private static final int PREWARM_DELAY_TICKS = 100;

    private PerformanceOptimizationManager() {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ProgressiveVAOBaker.tick();

            if (client.player == null)
            {
                prewarmScheduled = false;
                prewarmCountdown = -1;
                return;
            }

            // 已经打开过 Dashboard（用户主动开过 / 快捷键触发）就不再预热
            if (BBSModClient.getDashboardIfCreated() != null)
            {
                prewarmScheduled = false;
                prewarmCountdown = -1;
                return;
            }

            if (!prewarmScheduled)
            {
                prewarmScheduled = true;
                prewarmCountdown = PREWARM_DELAY_TICKS;
                return;
            }

            if (prewarmCountdown > 0)
            {
                prewarmCountdown--;
                return;
            }

            // 到点了，在主线程惰性创建 Dashboard。
            // 此时玩家已进入世界数秒，远离了"刚进存档"的加载尖峰，
            // 用户即使立刻打开模型方块，Dashboard 也已经构造完成。
            try
            {
                long t0 = System.currentTimeMillis();
                BBSModClient.getDashboard();
                long dt = System.currentTimeMillis() - t0;
                if (dt > 200)
                {
                    BBSPlusPlusMod.LOGGER.info("[性能] Dashboard 预热完成，耗时 {} ms", dt);
                }
            }
            catch (Throwable t)
            {
                BBSPlusPlusMod.LOGGER.warn("[性能] Dashboard 预热失败（不影响正常打开）", t);
            }

            prewarmScheduled = false;
            prewarmCountdown = -1;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            prewarmScheduled = false;
            prewarmCountdown = -1;
        });
    }
}
