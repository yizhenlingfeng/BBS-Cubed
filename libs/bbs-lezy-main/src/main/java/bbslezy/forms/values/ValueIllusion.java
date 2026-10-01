package bbslezy.forms.values;

import bbslezy.forms.utils.Illusion;
import bbslezy.utils.keyframes.factories.IllusionKeyframeFactory;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;

public class ValueIllusion extends BaseKeyframeFactoryValue<Illusion>
{
    public ValueIllusion(String id, Illusion value)
    {
        super(id, IllusionKeyframeFactory.INSTANCE, value);
    }
}
