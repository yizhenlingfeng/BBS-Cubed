package routu.bbsuirabbit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import routu.bbsuirabbit.ui.morphing.MorphCellNames;
import routu.bbsuirabbit.ui.morphing.MorphCellState;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.FormCellRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Inside the BBS++ morph palette, a hovered particle cell plays its effect live instead
 * of the static sprite, an unnamed particle (BBS or vanilla) names itself by its effect /
 * particle type instead of the raw id, and the per-kind name switches ({@link MorphCellNames})
 * decide whether a cell has a name strip at all.
 */
@Mixin(value = FormCellRenderer.class, remap = false)
public abstract class FormCellRendererMorphMixin
{
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/forms/FormUtilsClient;renderPreview(Lmchorse/bbs_mod/forms/forms/Form;Lmchorse/bbs_mod/ui/framework/UIContext;IIII)V"), remap = false)
    private static void rabbit$livePreview(Form form, UIContext context, int x1, int y1, int x2, int y2, Operation<Void> original)
    {
        boolean live = MorphCellState.currentForm == form && MorphCellState.livePreview(form, context, x1, y1, x2 - x1, y2 - y1);

        /* An empty emitter (burst re-arming, not yet fired) falls back to the sprite */
        if (live && MorphCellState.hasParticles())
        {
            return;
        }

        original.call(form, context, x1, y1, x2, y2);
    }

    @WrapOperation(method = {"render", "showsWholeName"}, at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/forms/forms/Form;getDisplayName()Ljava/lang/String;"), remap = false)
    private static String rabbit$particleDisplayName(Form form, Operation<String> original)
    {
        if (MorphCellState.currentForm == form)
        {
            String name = MorphCellNames.particleName(form);

            if (name != null)
            {
                return name;
            }
        }

        return original.call(form);
    }

    /* A kind of form whose names are switched off gets no name strip in the palette,
     * and its picture takes the strip's room. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/forms/FormCellRenderer;hasName(I)Z"), remap = false)
    private static boolean rabbit$nameStrip(int w, Operation<Boolean> original)
    {
        Form form = MorphCellState.currentForm;

        if (form != null && !MorphCellNames.shows(form))
        {
            return false;
        }

        return original.call(w);
    }
}
