package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/** BBS 设置页「摄像机编辑设置」模块，注册剪辑处理开关 */
@Environment(EnvType.CLIENT)
public class BcpConflictSettingsModule {
    public static final String MODULE_ID = "conflict_features";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");

    public static void register(RegisterClientSettingsEvent event) {
        try {
            BBSMod.setupConfig(
                Icons.FRUSTUM,
                MODULE_ID,
                new File(BBSMod.getSettingsFolder(), MODULE_ID + ".json"),
                BcpClipProcessing::registerValues
            );
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「摄像机编辑设置」注册失败", e);
        }
    }
}
