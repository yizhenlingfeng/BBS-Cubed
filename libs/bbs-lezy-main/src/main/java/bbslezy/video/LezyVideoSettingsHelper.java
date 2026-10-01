package bbslezy.video;

import bbslezy.utils.LezyOS;
import bbslod.LodSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

public class LezyVideoSettingsHelper
{
    private static final Logger LOG = LogManager.getLogger("bbslezy");

    public static volatile boolean forceCpuOnce = false;

    public static String apply(String params)
    {
        if (params == null || params.isEmpty())
        {
            return params;
        }

        int cqp = LodSettings.videoCqp != null ? LodSettings.videoCqp.get() : 18;
        int codecMode = LodSettings.videoCodec != null ? LodSettings.videoCodec.get() : 0;
        boolean hwAccel = LodSettings.hardwareAcceleration == null || LodSettings.hardwareAcceleration.get();
        int gpuMode = LodSettings.gpuVendor != null ? LodSettings.gpuVendor.get() : 0;

        int detectedGpu = detectGpu(gpuMode);

        if (forceCpuOnce || !hwAccel)
        {
            forceCpuOnce = false;
            return applyCpuEncoding(params, codecMode, cqp);
        }

        if (LezyOS.isLinuxLike() && LezyEncoderProbe.isProbeDone() && LezyEncoderProbe.probeSucceeded)
        {
            return applyLinuxEncoding(params, codecMode, detectedGpu, cqp);
        }

        return applyGpuEncoding(params, codecMode, detectedGpu, cqp);
    }

