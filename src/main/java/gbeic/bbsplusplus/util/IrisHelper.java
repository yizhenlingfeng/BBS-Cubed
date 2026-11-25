package gbeic.bbsplusplus.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import gbeic.bbsplusplus.BBSPlusPlusMod;

public class IrisHelper {
    private static boolean initialized = false;
    private static boolean shaderStateErrorLogged;
    private static Method toggleShadersMethod;
    private static Method getIrisConfigMethod;
    private static Method areShadersEnabledMethod;
    private static Constructor<?> shaderScreenConstructor;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            getIrisConfigMethod = irisClass.getMethod("getIrisConfig");
            Object irisConfig = getIrisConfigMethod.invoke(null);
            areShadersEnabledMethod = irisConfig.getClass().getMethod("areShadersEnabled");
            toggleShadersMethod = irisClass.getMethod("toggleShaders", MinecraftClient.class, boolean.class);

            Class<?> screenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            shaderScreenConstructor = screenClass.getConstructor(Screen.class);
        } catch (ClassNotFoundException e) {
            if (getIrisConfigMethod != null) {
                BBSPlusPlusMod.LOGGER.warn("Iris 兼容：找不到光影选择界面", e);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BBSPlusPlusMod.LOGGER.warn("Iris 兼容初始化失败", e);
        }
    }

    /**
     * 通过反射调用 Iris 内部方法来开启或关闭着色器
     */
    public static void toggleShaders() {
        init();
        try {
            if (getIrisConfigMethod != null && areShadersEnabledMethod != null && toggleShadersMethod != null) {
                Object irisConfig = getIrisConfigMethod.invoke(null);
                boolean enabled = (boolean) areShadersEnabledMethod.invoke(irisConfig);
                toggleShadersMethod.invoke(null, MinecraftClient.getInstance(), !enabled);
            }
        } catch (Exception e) {
            BBSPlusPlusMod.LOGGER.warn("Iris 兼容：切换光影失败", e);
        }
    }

    /**
     * 获取当前着色器是否开启
     */
    public static boolean isShadersEnabled() {
        init();
        try {
            if (getIrisConfigMethod != null && areShadersEnabledMethod != null) {
                Object irisConfig = getIrisConfigMethod.invoke(null);
                return (boolean) areShadersEnabledMethod.invoke(irisConfig);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!shaderStateErrorLogged) {
                shaderStateErrorLogged = true;
                BBSPlusPlusMod.LOGGER.warn("Iris 兼容：读取光影状态失败，后续同类错误不再重复输出", e);
            }
        }
        return false;
    }

    /**
     * 通过反射直接实例化 ShaderPackScreen 并延迟显示
     */
    public static void openShaderScreen() {
        init();
        try {
            if (shaderScreenConstructor != null) {
                Object screen = shaderScreenConstructor.newInstance(MinecraftClient.getInstance().currentScreen);
                // 延迟到下一帧切换界面，避免在 UI 事件分发循环中破坏迭代器导致主线程死锁
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().setScreen((Screen) screen);
                });
            }
        } catch (Exception e) {
            BBSPlusPlusMod.LOGGER.warn("Iris 兼容：打开光影选择界面失败", e);
        }
    }
}
