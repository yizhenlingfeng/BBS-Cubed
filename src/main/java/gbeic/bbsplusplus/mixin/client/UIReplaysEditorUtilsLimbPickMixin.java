package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 肢体选择：按住按键时左键也用来新建关键帧。
 *
 * <p>BBS 在 {@link UIReplaysEditorUtils#pickFormWithOffers} 里把左键当「选中」、右键当「插入关键帧」
 * （{@code insert = context.mouseButton == 1}），所以肢体选择模式下左键点肢体只会选中。这里在按住
 * 按键时把左键也按插入处理；具体给哪根骨骼插帧仍由模板缓冲区决定：左键是实际肢体，右键是骨骼覆盖
 * 指向的骨骼。</p>
 */
@Mixin(value = UIReplaysEditorUtils.class, remap = false)
public class UIReplaysEditorUtilsLimbPickMixin
{
    @Inject(method = "pickFormWithOffers", at = @At("HEAD"), cancellable = true)
    private static void bbspp$limbPickInsertOnLeftClick(UIContext context, Pair<Form, String> pair, UIReplaysEditorUtils.FormPicker picker, CallbackInfoReturnable<Boolean> cir)
    {
        if (context.mouseButton != 0 || !LimbPickMode.held(context) || pair == null || pair.a == null)
        {
            return;
        }

        if (Window.isShiftPressed())
        {
            UIReplaysEditorUtils.offerHierarchy(context, pair.a, pair.b, bone -> picker.pick(pair.a, bone, true));
        }
        else
        {
            picker.pick(pair.a, pair.b, true);
        }

        cir.setReturnValue(true);
    }
}
