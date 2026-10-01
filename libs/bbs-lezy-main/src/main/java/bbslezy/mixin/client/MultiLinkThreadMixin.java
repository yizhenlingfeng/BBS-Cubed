package bbslezy.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.resources.MultiLink;
import mchorse.bbs_mod.utils.resources.MultiLinkThread;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.resources.TextureProcessor;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(value = MultiLinkThread.class, remap = false)
public abstract class MultiLinkThreadMixin
{
    private static ExecutorService bbslezy$executor;

    private static synchronized ExecutorService bbslezy$getExecutor()
    {
        if (bbslezy$executor == null)
        {
            bbslezy$executor = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                (runnable) ->
                {
                    Thread thread = new Thread(runnable, "BBS Lezy MultiLink Worker");
                    thread.setDaemon(true);
                    return thread;
                }
            );
        }

        return bbslezy$executor;
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private static void bbslezy$add(MultiLink location, CallbackInfo ci)
    {
        ci.cancel();

        bbslezy$getExecutor().submit(() ->
        {
            try
            {
                Pixels pixels = TextureProcessor.process(location);

                MinecraftClient.getInstance().execute(() ->
                {
                    Texture newTexture = BBSModClient.getTextures().createTexture(location);

                    newTexture.bind();
                    newTexture.uploadTexture(pixels);

                    if (newTexture.isMipmap())
                    {
                        newTexture.generateMipmap();
                    }
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private static void bbslezy$clear(CallbackInfo ci)
    {
        ci.cancel();

        if (bbslezy$executor != null)
        {
            bbslezy$executor.shutdownNow();
            bbslezy$executor = null;
        }
    }
}
