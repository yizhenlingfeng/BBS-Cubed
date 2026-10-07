package gbeic.bbsplusplus.client.ui.animations;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;

import java.util.function.Supplier;

/** 动画列表悬停预览：鼠标停在动画条目上时弹出正方形预览栏，循环播放对应动画，仅预览不交互。 */
public class UIAnimationPreviewTooltip implements ITooltip
{
    public static final int SIZE = 120;

    private final Supplier<ModelForm> formSupplier;
    private final UIAnimationPreview preview = new UIAnimationPreview();

    private String animation;
    private int anchorX;
    private int anchorY;

    public UIAnimationPreviewTooltip(Supplier<ModelForm> formSupplier)
    {
        this.formSupplier = formSupplier;
    }

    /** 列表渲染期调用：记录悬停动画与行的视觉右下角锚点。 */
    public void prepare(String animation, int anchorScreenX, int anchorScreenY)
    {
        this.animation = animation;
        this.anchorX = anchorScreenX;
        this.anchorY = anchorScreenY;
    }

    public void clear()
    {
        this.animation = null;
    }

    @Override
    public IKey getLabel()
    {
        return IKey.EMPTY;
    }

    @Override
    public void renderTooltip(UIContext context)
    {
        if (this.animation == null)
        {
            return;
        }

        ModelForm form = this.formSupplier.get();

        if (form == null)
        {
            return;
        }

        int w = SIZE;
        int h = SIZE;
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int margin = 4;
        int gap = 4;

        /* 预览栏顶部对齐行底部，右边缘贴 list 右边，向下延伸。 */
        int x = this.anchorX - w - gap;
        int y = this.anchorY;

        x = Math.max(0, x);
        if (y + h + margin > screenH)
        {
            y = screenH - h - margin;
        }
        y = Math.max(0, y);

        int color = BBSSettings.primaryColor.get();
        context.batcher.dropShadow(x, y, x + w, y + h, 6, Colors.A25 + color, color);
        new Area(x, y, w, h).render(context.batcher, BBSSettings.raisedSurface());

        this.preview.setAnimation(form, this.animation);
        this.preview.area.set(x + 1, y + 1, w - 2, h - 2);
        this.preview.render(context);
    }
}
