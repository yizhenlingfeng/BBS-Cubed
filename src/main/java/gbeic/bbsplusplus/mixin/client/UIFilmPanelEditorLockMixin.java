package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.ui.film.lock.FilmEditorLockManager;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Installs the per-film editor lock manager on {@link UIFilmPanel} and wires the
 * merged timeline rail into the film editor.
 *
 * <p>Locked modes are only removed from the {@code ·} cycle and dimmed in the
 * action bar / vertical timeline rail; they can still be selected like any other
 * editor. The POV addon adds its third editor at the end of the panel's constructor,
 * so the manager keeps resolving it from the client tick.</p>
 */
@Mixin(value = UIFilmPanel.class, remap = false, priority = 900)
public abstract class UIFilmPanelEditorLockMixin
{
    @Unique
    private FilmEditorLockManager bbspp$editorLock;

    @Unique
    private boolean bbspp$editorLockFailed;

    @Unique
    private FilmEditorLockManager bbspp$editorLock()
    {
        if (this.bbspp$editorLock == null && !this.bbspp$editorLockFailed)
        {
            try
            {
                this.bbspp$editorLock = new FilmEditorLockManager((UIFilmPanel) (Object) this);
                this.bbspp$editorLock.install();
            }
            catch (Throwable t)
            {
                this.bbspp$editorLockFailed = true;
                this.bbspp$editorLock = null;

                BBSPlusPlusMod.LOGGER.error("[EditorLock] 时间轴锁定初始化失败", t);
            }
        }

        return this.bbspp$editorLock;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp$initEditorLock(CallbackInfo ci)
    {
        this.bbspp$editorLock();
    }

    @Inject(method = "fill(Lmchorse/bbs_mod/film/Film;)V", at = @At("TAIL"), remap = false)
    private void bbspp$afterFill(Film film, CallbackInfo ci)
    {
        FilmEditorLockManager manager = this.bbspp$editorLock;

        if (manager != null)
        {
            manager.onFilmFilled();
        }
    }

    @Inject(
        method = "showPanel(Lmchorse/bbs_mod/ui/framework/elements/UIElement;)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp$afterShowPanel(UIElement element, CallbackInfo ci)
    {
        FilmEditorLockManager manager = this.bbspp$editorLock;

        if (manager != null)
        {
            manager.onPanelShown();
        }
    }

    @Inject(method = "updateMainEditorVisibility", at = @At("TAIL"), remap = false)
    private void bbspp$afterVisibility(boolean hasFilm, CallbackInfo ci)
    {
        FilmEditorLockManager manager = this.bbspp$editorLock;

        if (manager != null)
        {
            manager.onVisibilityUpdated(hasFilm);
        }
    }
}
