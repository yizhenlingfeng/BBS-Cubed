package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hides film disguise forms at their common renderer entry point. */
@Mixin(value = BaseFilmController.class, remap = false)
public abstract class BaseFilmControllerVisibilityMixin
{
    @Redirect(
        method = "renderEntity(Lmchorse/bbs_mod/film/FilmControllerContext;)V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/forms/FormUtilsClient;render(Lmchorse/bbs_mod/forms/forms/Form;Lmchorse/bbs_mod/forms/renderers/FormRenderingContext;)V"
        ),
        remap = false,
        require = 0
    )
    private static void bbspp_cml$renderDisguise(Form form, FormRenderingContext context)
    {
        if (FilmVisibilityController.shouldRenderFilmForm(form))
        {
            FormUtilsClient.render(form, context);
        }
    }
}
