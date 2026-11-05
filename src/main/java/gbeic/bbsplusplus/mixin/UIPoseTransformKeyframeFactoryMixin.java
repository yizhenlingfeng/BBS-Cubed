package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import gbeic.bbsplusplus.client.outline.OutlineEmissiveTexture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 在影片编辑器中，肢体轨道（BONE 类型）选中关键帧后打开的属性面板
 * 是 {@link UIPoseTransformKeyframeFactory}。它自带固定、可见、颜色、叠加色、光照五个控件，
 * 但缺少逐骨骼 PBR、描边与附魔光效控件。纹理变换是表单级属性，仅由模型编辑页提供，
 * 这里不再重复。
 *
 * <p>本 Mixin 在该面板构造完成后，于原版材质栏之后依次追加三个折叠栏：</p>
 * <ol>
 *   <li><b>PBR</b> —— 光泽度 / 金属度 / 散射 / 自发光 / 凹凸（0~1）；</li>
 *   <li><b>描边</b> —— 开关 / 颜色 / 宽度 / 发光 / 原版辉光 / 面剔除 / 不描边块尺寸；</li>
 *   <li><b>附魔光效</b> —— 开关 / 颜色 / 流动速度 / 亮度。</li>
 * </ol>
 *
 * <p><b>与姿势轨道面板的适配差异：</b></p>
 * <ul>
 *   <li>姿势轨道（{@code UIPoseFactoryEditor}，继承自 {@code UIPoseEditor}）——
 *       各栏作用于 3D 视口中<b>当前选中的骨骼</b>，由 {@code UIPoseEditorMixin} 实现；</li>
 *   <li>肢体轨道（本类）—— 关键帧值本身就是该骨骼的 {@code PoseTransform}，
 *       各栏直接读写当前关键帧，<b>无需骨骼选择</b>。</li>
 * </ul>
 *
 * <p>骨骼级写入路径复用原版 {@code UIPoseTransforms.apply(editor, keyframe, consumer)}，
 * 与固定 / 光照滑条走同一套关键帧通知与 undo 流程。</p>
 *
 * <p>本类 extends {@link UIKeyframeFactory}&lt;{@link PoseTransform}&gt; 以直接访问基类的
 * {@code scroll}、{@code keyframe}、{@code editor} 字段——Mixin @Shadow 默认不沿继承链查找，
 * 而这三个字段都声明在基类中（与 {@code UIPoseEditorMixin extends UIElement} 同理）。</p>
 */
@Mixin(value = UIPoseTransformKeyframeFactory.class, remap = true)
public abstract class UIPoseTransformKeyframeFactoryMixin extends UIKeyframeFactory<PoseTransform>
{
    /** 仅供编译器通过：Mixin 不会被实例化，super(null, null) 运行时不会执行。 */
    private UIPoseTransformKeyframeFactoryMixin()
    {
        super(null, null);
    }

    @Unique private UISection bbspp$pbrSection;
    @Unique private UISliderTrackpad bbspp$pbrSmoothness;
    @Unique private UISliderTrackpad bbspp$pbrMetallic;
    @Unique private UISliderTrackpad bbspp$pbrSss;
    @Unique private UISliderTrackpad bbspp$pbrEmission;
    @Unique private UISliderTrackpad bbspp$pbrRelief;

    @Unique private UISection bbspp$outlineSection;
    @Unique private UIToggle bbspp$outline;
    @Unique private UIColor bbspp$outlineColor;
    @Unique private UITrackpad bbspp$outlineWidth;
    @Unique private UITrackpad bbspp$outlineGlow;
    @Unique private UIToggle bbspp$outlineVanillaGlow;
    @Unique private UITrackpad bbspp$outlineDecal;
    @Unique private UIToggle bbspp$outlineFaceCull;

    @Unique private UISection bbspp$glintSection;
    @Unique private UIToggle bbspp$glint;
    @Unique private UIColor bbspp$glintColor;
    @Unique private UITrackpad bbspp$glintSpeed;
    @Unique private UITrackpad bbspp$glintBrightness;

