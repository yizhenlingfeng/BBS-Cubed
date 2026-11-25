package gbeic.bbsplusplus.client.ui.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphData;
import java.util.Objects;
import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import gbeic.bbsplusplus.client.morph.ModelMorphSkeleton;
import gbeic.bbsplusplus.client.morph.ModelMorphState;
import gbeic.bbsplusplus.client.morph.MorphScreenPicks;
import gbeic.bbsplusplus.client.texture.ModelBindingGeometry;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import org.joml.Vector3f;

/**
 * 形变预览：用一份合成的 {@link ModelForm} 渲染源模型，并把形变状态写到表单上。
 *
 * <p>真正的形变渲染管线读的就是表单上的 {@code ModelMorphState}，
 * 所以这里不需要复制任何渲染逻辑 —— 拖动进度条就是在跑真正的播放路径。</p>
 */
final class MorphPreview extends UIModelRenderer
{
    private static final float PREVIEW_ENDPOINT_EPSILON = 1.0E-4F;

    private ModelForm form;
    private String modelId;
    private String sourceId;
    private String targetId;
    private ModelMorphData data;
    private ModelMorphData previewData;
    private int previewDataHash = Integer.MIN_VALUE;
    private float progress;

    /** 当前预览的是哪一侧的模型（决定点选落到源还是目标列表）。 */
    private boolean showTarget;

    /** 骨骼快照，供投影拾取的 key 反查与高亮使用。 */
    private ModelMorphSkeleton skeleton;

    /** 点选回调：命中某个 Cube 时通知编辑器。 */
    private java.util.function.BiConsumer<Boolean, String> onPick;

    /** 当前需要高亮的 Cube key 集合（来自列表选择，支持多选）。 */
    private final java.util.Set<String> highlighted = new java.util.LinkedHashSet<>();

    /** 鼠标悬浮命中的 Cube key（预高亮）。 */
    private String hovered;

    /**
     * 渲染时缓存的「投影 × 视图」矩阵与视口。
     *
     * <p>拾取必须在<b>渲染用的同一套矩阵</b>下做：自己用 camera 重新拼一遍很容易
     * 与实际绘制差一个平移或翻转，导致点哪里都选不中。</p>
     */
    private int lastViewportX;
    private int lastViewportY;
    private int lastViewportW;
    private int lastViewportH;
    private boolean hasViewProjection;

    MorphPreview()
    {
        this.grid = true;
        this.setRotation(25F, -10F);
        this.setDistance(4F);
    }

    void setPickHandler(java.util.function.BiConsumer<Boolean, String> handler)
    {
        this.onPick = handler;
    }

    void setHighlighted(java.util.Collection<String> cubeKeys)
    {
        this.highlighted.clear();

        if (cubeKeys != null)
        {
            this.highlighted.addAll(cubeKeys);
        }
    }

    /**
     * 切换预览的是源模型还是目标模型。
     *
     * <p>用户需要能对着源模型点选源 Cube、对着目标模型点选目标 Cube，
     * 两侧的骨骼结构往往完全不同，所以必须能切换。</p>
     */
    boolean isShowingTarget()
    {
        return this.showTarget;
    }

    void setShowTarget(boolean showTarget)
    {
        this.showTarget = showTarget;
        this.modelId = null;
    }

    /** 按下时的鼠标位置，用于区分「点击选块」与「拖拽转视角」。 */
    private int pressX = Integer.MIN_VALUE;
    private int pressY = Integer.MIN_VALUE;

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        /* 基类会把按下事件用于开始拖拽（旋转/平移），这里只记录起点，
           真正的拾取放到释放时做，才能区分「点一下」和「拖着转」。 */
        this.pressX = context.mouseX();
        this.pressY = context.mouseY();

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        boolean handled = super.subMouseReleased(context);
        int x = context.mouseX();
        int y = context.mouseY();
        int startX = this.pressX;
        int startY = this.pressY;

        this.pressX = Integer.MIN_VALUE;
        this.pressY = Integer.MIN_VALUE;

        if (startX == Integer.MIN_VALUE || this.skeleton == null)
        {
            return handled;
        }

        /* 移动超过阈值视为拖拽转视角，不做拾取，否则每次转动视角都会误选。 */
        if (Math.abs(x - startX) > 3 || Math.abs(y - startY) > 3)
        {
            return handled;
        }

        if (!this.area.isInside(x, y))
        {
            return handled;
        }

        String picked = this.pickAt(x, y);

        if (this.onPick != null)
        {
            this.onPick.accept(this.showTarget, picked);
        }

