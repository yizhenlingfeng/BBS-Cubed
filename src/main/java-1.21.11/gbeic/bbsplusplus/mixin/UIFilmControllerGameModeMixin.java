package gbeic.bbsplusplus.mixin;


import gbeic.bbsplusplus.settings.ModSettings;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import gbeic.bbsplusplus.util.GameModeMessageSuppressor;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — H 键控制/释放演员时配合自动旁观模式维护正确的游戏模式。
 *
 * <p>1.21.11 适配（B6-05）：{@code ClientPlayNetworkHandler#sendCommand} 改名 {@code sendChatCommand}。
 * 原版 {@code UIFilmController#renderHUD} 在 1.21.11 已重写为
 * {@code renderHUD(UIContext, PreviewHud, Area)} 且不再读 {@code BBSSettings.editorLoop}，
 * 所以原来那条「隐藏重复循环图标」的 {@code @Redirect} 一并删除（1.21.11 上没有可拦的调用点）。</p>
 */
@Mixin(UIFilmController.class)
public abstract class UIFilmControllerGameModeMixin
{
    /** 记录 toggleControl() 调用前是否已在控制演员 */
    @Unique
    private boolean bbs_wasControllingBeforeToggle;

    @Shadow
    private mchorse.bbs_mod.forms.entities.IEntity controlled;

    @Inject(
        method = "toggleControl",
        at = @At("HEAD"),
        remap = true
    )
    private void onToggleControlHead(CallbackInfo ci)
    {
        this.bbs_wasControllingBeforeToggle = this.controlled != null;
    }

    @Inject(
        method = "toggleControl",
        at = @At("TAIL"),
        remap = true
    )
    private void onToggleControlTail(CallbackInfo ci)
    {
        if (ModSettings.filmAutoGameMode != null
            && !ModSettings.filmAutoGameMode.get())
        {
            return; /* 设置关闭，不做干预 */
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (!this.bbs_wasControllingBeforeToggle && this.controlled != null)
        {
            /* 刚控制了一个演员（从无到有） */

            if (mc.player != null)
            {
                /*
                 * 原版代码在 replacePlayer=false 时不会切换游戏模式，
                 * 需要手动切换到创造模式，防止演员继承旁观模式而穿墙。
                 */
                GameModeMessageSuppressor.suppressNext();
                mc.player.networkHandler.sendChatCommand("gamemode creative");
            }
        }
        else if (this.bbs_wasControllingBeforeToggle && this.controlled == null)
        {
            /* 刚释放了演员（从有到无） */
            /*
             * bbs-fs 在释放时不会切换游戏模式，玩家仍停留在创造模式，
             * 导致玩家身体停留在伪装位置可见。需要手动切回旁观模式，
             * 匹配 bbs-mod-nyk 的 PlayerUtils.gamemode(SPECTATOR) 行为。
             */
            if (mc.player != null)
            {
                GameModeMessageSuppressor.suppressNext();
                mc.player.networkHandler.sendChatCommand("gamemode spectator");
            }
        }
        /* else: 既非控制也非释放（如未选中回放条目），不做任何事 */
    }
}
