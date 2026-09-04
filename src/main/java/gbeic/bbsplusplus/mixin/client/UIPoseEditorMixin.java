package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.PickTextureButtonHolder;
import gbeic.bbsplusplus.api.PoseTextureGradeEditorHolder;
import gbeic.bbsplusplus.api.TextureGradeHolder;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
public abstract class UIPoseEditorMixin extends UIElement implements PickTextureButtonHolder, PoseTextureGradeEditorHolder
{
    @Unique
    private UIButton bbspp_cml$pickTexture;

    @Unique
    private UIColor bbspp_cml$textureTint;

    @Unique
    private UITrackpad bbspp_cml$textureWhiten;

    @Unique
    private UISection bbspp_cml$textureGradeSection;

    @Unique
    private boolean bbspp_cml$syncingTextureGrade;

    @Override
    public UIButton bbspp_cml$getPickTextureButton()
    {
        return this.bbspp_cml$pickTexture;
    }

    @Override
    public UIColor bbspp_cml$getTextureTintControl()
    {
        return this.bbspp_cml$textureTint;
    }

    @Override
    public UIElement bbspp_cml$getTextureGradeSection()
    {
        return this.bbspp_cml$textureGradeSection;
    }

    @Override
    public UITrackpad bbspp_cml$getTextureWhitenControl()
    {
        return this.bbspp_cml$textureWhiten;
    }

    @Override
    public void bbspp_cml$refreshTextureGradeControls()
    {
        UIPoseEditor self = (UIPoseEditor) (Object) this;
        List<String> bones = self.groups == null || self.groups.list == null
            ? java.util.Collections.emptyList()
            : new ArrayList<>(self.groups.list.getCurrent());

        this.bbspp_cml$syncTextureGradeControls(self, bones, false);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createBoneTextureButton(CallbackInfo ci)
    {
        UIPoseEditor self = (UIPoseEditor) (Object) this;

        this.bbspp_cml$pickTexture = new UIButton(L10n.lang("bbs.ui.pose.pick_bone_texture"), (b) ->
        {
            this.bbspp_cml$openBoneTexturePicker(self);
        });

        this.bbspp_cml$textureTint = new UIColor((color) ->
        {
            if (!this.bbspp_cml$syncingTextureGrade)
            {
                Color tint = new Color().set(color);
                PoseTransform current = this.bbspp_cml$getCurrentPoseTransform(self);

                /* A newly-created tint is transparent (alpha 0 means disabled). When the user
                 * changes RGB for the first time, make that edit visible immediately. Explicitly
                 * dragging the alpha slider back to 0 still works because RGB then stays unchanged. */
                if (tint.a <= 0.0001F && current instanceof TextureGradeHolder grade)
                {
                    Color previous = grade.bbspp_cml$getTextureTint();

                    if (previous.a <= 0.0001F && tint.getRGBColor() != previous.getRGBColor())
                    {
                        tint.a = 1F;
                        this.bbspp_cml$textureTint.setColor(tint.getARGBColor());
                    }
                }

                this.bbspp_cml$applyTextureTintToSelection(self, tint);
            }
        })
            .withAlpha()
            .direction(Direction.LEFT);
        this.bbspp_cml$textureTint.tooltip(L10n.lang("bbspp.ui.film.replays.texture_tint_tooltip"));

        this.bbspp_cml$textureWhiten = new UITrackpad((value) ->
        {
            if (!this.bbspp_cml$syncingTextureGrade)
            {
                this.bbspp_cml$applyTextureWhitenToSelection(self, value.floatValue());
            }
        });
        this.bbspp_cml$textureWhiten.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);
        this.bbspp_cml$textureWhiten.tooltip(L10n.lang("bbspp.ui.film.replays.texture_whiten_tooltip"));

        UISection textureGradeSection = new UISection(
            L10n.lang("bbspp.ui.film.replays.texture_grade_section"));
        textureGradeSection.fields.add(
            UI.label(L10n.lang("bbspp.ui.film.replays.texture_tint")),
            this.bbspp_cml$textureTint,
            UI.label(L10n.lang("bbspp.ui.film.replays.texture_whiten")),
            this.bbspp_cml$textureWhiten
        );
        textureGradeSection.setExpanded(false);
        this.bbspp_cml$textureGradeSection = textureGradeSection;
        this.bbspp_cml$textureGradeSection.setVisible(false);

        self.add(this.bbspp_cml$textureGradeSection, this.bbspp_cml$pickTexture);
        this.bbspp_cml$refreshBoneTextureButton();
    }

