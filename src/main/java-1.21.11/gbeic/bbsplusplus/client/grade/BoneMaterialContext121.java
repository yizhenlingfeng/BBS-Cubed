package gbeic.bbsplusplus.client.grade;

import mchorse.bbs_mod.resources.Link;

/**
 * 当前正在绘制的那一个材质通道的「材质名 + 材质链接」。
 *
 * <p>1.21.11 的 {@code ModelInstance#render} 会为每个材质各跑一遍：
 * 先 {@code textureResolver.apply(material)} 绑定该材质的贴图，再遍历整个模型、最后提交一次。
 * 逐骨骼的贴图覆盖（B2-03）与逐骨骼 PBR（B2-02）都要求「一个组的贴图可以不同于该材质的贴图」，
 * 而 {@code FormPbr#resolveAlbedo} 需要材质的 {@link Link} 才能产出变体 ——
 * 这个 {@code Link} 只存在于 {@code ModelFormRenderer#albedo(String, Link)} 的局部变量里，
 * 所以在那里把它记下来，供同一次材质通道内的逐组提交查询。
 * </p>
 *
 * <p>用 {@link ThreadLocal}：面板预览的离屏 FBO 会在同一线程递归渲染。</p>
 */
public final class BoneMaterialContext121
{
    private record State(String material, Link link)
    {
    }

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private BoneMaterialContext121()
    {
    }

    public static void set(String material, Link link)
    {
        CURRENT.set(new State(material, link));
    }

    public static void clear()
    {
        CURRENT.remove();
    }

    /** 当前材质名；整模型单通道时为 {@code null}。 */
    public static String material()
    {
        State state = CURRENT.get();

        return state == null ? null : state.material();
    }

    /** 当前材质链接；没解析出贴图时为 {@code null}。 */
    public static Link link()
    {
        State state = CURRENT.get();

        return state == null ? null : state.link();
    }
}
