package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.api.ModelVAODataAccess;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.21.11 的 {@link ModelVAO} 已经把几何数据整体留在 CPU 侧（{@code private final ModelVAOData data}），
 * 不再有 1.20.x 的 GL 句柄字段。这里直接把那个字段暴露给挤出形态的动态 UV 路径，
 * 不需要再像旧版那样在上传时截获数组。
 */
@Mixin(value = ModelVAO.class, remap = false)
public abstract class ModelVAOMixin121 implements ModelVAODataAccess
{
    @Shadow @Final private ModelVAOData data;

    @Override
    public ModelVAOData bbspp$getModelVaoData()
    {
        return this.data;
    }
}
