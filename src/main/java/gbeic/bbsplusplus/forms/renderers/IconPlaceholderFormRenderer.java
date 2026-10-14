package gbeic.bbsplusplus.forms.renderers;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 表单拾取器与编辑器里的图标占位渲染器。
 *
 * <p>物品喷射、流体伪装等表单的真实渲染器用旧立即模式渲染 API，在 1.21.11 构建里被
 * source-set excludes 整份排除，运行时 {@code FormUtilsClient.getRenderer()} 便返回
 * {@code null}。BBS 多处直接假定它有值：表单格子经 {@code renderPreview()} 取图，
 * 渲染器缺失时整格空白；编辑器 {@code UIForm.getOrigin()} 直接对返回值调
 * {@code collectMatrices()}，于是预览轮次每帧抛 NullPointerException（表现为
 * "图标空白 + 点开表单没反应"）。本类为这些路径兜底：画出静态图标，并沿用基类的
 * {@code collectMatrices()} 默认实现（产出空前缀矩阵条目）保住编辑器与轨道相机的坐标链路。</p>
 *
 * <p>只画图标不画 3D：世界内的真实显示仍由完整渲染器负责，占位渲染器仅让 UI 恢复可用，
 * 场景内该表单静默不显示。</p>
 */
public class IconPlaceholderFormRenderer<T extends Form> extends FormRenderer<T>
{
    private final Link texture;
    private final Icon icon;

    public IconPlaceholderFormRenderer(T form, Link texture, Icon icon)
    {
        super(form);

        this.texture = texture;
        this.icon = icon;
    }

    /**
     * 真实渲染器类不存在（被 1.21.11 构建排除）时，注册图标占位渲染器。
     *
     * <p>类存在时什么都不做——调用方仍按原样注册真实渲染器；1.20.1/1.20.4 上因此只是
     * 一次无副作用的存在性探测。</p>
     */
    public static <T extends Form> void registerIfRendererMissing(Class<T> formClass, String rendererClassName, Link texture)
    {
        if (rendererMissing(rendererClassName))
        {
            FormUtilsClient.register(formClass, form -> new IconPlaceholderFormRenderer<>(form, texture, null));

            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.warn(
                "[BBS++] {} 的真实渲染器 {} 不可用，已改用图标占位渲染器", formClass.getSimpleName(), rendererClassName);
        }
    }

    /** 同 {@link #registerIfRendererMissing(Class, String, Link)}，但用 BBS 图标图集里的图标。 */
    public static <T extends Form> void registerIfRendererMissing(Class<T> formClass, String rendererClassName, Icon icon)
    {
        if (rendererMissing(rendererClassName))
        {
            FormUtilsClient.register(formClass, form -> new IconPlaceholderFormRenderer<>(form, null, icon));

            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.warn(
                "[BBS++] {} 的真实渲染器 {} 不可用，已改用图标占位渲染器", formClass.getSimpleName(), rendererClassName);
        }
    }

    private static boolean rendererMissing(String rendererClassName)
    {
        try
        {
            /* initialize=false：只探测存在性，不触发真实渲染器的静态初始化。 */
            Class.forName(rendererClassName, false, IconPlaceholderFormRenderer.class.getClassLoader());

            return false;
        }
        catch (Throwable e)
        {
            /* 类不存在，或因依赖被排除而无法链接——两种情况都当作"不可用"处理。 */
            return true;
        }
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int w = x2 - x1;
        int h = y2 - y1;

        if (this.texture != null)
        {
            Texture texture = context.render.getTextures().getTexture(this.texture);

            if (texture != null && texture.width > 0 && texture.height > 0)
            {
                /* 等比适配：长边贴满格子、短边居中，避免缩略图被拉伸。 */
                float aspect = (float) texture.width / texture.height;
                float boxAspect = (float) w / Math.max(1, h);
                float dw = aspect > boxAspect ? w : h * aspect;
                float dh = aspect > boxAspect ? w / aspect : h;

                context.batcher.fullTexturedBox(texture, x1 + (w - dw) / 2F, y1 + (h - dh) / 2F, dw, dh);

                return;
            }
        }
        else if (this.icon != null)
        {
            float size = Math.min(w, h) * 0.75F;

            context.batcher.scaledIcon(this.icon, Colors.WHITE, x1 + (w - size) / 2F, y1 + (h - size) / 2F, size);

            return;
        }

        /* 纹理缺失时留一层半透明底，至少说明这一格是有效表单而非空白。 */
        context.batcher.box(x1 + 2, y1 + 2, x2 - 2, y2 - 2, Colors.mulA(Colors.WHITE, 0.25F));
    }
}
