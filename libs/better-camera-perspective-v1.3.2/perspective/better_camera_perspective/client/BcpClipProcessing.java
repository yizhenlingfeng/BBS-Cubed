package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** 剪辑条可视化开关（名称/边界线/类型配色），由 UIClipRendererMixin 读取 */
@Environment(EnvType.CLIENT)
public class BcpClipProcessing {
    public static final String KEY_CLIP_NAMES = "clip_names";
    public static final String KEY_CLIP_BORDERS = "clip_borders";
    public static final String KEY_CLIP_COLORS = "clip_colors";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final Map<String, ValueBoolean> VALUES = new HashMap<>();
    private static boolean registered = false;

    public static void registerValues(SettingsBuilder builder) {
        if (registered) return;
        try {
            builder.category("clip_processing");
            VALUES.put(KEY_CLIP_NAMES, builder.getBoolean(KEY_CLIP_NAMES, true));
            VALUES.put(KEY_CLIP_BORDERS, builder.getBoolean(KEY_CLIP_BORDERS, true));
            VALUES.put(KEY_CLIP_COLORS, builder.getBoolean(KEY_CLIP_COLORS, true));
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「剪辑处理设置」注册失败", e);
        }
    }

    public static boolean isEnabled(String key) {
        ValueBoolean value = VALUES.get(key);
        return value == null || value.get();
    }
}
