package gbeic.bbsppp.keyframes;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/**
 * 把旧版独立纹理补间轨道一次性迁移到模型原生纹理轨道。
 *
 * <p>旧工程使用 {@code bbsppp_texture_tween} 保存纹理 Link 与补间关键帧，新版则把补间参数直接挂在
 * {@code texture} 关键帧上。本类按完整表单路径合并两者：同 tick 已有原生纹理帧时保留其 Link，
 * 仅迁移段落插值和补间参数；缺少原生帧时使用旧轨道 Link 创建。成功后删除旧轨道，确保重复加载安全。</p>
 *
 * <p>这是过渡期临时兼容代码；确认旧工程全部保存为新格式后，应删除本类及加载 Mixin 中的调用。</p>
 */
public final class LegacyTextureTweenTrackMigration
{
    private static final String LEGACY_TEXTURE_TWEEN_ID = "bbsppp_texture_tween";

    private LegacyTextureTweenTrackMigration()
    {}

    public static void migrate(FormProperties properties)
    {
        List<String> legacyKeys = new ArrayList<>();

        for (String key : properties.properties.keySet())
        {
            if (isLegacyChannel(key))
            {
                legacyKeys.add(key);
            }
        }

        for (String legacyKey : legacyKeys)
        {
            migrateChannel(properties, legacyKey);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void migrateChannel(FormProperties properties, String legacyKey)
    {
        KeyframeChannel legacy = properties.properties.get(legacyKey);

        if (legacy == null || legacy.getFactory() != KeyframeFactories.LINK)
        {
            return;
        }

        String textureKey = toTextureChannel(legacyKey);
        KeyframeChannel target = properties.properties.get(textureKey);

        if (target == null)
        {
            target = properties.registerChannel(textureKey, KeyframeFactories.LINK);
        }

        if (target == null || target.getFactory() != KeyframeFactories.LINK)
        {
            return;
        }

        for (Object object : new ArrayList<>(legacy.getKeyframes()))
        {
            if (!(object instanceof Keyframe<?> source) || !(source.getValue() instanceof Link sourceLink))
            {
                continue;
            }

            Keyframe destination = findAtTick(target, source.getTick());

            if (destination == null)
            {
                int index = target.insert(source.getTick(), sourceLink);

                destination = target.get(index);
            }

            if (destination != null)
            {
                destination.copyOverExtra(source);
            }
        }

        target.sort();
        properties.properties.remove(legacyKey);
        properties.remove(legacy);
    }

    private static Keyframe<?> findAtTick(KeyframeChannel<?> channel, float tick)
    {
        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            if (keyframe.getTick() == tick)
            {
                return keyframe;
            }
        }

        return null;
    }

    private static boolean isLegacyChannel(String id)
    {
        return LEGACY_TEXTURE_TWEEN_ID.equals(id) || id.endsWith("/" + LEGACY_TEXTURE_TWEEN_ID);
    }

    private static String toTextureChannel(String legacyKey)
    {
        return legacyKey.substring(0, legacyKey.length() - LEGACY_TEXTURE_TWEEN_ID.length()) + "texture";
    }
}
