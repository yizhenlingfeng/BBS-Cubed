package bbslezy.video;

import bbslezy.utils.LezyOS;
import mchorse.bbs_mod.utils.FFMpegUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LezyEncoderProbe
{
    private static final Logger LOG = LogManager.getLogger("bbslezy");
    private static final Pattern ENCODER_LINE = Pattern.compile("^\\s*[VA][\\w.]{5}\\s+(\\S+)");
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    static volatile boolean probeDone;
    static volatile boolean probeSucceeded;
    static volatile boolean nvencH264;
    static volatile boolean nvencHevc;
    static volatile boolean vaapiH264;
    static volatile boolean vaapiHevc;
    static volatile boolean qsvH264;
    static volatile boolean qsvHevc;
    static volatile String vaapiDevice;
    static volatile String qsvDevice;
    static volatile int detectedNodeVendor;
    public static boolean isProbeDone()
    {
        return probeDone;
    }

    public static void startProbeAsync()
    {
        if (!LezyOS.isLinuxLike())
        {
            return;
        }

        if (!STARTED.compareAndSet(false, true))
        {
            return;
        }

        final int glVendor = LezyVideoSettingsHelper.detectGpu(0);

        Thread thread = new Thread(() -> runProbe(glVendor), "BBS Lezy Encoder Probe");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runProbe(int glVendor)
    {
        try
        {
            String ffmpeg = resolveFfmpeg();
            Set<String> encoders = queryEncoders(ffmpeg);

            if (encoders.isEmpty())
            {
                probeSucceeded = false;
                probeDone = true;
                LOG.warn("ffmpeg encoder probe returned no encoders; keeping default heuristic");
                return;
            }

            nvencH264 = encoders.contains("h264_nvenc") && testDeviceEncode(ffmpeg, null, null, "h264_nvenc", false);
            nvencHevc = encoders.contains("hevc_nvenc") && testDeviceEncode(ffmpeg, null, null, "hevc_nvenc", false);

            List<String> nodes = findCandidateRenderNodes(glVendor);

            if (encoders.contains("h264_qsv") || encoders.contains("hevc_qsv"))
            {
                for (String node : nodes)
                {
                    boolean h264 = encoders.contains("h264_qsv") && testDeviceEncode(ffmpeg, "-qsv_device", node, "h264_qsv", false);
                    boolean hevc = encoders.contains("hevc_qsv") && testDeviceEncode(ffmpeg, "-qsv_device", node, "hevc_qsv", false);

                    if (h264 || hevc)
                    {
                        qsvDevice = node;
                        qsvH264 = h264;
                        qsvHevc = hevc;
                        break;
                    }
                }
            }

            if (encoders.contains("h264_vaapi") || encoders.contains("hevc_vaapi"))
            {
                for (String node : nodes)
                {
                    boolean h264 = encoders.contains("h264_vaapi") && testDeviceEncode(ffmpeg, "-vaapi_device", node, "h264_vaapi", true);
                    boolean hevc = encoders.contains("hevc_vaapi") && testDeviceEncode(ffmpeg, "-vaapi_device", node, "hevc_vaapi", true);

                    if (h264 || hevc)
                    {
                        vaapiDevice = node;
                        vaapiH264 = h264;
                        vaapiHevc = hevc;
                        break;
                    }
                }
            }
            probeSucceeded = true;
            probeDone = true;

            LOG.info(
                "ffmpeg encoder probe complete: nvenc(h264={}, hevc={}), vaapi(device={}, h264={}, hevc={}), qsv(device={}, h264={}, hevc={})",
                nvencH264, nvencHevc, vaapiDevice, vaapiH264, vaapiHevc, qsvDevice, qsvH264, qsvHevc
            );
        }
        catch (Throwable t)
        {
            probeSucceeded = false;
            probeDone = true;
            LOG.warn("ffmpeg encoder probe failed; keeping default heuristic", t);
        }
    }

    private static String resolveFfmpeg()
    {
        try
        {
            String path = FFMpegUtils.getFFMPEG();

            if (path != null && !path.isEmpty())
            {
                return path;
            }
        }
        catch (Throwable ignored)
        {}

        return "ffmpeg";
    }

    private static Set<String> queryEncoders(String ffmpeg) throws Exception
    {
        Process process = new ProcessBuilder(ffmpeg, "-hide_banner", "-encoders")
            .redirectErrorStream(true)
            .start();

        Set<String> encoders = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;

            while ((line = reader.readLine()) != null)
            {
                Matcher matcher = ENCODER_LINE.matcher(line);

                if (matcher.find())
                {
                    encoders.add(matcher.group(1));
                }
            }
        }

        process.waitFor(10, TimeUnit.SECONDS);

        return encoders;
    }

    private static List<String> findCandidateRenderNodes(int preferredVendor)
    {
        File driDir = new File("/dev/dri");
        File[] files = driDir.listFiles((dir, name) -> name.startsWith("renderD"));

        if (files == null || files.length == 0)
        {
            return List.of();
        }

        Arrays.sort(files, Comparator.comparingInt(LezyEncoderProbe::parseRenderNodeNumber));

        List<String> matching = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for (File file : files)
        {
            if (!file.canRead() || !file.canWrite())
            {
                LOG.warn("skipping DRM render node {} (insufficient permissions)", file.getAbsolutePath());
                continue;
            }

            int nodeVendor = readNodeVendor(file.getName());

            if (detectedNodeVendor == 0 && nodeVendor != 0)
            {
                detectedNodeVendor = nodeVendor;
            }

            if (preferredVendor != 0 && nodeVendor == preferredVendor)
            {
                matching.add(file.getAbsolutePath());
            }
            else
            {
                others.add(file.getAbsolutePath());
            }
        }

        List<String> result = new ArrayList<>(matching.size() + others.size());
        result.addAll(matching);
        result.addAll(others);

        return result;
    }

    private static int parseRenderNodeNumber(File file)
    {
        try
        {
            return Integer.parseInt(file.getName().substring("renderD".length()));
        }
        catch (NumberFormatException e)
        {
            return Integer.MAX_VALUE;
        }
    }

    private static int readNodeVendor(String nodeName)
    {
        try
        {
            Path vendorPath = Path.of("/sys/class/drm", nodeName, "device", "vendor");

            if (!Files.isReadable(vendorPath))
            {
                return 0;
            }

            String hex = Files.readString(vendorPath, StandardCharsets.UTF_8).trim().toLowerCase();

            if (hex.contains("0x10de"))
            {
                return 1;
            }

            if (hex.contains("0x1002"))
            {
                return 2;
            }

            if (hex.contains("0x8086"))
            {
                return 3;
            }
        }
        catch (Throwable ignored)
        {}

        return 0;
    }

    private static boolean testDeviceEncode(String ffmpeg, String deviceFlag, String node, String encoder, boolean vaapiUpload)
    {
        try
        {
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpeg);
            cmd.add("-hide_banner");
            if (deviceFlag != null && node != null)
            {
                cmd.add(deviceFlag);
                cmd.add(node);
            }

            cmd.add("-f");
            cmd.add("lavfi");
            cmd.add("-i");
            cmd.add("nullsrc=size=128x128:rate=25");
            cmd.add("-t");
            cmd.add("0.5");

            if (vaapiUpload)
            {
                cmd.add("-vf");
                cmd.add("format=nv12,hwupload");
            }

            cmd.add("-c:v");
            cmd.add(encoder);
            cmd.add("-f");
            cmd.add("null");
            cmd.add("-");

            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            process.getInputStream().readAllBytes();

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (!finished)
            {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    static void resetForTests()
    {
        probeDone = false;
        probeSucceeded = false;
        nvencH264 = false;
        nvencHevc = false;
        vaapiH264 = false;
        vaapiHevc = false;
        qsvH264 = false;
        qsvHevc = false;
        vaapiDevice = null;
        qsvDevice = null;
        detectedNodeVendor = 0;
    }
}
