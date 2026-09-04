package gbeic.bbsplusplus.mixin.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gbeic.bbsplusplus.utils.VanillaSoundResource;
import mchorse.bbs_mod.audio.AudioCacheManager;
import mchorse.bbs_mod.audio.SoundLikeManager;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIVanillaSoundList;
import net.minecraft.client.resource.language.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 接管 BBS Minecraft 音效列表的下载 / 点赞 / 试听缓存。
 *
 * <p>只注入参数类型为公开类型的方法；私有内部类 {@code VanillaSoundAsset}
 * 不能写在 mixin 方法签名里，否则会 InvalidInjectionException 直接崩溃。</p>
 *
 * @see gbeic.bbsplusplus.utils.VanillaSoundResource
 */
@Mixin(value = UIVanillaSoundList.class, remap = false)
public abstract class UIVanillaSoundListMixin
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UIVanillaSoundListMixin.class);

    @Shadow
    private Consumer<String> downloadCallback;

    @Shadow
    private Runnable likeToggleCallback;

    @Shadow
    private SoundLikeManager likeManager;

    /**
     * Put Minecraft's localized subtitle before the original sound name so it
     * stays visible when a narrow list clips long resource IDs. Sound operations
     * strip the localized display part before looking up the original value.
     */
    @Inject(method = "populateList", at = @At("RETURN"), remap = false)
    private void bbspp_cml$appendLocalizedSoundNames(CallbackInfo ci)
    {
        try
        {
            Map<?, ?> soundAssets = this.bbspp_cml$getSoundAssetMap();
            JsonObject soundsJson = this.bbspp_cml$getCachedSoundsJson();

            if (soundAssets == null || soundAssets.isEmpty() || soundsJson == null)
            {
                return;
            }

            Object sampleAsset = soundAssets.values().stream().filter((asset) -> asset != null).findFirst().orElse(null);

            if (sampleAsset == null)
            {
                return;
            }

            Field resourcePathField = sampleAsset.getClass().getDeclaredField("resourcePath");
            resourcePathField.setAccessible(true);

            List<String> sounds = ((UIVanillaSoundList) (Object) this).getList();
            Map<String, String> translations = new HashMap<>();
            boolean changed = false;

            for (int i = 0; i < sounds.size(); i++)
            {
                String displayName = sounds.get(i);
                String originalName = VanillaSoundResource.removeCategoryPrefix(displayName);
                Object asset = soundAssets.get(originalName);

                if (asset == null)
                {
                    continue;
                }

                Object resourcePath = resourcePathField.get(asset);
                String translation = this.bbspp_cml$getSoundTranslation(
                    resourcePath == null ? "" : resourcePath.toString(), soundsJson, translations
                );

                if (translation != null)
                {
                    sounds.set(i, VanillaSoundResource.addLocalizedName(displayName, translation));
                    changed = true;
                }
            }

            if (changed)
            {
                ((UIVanillaSoundList) (Object) this).update();
            }
        }
        catch (Exception e)
        {
            LOGGER.warn("[FSloveCML] 音效本地化名称追加失败", e);
        }
    }

    @Inject(method = "downloadSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$downloadSound(String displayName, CallbackInfo ci)
    {
        ci.cancel();

        String originalName = VanillaSoundResource.removeCategoryPrefix(displayName);
        String soundPath = this.bbspp_cml$getFirstSoundPath(originalName);

        if (soundPath == null)
        {
            return;
        }

        String finalName = VanillaSoundResource.copyToAudioFolder(originalName, soundPath);

        if (finalName != null && this.downloadCallback != null)
        {
            this.downloadCallback.accept("assets:audio/" + finalName + ".ogg");
        }

        ((UIVanillaSoundList) (Object) this).update();
    }

    @Inject(method = "toggleLikeWithDownload", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$toggleLikeWithDownload(String displayName, CallbackInfo ci)
    {
        ci.cancel();

        String originalName = VanillaSoundResource.removeCategoryPrefix(displayName);
        String soundPath = this.bbspp_cml$getFirstSoundPath(originalName);

        if (soundPath == null)
        {
            return;
        }

        try
        {
            String downloadedPath = VanillaSoundResource.findDownloadedPath(displayName);

            if (downloadedPath != null)
            {
                this.likeManager.toggleSoundLiked(downloadedPath, displayName);

                if (this.likeToggleCallback != null)
                {
                    this.likeToggleCallback.run();
                }

                ((UIVanillaSoundList) (Object) this).update();
                return;
            }

            String finalName = VanillaSoundResource.copyToAudioFolder(originalName, soundPath);

            if (finalName != null)
            {
                String assetsPath = "assets:audio/" + finalName + ".ogg";
                this.likeManager.setSoundLiked(assetsPath, displayName, true);

                if (this.downloadCallback != null)
                {
                    this.downloadCallback.accept(assetsPath);
                }

                if (this.likeToggleCallback != null)
                {
                    this.likeToggleCallback.run();
                }

                ((UIVanillaSoundList) (Object) this).update();
            }
        }
        catch (Exception e)
        {
            LOGGER.warn("[FSloveCML] 音效点赞/下载失败", e);
        }
    }

    @Inject(method = "findDownloadedSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$findDownloadedSound(String displayName, CallbackInfoReturnable<String> cir)
    {
        cir.setReturnValue(VanillaSoundResource.findDownloadedPath(displayName));
    }

    @Inject(method = "getTemporaryFileForSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$getTemporaryFileForSound(String displayName, CallbackInfoReturnable<File> cir)
    {
        String originalName = VanillaSoundResource.removeCategoryPrefix(displayName);
        String soundPath = this.bbspp_cml$getFirstSoundPath(originalName);

        if (soundPath == null)
        {
            cir.setReturnValue(null);
            return;
        }

        try
        {
            AudioCacheManager cacheManager = AudioCacheManager.getInstance();
            String cacheKey = soundPath.endsWith(".ogg") ? soundPath : soundPath + ".ogg";
            cacheKey = cacheKey.replace('\\', '/');

            File cached = cacheManager.getCachedFile(cacheKey);

            if (cached != null && cached.isFile() && cached.length() > 0)
            {
                cir.setReturnValue(cached);
                return;
            }

            File cacheFile = cacheManager.createTempCacheFile(cacheKey);

            if (cacheFile == null)
            {
                cir.setReturnValue(null);
                return;
            }

            File result = VanillaSoundResource.copyToCacheFile(soundPath, cacheFile);
            cir.setReturnValue(result);
        }
        catch (Exception e)
        {
            LOGGER.warn("[FSloveCML] 音效缓存文件获取失败", e);
            cir.setReturnValue(null);
        }
    }

    @Unique
    private String bbspp_cml$getFirstSoundPath(String originalName)
    {
        try
        {
            Map<?, ?> soundMap = this.bbspp_cml$getSoundAssetMap();

            if (soundMap == null)
            {
                return null;
            }

            Object asset = soundMap.get(originalName);

            if (asset == null)
            {
                return null;
            }

            Field pathsField = asset.getClass().getDeclaredField("actualSoundPaths");
            pathsField.setAccessible(true);
            Object paths = pathsField.get(asset);

            if (paths instanceof List<?> list && !list.isEmpty() && list.get(0) != null)
            {
                return list.get(0).toString();
            }

            Field resField = asset.getClass().getDeclaredField("resourcePath");
            resField.setAccessible(true);
            Object res = resField.get(asset);

            return res == null ? null : res.toString();
        }
        catch (Exception e)
        {
            LOGGER.warn("[FSloveCML] 音效路径查找失败", e);
            return null;
        }
    }

    @Unique
    private Map<?, ?> bbspp_cml$getSoundAssetMap() throws ReflectiveOperationException
    {
        Field mapField = UIVanillaSoundList.class.getDeclaredField("soundAssetMap");
        mapField.setAccessible(true);
        Object map = mapField.get(this);

        return map instanceof Map<?, ?> soundMap ? soundMap : null;
    }

    @Unique
    private JsonObject bbspp_cml$getCachedSoundsJson() throws ReflectiveOperationException
    {
        Field jsonField = UIVanillaSoundList.class.getDeclaredField("cachedSoundsJson");
        jsonField.setAccessible(true);
        Object json = jsonField.get(this);

        return json instanceof JsonObject soundsJson ? soundsJson : null;
    }

    @Unique
    private String bbspp_cml$getSoundTranslation(
        String resourcePath,
        JsonObject soundsJson,
        Map<String, String> translations
    )
    {
        int namespace = resourcePath.indexOf(':');
        String eventName = namespace < 0 ? resourcePath : resourcePath.substring(namespace + 1);

        if (eventName.isEmpty())
        {
            return null;
        }

        if (translations.containsKey(eventName))
        {
            return translations.get(eventName);
        }

        JsonElement event = soundsJson.get(eventName);
        String translation = null;

        if (event != null && event.isJsonObject())
        {
            JsonElement subtitle = event.getAsJsonObject().get("subtitle");

            if (subtitle != null && subtitle.isJsonPrimitive())
            {
                String translationKey = subtitle.getAsString();

                if (I18n.hasTranslation(translationKey))
                {
                    String localized = I18n.translate(translationKey).trim();

                    if (!localized.isEmpty() && !localized.equals(translationKey))
                    {
                        translation = localized;
                    }
                }
            }
        }

        translations.put(eventName, translation);

        return translation;
    }
}
