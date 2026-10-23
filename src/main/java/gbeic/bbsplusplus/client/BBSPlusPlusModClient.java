package gbeic.bbsplusplus.client;

import mchorse.bbs_mod.BBSMod;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.BBSPlusPlusSettings;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import gbeic.bbsplusplus.client.audio.AudioOutputDeviceWatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import gbeic.bbsplusplus.util.ModelCullingConfigManager;
import net.fabricmc.loader.api.FabricLoader;

/**
 * BBS++ 客户端入口点（1.20.x 与 1.21.11 共用）。
 * <p>
 * 版本差异通过运行时类检测收敛，不再维护 {@code BBSPlusPlusModClient12111} 副本：
 * 被 1.21.11 source-set excludes 的类（AAA/物品喷射渲染器、VFX 破坏魔杖、
 * {@code FilmAutoGameModeRestoreState} 等）全部以可选方式注册。
 * </p>
 */
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
        });

        registerAutoGameModeHooks();
        ClientTickEvents.END_CLIENT_TICK.register(client -> ModelCullingConfigManager.tick());
        ClientTickEvents.END_CLIENT_TICK.register(AudioOutputDeviceWatcher::tick);

        boolean hasAAAParticles = FabricLoader.getInstance().isModLoaded("aaa_particles");

        if (hasAAAParticles && classExists("gbeic.bbsplusplus.client.renderer.AAAParticleFormRenderer"))
        {
            this.registerAAAParticles();
        }
        else
        {
            BBSPlusPlusMod.LOGGER.info("未检测到 aaa_particles 前置模组或渲染器不可用，跳过 AAA 粒子功能注册。");
        }

        // ItemSprayForm 注册（渲染器在 1.21.11 被 excludes，运行时检测）
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
            try { registerFormRenderer("gbeic.bbsplusplus.forms.ItemSprayForm",
                    "gbeic.bbsplusplus.client.renderer.ItemSprayFormRenderer",
                    "gbeic.bbsplusplus.client.ui.forms.editors.forms.UIItemSprayForm"); }
            catch (Throwable e) { BBSPlusPlusMod.LOGGER.warn("注册物品喷射形态失败", e); }

            /* 渲染器被 excludes 时用图标占位渲染器兜底：没有渲染器时 BBS 的格子预览整格空白，
             * 编辑器打开面板还会在 getOrigin() 里对 null 调 collectMatrices() 抛 NPE。 */
            gbeic.bbsplusplus.forms.renderers.IconPlaceholderFormRenderer.registerIfRendererMissing(
                gbeic.bbsplusplus.forms.ItemSprayForm.class,
                "gbeic.bbsplusplus.client.renderer.ItemSprayFormRenderer",
                mchorse.bbs_mod.resources.Link.assets("textures/itemspray.png"));
        });

        this.registerVFXDestructionWand();
    }

    private static boolean classExists(String name)
    {
        try { Class.forName(name); return true; }
        catch (ClassNotFoundException e) { return false; }
    }

    /**
     * 自动旁观模式的 tick/restore 钩子。
     * <p>
     * {@code FilmAutoGameModeRestoreState} 在 1.21.11 构建中被 excludes 排除，
     * 因此这里用一次性反射注册；类缺失时只打一条 info，不影响其余客户端初始化。
     * </p>
     */
    private static void registerAutoGameModeHooks()
    {
        try
        {
            Class<?> type = Class.forName("gbeic.bbsplusplus.util.FilmAutoGameModeRestoreState");
            java.lang.reflect.Method tick = type.getMethod("tick", net.minecraft.client.MinecraftClient.class);
            java.lang.reflect.Method restore = type.getMethod("tryRestoreBeforeShutdown", net.minecraft.client.MinecraftClient.class);

            ClientTickEvents.END_CLIENT_TICK.register(client -> invokeAutoGameMode(tick, client, "tick"));
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> invokeAutoGameMode(restore, client, "restore"));
        }
        catch (ClassNotFoundException e)
        {
            BBSPlusPlusMod.LOGGER.info("自动旁观模式在当前构建不可用（FilmAutoGameModeRestoreState 未编译），跳过钩子。");
        }
        catch (NoSuchMethodException e)
        {
            BBSPlusPlusMod.LOGGER.warn("自动旁观模式钩子签名不匹配，跳过。", e);
        }
    }

    private static void invokeAutoGameMode(java.lang.reflect.Method method, net.minecraft.client.MinecraftClient client, String phase)
    {
        try
        {
            method.invoke(null, client);
        }
        catch (ReflectiveOperationException e)
        {
            if (!autoGameModeErrorLogged)
            {
                autoGameModeErrorLogged = true;
                BBSPlusPlusMod.LOGGER.warn("自动旁观模式 {} 调用失败，后续同类错误不再重复输出。", phase, e);
            }
        }
    }

    /**
     * 用反射注册 FormRenderer 和 UIFormEditor。
     * 1.20.1 上类存在时正常注册；1.21.11 上类被 excludes 时跳过。
     */
    @SuppressWarnings("unchecked")
    private static void registerFormRenderer(String formClassName, String rendererClassName, String uiClassName) throws Exception
    {
        Class<?> formCls = Class.forName(formClassName);

        /* 渲染器与编辑器面板各自独立可用：1.21.11 上渲染器被 excludes 而编辑器面板仍在，
         * 此时若因渲染器缺失而整体 return，编辑器也会跟着注册不上（表现为"点开没反应"）。 */
        if (classExists(rendererClassName))
        {
            Class<?> rendererCls = Class.forName(rendererClassName);

            // 创建 IFormRendererFactory 代理
            Class<?> factoryInterface = Class.forName("mchorse.bbs_mod.forms.FormUtilsClient$IFormRendererFactory");
            Object factory = java.lang.reflect.Proxy.newProxyInstance(
                BBSPlusPlusModClient.class.getClassLoader(),
                new Class<?>[]{factoryInterface},
                (proxy, method, args) -> {
                    if (method.getName().equals("create")) {
                        return rendererCls.getConstructor(formCls).newInstance(args[0]);
                    }
                    return null;
                });

            mchorse.bbs_mod.forms.FormUtilsClient.class
                .getMethod("register", Class.class, factoryInterface)
                .invoke(null, formCls, factory);
        }
        else
        {
            BBSPlusPlusMod.LOGGER.warn("渲染器 {} 不可用，跳过 {} 的渲染器注册", rendererClassName, formClassName);
        }

        // UIFormEditor.register
        if (classExists(uiClassName)) {
            registerFormEditor(formCls, uiClassName);
        }
    }

    /**
     * 注册表单的编辑器面板（UIFormEditor）。
     * <p>
     * BBS 2.7 起 {@code UIFormEditor.register} 的第二参数是
     * {@code java.util.function.Supplier<UIForm>}（见 BBSModClient 里的
     * {@code RegisterFormEditorsEvent#register}），BBS 的编辑器面板类一律无参构造；
     * 旧的嵌套工厂接口 {@code UIFormEditor$IFormEditorFactory} 在 2.7 已被移除。
     * 这里按 {@code register} 方法的真实参数类型分派，避免再次写死接口名。
     * </p>
     */
    private static void registerFormEditor(Class<?> formCls, String uiClassName) throws Exception
    {
        Class<?> uiCls = Class.forName(uiClassName);
        Class<?> editorCls = mchorse.bbs_mod.ui.forms.editors.UIFormEditor.class;

        java.lang.reflect.Method register = null;
        Class<?> param = null;

        for (java.lang.reflect.Method m : editorCls.getMethods())
        {
            if (!m.getName().equals("register")) continue;

            Class<?>[] p = m.getParameterTypes();

            if (p.length == 2 && p[0] == Class.class)
            {
                register = m;
                param = p[1];
                break;
            }
        }

        if (register == null)
        {
            throw new NoSuchMethodException("UIFormEditor.register(Class, ?) not found");
        }

        /* Supplier<UIForm>：面板类无参构造 */
        if (java.util.function.Supplier.class.isAssignableFrom(param))
        {
            Object supplier = java.lang.reflect.Proxy.newProxyInstance(
                BBSPlusPlusModClient.class.getClassLoader(),
                new Class<?>[]{param},
                (proxy, method, args) -> {
                    if (method.getName().equals("get")) {
                        return uiCls.getDeclaredConstructor().newInstance();
                    }
                    return null;
                });

            register.invoke(null, formCls, supplier);
        }
        /* 旧版 BBS：嵌套的 IFormEditorFactory，create(UIFormEditor) */
        else
        {
            Object factory = java.lang.reflect.Proxy.newProxyInstance(
                BBSPlusPlusModClient.class.getClassLoader(),
                new Class<?>[]{param},
                (proxy, method, args) -> {
                    if (method.getName().equals("create")) {
                        return uiCls.getDeclaredConstructor().newInstance();
                    }
                    return null;
                });

            register.invoke(null, formCls, factory);
        }
    }

    private void registerAAAParticles()
    {
        try
        {
            Class<?> rendererCls = Class.forName("gbeic.bbsplusplus.client.renderer.AAAParticleFormRenderer");
            Object activeRenderers = rendererCls.getField("activeRenderers").get(null);

            ClientTickEvents.END_CLIENT_TICK.register(client ->
            {
                try
                {
                    java.util.List<?> renderers = new java.util.ArrayList<>((java.util.List<?>) activeRenderers);
                    for (Object renderer : renderers)
                    {
                        renderer.getClass().getMethod("checkCleanup").invoke(renderer);
                    }
                }
                catch (Throwable ignored) {}
            });
        }
        catch (Throwable e)
        {
            BBSPlusPlusMod.LOGGER.warn("AAA 粒子渲染器清理观察器注册失败", e);
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
            try { registerFormRenderer("gbeic.bbsplusplus.forms.AAAParticleForm",
                    "gbeic.bbsplusplus.client.renderer.AAAParticleFormRenderer",
                    "gbeic.bbsplusplus.client.ui.forms.editors.forms.UIAAAParticleForm"); }
            catch (Throwable e) { BBSPlusPlusMod.LOGGER.warn("注册 AAA 粒子表单失败", e); }

            try
            {
                java.io.File effeksDir = new java.io.File(BBSMod.getAssetsFolder(), "effeks");
                if (!effeksDir.exists()) effeksDir.mkdirs();
            }
            catch (Exception e)
            {
                BBSPlusPlusMod.LOGGER.error("创建 effeks 特效文件夹失败", e);
            }
        });
    }

    private void registerVFXDestructionWand()
    {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("bbsvfx") && !loader.isModLoaded("xavin")) return;

        try
        {
            if (classExists("gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection"))
            {
                Class.forName("gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection")
                     .getMethod("register").invoke(null);
                Class.forName("gbeic.bbsplusplus.client.structure.VFXDestructionWandTooltip")
                     .getMethod("register").invoke(null);
            }
        }
        catch (Throwable e)
        {
            BBSPlusPlusMod.LOGGER.warn("VFX 破坏魔杖注册失败", e);
        }
    }
}
