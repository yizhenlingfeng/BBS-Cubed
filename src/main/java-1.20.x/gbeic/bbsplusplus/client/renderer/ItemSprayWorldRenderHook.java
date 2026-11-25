package gbeic.bbsplusplus.client.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * 1.20.x 的世界渲染事件注册。
 *
 * <p>该版本的渲染上下文直接提供矩阵栈、相机、视锥与刻插值，取值后交给共享的
 * {@link ItemSprayGlobalSystem} 绘制。</p>
 */
final class ItemSprayWorldRenderHook
{
    private ItemSprayWorldRenderHook() {}

    static void registerStart(Runnable onStart)
    {
        WorldRenderEvents.START.register((ctx) -> onStart.run());
    }

    static void registerAfterEntities()
    {
        WorldRenderEvents.AFTER_ENTITIES.register((ctx) ->
        {
            if (ItemSprayGlobalSystem.isShadowLikePass()) return;
            if (ItemSprayGlobalSystem.GLOBAL_ITEMS.isEmpty() && ItemSprayGlobalSystem.DETERMINISTIC_WORLD_ITEMS.isEmpty()) return;

            ItemSprayGlobalSystem.renderGlobalItems(ctx.matrixStack(), ctx.camera().getPos(), ctx.frustum(), ctx.tickDelta(), ctx.world());
        });
    }
}
