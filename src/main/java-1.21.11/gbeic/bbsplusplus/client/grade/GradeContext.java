package gbeic.bbsplusplus.client.grade;

/**
 * 当前正在渲染的模型实例的分级参数（调色 / 白化 / 附魔光效）。
 *
 * <p>1.20.x 的写法是「换 shader + 逐组设 uniform」，1.21.11 的绘制是「整个模型的顶点先
 * 塞进一个 {@code BufferBuilder}，再由 {@code FormTranslucentQueue} 统一提交」，所以参数
 * 必须在 {@code ModelInstance.render} 的调用栈上先算好、由提交点取用。
 * 用 {@link ThreadLocal} 而不是静态字段：面板预览的离屏 FBO 会在同一线程上递归调用
 * {@code render}，嵌套时靠 push/pop 保持正确。
 */
public final class GradeContext
{
    private static final ThreadLocal<GradeParams> CURRENT = ThreadLocal.withInitial(() -> GradeParams.NONE);

    private GradeContext()
    {
    }

    public static void push(GradeParams params)
    {
        CURRENT.set(params == null ? GradeParams.NONE : params);
    }

    public static void pop()
    {
        CURRENT.set(GradeParams.NONE);
    }

    public static GradeParams current()
    {
        return CURRENT.get();
    }
}