    /**
     * 在构造器尾部追加 PBR / 描边 / 附魔光效三个折叠栏。
     * 原版最后 {@code scroll.add(...)} 放入四个控件，这里在其之后再 add 各 section，
     * 顺序为：可见 → 固定 → 变换 → 材质（颜色/叠加/光照）→ PBR → 描边 → 附魔光效。
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = true)
    private void bbspp$addExtraSections(Keyframe<PoseTransform> keyframe, UIKeyframes editor, CallbackInfo ci)
    {
        /* ---- PBR 折叠栏 ---- */
        this.bbspp$pbrSmoothness = this.bbspp$createPbrSlider((poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setSmoothness(v));
        this.bbspp$pbrMetallic  = this.bbspp$createPbrSlider((poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setMetallic(v));
        this.bbspp$pbrSss        = this.bbspp$createPbrSlider((poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setSss(v));
        this.bbspp$pbrEmission   = this.bbspp$createPbrSlider((poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setEmission(v));
        this.bbspp$pbrRelief     = this.bbspp$createPbrSlider((poseT, v) -> ((BonePbrHolder) poseT).bbspp_cml$setRelief(v));

        this.bbspp$pbrSection = new UISection(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_PBR);
        this.bbspp$pbrSection.setExpanded(false);
        this.bbspp$pbrSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_SMOOTHNESS,     this.bbspp$pbrSmoothness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_METALLIC,       this.bbspp$pbrMetallic),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_SSS,             this.bbspp$pbrSss),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_PIXEL_EMISSION, this.bbspp$pbrEmission),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_RELIEF,          this.bbspp$pbrRelief)
        );

        /* ---- 描边折叠栏 ---- */
        this.bbspp$outline = new UIToggle(L10n.lang("bbs.ui.model_editor.outline.all"), false, (toggle) ->
        {
            this.bbspp$write((poseT) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutline(toggle.getValue()));
        });
        this.bbspp$outline.h(20);

        this.bbspp$outlineColor = new UIColor((argb) ->
        {
            int value = argb == null ? 0xFFFFFFFF : argb;

            this.bbspp$write((poseT) -> ((BoneOutlineHolder) poseT).bbspp_cml$getOutlineColor().set(value));
            OutlineEmissiveTexture.invalidate();
        });
        this.bbspp$outlineColor.withAlpha().direction(Direction.LEFT).h(20);

        this.bbspp$outlineWidth = this.bbspp$createTrackpad(0D, 10D, 1D,
            L10n.lang("bbs.ui.model_editor.outline.width.tooltip"),
            (poseT, v) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineWidth(v));
        this.bbspp$outlineGlow = this.bbspp$createTrackpad(0D, 254D, 1D,
            L10n.lang("bbs.ui.model_editor.outline.glow.tooltip"),
            (poseT, v) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineGlow(v));
        this.bbspp$outlineVanillaGlow = new UIToggle(L10n.lang("bbs.ui.model_editor.outline.vanilla_glow"),
            false, (toggle) ->
        {
            float strength = ((UIToggle) toggle).getValue()
                ? BoneOutlineHolder.VANILLA_GLOW_ON
                : BoneOutlineHolder.DEFAULT_VANILLA_GLOW;

            this.bbspp$write((poseT) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineVanillaGlow(strength));
        });
        this.bbspp$outlineVanillaGlow.h(20);
        this.bbspp$outlineVanillaGlow.tooltip(
            L10n.lang("bbs.ui.model_editor.outline.vanilla_glow.tooltip"), Direction.LEFT);
        this.bbspp$outlineDecal = this.bbspp$createTrackpad(0D, 16D, 1D,
            L10n.lang("bbs.ui.model_editor.outline.decal_size.tooltip"),
            (poseT, v) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineDecalSize(v));
        this.bbspp$outlineFaceCull = new UIToggle(L10n.lang("bbs.ui.model_editor.outline.face_cull"),
            BoneOutlineHolder.DEFAULT_FACE_CULL, (toggle) ->
        {
            this.bbspp$write((poseT) -> ((BoneOutlineHolder) poseT).bbspp_cml$setOutlineFaceCull(toggle.getValue()));
        });
        this.bbspp$outlineFaceCull.h(20);
        this.bbspp$outlineFaceCull.tooltip(
            L10n.lang("bbs.ui.model_editor.outline.face_cull.tooltip"), Direction.LEFT);

        this.bbspp$outlineSection = new UISection(L10n.lang("bbs.ui.model_editor.outline"));
        this.bbspp$outlineSection.setExpanded(false);
        this.bbspp$outlineSection.fields.add(
            UI.row(this.bbspp$outline),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.color"), this.bbspp$outlineColor),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.width"), this.bbspp$outlineWidth),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.glow"), this.bbspp$outlineGlow),
            /* UIToggle 自带标签，套 labelRow 会把名字画两遍，所以独占一行。 */
            UI.row(this.bbspp$outlineVanillaGlow),
            UI.row(this.bbspp$outlineFaceCull),
            UI.labelRow(L10n.lang("bbs.ui.model_editor.outline.decal_size"), this.bbspp$outlineDecal)
        );

        /* ---- 附魔光效折叠栏 ---- */
        this.bbspp$glint = new UIToggle(L10n.lang("bbs.ui.pose.enchant_glint"), false, (toggle) ->
        {
            this.bbspp$write((poseT) -> ((GlintHolder) poseT).bbspp_cml$setGlint(toggle.getValue()));
        });
        this.bbspp$glint.h(16);

        this.bbspp$glintColor = new UIColor((argb) ->
        {
            int value = argb == null ? 0xFFFFFFFF : argb;

            this.bbspp$write((poseT) -> ((GlintHolder) poseT).bbspp_cml$getGlintColor().set(value));
        });
        this.bbspp$glintColor.withAlpha().h(16);

        this.bbspp$glintSpeed = this.bbspp$createTrackpad(0D, GlintSettingsHolder.MAX_SPEED, 0.05D,
            L10n.lang("bbs.ui.pose.glint_speed.tooltip"),
            (poseT, v) -> ((GlintSettingsHolder) poseT).bbspp_cml$setGlintSpeed(v));
        this.bbspp$glintBrightness = this.bbspp$createTrackpad(0D, GlintSettingsHolder.MAX_BRIGHTNESS, 0.05D,
            L10n.lang("bbs.ui.pose.glint_brightness.tooltip"),
            (poseT, v) -> ((GlintSettingsHolder) poseT).bbspp_cml$setGlintBrightness(v));

        this.bbspp$glintSection = new UISection(L10n.lang("bbs.ui.pose.section_glint"));
        this.bbspp$glintSection.setExpanded(false);
        this.bbspp$glintSection.fields.add(
            UI.row(this.bbspp$glint),
            UI.labelRow(L10n.lang("bbs.ui.pose.glint_color"), this.bbspp$glintColor),
            UI.labelRow(L10n.lang("bbs.ui.pose.glint_speed"), this.bbspp$glintSpeed),
            UI.labelRow(L10n.lang("bbs.ui.pose.glint_brightness"), this.bbspp$glintBrightness)
        );

        this.scroll.add(this.bbspp$pbrSection, this.bbspp$outlineSection, this.bbspp$glintSection);

        /* 用当前关键帧的 PoseTransform 值初始化各控件。 */
        this.bbspp$syncPbrSliders();
        this.bbspp$syncExtraSections();
    }

    /** 把写入统一派发到当前选中关键帧（原版已处理多选与 undo 通知）。 */
    @Unique
    private void bbspp$write(Consumer<PoseTransform> writer)
    {
        UIPoseTransformKeyframeFactory.UIPoseTransforms.apply(this.editor, this.keyframe, writer);
    }

    /**
     * 创建一个浮点滑条：限制在 [min, max]，按 increment 步进，经 {@code bbspp$write} 写入当前关键帧。
     */
    @Unique
    private UITrackpad bbspp$createTrackpad(double min, double max, double increment, IKey tooltip,
                                           BiConsumer<PoseTransform, Float> setter)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            float value = v.floatValue();
            this.bbspp$write((poseT) -> setter.accept(poseT, value));
        });

        trackpad.limit(min, max);
        trackpad.increment(increment);
        trackpad.delayedInput();
        trackpad.tooltip(tooltip, Direction.LEFT);

        return trackpad;
    }

    /**
     * 创建一个 PBR 滑条：limit(0,1)，回调经 {@code UIPoseTransforms.apply} 写入当前关键帧。
     */
    @Unique
    private UISliderTrackpad bbspp$createPbrSlider(BiConsumer<PoseTransform, Float> setter)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) ->
        {
            float value = v.floatValue();
            this.bbspp$write((poseT) -> setter.accept(poseT, value));
        });

        slider.limit(0D, 1D);
        return slider;
    }

    /** 从当前关键帧的 PoseTransform 读取 PBR 五值，同步到滑条。 */
    @Unique
    private void bbspp$syncPbrSliders()
    {
        if (this.keyframe == null || this.keyframe.getValue() == null)
        {
            return;
        }

        PoseTransform poseT = this.keyframe.getValue();

        this.bbspp$pbrSmoothness.setValue(((BonePbrHolder) poseT).bbspp_cml$getSmoothness());
        this.bbspp$pbrMetallic.setValue(((BonePbrHolder) poseT).bbspp_cml$getMetallic());
        this.bbspp$pbrSss.setValue(((BonePbrHolder) poseT).bbspp_cml$getSss());
        this.bbspp$pbrEmission.setValue(((BonePbrHolder) poseT).bbspp_cml$getEmission());
        this.bbspp$pbrRelief.setValue(((BonePbrHolder) poseT).bbspp_cml$getRelief());
    }

    /** 从当前关键帧的 PoseTransform 同步描边与附魔光效控件。 */
    @Unique
    private void bbspp$syncExtraSections()
    {
        if (this.bbspp$outlineSection == null)
        {
            return;
        }

        PoseTransform poseT = this.keyframe == null ? null : this.keyframe.getValue();
        BoneOutlineHolder outline = (BoneOutlineHolder) poseT;
        GlintHolder glint = (GlintHolder) poseT;
        GlintSettingsHolder glintSettings = (GlintSettingsHolder) poseT;

        this.bbspp$outline.setValue(outline != null && outline.bbspp_cml$getOutline());
        this.bbspp$outlineColor.setColor(outline == null
            ? 0xFFFFFFFF
            : outline.bbspp_cml$getOutlineColor().getARGBColor());
        this.bbspp$outlineWidth.setValue(outline == null
            ? BoneOutlineHolder.DEFAULT_WIDTH
            : outline.bbspp_cml$getOutlineWidth());
        this.bbspp$outlineGlow.setValue(outline == null
            ? BoneOutlineHolder.DEFAULT_GLOW
            : outline.bbspp_cml$getOutlineGlow());
        this.bbspp$outlineVanillaGlow.setValue(outline != null && outline.bbspp_cml$getOutlineVanillaGlow() > 0F);
        this.bbspp$outlineDecal.setValue(outline == null
            ? BoneOutlineHolder.DEFAULT_DECAL
            : outline.bbspp_cml$getOutlineDecalSize());
        this.bbspp$outlineFaceCull.setValue(outline == null
            ? BoneOutlineHolder.DEFAULT_FACE_CULL
            : outline.bbspp_cml$getOutlineFaceCull());

        this.bbspp$glint.setValue(glint != null && glint.bbspp_cml$getGlint());
        this.bbspp$glintColor.setColor(glint == null
            ? 0xFFFFFFFF
            : glint.bbspp_cml$getGlintColor().getARGBColor());
        this.bbspp$glintSpeed.setValue(glintSettings == null
            ? GlintSettingsHolder.DEFAULT_SPEED
            : glintSettings.bbspp_cml$getGlintSpeed());
        this.bbspp$glintBrightness.setValue(glintSettings == null
            ? GlintSettingsHolder.DEFAULT_BRIGHTNESS
            : glintSettings.bbspp_cml$getGlintBrightness());
    }
}
