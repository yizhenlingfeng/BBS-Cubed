package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Transform;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

/**
 * 修复多选关键帧后"重置变换"只重置主关键帧的问题。
 *
 * <p>同 {@link UITransformKeyframePropTransformResetMixin}：
 * {@code UIAnchorKeyframeFactory.UIAnchorTransforms}（锚点轨道）遗漏了 {@code reset()}
 * 的绝对赋值重写，回退到 delta 路径，导致多选时只有主关键帧被正确重置。</p>
 */
@Mixin(value = UIAnchorKeyframeFactory.UIAnchorTransforms.class, remap = true)
public abstract class UIAnchorKeyframePropTransformResetMixin extends UIDeltaPropTransform
{
    private UIAnchorKeyframePropTransformResetMixin()
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
