package gbeic.bbsplusplus.clips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 特征测试：固定 {@link ReplayActionPolicy} 的重采样绕过规则。
 * 重点是"声音/音频"只按剪辑类名词根匹配，不再按 contains 泛匹配。
 */
class ReplayActionPolicyTest
{
    @Test
    void nullClassNameNeverBypasses()
    {
        assertFalse(ReplayActionPolicy.bypassesResampling(null));
    }

    @Test
    void actionClipsByNameRootAreBypassed()
    {
        assertTrue(ReplayActionPolicy.bypassesResampling("mchorse.bbs_mod.film.replays.actions.types.ActionClipAttackActionClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("some.pkg.SoundActionClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("some.pkg.AudioActionClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("some.pkg.SoundClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("some.pkg.AudioClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("some.pkg.DamageActionClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("some.pkg.SwipeActionClip"));
    }

    @Test
    void packageBasedBypassCoversChatBlocksAndItemActions()
    {
        assertTrue(ReplayActionPolicy.bypassesResampling("x.actions.types.chat.ChatActionClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("x.actions.types.blocks.PlaceActionClip"));
        assertTrue(ReplayActionPolicy.bypassesResampling("x.actions.types.item.UseActionClip"));
    }

    @Test
    void classesThatMerelyMentionSoundOrAudioAreNotBypassed()
    {
        assertFalse(ReplayActionPolicy.bypassesResampling("some.pkg.SoundSettingsPanel"));
        assertFalse(ReplayActionPolicy.bypassesResampling("some.pkg.AudioMeterOverlay"));
        assertFalse(ReplayActionPolicy.bypassesResampling("some.pkg.SoundActionClipHandler"));
    }

    @Test
    void matchingIsCaseInsensitive()
    {
        assertTrue(ReplayActionPolicy.bypassesResampling("some.pkg.SOUNDACTIONCLIP"));
        assertTrue(ReplayActionPolicy.bypassesResampling("x.ACTIONS.Types.Chat.Y"));
    }

    @Test
    void unrelatedClipsAreNotBypassed()
    {
        assertFalse(ReplayActionPolicy.bypassesResampling("mchorse.bbs_mod.film.replays.actions.types.ActionClip"));
        assertFalse(ReplayActionPolicy.bypassesResampling(""));
    }
}
