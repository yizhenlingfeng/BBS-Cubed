package bbslezy.mixin.client;

import mchorse.bbs_mod.graphics.window.Window;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Set;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;

@Mixin(value = Window.class, remap = false)
public abstract class WindowMixin
{
    @Shadow
    @Final
    private static Set<Object> cursorHolders;

    private static volatile boolean bbslezy$mouseXFieldResolved;
    private static Field bbslezy$mouseXField;
    private static Field bbslezy$mouseYField;

    /**
     * On Wayland, {@code glfwSetCursorPos} only updates GLFW's virtual cursor
     * coordinates when the cursor mode is {@code GLFW_CURSOR_DISABLED}, and does
     * not fire the cursor-pos callback synchronously. Sync {@code Mouse.x/y} so
     * the next UI frame immediately sees the wrapped position.
     */
    @Inject(method = "moveCursor(II)V", at = @At("TAIL"))
    private static void bbslezy$afterMoveCursor(int x, int y, CallbackInfo ci)
    {
        try
        {
            long handle = Window.getWindow();

            if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED)
            {
                syncMinecraftMouse(x, y);
            }
        }
        catch (Throwable ignored)
        {}
    }


    @Inject(method = "setStandardCursor(I)V", at = @At("HEAD"))
    private static void bbslezy$pruneStaleTrackpadHolders(int shape, CallbackInfo ci)
    {
        if (cursorHolders == null || cursorHolders.isEmpty())
        {
            return;
        }

        try
        {
            if (!Window.isMouseButtonPressed(0))
            {
                boolean removed = cursorHolders.removeIf((h) -> h instanceof UINumericInput<?>);

                if (removed && cursorHolders.isEmpty() && MinecraftClient.getInstance().currentScreen != null)
                {
                    long window = Window.getWindow();

                    if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL)
                    {
                        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                    }
                }
            }
        }
        catch (Throwable ignored)
        {}
    }
    private static void syncMinecraftMouse(int x, int y)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.mouse == null)
        {
            return;
        }

        if (!bbslezy$mouseXFieldResolved)
        {
            synchronized (WindowMixin.class)
            {
                if (!bbslezy$mouseXFieldResolved)
                {
                    try
                    {
                        for (Field f : mc.mouse.getClass().getDeclaredFields())
                        {
                            if (f.getType() == double.class)
                            {
                                f.setAccessible(true);

                                if (bbslezy$mouseXField == null)
                                {
                                    bbslezy$mouseXField = f;
                                }
                                else if (bbslezy$mouseYField == null)
                                {
                                    bbslezy$mouseYField = f;
                                    break;
                                }
                            }
                        }
                    }
                    catch (Throwable ignored)
                    {}

                    bbslezy$mouseXFieldResolved = true;
                }
            }
        }

        try
        {
            if (bbslezy$mouseXField != null && bbslezy$mouseYField != null)
            {
                bbslezy$mouseXField.setDouble(mc.mouse, x);
                bbslezy$mouseYField.setDouble(mc.mouse, y);
            }
        }
        catch (Throwable ignored)
        {}
    }
}
