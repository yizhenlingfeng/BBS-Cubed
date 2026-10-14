package gbeic.bbsplusplus.network;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class BBSPlusPlusNetwork
{
    public static final Identifier TRIGGER_PARTICLE = Identifier.of("bbsplusplus", "trigger_particle");

    /**
     * 服务端：广播触发器数据包到附近的玩家
     * TODO(1.21.11 port): migrate to CustomPayload + Id<T>
     */
    public static void broadcastTrigger(ServerWorld world, BlockPos pos, int triggerIndex)
    {
        // 1.21.11 Fabric API 要求 ServerPlayNetworking.send 接受 CustomPayload，
        // 旧的 Identifier + PacketByteBuf 方式已移除。暂时空实现。
    }
}
