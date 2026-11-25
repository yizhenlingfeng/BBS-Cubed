package gbeic.bbsplusplus.client.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;

/**
 * 1.21.11 的世界渲染事件注册。
 *
 * <p>该版本的渲染上下文不再直接暴露相机、视锥、刻插值与世界，改从 {@link MinecraftClient}
 * 侧读取（同一渲染线程、同一帧状态，语义等价），矩阵栈仍由上下文提供。</p>
 */
final class ItemSprayWorldRenderHook
{
    private ItemSprayWorldRenderHook() {}

    static void registerStart(Runnable onStart)
    {
        WorldRenderEvents.START_MAIN.register((ctx) -> onStart.run());
    }

    static void registerAfterEntities()
    {
        WorldRenderEvents.AFTER_ENTITIES.register((ctx) ->
        {
            if (ItemSprayGlobalSystem.isShadowLikePass()) return;
            if (ItemSprayGlobalSystem.GLOBAL_ITEMS.isEmpty() && ItemSprayGlobalSystem.DETERMINISTIC_WORLD_ITEMS.isEmpty()) return;

            MinecraftClient client = MinecraftClient.getInstance();

            if (client.world == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) return;

            ItemSprayGlobalSystem.renderGlobalItems(ctx.matrices(), client.gameRenderer.getCamera().getCameraPos(), client.worldRenderer.getCapturedFrustum(), client.getRenderTickCounter().getTickProgress(true), client.world);
        });
    }
}
