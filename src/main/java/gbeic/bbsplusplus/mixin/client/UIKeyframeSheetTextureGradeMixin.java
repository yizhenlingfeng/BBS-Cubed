package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds readable labels and visual identity to generated texture-grade tracks.
 *
 * <p>挂在 {@link UIKeyframeSheet#applyStyle()} 而非构造器：BBSFS 2.6 重写了构造器体系，
 * 旧的 7 参构造器描述符已失效（{@code defaultRequire=1} 会让目标类直接加载失败）。
 * {@code applyStyle()} 对所有构造路径都会在末尾执行，且签名稳定。</p>
 */
@Mixin(value = UIKeyframeSheet.class, remap = false)
public abstract class UIKeyframeSheetTextureGradeMixin
{
    @Inject(method = "applyStyle", at = @At("RETURN"), remap = false)
    private void bbspp_cml$labelTextureGrade(CallbackInfo ci)
    {
        UIKeyframeSheet sheet = (UIKeyframeSheet) (Object) this;
        String id = sheet.id;

        if (id == null)
        {
            return;
        }

        if (id.equals("texture_tint") || id.endsWith("/texture_tint"))
        {
            sheet.title = L10n.lang("bbspp.ui.film.replays.texture_tint");
            sheet.color = Colors.MAGENTA;
            sheet.icon(Icons.BUCKET);
        }
        else if (id.equals("texture_whiten") || id.endsWith("/texture_whiten"))
        {
            sheet.title = L10n.lang("bbspp.ui.film.replays.texture_whiten");
            sheet.color = Colors.LIGHTEST_GRAY & Colors.RGB;
            sheet.icon(Icons.LIGHT);
        }
    }
}
