package gbeic.bbsplusplus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gbeic.bbsplusplus.command.modules.AAAParticleCommandModule;
import gbeic.bbsplusplus.command.modules.BBSPlusPlusCommandModule;
import gbeic.bbsplusplus.command.modules.DebugCommandModule;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * BBS++ 服务端指令总入口。
 * <p>
 * 本类只负责注册 {@code /bbsplusplus} 根命令，并把各功能模块挂到根命令下。具体模块的参数、
 * 前置检测和执行逻辑放在 {@code command.modules} 包中，避免指令越加越多时根入口变成杂物间。
 * </p>
 */
public class BBSPlusPlusCommand
{
    private static final List<BBSPlusPlusCommandModule> MODULES = List.of(
        new DebugCommandModule(),
        new AAAParticleCommandModule()
    );

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment)
    {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("bbsplusplus")
            /* 客户端 addon 只会在集成服务器注册命令；限制为单人/局域网主机的玩家，
             * 避免在无本模组的远程服务器上暴露空指令。 */
            .requires(source -> source.getServer().isSingleplayer() && source.getEntity() instanceof ServerPlayerEntity);

        for (BBSPlusPlusCommandModule module : MODULES)
        {
            root.then(module.create());
        }

        dispatcher.register(root);
    }
}
