package bbslod;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The addon's client settings, assigned in the settings consumer (BBS's own pattern, cf.
 * {@code BBSSettings}). The file lands at {@code <bbs settings folder>/bbslezy.json} and is edited
 * through BBS's own settings screen — no UI work in this addon.
 */
public class LodSettings
{
    public static ValueBoolean enabled;
    public static ValueInt renderLimit;
    public static ValueDouble focusDistance;
    public static ValueBoolean separateAudioTracks;
    public static ValueBoolean openFolderOnImport;
    public static ValueInt bakingBatchPercent;
    public static ValueInt videoCqp;
    public static ValueInt videoCodec;
    public static ValueBoolean hardwareAcceleration;
    public static ValueInt gpuVendor;

    public static void register(SettingsBuilder builder)
    {
        builder.category("general", Icons.GEAR);

        enabled = builder.getBoolean("enabled", true);
        renderLimit = builder.getInt("render_limit", 100, 0, 2000);
        focusDistance = builder.getDouble("focus_distance", 0D, 0D, 256D);
        separateAudioTracks = builder.getBoolean("separate_audio_tracks", false);
        openFolderOnImport = builder.getBoolean("open_folder_on_import", false);
        bakingBatchPercent = builder.getInt("baking_batch_percent", 5, 1, 100);
        videoCqp = builder.getInt("video_cqp", 18, 0, 51).slider();
        videoCodec = builder.getInt("video_codec", 0, 0, 2).modes(
            IKey.raw("H.264 (MP4)"),
            IKey.raw("H.265 / HEVC (MP4)"),
            IKey.raw("VP9 (WebM)")
        );
        hardwareAcceleration = builder.getBoolean("hardware_acceleration", true);
        gpuVendor = builder.getInt("gpu_vendor", 0, 0, 3).modes(
            IKey.raw("Auto-Detect GPU"),
            IKey.raw("NVIDIA (NVENC)"),
            IKey.raw("AMD (AMF)"),
            IKey.raw("Intel (QSV)")
        );
    }
}
