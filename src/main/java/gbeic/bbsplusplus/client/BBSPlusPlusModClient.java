package gbeic.bbsplusplus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.BBSPlusPlusSettings;
import gbeic.bbsplusplus.client.audio.AudioOutputDeviceWatcher;
import gbeic.bbsplusplus.client.renderer.ItemSprayFormRenderer;
import gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection;
import gbeic.bbsplusplus.client.structure.VFXDestructionWandTooltip;
import gbeic.bbsplusplus.client.ui.forms.editors.forms.UIItemSprayForm;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import gbeic.bbsplusplus.forms.renderers.IconPlaceholderFormRenderer;
import gbeic.bbsplusplus.ui.film.lock.FilmEditorLockManager;
import gbeic.bbsplusplus.util.FilmAutoGameModeRestoreState;
import gbeic.bbsplusplus.util.ModelCullingConfigManager;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 1.20.x 与 1.21.11 共用的客户端入口。 */
public class BBSPlusPlusModClient implements ClientModInitializer
{
    private static boolean autoGameModeErrorLogged;

    @Override
    public void onInitializeClient()
    {
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
            BBSMod.setupConfig(Icons.DUPE, "bbspp", BBSMod.getSettingsPath("bbspp.json"),
                BBSPlusPlusSettings::register);
            ModelCullingConfigManager.onClientStarted();
            runTextureGradeSelfCheck();
            runMixinSelfCheck();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client ->
            runAutoGameMode(() -> FilmAutoGameModeRestoreState.tick(client), "tick"));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            runAutoGameMode(() -> FilmAutoGameModeRestoreState.tryRestoreBeforeShutdown(client), "restore"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> ModelCullingConfigManager.tick());
        ClientTickEvents.END_CLIENT_TICK.register(AudioOutputDeviceWatcher::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> FilmEditorLockManager.tickAll());

        ClientFeatures.initializeFeature("AAA 粒子客户端钩子", AAAParticleClientRegistration::register);

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
            if (!ClientFeatures.initializeFeature("物品喷射渲染器",
                () -> FormUtilsClient.register(ItemSprayForm.class, ItemSprayFormRenderer::new)))
            {
                ClientFeatures.initializeFeature("物品喷射占位渲染器", () ->
                    FormUtilsClient.register(ItemSprayForm.class, form ->
                        new IconPlaceholderFormRenderer<>(form, Link.assets("textures/itemspray.png"), null)));
            }

            ClientFeatures.initializeFeature("物品喷射编辑器",
                () -> UIFormEditor.register(ItemSprayForm.class, UIItemSprayForm::new));
        });

        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("bbsvfx") || loader.isModLoaded("xavin"))
        {
            ClientFeatures.initializeFeature("VFX 破坏魔杖", () ->
            {
                VFXDestructionWandSelection.register();
                VFXDestructionWandTooltip.register();
            });
        }

        ClientFeatures.initialize();
    }

    private static void runAutoGameMode(Runnable action, String phase)
    {
        try
        {
            action.run();
        }
        catch (RuntimeException | LinkageError e)
        {
            if (!autoGameModeErrorLogged)
            {
                autoGameModeErrorLogged = true;
                BBSPlusPlusMod.LOGGER.warn("自动旁观模式 {} 调用失败，后续同类错误不再重复输出。", phase, e);
            }
        }
    }

    /** 检查 1.21.11 专属配置中的每个 Mixin 类是否已打包。 */
    private static void runMixinSelfCheck()
    {
        String configName = "bbsplusplus-12111.mixins.json";
        ClassLoader loader = BBSPlusPlusModClient.class.getClassLoader();

        try (var input = loader.getResourceAsStream(configName))
        {
            if (input == null)
            {
                if (loader.getResource("gbeic/bbsplusplus/client/grade/GradeSelfCheck.class") != null)
                {
                    BBSPlusPlusMod.LOGGER.warn("1.21.11 Mixin 自检：缺少配置 {}", configName);
                }

                return;
            }

            JsonObject config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            String packagePath = config.get("package").getAsString().replace('.', '/') + "/";
            int configured = 0;
            int present = 0;

            for (String section : List.of("mixins", "client", "server"))
            {
                if (!config.has(section))
                {
                    continue;
                }

                for (var entry : config.getAsJsonArray(section))
                {
                    String name = entry.getAsString();
                    configured++;

                    // 查找资源，避免触发 Mixin 服务禁止的直接类加载。
                    if (loader.getResource(packagePath + name.replace('.', '/') + ".class") != null)
                    {
                        present++;
                    }
                    else
                    {
                        BBSPlusPlusMod.LOGGER.warn("1.21.11 Mixin 自检：类缺失 {}", name);
                    }
                }
            }

            if (present == configured && configured > 0)
            {
                BBSPlusPlusMod.LOGGER.info("1.21.11 Mixin 自检：类 {}/{}", present, configured);
            }
            else
            {
                BBSPlusPlusMod.LOGGER.warn("1.21.11 Mixin 自检：类 {}/{}，请检查 {}", present, configured, configName);
            }
        }
        catch (IOException | RuntimeException e)
        {
            BBSPlusPlusMod.LOGGER.warn("1.21.11 Mixin 自检执行失败", e);
        }
    }

    private static void runTextureGradeSelfCheck()
    {
        try
        {
            Class<?> selfCheck = Class.forName("gbeic.bbsplusplus.client.grade.GradeSelfCheck");
            selfCheck.getMethod("verify").invoke(null);
        }
        catch (ClassNotFoundException ignored)
        {
            // 纹理分级自检仅随 1.21.11 源码集构建。
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            BBSPlusPlusMod.LOGGER.warn("纹理分级注入自检执行失败", e);
        }
    }
}
