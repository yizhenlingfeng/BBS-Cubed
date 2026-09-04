package gbeic.bbsplusplus.api;

/**
 * Timeline metadata attached to an action configuration. Two adjacent action
 * configurations with the same clip ID form one seekable animation segment.
 */
public interface ActionTimelineConfig
{
    String bbspp_TRANSITION_PREFIX = "bbspp_transition:";

    String bbspp_cml$getClipId();

    void bbspp_cml$setClipId(String clipId);

    float bbspp_cml$getTimelineFrame();

    void bbspp_cml$setTimelineFrame(float frame);

    boolean bbspp_cml$isOverlayTimeline();

    void bbspp_cml$setOverlayTimeline(boolean overlay);

    float bbspp_cml$getTimelineWeight();

    void bbspp_cml$setTimelineWeight(float weight);

    boolean bbspp_cml$isLoopBeyond();

    void bbspp_cml$setLoopBeyond(boolean loopBeyond);

    float bbspp_cml$getLoopInterval();

    void bbspp_cml$setLoopInterval(float interval);

    default boolean bbspp_cml$isTimelineDriven()
    {
        String clipId = this.bbspp_cml$getClipId();

        return clipId != null && !clipId.isEmpty();
    }
}
