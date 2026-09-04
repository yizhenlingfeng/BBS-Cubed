package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * 修复 BBS 原版 {@code getVideoFolder}:非空 export_path 若目录尚不存在会
 * 因 {@code isDirectory()==false} 静默回退 movies。这里在 HEAD 拦截:
 * 非空路径先 {@code mkdirs()},成功则直接返回该目录。
 */
@Mixin(value = BBSRendering.class, remap = false)
public abstract class BBSRenderingGetVideoFolderMixin
{
    @Inject(method = "getVideoFolder", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbspp_cml$resolveExportPath(CallbackInfoReturnable<File> cir)
    {
        if (BBSSettings.videoExportPath == null)
        {
            return;
        }

        String path = BBSSettings.videoExportPath.get();
        if (path == null || path.trim().isEmpty())
        {
            return;
        }

        File dir = new File(path.trim());
        dir.mkdirs();

        if (dir.isDirectory())
        {
            cir.setReturnValue(dir);
        }
    }
}
