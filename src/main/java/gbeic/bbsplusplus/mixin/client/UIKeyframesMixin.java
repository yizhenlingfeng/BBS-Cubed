package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.List;

/**
 * 关键帧面板增强：注册"按列选中当前时刻所有轨道关键帧"的快捷键。
 */
@Mixin(value = UIKeyframes.class, priority = 2000, remap = false)
public abstract class UIKeyframesMixin
{
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$registerColumnSelectionKey(
        java.util.function.Consumer<Keyframe> callback,
        CallbackInfo ci
    )
    {
        UIKeyframes self = (UIKeyframes) (Object) this;

        self.keys().register(SnowUIKeys.KEYFRAMES_SELECT_COLUMN_KEY, () ->
        {
            float tick = self.getTick();
            self.getGraph().clearSelection();

            for (UIKeyframeSheet sheet : self.getGraph().getSheets())
            {
                List<Keyframe> keyframes = sheet.channel.getKeyframes();

                for (int i = 0; i < keyframes.size(); i++)
                {
                    if (Math.abs(keyframes.get(i).getTick() - tick) < 0.0001F)
                    {
                        sheet.selection.add(i);
                    }
                }
            }

            self.getGraph().pickSelected();
        }).strict().active(self::canBeSeen);
    }
}
