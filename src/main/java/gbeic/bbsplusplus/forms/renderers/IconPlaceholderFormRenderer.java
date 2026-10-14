package gbeic.bbsplusplus.forms.renderers;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 表单拾取器里的图标占位渲染器。
 *
 * <p>某些表单的真实渲染器在 1.21.11 构建里被 source-set excludes 排除（它们用旧
 * 立即模式渲染 API），运行时 {@code FormUtilsClient.getRenderer()} 便返回
 * {@code null}。BBS 的表单格子和编辑器都假定渲染器存在——格子通过
 * {@code FormUtilsClient.renderPreview()} 取图标，没有渲染器时整格留白；编辑器在
 * {@code UIForm.getOrigin()} 里直接对返回值调 {@code collectMatrices()}，于是每次
 * 打开面板都抛 NullPointerException。本类作为这两条路径的兜底：画出静态图标，
 * 并让基类默认的 {@code collectMatrices()} 提供一个空前缀矩阵条目。</p>
 *
 * <p>只画图标不画 3D——真正的显示效果仍由 1.20.1 构建里的完整渲染器负责；这里要
 * 解决的只是"格子空白 + 编辑器崩溃"。</p>
 */
public class IconPlaceholderFormRenderer<T extends Form> extends FormRenderer<T>
{
    private final Link icon;

    public IconPlaceholderFormRenderer(T form, Link icon)
    {
        super(form);

        this.icon = icon;
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(this.icon);

        if (texture != null && texture.width > 0 && texture.height > 0)
        {
            float textureAspect = (float) texture.width / texture.height;
            float cellAspect = (float) (x2 - x1) / Math.max(1, y2 - y1);

            /* 长边贴满格子、短边居中，避免图标被拉伸变形。 */
            float w = textureAspect > cellAspect ? (x2 - x1) : ((y2 - y1) * textureAspect);
            float h = textureAspect > cellAspect ? ((x2 - x1) / textureAspect) : (y2 - y1);

            context.batcher.fullTexturedBox(texture, x1 + ((x2 - x1) - w) / 2F, y1 + ((y2 - y1) - h) / 2F, w, h);
        }
        else
        {
            context.batcher.box(x1 + 2, y1 + 2, x2 - 2, y2 - 2, Colors.A25 | Colors.WHITE);
        }
    }
}
