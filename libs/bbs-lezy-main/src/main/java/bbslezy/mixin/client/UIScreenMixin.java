package bbslezy.mixin.client;

import bbslod.LodSettings;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.utils.UIUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

@Mixin(value = UIScreen.class, remap = false)
public abstract class UIScreenMixin
{
    /**
     * ponytail: redirect openFolder on file drag-import to respect the
     * openFolderOnImport setting; skips opening File Explorer unless enabled.
     */
    @Redirect(
        method = "acceptFilePaths",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/utils/UIUtils;openFolder(Ljava/io/File;)Z"
        )
    )
    private boolean bbslezy$openFolderAfterImport(File folder)
    {
        if (LodSettings.openFolderOnImport.get())
        {
            return UIUtils.openFolder(folder);
        }

        return false;
    }
}
