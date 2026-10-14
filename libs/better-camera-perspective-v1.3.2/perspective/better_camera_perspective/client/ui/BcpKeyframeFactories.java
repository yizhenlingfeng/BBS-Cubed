package perspective.better_camera_perspective.client.ui;

import java.util.HashMap;
import java.util.Map;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * BCP 专用关键帧值 UI 工厂注册表（v1.0）。
 *
 * 与 BBS 的 UIKeyframeFactory.PROPERTIES 静态注册表分离，避免 x/y/z 通道 id
 * 在 BBS 其他模块（ReplayKeyframes / FormProperties 等）中全局污染：
 * 只有多样追踪剪辑的关键帧编辑器（由 UIKeyframeFactoryMixin 识别）才会查本表。
 */
@Environment(EnvType.CLIENT)
public class BcpKeyframeFactories {
    private static final Map<String, UIKeyframeFactory.IUIKeyframeFactoryFactory> PROPERTIES = new HashMap<>();
    private static boolean registered = false;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // XYZ 关键帧值面板：绝对值 + 相对值 双值联动编辑
        PROPERTIES.put("x", (kf, ed) -> new UIXyzKeyframeFactory(kf, ed, 0));
        PROPERTIES.put("y", (kf, ed) -> new UIXyzKeyframeFactory(kf, ed, 1));
        PROPERTIES.put("z", (kf, ed) -> new UIXyzKeyframeFactory(kf, ed, 2));
    }

    public static UIKeyframeFactory.IUIKeyframeFactoryFactory get(String name) {
        return PROPERTIES.get(name);
    }
}