    @Inject(method = "pickBones(Ljava/util/List;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncTextureGradeControls(List<String> bones, CallbackInfo ci)
    {
        this.bbspp_cml$syncTextureGradeControls((UIPoseEditor) (Object) this, bones, true);
    }

    @Unique
    private void bbspp_cml$syncTextureGradeControls(
        UIPoseEditor self,
        List<String> bones,
        boolean resizeParent
    )
    {
        if (this.bbspp_cml$textureGradeSection == null)
        {
            return;
        }

        boolean hasSelection = bones != null && !bones.isEmpty() && self.getPose() != null;
        boolean keyframeEditor = self instanceof UIPoseKeyframeFactory.UIPoseFactoryEditor;
        boolean visible = keyframeEditor || hasSelection;

        if (this.bbspp_cml$textureGradeSection.isVisible() != visible)
        {
            this.bbspp_cml$textureGradeSection.setVisible(visible);

            if (resizeParent)
            {
                this.bbspp_cml$textureGradeSection.resizeParent();
            }
        }

        this.bbspp_cml$textureTint.setEnabled(hasSelection);
        this.bbspp_cml$textureWhiten.setEnabled(hasSelection);

        this.bbspp_cml$syncingTextureGrade = true;

        try
        {
            if (!hasSelection)
            {
                this.bbspp_cml$textureTint.setColor(0x00ffffff);
                this.bbspp_cml$textureWhiten.setValue(0D);
                return;
            }

            TextureGradeHolder grade = (TextureGradeHolder) self.getPose().get(bones.get(0));

            this.bbspp_cml$textureTint.setColor(grade.bbspp_cml$getTextureTint().getARGBColor());
            this.bbspp_cml$textureWhiten.setValue(grade.bbspp_cml$getTextureWhiten());
        }
        finally
        {
            this.bbspp_cml$syncingTextureGrade = false;
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
    private void bbspp_cml$applyTextureTintToSelection(UIPoseEditor self, Color tint)
    {
        this.bbspp_cml$applyTextureGradeToSelection(self,
            (grade) -> grade.bbspp_cml$setTextureTint(tint));
    }

    @Unique
    private void bbspp_cml$applyTextureWhitenToSelection(UIPoseEditor self, float whiten)
    {
        float value = Math.max(0F, Math.min(1F, whiten));

        this.bbspp_cml$applyTextureGradeToSelection(self,
            (grade) -> grade.bbspp_cml$setTextureWhiten(value));
    }

    @Unique
    private void bbspp_cml$applyTextureGradeToSelection(UIPoseEditor self, Consumer<TextureGradeHolder> consumer)
    {
        List<String> selectedBones = self.groups == null
            ? java.util.Collections.emptyList()
            : new ArrayList<>(self.groups.list.getCurrent());

        if (selectedBones.isEmpty())
        {
            return;
        }

        if (self instanceof UIPoseKeyframeFactory.UIPoseFactoryEditor)
        {
            UIPoseFactoryEditorAccessor accessor = (UIPoseFactoryEditorAccessor) self;

            UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(
                accessor.bbspp_cml$getEditor(), accessor.bbspp_cml$getKeyframe(), selectedBones,
                (poseTransform) -> consumer.accept((TextureGradeHolder) poseTransform)
            );
            return;
        }

        ValuePose valuePose = self instanceof UIModelPoseEditor
            ? ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose()
            : null;

        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        for (String bone : selectedBones)
        {
            consumer.accept((TextureGradeHolder) self.getPose().get(bone));
        }

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
