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
        /* 客户端 addon 只应在集成服务器（单人 / 局域网主机）注册命令，避免在装了本模组
         * 但并非主机的专用服务器上暴露空指令。 */
        if (environment != CommandManager.RegistrationEnvironment.INTEGRATED)
        {
            return;
        }

        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("bbsplusplus")
            /* require 会被两类 source 求值：
             * 1) 真实玩家：server 非空，要求是玩家实体；
             * 2) 1.21.11 序列化命令树时 CommandManager$1 用 createSource(...) 造的假 source
             *    （server=null、entity=null），求值结果只决定节点是否被标记 restricted。
             *    这里必须对它返回 true，否则 /bbsplusplus 会在客户端被标成不可用；
             *    也绝不能直接解引用 getServer()，那会让整棵树构建失败、
             *    玩家进入世界时被踢成「无效的玩家数据」。 */
            .requires(source -> source.getServer() == null
                || source.getEntity() instanceof ServerPlayerEntity);

        for (BBSPlusPlusCommandModule module : MODULES)
        {
            root.then(module.create());
        }

        dispatcher.register(root);
    }
}
