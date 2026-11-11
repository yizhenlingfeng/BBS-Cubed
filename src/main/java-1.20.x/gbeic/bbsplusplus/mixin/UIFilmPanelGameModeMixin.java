package gbeic.bbsplusplus.mixin;


import gbeic.bbsplusplus.settings.ModSettings;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import gbeic.bbsplusplus.util.FilmAutoGameModeRestoreState;
import gbeic.bbsplusplus.util.GameModeMessageSuppressor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — 相机界面进入时自动切换旁观模式，退出时恢复。
 * <p>
 * BBS 2.6 起 {@code appear/close} 上移到 {@link UIDashboardPanel}（final），
 * 因此注入父类并用 {@code instanceof UIFilmPanel} 限定只对影片面板生效。
 * </p>
 */
@Mixin(value = UIDashboardPanel.class, remap = false)
public abstract class UIFilmPanelGameModeMixin
{
    /** 进入相机前保存的游戏模式，退出时恢复 */
    @Unique
    private GameMode bbs_savedGameMode = null;

    @Inject(
        method = "appear",
        at = @At("HEAD"),
        remap = false,
        require = 1
    )
    private void onFilmPanelAppear(CallbackInfo ci)
    {
        if (!((Object) this instanceof UIFilmPanel panel))
        {
            return;
        }

        if (ModSettings.filmAutoGameMode != null
            && !ModSettings.filmAutoGameMode.get())
        {
            return; /* 设置关闭，不自动切换 */
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (panel.getContext() == null)
        {
            return; /* 面板懒加载构造时不是真正打开编辑器，不能切换模式 */
        }

        if (mc.player != null && mc.getNetworkHandler() != null)
        {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());

            if (entry != null)
            {
                GameMode current = entry.getGameMode();

                if (current != GameMode.SPECTATOR)
                {
                    this.bbs_savedGameMode = current;
                    FilmAutoGameModeRestoreState.markAutoSwitch(mc, current);
                    GameModeMessageSuppressor.suppressNext();
                    mc.player.networkHandler.sendCommand("gamemode spectator");
                }
            }
        }
    }

    @Inject(
        method = "close",
        at = @At("HEAD"),
        remap = false,
        require = 1
    )
    private void onFilmPanelClose(CallbackInfo ci)
    {
        if (!((Object) this instanceof UIFilmPanel))
        {
            return;
        }

        FilmAutoGameModeRestoreState.restoreFromFilmPanel(
            MinecraftClient.getInstance(),
            this.bbs_savedGameMode
        );

        this.bbs_savedGameMode = null;
    }
}
