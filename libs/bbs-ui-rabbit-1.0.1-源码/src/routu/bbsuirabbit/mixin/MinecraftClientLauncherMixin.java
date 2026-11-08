package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.launcher.Launcher;
import net.minecraft.class_310;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vanilla's title screen, world list and server list open as the BBS-Rabbit launcher. */
@Mixin(class_310.class)
public abstract class MinecraftClientLauncherMixin
{
    @ModifyVariable(method = "method_1507", at = @At("HEAD"), argsOnly = true)
    private class_437 rabbit$openLauncher(class_437 screen)
    {
        return Launcher.wrap(screen);
    }
}
