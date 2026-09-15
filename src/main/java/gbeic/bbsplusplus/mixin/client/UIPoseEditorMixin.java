package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.PickTextureButtonHolder;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 在 {@link UIPoseEditor} 中追加两个按钮：骨骼纹理、附魔光效，均作用于当前选中骨骼。
 *
 * <p><b>骨骼纹理</b> —— 点开按钮弹出 {@link UITexturePicker}，选择纹理后写回当前选中骨骼对应
 * {@link PoseTransform} 的 texture 字段（经 {@link BoneTextureHolder} 接口）。
 * 默认无选中骨骼则 no-op；受 {@code CMLSettings.pickLimbTexture} 开关控制（与 CML 行为一致）。</p>
 *
 * <p><b>附魔光效</b> —— 点击切换当前选中骨骼的原版风格附魔流光（经 {@link GlintHolder} 接口），
 * 受 {@code CMLSettings.enchantGlint} 开关控制，按钮文字会反映当前骨骼的开/关状态。
 * 两个按钮共用同一套写入分派（{@link #bbspp_cml$writeToSelection}）。</p>
 *
 * <p>纹理数据流：UI → {@code BoneTextureHolder#bbspp_cml$setTexture} →
 * PoseTransformMixin 的 texture 字段（含 toData/fromData/copy 持久化钩子）→
 * ModelFormRenderer.getPose() 每帧 copy → ModelMixin 在 Model.applyPose TAIL 传播到
 * ModelGroup → CubicVAORendererMixin 渲染时优先绑定 —— 选择后下一帧立即可见。</p>
 *
 * <p>按宿主分派写入路径：
 * <ul>
 *   <li>{@link UIModelPoseEditor}（伪装编辑界面）—— 经其 valuePose 触发
 *       {@code preNotify/postNotify(FLAG_UNMERGEABLE)}，进 undo 并标记 form 变更；</li>
 *   <li>{@link UIPoseKeyframeFactory.UIPoseFactoryEditor}（pose 关键帧属性面板）——
 *       走其静态 apply 帮手（对所有选中关键帧生效 + 关键帧级 pre/postNotify）；</li>
 *   <li>其它宿主 —— 直接写。</li>
 * </ul></p>
 */
@Mixin(UIPoseEditor.class)
public abstract class UIPoseEditorMixin extends UIElement implements PickTextureButtonHolder
{
    /* 用于右键"应用到子骨骼"：需要模型结构（查子骨骼）与骨骼列表（查当前选中）。 */
    @Shadow(remap = false)
    protected IBoneHierarchy model;

    @Unique
    private UIButton bbspp_cml$pickTexture;

    @Unique
    private UIToggle bbspp_cml$glint;

    @Unique
    private UIColor bbspp_cml$glintColor;

    @Override
    public UIButton bbspp_cml$getPickTextureButton()
    {
        return this.bbspp_cml$pickTexture;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createPoseButtons(CallbackInfo ci)
    {
        UIPoseEditor self = (UIPoseEditor) (Object) this;

        this.bbspp_cml$pickTexture = new UIButton(L10n.lang("bbs.ui.pose.pick_bone_texture"), (b) ->
        {
            this.bbspp_cml$openBoneTexturePicker(self);
        });

        /* UIToggle 的开关样式与 BBS 原版姿势页的"光照"开关一致。
         * 三参构造的第二个参数是初始值；setValue() 只写字段不触发回调，可安全用于同步。 */
        this.bbspp_cml$glint = new UIToggle(L10n.lang("bbs.ui.pose.enchant_glint"), false, (t) ->
        {
            this.bbspp_cml$applyGlintToSelection(self, ((UIToggle) t).getValue());
        });
        this.bbspp_cml$glint.h(16);

        /* 与 FS 原版的定位/颜色/光照一致：给开关挂一个右键"应用到子骨骼"菜单项，
         * 图标与文案复用 BBS 自带的 Icons.DOWNLOAD / UIKeys.POSE_CONTEXT_APPLY，无需新增翻译。 */
        this.bbspp_cml$glint.context((ContextMenuManager manager) ->
        {
            manager.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.bbspp_cml$applyGlintToChildren(self, this.bbspp_cml$glint.getValue());
            });
        });

        /* 光效颜色：挂在开关正下方（add 顺序即面板自上而下），带 alpha 滑条可顺带调淡。 */
        this.bbspp_cml$glintColor = new UIColor((argb) ->
        {
            this.bbspp_cml$applyGlintColorToSelection(self, argb == null ? 0xFFFFFFFF : argb);
        });
        this.bbspp_cml$glintColor.withAlpha().h(16);

        this.bbspp_cml$glintColor.context((ContextMenuManager manager) ->
        {
            manager.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.bbspp_cml$applyGlintColorToChildren(self);
            });
        });

        /* UIPoseEditor 本体是一个 column().vertical().stretch() 的纵向列，
         * add() 的先后顺序就是面板自上而下的排列顺序。排在骨骼纹理之后，
         * 即落在姿势子页面右侧按钮列表的最下方。 */
        self.add(this.bbspp_cml$pickTexture);

        /* glint 开关/取色器不在这里 add：由 refreshGlintButton 按设置显隐。
         * BBS 的 ColumnResizer 布局不跳过不可见元素，setVisible(false) 仍占位，
         * 隐藏时必须 removeFromParent，否则面板上留一块空白。 */

        this.bbspp_cml$refreshBoneTextureButton();
        this.bbspp_cml$refreshGlintButton();
    }

    /** 换姿势 / 换选中的骨骼时，把开关与颜色同步成当前骨骼的实际状态。 */
    @Inject(method = "setPose", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncGlintOnSetPose(Pose pose, String group, CallbackInfo ci)
    {
        this.bbspp_cml$syncGlintToggle();
    }

    @Inject(method = "selectBone(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncGlintOnSelectBone(String bone, CallbackInfo ci)
    {
        this.bbspp_cml$syncGlintToggle();
    }

    @Inject(method = "selectBone(Ljava/lang/String;Z)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncGlintOnSelectBone2(String bone, boolean value, CallbackInfo ci)
    {
        this.bbspp_cml$syncGlintToggle();
    }

    @Unique
    private void bbspp_cml$syncGlintToggle()
    {
        if (this.bbspp_cml$glint == null)
        {
            return;
        }

        UIPoseEditor self = (UIPoseEditor) (Object) this;
        PoseTransform current = this.bbspp_cml$getCurrentPoseTransform(self);
        boolean on = current != null && ((GlintHolder) current).bbspp_cml$getGlint();

        /* UIToggle.setValue() 只写字段不触发回调，同步是安全的。 */
        this.bbspp_cml$glint.setValue(on);

        if (this.bbspp_cml$glintColor != null)
        {
            this.bbspp_cml$glintColor.setColor(current == null
                ? 0xFFFFFFFF
                : ((GlintHolder) current).bbspp_cml$getGlintColor().getARGBColor());
        }
    }

    @Unique
    private void bbspp_cml$openBoneTexturePicker(UIPoseEditor self)
    {
        PoseTransform current = this.bbspp_cml$getCurrentPoseTransform(self);
        Link existing = current == null ? null : ((BoneTextureHolder) current).bbspp_cml$getTexture();

        /* 用 UIContext 重载（picker.full(overlay)），使选择器像其它纹理选择一样覆盖全屏 overlay，
         * 而非嵌入姿态编辑器内的小区域。 */
        UIContext context = self.getContext();

        if (context == null)
        {
            return;
        }

        UITexturePicker.open(context, existing, (newTexture) ->
        {
            this.bbspp_cml$applyTextureToSelection(self, newTexture);
        });
    }

    @Unique
    private PoseTransform bbspp_cml$getCurrentPoseTransform(UIPoseEditor self)
    {
        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return null;
        }

        try
        {
            return self.getPose().get(bone);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 通用写入分派（骨骼纹理与附魔光效共用），按宿主选择写入路径：
     * <ul>
     *   <li>{@link UIPoseKeyframeFactory.UIPoseFactoryEditor}（pose 关键帧属性面板）——
     *       经静态 {@code apply} 对<b>所有选中关键帧</b>生效 + 关键帧级通知/undo；</li>
     *   <li>{@link UIModelPoseEditor}（伪装编辑界面）—— 经 valuePose 的
     *       preNotify/postNotify(FLAG_UNMERGEABLE)，进 undo 记录并标记 form 变更；</li>
     *   <li>其它宿主 —— 直接写。</li>
     * </ul>
     * 无选中骨骼时 no-op。
     */
    @Unique
    private void bbspp_cml$writeToSelection(UIPoseEditor self, Consumer<PoseTransform> writer)
    {
        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        if (self instanceof UIPoseKeyframeFactory.UIPoseFactoryEditor)
        {
            UIPoseFactoryEditorAccessor accessor = (UIPoseFactoryEditorAccessor) self;

            UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(accessor.bbspp_cml$getEditor(), accessor.bbspp_cml$getKeyframe(), bone, writer);

            return;
        }

        /* 骨骼在 Pose 里可能<b>还没有条目</b>：`Pose` 只有 `get(String)` 一个取值方法，
         * 没有自动创建的重载，从未被改动过的骨骼会返回 null。
         * 若此时直接 return，写入会被整个跳过 —— 表现为"开关只在界面里翻转，
         * 退出页面就变回默认关，渲染端永远拿不到值"。
         * 这里补建一个空的 PoseTransform（即恒等变换，与手动拖动骨骼时 BBS 的行为一致），
         * 让值真正落进数据链。骨骼纹理按钮此前也有同样的问题。 */
        Pose pose = self.getPose();
        PoseTransform poseTransform = pose == null ? null : pose.get(bone);

        if (poseTransform == null && pose != null)
        {
            poseTransform = new PoseTransform();
            pose.transforms.put(bone, poseTransform);
        }

        if (poseTransform == null)
        {
            return;
        }

        ValuePose valuePose = self instanceof UIModelPoseEditor
            ? ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose()
            : null;

        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        writer.accept(poseTransform);

        if (valuePose != null)
        {
            valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        /* 防御：编辑器里的 pose 与 valuePose.get() 若<b>不是</b>同一个对象，
         * 写编辑器这份是没用的 —— 退出页面重新 setPose 会用 valuePose 里的旧数据覆盖。
         * 这里镜像写一份到权威数据上，保证退出后不丢。实测两者是同一对象，此分支平时不生效。 */
        Pose live = valuePose == null ? null : valuePose.get();

        if (live != null && live != pose)
        {
            PoseTransform liveTransform = live.get(bone);

            if (liveTransform == null)
            {
                liveTransform = new PoseTransform();
                live.transforms.put(bone, liveTransform);
            }

            writer.accept(liveTransform);
        }
    }

    @Unique
    private void bbspp_cml$applyTextureToSelection(UIPoseEditor self, Link texture)
    {
        Link copied = texture == null ? null : LinkUtils.copy(texture);

        this.bbspp_cml$writeToSelection(self, (poseT) ->
        {
            /* 每个目标骨骼单独复制一份 Link，避免多个关键帧共享同一实例。 */
            ((BoneTextureHolder) poseT).bbspp_cml$setTexture(copied == null ? null : LinkUtils.copy(copied));
        });
    }

    /** 把开关状态写进当前选中的骨骼；无选中骨骼则 no-op。 */
    @Unique
    private void bbspp_cml$applyGlintToSelection(UIPoseEditor self, boolean value)
    {
        this.bbspp_cml$writeToSelection(self, (poseT) ->
        {
            ((GlintHolder) poseT).bbspp_cml$setGlint(value);
        });
    }

    /** 把颜色写进当前选中的骨骼；无选中骨骼则 no-op。 */
    @Unique
    private void bbspp_cml$applyGlintColorToSelection(UIPoseEditor self, int argb)
    {
        this.bbspp_cml$writeToSelection(self, (poseT) ->
        {
            ((GlintHolder) poseT).bbspp_cml$getGlintColor().set(argb);
        });
    }

    /**
     * 右键「应用到子骨骼」（开关用）：把开关当前状态应用到所有选中骨骼的全部子骨骼。
     * 语义与 FS 原版定位/颜色/光照的右键菜单完全一致（当前骨骼的值由控件本身给出，
     * 菜单只负责扩散到子骨骼）。
     *
     * <p>与原版 {@code applyChildren} 的差别：原版直接 {@code pose.get(child)} 后就交给
     * 回调，骨骼在 Pose 里没有条目时拿到 null 会 NPE；这里补建条目，保证
     * "一条肢体链上从未动过的子骨骼"也能被正确打开光效 —— 这恰是本功能的主要使用场景。</p>
     */
    @Unique
    private void bbspp_cml$applyGlintToChildren(UIPoseEditor self, boolean value)
    {
        this.bbspp_cml$forEachSelectedChildren(self, (poseT) ->
        {
            ((GlintHolder) poseT).bbspp_cml$setGlint(value);
        });
    }

    /** 右键「应用到子骨骼」（颜色用）：把取色器当前颜色应用到所有选中骨骼的全部子骨骼。 */
    @Unique
    private void bbspp_cml$applyGlintColorToChildren(UIPoseEditor self)
    {
        if (this.bbspp_cml$glintColor == null)
        {
            return;
        }

        int argb = this.bbspp_cml$glintColor.picker.color.getARGBColor();

        this.bbspp_cml$forEachSelectedChildren(self, (poseT) ->
        {
            ((GlintHolder) poseT).bbspp_cml$getGlintColor().set(argb);
        });
    }

    /**
     * 遍历所有选中骨骼的子骨骼并应用回调。
     * 与原版 {@code UIPoseEditor.applyChildren} 的差别：原版直接 {@code pose.get(child)}
     * 交给回调，子骨骼在 Pose 里没有条目时会拿到 null → NPE；这里补建条目，
     * 保证"一条肢体链上从未动过的子骨骼"也能被正确写入。
     */
    @Unique
    private void bbspp_cml$forEachSelectedChildren(UIPoseEditor self, Consumer<PoseTransform> writer)
    {
        Pose pose = self.getPose();

        if (pose == null || this.model == null)
        {
            return;
        }

        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        ValuePose valuePose = self instanceof UIModelPoseEditor
            ? ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose()
            : null;

        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        Collection<String> children = this.model.getAllChildrenKeys(bone);

        if (children != null)
        {
            for (String child : children)
            {
                PoseTransform transform = pose.get(child);

                if (transform == null)
                {
                    transform = new PoseTransform();
                    pose.transforms.put(child, transform);
                }

                writer.accept(transform);
            }
        }

        if (valuePose != null)
        {
            valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        this.bbspp_cml$syncGlintToggle();
    }

    @Unique
    private void bbspp_cml$refreshGlintButton()
    {
        if (this.bbspp_cml$glint == null)
        {
            return;
        }

        UIPoseEditor self = (UIPoseEditor) (Object) this;
        boolean enabled = CMLSettings.enchantGlint != null && CMLSettings.enchantGlint.get();

        /* BBS ColumnResizer 布局不跳过不可见元素：隐藏时必须真的从父容器移除，
         * 否则即使 setVisible(false) 也会在面板上留一块空白。 */
        if (enabled)
        {
            if (this.bbspp_cml$glint.getParent() == null)
            {
                self.add(this.bbspp_cml$glint);
            }

            if (this.bbspp_cml$glintColor != null && this.bbspp_cml$glintColor.getParent() == null)
            {
                self.add(this.bbspp_cml$glintColor);
            }

            this.bbspp_cml$glint.setVisible(true);

            if (this.bbspp_cml$glintColor != null)
            {
                this.bbspp_cml$glintColor.setVisible(true);
            }

            this.bbspp_cml$syncGlintToggle();
        }
        else
        {
            if (this.bbspp_cml$glint.getParent() != null)
            {
                this.bbspp_cml$glint.removeFromParent();
            }

            if (this.bbspp_cml$glintColor != null && this.bbspp_cml$glintColor.getParent() != null)
            {
                this.bbspp_cml$glintColor.removeFromParent();
            }
        }
    }
    @Unique
    private void bbspp_cml$refreshBoneTextureButton()
    {
        if (this.bbspp_cml$pickTexture == null)
        {
            return;
        }

        boolean enabled = CMLSettings.pickLimbTexture != null && CMLSettings.pickLimbTexture.get();

        this.bbspp_cml$pickTexture.setVisible(enabled);
    }
}
