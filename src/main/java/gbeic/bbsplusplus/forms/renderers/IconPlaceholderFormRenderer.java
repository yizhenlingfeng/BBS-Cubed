package gbeic.bbsplusplus.forms.renderers;

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
 * <p>当真实渲染器无法注册时，提供静态图标和默认矩阵，使表单拾取器、
 * 编辑器和轨道相机仍有可用的渲染器。占位渲染器只在界面内绘制。</p>
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
