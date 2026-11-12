package gbeic.bbsplusplus;

import gbeic.bbsplusplus.settings.ModSettings;
import gbeic.bbsplusplus.util.keybind.KeybindConflictSettings;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * BBS Cubed 独立设置模块的注册入口。
 * <p>
 * 设置模块内部分为 7 个功能分类，显示在设置界面的分类列表中：
 * <ul>
 *   <li>时间轴与关键帧 — 时间轴导航、关键帧边界与剪辑轨道</li>
 *   <li>姿势与动画 — 骨骼、姿势、IK、锚点与动画状态读取</li>
 *   <li>光影 — Iris 入口、世界播放与光影曲线选择</li>
 *   <li>界面与交互 — 界面布局、布局锁定、快捷键提示、编辑会话与剪贴板</li>
 *   <li>渲染与性能 — 物品喷射、模型方块与其他渲染开销</li>
 *   <li>操控器与建模 — Blockbench 外部编辑与操控器交互</li>
 *   <li>导出 — Premiere 工程、音频与字幕导出</li>
 * </ul>
 * </p>
 */
public class BBSPlusPlusSettings
{
    /**
     * 向独立的 BBS Cubed 设置模块注册全部分类和设置项。
     */
    public static void register(SettingsBuilder builder)
    {
        /* ===== 时间轴与关键帧 ===== */
        builder.category("timeline_keyframes", Icons.TIME);

        ModSettings.chineseKeyframeNames = builder.getBoolean("chinese_keyframe_names", false);
        ModSettings.preventNegativeKeyframes = builder.getBoolean("prevent_negative_keyframes", false);
        ModSettings.reverseTimelineScroll = builder.getBoolean("reverse_timeline_scroll", false);
        ModSettings.filmAltWheelTimelineMode = builder.getInt("film_alt_wheel_timeline_mode", 0, 0, 2).modes(
            lang("bbspp.config.timeline_keyframes.film_alt_wheel_timeline_mode.default"),
            lang("bbspp.config.timeline_keyframes.film_alt_wheel_timeline_mode.disabled"),
            lang("bbspp.config.timeline_keyframes.film_alt_wheel_timeline_mode.horizontal_scroll")
        );
        ModSettings.allowClipTrackExpansion = builder.getBoolean("allow_clip_track_expansion", false);
        ModSettings.snowActions = builder.getBoolean("snow_actions", false);

        /* 隐藏设置项（不显示在界面中，仅持久化） */
        ModSettings.keyframeEditorTimelineRatio = builder.getFloat("keyframe_editor_timeline_ratio", 0.65F, 0.2F, 0.8F);
        ModSettings.keyframeEditorTimelineRatio.invisible();
        ModSettings.replayTrackOrder = (ValueString) builder.getString("replay_track_order", "").invisible();

        /* ===== 姿势与动画 ===== */
        builder.category("pose_animation", Icons.POSE);

        ModSettings.directParentPicking = builder.getBoolean("direct_parent_picking", false);
        ModSettings.ikTargetCycleConstraint = builder.getBoolean("ik_target_cycle_constraint", true);
        ModSettings.pivotTransform = builder.getBoolean("pivot_transform", true);
        ModSettings.transformTranslateStep = builder.getFloat("transform_translate_step", ModSettings.DEFAULT_TRANSFORM_TRANSLATE_STEP, 0.001F, 1F);
        ModSettings.transformScaleStep = builder.getFloat("transform_scale_step", ModSettings.DEFAULT_TRANSFORM_SCALE_STEP, 0.001F, 4F);
        ModSettings.transformRotateStep = builder.getFloat("transform_rotate_step", ModSettings.DEFAULT_TRANSFORM_ROTATE_STEP, 0.01F, 180F);
        ModSettings.transformPivotStep = builder.getFloat("transform_pivot_step", ModSettings.DEFAULT_TRANSFORM_PIVOT_STEP, 0.001F, 1F);
        ModSettings.pickLimbTexture = builder.getBoolean("pick_limb_texture", true);
        ModSettings.autoLoadAnimationFile = builder.getBoolean("auto_load_animation_file", true);

        /* 隐藏设置项（不显示在界面中，仅持久化） */
        ModSettings.bonePriorityExpandedLimb = builder.getBoolean("bone_priority_expanded_limb", false);
        ModSettings.bonePriorityExpandedLimb.invisible();
        ModSettings.bonePriorityTrack = builder.getString("bone_priority_track", "");
        ModSettings.bonePriorityTrack.invisible();
        ModSettings.animationStateLayout = builder.getString("animation_state_layout", "");
        ModSettings.animationStateLayout.invisible();
        ModSettings.animationStateHiddenPanels = builder.getString("animation_state_hidden_panels", "");
        ModSettings.animationStateHiddenPanels.invisible();

        /* ===== 光影 ===== */
        builder.category("shaders", Icons.SUN);

        ModSettings.enableIrisButton = builder.getBoolean("enable_iris_button", false);
        ModSettings.worldFilmShaderCurves = builder.getBoolean("world_film_shader_curves", false);
        ModSettings.shaderCurvePicker = builder.getBoolean("shader_curve_picker", false);

        /* ===== 界面与交互 ===== */
        builder.category("interface_interaction", Icons.LAYOUT);

        ModSettings.newMorphingPanel = builder.getBoolean("new_morphing_panel", false);
        ModSettings.newFilmLibraryUi = builder.getBoolean("new_film_library_ui", false);
        ModSettings.enableUiKeyframesLayoutLock = builder.getBoolean("enable_ui_keyframes_layout_lock", false);
        ModSettings.lockedLayoutPreventsResizing = builder.getBoolean("locked_layout_prevents_resizing", false);
        KeybindConflictSettings.showBadges = builder.getBoolean("keybind_conflict_badges", false);
        ModSettings.filmAutoGameMode = builder.getBoolean("film_auto_game_mode", false);
        ModSettings.privateBbsClipboard = builder.getBoolean("private_bbs_clipboard", false);

        /* 隐藏设置项（不显示在界面中，仅持久化） */
        /* 旧配置键：texture_manager_layout 已无消费方，保留读写以兼容旧配置文件 */
        ModSettings.textureManagerLayout = (ValueInt) builder.getInt("texture_manager_layout", 0).invisible();
        ModSettings.aaaEffectPickerWidth = (ValueInt) builder.getInt("aaa_effect_picker_width", 0, 0, 16384).invisible();
        ModSettings.aaaEffectPickerHeight = (ValueInt) builder.getInt("aaa_effect_picker_height", 0, 0, 16384).invisible();
        ModSettings.filmLibrarySortMode = (ValueInt) builder.getInt("film_library_sort_mode", 0, 0, 1).invisible();
        ModSettings.filmLibraryDefaultLocation = (ValueString) builder.getString("film_library_default_location", "folder:").invisible();
        ModSettings.filmLibraryShortcuts = (ValueString) builder.getString("film_library_shortcuts", "").invisible();
        ModSettings.morphingDefaultCategory = (ValueString) builder.getString("morphing_default_category", "home").invisible();
        ModSettings.morphingLayoutMode = (ValueInt) builder.getInt("morphing_layout_mode", 0, 0, 1).invisible();
        ModSettings.morphingIconScale = (ValueInt) builder.getInt("morphing_icon_scale", 100, 20, 200).invisible();
        ModSettings.filmToolbarOverflow = (ValueString) builder.getString("film_toolbar_overflow", "").invisible();

        /* ===== 渲染与性能 ===== */
        builder.category("rendering_performance", Icons.SPRAY);

        ModSettings.autoDisableFaceCulling = builder.getBoolean("auto_disable_face_culling", false);
        ModSettings.itemSprayFrustumCulling = builder.getBoolean("item_spray_frustum_culling", true);
        ModSettings.itemSprayMaxRenderDistance = builder.getInt("item_spray_max_render_distance", 0, 0, 512);
        ModSettings.itemSprayMaxRenderedItems = builder.getInt("item_spray_max_rendered_items", 1024, 0, 8192);
        ModSettings.itemSprayIRLiteShadowMaxItems = builder.getInt("item_spray_irlite_shadow_max_items", 1024, 0, 4096);
        /* 旧配置键：replay_sprint_particles 已无消费方，保留以免旧配置报未知键 */
        ModSettings.replaySprintParticles = builder.getBoolean("replay_sprint_particles", false);
        ModSettings.fluidRealisticModelInteraction = builder.getBoolean("fluid_realistic_model_interaction", false);

        ModSettings.modelBlockRenderDistance = builder.getInt("model_block_render_distance", 256, 0, 1024);

        /* ===== 操控器与建模 ===== */
        builder.category("gizmo_modeling", Icons.MOVE_TO);

        ModSettings.blockbenchPath = builder.getString("blockbench_path", "");
        ModSettings.blockbenchPreferBbmodel = builder.getBoolean("blockbench_prefer_bbmodel", true);
        ModSettings.blockbenchGizmo = builder.getBoolean("blockbench_gizmo", false);
        ModSettings.gizmoKeepOriginal = builder.getBoolean("gizmo_keep_original", false);

        /* ===== 导出 ===== */
        builder.category("export", Icons.DOWNLOAD);

        ModSettings.exportAudioSubtitle = builder.getBoolean("export_audio_subtitle", false);
        ModSettings.premiereExportEnabled = builder.getBoolean("premiere_export_enabled", false);
        ModSettings.premiereExportIndividualAudio = builder.getBoolean("premiere_export_individual_audio", true);
        ModSettings.premiereExportAudioOnly = builder.getBoolean("premiere_export_audio_only", false);
        ModSettings.premiereExportNtscFlag = builder.getBoolean("premiere_export_ntsc_flag", true);
        ModSettings.premiereExportSrt = builder.getBoolean("premiere_export_srt", false);
    }

    private static IKey lang(String key)
    {
        return () -> BBSModClient.getL10n() == null ? key : L10n.lang(key).get();
    }
}
