package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Transform;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

/**
 * 修复多选关键帧后"重置变换"只重置主关键帧的问题。
 *
 * <p>BBS 2.7 中 {@code UIPoseKeyframeFactory.UIPoseTransforms}（pose 骨骼轨道）正确重写了
 * {@code reset()} 为绝对赋值，但下列三个 {@code UIKeyframePropTransform} 子类都遗漏了重写，
 * 回退到 {@code UITransform.reset()} 的 delta 路径：{@code dx = 0 - primary.tx}，
 * 再把 dx 叠加到每个选中关键帧。结果只有主关键帧被正确归零，其余选中关键帧只减去了主关键帧的偏移量。</p>
 *
 * <ul>
 *   <li>{@code UITransformKeyframeFactory.UIPoseTransforms} —— 模型根变换 / 摄像机变换等通用变换轨道；</li>
 *   <li>{@code UIPoseTransformKeyframeFactory.UIPoseTransforms} —— 肢体（BONE）轨道；</li>
 *   <li>{@code UIAnchorKeyframeFactory.UIAnchorTransforms} —— 锚点轨道。</li>
 * </ul>
 *
 * <p>三个目标的注入点完全一致：{@code reset()} 均由 {@code UITransform} 继承而来、
 * 目标类自身未重写，签名同为 {@code protected void reset()}，且都不需要任何 {@code @Shadow} 字段。
 * 因此合并为一个多目标 {@code @Mixin}，注入体直接在 Mixin 中定义 {@code reset()} 覆盖父类实现
 * （与 {@code UIPoseKeyframeFactory.UIPoseTransforms#reset()} 同构），
 * 通过 {@code applyToSelection} fan-out 到所有选中关键帧做绝对赋值。</p>
 */
@Mixin(value = {
    UITransformKeyframeFactory.UIPoseTransforms.class,
    UIPoseTransformKeyframeFactory.UIPoseTransforms.class,
    UIAnchorKeyframeFactory.UIAnchorTransforms.class
}, remap = true)
public abstract class UIKeyframePropTransformResetMixin extends UIDeltaPropTransform
{
    private UIKeyframePropTransformResetMixin()
    {
        super();
    }

    @Override
    protected void reset()
    {
        this.applyToSelection((Consumer<Transform>) (t) ->
        {
            t.translate.set(0F, 0F, 0F);
            t.scale.set(1F, 1F, 1F);
            t.resetRotation();
        });
        this.refillTransform();
    }
}
