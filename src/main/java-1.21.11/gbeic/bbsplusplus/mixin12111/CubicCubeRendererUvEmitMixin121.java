package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.renderer.ModelUVTransformRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 在 1.21.11 的 CPU 顶点渲染路径上应用模型贴图 UV 变换。
 * <p>
 * 1.21.11 的 {@code CubicCubeRenderer#emit(BufferBuilder, ModelGroup, float x, float y,
 * float z, float u, float v, Vector3f normal)} 与 1.20.x 签名一致，但顶点写入方式变了
 * （没有 {@code .next()}，overlay 走 {@code cpuOverlayActive/groupOverlay}），所以旧的
 * 「取消 HEAD、重写整个顶点」实现不能直接解禁。
 * </p>
 * <p>
 * 这里改成只在 HEAD 处改写参数里的 u/v 两个 float 实参，其余顶点写入完全交给上游自己，
 * 因此不依赖任何顶点格式细节，也不 shadow 任何字段。
 * </p>
 */
@Mixin(targets = "mchorse.bbs_mod.cubic.render.CubicCubeRenderer", remap = false)
public class CubicCubeRendererUvEmitMixin121
{
    @ModifyVariable(method = "emit", at = @At("HEAD"), argsOnly = true, index = 6)
    private float bbspp$transformU(float u)
    {
        ModelUVTransformRuntime.Transform transform = ModelUVTransformRuntime.current();

        return transform.active() ? u * transform.scaleU() + transform.normalizedOffsetU() : u;
    }

    @ModifyVariable(method = "emit", at = @At("HEAD"), argsOnly = true, index = 7)
    private float bbspp$transformV(float v)
    {
        ModelUVTransformRuntime.Transform transform = ModelUVTransformRuntime.current();

        return transform.active() ? v * transform.scaleV() + transform.normalizedOffsetV() : v;
    }
}