        return picked != null || handled;
    }

    /**
     * 在 2D 坐标系下画出选中/悬浮 Cube 的包围框。
     *
     * <p>调用点必须是 {@code super.render()} 之后：BBS 的 {@code UIModelRenderer}
     * 会在渲染模型期间把 3D 矩阵压栈，结束后才恢复 2D。在压栈期间画屏幕坐标的框
     * 会被 3D 矩阵再变换一次，框因此跑到屏幕外——这是「框从不出现」的原因。</p>
     */
    private void renderHighlights(UIContext context)
    {
        if (this.skeleton == null)
        {
            return;
        }

        for (MorphScreenPicks.Box box : MorphScreenPicks.boxes(this, this.modelId).values())
        {
            String key = resolveCubeKey(this.skeleton, box.groupName(), box.cubeKey());

            if (key == null)
            {
                continue;
            }

            boolean selected = this.highlighted.contains(key);
            boolean hover = key.equals(this.hovered);

            if (!selected && !hover)
            {
                continue;
            }

            /* 选中用亮黄、悬浮用半透明白——后者与 BBS 原版模型编辑器一致
               （HOVER_COLOR = Colors.setA(Colors.WHITE, 0.6F)）。 */
            int color = selected ? 0xFFFFC107 : 0x99FFFFFF;

            if (box.hasCorners())
            {
                this.drawEdges(context, box, color);
            }
            else
            {
                this.drawBoxRect(context, box, color);
            }
        }
    }

    /**
     * 画出立方体的 12 条真实棱边。
     *
     * <p>比外接矩形更能表达立方体的大小与朝向——矩形只反映屏幕包围盒，
     * 斜视角下会明显大于实际体积，用户无法据此判断选中的是哪一块。</p>
     */
    private void drawEdges(UIContext context, MorphScreenPicks.Box box, int color)
    {
        float[] corners = box.corners();

        for (int[] edge : MorphScreenPicks.Box.EDGES)
        {
            float ax = this.area.x + corners[edge[0] * 2] * this.area.w;
            float ay = this.area.y + corners[edge[0] * 2 + 1] * this.area.h;
            float bx = this.area.x + corners[edge[1] * 2] * this.area.w;
            float by = this.area.y + corners[edge[1] * 2 + 1] * this.area.h;

            this.drawLine(context, ax, ay, bx, by, color);
        }
    }

    /**
     * 用一像素宽的细长矩形近似一条线。
     *
     * <p>BBS 的 {@code Batcher2D} 只有 {@code box} 与 {@code outline}（轴对齐矩形），
     * 没有任意线段 API；而我们在 2D pass 里画，只能用它。用 box 铺线在像素级
     * 足够精确，且与 UI 其它元素共用同一批次。</p>
     */
    private void drawLine(UIContext context, float x1, float y1, float x2, float y2, int color)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.5F)
        {
            return;
        }

        /* 沿线段取样，步长 1px：斜线也能连续。 */
        int steps = (int) Math.ceil(length);
        float stepX = dx / steps;
        float stepY = dy / steps;

        for (int i = 0; i <= steps; i++)
        {
            float px = x1 + stepX * i;
            float py = y1 + stepY * i;

            context.batcher.box(px, py, px + 1F, py + 1F, color);
        }
    }

    /**
     * 没有角点数据时退回外接矩形（兼容旧记录）。
     */
    private void drawBoxRect(UIContext context, MorphScreenPicks.Box box, int color)
    {
        int x1 = Math.round(this.area.x + box.minX() * this.area.w);
        int x2 = Math.round(this.area.x + box.maxX() * this.area.w);
        int y1 = Math.round(this.area.y + box.minY() * this.area.h);
        int y2 = Math.round(this.area.y + box.maxY() * this.area.h);

        if (x2 <= x1 || y2 <= y1)
        {
            return;
        }

        context.batcher.outline(x1, y1, x2, y2, color);
    }

    /**
     * 根据当前鼠标位置更新悬浮高亮。
     *
     * <p>与点击拾取走同一条数据（采集到的屏幕矩形），因此悬浮与点击必然一致。</p>
     */
    private void updateHover(UIContext context)
    {
        this.hovered = null;

        if (!this.area.isInside(context.mouseX(), context.mouseY()) || this.skeleton == null)
        {
            return;
        }

        MorphScreenPicks.Box box = this.pickBox(context.mouseX(), context.mouseY());

        if (box != null)
        {
            this.hovered = resolveCubeKey(this.skeleton, box.groupName(), box.cubeKey());
        }
    }

    /**
     * 拾取面板专用配置：只显示一侧的静态模型，不参与形变。
     *
     * <p>选块需要看清每个方块的真实形状，叠加形变会让轮廓变糊、反而难以点准。</p>
     */
    void configurePicker(String modelId)
    {
        this.sourceId = modelId;
        this.targetId = null;
        this.data = null;
        this.previewData = null;
        this.progress = 0F;

        if (!Objects.equals(modelId, this.modelId))
        {
            this.modelId = modelId;
            this.skeleton = modelId == null || modelId.isEmpty() ? null : MorphMappingSelection.skeletonOf(modelId);
            this.buildForm(modelId);
        }

        this.applyState();
    }

    void configure(String sourceId, String targetId, ModelMorphData data, float progress)
    {
        boolean modelPairChanged = UICubeMappingEditorPanel.shouldRefitCamera(this.sourceId, this.targetId, sourceId, targetId);

        this.sourceId = sourceId;
        this.targetId = targetId;
        this.data = data;
        this.progress = progress;

        /* 预览必须能看到形变，即使关键帧上的 Morph 开关还没打开：
           这里用一份强制开启的副本，真实关键帧数据不受影响。 */
        if (data != null && data.hashCode() != this.previewDataHash)
        {
            this.previewDataHash = data.hashCode();
            this.previewData = data.copy();
            this.previewData.setEnabled(true);
        }

        /* 预览跟随当前要看的一侧：源与目标的骨骼结构通常完全不同，
           必须能分别对着看、对着点。源模型缺失时退化成目标模型。 */
        String id = UICubeMappingEditorPanel.previewModelId(this.showTarget, sourceId, targetId, progress,
            ModelMorphRuntime.model(sourceId) != null, ModelMorphRuntime.model(targetId) != null);

        if (!Objects.equals(id, this.modelId) || modelPairChanged)
        {
            this.modelId = id;
            this.skeleton = id == null || id.isEmpty() ? null : MorphMappingSelection.skeletonOf(id);
            /* At 100%, only the displayed endpoint model changes. Keep the user's orbit camera;
               refit only when the source/target pair itself changes. */
            this.buildForm(id, modelPairChanged);
        }

        this.applyState();
    }

    private void buildForm(String id)
    {
        this.buildForm(id, true);
    }

    private void buildForm(String id, boolean fitCamera)
    {
        this.form = null;

        if (id == null || id.isEmpty() || ModelMorphRuntime.model(id) == null)
        {
            return;
        }

        ModelForm previewForm = new ModelForm();

        previewForm.model.set(id);
        this.form = previewForm;

        if (fitCamera)
        {
            this.fitCamera(previewForm);
        }
    }

    private void applyState()
    {
        if (this.form == null)
        {
            return;
        }

        ModelMorphState state = ModelMorphRuntime.state(this.form);

        if (state == null)
        {
            return;
        }

        /* 拾取面板没有目标模型，天然走清空分支，显示纯模型。 */
        boolean sourceIsDisplayed = this.sourceId != null && this.sourceId.equals(this.form.model.get());

        if (sourceIsDisplayed && this.targetId != null && this.progress > 0F
            && this.progress < 1F - PREVIEW_ENDPOINT_EPSILON)
        {
            state.set(this.sourceId, this.targetId, this.progress,
                this.previewData == null ? this.data : this.previewData);
        }
        else
        {
            state.clear();
        }
    }

    private void fitCamera(ModelForm previewForm)
    {
        ModelBindingGeometry geometry = ModelBindingGeometry.create(previewForm);

        if (geometry == null)
        {
            this.setPosition(0F, 0.5F, 0F);
            this.setDistance(4F);
            this.setRotation(25F, -10F);

            return;
        }

        Vector3f min = geometry.min();
        Vector3f max = geometry.max();
        Vector3f center = new Vector3f(min).add(max).mul(0.5F);
        float span = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));

        this.setPosition(-center.x, center.y, -center.z);
        this.setDistance(Math.max(1F, span * 1.3F));
        this.setRotation(25F, -10F);
    }

    private String pickAt(int mouseX, int mouseY)
    {
        if (this.modelId == null || this.modelId.isEmpty())
        {
            return null;
        }

        /* 用渲染时记录的真实屏幕矩形做拾取。
           这些矩形由渲染器在 renderCube 阶段用最终模型矩阵投影得到，
           与画面完全一致，不依赖 UI 侧重拼变换链。 */
        MorphScreenPicks.Box box = this.pickBox(mouseX, mouseY);

        if (box == null)
        {
            return null;
        }

        /* 渲染侧和快照侧都使用完整骨骼路径，再配短 Cube key 精确反查。 */
        return resolveCubeKey(this.skeleton, box.groupName(), box.cubeKey());
    }

    /**
     * 把屏幕坐标换算成采集表里的归一化坐标并查询。
     */
    private MorphScreenPicks.Box pickBox(int mouseX, int mouseY)
    {
        if (this.modelId == null || this.modelId.isEmpty() || this.lastViewportW <= 0 || this.lastViewportH <= 0)
        {
            return null;
        }

        /* 采集端已经把 Y 翻成「屏幕向下」的约定（见 CubicCubeRendererMorphMixin：
           screenY = 1 - (ndcY * 0.5 + 0.5)），因此这里<b>不能再翻</b>。
           两边都翻会导致双重翻转，回到垂直镜像——这正是上一轮「修了还是 miss」的原因。 */
        float ndcX = (mouseX - this.lastViewportX) / (float) this.lastViewportW;
        float ndcY = (mouseY - this.lastViewportY) / (float) this.lastViewportH;

        return MorphScreenPicks.pick(this, this.modelId, ndcX, ndcY, 0.01F);
    }

    /**
     * 用「父级组名 + 短 key」在骨架里反查 Cube 的完整 key。
     *
     * <p>组名在同一模型内唯一，因此这一步不会产生歧义。</p>
     */
    private static String resolveCubeKey(ModelMorphSkeleton skeleton, String groupPath, String shortKey)
    {
        if (skeleton == null || groupPath == null || shortKey == null)
        {
            return null;
        }

        for (ModelMorphSkeleton.GroupSpec group : skeleton.groups())
        {
            if (!groupPath.equals(group.path()))
            {
                continue;
            }

            for (ModelMorphSkeleton.CubeSpec cube : group.cubes())
            {
                if (MorphMappingSelection.shortCubeKey(cube, group).equals(shortKey))
                {
                    return cube.key();
                }
            }
        }

        return null;
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff141414);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A25);

        /* 采集必须<b>围绕</b>本次绘制：renderCube 在这期间被调用，
           把每个 Cube 的屏幕矩形写进记录表。
           矩阵用<b>上一帧</b>缓存的值——相机投影在 super.render() 内部才更新，
           而同一帧内用它采集与绘制是自洽的；跨帧只差一次鼠标移动，
           对拾取没有可感知影响。 */
        /* 矩阵还没算出来（首帧）时不要采集：用单位矩阵投影会得到全错的矩形，
           而且会覆盖掉上一帧的正确数据。 */
        if (this.hasViewProjection && this.camera != null)
        {
            /* 只传 projection：渲染栈里已经有 view 与相机平移了。 */
            MorphScreenPicks.beginCapture(this, this.modelId, this.camera.projection,
                this.area.x, this.area.y, this.area.w, this.area.h);
        }

        try
        {
            super.render(context);
        }
        finally
        {
            MorphScreenPicks.endCapture();
        }

        /* 悬浮预高亮：UIElement 没有 hover 事件，但 render 每帧执行、
           context 里鼠标坐标实时可用，因此在这里算最直接。 */
        this.updateHover(context);

        /* 高亮框必须画在 super.render() <b>之后</b>：那时 UIModelRenderer 已经
           restoreMatrices() 恢复成 2D 坐标系，屏幕坐标才有效。 */
        this.renderHighlights(context);

        /* 绘制结束后刷新矩阵，供下一帧采集与拾取使用。 */
        if (this.camera != null)
        {
            this.lastViewportX = this.area.x;
            this.lastViewportY = this.area.y;
            this.lastViewportW = this.area.w;
            this.lastViewportH = this.area.h;
            this.hasViewProjection = true;
        }
    }

    @Override
    protected void renderUserModelOverlay(UIContext context)
    {
        /* 刻意不在这里画高亮框。
           BBS 的 UIModelRenderer#renderModel 在 stack.push() 之后、stack.pop() 之前
           调用本方法，此时 MatrixStack 里仍是 3D 投影矩阵；在这里用屏幕坐标
           batcher.outline 画框会被 3D 矩阵再变换一次，框会跑到屏幕外。
           正确位置是 super.render() 之后——那里已 restoreMatrices() 恢复成 2D 坐标系。 */
        super.renderUserModelOverlay(context);
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, context.batcher.getContext().getMatrices(),
                LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        FormUtilsClient.render(this.form, formContext);
    }
}
