package gbeic.bbsplusplus.client.compat.iris;

import gbeic.bbsplusplus.client.WorldFilmShaderCurveState;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;

import java.util.Map;
import java.util.Optional;

/**
 * 为 Iris 提供两条 BBS 原版没有的可动画光影参数：日月偏角与焦点。
 * <p>
 * 移植自 BBSTools 4.1。这两个值本身不是光影包里的宏，而是 Iris 自己算出来喂给着色器的量，
 * 所以没法走 ShaderCurves 那套宏替换，只能在 Iris 读取它们的位置把值换掉。
 * 本类负责从当前播放的曲线剪辑里取出这两个值，具体的替换由 {@code mixin.shadercurves} 包下的几个注入完成。
 * </p>
 * <p>
 * 相比 BBSTools 原版，这里额外接上了 BBS++ 的 {@link WorldFilmShaderCurveState}，
 * 因此右 Ctrl 在世界里播放影片时这两条曲线同样生效，而不只是在相机编辑器预览中生效。
 * </p>
 */
public final class ShaderCurveState
{
    /** 日月偏角曲线的通道 ID */
    public static final String SUN_PATH_ROTATION = "sun_path_rotation";

    /** 焦点曲线的通道 ID */
    public static final String CENTER_DEPTH = "center_depth";

    private static Float sunPathRotationValue;

    private ShaderCurveState()
    {}

    /** 外部强制指定日月偏角，传 {@code null} 表示回到跟随曲线 */
    public static void setSunPathRotationValue(Float value)
    {
        sunPathRotationValue = value;
    }

    /**
     * 取当前的日月偏角。
     *
     * @param fallback 曲线没有提供该参数时使用的原始值
     */
    public static float getSunPathRotation(float fallback)
    {
        if (sunPathRotationValue != null)
        {
            return sunPathRotationValue;
        }

        Double value = getCurveValue(SUN_PATH_ROTATION);

        return value == null ? fallback : value.floatValue();
    }

    /**
     * 焦点深度纹理。
     * <p>
     * 1.20.1 版这里会用 {@code GlStateManager._genTexture()} 建一张 1×1 的 R32F 纹理，
     * 再把曲线给的对焦距离反算成深度值，通过一个专用 FBO 把值 clear 进去，最后把 GL id 交给 Iris。
     * 那套 GL 状态机（{@code glGenFramebuffers} / {@code glFramebufferTexture2D} / {@code glDrawBuffers} /
     * {@code glClear}）在 1.21.11 已经没有对应 API，纹理与 FBO 的生命周期也改由 {@code GpuDevice} 管理，
     * 这里无法再用裸 GL id 交差。
     * </p>
     * <p>
     * 因此 1.21.11 上暂时恒返回空，让 Iris 回退到它自己采样的屏幕中心深度 —— 曲线里的
     * {@link #CENTER_DEPTH} 通道仍然能被编辑、也会进曲线剪辑数据，只是不再改写 Iris 的焦点。
     * 真正的替代实现（离屏 1×1 目标 + {@code RenderPass} 写入，再把 {@code GpuTextureView} 交给 Iris）
     * 记在施工表 B7-01，需要先确认 Iris 的注入点能否接受非裸 GL id。
     * </p>
     *
     * @return 恒为空，调用方回退到 Iris 原本的纹理
     */
    public static Optional<Integer> getCenterDepthTexture()
    {
        return Optional.empty();
    }

    /**
     * 读取当前帧某条曲线的值。
     * <p>
     * 优先取相机编辑器预览时的相机控制器上下文；没有的话再取 BBS++ 世界内播放影片时缓存的采样结果。
     * </p>
     */
    private static Double getCurveValue(String id)
    {
        ICameraController controller = BBSModClient.getCameraController().getCurrent();

        if (controller instanceof CameraWorkCameraController)
        {
            Map<String, Double> values = CurveClip.getValues(((CameraWorkCameraController) controller).getContext());
            Double value = values.get(id);

            if (value != null)
            {
                return value;
            }
        }

        return WorldFilmShaderCurveState.getValue(id);
    }
}
