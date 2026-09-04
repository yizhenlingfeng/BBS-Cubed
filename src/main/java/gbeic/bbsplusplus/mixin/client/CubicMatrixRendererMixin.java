package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.utils.CubicPivotTransformations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * {@link CubicMatrixRenderer}（骨骼矩阵收集：gizmo、body part 挂点、流体交互
 * 等消费 MatrixCache 的地方）在 jar 中已覆盖 applyGroupTransformations
 * （多一步 origins 记录），无法用"添加 override"方式插入中心点平移，
 * 故 @Overwrite 精确复刻原实现（translate → origins 记录 → 枢轴序列）并
 * 在 rotate/scale 前后插入成对 ±pivot。renderGroup RETURN 还会把本地矩阵
 * 和全局原点矩阵的最终锚点从 bind point 修正为 bind point + pose pivot，
 * 供 gizmo 的显示、拾取和拖拽共同消费。
 */
@Mixin(value = CubicMatrixRenderer.class, remap = false)
public abstract class CubicMatrixRendererMixin
{
    @Shadow(remap = false)
    public List<Matrix4f> matrices;

    @Shadow(remap = false)
    public List<Matrix4f> origins;

    /**
     * @author bbspp
     * @reason 在 rotate/scale 前后插入 pose 中心点的成对平移（原方法无扩展点，
     * 内部 origins 记录夹在序列中间，无法以添加 override 或普通注入实现）
     */
    @Overwrite(remap = false)
    public void applyGroupTransformations(MatrixStack stack, ModelGroup group)
    {
        ICubicRenderer.translateGroup(stack, group);

        this.origins.get(group.index).set(stack.peek().getPositionMatrix());

        ICubicRenderer.moveToGroupPivot(stack, group);
        CubicPivotTransformations.moveToPoseAnchor(stack, group);
        ICubicRenderer.rotateGroup(stack, group);
        ICubicRenderer.scaleGroup(stack, group);
        CubicPivotTransformations.moveBackFromPoseAnchor(stack, group);
        ICubicRenderer.moveBackFromGroupPivot(stack, group);
    }

    @Inject(method = "renderGroup", at = @At("RETURN"), remap = false)
    private void bbspp_cml$moveCollectedMatricesToPoseAnchor(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model, CallbackInfoReturnable<Boolean> cir)
    {
        CubicPivotTransformations.moveCollectedMatrixToPoseAnchor(this.matrices.get(group.index), group);
        CubicPivotTransformations.moveCollectedMatrixToPoseAnchor(this.origins.get(group.index), group);
    }
}
