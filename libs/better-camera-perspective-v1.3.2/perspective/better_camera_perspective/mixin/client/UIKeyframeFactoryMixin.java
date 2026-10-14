package perspective.better_camera_perspective.mixin.client;

import java.util.Map;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.client.ui.BcpKeyframeFactories;

/**
 * v1.0：接管 UIKeyframeFactory.getPropertyFactory 的属性工厂匹配。
 *
 * BBS 原逻辑按「sheet.property 非空」查全局 PROPERTIES 注册表——若把 x/y/z 注册进该表，
 * 会污染 BBS 其他模块（ReplayKeyframes / FormProperties 等）的同名通道。
 * 本 mixin 改为：
 *  - 多样追踪剪辑的关键帧编辑器：只查 BCP 专用注册表（XYZ 双值工厂），未命中回退 FACTORIES；
 *  - 其他编辑器：完整保留原逻辑（BBS 的 PROPERTIES 注册表）。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIKeyframeFactory.class)
public class UIKeyframeFactoryMixin {
    @Shadow
    private static Map<String, UIKeyframeFactory.IUIKeyframeFactoryFactory> PROPERTIES;

    @Inject(method = "getPropertyFactory", at = @At("HEAD"), cancellable = true)
    private static void bcp$getPropertyFactory(Keyframe<?> keyframe, UIKeyframes editor, CallbackInfoReturnable<UIKeyframeFactory.IUIKeyframeFactoryFactory> cir) {
        IUIKeyframeGraph graph = editor == null ? null : editor.getGraph();
        UIKeyframeSheet sheet = graph == null ? null : graph.getSheet(keyframe);
        if (sheet == null || sheet.isBoneTrack) {
            cir.setReturnValue(null);
            return;
        }

        // 多样追踪剪辑编辑器：按 BCP 专用注册表匹配（x/y/z → 双值联动工厂）
        if (editor instanceof UIFilmKeyframes film && film.editor != null && film.editor.getClip() instanceof MultiTrackerClientClip) {
            cir.setReturnValue(BcpKeyframeFactories.get(StringUtils.fileName(sheet.channel.getId())));
            return;
        }

        // 其他编辑器：保留原逻辑（属性型通道按属性名匹配）
        if (sheet.property != null) {
            cir.setReturnValue(PROPERTIES.get(StringUtils.fileName(sheet.channel.getId())));
            return;
        }

        cir.setReturnValue(null);
    }
}
