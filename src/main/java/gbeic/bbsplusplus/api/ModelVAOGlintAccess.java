package gbeic.bbsplusplus.api;

/**
 * 暴露 {@code ModelVAO} 的 POSITION_TEX 顶点布局（{@code vao2}）。
 *
 * <p>BBS 的主 VAO（{@code vao}）在 Iris 下带扩展属性，UV0 位于 attribute 2；
 * 而原版 glint 程序的顶点格式是 {@code POSITION_TEX}，UV0 位于 attribute 1。
 * {@code vao2} 恰好按 {@code POSITION_TEXTURE_LIGHT_COLOR} 上传（0=Position、1=UV0），
 * 因此附魔光效的第二遍绘制必须绕过 {@code ModelVAO#render} 直接绑定它。</p>
 *
 * <p>duck 入口：{@code ((ModelVAOGlintAccess) modelVAO).bbspp_cml$drawGlintVao()}。</p>
 */
public interface ModelVAOGlintAccess
{
    void bbspp_cml$drawGlintVao();
}
