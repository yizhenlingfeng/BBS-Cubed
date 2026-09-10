package gbeic.bbsplusplus.client.renderer;

import mchorse.bbs_mod.BBSMod;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.util.EnvLogger;
import mod.chloeprime.aaaparticles.api.client.EffectDefinition;
import mod.chloeprime.aaaparticles.api.client.EffectHolder;
import mod.chloeprime.aaaparticles.api.client.EffectMetadata;
import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerEffect;
import mod.chloeprime.aaaparticles.api.client.effekseer.TextureType;
import mod.chloeprime.aaaparticles.client.loader.EffekAssetLoader;
import mod.chloeprime.aaaparticles.client.render.RenderUtil;
import net.minecraft.util.Identifier;

import com.google.common.collect.BiMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * BBS 特效加载器。
 * <p>
 * 从 BBS 外部资源文件夹（config/bbs/assets/）加载 Effekseer 特效文件，
 * 并通过反射注入到 AAA Particles 的 EffekAssetLoader 中。
 * </p>
 */
public class BBSEffectLoader
{
    /* 跟踪已加载的 BBS 特效 ID，用于重载时清理 */
    private static final Set<Identifier> bbsLoadedEffects = new java.util.HashSet<>();

    /* EffectDefinition 缓存池，防止同一特效重复加载 */
    private static final Map<Identifier, EffectDefinition> definitionCache = new java.util.HashMap<>();

    /* AAA Particles 中 loadedEffects 字段的反射缓存 */
    private static Field loadedEffectsField = null;

    /* 加载锁，防止多线程同时调用 getOrLoad 导致 native 层竞态 */
    private static final Object LOAD_LOCK = new Object();

    /* 记录最近加载失败的时间，短暂抑制每帧重试，同时允许用户修复文件后自动恢复 */
    private static final Map<Identifier, Long> failedEffects = new java.util.HashMap<>();
    private static final long FAILED_RETRY_INTERVAL_MS = 2000L;

