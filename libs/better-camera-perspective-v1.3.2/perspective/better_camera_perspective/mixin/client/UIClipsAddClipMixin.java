package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.utils.context.ColorfulContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.IFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpVisibility;
import perspective.better_camera_perspective.client.ui.BcpAdaptiveClipMenu;

import java.util.ArrayList;
import java.util.List;

/** 接管右键添加剪辑菜单：插入「可视性设置」项、按可见性过滤、换自适应多列菜单；关闭时清掉预览白框 */
@Environment(EnvType.CLIENT)
@Mixin(UIClips.class)
public class UIClipsAddClipMixin {
    @Shadow
    @Final
    private IFactory<Clip, ClipFactoryData> factory;

    @Shadow
    private Vector3i addPreview;

    @Shadow
    public void addClip(Link type, int tick, int layer, int duration) {
        throw new AssertionError();
    }

    @Inject(method = "showAddClips", at = @At("HEAD"), cancellable = true)
    private void bcp$showAddClips(UIContext context, Vector3i preview, CallbackInfo ci) {
        if (preview == null) {
            this.addPreview = null;
            context.notifyError(UIKeys.CAMERA_TIMELINE_CANT_FIT_NOTIFICATION);
        } else {
            List<ContextAction> actions = new ArrayList<>();

            // 「可视性设置」伪条目：恒定排在菜单最前（列1 第一格 = 1 号位）
            actions.add(new ContextAction(
                Icons.SETTINGS,
                L10n.lang("bbs.ui.camera.visibility_settings"),
                () -> BcpVisibility.openVisibilityPanel(context)
            ));

            for (Link key : this.factory.getKeys()) {
                if (!BcpVisibility.isClipVisible(key)) {
                    continue;
                }
                ClipFactoryData data = this.factory.getData(key);
                Runnable run = () -> this.addClip(key, preview.x, preview.y, preview.z);
                ContextAction action = data.color != 0
                    ? new ColorfulContextAction(data.icon, UIKeys.C_CLIP.get(key), run, data.color)
                    : new ContextAction(data.icon, UIKeys.C_CLIP.get(key), run);
                actions.add(action);
            }

            if (!actions.isEmpty()) {
                BcpAdaptiveClipMenu menu = new BcpAdaptiveClipMenu(actions);
                context.replaceContextMenu(menu);
                // 菜单关闭时清掉预览占位框，否则会残留在原右键位置
                menu.getEvents().register(UIRemovedEvent.class, (e) -> this.addPreview = null);
            }
            this.addPreview = preview;
        }

        ci.cancel();
    }
}
