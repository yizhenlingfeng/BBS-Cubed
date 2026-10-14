package gbeic.bbsplusplus.api;

/**
 * BBS++ 对 {@code mchorse.bbs_mod.cubic.data.animation.Animation} 的扩展字段接口。
 *
 * <p>Bedrock {@code *.animation.json} 中的 {@code loop} 字段（{@code true}/{@code false}
 * 或 {@code "hold_on_last_frame"}）在原生 {@code GeoAnimationParser} 中被丢弃，
 * 由此接口经由 {@code AnimationMixin} 携带，供动画状态自动同步读取循环标记。</p>
 */
public interface IBbsppAnimation
{
    /** 动画是否循环（来自 {@code loop} 字段，缺省 false）。 */
    boolean bbspp$isLoop();

    void bbspp$setLoop(boolean loop);
}
