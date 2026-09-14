package gbeic.bbsplusplus.utils;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.ModelForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Locale;

/**
 * 用外部 Blockbench 打开当前选中模型的工具。
 *
 * <p>模型来源只认用户自己放进 assets/models/ 的模型：其目录在磁盘上真实存在。
 * BBS 内置模型（来自 jar / 资源包）在磁盘上没有目录，直接判定为「不可编辑」。</p>
 *
 * <p>打开规则：</p>
 * <ul>
 *   <li>目录下有 {@code .bbmodel} 工程文件，且设置里开启了「优先用 .bbmodel」→ 打开 .bbmodel</li>
 *   <li>否则打开目录下第一个 {@code .geo.json}</li>
 *   <li>目录下没有 .geo.json（如用户加的是 .bobj/.obj）→ 视为不支持，右键项置灰</li>
 * </ul>
 */
public final class BlockbenchLauncher
{
    private static final Logger LOGGER = LoggerFactory.getLogger("bbspp-blockbench");

    private BlockbenchLauncher()
    {}

    /** 当前配置的 Blockbench.exe 是否可用（路径非空且指向真实文件）。 */
    public static boolean isExeValid()
    {
        String path = BBSAddonsSettings.blockbenchPath == null ? "" : BBSAddonsSettings.blockbenchPath.get();

        return path != null && !path.trim().isEmpty() && new File(path.trim()).isFile();
    }

    /**
     * 该模型是否为用户模型（目录在磁盘上真实存在）。
     * 内置模型在 jar/资源包内，磁盘上没有对应目录，返回 false。
     */
    public static boolean isUserModel(String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return false;
        }

        return getModelFolder(modelId).isDirectory();
    }

    /** 返回模型在磁盘上的目录（可能不存在，调用方需自行判断）。 */
    private static File getModelFolder(String modelId)
    {
        return new File(BBSMod.getAssetsPath("models"), modelId);
    }

    /**
     * 解析应当用 Blockbench 打开的文件。
     *
     * @return 目标文件；若目录下没有 .geo.json（或目录不存在）返回 null
     */
    public static File resolveTargetFile(String modelId)
    {
        File folder = getModelFolder(modelId);

        if (!folder.isDirectory())
        {
            return null;
        }

        boolean preferBbmodel = BBSAddonsSettings.blockbenchPreferBbmodel != null
            && BBSAddonsSettings.blockbenchPreferBbmodel.get();

        if (preferBbmodel)
        {
            File bbmodel = findBySuffix(folder, ".bbmodel");

            if (bbmodel != null)
            {
                return bbmodel;
            }
        }

        return findBySuffix(folder, ".geo.json");
    }

    private static File findBySuffix(File folder, String suffix)
    {
        String lower = suffix.toLowerCase(Locale.ROOT);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(lower));

        if (files == null || files.length == 0)
        {
            return null;
        }

        return files[0];
    }

    /**
     * 用配置的 Blockbench.exe 打开给定模型。
     *
     * @return 启动成功返回 true；路径无效、找不到文件或进程启动失败返回 false
     */
    public static boolean openModel(ModelForm form)
    {
        if (form == null || form.model == null)
        {
            return false;
        }

        String modelId = form.model.get();

        if (!isExeValid())
        {
            LOGGER.warn("[blockbench] exe 路径无效: {}", BBSAddonsSettings.blockbenchPath == null ? "(null)" : BBSAddonsSettings.blockbenchPath.get());
            return false;
        }

        File target = resolveTargetFile(modelId);

        if (target == null)
        {
            LOGGER.warn("[blockbench] 模型目录下没有可打开的 .geo.json/.bbmodel: {}", modelId);
            return false;
        }

        String exe = BBSAddonsSettings.blockbenchPath.get().trim();

        try
        {
            new ProcessBuilder(exe, target.getAbsolutePath()).start();
            LOGGER.info("[blockbench] 已启动 Blockbench 打开: {}", target.getAbsolutePath());
            return true;
        }
        catch (Exception e)
        {
            LOGGER.error("[blockbench] 启动 Blockbench 失败: {}", exe, e);
            return false;
        }
    }
}
