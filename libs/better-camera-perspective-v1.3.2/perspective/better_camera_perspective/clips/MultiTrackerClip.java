package perspective.better_camera_perspective.clips;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * 多样追踪（MultiTracker）——服务端剪辑类。
 *
 * 继承 {@link KeyframeClip}：天然拥有 X / Y / Z / yaw / pitch / roll / fov / distance
 * 八个关键帧通道与 additive 开关（即「关键帧」剪辑的全部能力）。
 *
 * 同时携带「追踪器」剪辑的全部追踪参数（selector / offset / angle / group / look_at / relative / active），
 * 使剪辑既有追踪能力、又可对每个要素打关键帧曲线——即「有追踪能力的关键帧」。
 *
 * 服务端 applyClip 沿用 KeyframeClip 的关键帧插值；真正的骨骼追踪求值发生在
 * 客户端 {@code MultiTrackerClientClip} 中（与原版 TrackerClip / TrackerClientClip 的职责划分一致）。
 */
public class MultiTrackerClip extends KeyframeClip {
    /** 追踪目标演员的 replay 稳定 ID（与 TrackerClip.selector 相同）。 */
    public final ValueString selector = new ValueString("selector", "");
    /** 相对追踪目标的偏移。 */
    public final ValuePoint offset = new ValuePoint("offset", new Point(0.0, 0.0, 0.0));
    /** 追踪视角角度。 */
    public final ValuePoint angle = new ValuePoint("angle", new Point(0.0, 0.0, 0.0));
    /** 追踪的骨骼/附件路径（空 = 根骨骼）。 */
    public final ValueString group = new ValueString("group", "");
    /** 看向目标（lookAt 模式）。 */
    public final ValueBoolean lookAt = new ValueBoolean("look_at", false);
    /** 相对模式。 */
    public final ValueBoolean relative = new ValueBoolean("relative");
    /**
     * 位掩码（默认 63 = 0b111111，6 位全开）：
     * 0/1/2 = X/Y/Z 位移跟随；3/4/5 = yaw/pitch/roll 角度跟随。
     * 关闭某一位后，对应要素改由关键帧通道驱动。
     * 注：fov 不参与位掩码，始终由 fov 关键帧通道驱动（比原版追踪器更灵活）。
     */
    public final ValueInt active = new ValueInt("active", 63, 0, 63);
    /**
     * anchor 追踪轴（v0.3）：追踪目标的时间轴覆盖通道。
     *
     * 值类型为 {@link Anchor}（replay = 演员稳定 ID，attachment = 模型部位路径），
     * 原理同 BBS 模型数据的 anchor 轴：
     *   - 通道无关键帧 / 关键帧为空值（replay 为空）→ 回退剪辑属性的模型绑定（selector/group）；
     *   - 通道有关键帧且值非空 → 以 anchor 轴数值为主（该时刻追踪目标 = 关键帧指定的演员+部位）；
     *   - 相邻关键帧目标不同时按 BBS 的 previous/x 过渡插值，实现跨演员平滑切换。
     */
    public final KeyframeChannel<Anchor> anchor = new KeyframeChannel<>("anchor", KeyframeFactories.ANCHOR);

    public MultiTrackerClip() {
        this.add(this.selector);
        this.add(this.offset);
        this.add(this.angle);
        this.add(this.group);
        this.add(this.lookAt);
        this.add(this.relative);
        this.add(this.active);
        this.add(this.anchor);
    }

    @Override
    public Clip create() {
        return new MultiTrackerClip();
    }
}
