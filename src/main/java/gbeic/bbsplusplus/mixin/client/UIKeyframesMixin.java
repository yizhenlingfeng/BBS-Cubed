package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BoneCollapseHandler;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 骨骼折叠箭头点击的必胜层级拦截：{@code subMouseClicked} HEAD 中、在把事件
 * 分发给 graph 之前检测折叠箭头命中（经 {@link BoneCollapseHandler} 调
 * dope sheet 的命中逻辑）。BBS-PoseCurve-Addon 在下游 dope sheet 的
 * mouseClicked HEAD 把标签行点击改成打开曲线编辑，在同层用 priority 竞争
 * 不可靠 —— 上游检测保证折叠箭头永远先判定；未命中时不做任何干预，
 * PoseCurve 的行为不受影响。
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

    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$interceptCollapseArrow(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UIKeyframes self = (UIKeyframes) (Object) this;

        if (context.mouseButton != 0 || !self.area.isInside(context))
        {
            return;
        }

        /* 仅 dope sheet 视图（单轨道曲线视图没有骨骼行） */
        if (self.getGraph() != self.getDopeSheet())
        {
            return;
        }

        if (((BoneCollapseHandler) self.getDopeSheet()).bbspp_cml$handleCollapseClick(context))
        {
            cir.setReturnValue(true);
        }
    }
}
