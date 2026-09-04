package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.utils.CubicPivotTransformations;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 {@link CubicCubeRenderer}（含子类 CubicVAORenderer，即全部实际渲染路径）
 * 添加 {@code applyGroupTransformations} 覆盖：在接口 default 序列的
 * rotate/scale 前后插入 pose 中心点的成对平移。
 *
 * <p>为何不直接注入接口：Mixin 对 interface 目标的 injector 支持不可靠
 * （静默不生效）；jar 中该类未覆盖此方法，mixin 合并的方法即成为
 * override，虚分发替代接口 default。</p>
 */
@Mixin(value = CubicCubeRenderer.class, remap = false)
public abstract class CubicCubeRendererMixin
{
    public void applyGroupTransformations(MatrixStack stack, ModelGroup group)
    {
        ICubicRenderer.translateGroup(stack, group);
        ICubicRenderer.moveToGroupPivot(stack, group);
        CubicPivotTransformations.moveToPoseAnchor(stack, group);
        ICubicRenderer.rotateGroup(stack, group);
        ICubicRenderer.scaleGroup(stack, group);
        CubicPivotTransformations.moveBackFromPoseAnchor(stack, group);
        ICubicRenderer.moveBackFromGroupPivot(stack, group);
    }
}
