package gbeic.bbsplusplus.client.morph;

/**
 * 单个模型表单在某一帧的形变状态。
 *
 * <p>由关键帧求值阶段写入、渲染阶段读取。每次属性应用前会先 {@link #clear()}，
 * 避免播放头跳转后残留旧状态。</p>
 *
 * <p>注意：{@code clear()} 只清「本帧是否处于形变区间」这类瞬时信息，
 * 已建立的 {@link MorphGeometry} 采样缓冲区按 (源, 目标, 配置) 保留复用。
 * 形变区间内每帧都会先 clear 再 set，若连缓冲区一起丢弃，就会变成每帧重新分配
 * 所有顶点数组——这正是本类刻意区分 transient 与 cached 两部分的原因。</p>
 */
public final class ModelMorphState
{
    private String sourceId = "";
    private String targetId = "";
    private float progress;
    private ModelMorphData data;

    /** 该区间结束时实际切换到的模型 id，仅用于校验提示，不参与渲染。 */
    private String nextModelId = "";

    private MorphGeometry geometry;
    private String geometrySource = "";
    private String geometryTarget = "";
    private int geometryHash = Integer.MIN_VALUE;

    /**
     * 是否处于「形变应当接管渲染」的状态。
     *
     * <p>与 {@link #active()} 的区别：本方法只要求状态已由关键帧求值写入且落在形变区间内，
     * 不依赖几何是否已解析。BBS 在调用 {@code render()} <b>之前</b>就用
     * {@code isVAORendered()} 决定着色器，那时渲染上下文还没压栈，必须靠这里做判定，
     * 否则着色器与几何路径会不一致，光照表现随渲染次序抖动。</p>
     */
    public boolean morphing()
    {
        return this.active();
    }


    /**
     * 是否应当接管渲染。
     *
     * <p>必须严格在 {@code 0 < progress < 1} 之间接管：端点的外观由 BBS 自己的
     * 模型渲染负责，若在端点也接管，就会同时绘制「形变几何」与「真实模型」两套
     * 完全重合的几何，产生 z-fighting 与额外开销——表现为画面细微卡顿。</p>
     *
     * <p>端点切换之所以可见，是因为 t=0/t=1 的过渡几何与真实模型之间还有极小的
     * 浮点差异；这由 {@link #SNAP_EPSILON} 处理，而不是靠端点也接管来掩盖。</p>
     */
    public boolean active()
    {
        return this.data != null && this.data.enabled()
            && !this.sourceId.isEmpty() && !this.targetId.isEmpty()
            && this.progress > SNAP_EPSILON
            && this.progress < 1F - SNAP_EPSILON;
    }

    /**
     * 端点吸附阈值。
     *
     * <p>进度落在这段范围内就直接按端点处理（交给真实模型），
     * 避免在端点附近反复接管/退出造成闪烁。</p>
     */
    private static final float SNAP_EPSILON = 1.0E-4F;

    public String sourceId()
    {
        return this.sourceId;
    }

    public String targetId()
    {
        return this.targetId;
    }

    public float progress()
    {
        return this.progress;
    }

    public ModelMorphData data()
    {
        return this.data;
    }

    public MorphGeometry geometry()
    {
        return this.geometry;
    }

    /**
     * @param nextModelId 该区间结束时实际切换到的模型 id，用于校验「形变目标 == 终点模型」
     */
    public void set(String sourceId, String targetId, float progress, ModelMorphData data, String nextModelId)
    {
        this.nextModelId = nextModelId;
        this.set(sourceId, targetId, progress, data);
    }

    public void set(String sourceId, String targetId, float progress, ModelMorphData data)
    {
        this.sourceId = sourceId == null ? "" : sourceId;
        this.targetId = targetId == null ? "" : targetId;
        this.progress = MorphMath.clamp01(progress);
        this.data = data;
    }

    /**
     * 清空本帧的瞬时状态；采样缓冲区与它对应的方案键保留，供下一帧复用。
     */
    public void clear()
    {
        this.sourceId = "";
        this.targetId = "";
        this.progress = 0F;
        this.data = null;
        this.nextModelId = "";
    }

    /**
     * 惰性解析形变方案：只有真正要渲染时才建立，关闭 Morph 时不产生任何开销。
     */
    public MorphGeometry ensureGeometry()
    {
        if (!this.active())
        {
            return null;
        }

        int hash = this.data.hashCode();

        /* 同一 (源, 目标, 配置) 复用已有缓冲区：形变期间每帧都走这条路径。 */
        if (this.geometry != null
            && this.geometryHash == hash
            && this.geometrySource.equals(this.sourceId)
            && this.geometryTarget.equals(this.targetId))
        {
            return this.geometry;
        }

        MorphPlan plan = MorphPlanCache.get(this.sourceId, this.targetId, this.data, this.nextModelId);

        this.geometry = null;
        this.geometrySource = this.sourceId;
        this.geometryTarget = this.targetId;
        this.geometryHash = hash;

        if (plan == null)
        {
            return null;
        }

        this.geometry = new MorphGeometry(plan);

        return this.geometry;
    }
}