    public static boolean isHwAccelUnsupported()
    {
        if (forceCpuOnce)
        {
            return false;
        }

        boolean hwAccel = LodSettings.hardwareAcceleration == null || LodSettings.hardwareAcceleration.get();

        if (!hwAccel)
        {
            return false;
        }

        int codecMode = LodSettings.videoCodec != null ? LodSettings.videoCodec.get() : 0;

        /* VP9 (codecMode == 2) has no hardware encoder in NVIDIA (NVENC), AMD (AMF), or Intel consumer GPUs. */
        if (codecMode == 2)
        {
            return true;
        }

        if (LezyOS.isLinuxLike() && LezyEncoderProbe.isProbeDone() && LezyEncoderProbe.probeSucceeded)
        {
            int gpuMode = LodSettings.gpuVendor != null ? LodSettings.gpuVendor.get() : 0;
            int detectedGpu = detectGpu(gpuMode);

            if (!linuxEncoderAvailableFor(detectedGpu, codecMode))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean linuxEncoderAvailableFor(int gpu, int codecMode)
    {
        if (gpu == 1)
        {
            return codecMode == 1 ? LezyEncoderProbe.nvencHevc : LezyEncoderProbe.nvencH264;
        }
        else if (gpu == 2)
        {
            return codecMode == 1 ? LezyEncoderProbe.vaapiHevc : LezyEncoderProbe.vaapiH264;
        }
        else if (gpu == 3)
        {
            if (codecMode == 1)
            {
                return LezyEncoderProbe.vaapiHevc || LezyEncoderProbe.qsvHevc;
            }

            return LezyEncoderProbe.vaapiH264 || LezyEncoderProbe.qsvH264;
        }

        return false;
    }

    public static String getGpuName()
    {
        int gpuMode = LodSettings.gpuVendor != null ? LodSettings.gpuVendor.get() : 0;
        int detectedGpu = detectGpu(gpuMode);

        if (LezyOS.isLinuxLike() && LezyEncoderProbe.isProbeDone() && LezyEncoderProbe.probeSucceeded)
        {
            switch (detectedGpu)
            {
                case 1: return "NVIDIA (NVENC)";
                case 2: return "AMD (VA-API)";
                case 3: return "Intel (QSV/VA-API)";
                default: return "GPU";
            }
        }

        switch (detectedGpu)
        {
            case 1: return "NVIDIA (NVENC)";
            case 2: return "AMD (AMF)";
            case 3: return "Intel (QSV)";
            default: return "GPU";
        }
    }

    public static String getCodecName()
    {
        int codecMode = LodSettings.videoCodec != null ? LodSettings.videoCodec.get() : 0;

        switch (codecMode)
        {
            case 1: return "H.265 / HEVC (MP4)";
            case 2: return "VP9 (WebM)";
            default: return "H.264 (MP4)";
        }
    }

    static int detectGpu(int gpuMode)
    {
        if (gpuMode != 0)
        {
            return gpuMode; // 1 = NVIDIA, 2 = AMD, 3 = Intel
        }

        try
        {
            if (org.lwjgl.opengl.GL.getCapabilities() != null)
            {
                String vendor = (GL11.glGetString(GL11.GL_VENDOR) + " " + GL11.glGetString(GL11.GL_RENDERER)).toLowerCase();

                if (vendor.contains("nvidia") || vendor.contains("geforce") || vendor.contains("quadro"))
                {
                    return 1;
                }

                if (vendor.contains("amd") || vendor.contains("ati") || vendor.contains("radeon"))
                {
                    return 2;
                }

                if (vendor.contains("intel") || vendor.contains("uhd") || vendor.contains("iris") || vendor.contains("arc"))
                {
                    return 3;
                }
            }
        }
        catch (Throwable ignored)
        {}
        if (LezyOS.isLinuxLike() && LezyEncoderProbe.detectedNodeVendor != 0)
        {
            return LezyEncoderProbe.detectedNodeVendor;
        }

        return 1; // Default to NVIDIA if cannot query OpenGL string
    }

    private static String applyLinuxEncoding(String params, int codecMode, int gpu, int cqp)
    {
        if (codecMode == 2)
        {
            return applyGpuEncoding(params, codecMode, gpu, cqp);
        }

        if (gpu == 1)
        {
            boolean ok = codecMode == 1 ? LezyEncoderProbe.nvencHevc : LezyEncoderProbe.nvencH264;

            if (!ok)
            {
                LOG.info("encoder: CPU fallback (NVIDIA NVENC unavailable for codec {})", codecMode);
                return applyCpuEncoding(params, codecMode, cqp);
            }

            String enc = codecMode == 1 ? "hevc_nvenc" : "h264_nvenc";
            LOG.info("encoder: {} (probe=linux)", enc);

            return applyGpuEncoding(params, codecMode, 1, cqp);
        }

        if (gpu == 2)
        {
            boolean ok = codecMode == 1 ? LezyEncoderProbe.vaapiHevc : LezyEncoderProbe.vaapiH264;

            if (!ok)
            {
                LOG.info("encoder: CPU fallback (AMD VA-API unavailable for codec {})", codecMode);
                return applyCpuEncoding(params, codecMode, cqp);
            }

            return applyVaapiEncoding(params, codecMode, cqp);
        }

        if (gpu == 3)
        {
            boolean qsvOk = codecMode == 1 ? LezyEncoderProbe.qsvHevc : LezyEncoderProbe.qsvH264;

            if (qsvOk)
            {
                return applyLinuxQsvEncoding(params, codecMode, cqp);
            }

            boolean vaapiOk = codecMode == 1 ? LezyEncoderProbe.vaapiHevc : LezyEncoderProbe.vaapiH264;

            if (vaapiOk)
            {
                return applyVaapiEncoding(params, codecMode, cqp);
            }

            LOG.info("encoder: CPU fallback (Intel QSV/VA-API unavailable for codec {})", codecMode);
            return applyCpuEncoding(params, codecMode, cqp);
        }

        return applyCpuEncoding(params, codecMode, cqp);
    }

    private static String applyVaapiEncoding(String params, int codecMode, int cqp)
    {
        String device = LezyEncoderProbe.vaapiDevice;

        if (device == null || device.isEmpty() || !params.contains("-vf %FILTERS%"))
        {
            LOG.info("encoder: CPU fallback (VA-API missing device or -vf %FILTERS% token)");
            return applyCpuEncoding(params, codecMode, cqp);
        }

        params = stripCpuTuningParams(params);
        params = params.replaceAll("-pix_fmt (?!bgr24)\\S+", "");

        params = params.replace("-vf %FILTERS%", "-vaapi_device " + device + " -vf %FILTERS%,format=nv12,hwupload");

        String encoder = codecMode == 1 ? "hevc_vaapi" : "h264_vaapi";
        String encoderArgs = codecMode == 1
            ? "-c:v hevc_vaapi -qp " + cqp + " -tag:v hvc1"
            : "-c:v h264_vaapi -qp " + cqp;

        params = params.replaceAll("-c:v \\S+", encoderArgs);
        LOG.info("encoder: {} device={} (probe=linux)", encoder, device);

        return params.replaceAll("\\s+", " ").trim();
    }

    private static String applyLinuxQsvEncoding(String params, int codecMode, int cqp)
    {
        String device = LezyEncoderProbe.qsvDevice;

        if (device == null || device.isEmpty() || !params.contains("-vf %FILTERS%"))
        {
            LOG.info("encoder: CPU fallback (QSV missing device or -vf %FILTERS% token)");
            return applyCpuEncoding(params, codecMode, cqp);
        }

        params = stripCpuTuningParams(params);

        params = params.replace("-vf %FILTERS%", "-qsv_device " + device + " -vf %FILTERS%");

        String encoder = codecMode == 1 ? "hevc_qsv" : "h264_qsv";
        String encoderArgs = codecMode == 1
            ? "-c:v hevc_qsv -global_quality " + cqp + " -tag:v hvc1"
            : "-c:v h264_qsv -global_quality " + cqp;

        params = params.replaceAll("-c:v \\S+", encoderArgs);
        LOG.info("encoder: {} device={} (probe=linux)", encoder, device);

        return params.replaceAll("\\s+", " ").trim();
    }

    private static String applyGpuEncoding(String params, int codecMode, int gpu, int cqp)
    {
        params = stripCpuTuningParams(params);

        String encoderArgs;

        if (gpu == 1) // NVIDIA NVENC
        {
            if (codecMode == 1)
            {
                encoderArgs = "-c:v hevc_nvenc -preset p4 -cq " + cqp + " -tag:v hvc1";
            }
            else if (codecMode == 2)
            {
                encoderArgs = "-c:v libvpx-vp9 -b:v 0 -deadline realtime -crf " + cqp;
                params = params.replaceAll("%NAME%\\.mp4", "%NAME%.webm");
            }
            else
            {
                encoderArgs = "-c:v h264_nvenc -preset p4 -cq " + cqp;
            }
        }
        else if (gpu == 2) // AMD AMF
        {
            if (codecMode == 1)
            {
                encoderArgs = "-c:v hevc_amf -rc cqp -qp_i " + cqp + " -qp_p " + cqp + " -tag:v hvc1";
            }
            else if (codecMode == 2)
            {
                encoderArgs = "-c:v libvpx-vp9 -b:v 0 -deadline realtime -crf " + cqp;
                params = params.replaceAll("%NAME%\\.mp4", "%NAME%.webm");
            }
            else
            {
                encoderArgs = "-c:v h264_amf -rc cqp -qp_i " + cqp + " -qp_p " + cqp;
            }
        }
        else // Intel QSV
        {
            if (codecMode == 1)
            {
                encoderArgs = "-c:v hevc_qsv -global_quality " + cqp + " -tag:v hvc1";
            }
            else if (codecMode == 2)
            {
                encoderArgs = "-c:v libvpx-vp9 -b:v 0 -deadline realtime -crf " + cqp;
                params = params.replaceAll("%NAME%\\.mp4", "%NAME%.webm");
            }
            else
            {
                encoderArgs = "-c:v h264_qsv -global_quality " + cqp;
            }
        }

        params = params.replaceAll("-c:v \\S+", encoderArgs);

        return params.replaceAll("\\s+", " ").trim();
    }

    private static String stripCpuTuningParams(String params)
    {
        return params
            .replaceAll("-preset \\S+", "")
            .replaceAll("-tune \\S+", "")
            .replaceAll("-qp \\d+", "")
            .replaceAll("-crf \\d+", "");
    }

    private static String applyCpuEncoding(String params, int codecMode, int cqp)
    {
        /* Replace CQP / CRF parameter */
        if (params.contains("-qp "))
        {
            params = params.replaceAll("-qp \\d+", "-qp " + cqp);
        }
        else if (params.contains("-crf "))
        {
            params = params.replaceAll("-crf \\d+", "-crf " + cqp);
        }

        if (codecMode == 1)
        {
            params = params.replaceAll("-c:v \\S+", "-c:v libx265 -tag:v hvc1");
        }
        else if (codecMode == 2)
        {
            params = params.replaceAll("-c:v \\S+", "-c:v libvpx-vp9 -b:v 0");
            params = params.replaceAll("-tune zerolatency", "-deadline realtime");
            params = params.replaceAll("-qp \\d+", "-crf " + cqp);
            params = params.replaceAll("%NAME%\\.mp4", "%NAME%.webm");
        }
        else
        {
            params = params.replaceAll("-c:v \\S+", "-c:v libx264");
        }

        return params.replaceAll("\\s+", " ").trim();
    }
}
