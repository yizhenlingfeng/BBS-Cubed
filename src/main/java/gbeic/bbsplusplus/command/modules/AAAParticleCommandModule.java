package gbeic.bbsplusplus.command.modules;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * AAA 粒子服务端指令模块。
 * <p>
 * 提供 {@code /bbsplusplus aaa_particle trigger <pos> <triggerIndex>}，用于触发模型方块上的
 * AAA 粒子表单触发器。模块始终注册自身入口；缺少 {@code aaa_particles} 前置时给出明确提示，
 * 而不是让根命令或模块命令消失。
 * </p>
 */
public class AAAParticleCommandModule implements BBSPlusPlusCommandModule
{
    @Override
    public LiteralArgumentBuilder<ServerCommandSource> create()
    {
        boolean available = FabricLoader.getInstance().isModLoaded("aaa_particles");

        LiteralArgumentBuilder<ServerCommandSource> trigger = CommandManager.literal("trigger")
            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                .then(CommandManager.argument("triggerIndex", IntegerArgumentType.integer(0, 3))
                    .executes(available
                        ? AAAParticleCommandModule::executeTrigger
                        : AAAParticleCommandModule::executeUnavailable)
                )
            );

        LiteralArgumentBuilder<ServerCommandSource> module = CommandManager.literal("aaa_particle")
            .then(trigger);

        if (!available)
        {
            module.executes(AAAParticleCommandModule::executeUnavailable);
            trigger.executes(AAAParticleCommandModule::executeUnavailable);
        }

        return module;
    }

    private static int executeTrigger(CommandContext<ServerCommandSource> context) throws CommandSyntaxException
    {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
        int triggerIndex = IntegerArgumentType.getInteger(context, "triggerIndex");

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ModelBlockEntity modelBe)
        {
            if (modelBe.getProperties().getForm() instanceof AAAParticleForm)
            {
                /* 本模组是客户端 addon，服务端命令树只存在于集成服务器（单人/局域网主机）。
                 * 直接在客户端线程把触发器脉冲写到客户端世界的同一 ModelBlockEntity 上。 */
                MinecraftClient client = MinecraftClient.getInstance();

                client.execute(() ->
                {
                    if (client.world == null)
                    {
                        return;
                    }

                    BlockEntity clientBe = client.world.getBlockEntity(pos);

                    if (clientBe instanceof ModelBlockEntity clientModel
                        && clientModel.getProperties().getForm() instanceof AAAParticleForm form)
                    {
                        form.manualTriggerPulse[triggerIndex] = true;
                    }
                });

                source.sendFeedback(() -> Text.literal("已请求本地客户端触发 AAA 粒子 (索引: " + triggerIndex + ")，坐标 " + pos.toShortString()), true);

                return 1;
            }

            source.sendError(Text.literal("目标模型方块挂载的不是 AAA 粒子表单"));
        }
        else
        {
            source.sendError(Text.literal("目标坐标没有模型方块 (ModelBlockEntity)"));
        }

        return 0;
    }

    private static int executeUnavailable(CommandContext<ServerCommandSource> context)
    {
        context.getSource().sendError(Text.literal("AAA 粒子指令不可用：未安装 aaa_particles 前置模组"));

        return 0;
    }

}
