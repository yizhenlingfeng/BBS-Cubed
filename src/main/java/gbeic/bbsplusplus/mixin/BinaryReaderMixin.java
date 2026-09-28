package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.audio.BinaryReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.io.EOFException;
import java.io.InputStream;

/**
 * 修复 {@link BinaryReader#skip(InputStream, long)} 在 EOF 附近死循环的问题。
 * 原实现 skip 返回 0 时无法退出循环，读取带额外 RIFF chunk 的 WAV 会卡住。
 */
@Mixin(BinaryReader.class)
public abstract class BinaryReaderMixin
{
    /**
     * @author bbsplusplus
     * @reason 修复 EOF 时 skip 死循环
     */
    @Overwrite
    public void skip(InputStream stream, long bytes) throws Exception
    {
        while (bytes > 0)
        {
            long skipped = stream.skip(bytes);
            if (skipped <= 0) throw new EOFException();
            bytes -= skipped;
        }
    }
}
