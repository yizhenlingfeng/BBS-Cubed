package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import gbeic.bbsplusplus.api.ModelFormUvSectionHolder;
import gbeic.bbsplusplus.api.PickTextureButtonHolder;
import gbeic.bbsplusplus.client.outline.OutlineEmissiveTexture;
import gbeic.bbsplusplus.settings.ModSettings;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.Direction;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 在 {@link UIPoseEditor} 的材质栏下方追加骨骼纹理按钮，以及 PBR / 描边 / 附魔光效三个折叠栏，
 * 均作用于当前选中骨骼。
 *
 * <p><b>骨骼纹理</b> —— 点开按钮弹出 {@link UITexturePicker}，选择纹理后写回当前选中骨骼对应
 * {@link PoseTransform} 的 texture 字段（经 {@link BoneTextureHolder} 接口）。
 * 默认无选中骨骼则 no-op；受 {@code ModSettings.pickLimbTexture} 开关控制（与 CML 行为一致）。</p>
 *
 * <p><b>PBR / 描边 / 附魔光效</b> —— 分别经 {@link BonePbrHolder}、{@link BoneOutlineHolder}、
 * {@link GlintHolder} 等接口读写当前选中骨骼；这些栏始终显示，不再依赖模组设置开关。
 * 骨骼纹理按钮与三个折叠栏共用同一套写入分派（{@link #bbspp_cml$writeToSelection}）。</p>
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

    /* 材质栏：PBR 折叠栏与骨骼纹理按钮需要紧跟在它后面插入。 */
    @Shadow(remap = false)
    public UISection material;

    @Unique
    private UIButton bbspp_cml$pickTexture;

    /** 按钮已被外部宿主接管（模型形态页顶部），重建布局时不要收回到姿态面板。 */
    @Unique
    private boolean bbspp_cml$pickTextureDetached;

    @Unique
    private UIToggle bbspp_cml$glint;

    @Unique
    private UIColor bbspp_cml$glintColor;

    @Unique
    private UITrackpad bbspp_cml$glintSpeed;

    @Unique
    private UITrackpad bbspp_cml$glintBrightness;

    /* PBR 折叠栏：五个滑条，与材质栏同构，插在材质栏与骨骼纹理按钮之间。 */
    @Unique
    private UISection bbspp_cml$pbrSection;

    @Unique
    private UISection bbspp_cml$outlineSection;

    @Unique
    private UIToggle bbspp_cml$outline;

    @Unique
    private UIColor bbspp_cml$outlineColor;

    @Unique
    private UITrackpad bbspp_cml$outlineWidth;

    @Unique
    private UITrackpad bbspp_cml$outlineGlow;

    @Unique
    private UITrackpad bbspp_cml$outlineDecal;

    @Unique
    private UISection bbspp_cml$glintSection;

    @Unique
    private UISliderTrackpad bbspp_cml$pbrSmoothness;

    @Unique
    private UISliderTrackpad bbspp_cml$pbrMetallic;

    @Unique
    private UISliderTrackpad bbspp_cml$pbrSss;

    @Unique
    private UISliderTrackpad bbspp_cml$pbrEmission;

    @Unique
    private UISliderTrackpad bbspp_cml$pbrRelief;

    @Override
    public UIButton bbspp_cml$getPickTextureButton()
    {
        return this.bbspp_cml$pickTexture;
    }

    @Override
    public void bbspp_cml$setPickTextureButtonDetached(boolean detached)
    {
        this.bbspp_cml$pickTextureDetached = detached;

        if (detached && this.bbspp_cml$pickTexture != null)
        {
            this.bbspp_cml$pickTexture.removeFromParent();
        }
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

        /* 流动速度 / 亮度：与颜色同级；右键"应用到子骨骼"沿用同一套扩散逻辑。 */
        this.bbspp_cml$glintSpeed = this.bbspp_cml$createGlintTrackpad(self,
            GlintSettingsHolder.MAX_SPEED,
            L10n.lang("bbs.ui.pose.glint_speed.tooltip"),
            (poseT, v) -> ((GlintSettingsHolder) poseT).bbspp_cml$setGlintSpeed(v));
        this.bbspp_cml$glintBrightness = this.bbspp_cml$createGlintTrackpad(self,
            GlintSettingsHolder.MAX_BRIGHTNESS,
            L10n.lang("bbs.ui.pose.glint_brightness.tooltip"),
            (poseT, v) -> ((GlintSettingsHolder) poseT).bbspp_cml$setGlintBrightness(v));

        /* ---- PBR 折叠栏 ---- */
        this.bbspp_cml$pbrSmoothness = this.bbspp_cml$createPbrSlider(self, (poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setSmoothness(v));
        this.bbspp_cml$pbrMetallic = this.bbspp_cml$createPbrSlider(self, (poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setMetallic(v));
        this.bbspp_cml$pbrSss = this.bbspp_cml$createPbrSlider(self, (poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setSss(v));
        this.bbspp_cml$pbrEmission = this.bbspp_cml$createPbrSlider(self, (poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setEmission(v));
        this.bbspp_cml$pbrRelief = this.bbspp_cml$createPbrSlider(self, (poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setRelief(v));

        this.bbspp_cml$pbrSection = new UISection(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_PBR);
        this.bbspp_cml$pbrSection.setExpanded(false);
        this.bbspp_cml$pbrSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_SMOOTHNESS, this.bbspp_cml$pbrSmoothness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_METALLIC, this.bbspp_cml$pbrMetallic),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_SSS, this.bbspp_cml$pbrSss),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_PIXEL_EMISSION, this.bbspp_cml$pbrEmission),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_RELIEF, this.bbspp_cml$pbrRelief)
        );

        /* ---- 描边折叠栏（逐骨骼，插在 PBR 下方） ---- */
        this.bbspp_cml$outline = new UIToggle(L10n.lang("bbs.ui.model_editor.outline.all"), false, (toggle) ->
        {
            this.bbspp_cml$applyOutlineToSelection(self, toggle.getValue());
        });
        this.bbspp_cml$outline.h(20);

        /* 与光效/定位一致：开关右键可把开启状态扩散到整条肢体链。 */
        this.bbspp_cml$outline.context((ContextMenuManager manager) ->
        {
            manager.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.bbspp_cml$applyOutlineToChildren(self, this.bbspp_cml$outline.getValue());
            });
        });

        this.bbspp_cml$outlineColor = new UIColor((argb) ->
        {
            this.bbspp_cml$applyOutlineColorToSelection(self, argb == null ? 0xFFFFFFFF : argb);
            OutlineEmissiveTexture.invalidate();
        });
        this.bbspp_cml$outlineColor.withAlpha().direction(Direction.LEFT);
        this.bbspp_cml$outlineColor.h(20);

        this.bbspp_cml$outlineColor.context((ContextMenuManager manager) ->
        {
            manager.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.bbspp_cml$applyOutlineColorToChildren(self);
            });
        });

        this.bbspp_cml$outlineWidth = this.bbspp_cml$createOutlineTrackpad(self, 0D, 10D,
            L10n.lang("bbs.ui.model_editor.outline.width.tooltip"),
            (poseT, v) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineWidth(v));
        this.bbspp_cml$outlineGlow = this.bbspp_cml$createOutlineTrackpad(self, 0D, 255D,
            L10n.lang("bbs.ui.model_editor.outline.glow.tooltip"),
            (poseT, v) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineGlow(v));
        this.bbspp_cml$outlineDecal = this.bbspp_cml$createOutlineTrackpad(self, 0D, 16D,
            L10n.lang("bbs.ui.model_editor.outline.decal_size.tooltip"),
            (poseT, v) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineDecalSize(v));

        this.bbspp_cml$outlineSection = new UISection(L10n.lang("bbs.ui.model_editor.outline"));
        this.bbspp_cml$outlineSection.setExpanded(false);
        this.bbspp_cml$outlineSection.fields.add(
            UI.row(this.bbspp_cml$outline),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.color"), this.bbspp_cml$outlineColor),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.width"), this.bbspp_cml$outlineWidth),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.glow"), this.bbspp_cml$outlineGlow),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.decal_size"), this.bbspp_cml$outlineDecal)
        );

        /* ---- 附魔光效折叠栏（插在描边下方） ---- */
        this.bbspp_cml$glintSection = new UISection(L10n.lang("bbs.ui.pose.section_glint"));
        this.bbspp_cml$glintSection.setExpanded(false);
        this.bbspp_cml$glintSection.fields.add(
            UI.row(this.bbspp_cml$glint),
            UI.labelRow(L10n.lang("bbs.ui.pose.glint_color"), this.bbspp_cml$glintColor),
            UI.labelRow(L10n.lang("bbs.ui.pose.glint_speed"), this.bbspp_cml$glintSpeed),
            UI.labelRow(L10n.lang("bbs.ui.pose.glint_brightness"), this.bbspp_cml$glintBrightness)
        );

        /* PBR / 描边 / 光效栏与骨骼纹理按钮插入到材质栏正下方，而非面板末尾。 */
        this.bbspp_cml$attachAfterMaterial();

        this.bbspp_cml$syncGlintToggle();
        this.bbspp_cml$refreshBoneTextureButton();
        this.bbspp_cml$syncOutlineControls();
        this.bbspp_cml$syncAutoExpand();
    }

    /** 换姿势 / 换选中的骨骼时，把开关与颜色同步成当前骨骼的实际状态。 */
    @Inject(method = "setPose", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncGlintOnSetPose(Pose pose, String group, CallbackInfo ci)
    {
        this.bbspp_cml$syncGlintToggle();
        this.bbspp_cml$syncPbrSliders();
        this.bbspp_cml$syncOutlineControls();
        this.bbspp_cml$syncAutoExpand();
    }

    /**
     * buildLayout 开头做了 removeAll() 重建布局，会把我们挂载的元素清掉。
     * 每次布局重建后重新插入到材质栏正下方。构造函数内首次 buildLayout 时
     * PBR 栏尚未创建（null），自动跳过。
     */
    @Inject(method = "buildLayout", at = @At("RETURN"), remap = false)
    private void bbspp_cml$reattachAfterBuildLayout(boolean wide, CallbackInfo ci)
    {
        if (this.bbspp_cml$pbrSection == null)
        {
            return;
        }

        this.bbspp_cml$attachAfterMaterial();
        this.bbspp_cml$syncGlintToggle();
        this.bbspp_cml$refreshBoneTextureButton();
        this.bbspp_cml$syncOutlineControls();
        this.bbspp_cml$syncAutoExpand();
    }

    /**
     * 把 PBR、描边、附魔光效折叠栏与骨骼纹理按钮插入到材质栏正下方。
     * 通过 material.getParent() 统一拿到直接父容器，wide / narrow 两种布局均适用。
     */
    @Unique
    private void bbspp_cml$attachAfterMaterial()
    {
        UIPoseEditor self = (UIPoseEditor) (Object) this;

        if (self.material == null || this.bbspp_cml$pbrSection == null || this.bbspp_cml$outlineSection == null)
        {
            return;
        }

        /* material 只在非 poseOnly 布局里挂到父容器；模型编辑器的 poseOnly
         * 布局只有骨骼可见性与 transform 两个字段，此时退化为以 transform
         * 为锚点插入，否则这些栏在模型编辑器里永远不会出现。 */
        UIElement anchor = self.material;
        UIElement parent = anchor.getParent();

        if (parent == null)
        {
            anchor = self.transform;
            parent = anchor == null ? null : anchor.getParent();
        }

        if (parent == null || anchor == null)
        {
            return;
        }

        /* 先从旧父容器移除（如果已经挂载过），避免重复。
         * 被外部宿主接管的骨骼纹理按钮不在此列：它现在属于顶部按钮行，
         * 这里再移除会把按钮从行里拽走。 */
        for (UIElement element : new UIElement[] {
            this.bbspp_cml$pbrSection, this.bbspp_cml$outlineSection,
            this.bbspp_cml$glintSection, this.bbspp_cml$pickTexture
        })
        {
            if (element != null && element.getParent() != null
                && !(element == this.bbspp_cml$pickTexture && this.bbspp_cml$pickTextureDetached))
            {
                element.removeFromParent();
            }
        }

        /* 在锚点（材质栏或 poseOnly 的 transform）后面依次插入。 */
        parent.addAfter(anchor, this.bbspp_cml$pbrSection);
        parent.addAfter(this.bbspp_cml$pbrSection, this.bbspp_cml$outlineSection);

        UIElement last = this.bbspp_cml$outlineSection;

        if (this.bbspp_cml$glintSection != null)
        {
            parent.addAfter(last, this.bbspp_cml$glintSection);
            last = this.bbspp_cml$glintSection;
        }

        /* 骨骼纹理按钮接在最后一个折叠栏之后。 */
        if (this.bbspp_cml$pickTexture != null && !this.bbspp_cml$pickTextureDetached)
        {
            parent.addAfter(last, this.bbspp_cml$pickTexture);
        }
    }

    @Inject(method = "selectBone(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncGlintOnSelectBone(String bone, CallbackInfo ci)
    {
        this.bbspp_cml$syncGlintToggle();
        this.bbspp_cml$syncPbrSliders();
        this.bbspp_cml$syncOutlineControls();
        this.bbspp_cml$syncAutoExpand();
    }

    @Inject(method = "selectBone(Ljava/lang/String;Z)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncGlintOnSelectBone2(String bone, boolean value, CallbackInfo ci)
    {
        this.bbspp_cml$syncGlintToggle();
        this.bbspp_cml$syncPbrSliders();
        this.bbspp_cml$syncOutlineControls();
        this.bbspp_cml$syncAutoExpand();
    }

    /** 骨骼列表自动选中首根骨骼时不经过 selectBone()，在此统一刷新面板控件。 */
    @Inject(method = "pickBones(Ljava/util/List;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncOnPickBones(List<String> bones, CallbackInfo ci)
    {
        this.bbspp_cml$syncGlintToggle();
        this.bbspp_cml$syncPbrSliders();
        this.bbspp_cml$syncOutlineControls();
        this.bbspp_cml$syncAutoExpand();
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

        GlintSettingsHolder tuning = current == null ? null : (GlintSettingsHolder) current;

        if (this.bbspp_cml$glintSpeed != null)
        {
            this.bbspp_cml$glintSpeed.setValue(tuning == null
                ? GlintSettingsHolder.DEFAULT_SPEED
                : tuning.bbspp_cml$getGlintSpeed());
        }

        if (this.bbspp_cml$glintBrightness != null)
        {
            this.bbspp_cml$glintBrightness.setValue(tuning == null
                ? GlintSettingsHolder.DEFAULT_BRIGHTNESS
                : tuning.bbspp_cml$getGlintBrightness());
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

    /**
     * 模型编辑器里真正被保存的是 {@link UIModelPoseEditor} 持有的 {@code ValuePose.get()}，
     * 而不是 {@code UIPoseEditor.getPose()}。两者正常情况下是同一个对象，但模型编辑器
     * 每次 fill 都会用 {@code shownPose().get()} 重新 setPose，若我们只写编辑器那份，
     * 退出/重开时会被权威值覆盖。
     */
    @Unique
    private Pose bbspp_cml$getAuthoritativePose(UIPoseEditor self)
    {
        if (self instanceof UIModelPoseEditor)
        {
            ValuePose valuePose = ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose();

            if (valuePose != null && valuePose.get() != null)
            {
                return valuePose.get();
            }
        }

        return self.getPose();
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
            Pose pose = this.bbspp_cml$getAuthoritativePose(self);

            return pose == null ? null : pose.get(bone);
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
        Pose editorPose = self.getPose();
        ValuePose valuePose = self instanceof UIModelPoseEditor
            ? ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose()
            : null;
        Pose livePose = valuePose == null ? null : valuePose.get();
        Pose targetPose = livePose != null ? livePose : editorPose;

        if (targetPose == null)
        {
            return;
        }

        /* 通知窗口必须包住全部写入：ValuePose 的监听器/撤销/保存都在 preNotify/postNotify
         * 之间收集变化，postNotify 之后再补写不会被看到。 */
        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        PoseTransform target = targetPose.get(bone);

        if (target == null)
        {
            target = new PoseTransform();
            targetPose.transforms.put(bone, target);
        }

        writer.accept(target);

        /* 编辑器缓存与权威数据不是同一个对象时，镜像写一份；正常同对象时不会重复。 */
        if (editorPose != null && editorPose != targetPose)
        {
            PoseTransform editorTransform = editorPose.get(bone);

            if (editorTransform == null)
            {
                editorTransform = new PoseTransform();
                editorPose.transforms.put(bone, editorTransform);
            }

            writer.accept(editorTransform);
        }

        if (valuePose != null)
        {
            valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
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
        Pose editorPose = self.getPose();
        ValuePose valuePose = self instanceof UIModelPoseEditor
            ? ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose()
            : null;
        Pose livePose = valuePose == null ? null : valuePose.get();

        if ((editorPose == null && livePose == null) || this.model == null)
        {
            return;
        }

        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        Collection<String> children = this.model.getAllChildrenKeys(bone);

        if (children != null)
        {
            for (String child : children)
            {
                /* 先写权威数据（真正被保存的那一份），再镜像到编辑器缓存。 */
                if (livePose != null)
                {
                    PoseTransform transform = livePose.get(child);

                    if (transform == null)
                    {
                        transform = new PoseTransform();
                        livePose.transforms.put(child, transform);
                    }

                    writer.accept(transform);
                }

                if (editorPose != null && editorPose != livePose)
                {
                    PoseTransform transform = editorPose.get(child);

                    if (transform == null)
                    {
                        transform = new PoseTransform();
                        editorPose.transforms.put(child, transform);
                    }

                    writer.accept(transform);
                }
            }
        }

        if (valuePose != null)
        {
            valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        this.bbspp_cml$syncGlintToggle();
        this.bbspp_cml$syncOutlineControls();
    }

    @Unique
    private void bbspp_cml$syncOutlineControls()
    {
        if (this.bbspp_cml$outline == null)
        {
            return;
        }

        UIPoseEditor self = (UIPoseEditor) (Object) this;
        PoseTransform current = this.bbspp_cml$getCurrentPoseTransform(self);
        BoneOutlineHolder holder = current == null ? null : (BoneOutlineHolder) current;

        this.bbspp_cml$outline.setValue(holder != null && holder.bbspp_cml$getOutline());
        this.bbspp_cml$outlineColor.setColor(holder == null
            ? 0xFFFFFFFF
            : holder.bbspp_cml$getOutlineColor().getARGBColor());
        this.bbspp_cml$outlineWidth.setValue(holder == null
            ? BoneOutlineHolder.DEFAULT_WIDTH
            : holder.bbspp_cml$getOutlineWidth());
        this.bbspp_cml$outlineGlow.setValue(holder == null
            ? BoneOutlineHolder.DEFAULT_GLOW
            : holder.bbspp_cml$getOutlineGlow());
        this.bbspp_cml$outlineDecal.setValue(holder == null
            ? BoneOutlineHolder.DEFAULT_DECAL
            : holder.bbspp_cml$getOutlineDecalSize());
    }

    @Unique
    private void bbspp_cml$applyOutlineToSelection(UIPoseEditor self, boolean value)
    {
        this.bbspp_cml$writeToSelection(self, (poseT) ->
        {
            ((BoneOutlineHolder) poseT).bbspp_cml$setOutline(value);
        });
    }

    @Unique
    private void bbspp_cml$applyOutlineColorToSelection(UIPoseEditor self, int argb)
    {
        this.bbspp_cml$writeToSelection(self, (poseT) ->
        {
            ((BoneOutlineHolder) poseT).bbspp_cml$getOutlineColor().set(argb);
        });
    }

    @Unique
    private void bbspp_cml$applyOutlineToChildren(UIPoseEditor self, boolean value)
    {
        this.bbspp_cml$forEachSelectedChildren(self, (poseT) ->
        {
            ((BoneOutlineHolder) poseT).bbspp_cml$setOutline(value);
        });
    }

    @Unique
    private void bbspp_cml$applyOutlineColorToChildren(UIPoseEditor self)
    {
        if (this.bbspp_cml$outlineColor == null)
        {
            return;
        }

        int argb = this.bbspp_cml$outlineColor.picker.color.getARGBColor();

        this.bbspp_cml$forEachSelectedChildren(self, (poseT) ->
        {
            ((BoneOutlineHolder) poseT).bbspp_cml$getOutlineColor().set(argb);
        });

        OutlineEmissiveTexture.invalidate();
    }

    /** 光效参数滑条：0..max，步进 0.05，右键应用到子骨骼。 */
    @Unique
    private UITrackpad bbspp_cml$createGlintTrackpad(UIPoseEditor self, float max, IKey tooltip,
                                                     BiConsumer<PoseTransform, Float> setter)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            float value = v.floatValue();
            this.bbspp_cml$writeToSelection(self, (poseT) -> setter.accept(poseT, value));
        });

        trackpad.limit(0D, max);
        trackpad.increment(0.05D);
        trackpad.delayedInput();
        trackpad.tooltip(tooltip, Direction.LEFT);

        trackpad.context((ContextMenuManager manager) ->
        {
            manager.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                float value = (float) trackpad.getValue();
                this.bbspp_cml$forEachSelectedChildren(self, (poseT) -> setter.accept(poseT, value));
            });
        });

        return trackpad;
    }

    @Unique
    private UITrackpad bbspp_cml$createOutlineTrackpad(UIPoseEditor self, double min, double max, IKey tooltip,
                                                       BiConsumer<PoseTransform, Float> setter)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            float value = v.floatValue();
            this.bbspp_cml$writeToSelection(self, (poseT) -> setter.accept(poseT, value));
        });

        trackpad.limit(min, max);
        trackpad.increment(1D);
        trackpad.delayedInput();
        trackpad.tooltip(tooltip, Direction.LEFT);

        trackpad.context((ContextMenuManager manager) ->
        {
            manager.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                float value = (float) trackpad.getValue();
                this.bbspp_cml$forEachSelectedChildren(self, (poseT) -> setter.accept(poseT, value));
            });
        });

        return trackpad;
    }

    @Unique
    private void bbspp_cml$refreshBoneTextureButton()
    {
        if (this.bbspp_cml$pickTexture == null)
        {
            return;
        }

        boolean enabled = ModSettings.pickLimbTexture != null && ModSettings.pickLimbTexture.get();

        this.bbspp_cml$pickTexture.setVisible(enabled);
    }

    /**
     * 创建一个 PBR 滑条：limit(0,1)，回调经 writeToSelection 写入当前选中骨骼，
     * 右键菜单"应用到子骨骼"。
     */
    @Unique
    private UISliderTrackpad bbspp_cml$createPbrSlider(UIPoseEditor self, BiConsumer<PoseTransform, Float> setter)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) ->
        {
            float value = v.floatValue();
            this.bbspp_cml$writeToSelection(self, (poseT) -> setter.accept(poseT, value));
        });

        slider.limit(0D, 1D);

        slider.context((ContextMenuManager manager) ->
        {
            manager.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                float value = (float) slider.getValue();
                this.bbspp_cml$forEachSelectedChildren(self, (poseT) -> setter.accept(poseT, value));
            });
        });

        return slider;
    }

    /** 换姿势 / 换选中骨骼时，把 PBR 五值滑条同步成当前骨骼的实际值。 */
    @Unique
    private void bbspp_cml$syncPbrSliders()
    {
        if (this.bbspp_cml$pbrSection == null)
        {
            return;
        }

        UIPoseEditor self = (UIPoseEditor) (Object) this;
        PoseTransform current = this.bbspp_cml$getCurrentPoseTransform(self);

        float smooth = current == null ? 0F : ((BonePbrHolder) current).bbspp_cml$getSmoothness();
        float metal = current == null ? 0F : ((BonePbrHolder) current).bbspp_cml$getMetallic();
        float sss = current == null ? 0F : ((BonePbrHolder) current).bbspp_cml$getSss();
        float emission = current == null ? 0F : ((BonePbrHolder) current).bbspp_cml$getEmission();
        float relief = current == null ? 0F : ((BonePbrHolder) current).bbspp_cml$getRelief();

        /* setValue() 只写字段不触发回调，同步是安全的。 */
        this.bbspp_cml$pbrSmoothness.setValue(smooth);
        this.bbspp_cml$pbrMetallic.setValue(metal);
        this.bbspp_cml$pbrSss.setValue(sss);
        this.bbspp_cml$pbrEmission.setValue(emission);
        this.bbspp_cml$pbrRelief.setValue(relief);
    }

    /**
     * 模型编辑页的“默认展开”策略：选中骨骼后，按该骨骼的 PoseTransform 决定材质 / PBR /
     * 描边 / 附魔光效四栏是否展开。纹理变换是模型级属性，由 {@code UIModelFormPanelMixin}
     * 在表单同步时处理。
     *
     * <p>只在 {@link UIModelPoseEditor} 宿主生效；影片姿势关键帧面板的 UIPoseEditor 也带
     * 这些栏，但它的栏位应对应当前关键帧，而不是模型编辑页的骨骼选择逻辑。</p>
     */
    @Unique
    private void bbspp_cml$autoExpandSections()
    {
        UIPoseEditor self = (UIPoseEditor) (Object) this;

        if (!(self instanceof UIModelPoseEditor))
        {
            return;
        }

        PoseTransform current = this.bbspp_cml$getCurrentPoseTransform(self);
        boolean material = false;
        boolean pbr = false;
        boolean outline = false;
        boolean glint = false;

        if (current != null)
        {
            material = current.color.getARGBColor() != 0xFFFFFFFF
                || current.overlay.r != 1F
                || current.overlay.g != 1F
                || current.overlay.b != 1F
                || current.overlay.a != 0F
                || current.lighting != 0F;

            BonePbrHolder pbrHolder = (BonePbrHolder) current;
            pbr = pbrHolder.bbspp_cml$getSmoothness() != 0F
                || pbrHolder.bbspp_cml$getMetallic() != 0F
                || pbrHolder.bbspp_cml$getSss() != 0F
                || pbrHolder.bbspp_cml$getEmission() != 0F
                || pbrHolder.bbspp_cml$getRelief() != 0F;

            BoneOutlineHolder outlineHolder = (BoneOutlineHolder) current;
            outline = outlineHolder.bbspp_cml$getOutline()
                || outlineHolder.bbspp_cml$getOutlineColor().getARGBColor() != 0xFFFFFFFF
                || outlineHolder.bbspp_cml$getOutlineWidth() != BoneOutlineHolder.DEFAULT_WIDTH
                || outlineHolder.bbspp_cml$getOutlineGlow() != BoneOutlineHolder.DEFAULT_GLOW
                || outlineHolder.bbspp_cml$getOutlineDecalSize() != BoneOutlineHolder.DEFAULT_DECAL;

            GlintHolder glintHolder = (GlintHolder) current;
            GlintSettingsHolder glintSettings = (GlintSettingsHolder) current;
            glint = glintHolder.bbspp_cml$getGlint()
                || glintHolder.bbspp_cml$getGlintColor().getARGBColor() != 0xFFFFFFFF
                || glintSettings.bbspp_cml$getGlintSpeed() != GlintSettingsHolder.DEFAULT_SPEED
                || glintSettings.bbspp_cml$getGlintBrightness() != GlintSettingsHolder.DEFAULT_BRIGHTNESS;
        }

        if (self.material != null)
        {
            self.material.setExpanded(material);
        }

        if (this.bbspp_cml$pbrSection != null)
        {
            this.bbspp_cml$pbrSection.setExpanded(pbr);
        }

        if (this.bbspp_cml$outlineSection != null)
        {
            this.bbspp_cml$outlineSection.setExpanded(outline);
        }

        if (this.bbspp_cml$glintSection != null)
        {
            this.bbspp_cml$glintSection.setExpanded(glint);
        }

        /* 纹理变换是模型级属性（模型编辑页表单面板），这里只需刷新它的默认展开状态。 */
        UIElement parent = self.getParent();

        while (parent != null)
        {
            if (parent instanceof ModelFormUvSectionHolder holder)
            {
                holder.bbspp$refreshUvSectionExpansion();

                break;
            }

            parent = parent.getParent();
        }
    }

    /** 刷新模型编辑页各栏的默认展开状态。 */
    @Unique
    private void bbspp_cml$syncAutoExpand()
    {
        this.bbspp_cml$autoExpandSections();
    }
}
