package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpVisibility;
import perspective.better_camera_perspective.client.FilmDisplayNames;

/**
 * UIFilmPanel 相关钩子：
 *  - 构造完成后，把面板登记进 {@link FilmDisplayNames}（Form 显示名反查）；
 *  - 进入摄像机编辑器（fillData）时：应用用户保存的剪辑可见性筛选。
 */
@Mixin(UIFilmPanel.class)
public abstract class UIFilmPanelMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bcp$registerPanel(CallbackInfo ci) {
        FilmDisplayNames.register((UIFilmPanel) (Object) this);
    }

    @Inject(method = "fillData", at = @At("HEAD"))
    private void bcp$onEnterEditor(CallbackInfo ci) {
        UIFilmPanel panel = (UIFilmPanel) (Object) this;
        UIContext context = panel.getContext();
        BcpVisibility.onEnterEditor(context);
    }
}
