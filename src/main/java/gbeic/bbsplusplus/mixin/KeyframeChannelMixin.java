package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.IBbsppStepChannel;
import gbeic.bbsplusplus.keyframes.BBSPlusPlusKeyframeFactories;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * 通道侧的两处兼容：
 * <ol>
 *   <li>旧录像里的 {@code effect} 通道若仍使用原版 LINK 工厂，加载时自动升级为
 *       AAA_EFFECT，无需手动改档；</li>
 *   <li>为 {@link IBbsppStepChannel} 提供挂载点，登记 Bedrock 步阶帧的 tick。</li>
 * </ol>
 */
@Mixin(value = KeyframeChannel.class, remap = true)
public class KeyframeChannelMixin implements IBbsppStepChannel
{
    @Shadow
    private IKeyframeFactory<?> factory;

    @Unique
    private final Set<Float> bbspp$stepTicks = new HashSet<>();

    @Override
    public void bbspp$addStepTick(float tick)
    {
        this.bbspp$stepTicks.add(tick);
    }

    @Override
    public boolean bbspp$isStepTick(float tick)
    {
        for (float step : this.bbspp$stepTicks)
        {
            /* 秒转 tick 经过 float 乘法，用 0.01 tick 容差兜住舍入差。 */
            if (Math.abs(step - tick) < 0.01F)
            {
                return true;
            }
        }

        return false;
    }

    @Inject(method = "fromData", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/settings/values/core/ValueList;fromData(Lmchorse/bbs_mod/data/types/BaseType;)V", shift = At.Shift.BEFORE))
    private void bbspp$modifyFactory(BaseType data, CallbackInfo ci)
    {
        KeyframeChannel<?> channel = (KeyframeChannel<?>) (Object) this;

        if ("effect".equals(channel.getId()) && this.factory == KeyframeFactories.LINK)
        {
            this.factory = BBSPlusPlusKeyframeFactories.AAA_EFFECT;
        }
    }
}
