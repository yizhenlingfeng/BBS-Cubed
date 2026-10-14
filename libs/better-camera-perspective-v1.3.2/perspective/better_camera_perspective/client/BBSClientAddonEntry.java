package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.api.client.events.RegisterClipPanelsEvent;
import mchorse.bbs_mod.api.client.events.RegisterL10nEvent;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.BBSAddonEntry;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.client.ui.UIMultiTrackerClip;

/**
 * BBS 附加包客户端入口（fabric.mod.json 的 "bbs-client-addon" 字段）。
 *
 * RegisterClipPanelsEvent 由 BBSModClient 在初始化时 post（此时 BBS 工厂必然已创建）：
 *   1. 用相同 Link id 注册客户端剪辑类 MultiTrackerClientClip（覆盖服务端类，供播放求值）；
 *   2. 注册该剪辑的 UI 面板（按运行时剪辑类匹配）。
 *
 * RegisterL10nEvent：注册本 mod 的语言文件（剪辑显示名「多样追踪」）。
 */
@Environment(EnvType.CLIENT)
public class BBSClientAddonEntry implements BBSAddonMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static L10n registeredL10n;

    @Subscribe
    public void onRegisterClipPanels(RegisterClipPanelsEvent event) {
        try {
            MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
            if (factory == null) {
                LOGGER.warn("[BCP] 相机剪辑工厂为空，无法注册客户端剪辑类");
            } else {
                factory.register(
                    Link.bbs(BBSAddonEntry.MULTI_TRACKER_ID),
                    MultiTrackerClientClip.class,
                    new ClipFactoryData(Icons.USER, 0xFFB74D)
                );
                LOGGER.info("[BCP] 「多样追踪」客户端剪辑类 (MultiTrackerClientClip) 注册成功");
            }

            event.register(MultiTrackerClientClip.class, UIMultiTrackerClip::new);
            LOGGER.info("[BCP] 「多样追踪」UI 面板 (UIMultiTrackerClip) 注册成功");
        } catch (Exception e) {
            LOGGER.error("[BCP] 「多样追踪」客户端注册失败", e);
        }
    }

    @Subscribe
    public void onRegisterSettings(RegisterClientSettingsEvent event) {
        try {
            BcpConflictSettingsModule.register(event);
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页注册异常", e);
        }
    }

    @Subscribe
    public void onRegisterL10n(RegisterL10nEvent event) {
        if (event.l10n != null && registeredL10n != event.l10n) {
            registeredL10n = event.l10n;
            event.l10n.registerOne(lang -> Link.assets("better_camera_perspective/strings/" + lang + ".json"));
            try {
                event.l10n.reload();
                LOGGER.info("[BCP] 多样追踪语言文件注册并加载成功");
            } catch (RuntimeException e) {
                LOGGER.error("[BCP] 多样追踪语言文件加载失败", e);
            }
        }
    }
}
