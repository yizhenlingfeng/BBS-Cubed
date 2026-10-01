package bbslezy.mixin.client;

import bbslezy.utils.LezyOS;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UISliderTrackpad.class, remap = false)
public abstract class UISliderTrackpadMixin
{
    @Shadow
    protected boolean dragging;

    @Shadow
    protected int initialX;

    private long bbslezy$lastWarp;
    private boolean bbslezy$holdingCursor;

    @Inject(method = "render(Lmchorse/bbs_mod/ui/framework/UIContext;)V", at = @At("HEAD"))
    private void bbslezy$beforeRender(UIContext context, CallbackInfo ci)
    {
        this.bbslezy$holdingCursor = LezyOS.updateCursorHold(this, this.bbslezy$holdingCursor, this.dragging && Window.isMouseButtonPressed(0));
    }

    @Inject(method = "stopDragging()V", at = @At("TAIL"))
    private void bbslezy$onStopDragging(CallbackInfo ci)
    {
        this.bbslezy$holdingCursor = LezyOS.updateCursorHold(this, this.bbslezy$holdingCursor, false);
    }

    @Inject(method = "render(Lmchorse/bbs_mod/ui/framework/UIContext;)V", at = @At("TAIL"))
    private void bbslezy$warpCursorAtEdges(UIContext context, CallbackInfo ci)
    {
        if (!this.dragging || LezyOS.isWindows())
        {
            return;
        }

        try
        {
            long now = System.currentTimeMillis();

            if (now - this.bbslezy$lastWarp < 30L)
            {
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            int ww = mc.getWindow().getWidth();
            double factor = Math.ceil(ww / (double) context.menu.width);
            int mouseX = context.globalX(context.mouseX);
            int border = 5;
            int borderPadding = border + 1;
            int jump = context.menu.width - borderPadding * 2;

            if (mouseX <= border)
            {
                Window.moveCursor(ww - (int) (factor * borderPadding), (int) mc.mouse.getY());
                this.initialX += jump;
                this.bbslezy$lastWarp = now;
            }
            else if (mouseX >= context.menu.width - border)
            {
                Window.moveCursor((int) (factor * borderPadding), (int) mc.mouse.getY());
                this.initialX -= jump;
                this.bbslezy$lastWarp = now;
            }
        }
        catch (Throwable ignored)
        {}
    }
}
