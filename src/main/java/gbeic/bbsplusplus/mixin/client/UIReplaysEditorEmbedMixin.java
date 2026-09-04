package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.UIReplayKeyframeEmbedHelper;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 切换 replay 时 updateChannelsList 整体重建 keyframeEditor,需重新 embed inspector(新 Bug2)。
 */
@Mixin(value = UIReplaysEditor.class, remap = false)
public abstract class UIReplaysEditorEmbedMixin
{
    @Inject(method = "updateChannelsList", at = @At("HEAD"), remap = false)
    private void bbspp_cml$prepareInspectorBeforeUpdate(CallbackInfo ci)
    {
        UIReplayKeyframeEmbedHelper.prepareReplayInspectorRebuild((UIReplaysEditor) (Object) this);
    }

    @Inject(method = "updateChannelsList", at = @At("TAIL"), remap = false)
    private void bbspp_cml$reembedAfterUpdateChannels(CallbackInfo ci)
    {
        UIReplayKeyframeEmbedHelper.reembedFromReplay((UIReplaysEditor) (Object) this);
    }
}
