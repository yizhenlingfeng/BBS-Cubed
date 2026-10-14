package perspective.better_camera_perspective.client.clips;

import java.util.Collections;
import java.util.List;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * 多样追踪（MultiTracker）——客户端剪辑类。
 *
 * 继承 {@link KeyframeClip}（拥有 8 个关键帧通道 + additive），
 * 并复制了 {@code TrackerClientClip} 的骨骼追踪求值逻辑（位移位 0-2、角度位 3-5）。
 *
 * 播放求值顺序：
 *   1. 追踪求值（若设置了追踪目标，按 active 位掩码用目标骨骼矩阵覆盖对应的位置/角度轴）；
 *   2. 关键帧插值（X/Y/Z/yaw/pitch/roll/fov/distance 由曲线驱动，等效「关键帧」剪辑）——关键帧优先。
 *
 * 默认 active = 63（六位全开）时行为接近原版「追踪器」；关掉某一位后，
 * 该轴改由关键帧曲线驱动——这就是「多样追踪」的核心玩法。
 */
@Environment(EnvType.CLIENT)
public class MultiTrackerClientClip extends KeyframeClip {
    /** 追踪目标演员的 replay 稳定 ID。 */
    public final ValueString selector = new ValueString("selector", "");
    /** 相对追踪目标的偏移。 */
    public final ValuePoint offset = new ValuePoint("offset", new Point(0.0, 0.0, 0.0));
    /** 追踪视角角度。 */
    public final ValuePoint angle = new ValuePoint("angle", new Point(0.0, 0.0, 0.0));
    /** 追踪的骨骼/附件路径。 */
    public final ValueString group = new ValueString("group", "");
    /** 看向目标。 */
    public final ValueBoolean lookAt = new ValueBoolean("look_at", false);
    /** 相对模式。 */
    public final ValueBoolean relative = new ValueBoolean("relative");
    /** 位掩码（0-2 位移，3-5 角度；默认 63 全开）。 */
    public final ValueInt active = new ValueInt("active", 63, 0, 63);
    /**
     * anchor 追踪轴（v0.3）：追踪目标（演员+部位）的时间轴覆盖通道，原理同模型数据的 anchor 轴。
     * 无关键帧 / 空值 → 回退剪辑属性 selector/group；有关键帧非空 → 以 anchor 轴为准；
     * 相邻关键帧目标不同时按 previous/x 过渡插值，跨演员平滑切换。
     */
    public final KeyframeChannel<Anchor> anchor = new KeyframeChannel<>("anchor", KeyframeFactories.ANCHOR);
    /** 相对模式所需的进入本剪辑前的相机位置（等效 EntityClip.position）。 */
    public Position position = new Position(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    /**
     * v1.1：XYZ 新建关键帧的「主继承模式」（UI 状态，不参与序列化）。
     * 0 = 相对值（默认）：下一帧新建关键帧继承「相对偏移」；
     * 1 = 绝对值：下一帧新建关键帧继承「绝对坐标」。
     * 由关键帧编辑界面的「将主继承相对值 / 将主继承绝对值」按钮切换。
     */
    public int inheritMode = 0;

    public MultiTrackerClientClip() {
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
    public void applyClip(ClipContext context, Position position) {
        boolean additive = this.additive.get();
        boolean lookAt = this.lookAt.get();
        // v1.0：XYZ 关键帧固定为「相对值」语义（锚点 + 关键帧偏移合成；lookAt / 叠加除外）
        boolean relativeMode = !additive && !lookAt;

        // —— 1) 追踪目标解析（anchor 轴优先，v0.3）——
        String sel = this.selector.get();
        String grp = this.group.get();
        String fromSel = null;
        String fromGrp = null;
        float blend = 1.0F;
        boolean blendSource = false;

        if (context instanceof CameraClipContext) {
            Anchor anchor = this.anchor.isEmpty() ? null : this.anchor.interpolate((float) context.relativeTick + context.transition);
            if (anchor != null) {
                if (anchor.hasTarget()) {
                    sel = anchor.replay;
                    grp = anchor.attachment;
                    if (anchor.previous != null && anchor.previous.hasTarget() && !anchor.previous.hasSameTarget(anchor)) {
                        fromSel = anchor.previous.replay;
                        fromGrp = anchor.previous.attachment;
                        blend = anchor.x;
                        blendSource = true;
                    } else if (anchor.previous != null && !anchor.previous.hasTarget()) {
                        fromSel = this.selector.get();
                        fromGrp = this.group.get();
                        blend = anchor.x;
                        blendSource = true;
                    }
                } else if (anchor.previous != null && anchor.previous.hasTarget()) {
                    sel = this.selector.get();
                    grp = this.group.get();
                    fromSel = anchor.previous.replay;
                    fromGrp = anchor.previous.attachment;
                    blend = 1.0F - anchor.x;
                    blendSource = true;
                }
            }
        }

        // —— 2) 追踪求值（含过渡混合）——
        boolean tracked = false;
        double anchorX = 0.0D, anchorY = 0.0D, anchorZ = 0.0D;

        if (!sel.isEmpty()) {
            Position current = this.track(context, position, sel, grp, lookAt);
            if (current != null) {
                tracked = true;
                if (blendSource && blend < 1.0F) {
                    Position from = fromSel == null || fromSel.isEmpty() ? null : this.track(context, position, fromSel, fromGrp, lookAt);
                    Position base = from != null ? from : position.copy();
                    current = lerp(base, current, blend);
                }

                if (relativeMode) {
                    // 相对值语义（默认）：存追踪锚点，角度照常覆盖；XYZ 在关键帧插值后合成
                    anchorX = current.point.x;
                    anchorY = current.point.y;
                    anchorZ = current.point.z;
                    position.angle.yaw = this.isActive(3) ? current.angle.yaw : position.angle.yaw;
                    position.angle.pitch = this.isActive(4) ? current.angle.pitch : position.angle.pitch;
                    position.angle.roll = this.isActive(5) ? current.angle.roll : position.angle.roll;
                } else {
                    // lookAt / 叠加：追踪直接覆盖对应轴（lookAt 时 XYZ 未被覆盖，保持关键帧绝对值）
                    position.point.x = this.isActive(0) ? current.point.x : position.point.x;
                    position.point.y = this.isActive(1) ? current.point.y : position.point.y;
                    position.point.z = this.isActive(2) ? current.point.z : position.point.z;
                    position.angle.yaw = this.isActive(3) ? current.angle.yaw : position.angle.yaw;
                    position.angle.pitch = this.isActive(4) ? current.angle.pitch : position.angle.pitch;
                    position.angle.roll = this.isActive(5) ? current.angle.roll : position.angle.roll;
                }
            }
        }

        // —— 3) 关键帧插值（KeyframeClip 的全部逻辑）—— 关键帧优先。
        super.applyClip(context, position);

        // —— 4) 相对值语义 + 追踪生效：XYZ = 锚点 + 关键帧偏移（关键帧通道为空 = 纯追踪锚点）
        if (tracked && relativeMode) {
            if (this.isActive(0)) {
                position.point.x = this.x.isEmpty() ? anchorX : anchorX + position.point.x;
            }
            if (this.isActive(1)) {
                position.point.y = this.y.isEmpty() ? anchorY : anchorY + position.point.y;
            }
            if (this.isActive(2)) {
                position.point.z = this.z.isEmpty() ? anchorZ : anchorZ + position.point.z;
            }
        }
    }

    /** 追踪求值：返回按 active 位过滤后的 Position 副本；目标实体或帧无效时返回 null。 */
    private Position track(ClipContext context, Position position, String sel, String grp, boolean lookAt) {
        if (!(context instanceof CameraClipContext camera)) {
            return null;
        }

        IEntity entity = camera.entities.get(sel);
        if (entity == null) {
            return null;
        }

        if (!context.applyUnderneath(this.tick.get(), 0.0F, this.position)) {
            this.position.copy(position);
        }

        TrackerFrame frame = TrackerFrame.resolve(
            camera.entities, entity, grp, position.point.x, position.point.y, position.point.z, context.transition
        );
        if (frame == null) {
            return null;
        }

        if (this.relative.get()) {
            frame.relative(position, this.position);
        }

        Point offset = this.offset.get();
        Point angle = this.angle.get();
        Angle newAngle = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);
        Position result = position.copy();
        if (lookAt) {
            // lookAt：XYZ 不追踪（由关键帧绝对值驱动），仅角度看向目标
            result.angle.yaw = this.isActive(3) ? newAngle.yaw : result.angle.yaw;
            result.angle.pitch = this.isActive(4) ? newAngle.pitch : result.angle.pitch;
            result.angle.roll = this.isActive(5) ? newAngle.roll : result.angle.roll;
        } else {
            Vector3d newPosition = frame.position(offset);
            result.point.x = this.isActive(0) ? newPosition.x : result.point.x;
            result.point.y = this.isActive(1) ? newPosition.y : result.point.y;
            result.point.z = this.isActive(2) ? newPosition.z : result.point.z;
            result.angle.yaw = this.isActive(3) ? newAngle.yaw : result.angle.yaw;
            result.angle.pitch = this.isActive(4) ? newAngle.pitch : result.angle.pitch;
            result.angle.roll = this.isActive(5) ? newAngle.roll : result.angle.roll;
        }
        return result;
    }

    /** 两个追踪结果按 t 混合（位置/角度逐分量 lerp，角度按最短弧插值）。 */
    private static Position lerp(Position a, Position b, float t) {
        Position result = new Position();
        result.point.x = a.point.x + (b.point.x - a.point.x) * (double) t;
        result.point.y = a.point.y + (b.point.y - a.point.y) * (double) t;
        result.point.z = a.point.z + (b.point.z - a.point.z) * (double) t;
        result.angle.yaw = lerpAngle(a.angle.yaw, b.angle.yaw, t);
        result.angle.pitch = lerpAngle(a.angle.pitch, b.angle.pitch, t);
        result.angle.roll = lerpAngle(a.angle.roll, b.angle.roll, t);
        return result;
    }

    /** 角度最短弧插值（BBS 角度为弧度制）。 */
    private static float lerpAngle(float a, float b, float t) {
        float diff = (float) Math.atan2(Math.sin(b - a), Math.cos(b - a));
        return a + diff * t;
    }

    public boolean isActive(int bit) {
        return (this.active.get() >> bit & 1) == 1;
    }

    public List<IEntity> getEntities(ClipContext context) {
        String id = this.selector.get();
        if (context instanceof CameraClipContext cameraClipContext && !id.isEmpty()) {
            IEntity entity = cameraClipContext.entities.get(id);
            if (entity != null) {
                return Collections.singletonList(entity);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public Clip create() {
        return new MultiTrackerClientClip();
    }
}
