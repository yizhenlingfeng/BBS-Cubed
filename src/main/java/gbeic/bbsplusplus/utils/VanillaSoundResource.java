package gbeic.bbsplusplus.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Sound;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 Minecraft 1.20+ / Fabric 真实资源管线读取原版音效。
 *
 * <p>第一性原理：
 * <ol>
 *   <li>{@code sounds.json} 里的 name 是「逻辑 id」(如 {@code entity/cow/ambient})，
 *       不是磁盘路径；</li>
 *   <li>官方把逻辑 id 变成资源 id 的方式是 {@link Sound#FINDER}
 *       ({@code ResourceFinder("sounds", ".ogg")}) →
 *       {@code namespace:sounds/&lt;path&gt;.ogg}；</li>
 *   <li>原版音效本体在启动器的 hashed asset store
 *       ({@code assets/objects/&lt;aa&gt;/&lt;hash&gt;})，由 asset index 映射
 *       {@code minecraft/sounds/...ogg}。部分启动器布局下，
 *       {@code ResourceManager.getResource} 可能拿不到流，必须走 index 回退；</li>
 *   <li>BBS 直接 {@code new Identifier("minecraft","sounds/"+raw)} 且不处理命名空间/点号，
 *       在 Fabric 1.20 资源链上会静默失败，表现为下载/点赞/试听全无效。</li>
 * </ol>
 */
public final class VanillaSoundResource
{
    public static final String LOCALIZED_NAME_SEPARATOR = " | ";

    private static final Logger LOGGER = LoggerFactory.getLogger("bbspp/vanilla_sound");
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("(.*)_(\\d+)$");

    /** asset index 键 → objects 文件 */
    private static final Map<String, File> INDEX_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean indexLoaded = false;

    private VanillaSoundResource()
    {}

    /* ------------------------------------------------------------------ */
    /*  显示名 / 逻辑 path 规范化                                           */
    /* ------------------------------------------------------------------ */

    public static String removeCategoryPrefix(String displayName)
    {
        if (displayName == null)
        {
            return "";
        }

        String baseName = displayName;
        int endBracket = displayName.indexOf(']');

        if (endBracket > 0 && displayName.startsWith("["))
        {
            int colonSpace = displayName.indexOf("]: ", endBracket);

            if (colonSpace > 0)
            {
                baseName = displayName.substring(colonSpace + 3);
            }
        }
        else if (displayName.startsWith("Music: ") || displayName.startsWith("Sound: "))
        {
            baseName = displayName.substring(7);
        }

        int localizedName = baseName.indexOf(LOCALIZED_NAME_SEPARATOR);

        return localizedName < 0
            ? baseName
            : baseName.substring(localizedName + LOCALIZED_NAME_SEPARATOR.length());
    }

    public static String addLocalizedName(String displayName, String localizedName)
    {
        if (displayName == null || localizedName == null || localizedName.isBlank())
        {
            return displayName;
        }

        int categoryEnd = displayName.startsWith("[") ? displayName.indexOf("]: ") : -1;

        if (categoryEnd > 0)
        {
            int nameStart = categoryEnd + 3;

            return displayName.substring(0, nameStart)
                + localizedName
                + LOCALIZED_NAME_SEPARATOR
                + displayName.substring(nameStart);
        }

        if (displayName.startsWith("Music: ") || displayName.startsWith("Sound: "))
        {
            return displayName.substring(0, 7)
                + localizedName
                + LOCALIZED_NAME_SEPARATOR
                + displayName.substring(7);
        }

        return localizedName + LOCALIZED_NAME_SEPARATOR + displayName;
    }

    /**
     * 把 sounds.json 的 name 规范成逻辑 path(无 .ogg、无 sounds/ 前缀)。
     * 返回 {@code namespace + '\0' + path} 便于拆分；失败返回 null。
     */
    public static String[] normalizeLogicalSound(String raw)
    {
        if (raw == null)
        {
            return null;
        }

        String s = raw.trim().replace('\\', '/');

        if (s.isEmpty())
        {
            return null;
        }

        if (s.toLowerCase(Locale.ROOT).endsWith(".ogg"))
        {
            s = s.substring(0, s.length() - 4);
        }

        String namespace = "minecraft";
        String path = s;

        int colon = s.indexOf(':');
        if (colon >= 0)
        {
            if (colon == 0 || colon == s.length() - 1)
            {
                return null;
            }
            namespace = s.substring(0, colon);
            path = s.substring(colon + 1);
        }

        if (path.startsWith("sounds/"))
        {
            path = path.substring("sounds/".length());
        }

        /* 极少数旧数据用点号，资源路径必须是斜杠 */
        if (path.indexOf('/') < 0 && path.indexOf('.') >= 0)
        {
            path = path.replace('.', '/');
        }

        if (path.isEmpty() || path.indexOf(' ') >= 0)
        {
            return null;
        }

        return new String[] { namespace, path };
    }

    /**
     * 官方等价路径：{@code Sound.FINDER.toResourcePath(id)}。
     * 例：entity/cow/ambient → minecraft:sounds/entity/cow/ambient.ogg
     */
    public static Identifier toResourceLocation(String rawSoundName)
    {
        String[] parts = normalizeLogicalSound(rawSoundName);

        if (parts == null)
        {
            return null;
        }

        try
        {
            Identifier logical = new Identifier(parts[0], parts[1]);
            return Sound.FINDER.toResourcePath(logical);
        }
        catch (Exception e)
        {
            LOGGER.warn("[vanilla_sound] bad logical id '{}': {}", rawSoundName, e.toString());
            return null;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  打开输入流：ResourceManager → findResources → asset index          */
    /* ------------------------------------------------------------------ */

    public static Optional<InputStream> openSoundStream(String rawSoundName)
    {
        Identifier location = toResourceLocation(rawSoundName);

        if (location == null)
        {
            LOGGER.warn("[vanilla_sound] cannot map logical name: {}", rawSoundName);
            return Optional.empty();
        }

        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.getResourceManager() != null)
        {
            ResourceManager rm = client.getResourceManager();

            /* 1) 直接 getResource —— 与 SoundManager.isSoundResourcePresent 同源 */
            try
            {
                Optional<Resource> direct = rm.getResource(location);

                if (direct.isPresent())
                {
                    return Optional.of(direct.get().getInputStream());
                }
            }
            catch (Exception e)
            {
                LOGGER.debug("[vanilla_sound] getResource failed for {}: {}", location, e.toString());
            }

            /* 2) findResources 扫描 sounds/ 再精确匹配(覆盖某些 pack 合并边界) */
            try
            {
                Map<Identifier, Resource> found = Sound.FINDER.findResources(rm);
                Resource hit = found.get(location);

                if (hit != null)
                {
                    return Optional.of(hit.getInputStream());
                }

                /* 宽松：只比 path */
                for (Map.Entry<Identifier, Resource> e : found.entrySet())
                {
                    if (e.getKey().getPath().equals(location.getPath())
                        && e.getKey().getNamespace().equals(location.getNamespace()))
                    {
                        return Optional.of(e.getValue().getInputStream());
                    }
                }
            }
            catch (Exception e)
            {
                LOGGER.debug("[vanilla_sound] findResources failed: {}", e.toString());
            }
        }

        /* 3) 启动器 asset index + objects 哈希文件 —— Fabric/多实例下的可靠回退 */
        File objectFile = resolveFromAssetIndex(location);

        if (objectFile != null && objectFile.isFile())
        {
            try
            {
                return Optional.of(new FileInputStream(objectFile));
            }
            catch (Exception e)
            {
                LOGGER.warn("[vanilla_sound] open object file failed {}: {}", objectFile, e.toString());
            }
        }

        LOGGER.warn("[vanilla_sound] unresolved sound stream for raw='{}' location={}", rawSoundName, location);
        return Optional.empty();
    }

    /* ------------------------------------------------------------------ */
    /*  落盘 / 缓存 / 查已下载                                              */
    /* ------------------------------------------------------------------ */

    public static File getAudioDir()
    {
        File audioDir = BBSMod.getAudioFolder();

        if (audioDir == null)
        {
            File game = FabricLoader.getInstance().getGameDir().toFile();
            audioDir = new File(game, "config/bbs/assets/audio");
        }

        if (!audioDir.exists() && !audioDir.mkdirs())
        {
            LOGGER.error("[vanilla_sound] cannot create audio dir {}", audioDir);
        }

        return audioDir;
    }

    public static String copyToAudioFolder(String preferredName, String rawSoundName)
    {
        File audioDir = getAudioDir();
        Optional<InputStream> stream = openSoundStream(rawSoundName);

        if (stream.isEmpty())
        {
            return null;
        }

        String safeName = sanitizeFileName(preferredName == null || preferredName.isEmpty()
            ? logicalBaseName(rawSoundName) : preferredName);
        String finalName = generateUniqueName(safeName, audioDir);
        File target = new File(audioDir, finalName + ".ogg");

        try (InputStream in = stream.get())
        {
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (!target.isFile() || target.length() == 0)
            {
                LOGGER.warn("[vanilla_sound] copied empty file for {}", rawSoundName);
                return null;
            }

            LOGGER.info("[vanilla_sound] downloaded {} -> {}", rawSoundName, target.getAbsolutePath());
            return finalName;
        }
        catch (Exception e)
        {
            LOGGER.error("[vanilla_sound] copy to audio folder failed for {}", rawSoundName, e);
            return null;
        }
    }

    public static File copyToCacheFile(String rawSoundName, File cacheFile)
    {
        if (cacheFile == null)
        {
            return null;
        }

        Optional<InputStream> stream = openSoundStream(rawSoundName);

        if (stream.isEmpty())
        {
            return null;
        }

        try (InputStream in = stream.get())
        {
            File parent = cacheFile.getParentFile();

            if (parent != null && !parent.exists())
            {
                parent.mkdirs();
            }

            Files.copy(in, cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return cacheFile.isFile() && cacheFile.length() > 0 ? cacheFile : null;
        }
        catch (Exception e)
        {
            LOGGER.error("[vanilla_sound] copy to cache failed for {}", rawSoundName, e);
            return null;
        }
    }

    public static String findDownloadedPath(String displayOrBaseName)
    {
        File audioDir = getAudioDir();

        if (audioDir == null || !audioDir.isDirectory())
        {
            return null;
        }

        String originalName = removeCategoryPrefix(displayOrBaseName);

        if (originalName.endsWith(".ogg"))
        {
            File exact = new File(audioDir, originalName);
            return exact.isFile() ? "assets:audio/" + originalName : null;
        }

        File exact = new File(audioDir, originalName + ".ogg");
        return exact.isFile() ? "assets:audio/" + originalName + ".ogg" : null;
    }

    /* ------------------------------------------------------------------ */
    /*  asset index 回退                                                    */
    /* ------------------------------------------------------------------ */

    private static File resolveFromAssetIndex(Identifier resourceLocation)
    {
        ensureIndexLoaded();

        /* resourceLocation path = sounds/entity/cow/ambient.ogg
         * index key             = minecraft/sounds/entity/cow/ambient.ogg */
        String key = resourceLocation.getNamespace() + "/" + resourceLocation.getPath();
        File file = INDEX_CACHE.get(key);

        if (file != null && file.isFile())
        {
            return file;
        }

        return null;
    }

    private static void ensureIndexLoaded()
    {
        if (indexLoaded)
        {
            return;
        }

        synchronized (VanillaSoundResource.class)
        {
            if (indexLoaded)
            {
                return;
            }

            try
            {
                loadAssetIndexes();
            }
            catch (Exception e)
            {
                LOGGER.error("[vanilla_sound] failed loading asset indexes", e);
            }
            finally
            {
                indexLoaded = true;
                LOGGER.info("[vanilla_sound] asset index entries for sounds: {}", INDEX_CACHE.size());
            }
        }
    }

    private static void loadAssetIndexes()
    {
        for (File assetsRoot : candidateAssetsRoots())
        {
            File indexesDir = new File(assetsRoot, "indexes");
            File objectsDir = new File(assetsRoot, "objects");

            if (!indexesDir.isDirectory() || !objectsDir.isDirectory())
            {
                continue;
            }

            File[] indexes = indexesDir.listFiles((dir, name) -> name.endsWith(".json"));

            if (indexes == null)
            {
                continue;
            }

            for (File indexFile : indexes)
            {
                try
                {
                    String text = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(text).getAsJsonObject();

                    if (!root.has("objects") || !root.get("objects").isJsonObject())
                    {
                        continue;
                    }

                    JsonObject objects = root.getAsJsonObject("objects");

                    for (String key : objects.keySet())
                    {
                        if (!key.contains("/sounds/") || !key.endsWith(".ogg"))
                        {
                            continue;
                        }

                        if (INDEX_CACHE.containsKey(key))
                        {
                            continue;
                        }

                        JsonObject meta = objects.getAsJsonObject(key);
                        String hash = meta.get("hash").getAsString();

                        if (hash == null || hash.length() < 4)
                        {
                            continue;
                        }

                        File objectFile = new File(objectsDir, hash.substring(0, 2) + "/" + hash);

                        if (objectFile.isFile())
                        {
                            INDEX_CACHE.put(key, objectFile);
                        }
                    }
                }
                catch (Exception e)
                {
                    LOGGER.debug("[vanilla_sound] skip index {}: {}", indexFile.getName(), e.toString());
                }
            }
        }
    }

    private static File[] candidateAssetsRoots()
    {
        java.util.LinkedHashSet<File> roots = new java.util.LinkedHashSet<>();

        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.runDirectory != null)
        {
            roots.add(new File(client.runDirectory, "assets"));
            /* 某些启动器把 assets 放在实例父级共享目录 */
            File parent = client.runDirectory.getParentFile();
            if (parent != null)
            {
                roots.add(new File(parent, "assets"));
            }
        }

        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        roots.add(new File(gameDir, "assets"));

        if (gameDir.getParentFile() != null)
        {
            roots.add(new File(gameDir.getParentFile(), "assets"));
        }

        /* 官方启动器默认位置(Windows) */
        String appdata = System.getenv("APPDATA");
        if (appdata != null)
        {
            roots.add(new File(appdata, ".minecraft/assets"));
        }

        String home = System.getProperty("user.home");
        if (home != null)
        {
            roots.add(new File(home, ".minecraft/assets"));
            roots.add(new File(home, "AppData/Roaming/.minecraft/assets"));
        }

        return roots.toArray(new File[0]);
    }

    /* ------------------------------------------------------------------ */
    /*  文件名工具                                                          */
    /* ------------------------------------------------------------------ */

    private static String logicalBaseName(String raw)
    {
        String[] parts = normalizeLogicalSound(raw);

        if (parts == null)
        {
            return "sound";
        }

        return parts[1].replace('/', '_');
    }

    private static String sanitizeFileName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return "sound";
        }

        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String generateUniqueName(String originalName, File audioDir)
    {
        File originalFile = new File(audioDir, originalName + ".ogg");

        if (!originalFile.exists())
        {
            return originalName;
        }

        Matcher matcher = SUFFIX_PATTERN.matcher(originalName);

        if (matcher.matches())
        {
            return findAvailableFileName(matcher.group(1), Integer.parseInt(matcher.group(2)) + 1, audioDir);
        }

        return findAvailableFileName(originalName, 1, audioDir);
    }

    private static String findAvailableFileName(String baseName, int startNumber, File audioDir)
    {
        int number = startNumber;
        String candidateName;
        File candidateFile;

        do
        {
            candidateName = baseName + "_" + number;
            candidateFile = new File(audioDir, candidateName + ".ogg");
            number += 1;
        }
        while (candidateFile.exists());

        return candidateName;
    }
}
