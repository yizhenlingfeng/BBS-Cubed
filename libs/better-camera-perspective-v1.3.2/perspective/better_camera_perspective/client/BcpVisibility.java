package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.client.ui.BcpVisibilityOverlayPanel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** 剪辑添加菜单的可见性筛选，持久化到 config/camera_perspective_clips.json */
@Environment(EnvType.CLIENT)
public class BcpVisibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final File CONFIG = new File(BBSMod.getSettingsFolder(), "camera_perspective_clips.json");

    private static final Map<String, Boolean> runtime = new HashMap<>();
    private static final Map<String, Boolean> saved = new HashMap<>();

    static {
        loadConfig();
    }

    public static boolean isClipVisible(Link type) {
        Boolean v = runtime.get(type.toString());
        return v == null || v;
    }

    public static boolean getVisible(Link type) {
        return isClipVisible(type);
    }

    public static void onEnterEditor(UIContext context) {
        ensureKeys();
        applySaved();
    }

    public static void openVisibilityPanel(UIContext context) {
        if (context == null) return;
        applySaved();
        UIOverlay.addOverlay(context, new BcpVisibilityOverlayPanel(), 460, 440);
    }

    public static void setVisible(Link type, boolean visible) {
        runtime.put(type.toString(), visible);
        saved.put(type.toString(), visible);
        saveConfig();
    }

    private static void ensureKeys() {
        MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
        if (factory == null) return;
        for (Link key : factory.getKeys()) {
            runtime.putIfAbsent(key.toString(), true);
        }
    }

    private static void applySaved() {
        ensureKeys();
        runtime.clear();
        runtime.putAll(saved);
    }

    private static void saveConfig() {
        try {
            MapType visibility = new MapType(true);
            for (Map.Entry<String, Boolean> e : saved.entrySet()) {
                visibility.putBool(e.getKey(), e.getValue());
            }
            MapType map = new MapType(true);
            map.put("visibility", visibility);
            DataToString.writeSilently(CONFIG, map);
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置写入异常", e);
        }
    }

    private static void loadConfig() {
        try {
            if (!CONFIG.exists()) return;
            BaseType base = DataToString.read(CONFIG);
            if (!(base instanceof MapType map)) return;
            if (map.get("visibility") instanceof MapType visibility) {
                saved.clear();
                for (String key : visibility.keys()) {
                    saved.put(key, visibility.getBool(key, true));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置加载异常", e);
        }
    }
}
