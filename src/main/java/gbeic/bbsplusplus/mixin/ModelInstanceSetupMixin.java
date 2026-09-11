package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.performance.ProgressiveVAOBaker;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * P1：拦截 {@link ModelInstance#lambda$setup$0(Model)}（即原版排入主线程的 VAO 烘焙），
 * 改为分帧烘焙。
 *
 * <p>原版 setup() 在主线程执行 lambda$setup$0，一次性烘焙全部 Group。
 * 这里在它执行前拦截，统计 cube 数量：小模型直接放行（同步烘焙），大模型
 * 提交到 {@link ProgressiveVAOBaker} 分帧队列并取消本次同步烘焙。</p>
 */
@Mixin(value = ModelInstance.class, remap = true)
public abstract class ModelInstanceSetupMixin
{
    @Inject(
        method = "lambda$setup$0",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void bbspp$bakeVAOProgressive(Model model, CallbackInfo ci)
    {
        int cubeCount = countCubes(model);
        boolean bigEnough = cubeCount > 64;

        if (!bigEnough)
        {
            // 小模型：放行，走原版一次性同步烘焙
            return;
        }

        // 大模型：提交分帧烘焙，取消原版同步调用
        ProgressiveVAOBaker.submit((ModelInstance)(Object)this, model, true);
        ci.cancel();
    }

    private static int countCubes(Model model)
    {
        int[] count = {0};
        countRecursive(model.topGroups, count);
        return count[0];
    }

    private static void countRecursive(List<ModelGroup> groups, int[] count)
    {
        for (ModelGroup group : groups)
        {
            count[0] += group.cubes.size();
            for (ModelMesh mesh : group.meshes)
            {
                count[0] += 8;
            }
            if (!group.children.isEmpty())
            {
                countRecursive(group.children, count);
            }
        }
    }
}
