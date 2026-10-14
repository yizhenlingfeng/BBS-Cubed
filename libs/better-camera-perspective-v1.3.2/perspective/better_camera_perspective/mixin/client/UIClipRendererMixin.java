package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.clips.renderer.UIClipRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import perspective.better_camera_perspective.client.BcpClipProcessing;

/**
 * v1.3.2：「剪辑条显示」开关组。
 *
 * 在 UIClipRenderer 基类渲染中按开关跳过/替换绘制：
 *  - clip_names：关闭时跳过剪辑名称文本（textShadow）；
 *  - clip_borders：关闭时跳过剪辑条边界线（outline）；
 *  - clip_colors：关闭时以中性灰替换剪辑类型配色（box 底色）。
 *
 * 仅影响使用基类渲染路径的剪辑条（子类覆写 renderClip/renderBackground 的
 * 渲染器保持原样，默认开关全开时行为不变）。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIClipRenderer.class)
public abstract class UIClipRendererMixin {
    @Redirect(
        method = "renderClip",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/elements/utils/Batcher2D;outline(FFFFI)V")
    )
    private void bcp$clipBorder(Batcher2D batcher, float x1, float y1, float x2, float y2, int color) {
        if (BcpClipProcessing.isEnabled(BcpClipProcessing.KEY_CLIP_BORDERS)) {
            batcher.outline(x1, y1, x2, y2, color);
        }
    }

    @Redirect(
        method = "renderClip",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/elements/utils/Batcher2D;textShadow(Ljava/lang/String;FF)V")
    )
    private void bcp$clipName(Batcher2D batcher, String label, float x, float y) {
        if (BcpClipProcessing.isEnabled(BcpClipProcessing.KEY_CLIP_NAMES)) {
            batcher.textShadow(label, x, y);
        }
    }

    @Redirect(
        method = "renderBackground",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/elements/utils/Batcher2D;box(FFFFI)V")
    )
    private void bcp$clipColor(Batcher2D batcher, float x1, float y1, float x2, float y2, int color) {
        int finalColor = BcpClipProcessing.isEnabled(BcpClipProcessing.KEY_CLIP_COLORS) ? color : 0xFF3A3A3A;
        batcher.box(x1, y1, x2, y2, finalColor);
    }
}
