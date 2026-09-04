package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.UIClipsAccessor;
import gbeic.bbsplusplus.utils.AudioDropImporter;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.FFMpegUtils;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 接管音频文件的拖入:原版 acceptFilePaths 在渲染线程同步跑 ffmpeg(大文件
 * 卡顿数秒),这里 HEAD 拦截纯音频拖入改走 {@link AudioDropImporter} 后台
 * 转换;释放点落在剪辑时间线上时,转换完成后直接在对应 tick/层创建音频剪辑。
 *
 * <p>释放坐标用 GLFW 实时查询:Windows 文件拖放(WM_DROPFILES)期间窗口收
 * 不到鼠标移动消息,MC 缓存的鼠标位置还停在拖动开始前;释放瞬间查询光标
 * 即为释放点。同理,拖动悬停期间系统不向窗口派发任何事件,"未松手跟随
 * 鼠标"在 GLFW 下无法实现,以释放点精确定位代替。非音频文件不拦截,照走
 * 原版导入。</p>
 */
@Mixin(value = UIScreen.class, remap = false)
public abstract class UIScreenMixin
{
    @Shadow(remap = false)
    private UIBaseMenu menu;

    @Inject(method = "acceptFilePaths", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$importAudioAsync(String[] paths, CallbackInfo ci)
    {
        if (this.menu == null || !AudioDropImporter.isAllAudio(paths))
        {
            return;
        }

        /* 没有 ffmpeg 时放行原方法弹它的错误提示 */
        if (!FFMpegUtils.checkFFMPEG())
        {
            return;
        }

        List<File> files = new ArrayList<>();

        for (String path : paths)
        {
            File file = new File(path);

            if (file.exists())
            {
                files.add(file);
            }
        }

        if (files.isEmpty())
        {
            return;
        }

        /* 释放点 UI 坐标 */
        MinecraftClient client = MinecraftClient.getInstance();
        double[] cursorX = new double[1];
        double[] cursorY = new double[1];

        GLFW.glfwGetCursorPos(client.getWindow().getHandle(), cursorX, cursorY);

        double scale = client.getWindow().getScaleFactor();
        int mouseX = (int) (cursorX[0] / scale);
        int mouseY = (int) (cursorY[0] / scale);

        /* 命中检测:释放点落在哪个剪辑时间线上;只认工厂里注册了音频剪辑
         * 的时间线(相机轨道),动作时间线不收音频,视为未命中走普通导入 */
        UIClips timeline = null;

        for (UIClips clips : this.menu.getRoot().getChildren(UIClips.class))
        {
            if (clips.area.isInside(mouseX, mouseY)
                && ((UIClipsAccessor) clips).bbspp_cml$getFactory().create(mchorse.bbs_mod.resources.Link.bbs("audio")) != null)
            {
                timeline = clips;

                break;
            }
        }

        /* 目标目录:时间线/无 provider 时用音频目录;仅导入(非时间线)时保持原版
         * 的 provider 目录 + 打开文件夹行为 */
        File directory = BBSMod.getAudioFolder();
        boolean openFolder = timeline == null;

        if (timeline == null)
        {
            for (mchorse.bbs_mod.importers.IImportPathProvider provider : this.menu.getRoot().getChildren(mchorse.bbs_mod.importers.IImportPathProvider.class))
            {
                File provided = provider.getImporterPath();

                if (provided != null)
                {
                    directory = provided;
                    openFolder = false;

                    break;
                }
            }
        }

        AudioDropImporter.importAudio(this.menu.context, files, directory, openFolder, timeline, mouseX, mouseY);
        ci.cancel();
    }
}
