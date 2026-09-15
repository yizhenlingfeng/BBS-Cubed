package gbeic.bbsplusplus.util;

import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerManager;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.internal.CollisionCallbackSupport;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import gbeic.bbsplusplus.BBSPlusPlusMod;

/**
 * X-Ray 管理器，用于处理穿透方块的粒子效果渲染。
*/

public class XRayManager {

    private static EffekseerManager XRAY_MANAGER = null;

    /* Unsafe 实例及字段偏移量缓存：AAA Particles 2.2.3 起 ParticleEmitter.manager / handle
     * 字段变为 private final，普通 Field.set() 在 Java 17 下无法可靠修改，
     * 必须通过 Unsafe 直接写内存才能保证 X-Ray 管理器迁移生效。 */
    private static final Unsafe UNSAFE = getUnsafe();
    private static final long MANAGER_FIELD_OFFSET = fieldOffset(ParticleEmitter.class, "manager");
    private static final long HANDLE_FIELD_OFFSET = fieldOffset(ParticleEmitter.class, "handle");
    private static final long THE_ONE_MANAGERS_OFFSET = staticFieldOffset(
            mod.chloeprime.aaaparticles.api.client.EffectDefinition.class, "THE_ONE_MANAGERS");
    private static final Object THE_ONE_MANAGERS_BASE = staticFieldBase(
            mod.chloeprime.aaaparticles.api.client.EffectDefinition.class, "THE_ONE_MANAGERS");

    /**
     * X-Ray 管理器是否已被创建。
     *
     * <p>{@link #get()} 只会在某个粒子被 {@link #migrate} 标记为穿透渲染
     * （{@code shouldBeXRay=true}）时被调用，因此本方法返回 {@code true}
     * 等价于「本次会话确实启用过穿透粒子」。</p>
     *
     * <p>供 {@code EffectDefinitionMixin} 做前置判断：没有穿透粒子时，
     * 不必为它付出改动全局 GL 深度状态与当前 Framebuffer 深度缓冲的代价。</p>
     */
    public static boolean isActive()
    {
        return XRAY_MANAGER != null;
    }

    public static EffekseerManager get() {
        if (XRAY_MANAGER == null) {
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
            XRAY_MANAGER = new EffekseerManager();
            if (!XRAY_MANAGER.init(100000)) {
                BBSPlusPlusMod.LOGGER.warn("[XRayManager] XRAY EffekseerManager 初始化失败");
            }
            XRAY_MANAGER.setCollisionCallback(CollisionCallbackSupport.Impl.DEFAULT_TRACER);
            XRAY_MANAGER.setupWorkerThreads(2);
        }
        return XRAY_MANAGER;
    }

    /**
     * 关闭 X-Ray 管理器，释放其持有的 EffekseerManager 原生资源。
     * <p>
     * 必须在游戏退出时调用，否则 Effekseer 原生 DLL 会在 JVM 卸载时因悬挂资源
     * 触发 STATUS_STACK_BUFFER_OVERRUN（退出码 -1073740791）。
     * </p>
     */
    public static void shutdown() {
        if (XRAY_MANAGER != null) {
            try {
                XRAY_MANAGER.stopAllEffects();
                XRAY_MANAGER.close();
            } catch (Exception e) {
                BBSPlusPlusMod.LOGGER.warn("[XRayManager] 关闭 XRay 管理器失败", e);
            }
            XRAY_MANAGER = null;
        }
    }

    public static void migrate(ParticleEmitter emitter, boolean shouldBeXRay, mod.chloeprime.aaaparticles.api.client.EffectDefinition effectDef, ParticleEmitter.Type targetType) {
        try {
            if (UNSAFE == null || MANAGER_FIELD_OFFSET < 0 || HANDLE_FIELD_OFFSET < 0) {
                BBSPlusPlusMod.LOGGER.warn("[XRayManager] Unsafe 或字段偏移量未就绪，跳过管理器迁移");
                return;
            }

            EffekseerManager currentManager = (EffekseerManager) UNSAFE.getObject(emitter, MANAGER_FIELD_OFFSET);
            EffekseerManager targetManager;

            if (shouldBeXRay) {
                targetManager = get();
            } else {
                targetManager = getGlobalManager(targetType);
            }

            if (currentManager == targetManager) {
                return;
            }

            int oldHandle = UNSAFE.getInt(emitter, HANDLE_FIELD_OFFSET);
            currentManager.getImpl().Stop(oldHandle);

            int newHandle = targetManager.getImpl().Play(effectDef.getEffect().getImpl());

            // 通过 Unsafe 直接写内存，绕过 final 字段的反射限制
            UNSAFE.putObject(emitter, MANAGER_FIELD_OFFSET, targetManager);
            UNSAFE.putInt(emitter, HANDLE_FIELD_OFFSET, newHandle);

        } catch (Exception e) {
            BBSPlusPlusMod.LOGGER.warn("[XRayManager] 迁移发射器管理器失败", e);
        }
    }

    /* ===== Unsafe 辅助方法 ===== */

    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            BBSPlusPlusMod.LOGGER.warn("[XRayManager] 获取 Unsafe 实例失败", e);
            return null;
        }
    }

    private static long fieldOffset(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return UNSAFE.objectFieldOffset(field);
        } catch (Exception e) {
            BBSPlusPlusMod.LOGGER.warn("[XRayManager] 获取字段偏移量失败 {}.{}", clazz.getSimpleName(), fieldName, e);
            return -1L;
        }
    }

    private static long staticFieldOffset(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return UNSAFE.staticFieldOffset(field);
        } catch (Exception e) {
            BBSPlusPlusMod.LOGGER.warn("[XRayManager] 获取静态字段偏移量失败 {}.{}", clazz.getSimpleName(), fieldName, e);
            return -1L;
        }
    }

    private static Object staticFieldBase(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return UNSAFE.staticFieldBase(field);
        } catch (Exception e) {
            BBSPlusPlusMod.LOGGER.warn("[XRayManager] 获取静态字段基址失败 {}.{}", clazz.getSimpleName(), fieldName, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static EffekseerManager getGlobalManager(ParticleEmitter.Type type) {
        if (THE_ONE_MANAGERS_BASE == null || THE_ONE_MANAGERS_OFFSET < 0) {
            // 回退到反射方式
            try {
                Field theOneManagersField = mod.chloeprime.aaaparticles.api.client.EffectDefinition.class.getDeclaredField("THE_ONE_MANAGERS");
                theOneManagersField.setAccessible(true);
                java.util.function.Supplier<?> supplier = (java.util.function.Supplier<?>) theOneManagersField.get(null);
                java.util.EnumMap<ParticleEmitter.Type, EffekseerManager> map =
                        (java.util.EnumMap<ParticleEmitter.Type, EffekseerManager>) supplier.get();
                return map.get(type);
            } catch (Exception e) {
                BBSPlusPlusMod.LOGGER.warn("[XRayManager] 创建/获取 EffekseerManager 失败", e);
                return null;
            }
        }

        java.util.function.Supplier<?> supplier = (java.util.function.Supplier<?>) UNSAFE.getObject(THE_ONE_MANAGERS_BASE, THE_ONE_MANAGERS_OFFSET);
        java.util.EnumMap<ParticleEmitter.Type, EffekseerManager> map =
                (java.util.EnumMap<ParticleEmitter.Type, EffekseerManager>) supplier.get();
        return map.get(type);
    }
}
