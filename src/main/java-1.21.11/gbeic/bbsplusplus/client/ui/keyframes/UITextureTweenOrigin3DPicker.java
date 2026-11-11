package gbeic.bbsplusplus.client.ui.keyframes;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.client.texture.ModelBindingGeometry;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * 纹理补间扩散起点的三维模型表面取点器。
 *
 * <p>组件以模型绑定姿态显示当前纹理，右键通过射线与模型三角形求交选择表面点；左键拖动旋转、
 * Shift+左键或中键拖动平移、Ctrl+滚轮缩放 —— 这三项全部交给基类 {@link UIModelRenderer}
 * 自己的拖拽系统处理。底部三个数值框用于百分比微调，三维十字标记始终与运行时扩散使用的
 * AABB 归一化模型坐标保持同步。</p>
 *
 * <p>1.21.11 移植要点：</p>
 * <ul>
 *     <li>{@code DrawContext.getMatrices()} 现在是 2D 的 {@link Matrix3x2fStack}，滚动补偿只能
 *     {@code pushMatrix()/translate(float,float)/popMatrix()}，不再进 3D 矩阵栈。</li>
 *     <li>模型的 3D 栈必须用基类的 {@link UIModelRenderer#createCameraStack()}（相机 view *
 *     translate(-pos) * transform），否则顶点会落在错误的空间里。</li>
 *     <li>{@code RenderSystem.setShader} / {@code BufferRenderer.drawWithGlobalProgram} /
 *     {@code RenderSystem.disableDepthTest()} 已全部删除，线段改走自建的
 *     {@link RenderLayer}（{@code POSITION_COLOR_SNIPPET} + {@code DEBUG_LINES} + 不测深度）。</li>
 *     <li>{@code GL11.glClear(GL_DEPTH_BUFFER_BIT)} 不再由 UI 层直接调用，深度清理由预览 FBO
 *     的渲染通道负责，因此 {@code setupViewport} 只保留视口与相机矩阵的更新。</li>
 * </ul>
 */
public class UITextureTweenOrigin3DPicker extends UIElement
{
    private static final int CONTROL_HEIGHT = 20;

    /**
     * 三维十字标记专用管线：DEBUG_LINES + 不测深度。
     *
     * <p>1.20 的写法是 {@code RenderSystem.disableDepthTest()} 后再画，保证标记永远压在模型之上；
     * 1.21.11 的深度状态写在管线里，所以要单独建一条 {@link DepthTestFunction#NO_DEPTH_TEST} 的线管线，
     * 不能复用 {@code Draw.flushLines}（那条是 LEQUAL 深度测试的）。</p>
     */
    private static final RenderPipeline MARKER_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("bbsplusplus", "pipeline/texture_tween_origin_marker"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer markerLayer;

    private final Consumer<Vector3f> callback;
    private final Preview preview;
    private final UITrackpad x;
    private final UITrackpad y;
    private final UITrackpad z;
    private boolean syncing;

    public UITextureTweenOrigin3DPicker(ModelForm form, Consumer<Vector3f> callback, Runnable editStart, Runnable editEnd)
    {
        this.callback = callback;
        this.preview = new Preview(form, this::setFromPreview, editStart, editEnd);
        this.x = this.createTrackpad(0, editStart, editEnd);
        this.y = this.createTrackpad(1, editStart, editEnd);
        this.z = this.createTrackpad(2, editStart, editEnd);

        UIElement controls = UI.row(4,
            UI.label(IKey.raw("X"), CONTROL_HEIGHT).w(8), this.x,
            UI.label(IKey.raw("Y"), CONTROL_HEIGHT).w(8), this.y,
            UI.label(IKey.raw("Z"), CONTROL_HEIGHT).w(8), this.z
        );

        this.preview.relative(this).w(1F).h(1F, -CONTROL_HEIGHT - 4);
        controls.relative(this).y(1F, -CONTROL_HEIGHT).w(1F).h(CONTROL_HEIGHT);
        this.add(this.preview, controls);
        this.wh(240, 224);
    }

    public UITextureTweenOrigin3DPicker setValue(float x, float y, float z)
    {
        this.setInternal(x, y, z);

        return this;
    }

    @Override
    public void render(UIContext context)
    {
        int bottom = this.area.ey() - CONTROL_HEIGHT - 4;

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), bottom, 0xff181818);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), bottom, Colors.A50);
        super.render(context);
    }

    private UITrackpad createTrackpad(int axis, Runnable editStart, Runnable editEnd)
    {
        UITrackpad trackpad = new UITrackpad((value) -> this.setFromTrackpad(axis, value.floatValue() / 100F))
            .limit(0D, 100D).values(1D, 0.1D, 5D).delayedInput();

        trackpad.getEvents().register(UITrackpadDragStartEvent.class, (event) -> editStart.run());
        trackpad.getEvents().register(UITrackpadDragEndEvent.class, (event) -> editEnd.run());

        return trackpad;
    }

    private void setFromPreview(Vector3f point)
    {
        this.setInternal(point.x, point.y, point.z);
        this.callback.accept(new Vector3f(point));
    }

    private void setFromTrackpad(int axis, float value)
    {
        if (this.syncing)
        {
            return;
        }

        Vector3f point = this.preview.getValue();

        point.setComponent(axis, clamp(value));
        this.setInternal(point.x, point.y, point.z);
        this.callback.accept(point);
    }

    private void setInternal(float x, float y, float z)
    {
        this.syncing = true;
        this.preview.setValue(clamp(x), clamp(y), clamp(z));
        this.x.setValue(clamp(x) * 100F);
        this.y.setValue(clamp(y) * 100F);
        this.z.setValue(clamp(z) * 100F);
        this.syncing = false;
    }

    private static float clamp(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }

    private static RenderLayer markerLayer()
    {
        if (markerLayer == null)
        {
            markerLayer = RenderLayer.of("bbsplusplus_texture_tween_origin_marker",
                RenderSetup.builder(MARKER_PIPELINE).translucent().build());
        }

        return markerLayer;
    }

    private static class Preview extends UIModelRenderer
    {
        private final ModelForm form;
        private final ModelBindingGeometry geometry;
        private final Consumer<Vector3f> callback;
        private final Runnable editStart;
        private final Runnable editEnd;
        private final Vector3f value = new Vector3f(0.5F);
        private final Area viewportArea = new Area();
        private float bbsppDistance = 2.25F;

        private Preview(ModelForm source, Consumer<Vector3f> callback, Runnable editStart, Runnable editEnd)
        {
            this.callback = callback;
            this.editStart = editStart;
            this.editEnd = editEnd;
            /* 复用 BBS 模型预览器自带的地面网格和红、蓝坐标轴。 */
            this.grid = true;

            if (source == null)
            {
                this.form = null;
                this.geometry = null;

                return;
            }

            this.form = new ModelForm();
            this.form.model.set(source.model.get());
            this.form.texture.set(source.texture.get());
            this.form.color.set(source.color.get());
            this.geometry = ModelBindingGeometry.create(this.form);
            this.fitCamera();
        }

        private Vector3f getValue()
        {
            return new Vector3f(this.value);
        }

        private void setValue(float x, float y, float z)
        {
            this.value.set(x, y, z);
        }

        /**
         * 抵消滚动容器写入矩阵栈的二维平移。
         *
         * <p>模型的预览视口已经跟随滚动后的屏幕位置（{@link #setupViewport(UIContext)} 用全局坐标换算），
         * 如果继续继承二维内容矩阵中的负向滚动量，模型还会在自己的视口内部再次移动一次。</p>
         *
         * <p>1.21.11 的 {@code DrawContext.getMatrices()} 是 {@link Matrix3x2fStack}，只有
         * {@code pushMatrix()/translate(float,float)/popMatrix()}，不能再像 1.20 那样往 3D 栈里塞。</p>
         */
        @Override
        public void render(UIContext context)
        {
            Matrix3x2fStack matrices = context.batcher.getContext().getMatrices();

            matrices.pushMatrix();
            matrices.translate(context.viewportStack.getShiftX(), context.viewportStack.getShiftY());
            super.render(context);
            matrices.popMatrix();
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (!this.area.isInside(context))
            {
                return false;
            }

            if (context.mouseButton == 1)
            {
                Vector3f point = this.pick(context);

                if (point != null)
                {
                    this.editStart.run();
                    this.value.set(point);
                    this.callback.accept(point);
                    this.editEnd.run();
                }

                return true;
            }

            /* 左键（含 Shift+左键的平移）必须交回基类 UIModelRenderer 自己的拖拽系统。
             *
             * 2.6 起基类把相机姿态的唯一真源放在**私有**的 targetRotation / targetPos /
             * targetDistance 上，每帧再从它们同步（或插值）进 camera —— 直接改
             * camera.rotation 会被下一帧覆盖，表现为"按住左键拖不动视角"。
             * 同时基类的拖拽状态 dragging 只在它自己的 subMouseClicked 里置位，
             * 不调 super 就永远不会开始拖拽。 */
            return super.subMouseClicked(context);
        }

        /**
         * 将模型缩放与属性面板滚动明确分流。
         *
         * <p>普通滚轮不在此处处理，让外层滚动面板正常移动；只有鼠标位于预览区并按住 Ctrl 时
         * 才调整相机距离，同时消费事件，防止外层面板也跟着滚动。</p>
         */
        @Override
        public boolean subMouseScrolled(UIContext context)
        {
            if (!this.area.isInside(context) || !Window.isCtrlPressed())
            {
                return false;
            }

            if (!this.isDragging())
            {
                int step = Double.compare(-context.mouseWheel, 0D);

                this.bbsppDistance += step;
                this.setDistance(this.bbsppDistance);
            }

            return true;
        }

        @Override
        protected void renderUserModel(UIContext context)
        {
            if (this.form == null || this.geometry == null)
            {
                return;
            }

            /* 1.21.11：立方体几何会把相机模型视图烘进顶点（见 UIModelRenderer#createCameraStack 的注释），
             * 所以必须用 createCameraStack()，不能再用 2D 的内容矩阵。 */
            FormRenderingContext formContext = new FormRenderingContext()
                .set(FormRenderType.PREVIEW, this.entity, this.createCameraStack(),
                    LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
                .camera(this.camera)
                .modelRenderer(context.getTick());

            FormUtilsClient.render(this.form, formContext);
            this.renderMarker(context);
        }

        /**
         * 记录视口矩形。
         *
         * <p>BBS 基类直接使用元素的内容坐标；元素位于 {@code UIScrollView} 时，二维矩阵会随滚动偏移，
         * 视口却停在原处，随后被滚动区域裁掉。这里通过 UI 上下文换算全局坐标，使模型与边框同步滚动。</p>
         *
         * <p>1.21.11 不再在 UI 层 {@code glClear(GL_DEPTH_BUFFER_BIT)}，深度清理由预览 FBO 自己的
         * 渲染通道负责；相机投影与视图矩阵仍由基类计算。</p>
         */
        @Override
        protected void setupViewport(UIContext context)
        {
            this.viewportArea.set(context.globalX(this.area.x), context.globalY(this.area.y), this.area.w, this.area.h);

            super.setupViewport(context);
        }

        private Vector3f pick(UIContext context)
        {
            if (this.geometry == null)
            {
                return null;
            }

            this.setupPosition();

            Vector3f origin = new Vector3f((float) this.camera.position.x, (float) this.camera.position.y,
                (float) this.camera.position.z);
            Vector3f direction = this.camera.getMouseDirection(
                context.mouseX, context.mouseY, this.area.x, this.area.y, this.area.w, this.area.h
            );
            Matrix4f inversePreviewRotation = new Matrix4f().rotateY(-MathUtils.PI);

            inversePreviewRotation.transformPosition(origin);
            inversePreviewRotation.transformDirection(direction).normalize();

            Vector3f hit = this.geometry.raycast(origin, direction);

            return hit == null ? null : this.geometry.normalize(hit);
        }

        private void renderMarker(UIContext context)
        {
            Vector3f point = this.geometry.denormalize(this.value.x, this.value.y, this.value.z);
            Vector3f min = this.geometry.min();
            Vector3f max = this.geometry.max();
            float size = Math.max(0.02F, new Vector3f(max).sub(min).length() * 0.018F);
            MatrixStack stack = this.createCameraStack();

            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));

            Matrix4f matrix = stack.peek().getPositionMatrix();
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            line(builder, matrix, point.x - size, point.y, point.z, point.x + size, point.y, point.z, 1F, 0.2F, 0.2F);
            line(builder, matrix, point.x, point.y - size, point.z, point.x, point.y + size, point.z, 0.2F, 1F, 0.2F);
            line(builder, matrix, point.x, point.y, point.z - size, point.x, point.y, point.z + size, 0.2F, 0.55F, 1F);

            BuiltBuffer built = builder.endNullable();

            if (built != null)
            {
                markerLayer().draw(built);
            }
        }

        private void fitCamera()
        {
            if (this.geometry == null)
            {
                return;
            }

            Vector3f min = this.geometry.min();
            Vector3f max = this.geometry.max();
            Vector3f center = new Vector3f(min).add(max).mul(0.5F);
            float span = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));
            float desiredDistance = Math.max(1F, span * 1.8F);

            this.setPosition(-center.x, center.y, -center.z);
            this.bbsppDistance = Math.max(1, Math.round((float) Math.sqrt(desiredDistance * 100F)));
            this.setDistance(this.bbsppDistance);
            this.setRotation(25F, -10F);
        }

        private static void line(BufferBuilder builder, Matrix4f matrix, float ax, float ay, float az,
                                 float bx, float by, float bz, float red, float green, float blue)
        {
            /* 1.21.11 的 VertexConsumer 没有 next()：每个 vertex() 调用自己就结束一个顶点。 */
            builder.vertex(matrix, ax, ay, az).color(red, green, blue, 1F);
            builder.vertex(matrix, bx, by, bz).color(red, green, blue, 1F);
        }
    }
}
