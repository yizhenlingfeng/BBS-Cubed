package gbeic.bbsplusplus.pbr.ui;

import gbeic.bbsplusplus.pbr.BonePBRData;
import gbeic.bbsplusplus.pbr.PBRChannel;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

public class UIBonePBRKeyframeFactory extends UIKeyframeFactory<BonePBRData>
{
    private final UIStringList bones;
    private final UITrackpad sR;
    private final UITrackpad sG;
    private final UITrackpad sB;
    private final UITrackpad sA;
    private final UITrackpad normal;
    private final UITrackpad emission;
    private String currentBone = "";
    private boolean bonesLoaded;

    public UIBonePBRKeyframeFactory(Keyframe<BonePBRData> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
        this.bones = new UIStringList(this::pickBone);
        this.bones.background().h(UIStringList.DEFAULT_HEIGHT * 8 - 8);
        this.bones.scroll.cancelScrolling();
        this.sR = bbspp_snow$trackpad(255, true);
        this.sG = bbspp_snow$trackpad(255, true);
        this.sB = bbspp_snow$trackpad(255, true);
        this.sA = bbspp_snow$trackpad(254, true);
        this.normal = bbspp_snow$trackpad(255, true);
        this.emission = bbspp_snow$trackpad(Integer.MAX_VALUE, false);
        this.emission.setValue(1);
        this.scroll.add(
            this.bones,
            UI.label(SnowUIKeys.BONE_PBR_SMOOTHNESS), this.sR,
            UI.label(SnowUIKeys.BONE_PBR_METAL), this.sG,
            UI.label(SnowUIKeys.BONE_PBR_POROSITY), this.sB,
            UI.label(SnowUIKeys.BONE_PBR_EMISSIVE), this.sA,
            UI.label(SnowUIKeys.BONE_PBR_NORMAL), this.normal,
            UI.label(SnowUIKeys.BONE_PBR_EMISSION_MULTIPLIER), this.emission
        );
    }

    private UITrackpad bbspp_snow$trackpad(double max, boolean integer)
    {
        UITrackpad trackpad = new UITrackpad(ignored -> this.apply());
        trackpad.limit(0, max);

        if (integer)
        {
            trackpad.integer();
        }

        return trackpad;
    }

    @Override
    public void render(UIContext context)
    {
        this.loadBones();
        super.render(context);
    }

    private void loadBones()
    {
        if (this.bonesLoaded)
        {
            return;
        }

        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);
        ModelForm form = null;

        if (sheet != null && sheet.form instanceof ModelForm modelForm)
        {
            form = modelForm;
        }
        else if (sheet != null && sheet.property != null && FormUtils.getForm(sheet.property) instanceof ModelForm modelForm)
        {
            form = modelForm;
        }

        if (form == null || !(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer))
        {
            return;
        }

        ModelInstance instance = renderer.getModel();
        IModel model = instance == null ? null : instance.model;
        List<String> keys = model == null ? null : model.getGroupKeysInHierarchyOrder();

        if (keys != null && !keys.isEmpty())
        {
            this.bones.add(keys);
            this.bonesLoaded = true;
        }
    }

    private void pickBone(List<String> selection)
    {
        if (selection == null || selection.isEmpty())
        {
            this.currentBone = "";

            return;
        }

        this.currentBone = selection.get(0);
        PBRChannel channel = this.keyframe.getValue().get(this.currentBone);
        this.sR.setValue(channel.sR);
        this.sG.setValue(channel.sG);
        this.sB.setValue(channel.sB);
        this.sA.setValue(channel.sA);
        this.normal.setValue(channel.n);
        this.emission.setValue(channel.emissionMultiplier);
    }

    public void selectBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        this.loadBones();

        String match = null;

        for (String candidate : this.bones.getList())
        {
            if (candidate.equals(bone))
            {
                match = candidate;
                break;
            }

            if (match == null && candidate.equalsIgnoreCase(bone))
            {
                match = candidate;
            }
        }

        if (match != null)
        {
            this.bones.setCurrentScroll(match);
            this.pickBone(List.of(match));
        }
    }

    private void apply()
    {
        if (this.currentBone.isEmpty())
        {
            return;
        }

        BonePBRData data = this.keyframe.getValue();
        PBRChannel channel = data.get(this.currentBone);
        channel.sR = (float) this.sR.getValue();
        channel.sG = (float) this.sG.getValue();
        channel.sB = (float) this.sB.getValue();
        channel.sA = (float) this.sA.getValue();
        channel.n = (float) this.normal.getValue();
        channel.emissionMultiplier = (float) this.emission.getValue();
        this.setValue(data);
    }
}
