package gbeic.bbsplusplus.util;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.BBSMod;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 模型面剔除配置管理器。
 * <p>
 * 当「自动关闭面剔除」开关开启时，扫描 BBS 模型目录，
 * 为所有没有 config.json 的模型文件夹自动生成包含 {@code "culling": false} 的配置文件。
 * </p>
 * <p>
 * 已有 config.json 的模型完全不受影响，不会被覆盖或修改。
 * </p>
 */
public class ModelCullingConfigManager
{
    /** 上一次检测到的开关状态，用于检测从关闭→打开的边沿触发 */
    private static boolean lastEnabledState = false;

    /** 防止同一帧内重复执行的标记 */
    private static boolean appliedThisSession = false;

    /** 模型文件扩展名列表，用于识别模型文件夹 */
    private static final String[] MODEL_EXTENSIONS = {
        ".geo.json", ".bbmodel", ".bobj", ".obj", ".vox"
    };

    /**
     * 每帧调用：检测开关状态变化，在从关闭→打开时执行一次配置生成。
     * <p>
     * 设计为边沿触发而非电平触发：只有用户主动打开开关的瞬间才执行扫描，
     * 避免游戏运行中反复扫描文件系统。
     * </p>
     */
    public static void tick()
    {
        boolean current = isEnabled();

        if (current && !lastEnabledState)
        {
            // 开关从关闭→打开，立即执行一次
            applyCullingConfigs();
        }

        lastEnabledState = current;
    }

    /**
     * 客户端启动完成后调用：如果开关已处于开启状态，执行一次配置生成。
     * <p>
     * 覆盖用户在配置文件中手动开启、或上次游戏开启后未关闭的场景。
     * </p>
     */
    public static void onClientStarted()
    {
        lastEnabledState = isEnabled();

        if (isEnabled() && !appliedThisSession)
        {
            applyCullingConfigs();
        }
    }

    /**
     * 资源重载（F3+T）后调用：如果开关开启，重新扫描并补全配置。
     */
    public static void onResourceReload()
    {
        if (isEnabled())
        {
            applyCullingConfigs();
        }
    }

    /**
     * 执行配置生成：扫描模型目录，为没有 config.json 的模型生成配置文件。
     *
     * @return 新生成的 config.json 数量
     */
    public static int applyCullingConfigs()
    {
        File modelsRoot = getModelsRoot();

        if (modelsRoot == null || !modelsRoot.exists() || !modelsRoot.isDirectory())
        {
            BBSPlusPlusMod.LOGGER.warn("[面剔除] 模型目录不存在，跳过配置生成: {}",
                modelsRoot == null ? "null" : modelsRoot.getAbsolutePath());
            return 0;
        }

        List<File> modelFolders = findModelFolders(modelsRoot);
        int generated = 0;

        for (File folder : modelFolders)
        {
            File configFile = new File(folder, "config.json");

            if (configFile.exists())
            {
                // 已有配置文件，跳过
                continue;
            }

            if (writeCullingConfig(configFile))
            {
                generated++;
                BBSPlusPlusMod.LOGGER.info("[面剔除] 已为模型生成配置: {}", folder.getAbsolutePath());
            }
        }

        appliedThisSession = true;

        if (generated > 0)
        {
            BBSPlusPlusMod.LOGGER.info("[面剔除] 共为 {} 个模型生成了关闭面剔除的 config.json", generated);
        }

        return generated;
    }

    /**
     * 获取 BBS 模型根目录：{@code config/bbs/assets/models/}
     */
    private static File getModelsRoot()
    {
        try
        {
            File assetsFolder = BBSMod.getAssetsFolder();
            return new File(assetsFolder, "models");
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.error("[面剔除] 获取模型目录失败", e);
            return null;
        }
    }

    /**
     * 递归扫描模型根目录，找出所有包含模型文件的文件夹。
     * <p>
     * 模型文件夹的判定标准：目录中存在至少一个以模型扩展名结尾的文件。
     * </p>
     */
    private static List<File> findModelFolders(File root)
    {
        List<File> result = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root.toPath()))
        {
            paths.filter(Files::isDirectory)
                .filter(ModelCullingConfigManager::containsModelFile)
                .forEach(path -> result.add(path.toFile()));
        }
        catch (IOException e)
        {
            BBSPlusPlusMod.LOGGER.error("[面剔除] 扫描模型目录失败", e);
        }

        return result;
    }

    /**
     * 判断目录中是否包含模型文件。
     */
    private static boolean containsModelFile(Path dir)
    {
        File[] files = dir.toFile().listFiles();

        if (files == null)
        {
            return false;
        }

        for (File file : files)
        {
            if (!file.isFile())
            {
                continue;
            }

            String name = file.getName().toLowerCase();

            for (String ext : MODEL_EXTENSIONS)
            {
                if (name.endsWith(ext))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 写入最小化的 config.json，仅包含关闭面剔除的设置。
     */
    private static boolean writeCullingConfig(File configFile)
    {
        try (FileWriter writer = new FileWriter(configFile))
        {
            writer.write("{\n");
            writer.write("    \"culling\": false\n");
            writer.write("}\n");
            return true;
        }
        catch (IOException e)
        {
            BBSPlusPlusMod.LOGGER.error("[面剔除] 写入配置文件失败: {}", configFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * 检查开关是否开启。空值安全：设置项未初始化时视为关闭。
     */
    private static boolean isEnabled()
    {
        return BBSAddonsSettings.autoDisableFaceCulling != null
            && BBSAddonsSettings.autoDisableFaceCulling.get();
    }
}