    /**
     * 获取或加载一个来自 BBS 外部资源的特效。
     * 特效会被注入到 AAA Particles 的 EffekAssetLoader 中。
     */
    public static EffectDefinition getOrLoad(Identifier id)
    {
        synchronized (LOAD_LOCK)
        {
            Long failedAt = failedEffects.get(id);
            if (failedAt != null && System.currentTimeMillis() - failedAt < FAILED_RETRY_INTERVAL_MS)
            {
                return null;
            }
            failedEffects.remove(id);

            // 优先从缓存池返回，避免重复加载同一特效
            EffectDefinition cachedDef = definitionCache.get(id);
            if (cachedDef != null)
            {
                return cachedDef;
            }

            // 检查 AAA Particles 是否已加载此特效
            EffekAssetLoader loader = EffekAssetLoader.get();

            if (loader != null)
            {
                EffectHolder holder = loader.get(id);

                if (holder != null)
                {
                    EffectDefinition existing = null;
                    try
                    {
                        existing = holder.lazyGet()
                                .orElseGet(() -> holder.load().join().orElse(null));
                    }
                    catch (Exception e)
                    {
                        EnvLogger.warn(BBSPlusPlusMod.LOGGER, "从已注册的 EffectHolder 获取特效失败，将从文件重新加载: {}", "Failed to get effect from registered EffectHolder, will reload from file: {}", id, e);
                    }

                    if (existing != null)
                    {
                        definitionCache.put(id, existing);
                        return existing;
                    }
                }
            }
            else
            {
                EnvLogger.warn(BBSPlusPlusMod.LOGGER, "EffekAssetLoader.get() 返回 null！", "EffekAssetLoader.get() returned null!");
            }

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "尝试从 BBS 资源加载特效：{}", "Attempting to load BBS asset effect: {}", id);

            // 从外部资源目录加载
            File assetsFolder = BBSMod.getAssetsFolder();
            String path = id.getPath();

            if (!path.startsWith("effeks/"))
            {
                path = "effeks/" + path;
            }

            if (!path.endsWith(".efkefc"))
            {
                path = path + ".efkefc";
            }

            File effectFile = new File(assetsFolder, path);

            if (!effectFile.exists())
            {
                EnvLogger.debug(BBSPlusPlusMod.LOGGER, "特效文件未找到：{}", "Effect file not found: {}", effectFile.getAbsolutePath());
                failedEffects.put(id, System.currentTimeMillis());
                return null;
            }

            EffectDefinition definition = loadEffect(effectFile, id);

            if (definition != null)
            {
                definitionCache.put(id, definition);
                injectIntoAAAParticles(id, definition);
                bbsLoadedEffects.add(id);
            }
            else
            {
                failedEffects.put(id, System.currentTimeMillis());
            }

            return definition;
        }
    }

    /**
     * 通过反射将特效注入到 AAA Particles 的 EffekAssetLoader。
     */
    private static void injectIntoAAAParticles(Identifier id, EffectDefinition definition)
    {
        try
        {
            EffekAssetLoader loader = EffekAssetLoader.get();

            if (loader == null)
            {
                EnvLogger.warn(BBSPlusPlusMod.LOGGER, "EffekAssetLoader 未初始化，无法注入特效 {}", "EffekAssetLoader not initialized, cannot inject effect {}", id);
                return;
            }

            if (loadedEffectsField == null)
            {
                loadedEffectsField = EffekAssetLoader.class.getDeclaredField("loadedEffects");
                loadedEffectsField.setAccessible(true);
            }

            // AAA Particles 2.2.3 起 loadedEffects 字段类型从 Map 改为 BiMap（Guava）
            @SuppressWarnings("unchecked")
            BiMap<Object, EffectHolder> loadedEffects = (BiMap<Object, EffectHolder>) loadedEffectsField.get(loader);

            // 将定义包装到 holder 中，使注册管线可用
            EffectHolder holder = new EffectHolder(EffectMetadata.DEFAULT, () -> definition);
            try
            {
                holder.load().join();
            }
            catch (Exception e)
            {
                EnvLogger.warn(BBSPlusPlusMod.LOGGER, "EffectHolder.load() 完成时出现异常（不影响注入）: {}", "EffectHolder.load() completed with exception (does not affect injection): {}", e.getMessage());
            }
            // 使用 forcePut 而非 put：BiMap.put 在值已存在于另一键下时会抛出 IllegalArgumentException，
            // forcePut 会先移除冲突的旧条目，保证注入始终成功
            loadedEffects.forcePut(id, holder);

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "已注入 BBS 特效到 AAA Particles：{}", "Injected BBS effect to AAA Particles: {}", id);
        }
        catch (Exception e)
        {
            EnvLogger.error(BBSPlusPlusMod.LOGGER, "注入特效到 AAA Particles 失败：{}", "Failed to inject effect to AAA Particles: {}", id, e);
        }
    }

    /**
     * 从文件加载特效
     */
    private static EffectDefinition loadEffect(File effectFile, Identifier id)
    {
        try (FileInputStream input = new FileInputStream(effectFile))
        {
            EffekseerEffect effect = new EffekseerEffect();
            boolean success = effect.load(input, 1);

            if (!success)
            {
                EnvLogger.error(BBSPlusPlusMod.LOGGER, "加载特效失败（文件可能损坏或版本过新）：{}", "Failed to load effect (file might be corrupted or version too new): {}", effectFile.getAbsolutePath());
                return null;
            }

            File parentDir = effectFile.getParentFile();

            // 在 GL 状态保护下加载所有 GPU 资源
            RenderUtil.runEffekLoadCodeHealthily(() ->
            {
                // 加载纹理
                for (TextureType texType : TextureType.values())
                {
                    int count = effect.textureCount(texType);
                    for (int i = 0; i < count; i++)
                    {
                        final int index = i;
                        String texturePath = effect.getTexturePath(i, texType);
                        if (texturePath != null && !texturePath.isEmpty())
                        {
                            loadAsset(parentDir, texturePath, (data, len) ->
                                effect.loadTexture(data, len, index, texType));
                        }
                    }
                }

                // 加载模型
                int modelCount = effect.modelCount();
                for (int i = 0; i < modelCount; i++)
                {
                    final int index = i;
                    String modelPath = effect.getModelPath(i);
                    if (modelPath != null && !modelPath.isEmpty())
                    {
                        loadAsset(parentDir, modelPath, (data, len) ->
                            effect.loadModel(data, len, index));
                    }
                }

                // 加载曲线
                int curveCount = effect.curveCount();
                for (int i = 0; i < curveCount; i++)
                {
                    final int index = i;
                    String curvePath = effect.getCurvePath(i);
                    if (curvePath != null && !curvePath.isEmpty())
                    {
                        loadAsset(parentDir, curvePath, (data, len) ->
                            effect.loadCurve(data, len, index));
                    }
                }

                // 加载材质
                int materialCount = effect.materialCount();
                for (int i = 0; i < materialCount; i++)
                {
                    final int index = i;
                    String materialPath = effect.getMaterialPath(i);
                    if (materialPath != null && !materialPath.isEmpty())
                    {
                        loadAsset(parentDir, materialPath, (data, len) ->
                            effect.loadMaterial(data, len, index));
                    }
                }
            });

            EffectDefinition definition = new EffectDefinition(EffectMetadata.DEFAULT);
            definition.setEffect(effect);

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "已加载 BBS 特效：{} 来自 {}", "Loaded BBS effect: {} from {}", id, effectFile.getAbsolutePath());
            return definition;
        }
        catch (IOException e)
        {
            EnvLogger.error(BBSPlusPlusMod.LOGGER, "读取特效文件失败：{}", "Failed to read effect file: {}", effectFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 加载特效引用的资源文件
     */
    private static void loadAsset(File parentDir, String assetPath, AssetLoader loader)
    {
        assetPath = assetPath.replace("\\", "/");
        File assetFile = new File(parentDir, assetPath);

        if (!assetFile.exists())
        {
            EnvLogger.warn(BBSPlusPlusMod.LOGGER, "资源文件未找到：{}", "Asset file not found: {}", assetFile.getAbsolutePath());
            return;
        }

        try (FileInputStream input = new FileInputStream(assetFile))
        {
            byte[] data = input.readAllBytes();
            loader.load(data, data.length);
        }
        catch (IOException e)
        {
            EnvLogger.error(BBSPlusPlusMod.LOGGER, "加载资源失败：{}", "Failed to load asset: {}", assetFile.getAbsolutePath(), e);
        }
    }

    /**
     * 标记缓存为脏，下次选择特效时重新从磁盘加载。
     * 不调用 holder.close() 避免触发 native DLL 释放正在渲染的资源。
     */
    public static void markCacheDirty()
    {
        synchronized (LOAD_LOCK)
        {
            definitionCache.clear();
            bbsLoadedEffects.clear();
            failedEffects.clear();

            try
            {
                EffekAssetLoader loader = EffekAssetLoader.get();

                if (loader != null && loadedEffectsField != null)
                {
                    @SuppressWarnings("unchecked")
                    BiMap<Object, EffectHolder> loadedEffects = (BiMap<Object, EffectHolder>) loadedEffectsField.get(loader);

                    java.util.List<Identifier> ids = new java.util.ArrayList<>();
                    for (Object k : loadedEffects.keySet())
                    {
                        if (k instanceof Identifier id)
                        {
                            if ("bbs".equals(id.getNamespace()))
                            {
                                ids.add(id);
                            }
                        }
                    }

                    for (Identifier id : ids)
                    {
                        // 强制停止所有正在使用该特效的粒子，防止 native 崩溃
                        for (AAAParticleFormRenderer renderer : AAAParticleFormRenderer.activeRenderers)
                        {
                            Identifier currentId = renderer.getLastEffectId();
                            if (id.equals(currentId))
                            {
                                renderer.forceStop();
                            }
                        }

                        EffectHolder removed = loadedEffects.remove(id);
                        if (removed != null)
                        {
                            removed.close();
                        }
                    }
                }
            }
            catch (Throwable e)
            {
                EnvLogger.error(BBSPlusPlusMod.LOGGER, "标记特效缓存脏失败", "Failed to mark effect cache as dirty", e);
            }

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "已标记所有 BBS 特效缓存为脏，下次选择时重新加载", "Marked all BBS effect caches as dirty, will reload on next selection");
        }
    }

    /**
     * 卸载指定特效，释放其 native 资源。
     * 当渲染器切换到新特效时调用，防止累积已加载的 EffectDefinition 导致 native 内存泄漏。
     */
    public static void unloadEffect(Identifier id)
    {
        if (id == null) return;

        // 检查是否还有其他活跃粒子正在使用此特效
        boolean inUse = false;
        for (AAAParticleFormRenderer renderer : AAAParticleFormRenderer.activeRenderers)
        {
                Identifier currentId = renderer.getLastEffectId();
                if (id.equals(currentId))
                {
                    inUse = true;
                    break;
                }
        }

        if (inUse)
        {
            return; // 仍有其他渲染器在使用，不可卸载
        }

        synchronized (LOAD_LOCK)
        {
            EffekAssetLoader loader = EffekAssetLoader.get();

            if (loader != null && loadedEffectsField != null)
            {
                try
                {
                    @SuppressWarnings("unchecked")
                    BiMap<Object, EffectHolder> loadedEffects = (BiMap<Object, EffectHolder>) loadedEffectsField.get(loader);
                    EffectHolder removed = loadedEffects.remove(id);

                    if (removed != null)
                    {
                        removed.close();
                        EnvLogger.debug(BBSPlusPlusMod.LOGGER, "已卸载特效：{}", "Unloaded effect: {}", id);
                    }
                }
                catch (IllegalAccessException e)
                {
                    EnvLogger.error(BBSPlusPlusMod.LOGGER, "卸载特效失败：{}", "Failed to unload effect: {}", id, e);
                }
            }

            definitionCache.remove(id);
            bbsLoadedEffects.remove(id);
        }
    }

    @FunctionalInterface
    private interface AssetLoader
    {
        boolean load(byte[] data, int length);
    }
}
