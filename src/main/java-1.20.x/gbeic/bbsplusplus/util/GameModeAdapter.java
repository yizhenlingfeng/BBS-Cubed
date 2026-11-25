package gbeic.bbsplusplus.util;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.world.GameMode;

/**
 * 游戏模式 API 的版本适配器。
 *
 * <p>1.20.x 里模式的字符串标识叫 {@code getName()}，反查用 {@code byName(...)}，
 * 发送聊天命令用 {@code sendCommand(...)}。</p>
 */
final class GameModeAdapter
{
    private GameModeAdapter() {}

    static String idOf(GameMode gameMode)
    {
        return gameMode == null ? null : gameMode.getName();
    }

    static GameMode byId(String id, GameMode fallback)
    {
        return GameMode.byName(id, fallback);
    }

    static void sendCommand(ClientPlayNetworkHandler handler, String command)
    {
        handler.sendCommand(command);
    }
}
