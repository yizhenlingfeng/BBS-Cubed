package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * P2：缓存模型方块面板的 UIFormPalette，避免每次点击"编辑/选择"都重建整套表单编辑 UI。
 *
 * <p>原版每次点击 pickEdit 都 {@code UIFormPalette.open(...)}，内部 new UIFormPalette
 * 并随之 new UIFormList + new UIFormEditor + setupForms。本 Mixin 在 UIModelBlockPanel
 * 上缓存最近一次创建的 palette：只要它还挂在 UI 树上（未被 close），open 就直接返回缓存，
 * 后续 lambda 里的 setSelected / edit / renderer.setTarget 会自动刷新到当前选中的 modelBlock。</p>
 */
@Mixin(value = UIModelBlockPanel.class, remap = true)
public abstract class UIModelBlockPanelPaletteCacheMixin
{
    @Unique private UIFormPalette bbspp$cachedPalette;

    @Redirect(
        method = "lambda$new$9",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/forms/UIFormPalette;open(Lmchorse/bbs_mod/ui/framework/elements/UIElement;ZLmchorse/bbs_mod/forms/forms/Form;Ljava/util/function/Consumer;)Lmchorse/bbs_mod/ui/forms/UIFormPalette;"
        ),
        remap = true
    )
    private UIFormPalette bbspp$reusePalette(UIElement parent, boolean editing, Form form, Consumer<Form> callback)
    {
        // 缓存的 palette 还挂在 UI 树上 -> 直接复用，避免 new + setupForms
        if (bbspp$cachedPalette != null && bbspp$cachedPalette.getParent() != null)
        {
            try
            {
                bbspp$cachedPalette.callback = callback;
                bbspp$cachedPalette.setSelected(form);
                bbspp$cachedPalette.edit(editing);
                return bbspp$cachedPalette;
            }
            catch (Throwable t)
            {
                BBSPlusPlusMod.LOGGER.warn("[性能] 复用缓存 UIFormPalette 失败，回退到新建", t);
                bbspp$cachedPalette = null;
            }
        }

        UIFormPalette palette = UIFormPalette.open(parent, editing, form, callback);
        bbspp$cachedPalette = palette;
        return palette;
    }
}
