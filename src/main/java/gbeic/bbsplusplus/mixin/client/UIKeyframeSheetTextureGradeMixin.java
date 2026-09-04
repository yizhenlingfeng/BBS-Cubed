package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds readable labels and visual identity to generated texture-grade tracks. */
@Mixin(value = UIKeyframeSheet.class, remap = false)
public abstract class UIKeyframeSheetTextureGradeMixin
{
    @Inject(method = "<init>(Ljava/lang/String;Lmchorse/bbs_mod/l10n/keys/IKey;IZLmchorse/bbs_mod/utils/keyframes/KeyframeChannel;Lmchorse/bbs_mod/settings/values/base/BaseValueBasic;Z)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$labelTextureGrade(
        String id,
        mchorse.bbs_mod.l10n.keys.IKey title,
        int color,
        boolean separator,
        mchorse.bbs_mod.utils.keyframes.KeyframeChannel channel,
        mchorse.bbs_mod.settings.values.base.BaseValueBasic property,
        boolean isBoneTrack,
        CallbackInfo ci
    )
    {
        UIKeyframeSheet sheet = (UIKeyframeSheet) (Object) this;

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
