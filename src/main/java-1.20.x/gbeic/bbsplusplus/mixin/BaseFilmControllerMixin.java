package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.WorldFilmShaderCurveState;
import gbeic.bbsplusplus.client.renderer.VideoTimelineState;
import gbeic.bbsplusplus.keyframes.EquipmentTransformRuntime;
import java.util.Map;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.WorldFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为影片控制器的渲染帧准备阶段提供 BBS++ 的额外采样：
 * 视频时间线平滑寻帧、世界影片光影曲线采样、装备槽位变换采样。
 */

@Mixin(BaseFilmController.class)
public abstract class BaseFilmControllerMixin {

    @Shadow(remap = true)
    public Film film;

    @Shadow(remap = true)
    public Map<String, IEntity> entities;

    @Shadow(remap = true)
    public abstract int getTick();

    @Shadow(remap = true)
    protected abstract float getTransition(IEntity entity, float transition);

    /**
     * 注入目标：{@code BaseFilmController#renderEntity(WorldRenderContext, Replay, IEntity)} 开始处。
     * 注入原因：实体 age 在编辑器向后跳转时仍只会递增，不能代表影片播放头时间；且整数 tick 每游戏 tick
     * 才跳变一次，高帧率视频会呈 20Hz 阶梯跳动导致卡顿。
     * 修改行为：在形态渲染期间暴露当前回放的真实局部 tick（含帧间浮点插值），让视频按播放头平滑正向或反向寻帧。
     */
    @Inject(
        method = "renderEntity(Lnet/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext;Lmchorse/bbs_mod/film/replays/Replay;Lmchorse/bbs_mod/forms/entities/IEntity;)V",
        at = @At("HEAD"),
        remap = true
    )
    private void bbspp$beginVideoTimelineRender(WorldRenderContext context, Replay replay, IEntity entity, CallbackInfo ci)
    {
        int baseTick = replay == null ? this.getTick() : replay.getTick(this.getTick());
        // 叠加帧间 partial tick，使视频目标秒数随渲染帧平滑推进，避免时间轴播放时的 20Hz 阶梯跳变。
        float tick = baseTick + this.getTransition(entity, context.tickDelta());

        VideoTimelineState.beginFilmRender(tick);
    }

    /**
     * 注入目标：{@code BaseFilmController#renderEntity(WorldRenderContext, Replay, IEntity)} 返回处。
     * 注入原因：真实影片 tick 只应影响当前演员的形态渲染，不能泄漏到普通实体或表单预览。
     * 修改行为：当前演员渲染结束后立即清除影片时间上下文。
     */
    @Inject(
        method = "renderEntity(Lnet/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext;Lmchorse/bbs_mod/film/replays/Replay;Lmchorse/bbs_mod/forms/entities/IEntity;)V",
        at = @At("RETURN"),
        remap = true
    )
    private void bbspp$endVideoTimelineRender(WorldRenderContext context, Replay replay, IEntity entity, CallbackInfo ci)
    {
        VideoTimelineState.endFilmRender();
    }

    /**
     * 注入目标：{@code BaseFilmController#startRenderFrame(float)} 结束处。
     * 注入原因：右 Ctrl 世界播放影片时，光影曲线不会经过相机控制器上下文，导致 BBSRendering 读不到曲线数据。
     * 修改行为：在世界影片控制器每帧完成演员属性准备后，额外采样当前影片相机剪辑里的光影曲线。
     */
    @Inject(method = "startRenderFrame", at = @At("TAIL"), remap = true)
    private void bbspp$sampleWorldFilmShaderCurves(float transition, CallbackInfo ci)
    {
        if ((Object) this instanceof WorldFilmController controller)
        {
            WorldFilmShaderCurveState.sample(controller, transition);
        }

        this.bbspp$sampleEquipmentTransforms(transition);
    }

    private void bbspp$sampleEquipmentTransforms(float transition)
    {
        if (this.film == null || this.entities == null)
        {
            return;
        }

        for (Replay replay : this.film.replays.getList())
        {
            if (replay == null || !replay.enabled.get())
            {
                continue;
            }

            IEntity entity = this.entities.get(replay.getId());

            if (entity == null)
            {
                continue;
            }

            float delta = this.getTransition(entity, transition);
            float tick = replay.getTick(this.getTick()) + delta;

            EquipmentTransformRuntime.apply(replay.keyframes, tick, entity);
        }
    }
}
