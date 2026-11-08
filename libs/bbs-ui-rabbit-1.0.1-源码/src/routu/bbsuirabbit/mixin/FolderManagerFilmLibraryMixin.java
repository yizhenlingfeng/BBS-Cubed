package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.library.LibraryNetwork;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.utils.manager.FolderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Film library pictures and labels follow their film when BBS's film manager renames, moves or
 * deletes it — from any player and any interface (library home, data manager, BBS Together),
 * since this is where the files actually change.
 */
@Mixin(value = FolderManager.class, remap = false)
public abstract class FolderManagerFilmLibraryMixin
{
    @Inject(method = "rename", at = @At("RETURN"), remap = false)
    private void rabbit$renamed(String from, String to, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && (Object) this instanceof FilmManager manager)
        {
            LibraryNetwork.filmRenamed(manager.getFolder(), from, to);
        }
    }

    @Inject(method = "delete", at = @At("RETURN"), remap = false)
    private void rabbit$deleted(String name, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && (Object) this instanceof FilmManager manager)
        {
            LibraryNetwork.filmDeleted(manager.getFolder(), name);
        }
    }

    @Inject(method = "renameFolder", at = @At("RETURN"), remap = false)
    private void rabbit$folderRenamed(String from, String to, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && (Object) this instanceof FilmManager manager)
        {
            LibraryNetwork.filmFolderRenamed(manager.getFolder(), from, to);
        }
    }
}
