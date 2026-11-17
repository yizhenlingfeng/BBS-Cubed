package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * Model Morph 的求值入口。
 *
 * <p>不累计逐帧状态：任意时间点都从「当前关键帧 + 下一关键帧 + 映射配置 + 插值进度」直接求出结果，
 * 因此拖动播放头、倒放、循环和跳转都自然正确。</p>
 */
public final class ModelMorphRuntime
{
    /**
     * 当前登记为「可能处于形变区间」的表单。
     *
     * <p>用弱引用集合避免持有已销毁的表单；只在关键帧求值阶段写入，
     * 因此 {@code isVAORendered()} 的判定不依赖共享的 {@code ModelInstance.form} 字段。</p>
     */
    private static final java.util.Set<ModelForm> ACTIVE_FORMS =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private ModelMorphRuntime()
    {
    }

    /**
     * 由 BBS 原生 Model 属性轨道调用：判断本段是否开启 Morph 并写入表单状态。
     */
    public static void update(ModelForm form, KeyframeSegment<?> segment)
    {
        if (form == null)
        {
            return;
        }

        ModelMorphState state = state(form);

        if (state == null)
        {
            return;
        }

        state.clear();

        if (segment == null || segment.a == segment.b || segment.a == null || segment.b == null)
        {
            return;
        }

        if (!(segment.a instanceof gbeic.bbsplusplus.keyframes.IModelMorphKeyframe keyframe))
        {
            return;
        }

        ModelMorphData data = keyframe.bbsppp$getModelMorph();

        if (data == null || !data.enabled() || data.groups().isEmpty())
        {
            return;
        }

        Object from = segment.a.getValue();
        Object to = segment.b.getValue();

        if (!(from instanceof String sourceId) || !(to instanceof String targetId))
        {
            return;
        }

        if (sourceId.isEmpty() || targetId.isEmpty())
        {
            return;
        }

        /* 不因配置里记录的模型名过期就停止形变：映射以 Cube 的稳定 key 与几何指纹为准，
           模型 id 漂移只影响提示信息。实际使用的 id 以当前关键帧求值为准。 */

        state.set(sourceId, targetId, interpolationProgress(segment), data, targetId);
        ACTIVE_FORMS.add(form);
    }

    /**
     * 与 {@code KeyframeSegment.createInterpolated()} 完全一致的进度：先做运动偏移重映射，
     * 再应用关键帧自身的插值曲线（线性 / 缓入缓出 / 贝塞尔）。
     */
    private static float interpolationProgress(KeyframeSegment<?> segment)
    {
        float raw = segment.getInterpolationProgress();
        Keyframe<?> keyframe = segment.a;
        IKeyframeFactory<?> factory = keyframe.getFactory();

        if (factory != null && factory.isStepped())
        {
            return MorphMath.clamp01(raw);
        }

        return MorphMath.clamp01(keyframe.getInterpolation().interpolate(0F, 1F, raw));
    }

    /**
     * 每帧属性应用前清空所有实例的形变状态，避免播放头跳转、轨道删除后残留旧状态。
     */
    public static void clearAll(mchorse.bbs_mod.forms.forms.Form root)
    {
        if (root == null)
        {
            return;
        }

        ModelMorphState state = state(root instanceof ModelForm form ? form : null);

        if (state != null)
        {
            state.clear();
        }

        for (mchorse.bbs_mod.forms.forms.BodyPart part : root.parts.getAllTyped())
        {
            if (part != null && part.getForm() != null)
            {
                clearAll(part.getForm());
            }
        }
    }

