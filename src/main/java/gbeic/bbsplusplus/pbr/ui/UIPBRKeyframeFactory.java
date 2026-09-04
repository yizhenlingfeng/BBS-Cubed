package gbeic.bbsplusplus.pbr.ui;

import gbeic.bbsplusplus.pbr.PBRChannel;
import gbeic.bbsplusplus.pbr.PBRData;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIPBRKeyframeFactory extends UIKeyframeFactory<PBRData>
{
    private final UITrackpad sR;
    private final UITrackpad sG;
    private final UITrackpad sB;
    private final UITrackpad sA;
    private final UITrackpad normal;

    public UIPBRKeyframeFactory(Keyframe<PBRData> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
        PBRChannel channel = keyframe.getValue().get("");
        this.sR = bbspp_snow$trackpad(channel.sR, 255, this::apply);
        this.sG = bbspp_snow$trackpad(channel.sG, 255, this::apply);
        this.sB = bbspp_snow$trackpad(channel.sB, 255, this::apply);
        this.sA = bbspp_snow$trackpad(channel.sA, 254, this::apply);
        this.normal = bbspp_snow$trackpad(channel.n, 255, this::apply);
        this.scroll.add(UI.row(this.sR, this.sG), UI.row(this.sB, this.sA), UI.row(this.normal));
    }

    private static UITrackpad bbspp_snow$trackpad(double value, double max, Runnable callback)
    {
        UITrackpad trackpad = new UITrackpad(ignored -> callback.run());
        trackpad.limit(0, max);
        trackpad.integer();
        trackpad.setValue(value);

        return trackpad;
    }

    private void apply()
    {
        PBRData data = new PBRData();
        PBRChannel channel = data.get("");
        channel.sR = (float) this.sR.getValue();
        channel.sG = (float) this.sG.getValue();
        channel.sB = (float) this.sB.getValue();
        channel.sA = (float) this.sA.getValue();
        channel.n = (float) this.normal.getValue();
        this.setValue(data);
    }
}
