package gbeic.bbsplusplus.client.grade;

import net.minecraft.client.render.BuiltBuffer;

/**
 * 一次性决定表（幂等守卫）。
 *
 * <p>历史：1.21.11 移植的第一版给 {@code FormTranslucentQueue.submit} 与
 * {@code RenderLayer.draw} 各写了两份 mixin（开发描述符 + 手写混淆孪生）。后来证实 Loom 的
 * mixin 注解处理器在**没有 refmap** 的情况下也会重映射选择器，于是产物里两份同时命中，
 * 同一个 {@code BuiltBuffer} 会被接管两次——第二次会对已经 {@code close()} 的缓冲再关一次，
 * 并且重复投递绘制命令（同一帧画两遍）。孪生 mixin 已删；这张表是第二道保险：
 * 一次绘制只做一次决定，第二次进来直接复用上次的答案。</p>
 *
 * <p><b>为什么不用 {@code IdentityHashMap} 直接当静态字段</b>：模型每帧都会产生新的
 * {@code BuiltBuffer}，无界表会把每帧的缓冲都强引用住 → 内存只增不减（世界渲染几十帧就能
 * 攒出几千个死对象）。这里改成有界 FIFO：只记最近 {@value #LIMIT} 个，超出的从队头淘汰。</p>
 */
public final class GradeDecisions
{
    private static final int LIMIT = 512;

    private static final java.util.Map<BuiltBuffer, Boolean> MAP = new java.util.IdentityHashMap<>();
    private static final java.util.Deque<BuiltBuffer> ORDER = new java.util.ArrayDeque<>();

    private GradeDecisions()
    {
    }

    /**
     * @return 这张缓冲之前做过的决定；没做过返回 {@code null}
     */
    public static synchronized Boolean get(BuiltBuffer buffer)
    {
        return buffer == null ? null : MAP.get(buffer);
    }

    /**
     * 记住对这张缓冲的决定（true = 我们接管了绘制并负责关闭，false = 原绘制继续）。
     */
    public static synchronized void put(BuiltBuffer buffer, boolean decided)
    {
        if (buffer == null)
        {
            return;
        }

        if (MAP.put(buffer, decided) == null)
        {
            ORDER.addLast(buffer);

            while (ORDER.size() > LIMIT)
            {
                BuiltBuffer evicted = ORDER.pollFirst();

                if (evicted != null)
                {
                    MAP.remove(evicted);
                }
            }
        }
    }
}
