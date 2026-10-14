package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.IBbsppAnimation;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 {@link Animation} 增加 {@code loop} 字段（见 {@link IBbsppAnimation}）。
 */
@Mixin(value = Animation.class, remap = false)
public abstract class AnimationMixin implements IBbsppAnimation
{
    @Unique
    private boolean bbspp$loop;

    @Override
    public boolean bbspp$isLoop()
    {
        return this.bbspp$loop;
    }

    @Override
    public void bbspp$setLoop(boolean loop)
    {
        this.bbspp$loop = loop;
    }
}
