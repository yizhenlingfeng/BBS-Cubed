package gbeic.bbsplusplus;

import gbeic.bbsppp.BBSPPPSettings;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * BBS++ 独立设置模块的注册入口。
 * <p>
 * 设置模块内部分为三个分类，显示在设置界面的分类列表中：
 * <ul>
 *   <li>BBS 增强 — 界面与交互增强，含 BBSPPP 纹理补间与字幕导出</li>
 *   <li>物品喷射 — 物品喷射粒子渲染与性能</li>
 *   <li>Gizmo 改版 — Blockbench 风格 Gizmo 交互</li>
 * </ul>
 * </p>
 */
public class BBSPlusPlusSettings
{
    /**
     * 向独立的 BBS++ 设置模块注册全部分类和设置项。
     */
    public static void register(SettingsBuilder builder)
    {
        /* ===== 分类一：BBS 增强 ===== */
        builder.category("bbs_enhancements", Icons.DUPE);

        BBSAddonsSettings.chineseKeyframeNames = builder.getBoolean("chinese_keyframe_names", false);
        BBSAddonsSettings.filmAutoGameMode = builder.getBoolean("film_auto_game_mode", false);
        BBSAddonsSettings.preventNegativeKeyframes = builder.getBoolean("prevent_negative_keyframes", false);
        BBSAddonsSettings.reverseTimelineScroll = builder.getBoolean("reverse_timeline_scroll", false);
        BBSAddonsSettings.directParentPicking = builder.getBoolean("direct_parent_picking", false);
        BBSAddonsSettings.newMorphingPanel = builder.getBoolean("new_morphing_panel", false);
        BBSAddonsSettings.newFilmLibraryUi = builder.getBoolean("new_film_library_ui", false);
        BBSAddonsSettings.enableIrisButton = builder.getBoolean("enable_iris_button", false);
        BBSAddonsSettings.shaderCurvePicker = builder.getBoolean("shader_curve_picker", false);
        BBSAddonsSettings.enableUiKeyframesLayoutLock = builder.getBoolean("enable_ui_keyframes_layout_lock", false);
        BBSAddonsSettings.worldFilmShaderCurves = builder.getBoolean("world_film_shader_curves", false);
        BBSAddonsSettings.allowClipTrackExpansion = builder.getBoolean("allow_clip_track_expansion", false);
        BBSAddonsSettings.privateBbsClipboard = builder.getBoolean("private_bbs_clipboard", false);
        BBSAddonsSettings.filmAltWheelTimelineMode = builder.getInt("film_alt_wheel_timeline_mode", 0, 0, 2).modes(
            lang("bbspp.config.bbs_enhancements.film_alt_wheel_timeline_mode.default"),
            lang("bbspp.config.bbs_enhancements.film_alt_wheel_timeline_mode.disabled"),
            lang("bbspp.config.bbs_enhancements.film_alt_wheel_timeline_mode.horizontal_scroll")
        );

        /* BBSPPP 私有选项合并到 BBS 增强分类中 */
        BBSPPPSettings.exportAudioSubtitle = builder.getBoolean("export_audio_subtitle", false);

        /* 隐藏设置项（不显示在界面中，仅持久化） */
        BBSAddonsSettings.textureManagerLayout = (ValueInt) builder.getInt("texture_manager_layout", 0).invisible();
        BBSAddonsSettings.aaaEffectPickerWidth = (ValueInt) builder.getInt("aaa_effect_picker_width", 0, 0, 16384).invisible();
        BBSAddonsSettings.aaaEffectPickerHeight = (ValueInt) builder.getInt("aaa_effect_picker_height", 0, 0, 16384).invisible();
        BBSAddonsSettings.filmLibrarySortMode = (ValueInt) builder.getInt("film_library_sort_mode", 0, 0, 1).invisible();
        BBSAddonsSettings.filmLibraryDefaultLocation = (ValueString) builder.getString("film_library_default_location", "all").invisible();
        BBSAddonsSettings.morphingDefaultCategory = (ValueString) builder.getString("morphing_default_category", "home").invisible();

        /* ===== 分类二：物品喷射 ===== */
        builder.category("item_spray", Icons.DUPE);

        BBSAddonsSettings.itemSprayFrustumCulling = builder.getBoolean("item_spray_frustum_culling", true);
        BBSAddonsSettings.itemSprayMaxRenderDistance = builder.getInt("item_spray_max_render_distance", 0, 0, 512);
        BBSAddonsSettings.itemSprayMaxRenderedItems = builder.getInt("item_spray_max_rendered_items", 1024, 0, 8192);
        BBSAddonsSettings.itemSprayIRLiteShadowMaxItems = builder.getInt("item_spray_irlite_shadow_max_items", 1024, 0, 4096);

        /* ===== 分类三：Gizmo 改版 ===== */
        builder.category("gizmo_modifications", Icons.DUPE);

        BBSAddonsSettings.gizmoBlockbenchMode = builder.getBoolean("gizmo_blockbench_mode", false);
        BBSAddonsSettings.gizmoTCombined = builder.getBoolean("gizmo_t_combined", false);
        BBSAddonsSettings.gizmoKeepOriginal = builder.getBoolean("gizmo_keep_original", false);
    }

    private static IKey lang(String key)
    {
        return () -> BBSModClient.getL10n() == null ? key : L10n.lang(key).get();
    }
}
