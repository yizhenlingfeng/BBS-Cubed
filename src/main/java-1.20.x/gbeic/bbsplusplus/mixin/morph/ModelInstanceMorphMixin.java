package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import gbeic.bbsplusplus.client.morph.MorphScreenPicks;
import gbeic.bbsplusplus.client.morph.MorphRenderContext;
import gbeic.bbsplusplus.client.morph.MorphTextures;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 模型渲染入口的形变接管。
 *
 * <p>只在「当前表单 + 当前模型实例」确实处于形变区间时压入渲染上下文，并绑定混合贴图；
 * 其余情况完全走 BBS 原版渲染路径，不产生额外开销。</p>
 *
 * <p>压栈/出栈用令牌严格配对：即使渲染中途抛异常，后续渲染也不会拿到错位的上下文。</p>
 *
 * <p>同时在本帧强制关闭 VAO 渲染：形变几何逐帧生成，必须走 CPU 顶点路径。</p>
 */
@Mixin(value = ModelInstance.class, remap = true)
public class ModelInstanceMorphMixin
{
    /* 刻意不使用实例字段保存渲染作用域：ModelInstance 是按模型 id 共享的资源，
       多个表单实例（含 UI 预览与回放）会复用同一个对象，实例字段会被交叉污染，
       表现为「鼠标移动（触发 UI 预览重绘）后光照就变」。
       作用域改为由线程内的渲染上下文栈承载，天然按调用配对。 */

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void bbsppp$beginMorphRender(MatrixStack stack, Supplier<ShaderProgram> program, Color color,
                                         int light, int overlay, StencilMap stencilMap, ShapeKeys keys,
                                         Function<String, Link> textureResolver, CallbackInfo ci)
    {
        ModelInstance instance = (ModelInstance) (Object) this;
        ModelMorphRuntime.RenderScope scope = ModelMorphRuntime.beginRender(instance, stencilMap != null);
        MorphRenderContext.beginInvocation(scope);

        if (scope == null)
        {
            return;
        }

        MorphRenderContext.Frame frame = scope.frame();
        Texture restore = BBSModClient.getTextures().getLastBound();
        MorphRenderContext.attachBinding(restore);
        Texture target = frame.textureSync()
            ? MorphTextures.blend(frame.geometry().plan().sourceTexture(),
                frame.geometry().plan().targetTexture(), frame.progress(), frame.geometry().plan().uvRegions())
            : null;

        /* 绑定状态与作用域一起交给线程内上下文，出栈时按配对恢复，
           不依赖实例字段，因此共享 ModelInstance 的多个表单不会互相影响。 */
        if (target != null)
        {
            BBSModClient.getTextures().bindTexture(target);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void bbsppp$endMorphRender(MatrixStack stack, Supplier<ShaderProgram> program, Color color,
                                       int light, int overlay, StencilMap stencilMap, ShapeKeys keys,
                                       Function<String, Link> textureResolver, CallbackInfo ci)
    {
        ModelMorphRuntime.RenderScope scope = MorphRenderContext.endInvocation();

        if (scope == null)
        {
            return;
        }

        Texture restore = MorphRenderContext.takeBinding();

        if (restore != null)
        {
            BBSModClient.getTextures().bindTexture(restore);
        }

        ModelMorphRuntime.endRender(scope);
    }

    /**
     * 形变期间强制走 CPU 顶点路径：VAO 在加载时烘焙，无法承载逐帧变化的几何。
     */
    @Inject(method = "isVAORendered", at = @At("RETURN"), cancellable = true, require = 1)
    private void bbsppp$disableVaoDuringMorph(CallbackInfoReturnable<Boolean> cir)
    {
        if (!cir.getReturnValue())
        {
            return;
        }

        /* 不能用 bbsppp$scope 判断：BBS 在调用 render() 之前就用 isVAORendered() 选着色器，
           那时渲染上下文还没压栈。必须直接问表单状态，才能保证「着色器选择」与
           「几何提交」始终走同一条路径，否则光照表现会随渲染次序变化。 */
        ModelInstance instance = (ModelInstance) (Object) this;

        if (ModelMorphRuntime.isMorphing(instance))
        {
            cir.setReturnValue(false);

            return;
        }

        /* 采集期间也必须走 CPU 顶点路径：VAO 是预烘焙的，不会调用 renderCube，
           而拾取所需的屏幕矩形正是在 renderCube 里记录的。
           拾取面板显示的是静态模型（没有形变上下文），若仍走 VAO 就永远采集不到。 */
        if (MorphScreenPicks.isCapturing())
        {
            cir.setReturnValue(false);
        }
    }
}
