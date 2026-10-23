package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.film.replays.IReplayDragSnapshot;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在通用 {@link UIElement#subMouseClicked(UIContext)} 上缓存回放列表拖拽前的多选。
 *
 * <p>{@code UIReplayList} 未覆写 {@code subMouseClicked}，BBS 2.6/2.7 的列表也不覆写，
 * 因此注入 {@code UIElement} 并通过 {@link IReplayDragSnapshot} 转发到实际混入的
 * {@code UIReplayList} 实例。</p>
 */
@Mixin(value = UIElement.class, remap = false)
public abstract class UIElementReplayDragSnapshotMixin
{
    @Inject(method = "subMouseClicked", at = @At("HEAD"), remap = false, require = 1)
    private void bbspp$captureReplayDragSelection(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if ((Object) this instanceof IReplayDragSnapshot host)
        {
            host.bbspp$captureDragSelection(context);
        }
    }
}
