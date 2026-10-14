package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3d;
import org.joml.Vector3f;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;

/**
 * 多样追踪（MultiTracker）——剪辑 UI 面板。
 *
 * 组合了两套控件：
 *  - 「关键帧」剪辑：编辑按钮 + 关键帧编辑器 + additive 开关；
 *  - 「追踪器」剪辑：目标选取、附件选取、偏移、角度、看向目标、相对、启用位掩码。
 *
 * 拖动相机时：先按追踪器方式反解 offset/angle（保持跟随），再按关键帧方式写入曲线。
 */
@Environment(EnvType.CLIENT)
public class UIMultiTrackerClip extends UIClip<MultiTrackerClientClip> {
    // —— 关键帧侧 ——
    public UIButton edit;
    public UIKeyframeEditor keyframes;
    public UIToggle additive;

    // —— 追踪侧 ——
    public UIButton selector;
    public UIButton group;
    public UIPointModule point;
    public UIPointModule angle;
    public UIToggle lookAt;
    public UIToggle relative;
    public UIBitToggle active;

    public UIMultiTrackerClip(MultiTrackerClientClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void addEnvelopes() {
        super.addEnvelopes();
        this.additive = this.toggle(UIKeys.CAMERA_PANELS_ADDITIVE, this.clip.additive);
        this.panels.add(this.additive);
    }

    @Override
    protected void registerUI() {
        super.registerUI();

        // —— 关键帧编辑器 ——
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes
            .view
            .rulerRenderer(
                context -> UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get())
            );
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("multi_tracker_keyframes");
        this.edit = new UIButton(UIKeys.GENERAL_EDIT, b -> {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());

        // —— 追踪目标 ——
        this.selector = new UIButton(
            UIKeys.CAMERA_PANELS_TARGET_TITLE,
            b -> {
                UIFilmPanel panel = this.getParent(UIFilmPanel.class);
                if (panel != null) {
                    UIAnchorKeyframeFactory.displayActors(
                        this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), i -> this.clip.selector.set(i)
                    );
                }
            }
        );
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.group = new UIButton(
            UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT,
            b -> UIAnchorKeyframeFactory.displayAttachments(
                this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.group.get(), attachment -> this.clip.group.set(attachment)
            )
        );
        this.point = (UIPointModule) this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu(), () -> this.point.fill(this.clip.offset));
        this.angle = (UIPointModule) this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_ANGLE).contextMenu(), () -> this.angle.fill(this.clip.angle));
        this.lookAt = this.toggle(UIKeys.CAMERA_PANELS_LOOK_AT, this.clip.lookAt);
        this.relative = this.toggle(UIKeys.CAMERA_PANELS_RELATIVE, this.clip.relative);
        this.active = (UIBitToggle) this.bind(new UIBitToggle(value -> this.clip.active.set(value)).all(), () -> this.active.setValue(this.clip.active.get()));

        // —— v1.0：XYZ 关键帧值面板改为「绝对值 + 相对值」双值联动编辑，注册 BCP 专用工厂 ——
        BcpKeyframeFactories.register();
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, new UIElement[]{this.edit}));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, new UIElement[]{this.selector, this.group}));
        this.panels.add(this.point);
        this.panels.add(this.angle);
        this.panels.add(this.lookAt);
        this.panels.add(this.relative);
        this.panels.add(this.active);
    }

    @Override
    public void editClip(Position position) {
        // 1) 追踪求解：把当前相机位置反解为 offset/angle/fov（保持跟随语义）
        this.solveTracker(position);
        // 2) 关键帧写入：把当前相机位置写入关键帧通道
        this.insertKeyframes(position);
        super.editClip(position);
    }

    // ================= 追踪求解（复制自 UITrackerClip.editClip） =================

    private void solveTracker(Position position) {
        TrackerFrame frame = this.resolveFrame(position);
        if (frame != null) {
            Point offset = this.clip.offset.get();
            Point angle = this.clip.angle.get();
            boolean lookAt = this.clip.lookAt.get();
            if (!lookAt && this.isActive(0, 1, 2)) {
                Vector3d current = frame.position(offset);
                Point solved = frame.solveOffset(
                    this.pick(0, position.point.x, current.x), this.pick(1, position.point.y, current.y), this.pick(2, position.point.z, current.z)
                );
                if (solved != null) {
                    this.clip.offset.set(solved);
                }
            }

            if (this.isActive(3, 4, 5)) {
                Angle current = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);
                float yaw = this.pick(3, position.angle.yaw, current.yaw);
                float pitch = this.pick(4, position.angle.pitch, current.pitch);
                float roll = this.pick(5, position.angle.roll, current.roll);
                this.clip.angle.set(lookAt ? frame.solveLookAtAngles(offset, yaw, pitch, roll) : frame.solveAngles(yaw, pitch, roll));
            }
        }
    }

    private TrackerFrame resolveFrame(Position position) {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        UIContext context = this.getContext();
        String selector = this.clip.selector.get();
        if (panel != null && context != null && !selector.isEmpty()) {
            TrackerFrame frame = TrackerFrame.resolve(
                panel.getController().getEntities(), panel.getController().getEntities().get(selector),
                this.clip.group.get(), position.point.x, position.point.y, position.point.z, context.getTransition()
            );
            if (frame != null && this.clip.relative.get()) {
                if (this.clip.getUnderneath() == null) {
                    return null;
                }

                frame.relative(this.clip.getUnderneath(), this.clip.position);
            }

            return frame;
        } else {
            return null;
        }
    }

    private boolean isActive(int... bits) {
        for (int bit : bits) {
            if (this.clip.isActive(bit)) {
                return true;
            }
        }

        return false;
    }

    private float pick(int bit, float camera, float current) {
        return this.clip.isActive(bit) ? camera : current;
    }

    private double pick(int bit, double camera, double current) {
        return this.clip.isActive(bit) ? camera : current;
    }

    // ================= 关键帧写入（复制自 UIKeyframeClip.editClip） =================

    private void insertKeyframes(Position position) {
        Position newPos = position.copy();
        UIContext context = this.getContext();
        float tick = this.editor.getKeyframeCursor(context == null ? 0.0F : context.getTransition()) - (float) this.clip.tick.get().intValue();
        if (this.clip.additive.get()) {
            Position underneath = this.clip.getUnderneath();
            float factor = this.clip.envelope.factorEnabled(this.clip.duration.get(), tick);
            if (underneath != null && !(tick <= 0.0F) && factor != 0.0F && this.clip.enabled.get()) {
                this.insertAdditiveKeyframe(tick, this.clip.x, (newPos.point.x - underneath.point.x) / (double) factor);
                this.insertAdditiveKeyframe(tick, this.clip.y, (newPos.point.y - underneath.point.y) / (double) factor);
                this.insertAdditiveKeyframe(tick, this.clip.z, (newPos.point.z - underneath.point.z) / (double) factor);
                this.insertAdditiveKeyframe(tick, this.clip.yaw, (double) ((newPos.angle.yaw - underneath.angle.yaw) / factor));
                this.insertAdditiveKeyframe(tick, this.clip.pitch, (double) ((newPos.angle.pitch - underneath.angle.pitch) / factor));
                this.insertAdditiveKeyframe(tick, this.clip.roll, (double) ((newPos.angle.roll - underneath.angle.roll) / factor));
                this.insertAdditiveKeyframe(tick, this.clip.fov, (double) ((newPos.angle.fov - underneath.angle.fov) / factor));
            }
        } else {
            if (!this.clip.distance.isEmpty()) {
                double distance = this.clip.distance.interpolate(tick);
                if (distance != 0.0) {
                    Vector3f rotation = Matrices.rotation(MathUtils.toRad(newPos.angle.pitch), MathUtils.toRad(-newPos.angle.yaw - 180.0F));
                    newPos.point.x = newPos.point.x - (double) rotation.x * distance;
                    newPos.point.y = newPos.point.y - (double) rotation.y * distance;
                    newPos.point.z = newPos.point.z - (double) rotation.z * distance;
                }
            }

            // 多样追踪（v1.0）：XYZ 关键帧固定「相对值」语义——存相对锚点的偏移
            //（「偏移栏」语义，播放时按「锚点 + 关键帧偏移」合成）。
            double ax = 0.0D, ay = 0.0D, az = 0.0D;
            if (!this.clip.lookAt.get() && this.isActive(0, 1, 2)) {
                TrackerFrame anchorFrame = this.resolveFrame(position);
                if (anchorFrame != null) {
                    Vector3d anchor = anchorFrame.position(this.clip.offset.get());
                    ax = this.clip.isActive(0) ? anchor.x : 0.0D;
                    ay = this.clip.isActive(1) ? anchor.y : 0.0D;
                    az = this.clip.isActive(2) ? anchor.z : 0.0D;
                }
            }
            this.insertKeyframe(tick, this.clip.x, newPos.point.x - ax);
            this.insertKeyframe(tick, this.clip.y, newPos.point.y - ay);
            this.insertKeyframe(tick, this.clip.z, newPos.point.z - az);
            this.insertKeyframe(tick, this.clip.yaw, (double) newPos.angle.yaw);
            this.insertKeyframe(tick, this.clip.pitch, (double) newPos.angle.pitch);
            this.insertKeyframe(tick, this.clip.roll, (double) newPos.angle.roll);
            this.insertKeyframe(tick, this.clip.fov, (double) newPos.angle.fov);
        }
    }

    private void insertAdditiveKeyframe(float tick, KeyframeChannel<Double> channel, double offset) {
        double reference = channel.isEmpty() ? 0.0 : channel.interpolate(0.0F);
        if (channel.getKeyframes().stream().noneMatch(keyframe -> keyframe.getTick() == 0.0F)) {
            this.insertKeyframe(0.0F, channel, reference);
        }

        this.insertKeyframe(tick, channel, reference - offset);
    }

    private void insertKeyframe(float tick, KeyframeChannel<Double> channel, double x) {
        KeyframeSegment<Double> segment = channel.findSegment(tick);
        int insert = channel.insert(tick, x);
        if (segment != null) {
            channel.get(insert).copyOverExtra(segment.a);
        }
    }

    @Override
    public void fillData() {
        super.fillData();
        this.updateDuration(this.clip.duration.get());
        this.keyframes.setClip(this.clip);
        // v0.3：UIKeyframeEditor.setClip 只遍历 channels 数组（8 个数值通道），这里手动补挂 anchor 通道
        this.appendSheet("anchor", L10n.lang("bbs.ui.camera.panels.axis_anchor"), 0xFF42A5F5, this.clip.anchor);
        // v1.1：给 X / Y / Z 通道设置 seed —— 新建关键帧（无插值段可继承）时按「主继承模式」供给继承值。
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets()) {
            if (sheet.channel == this.clip.x) {
                this.setSeed(sheet, 0);
            } else if (sheet.channel == this.clip.y) {
                this.setSeed(sheet, 1);
            } else if (sheet.channel == this.clip.z) {
                this.setSeed(sheet, 2);
            }
        }
    }

    private void appendSheet(String id, IKey title, int color, KeyframeChannel<?> channel) {
        boolean exists = this.keyframes.view.getGraph().getSheets().stream().anyMatch(sheet -> sheet.channel == channel);
        if (!exists) {
            this.keyframes.view.addSheet(new UIKeyframeSheet(id, title, color, channel, null));
        }
    }

    /** v1.1：为 X/Y/Z 通道 sheet 挂「主继承值」seed（无插值段新建关键帧时使用）。 */
    private void setSeed(UIKeyframeSheet sheet, int axis) {
        sheet.seed(() -> this.inheritSeedValue(axis));
    }

    /** 按剪辑主继承模式计算当前播放头处的继承值：相对 = 通道插值；绝对 = 插值 + 锚点。 */
    private Object inheritSeedValue(int axis) {
        KeyframeChannel<Double> channel = axis == 0 ? this.clip.x : (axis == 1 ? this.clip.y : this.clip.z);
        float tick = ((UIFilmKeyframes) this.keyframes.view).getOffset();
        double relative = channel.isEmpty() ? 0.0D : channel.interpolate(tick);
        if (this.clip.inheritMode == 0) {
            return relative;
        }
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        if (panel == null) {
            return relative;
        }
        return relative + anchorAxisAt(this.clip, panel, axis, tick);
    }

    // ================= XYZ 双值编辑锚点工具（v1.0） =================

    /**
     * 计算剪辑内 tick 处某轴（0=X / 1=Y / 2=Z）的追踪锚点坐标（供 UIXyzKeyframeFactory 双值联动）。
     * 追踪目标按 anchor 轴在该 tick 的插值优先，回退剪辑属性 selector/group；
     * 无追踪目标时返回 0（相对值 = 绝对值）。
     */
    public static double anchorAxisAt(MultiTrackerClientClip clip, UIFilmPanel panel, int axis, float tick) {
        String sel = clip.selector.get();
        String grp = clip.group.get();
        if (!clip.anchor.isEmpty()) {
            Anchor anchor = clip.anchor.interpolate(tick);
            if (anchor != null && anchor.hasTarget()) {
                sel = anchor.replay;
                grp = anchor.attachment;
            }
        }

        if (sel.isEmpty()) {
            return 0.0D;
        }

        IEntity actor = panel.getController().getEntities().get(sel);
        if (actor == null) {
            return 0.0D;
        }

        Vector3d pos = anchorPos(clip, panel, actor, grp, tick);
        if (pos == null) {
            return 0.0D;
        }

        return axis == 0 ? pos.x : (axis == 1 ? pos.y : pos.z);
    }

    private static Vector3d anchorPos(MultiTrackerClientClip clip, UIFilmPanel panel, IEntity actor, String group, float tick) {
        TrackerFrame frame = TrackerFrame.resolve(
            panel.getController().getEntities(), actor, group, 0.0F, 0.0F, 0.0F, tick
        );
        if (frame == null) {
            return null;
        }

        if (clip.relative.get()) {
            Position underneath = clip.getUnderneath();
            if (underneath == null) {
                return null;
            }

            frame.relative(underneath, clip.position);
        }

        return frame.position(clip.offset.get());
    }
}
