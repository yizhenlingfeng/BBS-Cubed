package gbeic.bbsplusplus.client.renderer;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * MediaPlayer-BBS 的反射桥。
 *
 * BBS++ 只把视频后端视为可选依赖，因此这里集中处理类查找、方法调用和失败原因。
 * 这样主工程无需在编译期依赖 MediaPlayer-BBS，也能在未安装后端时正常启动。
 */
public final class VideoBackendBridge
{
    private static boolean resolved;
    private static boolean available;
    private static String unavailableReason = "未检测到 MediaPlayer-BBS";
    private static Class<?> backendClass;
    private static Method isAvailableMethod;
    private static Method getUnavailableReasonMethod;
    private static Method openAssetVideoMethod;
    /** 时间轴寻帧启用所需的最低 MediaPlayer-BBS 版本：1.0.1 的 native 寻帧接口存在 use-after-free 崩溃。 */
    private static final String TIMELINE_SEEK_MIN_VERSION = "1.0.2";
    private static Boolean timelineSeekAllowed;
    private static String timelineSeekVersion;

    private VideoBackendBridge()
    {
    }

    public static boolean isAvailable()
    {
        resolve();

        return available;
    }

    public static String getUnavailableReason()
    {
        resolve();

        return unavailableReason;
    }

    public static DecoderHandle openAssetVideo(String relativePath)
    {
        resolve();

        if (!available)
        {
            throw new IllegalStateException(unavailableReason);
        }

        try
        {
            return new DecoderHandle(openAssetVideoMethod.invoke(null, relativePath));
        }
        catch (Throwable e)
        {
            throw new RuntimeException("打开视频后端失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按 MediaPlayer-BBS 的版本决定是否启用 native 时间轴寻帧（拖动播放头逐帧精确）。
     * <p>
     * 1.0.1 的 MediaPlayer.dll 在时间轴寻帧时会对已释放的互斥量加锁（msvcp140.dll 中
     * 访问违规），直接崩掉整个 JVM；该崩溃已在修复构建中解决，约定修复构建从 1.0.2 起
     * 编号。版本读取失败或低于 1.0.2 时按禁用处理（安全侧）。结果只解析一次。
     * </p>
     */
    public static boolean shouldUseTimelineSeek()
    {
        if (timelineSeekAllowed == null)
        {
            timelineSeekAllowed = resolveTimelineSeekAllowed();

            if (!timelineSeekAllowed)
            {
                BBSPlusPlusMod.LOGGER.info("MediaPlayer-BBS {} 的时间轴寻帧已禁用（1.0.1 的 native 寻帧会崩溃），"
                    + "升级到 " + TIMELINE_SEEK_MIN_VERSION + "+ 后自动启用", timelineSeekVersion);
            }
        }

        return timelineSeekAllowed;
    }

    private static boolean resolveTimelineSeekAllowed()
    {
        if (!FabricLoader.getInstance().isModLoaded("mediaplayer"))
        {
            timelineSeekVersion = "未安装";
            return false;
        }

        try
        {
            Version version = FabricLoader.getInstance()
                .getModContainer("mediaplayer")
                .map(container -> container.getMetadata().getVersion())
                .orElse(null);

            timelineSeekVersion = version == null ? "未知" : version.getFriendlyString();

            return version != null && version.compareTo(Version.parse(TIMELINE_SEEK_MIN_VERSION)) >= 0;
        }
        catch (Exception e)
        {
            timelineSeekVersion = "未知";
            return false;
        }
    }

    /**
     * 启用 MediaPlayer-BBS 的 native 时间轴寻帧接口（{@code VideoDecoder.renderTimeNative}）。
     * 该接口由 native 代码在解码器打开后把静态开关置为可用，这里在每次打开解码器后
     * 显式改回 true，覆盖可能的旧状态。字段随版本变化时反射失败会被忽略。
     */
    public static void enableTimelineSeek()
    {
        setTimelineNativeAvailable(true);
    }

    /**
     * 禁用 MediaPlayer-BBS 的 native 时间轴寻帧接口，让 {@code renderTime} 走连续解码路径。
     * 用于 1.0.1 及更早版本（其 native 寻帧会在 msvcp140.dll 中触发访问违规崩溃）。
     * 字段随版本变化时反射失败会被忽略，保持后端默认行为。
     */
    public static void disableTimelineSeek()
    {
        setTimelineNativeAvailable(false);
    }

    private static void setTimelineNativeAvailable(boolean value)
    {
        try
        {
            Class<?> decoderClass = Class.forName("net.hacker.mediaplayer.VideoDecoder");
            Field field = decoderClass.getDeclaredField("timelineNativeAvailable");
            field.setAccessible(true);
            field.setBoolean(null, value);
        }
        catch (Exception ignored)
        {
        }
    }

    private static void resolve()
    {
        if (resolved)
        {
            return;
        }

        resolved = true;

        if (!FabricLoader.getInstance().isModLoaded("mediaplayer"))
        {
            available = false;
            unavailableReason = "未检测到 MediaPlayer-BBS";
            return;
        }

        try
        {
            backendClass = Class.forName("net.hacker.mediaplayer.BBSVideoBackend");
            isAvailableMethod = backendClass.getMethod("isAvailable");
            getUnavailableReasonMethod = backendClass.getMethod("getUnavailableReason");
            openAssetVideoMethod = backendClass.getMethod("openAssetVideo", String.class);

            available = Boolean.TRUE.equals(isAvailableMethod.invoke(null));
            Object reason = getUnavailableReasonMethod.invoke(null);
            unavailableReason = reason instanceof String ? (String) reason : "";
        }
        catch (Exception e)
        {
            available = false;
            unavailableReason = "MediaPlayer-BBS API 不可用: " + e.getMessage();
        }
    }

    public static final class DecoderHandle implements AutoCloseable
    {
        private final Object decoder;
        /** 每帧调用的渲染句柄，使用 MethodHandle 避免逐帧反射开销。 */
        private final MethodHandle renderTime;
        private final MethodHandle getTextureId;
        private final MethodHandle close;
        private final Object metadata;
        private final MethodHandle width;
        private final MethodHandle height;
        private final MethodHandle durationSeconds;

        private DecoderHandle(Object decoder) throws Throwable
        {
            this.decoder = decoder;
            Class<?> decoderClass = decoder.getClass();
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            this.renderTime = lookup.unreflect(decoderClass.getMethod("renderTime", double.class));
            this.getTextureId = lookup.unreflect(decoderClass.getMethod("getTextureId"));
            this.close = lookup.unreflect(decoderClass.getMethod("close"));
            this.metadata = lookup.unreflect(decoderClass.getMethod("getMetadata")).invoke(decoder);

            Class<?> metadataClass = this.metadata.getClass();
            this.width = lookup.unreflect(metadataClass.getMethod("width"));
            this.height = lookup.unreflect(metadataClass.getMethod("height"));
            this.durationSeconds = lookup.unreflect(metadataClass.getMethod("durationSeconds"));
        }

        public void renderTime(double seconds)
        {
            try
            {
                this.renderTime.invoke(this.decoder, seconds);
            }
            catch (Throwable e)
            {
                throw new RuntimeException("视频寻帧失败: " + e.getMessage(), e);
            }
        }

        public int getTextureId()
        {
            try
            {
                return (int) this.getTextureId.invoke(this.decoder);
            }
            catch (Throwable e)
            {
                return 0;
            }
        }

        public int getWidth()
        {
            return this.getInt(this.width);
        }

        public int getHeight()
        {
            return this.getInt(this.height);
        }

        public double getDurationSeconds()
        {
            try
            {
                return (double) this.durationSeconds.invoke(this.metadata);
            }
            catch (Throwable e)
            {
                return 0D;
            }
        }

        private int getInt(MethodHandle method)
        {
            try
            {
                return (int) method.invoke(this.metadata);
            }
            catch (Throwable e)
            {
                return 0;
            }
        }

        @Override
        public void close()
        {
            try
            {
                this.close.invoke(this.decoder);
            }
            catch (Throwable ignored)
            {
            }
        }
    }
}
