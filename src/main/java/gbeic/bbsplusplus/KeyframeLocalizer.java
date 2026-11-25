package gbeic.bbsplusplus;


import gbeic.bbsplusplus.settings.ModSettings;
import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.Set;

/**
 * 关键帧轨道名称本地化工具。
 * <p>
 * 将硬编码的英文轨道名称映射为中文显示名。
 * 通过 {@link ModSettings#chineseKeyframeNames} 开关控制。
 * </p>
 * <p>
 * 轨道映射按形态/插件隔离：
 * 通用轨道（BBS 原版、摄像机剪辑、IRLights、LumenCore 等）走 {@link KeyframeLocalizerEntries#CN}；
 * BBS VFX 插件的轨道走 {@link KeyframeLocalizerEntries#VFX_CN}（语言键前缀 bbspp.keyframe.vfx.）；
 * VFX LIGHTS 灯插件的轨道走 {@link KeyframeLocalizerEntries#VFX_LIGHT_CN}（语言键前缀 bbspp.keyframe.vfxlight.）。
 * 互不干扰，避免同名属性（speed/radius/color 等）的翻译互相覆盖。
 * </p>
 */
public class KeyframeLocalizer
{

    /** BBS VFX 插件注入到任意 BBS 表单上的增强轨道（拖影、混合、文字增强、跟随偏移等）。
     *  这些轨道即使所属表单不是 VFX 表单（如 BlockForm 上的 blend 通道），也属于 VFX 插件。 */
    private static final Set<String> VFX_ENHANCEMENT_TRACKS = Set.of(
        "smear", "smear_frames", "motion_lines", "blend", "blend_mode", "follow_offset",
        "tracking", "stroke_width", "stroke_color", "stroke_only", "font", "font_size",
        "projection", "proj_range", "proj_fade", "gradient", "gradient_start", "gradient_end",
        "gradient_angle", "xavin$whole"
    );

    /**
     * 返回指定键的本地化名称，若开关未开启或无翻译则返回 {@code null}。
     * <p>
     * 查找优先级：用户自定义 JSON（L10n）> 硬编码映射（{@link KeyframeLocalizer}）。
     * 支持模型路径前缀：{@code "esey/pose_overlay2"} → {@code "esey/姿势叠加 2"}。
     * 支持数字后缀：{@code "pose_overlay1"} → {@code "姿势叠加 1"}。
     * </p>
     */

