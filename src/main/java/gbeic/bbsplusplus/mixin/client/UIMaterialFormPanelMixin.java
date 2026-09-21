package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BonePbrHolder;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMaterialFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 在 {@link UIMaterialFormPanel} 的 PBR 栏下方追加肢体选择器，
 * 选中肢体后 PBR 五值滑条会作用在该肢体上（而非材质级）。
 *
 * <p>材质 PBR 优先级高于肢体 PBR：渲染时如果材质设置了 PBR，就用材质的；
 * 材质没设置（值为 0）时，才用肢体的。</p>
 */
@Mixin(value = UIMaterialFormPanel.class, remap = false)
public abstract class UIMaterialFormPanelMixin
{
    @Shadow(remap = false)
    protected UIForm editor;

    @Shadow(remap = false)
    private UISection pbrSection;

    @Shadow(remap = false)
    public UISliderTrackpad smoothness;

    @Shadow(remap = false)
    public UISliderTrackpad metallic;

    @Shadow(remap = false)
    public UISliderTrackpad sss;

    @Shadow(remap = false)
    public UISliderTrackpad pixelEmission;

    @Shadow(remap = false)
    public UISliderTrackpad relief;

    /** 表示"材质级"的特殊行对象 */
    private static final String MATERIAL_LEVEL = new String("material_level");

    @Unique
    private UIStringList bbspp_cml$bonePbrList;

    @Unique
    private String bbspp_cml$selectedBone;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addBonePbrSelector(CallbackInfo ci)
    {
        /* 肢体 PBR 选择列表 */
        this.bbspp_cml$bonePbrList = new UIStringList((l) ->
        {
            this.bbspp_cml$selectedBone = l.isEmpty() || l.get(0) == MATERIAL_LEVEL ? null : l.get(0);
            this.bbspp_cml$syncBonePbrSliders();
        });
        this.bbspp_cml$bonePbrList.background();

        /* 填充骨骼列表 */
        this.bbspp_cml$refreshBoneList();

        /* 添加到 PBR 栏 */
        this.pbrSection.fields.add(this.bbspp_cml$bonePbrList);
    }

    /** 刷新骨骼列表 */
    @Unique
    private void bbspp_cml$refreshBoneList()
    {
        UIPoseEditor poseEditor = this.editor == null ? null : this.editor.getPoseEditor();

        if (poseEditor == null)
        {
            return;
        }

        List<String> levels = new ArrayList<>();
        levels.add(MATERIAL_LEVEL); /* 材质级 */

        /* 从姿势编辑器的骨骼列表里获取所有骨骼名 */
        Collection<String> bones = poseEditor.groups.list.getCurrent();
        if (bones != null)
        {
            levels.addAll(bones);
        }

        this.bbspp_cml$bonePbrList.setList(levels);

        /* 默认选中材质级 */
        this.bbspp_cml$selectedBone = null;
    }

    /** 同步 PBR 滑条为当前选中骨骼的值 */
    @Unique
    private void bbspp_cml$syncBonePbrSliders()
    {
        if (this.bbspp_cml$selectedBone == null)
        {
            return; /* 无选中肢体时，保持材质级值 */
        }

        UIPoseEditor poseEditor = this.editor == null ? null : this.editor.getPoseEditor();

        if (poseEditor == null)
        {
            return;
        }

        Pose pose = poseEditor.getPose();
        PoseTransform poseTransform = pose == null ? null : pose.get(this.bbspp_cml$selectedBone);

        float smooth = poseTransform == null ? 0F : ((BonePbrHolder) poseTransform).bbspp_cml$getSmoothness();
        float metal = poseTransform == null ? 0F : ((BonePbrHolder) poseTransform).bbspp_cml$getMetallic();
        float sss = poseTransform == null ? 0F : ((BonePbrHolder) poseTransform).bbspp_cml$getSss();
        float emission = poseTransform == null ? 0F : ((BonePbrHolder) poseTransform).bbspp_cml$getEmission();
        float relief = poseTransform == null ? 0F : ((BonePbrHolder) poseTransform).bbspp_cml$getRelief();

        this.smoothness.setValue(smooth);
        this.metallic.setValue(metal);
        this.sss.setValue(sss);
        this.pixelEmission.setValue(emission);
        this.relief.setValue(relief);
    }
}