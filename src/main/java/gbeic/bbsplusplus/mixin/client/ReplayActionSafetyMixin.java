package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.clips.ReplayActionPolicy;
import gbeic.bbsplusplus.clips.ReplayTimeBridge;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prevents irreversible actions from being fired again by a remapped replay
 * time. The original timeline pass remains unchanged.
 *
 * <p>注入方式评估（保留 @Redirect 的原因）：拦截目标是
 * {@code applyClientActions} 循环体内对单个 {@code ActionClip.applyClient}
 * 的调用，需要按次条件放行。普通 {@code @Inject} 无法否决单次调用
 * （cancel 会中止整个方法、跳过其余动作），{@code @ModifyArg} 也不能
 * 阻止调用发生；若直接在 {@code ActionClip.applyClient} HEAD 注入取消，
 * 则会波及其他调用方，扩大拦截面。{@code @WrapOperation} 语义最稳，
 * 但需要 MixinExtras 编译期依赖（本项目未引入，fabric-loader 0.15.11
 * 仅在运行期内置），故维持 @Redirect 并以 {@code require = 0} 软降级：
 * 一旦 BBS 改动目标方法签名，本注入自动失效并回落原版行为。</p>
 */
@Mixin(value = Replay.class, remap = false)
public abstract class ReplayActionSafetyMixin
{
    @Redirect(
        method = "applyClientActions",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/actions/types/ActionClip;applyClient(Lmchorse/bbs_mod/forms/entities/IEntity;Lmchorse/bbs_mod/film/Film;Lmchorse/bbs_mod/film/replays/Replay;I)V"
        ),
        require = 0
    )
    private void bbspp_cml$applyClientActionSafely(ActionClip action, IEntity entity,
        Film film, Replay replay, int tick)
    {
        if (!ReplayTimeBridge.isResampled()
            || !ReplayActionPolicy.bypassesResampling(action.getClass().getName()))
        {
            action.applyClient(entity, film, replay, tick);
        }
    }
}
