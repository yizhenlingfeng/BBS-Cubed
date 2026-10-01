package bbslezy.mixin.client;

import bbslezy.audio.LezyAudioCodecs;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AudioReader.class, remap = false)
public abstract class AudioReaderMixin
{
    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private static void bbslezy$decodeAny(AssetProvider provider, Link link, CallbackInfoReturnable<Wave> cir) throws Exception
    {
        String pathLower = link.path.toLowerCase();

        if (LezyAudioCodecs.shouldHandle(pathLower))
        {
            cir.setReturnValue(LezyAudioCodecs.decode(provider, link));
        }
    }
}
