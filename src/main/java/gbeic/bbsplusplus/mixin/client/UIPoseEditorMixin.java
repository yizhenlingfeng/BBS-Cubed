package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.PickTextureButtonHolder;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@link UIPoseEditor} 中追加"骨骼纹理"按钮，供选中带骨骼的模型时切换骨骼纹理。
 *
 * <p>点开按钮弹出 {@link UITexturePicker}，选择纹理后写回当前选中骨骼对应
 * {@link PoseTransform} 的 texture 字段（经 {@link BoneTextureHolder} 接口）。
 * 默认无选中骨骼则 no-op；受 {@code CMLSettings.pickLimbTexture} 开关控制（与 CML 行为一致）。</p>
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
    @Unique
    private UIButton bbspp_cml$pickTexture;

    @Override
    public UIButton bbspp_cml$getPickTextureButton()
    {
        return this.bbspp_cml$pickTexture;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createBoneTextureButton(CallbackInfo ci)
    {
        UIPoseEditor self = (UIPoseEditor) (Object) this;

        this.bbspp_cml$pickTexture = new UIButton(L10n.lang("bbs.ui.pose.pick_bone_texture"), (b) ->
        {
            this.bbspp_cml$openBoneTexturePicker(self);
        });

        self.add(this.bbspp_cml$pickTexture);
        this.bbspp_cml$refreshBoneTextureButton();
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

    @Unique
    private void bbspp_cml$applyTextureToSelection(UIPoseEditor self, Link texture)
    {
        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        Link copied = texture == null ? null : LinkUtils.copy(texture);

        /* pose 关键帧宿主：经静态 apply 写入所有选中关键帧（含关键帧级通知/undo）。 */
        if (self instanceof UIPoseKeyframeFactory.UIPoseFactoryEditor)
        {
            UIPoseFactoryEditorAccessor accessor = (UIPoseFactoryEditorAccessor) self;

            UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(accessor.bbspp_cml$getEditor(), accessor.bbspp_cml$getKeyframe(), bone, (poseT) ->
            {
                ((BoneTextureHolder) poseT).bbspp_cml$setTexture(copied == null ? null : LinkUtils.copy(copied));
            });

            return;
        }

        PoseTransform poseTransform = this.bbspp_cml$getCurrentPoseTransform(self);

        if (poseTransform == null)
        {
            return;
        }

        /* 伪装编辑界面宿主：经 valuePose 通知，进 undo 记录并标记 form 数据已变更。 */
        ValuePose valuePose = self instanceof UIModelPoseEditor
            ? ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose()
            : null;

        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        ((BoneTextureHolder) poseTransform).bbspp_cml$setTexture(copied);

        if (valuePose != null)
        {
            valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
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
