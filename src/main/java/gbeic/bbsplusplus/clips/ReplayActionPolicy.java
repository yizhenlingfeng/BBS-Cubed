package gbeic.bbsplusplus.clips;

import java.util.Locale;

/**
 * Identifies replay actions which must not be fired during time resampling.
 *
 * <p>声音/音频类动作的匹配从 {@code contains("sound")/contains("audio")}
 * 收窄为剪辑类名词根后缀匹配（soundactionclip/audioactionclip/soundclip/
 * audioclip）：旧写法误伤面太大，任何类名带 sound/audio 字样的第三方类
 * （如 SoundSettingsPanel、AudioMeterOverlay）都会被跳过重采样；收窄后
 * 只有真正的声音/音频动作剪辑类会被拦截，例如 SoundActionClip 或
 * AudioActionClip 仍按原语义 bypass。比较保持小写。</p>
 */
public final class ReplayActionPolicy
{
    private ReplayActionPolicy()
    {}

    public static boolean bypassesResampling(String className)
    {
        if (className == null)
        {
            return false;
        }

        String name = className.toLowerCase(Locale.ROOT);

        return name.contains(".actions.types.chat.")
            || name.contains(".actions.types.blocks.")
            || name.contains(".actions.types.item.")
            || name.endsWith("attackactionclip")
            || name.endsWith("damageactionclip")
            || name.endsWith("swipeactionclip")
            || name.endsWith("soundactionclip")
            || name.endsWith("audioactionclip")
            || name.endsWith("soundclip")
            || name.endsWith("audioclip");
    }
}
