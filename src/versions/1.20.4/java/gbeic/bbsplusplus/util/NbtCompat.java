package gbeic.bbsplusplus.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT 压缩读写跨版本兼容层（1.20.4）。
 * <p>
 * Minecraft 1.20.2 起 {@link NbtIo} 移除了 File 与无 NbtSizeTracker 的读写重载，改为
 * Path + NbtSizeTracker；本类把 1.20.4 的 API 统一成与共享源码一致的调用形态。
 * 版本实现位于 src/versions/&lt;mc&gt;/java。
 * </p>
 */
public final class NbtCompat
{
    private NbtCompat()
    {}

    public static NbtCompound readCompressed(InputStream in) throws IOException
    {
        return NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
    }

    public static NbtCompound readCompressed(File file) throws IOException
    {
        return NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
    }

    public static void writeCompressed(NbtCompound nbt, File file) throws IOException
    {
        NbtIo.writeCompressed(nbt, file.toPath());
    }
}
