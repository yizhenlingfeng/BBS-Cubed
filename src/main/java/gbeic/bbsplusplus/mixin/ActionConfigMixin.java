package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import gbeic.bbsplusplus.timeline.ActionTimelineMeta;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.data.types.MapType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * snow_actions 时间线元数据外挂薄壳：字段、序列化与相等性判定全部在
 * {@link ActionTimelineMeta}，本类只保留注入点与 duck 接口的一行委托。
 */
@Mixin(value = ActionConfig.class, remap = false)
public abstract class ActionConfigMixin implements ActionTimelineConfig
{
    @Unique
    private ActionTimelineMeta bbspp_cml$meta;

    @Unique
    private ActionTimelineMeta bbspp_cml$meta()
    {
        if (this.bbspp_cml$meta == null)
        {
            this.bbspp_cml$meta = new ActionTimelineMeta();
        }

        return this.bbspp_cml$meta;
    }

    @Override
    public String bbspp_cml$getClipId()
    {
        return this.bbspp_cml$meta().getClipId();
    }

    @Override
    public void bbspp_cml$setClipId(String clipId)
    {
        this.bbspp_cml$meta().setClipId(clipId);
    }

    @Override
    public float bbspp_cml$getTimelineFrame()
    {
        return this.bbspp_cml$meta().getTimelineFrame();
    }

    @Override
    public void bbspp_cml$setTimelineFrame(float frame)
    {
        this.bbspp_cml$meta().setTimelineFrame(frame);
    }

    @Override
    public boolean bbspp_cml$isOverlayTimeline()
    {
        return this.bbspp_cml$meta().isOverlayTimeline();
    }

    @Override
    public void bbspp_cml$setOverlayTimeline(boolean overlay)
    {
        this.bbspp_cml$meta().setOverlayTimeline(overlay);
    }

    @Override
    public float bbspp_cml$getTimelineWeight()
    {
        return this.bbspp_cml$meta().getTimelineWeight();
    }

    @Override
    public void bbspp_cml$setTimelineWeight(float weight)
    {
        this.bbspp_cml$meta().setTimelineWeight(weight);
    }

    @Override
    public boolean bbspp_cml$isLoopBeyond()
    {
        return this.bbspp_cml$meta().isLoopBeyond();
    }

    @Override
    public void bbspp_cml$setLoopBeyond(boolean loopBeyond)
    {
        this.bbspp_cml$meta().setLoopBeyond(loopBeyond);
    }

    @Override
    public float bbspp_cml$getLoopInterval()
    {
        return this.bbspp_cml$meta().getLoopInterval();
    }

    @Override
    public void bbspp_cml$setLoopInterval(float interval)
    {
        this.bbspp_cml$meta().setLoopInterval(interval);
    }

    @Inject(method = "toData(Lmchorse/bbs_mod/data/types/MapType;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$writeTimelineMetadata(MapType data, CallbackInfo ci)
    {
        this.bbspp_cml$meta().writeTo(data);
    }

    @Inject(method = "fromData(Lmchorse/bbs_mod/data/types/MapType;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$readTimelineMetadata(MapType data, CallbackInfo ci)
    {
        this.bbspp_cml$meta().readFrom(data);
    }

    @Inject(method = "copy", at = @At("RETURN"), remap = false)
    private void bbspp_cml$copyTimelineMetadata(CallbackInfoReturnable<ActionConfig> cir)
    {
        this.bbspp_cml$meta().copyTo((ActionTimelineConfig) cir.getReturnValue());
    }

    @Inject(method = "equals", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$compareTimelineMetadata(Object object, CallbackInfoReturnable<Boolean> cir)
    {
        Boolean refined = this.bbspp_cml$meta().refineEquals(cir.getReturnValueZ(), object);

        if (refined != null)
        {
            cir.setReturnValue(refined);
        }
    }

    @Inject(method = "isDefault()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$keepTimelineMetadataSerializable(CallbackInfoReturnable<Boolean> cir)
    {
        if (this.bbspp_cml$meta().isTimelineDriven())
        {
            cir.setReturnValue(false);
        }
    }
}
