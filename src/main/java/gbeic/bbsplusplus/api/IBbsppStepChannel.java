package gbeic.bbsplusplus.api;

/**
 * 关键帧通道上的 Bedrock 步阶帧（step）登记表。
 *
 * <p>步阶帧的插值标记只说明“到达该帧时瞬变”，无法区分 CONST 是进入还是离开该帧：
 * 方块模型动画取段内后一帧的插值（进入），pose 通道取段内首帧的插值（离开）。
 * 因此把步阶帧的 tick 单独登记在通道上，供动画转姿势关键帧时区分这两种语义，
 * 详见 {@code AnimationInterpInheritance#pickInterp}。</p>
 */
public interface IBbsppStepChannel
{
    /** 登记一个步阶帧 tick。 */
    void bbspp$addStepTick(float tick);

    /** 该 tick 是否为步阶帧（带容差比较，避免秒转 tick 的浮点误差漏判）。 */
    boolean bbspp$isStepTick(float tick);
}
