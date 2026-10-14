package perspective.better_camera_perspective;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.events.RegisterSettingsEvent;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.clips.MultiTrackerClip;

/**
 * BBS 附加包服务端入口（fabric.mod.json 的 "bbs-addon" 字段）。
 *
 * 参照 BBS-Cubed 的注册方式：在 BBS 的 RegisterSettingsEvent 事件中把
 * 多样追踪剪辑注册进 BBS 的相机剪辑工厂。
 */
public class BBSAddonEntry implements BBSAddonMod {
    public static final String MULTI_TRACKER_ID = "multi_tracker";
    public static final String MULTI_TRACKER_ICON_COLOR_HEX = "0xFFB74D";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective");

    @Subscribe
    public void onRegisterSettings(RegisterSettingsEvent event) {
        try {
            MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
            if (factory == null) {
                LOGGER.warn("[BCP] 相机剪辑工厂为空，无法注册「多样追踪」剪辑");
                return;
            }

            factory.register(
                Link.bbs(MULTI_TRACKER_ID),
                MultiTrackerClip.class,
                new ClipFactoryData(Icons.USER, 0xFFB74D)
            );
            LOGGER.info("[BCP] 「多样追踪」剪辑 (MultiTrackerClip) 服务端注册成功");
        } catch (Exception e) {
            LOGGER.error("[BCP] 注册「多样追踪」剪辑失败", e);
        }
    }
}
