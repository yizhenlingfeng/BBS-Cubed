package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;

/**
 * XYZ 关键帧值双值联动编辑工厂（v1.0 / v1.1）。
 *
 * 选中多样追踪剪辑的 X / Y / Z 关键帧时，同时显示两个数值：
 *  - 相对值：该视角相对于被绑定模型及部位（或默认位置）的相对偏移 —— 直接写入关键帧；
 *  - 绝对值：该视角在地图上的绝对坐标。
 * 两个值任改其一，另一个值按「该 tick 的追踪锚点」自动同步换算（绝对 = 相对 + 锚点）。
 *
 * v1.1：两个行首的锁定标签升级为「主继承模式」开关：
 *  - 默认激活「相对值」——点击后按钮文本变为「将主继承相对值」，表示后续新建关键帧主继承相对值；
 *  - 点击「绝对值」按钮同理（「将主继承绝对值」）；
 *  - 主继承模式存储在剪辑实例 {@link MultiTrackerClientClip#inheritMode}，
 *    新建关键帧时（多样追踪 X/Y/Z 通道）按该模式供给继承值。
 */
@Environment(EnvType.CLIENT)
public class UIXyzKeyframeFactory extends UIKeyframeFactory<Double> {
    private final int axis;
    private final UITrackpad relativeValue;
    private final UITrackpad absoluteValue;
    private final UIButton relativeButton;
    private final UIButton absoluteButton;

    public UIXyzKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor, int axis) {
        super(keyframe, editor);
        this.axis = axis;

        this.relativeValue = new UITrackpad(this::setRelative);
        this.relativeValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.relative"));
        this.absoluteValue = new UITrackpad(this::setAbsolute);
        this.absoluteValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.absolute"));

        this.relativeButton = this.modeButton(b -> this.setMode(0));
        this.absoluteButton = this.modeButton(b -> this.setMode(1));

        this.scroll.add(UI.row(2, 0, 0, this.relativeButton, this.relativeValue));
        this.scroll.add(UI.row(2, 0, 0, this.absoluteButton, this.absoluteValue));

        this.refreshButtons();
        this.updateValues();
    }

    /** v1.1：主继承模式按钮（可点击开关，替代 v1.0 的锁定标签）。 */
    private UIButton modeButton(java.util.function.Consumer<UIButton> callback) {
        UIButton button = new UIButton(IKey.raw(""), callback);
        button.wh(104, 16);
        button.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.inherit_tooltip"));
        return button;
    }

    /** 切换主继承模式：0 = 相对值，1 = 绝对值。 */
    private void setMode(int mode) {
        MultiTrackerClientClip clip = this.findClip();
        if (clip != null) {
            clip.inheritMode = mode;
        }
        this.refreshButtons();
    }

    /** 刷新两个模式按钮的文本与高亮状态（激活 = 长文本 + 主色底；未激活 = 短标签）。 */
    private void refreshButtons() {
        MultiTrackerClientClip clip = this.findClip();
        boolean relative = clip == null || clip.inheritMode == 0;

        this.relativeButton.label = IKey.raw(
            relative
                ? L10n.lang("bbs.ui.camera.panels.xyz.inherit_relative").get()
                : L10n.lang("bbs.ui.camera.panels.xyz.relative").get()
        );
        this.absoluteButton.label = IKey.raw(
            relative
                ? L10n.lang("bbs.ui.camera.panels.xyz.absolute").get()
                : L10n.lang("bbs.ui.camera.panels.xyz.inherit_absolute").get()
        );

        int primary = BBSSettings.primaryColor(0) | 0xFF000000;
        if (relative) {
            this.relativeButton.color(primary).background(true).textColor(0xFFFFFF, true);
            this.absoluteButton.background(false).textColor(0xAAAAAA, false);
        } else {
            this.absoluteButton.color(primary).background(true).textColor(0xFFFFFF, true);
            this.relativeButton.background(false).textColor(0xAAAAAA, false);
        }
    }

    /** 相对值编辑：关键帧值 = 输入；绝对值同步显示 = 输入 + 锚点。 */
    private void setRelative(double value) {
        this.setValue(value);
        this.absoluteValue.setValue(value + this.anchor());
    }

    /** 绝对值编辑：关键帧值 = 输入 − 锚点（相对偏移）；相对值同步显示。 */
    private void setAbsolute(double value) {
        double anchor = this.anchor();
        this.setValue(value - anchor);
        this.relativeValue.setValue(value - anchor);
    }

    /** 该 tick 的追踪锚点坐标（anchor 轴优先，回退剪辑属性；无目标 = 0）。 */
    private double anchor() {
        MultiTrackerClientClip clip = this.findClip();
        UIFilmPanel panel = this.getPanel();
        if (clip == null || panel == null) {
            return 0.0D;
        }
        return UIMultiTrackerClip.anchorAxisAt(clip, panel, this.axis, this.keyframe.getTick());
    }

    private void updateValues() {
        double value = this.keyframe.getValue();
        double anchor = this.anchor();
        this.relativeValue.setValue(value);
        this.absoluteValue.setValue(value + anchor);
    }

    @Override
    public void update() {
        super.update();
        this.updateValues();
    }

    private MultiTrackerClientClip findClip() {
        if (this.editor instanceof UIFilmKeyframes film && film.editor != null) {
            Clip clip = film.editor.getClip();
            if (clip instanceof MultiTrackerClientClip multi) {
                return multi;
            }
        }
        return null;
    }

    private UIFilmPanel getPanel() {
        return this.getParent(UIFilmPanel.class);
    }
}
