package gbeic.bbsplusplus.cubic.animation;

/**
 * 标记"当前正在应用 actions 附加层(actions_overlay)",让 cubic/BOBJ 动画器把每一层
 * 的骨骼变换按 {@code current += delta * weight} 相加叠加(并用 composeOrient 组合旋转
 * 四元数),而不是 vanilla 的 {@code current = delta + initial} 覆盖写入 —— 从而让多个
 * 附加层像 transform/pose 附加层那样彼此以及相对基础动作叠加,而非越靠下越覆盖。
 *
 * <p>只在客户端渲染线程上、由 {@code ModelFormRendererMixin} 在附加层应用循环前后成对
 * 开关;用深度计数支持嵌套,{@code isActive()} 供 {@code CubicModelAnimatorMixin} /
 * {@code BOBJModelAnimatorMixin} 判定是否走相加路径。</p>
 */
public final class AdditiveLayerContext
{
    private static int depth;

    private AdditiveLayerContext()
    {
    }

    public static void begin()
    {
        depth++;
    }

    public static void end()
    {
        if (depth > 0)
        {
            depth--;
        }
    }

    public static boolean isActive()
    {
        return depth > 0;
    }
}
