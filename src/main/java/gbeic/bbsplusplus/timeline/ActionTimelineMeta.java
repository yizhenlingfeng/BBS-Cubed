package gbeic.bbsplusplus.timeline;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.data.types.MapType;

import java.util.Objects;

/**
 * snow_actions 时间线外挂在 {@code ActionConfig} 上的元数据实体：字段存取、
 * 序列化、复制与相等性判定全部在此，ActionConfigMixin 仅保留一行委托。
 */
public final class ActionTimelineMeta
{
    private static final String KEY_CLIP_ID = "bbspp_clip";
    private static final String KEY_FRAME = "bbspp_frame";
    private static final String KEY_OVERLAY = "bbspp_overlay";
    private static final String KEY_WEIGHT = "bbspp_weight";
    private static final String KEY_LOOP_BEYOND = "bbspp_loop_beyond";
    private static final String KEY_LOOP_INTERVAL = "bbspp_loop_interval";

    private String clipId = "";
    private float timelineFrame;
    private boolean overlayTimeline;
    private float timelineWeight = 1F;
    private boolean loopBeyond = true;
    private float loopInterval;

    public String getClipId()
    {
        return this.clipId;
    }

    public void setClipId(String clipId)
    {
        this.clipId = clipId == null ? "" : clipId;
    }

    public float getTimelineFrame()
    {
        return this.timelineFrame;
    }

    public void setTimelineFrame(float frame)
    {
        this.timelineFrame = frame;
    }

    public boolean isOverlayTimeline()
    {
        return this.overlayTimeline;
    }

    public void setOverlayTimeline(boolean overlay)
    {
        this.overlayTimeline = overlay;
    }

    public float getTimelineWeight()
    {
        return this.timelineWeight;
    }

    public void setTimelineWeight(float weight)
    {
        this.timelineWeight = weight;
    }

    public boolean isLoopBeyond()
    {
        return this.loopBeyond;
    }

    public void setLoopBeyond(boolean loopBeyond)
    {
        this.loopBeyond = loopBeyond;
    }

    public float getLoopInterval()
    {
        return this.loopInterval;
    }

    public void setLoopInterval(float interval)
    {
        this.loopInterval = Math.max(0F, interval);
    }

    public boolean isTimelineDriven()
    {
        return this.clipId != null && !this.clipId.isEmpty();
    }

    /** 只有时间线驱动的动作才写出元数据，避免污染普通动作的存档。 */
    public void writeTo(MapType data)
    {
        if (this.isTimelineDriven())
        {
            data.putString(KEY_CLIP_ID, this.clipId);
            data.putFloat(KEY_FRAME, this.timelineFrame);
            data.putBool(KEY_OVERLAY, this.overlayTimeline);
            data.putFloat(KEY_WEIGHT, this.timelineWeight);
            data.putBool(KEY_LOOP_BEYOND, this.loopBeyond);
            data.putFloat(KEY_LOOP_INTERVAL, this.loopInterval);
        }
    }

    public void readFrom(MapType data)
    {
        this.clipId = data.getString(KEY_CLIP_ID, "");
        this.timelineFrame = data.getFloat(KEY_FRAME, 0F);
        this.overlayTimeline = data.getBool(KEY_OVERLAY, false);
        this.timelineWeight = data.getFloat(KEY_WEIGHT, 1F);
        this.loopBeyond = data.getBool(KEY_LOOP_BEYOND, true);
        this.loopInterval = Math.max(0F, data.getFloat(KEY_LOOP_INTERVAL, 0F));
    }

    public void copyTo(ActionTimelineConfig target)
    {
        target.bbspp_cml$setClipId(this.clipId);
        target.bbspp_cml$setTimelineFrame(this.timelineFrame);
        target.bbspp_cml$setOverlayTimeline(this.overlayTimeline);
        target.bbspp_cml$setTimelineWeight(this.timelineWeight);
        target.bbspp_cml$setLoopBeyond(this.loopBeyond);
        target.bbspp_cml$setLoopInterval(this.loopInterval);
    }

    /**
     * 在原版 equals 结果之上叠加元数据比较。返回 null 表示不接管
     * （功能关闭、原判定已为 false、或对方不是 ActionConfig）。
     */
    public Boolean refineEquals(boolean baseEquals, Object object)
    {
        if (CMLSettings.isSnowActionsEnabled() && baseEquals && object instanceof ActionConfig)
        {
            ActionTimelineConfig other = (ActionTimelineConfig) object;

            return Objects.equals(this.clipId, other.bbspp_cml$getClipId())
                && this.timelineFrame == other.bbspp_cml$getTimelineFrame()
                && this.overlayTimeline == other.bbspp_cml$isOverlayTimeline()
                && this.timelineWeight == other.bbspp_cml$getTimelineWeight()
                && this.loopBeyond == other.bbspp_cml$isLoopBeyond()
                && this.loopInterval == other.bbspp_cml$getLoopInterval();
        }

        return null;
    }
}