    /**
     * 检查当前游戏语言是否为中文。
     * 非中文语言（如 en_us）下，即使开启中文轨道名称开关也应显示英文。
     */
    private static boolean isChineseLanguage()
    {
        /* 优先使用 BBS 自己的语言设置 */
        try
        {
            String lang = BBSModClient.getLanguageKey();
            if (lang != null && !lang.isEmpty())
            {
                return lang.startsWith("zh") || lang.contains("zh_");
            }
        }
        catch (Throwable ignored)
        {
        }

        /* 回退到 Minecraft 的语言设置 */
        try
        {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getLanguageManager() != null)
            {
                String lang = client.getLanguageManager().getLanguage();
                return lang != null && (lang.startsWith("zh") || lang.contains("zh_"));
            }
        }
        catch (Throwable ignored)
        {
        }
        return true; /* 无法获取语言时默认按中文处理，保持原有行为 */
    }

    public static String localize(String key)
    {
        if (key == null || key.isEmpty())
        {
            return null;
        }

        /* 游戏语言为非中文时，即使开启了中文轨道名称开关也返回英文 */
        if (!isChineseLanguage())
        {
            return null;
        }

        if (ModSettings.chineseKeyframeNames != null
            && ModSettings.chineseKeyframeNames.get())
        {
            /* 同时支持两种 id 分隔格式：
             * - 旧版 BBS 2.5：path/to/bone.property（/ 分隔）
             * - 新版 BBS 2.6：material.smoothness / ik.chain1.target（. 分隔）
             * 取最后一段作为 property 名来查翻译。 */
            String prefix = "";
            String body = key;
            int slash = key.lastIndexOf('/');
            int dot = key.lastIndexOf('.');

            /* 取更靠后的分隔符作为分割点 */
            int split = Math.max(slash, dot);

            if (split >= 0)
            {
                prefix = key.substring(0, split + 1);
                body = key.substring(split + 1).trim();
            }

            /* 2.6 新轨道体系的前缀（如 materials.*. / ik. / physics.）是轨道类型标识，
               不是骨骼路径，用户不需要看到。当前缀里包含 * 通配符时，直接去掉前缀。 */
            boolean stripPrefix = prefix.contains("*") || prefix.equals("irlights.");

            if (body.isEmpty())
            {
                return null;
            }

            KeyframeTrackExtensionRegistry.Extension extension = KeyframeTrackExtensionRegistry.get(body);

            if (extension != null && extension.chineseName() != null && !extension.chineseName().isEmpty())
            {
                return stripPrefix ? extension.chineseName() : prefix + extension.chineseName();
            }

            /* 1. 优先查用户自定义 JSON（bbs_addons_*.json 中的 bbspp.keyframe.*） */
            String l10nResult = lookupL10n(body);

            if (l10nResult != null)
            {
                return stripPrefix ? l10nResult : prefix + l10nResult;
            }

            /* 2. 数字后缀匹配：pose_overlay1 → 查 pose_overlay + " 1" */
            String withSuffix = matchWithSuffix(body);

            if (withSuffix != null)
            {
                return stripPrefix ? withSuffix : prefix + withSuffix;
            }

            /* 3. 硬编码兜底 */
            String exact = KeyframeLocalizerEntries.CN.get(body);

            if (exact != null)
            {
                return stripPrefix ? exact : prefix + exact;
            }
        }

        return null;
    }

    /**
     * 返回物品喷射形态专属轨道名称，避免 {@code speed/range/radius} 等通用键污染其它形态。
     */
    public static String localizeItemSpray(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.item_spray.", KeyframeLocalizerEntries.ITEM_SPRAY_CN);
    }

    /** 返回 AAA 粒子形态专属轨道名称，避免通用属性名影响其它形态。 */
    public static String localizeAAAParticle(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.aaa_particle.", KeyframeLocalizerEntries.AAA_PARTICLE_CN);
    }

    public static String localizeVFX(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.vfx.", KeyframeLocalizerEntries.VFX_CN);
    }

    /** 返回 VFX LIGHTS 灯插件专属轨道名称（语言键前缀 bbspp.keyframe.vfxlight.）。 */
    public static String localizeVfxLight(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.vfxlight.", KeyframeLocalizerEntries.VFX_LIGHT_CN);
    }

    /**
     * 判断轨道 id 是否为 BBS VFX 插件注入到任意 BBS 表单上的增强轨道
     * （拖影、动态线、混合、文字增强、跟随偏移等）。
     * 这些轨道即使所属表单不是 VFX 表单（例如 BlockForm 上的 blend 通道），
     * 也属于 VFX 插件，应按 VFX 专属表汉化，不能依赖通用映射。
     */
    public static boolean isVfxEnhancementTrack(String key)
    {
        if (key == null || key.isEmpty())
        {
            return false;
        }

        if (key.startsWith("xavin_"))
        {
            return true;
        }

        return VFX_ENHANCEMENT_TRACKS.contains(key);
    }

    private static String localizeWithMap(String key, String l10nPrefix, Map<String, String> fallback)
    {
        if (key == null || key.isEmpty())
        {
            return null;
        }

        /* 游戏语言为非中文时，即使开启中文轨道名称开关也返回英文 */
        if (!isChineseLanguage())
        {
            return null;
        }

        if (ModSettings.chineseKeyframeNames != null
            && ModSettings.chineseKeyframeNames.get())
        {
            /* 同时支持 / 和 . 两种 id 分隔格式（旧版 / 分隔，新版 2.6 . 分隔） */
            String prefix = "";
            String body = key;
            int slash = key.lastIndexOf('/');
            int dot = key.lastIndexOf('.');
            int split = Math.max(slash, dot);

            if (split >= 0)
            {
                prefix = key.substring(0, split + 1);
                body = key.substring(split + 1).trim();
            }

            if (body.isEmpty())
            {
                return null;
            }

            String l10nResult = lookupL10n(body, l10nPrefix);

            if (l10nResult != null)
            {
                return prefix + l10nResult;
            }

            String withSuffix = matchWithSuffix(body, fallback);

            if (withSuffix != null)
            {
                return prefix + withSuffix;
            }

            String exact = fallback.get(body);

            if (exact != null)
            {
                return prefix + exact;
            }
        }

        return null;
    }

    /**
     * 通过 BBS 的 L10n 系统查询 {@code bbspp.keyframe.<body>}。
     * 用户可通过修改 JSON 文件自定义，优先级最高。
     */
    private static String lookupL10n(String body)
    {
        return lookupL10n(body, "bbspp.keyframe.");
    }

    private static String lookupL10n(String body, String l10nPrefix)
    {
        try
        {
            /* 数字后缀处理：先查 baseName 的 L10n */
            String baseName = stripNumericSuffix(body);

            if (baseName != null)
            {
                String translated = getL10nValue(baseName, l10nPrefix);

                if (translated != null)
                {
                    String number = body.substring(baseName.length());

                    return translated + " " + number;
                }
            }

            return getL10nValue(body, l10nPrefix);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String getL10nValue(String key, String l10nPrefix)
    {
        if (BBSModClient.getL10n() == null)
        {
            return null;
        }

        String l10nKey = l10nPrefix + key;
        IKey iKey = L10n.lang(l10nKey);
        String text = iKey.get();

        /* L10n.lang() 找不到翻译时返回 key 本身 */
        if (text != null && !text.equals(l10nKey))
        {
            return text;
        }

        return null;
    }

    /**
     * 分离数字后缀，例如 {@code "pose_overlay1"} → {@code "pose_overlay"}。
     * 若无后缀返回 {@code null}。
     */
    private static String stripNumericSuffix(String key)
    {
        int digits = 0;

        for (int i = key.length() - 1; i >= 0; i--)
        {
            if (Character.isDigit(key.charAt(i)))
            {
                digits++;
            }
            else
            {
                break;
            }
        }

        if (digits == 0)
        {
            return null;
        }

        return key.substring(0, key.length() - digits);
    }

    /**
     * 尝试匹配带数字后缀的键名，例如 {@code "pose_overlay1"} → "姿势叠加 1"。
     */
    private static String matchWithSuffix(String key)
    {
        return matchWithSuffix(key, KeyframeLocalizerEntries.CN);
    }

    private static String matchWithSuffix(String key, Map<String, String> map)
    {
        String base = stripNumericSuffix(key);

        if (base == null)
        {
            return null;
        }

        String number = key.substring(base.length());
        String translated = map.get(base);

        if (translated != null)
        {
            return translated + " " + number;
        }

        return null;
    }
}
