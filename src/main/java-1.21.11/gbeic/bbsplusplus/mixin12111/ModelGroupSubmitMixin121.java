package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.grade.BoneGroupResolve121;
import gbeic.bbsplusplus.client.grade.BoneMaterialContext121;
import gbeic.bbsplusplus.client.grade.GradeContext;
import gbeic.bbsplusplus.client.grade.GradeParams;
import gbeic.bbsplusplus.client.grade.ModelGradeResolver;
import gbeic.bbsplusplus.client.grade.ModelGroupStats121;
import gbeic.bbsplusplus.client.grade.ModelGroupSubmit121;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * 施工表 B2-01：把 1.21.11 的「整模型一次提交」拆成「每个顶层骨骼组一次提交」，
 * 并在每次提交前解析该组自己的贴图（B2-03）、PBR（B2-02）与剔除开关（B2-04）、
 * 分级参数（B2-06 的逐组光效）。
 *
 * <p>上游 {@code ModelInstance#render} 的立方体分支是：每个材质通道 → 绑定该材质贴图 →
 * 一个 {@code BufferBuilder} → 走遍全部顶层组 → {@code endNullable()} → 一条
 * {@code FormTranslucentQueue.submit}。逐骨骼特性在 1.20.x 靠「同一次 draw 内换 uniform /
 * 换绑定 / 开关 GL 剔除」实现，但 1.21.11 把这些状态全部固化在那条提交所用的
 * {@code RenderLayer} 上，所以必须先把提交拆开。
 * </p>
 *
 * <p>拆法：把 {@code CubicRenderer#processRenderModel} 那一次调用接管掉，忽略上游传进来的
 * {@code builder}（刻意留空，好让上游随后的 {@code endNullable()} 返回 null 从而跳过它自己的
 * 提交），改为按顶层组各开一个 {@code BufferBuilder}、各排空焊接补丁、各提交一次。
 * 遍历顺序与组内顶点顺序与上游完全一致，因此只增加 draw call、不改变画面合成顺序。
 * </p>
 *
 * <p>拾取通道与面板预览不走拆分：这两条分支的绘制在调用方、位于本次提交之后，
 * 提前提交会绕过它们（见 {@link ModelGroupSubmit121#canSplit}）。</p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public class ModelGroupSubmitMixin121
{
    private static final String PROCESS_RENDER_MODEL = "Lmchorse/bbs_mod/cubic/render/CubicRenderer;processRenderModel(Lmchorse/bbs_mod/cubic/render/ICubicRenderer;Lnet/minecraft/client/render/BufferBuilder;Lnet/minecraft/client/util/math/MatrixStack;Lmchorse/bbs_mod/cubic/data/model/Model;)Z";

    @Unique
    private StencilMap bbspp$stencilMap;

    @Unique
    private Color bbspp$renderColor;

    @Inject(method = "render", at = @At("HEAD"))
    private void bbspp$captureSubmitContext(MatrixStack stack, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Texture> textureResolver, CallbackInfo info)
    {
        this.bbspp$stencilMap = stencilMap;
        this.bbspp$renderColor = color;

        /* 绝对不能在这里清 {@link BoneMaterialContext121}：调用方（{@code ModelFormRenderer#render3D}）
         * 在调 {@code model.render(...)} 之前先执行 {@code bindTexture(this.albedo("", texture))}，
         * 那一次 albedo("") 正是把本模型的材质 link 记进上下文的唯一时机 —— 单材料模型在下面的
         * 材质循环里不会再解析贴图（{@code renderPasses} 直接返回 SINGLE_PASS），也就是说清掉
         * 之后再没有任何地方会写回它，逐骨骼 PBR 就会永远拿到 null link 而静默失效
         * （实机表现：肢体 PBR 滑条毫无反应）。上下文只在本方法 RETURN 时清。 */
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void bbspp$releaseSubmitContext(MatrixStack stack, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Texture> textureResolver, CallbackInfo info)
    {
        this.bbspp$stencilMap = null;
        this.bbspp$renderColor = null;

        BoneMaterialContext121.clear();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = PROCESS_RENDER_MODEL))
    private boolean bbspp$submitPerGroup(ICubicRenderer renderProcessor, BufferBuilder builder, MatrixStack stack, Model model)
    {
        ModelInstance self = (ModelInstance) (Object) this;
        boolean preview = ModelPreviewRenderer.ACTIVE && ModelPreviewRenderer.TEXTURE != null;

        if (!ModelGroupSubmit121.canSplit(model, this.bbspp$stencilMap != null, preview))
        {
            ModelGroupStats121.recordFallback();

            return CubicRenderer.processRenderModel(renderProcessor, builder, stack, model);
        }

        ModelForm form = self.form instanceof ModelForm modelForm ? modelForm : null;
        String material = BoneMaterialContext121.material();
        Link link = BoneMaterialContext121.link();

        /* 上下文没记到时退回模型的有效贴图 —— 上游 {@code ModelFormRenderer} 自己也是这么定的：
         * {@code Link texture = link == null ? model.getTexture() : link;}，所以这里与它同源，
         * 不会引入上游没有的贴图。 */
        if (link == null)
        {
            link = self.getTexture();
        }

        Color color = this.bbspp$renderColor;
        StencilMap stencilMap = this.bbspp$stencilMap;
        float alpha = color == null ? 1F : color.a;
        /* lambda 捕获要求 effectively final：link 上面被兜底赋过值，这里取一份终态副本。 */
        final Link groupLink = link;
        final String groupMaterial = material;
        Texture bound = BBSModClient.getTextures().getLastBound();
        GradeParams previousGrade = GradeContext.current();
        Vector3f origin = ModelVAORenderer.captureModelView(stack).getTranslation(new Vector3f());

        /* 两阶段：先把每组的缓冲造好（遍历期间颜色调色板被填满），上传调色板，再逐组提交。
         * 边遍历边提交会让先提交的组采样到还没填完的调色板。 */
        ModelGroupSubmit121.split(renderProcessor, model, stack, buffers ->
        {
            boolean tinted = FormOverlay.hasPalette();

            if (tinted)
            {
                FormOverlay.commitPalette();
            }

            for (ModelGroupSubmit121.GroupBuffer buffer : buffers)
            {
                ModelGroup group = buffer.group();

                /* 逐组贴图：骨骼贴图覆盖 → 材质级 PBR → 骨骼级 PBR，最终落到该组要绑的那张。 */
                Texture texture = BoneGroupResolve121.resolveTexture(form, groupMaterial, groupLink, bound, group);

                /* 逐组剔除：肢体剔除名单内的组走 culled 变体（1.20.x 用 RenderSystem.enableCull）。 */
                boolean cull = BoneGroupResolve121.culled(self, self.isCulling(), group);

                /* 逐组分级参数：调色/白化仍是整表单级，光效取这一组自己的。 */
                GradeParams grade = ModelGradeResolver.resolveForGroup(self, group);

                boolean rebound = texture != null && texture != bound
                    && BBSModClient.getTextures() != null;

                if (rebound)
                {
                    /* submit 解析 RenderLayer 时取「最后绑定的贴图」，所以必须在它之前绑好。 */
                    BBSModClient.getTextures().bindTexture(texture);
                }

                GradeContext.push(grade);

                try
                {
                    FormTranslucentQueue.submit(buffer.built(),
                        new BBSShaders.ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, cull),
                        texture, alpha, stencilMap, new Vector3f(origin), tinted);
                }
                finally
                {
                    GradeContext.push(previousGrade);

                    if (rebound)
                    {
                        BBSModClient.getTextures().bindTexture(bound);
                    }
                }
            }
        });

        /* 无论拆成功与否都不再补一次整模型提交：失败时 ModelGroupSubmit121 会永久标记 broken，
         * 下一帧起 canSplit 恒为 false，自然走回整模型路径。 */
        return false;
    }
}
