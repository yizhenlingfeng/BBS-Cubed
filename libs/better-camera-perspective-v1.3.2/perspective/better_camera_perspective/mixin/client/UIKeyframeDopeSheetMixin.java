package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.client.ui.UIMultiTrackerClip;

/**
 * v1.1：多样追踪「关键帧打点」主继承模式注入。
 *
 * 在时间轴（DopeSheet）上点击新建关键帧时（addKeyframeAt），若目标通道是
 * 多样追踪剪辑的 X / Y / Z 通道，则按剪辑当前「主继承模式」（inheritMode）计算继承值：
 *  - 相对值模式：该 tick 的通道插值（相对偏移）；
 *  - 绝对值模式：插值 + 该 tick 追踪锚点（绝对坐标）。
 * 非多样追踪 XYZ 通道保持 BBS 原逻辑（继承值 = null，走默认继承链）。
 *
 * 实现约束：addKeyframeManually / addKeyframe 是 IUIKeyframeGraph 的接口 default 方法，
 * @Shadow 无法绑定（sponge 只查类继承链），因此这里在 HEAD 取消后按原逻辑手动重放打点
 * （全部调用均为类 / 字段的 public 成员），避免 @ModifyArg 描述符（依赖 refmap）与
 * @Shadow 接口方法两种运行时失败。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIKeyframeDopeSheet.class)
public class UIKeyframeDopeSheetMixin {
    @Shadow
    private UIKeyframes keyframes;

    @Shadow
    public UIKeyframeSheet getSheet(int mouseY) {
        return null;
    }

    @Inject(method = "addKeyframeAt", at = @At("HEAD"), cancellable = true)
    private void bcp$addKeyframeAt(float tick, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        UIKeyframeSheet sheet = this.getSheet(mouseY);
        if (sheet == null || sheet.channel == null) {
            cir.setReturnValue(false);
            return;
        }

        Object value = this.bcp$inheritValue(sheet, tick);
        KeyframeChannel<Object> channel = (KeyframeChannel<Object>) sheet.channel;
        boolean inherits = value == null && !channel.isEmpty();
        int index;

        if (value == null) {
            // —— 与 IUIKeyframeGraph.addKeyframe 的默认继承链一致 ——
            KeyframeSegment<Object> segment = channel.find(tick);
            Keyframe extra = null;
            if (segment != null) {
                value = segment.createInterpolated();
                extra = segment.a;
            } else if (sheet.seed != null) {
                value = sheet.seed.get();
            } else if (sheet.property != null) {
                value = channel.getFactory().copy(sheet.property.get());
            } else {
                value = channel.getFactory().createEmpty();
            }
            channel.preNotify(1);
            index = channel.insert(tick, value);
            Keyframe keyframe = channel.get(index);
            if (extra != null) {
                keyframe.copyOverExtra(extra);
            }
        } else {
            channel.preNotify(1);
            index = channel.insert(tick, value);
        }

        Keyframe keyframe = channel.get(index);
        if (keyframe != null && !inherits) {
            keyframe.getInterpolation().setInterp(BBSSettings.getDefaultKeyframeInterpolation());
        }
        if (keyframe != null) {
            sheet.selection.add(index);
        }
        cir.setReturnValue(true);
    }

    /** 多样追踪 X/Y/Z 通道 → 主继承值；其余返回 null（保持 BBS 原逻辑）。 */
    private Object bcp$inheritValue(UIKeyframeSheet sheet, float tick) {
        if (!(this.keyframes instanceof UIFilmKeyframes film) || film.editor == null) {
            return null;
        }
        IUIClipsDelegate delegate = film.editor;
        Clip clip = delegate.getClip();
        if (!(clip instanceof MultiTrackerClientClip multi)) {
            return null;
        }

        int axis;
        if (sheet.channel == multi.x) {
            axis = 0;
        } else if (sheet.channel == multi.y) {
            axis = 1;
        } else if (sheet.channel == multi.z) {
            axis = 2;
        } else {
            return null;
        }

        KeyframeChannel<Double> channel = (KeyframeChannel<Double>) sheet.channel;
        double relative = channel.isEmpty() ? 0.0D : channel.interpolate(tick);
        if (multi.inheritMode == 0) {
            return relative;
        }
        UIFilmPanel panel = this.keyframes.getParent(UIFilmPanel.class);
        if (panel == null) {
            return relative;
        }
        return relative + UIMultiTrackerClip.anchorAxisAt(multi, panel, axis, tick);
    }
}
