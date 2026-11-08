package routu.bbsuirabbit.mixin;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.class_1041;
import net.minecraft.class_310;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import routu.bbsuirabbit.client.ui.home.UIFilmLibraryHome;

/**
 * An image dropped onto a film in the library home becomes its cover (instead of BBS only
 * complaining about FFmpeg).
 *
 * <p>The UI's mouse position is only refreshed when a frame renders, and while the files are
 * dragged in from Explorer the window gets no mouse moves. GLFW reports the drop point as a
 * cursor move right before the drop, so MC's mouse already sits there: use that.</p>
 */
@Mixin(value = UIScreen.class, remap = false)
public abstract class UIScreenFileDropMixin
{
    @Shadow(remap = false)
    private UIBaseMenu menu;

    @Inject(method = "acceptFilePaths", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$dropFiles(String[] paths, CallbackInfo ci)
    {
        if (this.menu == null)
        {
            return;
        }

        this.rabbit$syncDropPoint();

        if (UIFilmLibraryHome.acceptDroppedFiles(paths))
        {
            ci.cancel();
        }
    }

    @Unique
    private void rabbit$syncDropPoint()
    {
        class_310 mc = class_310.method_1551();
        class_1041 window = mc.method_22683();
        class_312 mouse = mc.field_1729;

        if (window.method_4480() <= 0 || window.method_4507() <= 0)
        {
            return;
        }

        int x = (int) (mouse.method_1603() * window.method_4486() / window.method_4480());
        int y = (int) (mouse.method_1604() * window.method_4502() / window.method_4507());

        this.menu.context.setMouse(x, y);
    }
}
