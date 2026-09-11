package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P5：切换模型方块的 "Global" 开关时，不再调用整世界 {@code WorldRenderer.reload()}。
 *
 * <p>原版在 global toggle 回调里直接 {@code mc.worldRenderer.reload()}，
 * 这会重建所有区块的渲染缓存（数秒卡顿）。这里拦截整个回调：先照常 setGlobal，
 * 然后只对当前方块位置 updateListeners，不重建整个世界。</p>
 */
@Mixin(value = UIModelBlockPanel.class, remap = true)
public abstract class UIModelBlockPanelGlobalMixin
{
    @Shadow private ModelBlockEntity modelBlock;

    @Inject(
        method = "lambda$new$13",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void bbspp$lightweightGlobalRefresh(UIToggle toggle, CallbackInfo ci)
    {
        if (this.modelBlock == null)
        {
            return;
        }

        // 照常设置 global 属性
        this.modelBlock.getProperties().setGlobal(toggle.getValue());

        // 替代 worldRenderer.reload()：只通知客户端该位置的方块状态变化
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;
        BlockPos pos = this.modelBlock.getPos();
        if (world != null && pos != null)
        {
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }

        // 取消原版逻辑（原版会调用 worldRenderer.reload()）
        ci.cancel();
    }
}
