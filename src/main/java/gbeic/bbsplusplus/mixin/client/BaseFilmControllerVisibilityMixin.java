package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hides film disguise forms at their common renderer entry point.
 *
 * <p>2.6 渲染链变更：BaseFilmController.renderEntity(WorldRenderContext, Replay, IEntity)
 * 不再直接渲染形态，而是委托给 {@link FilmEntityRenderer#renderEntity}。
 * FormUtilsClient.render 的实际调用点在 FilmEntityRenderer 内部，故 Mixin 目标改到这里。</p>
 */
@Mixin(value = FilmEntityRenderer.class, remap = false)
public abstract class BaseFilmControllerVisibilityMixin
{
    @Redirect(
        method = "renderEntity(Lmchorse/bbs_mod/film/FilmControllerContext;)V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/forms/FormUtilsClient;render(Lmchorse/bbs_mod/forms/forms/Form;Lmchorse/bbs_mod/forms/renderers/FormRenderingContext;)V"
        ),
        remap = false,
        require = 1
    )
    private static void bbspp_cml$renderDisguise(Form form, FormRenderingContext context)
    {
        if (FilmVisibilityController.shouldRenderFilmForm(form))
        {
            FormUtilsClient.render(form, context);
        }
    }
}
