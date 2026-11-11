package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.ui.animations.UIAnimationPreview;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让「不在根元素树里的」模型预览也能被世界阶段驱动。
 *
 * <p>1.21.11 的两阶段 GUI 把模型预览拆成了两步：世界阶段 {@code UIModelRenderer#renderModelToTexture}
 * 把模型烘进离屏贴图（必须发生在 {@code Screen.render} 的录制窗口之外，否则 GUI 的 alpha 写入掩码会把
 * FBO 的 alpha 清零，且模型的即时 RenderLayer 无法在自己的 pass 里落地），GUI 阶段只记录一次 blit。
 *
 * <p>{@code UIScreen#renderInWorld} 只遍历 {@code menu.getRoot().getChildren(UIModelRenderer.class)}，
 * 所以 tooltip 里的预览（动画列表悬停那个小窗）永远轮不到，贴图 id 一直是 -1，界面就是一片空白。
 * 这里在它遍历完之后补上我们自己登记的那些预览。
 */
@Mixin(value = UIScreen.class, remap = false)
public class UIScreenPreviewMixin121
{
    @Inject(method = "renderInWorld", at = @At("TAIL"), remap = false)
    private void bbspp$renderTooltipPreviews(WorldRenderContext context, CallbackInfo info)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu == null)
        {
            return;
        }

        UIAnimationPreview.renderActivePreviews(menu.context);
    }
}
