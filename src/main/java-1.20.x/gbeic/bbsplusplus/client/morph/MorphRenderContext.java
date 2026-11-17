package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.forms.forms.ModelForm;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 渲染阶段的形变上下文。
 *
 * <p>BBS 的 {@code CubicCubeRenderer.renderCube} 只拿到骨骼与方块，不携带表单实例信息，
 * 因此这里用线程内的栈把「本次渲染使用的形变采样结果」传递下去。</p>
 *
 * <p>用栈而不是单槽，是为了容纳嵌套渲染；每个帧带实例身份，嵌套渲染时内层模型的
 * Cube 不会误用外层的形变结果。压栈返回帧对象作为令牌，出栈时只有令牌仍是栈顶才弹出，
 * 因此即使某次渲染中途抛异常，也不会把后续渲染的上下文错位。</p>
 *
 * <p><b>为什么不用 ModelInstance 的实例字段保存状态</b>：{@code ModelInstance} 是按模型 id
 * 共享的资源，UI 预览与回放可能复用同一个对象。把「当前是否在形变 / 要恢复哪张贴图」
 * 存在实例字段上会被交叉污染，表现为「鼠标移动触发 UI 预览重绘后，回放里的光照跟着变」。
 * 因此这里把作用域与贴图绑定都放在线程内，按调用配对。</p>
 */
public final class MorphRenderContext
{
    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<Binding>> BINDINGS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<RenderInvocation>> INVOCATIONS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<FormInvocation>> FORM_INVOCATIONS = ThreadLocal.withInitial(ArrayDeque::new);

    private MorphRenderContext()
    {
    }

    /**
     * @return 本次压入的帧令牌；调用方必须原样交给 {@link #pop(Frame)}
     */
    public static Frame push(ModelInstance instance, MorphGeometry geometry, String sourceId, String targetId,
                             float progress, boolean textureSync)
    {
        Frame frame = new Frame(instance, geometry, sourceId, targetId, progress, sourceId, textureSync);

        STACK.get().push(frame);

        return frame;
    }

    /**
     * 只弹出仍位于栈顶的令牌，避免异常路径造成的错位弹出。
     */
    public static void pop(Frame frame)
    {
        Deque<Frame> stack = STACK.get();

        if (frame != null && stack.peek() == frame)
        {
            stack.pop();
        }

        if (stack.isEmpty())
        {
            STACK.remove();
        }
    }

    public static Frame current()
    {
        Deque<RenderInvocation> invocations = INVOCATIONS.get();

        if (!invocations.isEmpty())
        {
            ModelMorphRuntime.RenderScope scope = invocations.peek().scope();

            /* 普通模型的嵌套渲染必须屏蔽外层形变上下文，不能误替换它的 Cube。 */
            return scope == null ? null : scope.frame();
        }

        return STACK.get().peek();
    }

    /**
     * 为每次 ModelInstance.render 调用压入标记；即使该调用不形变也要压入 null 标记，
     * 以便嵌套普通模型暂时屏蔽外层的形变帧。
     */
    public static void beginInvocation(ModelMorphRuntime.RenderScope scope)
    {
        INVOCATIONS.get().push(new RenderInvocation(scope));
    }

    /**
     * 弹出与当前 render 调用匹配的标记。null 表示该调用没有形变作用域。
     */
    public static ModelMorphRuntime.RenderScope endInvocation()
    {
        Deque<RenderInvocation> invocations = INVOCATIONS.get();

        if (invocations.isEmpty())
        {
            INVOCATIONS.remove();

            return null;
        }

        ModelMorphRuntime.RenderScope scope = invocations.pop().scope();

        if (invocations.isEmpty())
        {
            INVOCATIONS.remove();
        }

        return scope;
    }

    /**
     * 声明当前 BBS ModelFormRenderer 正在绘制的表单。
     *
     * <p>BBS 选择 VAO 着色器时，尚未把当前表单写入共享的 {@code ModelInstance.form}；
     * 该作用域让 {@code isVAORendered()} 能按本次绘制的表单判定，而不是按同 ID 的任意表单判定。</p>
     */
    public static void beginForm(ModelForm form)
    {
        FORM_INVOCATIONS.get().push(new FormInvocation(form));
    }

    public static void endForm()
    {
        Deque<FormInvocation> forms = FORM_INVOCATIONS.get();

        if (!forms.isEmpty())
        {
            forms.pop();
        }

        if (forms.isEmpty())
        {
            FORM_INVOCATIONS.remove();
        }
    }

    public static boolean hasCurrentForm()
    {
        return !FORM_INVOCATIONS.get().isEmpty();
    }

    public static ModelForm currentForm()
    {
        FormInvocation invocation = FORM_INVOCATIONS.get().peek();

        return invocation == null ? null : invocation.form();
    }

    /**
     * 记录「进入本次渲染前绑定的贴图」，供出栈时恢复。
     *
     * @param restore 需要恢复的贴图；{@code null} 表示本次无需恢复
     */
    public static void attachBinding(Texture restore)
    {
        BINDINGS.get().push(new Binding(restore));
    }

    /**
     * @return 需要恢复的贴图；{@code null} 表示无需恢复
     */
    public static Texture takeBinding()
    {
        Deque<Binding> bindings = BINDINGS.get();

        if (bindings.isEmpty())
        {
            return null;
        }

        Texture restore = bindings.pop().restore();

        if (bindings.isEmpty())
        {
            BINDINGS.remove();
        }

        return restore;
    }

    public record Frame(ModelInstance instance, MorphGeometry geometry, String sourceId, String targetId,
                        float progress, String modelId, boolean textureSync)
    {
    }

    private record RenderInvocation(ModelMorphRuntime.RenderScope scope)
    {
    }

    private record FormInvocation(ModelForm form)
    {
    }

    private record Binding(Texture restore)
    {
    }
}
