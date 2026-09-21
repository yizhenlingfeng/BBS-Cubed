package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BonePbrHolder;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在影片编辑器中，肢体轨道（BONE 类型）选中关键帧后打开的属性面板
 * 是 {@link UIPoseTransformKeyframeFactory}。它自带固定、可见、颜色、叠加色、光照五个控件，
 * 但缺少逐骨骼 PBR 五值滑条。
 *
 * <p>本 Mixin 在该面板构造完成后追加一个 PBR 折叠栏（与姿势编辑器 UIPoseEditor 中的 PBR 栏
 * 同构：光泽度 / 金属度 / 散射 / 自发光 / 凹凸，0~1），直接读写当前关键帧的
 * {@link PoseTransform} 上的 PBR 字段（经 {@link BonePbrHolder} duck 接口）。</p>
 *
 * <p><b>与姿势轨道面板的适配差异：</b></p>
 * <ul>
 *   <li>姿势轨道（{@code UIPoseFactoryEditor}，继承自 {@code UIPoseEditor}）——
 *       PBR 栏作用于 3D 视口中<b>当前选中的骨骼</b>，由 {@code UIPoseEditorMixin} 实现；</li>
 *   <li>肢体轨道（本类）—— 关键帧值本身就是该骨骼的 {@code PoseTransform}，
 *       PBR 栏直接读写当前关键帧，<b>无需骨骼选择</b>。</li>
 * </ul>
 *
 * <p>写入路径复用原版 {@code UIPoseTransforms.apply(editor, keyframe, consumer)}，
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

    /**
     * 在构造器尾部追加 PBR 折叠栏。原版最后 {@code scroll.add(...)} 放入四个控件，
     * 这里在其之后再 add 一个 PBR section，顺序为：可见 → 固定 → 变换 → 材质（颜色/叠加/光照）→ PBR。
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = true)
    private void bbspp$addPbrSection(Keyframe<PoseTransform> keyframe, UIKeyframes editor, CallbackInfo ci)
    {
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

        this.scroll.add(this.bbspp$pbrSection);

        /* 用当前关键帧的 PoseTransform 值初始化滑条位置。 */
        this.bbspp$syncPbrSliders();
    }

    /**
     * 创建一个 PBR 滑条：limit(0,1)，回调经 {@code UIPoseTransforms.apply} 写入当前关键帧。
     */
    @Unique
    private UISliderTrackpad bbspp$createPbrSlider(java.util.function.BiConsumer<PoseTransform, Float> setter)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) ->
        {
            float value = v.floatValue();
            UIPoseTransformKeyframeFactory.UIPoseTransforms.apply(this.editor, this.keyframe, (poseT) ->
            {
                setter.accept(poseT, value);
            });
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
}