    /**
     * 当前模型实例是否处于形变渲染区间。
     *
     * <p>供 {@code isVAORendered()} 这类在渲染上下文建立<b>之前</b>被调用的判定使用，
     * 保证着色器选择与几何提交走同一条路径。</p>
     *
     * <p><b>不能依赖 {@code instance.form}</b>：{@code ModelInstance} 是按模型 id 共享的资源，
     * 而 {@code form} 是它上面的 public 可变字段，被 BBS 渲染器每帧写入。UI 预览与回放共用
     * 同一实例时会互相覆盖这个字段——鼠标在界面上移动触发预览重绘，就会把回放表单换成
     * 预览表单，于是本方法判定翻转、着色器随之切换，表现为「鼠标一动光照就变」。</p>
     *
     * <p>优先使用 {@link MorphRenderContext#currentForm()} 标识本次绘制的表单；不在
     * ModelFormRenderer 作用域内时才回退到活动表单集合。这样同一模型资源的其他表单不会
     * 因为其中一个表单形变而被错误切到 CPU 着色器路径。</p>
     */
    public static boolean isMorphing(ModelInstance instance)
    {
        String id = instance.id;

        if (id == null || id.isEmpty())
        {
            return false;
        }

        if (MorphRenderContext.hasCurrentForm())
        {
            return isMorphing(id, MorphRenderContext.currentForm());
        }

        for (ModelForm form : ACTIVE_FORMS)
        {
            if (isMorphing(id, form))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isMorphing(String modelId, ModelForm form)
    {
        if (form == null)
        {
            return false;
        }

        ModelMorphState state = state(form);

        /* 与几何接管保持完全一致的判定：端点交给 BBS 原版渲染。 */
        return state != null && state.morphing()
            && modelId.equals(form.model.get()) && modelId.equals(state.sourceId());
    }

    public static ModelMorphState state(ModelForm form)
    {
        return form instanceof IMorphForm holder ? holder.bbsppp$getModelMorphState() : null;
    }

    /**
     * 把形变状态挂在模型表单上（由 Mixin 实现）。状态归属于表单实例，
     * 因此多个回放实例共用同一份模型资源也不会互相影响。
     */
    public interface IMorphForm
    {
        ModelMorphState bbsppp$getModelMorphState();
    }

    /**
     * 渲染入口：判断当前模型实例是否应该走形变渲染，并在需要时压入渲染上下文。
     *
     * @return 本次压入的帧令牌；{@code null} 表示保持 BBS 原版渲染路径。
     *         调用方必须在结束处把令牌交回 {@link #endRender(ModelMorphRuntime.RenderScope)}。
     */
    public static RenderScope beginRender(ModelInstance instance, boolean picking)
    {
        /* 模板拾取通道必须用真实几何：BBS 靠逐骨骼写入拾取索引来做肢体高亮/选择。
           形变几何与真实几何面数/骨骼归属都不同，混进拾取通道会让索引错乱，
           表现为「鼠标一动光照/高亮就变」。拾取退回原版几何即可，它本来也不该显示。 */
        if (picking)
        {
            return null;
        }

        if (!(instance.form instanceof ModelForm form) || !(instance.model instanceof Model))
        {
            return null;
        }

        ModelMorphState state = state(form);

        if (state == null || !state.active())
        {
            return null;
        }

        String current = form.model.get();

        if (current == null || !current.equals(state.sourceId()))
        {
            return null;
        }

        MorphGeometry geometry = state.ensureGeometry();

        if (geometry == null || geometry.plan().isEmpty())
        {
            return null;
        }

        /* 焊接模型的几何由焊接缓存单独烘焙，逐帧替换顶点会与缓存不一致，第一版直接回退。 */
        if (!instance.getWeldBindings().isEmpty())
        {
            return null;
        }

        geometry.sample(state.progress());

        MorphRenderContext.Frame frame = MorphRenderContext.push(instance, geometry,
            state.sourceId(), state.targetId(), state.progress(), state.data().textureSync());

        return new RenderScope(frame);
    }

    public static void endRender(RenderScope scope)
    {
        if (scope != null)
        {
            MorphRenderContext.pop(scope.frame());
        }
    }

    /**
     * 一次形变渲染的作用域令牌，保证压栈/出栈严格配对。
     */
    public record RenderScope(MorphRenderContext.Frame frame)
    {
    }

    public static ModelInstance model(String id)
    {
        return id == null || id.isEmpty() ? null : BBSModClient.getModels().getModel(id);
    }
}
