package gbeic.bbsplusplus.client;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.client.renderer.AAAParticleFormRenderer;
import gbeic.bbsplusplus.client.ui.forms.editors.forms.UIAAAParticleForm;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;

final class AAAParticleClientRegistration
{
    private static boolean cleanupErrorLogged;

    private AAAParticleClientRegistration()
    {}

    static void register()
    {
        if (!FabricLoader.getInstance().isModLoaded("aaa_particles"))
        {
            BBSPlusPlusMod.LOGGER.debug("未安装 aaa_particles，跳过 AAA 粒子客户端注册");
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> cleanupRenderers());
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
            ClientFeatures.initializeFeature("AAA 粒子渲染器",
                () -> FormUtilsClient.register(AAAParticleForm.class, AAAParticleFormRenderer::new));
            ClientFeatures.initializeFeature("AAA 粒子编辑器",
                () -> UIFormEditor.register(AAAParticleForm.class, UIAAAParticleForm::new));

            try
            {
                Files.createDirectories(BBSMod.getAssetsFolder().toPath().resolve("effeks"));
            }
            catch (IOException | RuntimeException e)
            {
                BBSPlusPlusMod.LOGGER.warn("创建 effeks 特效文件夹失败", e);
            }
        });
    }

    private static void cleanupRenderers()
    {
        try
        {
            // activeRenderers 使用 CopyOnWriteArrayList，允许清理期间移除当前渲染器。
            for (AAAParticleFormRenderer renderer : AAAParticleFormRenderer.activeRenderers)
            {
                renderer.checkCleanup();
            }
        }
        catch (RuntimeException | LinkageError e)
        {
            if (!cleanupErrorLogged)
            {
                cleanupErrorLogged = true;
                BBSPlusPlusMod.LOGGER.warn("AAA 粒子渲染器清理失败，后续同类错误不再重复输出", e);
            }
        }
    }
}
