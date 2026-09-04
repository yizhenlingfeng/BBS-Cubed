package gbeic.bbsplusplus.ui.film.clips;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * 关键帧通道标题工具：把 channel.getId() 映射为翻译键
 * {@code bbs.ui.camera.clips.bbs:<id>} 并返回 IKey，缺失时 L10n 自动回退到 key 本身。
 */
public final class ClipTrackTitles
{
    private ClipTrackTitles()
    {}

    public static IKey titleOf(KeyframeChannel channel)
    {
        return L10n.lang("bbs.ui.camera.clips.bbs:" + channel.getId());
    }
}
