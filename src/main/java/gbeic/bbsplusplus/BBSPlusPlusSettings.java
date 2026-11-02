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
 * BBS++ 独立设置模块的注册入口。
 * <p>
 * 设置模块内部分为分类，显示在设置界面的分类列表中：
 * <ul>
 *   <li>BBS 增强 — 界面与交互增强，含 snow 前四项核心开关</li>
 *   <li>增强界面 — 全新伪装界面、影片库、光影曲线选择界面</li>
 *   <li>物品喷射 — 物品喷射粒子渲染与性能</li>
 *   <li>CML 扩展 — 回放疾跑粒子、骨骼纹理、流体精确交互</li>
 *   <li>导出增强 — Premiere 导出、音频字幕导出</li>
 *   <li>性能优化 — 模型方块渲染距离等</li>
 *   <li>未完成（WIP） — 实验性功能</li>
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

        ModSettings.chineseKeyframeNames = builder.getBoolean("chinese_keyframe_names", false);
        ModSettings.filmAutoGameMode = builder.getBoolean("film_auto_game_mode", false);
        ModSettings.preventNegativeKeyframes = builder.getBoolean("prevent_negative_keyframes", false);
        ModSettings.reverseTimelineScroll = builder.getBoolean("reverse_timeline_scroll", false);
        ModSettings.directParentPicking = builder.getBoolean("direct_parent_picking", false);
        ModSettings.ikTargetCycleConstraint = builder.getBoolean("ik_target_cycle_constraint", true);
        ModSettings.enableIrisButton = builder.getBoolean("enable_iris_button", false);
        ModSettings.enableUiKeyframesLayoutLock = builder.getBoolean("enable_ui_keyframes_layout_lock", false);
        ModSettings.worldFilmShaderCurves = builder.getBoolean("world_film_shader_curves", false);
        ModSettings.allowClipTrackExpansion = builder.getBoolean("allow_clip_track_expansion", false);
        ModSettings.privateBbsClipboard = builder.getBoolean("private_bbs_clipboard", false);
        ModSettings.autoDisableFaceCulling = builder.getBoolean("auto_disable_face_culling", false);
        ModSettings.filmAltWheelTimelineMode = builder.getInt("film_alt_wheel_timeline_mode", 0, 0, 2).modes(
            lang("bbspp.config.bbs_enhancements.film_alt_wheel_timeline_mode.default"),
            lang("bbspp.config.bbs_enhancements.film_alt_wheel_timeline_mode.disabled"),
            lang("bbspp.config.bbs_enhancements.film_alt_wheel_timeline_mode.horizontal_scroll")
        );

        /* snow 前四项核心开关整合到 BBS 增强 */
        ModSettings.pivotTransform = builder.getBoolean("pivot_transform", true);
        ModSettings.snowActions = builder.getBoolean("snow_actions", false);
        ModSettings.lockedLayoutPreventsResizing = builder.getBoolean("locked_layout_prevents_resizing", false);

        /* 动画状态自动读取模型目录的 *.animation.json（默认开启） */
        ModSettings.autoLoadAnimationFile = builder.getBoolean("auto_load_animation_file", true);

        /* 快捷键冲突提示 */
        KeybindConflictSettings.showBadges = builder.getBoolean("keybind_conflict_badges", false);

        /* 隐藏设置项（不显示在界面中，仅持久化） */
        ModSettings.textureManagerLayout = (ValueInt) builder.getInt("texture_manager_layout", 0).invisible();
        ModSettings.aaaEffectPickerWidth = (ValueInt) builder.getInt("aaa_effect_picker_width", 0, 0, 16384).invisible();
        ModSettings.aaaEffectPickerHeight = (ValueInt) builder.getInt("aaa_effect_picker_height", 0, 0, 16384).invisible();
        ModSettings.filmLibrarySortMode = (ValueInt) builder.getInt("film_library_sort_mode", 0, 0, 1).invisible();
        ModSettings.filmLibraryDefaultLocation = (ValueString) builder.getString("film_library_default_location", "folder:").invisible();
        ModSettings.filmLibraryShortcuts = (ValueString) builder.getString("film_library_shortcuts", "").invisible();
        ModSettings.morphingDefaultCategory = (ValueString) builder.getString("morphing_default_category", "home").invisible();
        ModSettings.morphingLayoutMode = (ValueInt) builder.getInt("morphing_layout_mode", 0, 0, 1).invisible();
        ModSettings.morphingIconScale = (ValueInt) builder.getInt("morphing_icon_scale", 100, 20, 200).invisible();
        ModSettings.replayTrackOrder = (ValueString) builder.getString("replay_track_order", "").invisible();
        ModSettings.filmToolbarOverflow = (ValueString) builder.getString("film_toolbar_overflow", "").invisible();

        /* CML 隐藏设置项 */
        ModSettings.bonePriorityExpandedLimb = builder.getBoolean("bone_priority_expanded_limb", false);
        ModSettings.bonePriorityExpandedLimb.invisible();
        ModSettings.bonePriorityTrack = builder.getString("bone_priority_track", "");
        ModSettings.bonePriorityTrack.invisible();
        ModSettings.animationStateLayout = builder.getString("animation_state_layout", "");
        ModSettings.animationStateLayout.invisible();
        ModSettings.animationStateHiddenPanels = builder.getString("animation_state_hidden_panels", "");
        ModSettings.animationStateHiddenPanels.invisible();
        ModSettings.keyframeEditorTimelineRatio = builder.getFloat("keyframe_editor_timeline_ratio", 0.65F, 0.2F, 0.8F);
        ModSettings.keyframeEditorTimelineRatio.invisible();

        /* ===== 分类二：增强界面 ===== */
        builder.category("ui_enhancements", Icons.DUPE);

        ModSettings.newMorphingPanel = builder.getBoolean("new_morphing_panel", false);
        ModSettings.newFilmLibraryUi = builder.getBoolean("new_film_library_ui", false);
        ModSettings.shaderCurvePicker = builder.getBoolean("shader_curve_picker", false);

        /* Blockbench 外部编辑：exe 路径 + 是否优先用 .bbmodel 工程文件打开 */
        ModSettings.blockbenchPath = builder.getString("blockbench_path", "");
        ModSettings.blockbenchPreferBbmodel = builder.getBoolean("blockbench_prefer_bbmodel", true);

        /* ===== 分类三：物品喷射 ===== */
        builder.category("item_spray", Icons.DUPE);

        ModSettings.itemSprayFrustumCulling = builder.getBoolean("item_spray_frustum_culling", true);
        ModSettings.itemSprayMaxRenderDistance = builder.getInt("item_spray_max_render_distance", 0, 0, 512);
        ModSettings.itemSprayMaxRenderedItems = builder.getInt("item_spray_max_rendered_items", 1024, 0, 8192);
        ModSettings.itemSprayIRLiteShadowMaxItems = builder.getInt("item_spray_irlite_shadow_max_items", 1024, 0, 4096);

        /* ===== 分类四：CML 增强 ===== */
        builder.category("cml_enhancements", Icons.DUPE);

        ModSettings.replaySprintParticles = builder.getBoolean("replay_sprint_particles", false);
        ModSettings.pickLimbTexture = builder.getBoolean("pick_limb_texture", true);
        ModSettings.fluidRealisticModelInteraction = builder.getBoolean("fluid_realistic_model_interaction", false);

        /* ===== 分类六：导出增强 ===== */
        builder.category("export_enhancements", Icons.DUPE);

        ModSettings.exportAudioSubtitle = builder.getBoolean("export_audio_subtitle", false);
        ModSettings.premiereExportEnabled = builder.getBoolean("premiere_export_enabled", false);
        ModSettings.premiereExportIndividualAudio = builder.getBoolean("premiere_export_individual_audio", true);
        ModSettings.premiereExportAudioOnly = builder.getBoolean("premiere_export_audio_only", false);
        ModSettings.premiereExportNtscFlag = builder.getBoolean("premiere_export_ntsc_flag", true);
        ModSettings.premiereExportSrt = builder.getBoolean("premiere_export_srt", false);

        /* ===== 分类七：性能优化 ===== */
        builder.category("performance", Icons.DUPE);

        ModSettings.modelBlockRenderDistance = builder.getInt("model_block_render_distance", 256, 0, 1024);

        /* ===== 分类八：Gizmo 改版 ===== */
        builder.category("gizmo_modifications", Icons.DUPE);

        ModSettings.blockbenchGizmo = builder.getBoolean("blockbench_gizmo", false);
        ModSettings.gizmoKeepOriginal = builder.getBoolean("gizmo_keep_original", false);

        /* ===== 分类九：附魔光效 ===== */
        builder.category("wip", Icons.DUPE);

        /* 默认关闭；开启后在姿势编辑器显示附魔光效栏，支持颜色、流动速度与亮度。 */
        ModSettings.enchantGlint = builder.getBoolean("enchant_glint", false);
    }

    private static IKey lang(String key)
    {
        return () -> BBSModClient.getL10n() == null ? key : L10n.lang(key).get();
    }
}
