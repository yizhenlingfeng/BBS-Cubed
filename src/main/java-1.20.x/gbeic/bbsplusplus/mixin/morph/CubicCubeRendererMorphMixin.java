package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.MorphGeometry;
import gbeic.bbsplusplus.client.morph.MorphRenderContext;
import gbeic.bbsplusplus.client.morph.MorphScreenPicks;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * 在 BBS 的 CPU 方块渲染路径上替换几何。
 *
 * <p>注入目标是 {@code CubicCubeRenderer#renderCube}：它正好一次处理一个方块，且此时
 * {@link MatrixStack} 已经包含了该骨骼的绑定姿态与当前动画姿态。接管后逐面提交形变后的
 * 顶点，其余渲染流程（着色器、深度、叠加层、阴影、模板拾取）完全沿用 BBS 原实现。</p>
 *
 * <p>没有形变上下文的方块、或者未被映射的方块，一律不接管，原版行为不变。</p>
 */
@Mixin(targets = "mchorse.bbs_mod.cubic.render.CubicCubeRenderer", remap = true)
public abstract class CubicCubeRendererMorphMixin
{
    @Shadow
    protected Predicate<String> materialVisibility;

    /** 模板拾取阶段只记录方块 ID，不能替换成形变几何，否则 3D 选择会失效。 */
    @Shadow
    private boolean captureOnly;

    /** 顶点色 alpha 乘数：BBS 用它算 {@code this.a * group.color.a}。 */
    @Shadow
    protected float a;

    @Shadow
    protected abstract void writeVertex(BufferBuilder builder, MatrixStack stack, ModelGroup group,
                                        ModelVertex vertex, Vector3f normal);

    @Unique
    private final ModelVertex bbsppp$morphVertex = new ModelVertex();

    @Unique
    private final Vector3f bbsppp$morphNormal = new Vector3f();

    @Inject(method = "renderCube", at = @At("HEAD"), cancellable = true, require = 1)
    private void bbsppp$renderMorphedCube(BufferBuilder builder, MatrixStack stack, ModelGroup group,
                                          ModelCube cube, CallbackInfo ci)
    {
        /* 模板拾取阶段只记录方块 ID，替换几何会让 3D 选择失效。 */
        if (this.captureOnly)
        {
            return;
        }

        /* 只在 UI 明确采集时检查材质可见性，避免把隐藏 Cube 记录为可点选目标。 */
        if (MorphScreenPicks.isCapturing()
            && (this.materialVisibility == null || this.materialVisibility.test(cube.material)))
        {
            bbsppp$recordScreenBox(stack, group, cube);
        }

        MorphRenderContext.Frame frame = MorphRenderContext.current();

        if (frame == null)
        {
            return;
        }

        List<MorphGeometry.Buffer> buffers = frame.geometry().get(cube);

        if (buffers == null || buffers.isEmpty())
        {
            return;
        }

        if (this.materialVisibility != null && !this.materialVisibility.test(cube.material))
        {
            ci.cancel();

            return;
        }

        Matrix3f normalMatrix = new Matrix3f(stack.peek().getNormalMatrix());
        boolean emitted = false;

        for (MorphGeometry.Buffer buffer : buffers)
        {
            if (!buffer.visible())
            {
                continue;
            }

            /* 刻意不用顶点色 alpha 做淡出：BBS 的 emit 里 alpha = this.a * group.color.a，
               调低它会令<b>整个模型</b>变半透明，观感突兀。
               终点的一致性改由「渲染到进度 1.0」保证——t=1 的几何与真实目标模型
               逐顶点相同（已验证误差 0），因此切换不可见。 */
            bbsppp$emit(buffer, builder, stack, group, normalMatrix);
            emitted = true;
        }

        if (!emitted)
        {
            ci.cancel();

            return;
        }

        ci.cancel();
    }

    /**
     * 记录 Cube 的屏幕包围盒、线框角点和真实面三角形，供 UI 的 3D 拾取使用。
     *
     * <p>取渲染栈当前的模型矩阵，把 Cube 的角点与面顶点变换后投影到屏幕；面深度用于
     * 处理重叠 Cube，避免只按外接矩形选择到被遮挡或根本不在鼠标下的对象。</p>
     */
    @Unique
    private void bbsppp$recordScreenBox(MatrixStack stack, ModelGroup group, ModelCube cube)
    {
        /* 只依赖「UI 正在采集」这一状态，<b>不</b>依赖形变上下文：
           拾取面板显示的是静态模型，没有形变上下文。 */
        if (!MorphScreenPicks.isCapturing())
        {
            return;
        }

        String modelId = MorphScreenPicks.capturingModelId();
        String groupPath = MorphScreenPicks.groupPath(group);
        String cubeKey = MorphScreenPicks.keyFor(group, cube);

        if (cubeKey == null || groupPath == null)
        {
            return;
        }

        float[] positions = MorphScreenPicks.cubeCorners(cube);

        if (positions == null)
        {
            return;
        }

        /* 关键：BBS 在调用 renderUserModel 之前已经把 view 与相机平移压进了 MatrixStack
           （见 UIModelRenderer#renderModel：multiply(camera.view) → translate(-camera.position)
           → multiply(transform)）。因此这里<b>只能</b>再乘 projection；
           若把 view 也乘上，等于应用了两次视图变换，投影结果全错——这正是
           「采集正常但点不中」的原因。 */
        org.joml.Matrix4f model = stack.peek().getPositionMatrix();
        org.joml.Matrix4f projection = MorphScreenPicks.captureProjection();

        if (projection == null)
        {
            return;
        }

        float[] faces = MorphScreenPicks.projectFaces(cube, model, projection);

        if (faces == null)
        {
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float depth = Float.MAX_VALUE;
        org.joml.Vector4f clip = new org.joml.Vector4f();

        /* 同时保存 8 个角点的屏幕坐标：UI 侧据此画出真实的三维棱边，
           而不是只在外围套一个相对相机的矩形——后者无法让用户判断立方体的大小与朝向。 */
        float[] corners = new float[16];

        for (int i = 0; i < 8; i++)
        {
            clip.set(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2], 1F);
            model.transform(clip);
            projection.transform(clip);

            if (clip.w <= 1.0E-6F)
            {
                return;
            }

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            float screenX = (ndcX * 0.5F + 0.5F);
            float screenY = (1F - (ndcY * 0.5F + 0.5F));

            corners[i * 2] = screenX;
            corners[i * 2 + 1] = screenY;

            minX = Math.min(minX, screenX);
            maxX = Math.max(maxX, screenX);
            minY = Math.min(minY, screenY);
            maxY = Math.max(maxY, screenY);
            depth = Math.min(depth, clip.w);
        }

        MorphScreenPicks.record(modelId, cubeKey,
            new MorphScreenPicks.Box(groupPath, cubeKey, minX, minY, maxX, maxY, depth, corners, faces));
    }

    @Unique
    private void bbsppp$emit(MorphGeometry.Buffer buffer, BufferBuilder builder, MatrixStack stack,
                             ModelGroup group, Matrix3f normalMatrix)
    {
        float[] positions = buffer.positions();
        float[] normals = buffer.normals();
        float[] uvs = buffer.uvs();
        int faceMask = buffer.faceMask();

        for (int face = 0; face < 6; face++)
        {
            if ((faceMask & (1 << face)) == 0)
            {
                continue;
            }

            int base = face * 4;
            boolean flipped = buffer.winding(face) == 1;

            if (flipped)
            {
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 2);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 1);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 3);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 2);
            }
            else
            {
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 1);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 2);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 2);
                bbsppp$writeVertex(builder, stack, group, positions, normals, uvs, normalMatrix, base + 3);
            }
        }
    }

    @Unique
    private void bbsppp$writeVertex(BufferBuilder builder, MatrixStack stack, ModelGroup group,
                                    float[] positions, float[] normals, float[] uvs,
                                    Matrix3f normalMatrix, int index)
    {
        int offset = index * 3;
        int uvOffset = index * 2;

        this.bbsppp$morphVertex.vertex.set(positions[offset], positions[offset + 1], positions[offset + 2]);
        this.bbsppp$morphVertex.uv.set(uvs[uvOffset], uvs[uvOffset + 1]);
        this.bbsppp$morphNormal.set(normals[offset], normals[offset + 1], normals[offset + 2]);
        normalMatrix.transform(this.bbsppp$morphNormal);
        this.bbsppp$morphVertex.normal.set(this.bbsppp$morphNormal);

        this.writeVertex(builder, stack, group, this.bbsppp$morphVertex, this.bbsppp$morphNormal);
    }
}
