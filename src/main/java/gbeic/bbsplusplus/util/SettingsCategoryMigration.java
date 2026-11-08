package gbeic.bbsplusplus.util;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 把旧版 {@code bbspp.json} 的分类键迁移到新的功能分类。
 * <p>
 * 本次分类重构之前，设置项按「BBS 增强 / 增强界面 / 物品喷射 / CML 扩展 / 导出增强 /
 * 性能优化 / Gizmo 改版」等旧分类存储。设置项 id 不变，只换了所属分类；
 * 若直接加载，{@code Settings.fromData} 会按新分类查找，旧值会被忽略并回落默认值。
 * 因此在解析前按选项 id 把旧分类里的值搬到新分类，迁移完成后删除旧分类键。
 * </p>
 */
public final class SettingsCategoryMigration
{
    public static final String SETTINGS_ID = "bbspp";

    private static final String BACKUP_SUFFIX = ".bbspp-categories-backup";
    private static final String TEMP_SUFFIX = ".bbspp-categories.tmp";

    private static final Set<String> LEGACY_CATEGORIES = Set.of(
        "bbs_enhancements",
        "ui_enhancements",
        "item_spray",
        "cml_enhancements",
        "export_enhancements",
        "performance",
        "gizmo_modifications"
    );

    private static final Map<String, String> OPTION_CATEGORIES = new HashMap<>();

    static
    {
        map("timeline_keyframes",
            "chinese_keyframe_names",
            "prevent_negative_keyframes",
            "reverse_timeline_scroll",
            "film_alt_wheel_timeline_mode",
            "allow_clip_track_expansion",
            "snow_actions",
            "keyframe_editor_timeline_ratio",
            "replay_track_order"
        );

        map("pose_animation",
            "direct_parent_picking",
            "ik_target_cycle_constraint",
            "pivot_transform",
            "pick_limb_texture",
            "auto_load_animation_file",
            "bone_priority_expanded_limb",
            "bone_priority_track",
            "animation_state_layout",
            "animation_state_hidden_panels"
        );

        map("shaders",
            "enable_iris_button",
            "world_film_shader_curves",
            "shader_curve_picker"
        );

        map("interface_interaction",
            "new_morphing_panel",
            "new_film_library_ui",
            "enable_ui_keyframes_layout_lock",
            "locked_layout_prevents_resizing",
            "keybind_conflict_badges",
            "film_auto_game_mode",
            "private_bbs_clipboard",
            "texture_manager_layout",
            "aaa_effect_picker_width",
            "aaa_effect_picker_height",
            "film_library_sort_mode",
            "film_library_default_location",
            "film_library_shortcuts",
            "morphing_default_category",
            "morphing_layout_mode",
            "morphing_icon_scale",
            "film_toolbar_overflow"
        );

        map("rendering_performance",
            "auto_disable_face_culling",
            "item_spray_frustum_culling",
            "item_spray_max_render_distance",
            "item_spray_max_rendered_items",
            "item_spray_irlite_shadow_max_items",
            "replay_sprint_particles",
            "fluid_realistic_model_interaction",
            "model_block_render_distance"
        );

        map("gizmo_modeling",
            "blockbench_path",
            "blockbench_prefer_bbmodel",
            "blockbench_gizmo",
            "gizmo_keep_original"
        );

        map("export",
            "export_audio_subtitle",
            "premiere_export_enabled",
            "premiere_export_individual_audio",
            "premiere_export_audio_only",
            "premiere_export_ntsc_flag",
            "premiere_export_srt"
        );
    }

    private SettingsCategoryMigration()
    {}

    public static void migrate(File file)
    {
        if (file == null || !file.isFile() || file.length() == 0)
        {
            return;
        }

        try
        {
            BaseType data = DataToString.read(file);

            if (!(data instanceof MapType root))
            {
                return;
            }

            boolean changed = false;

            for (String legacyCategory : LEGACY_CATEGORIES)
            {
                BaseType categoryData = root.get(legacyCategory);

                if (!(categoryData instanceof MapType legacyMap))
                {
                    continue;
                }

                for (Map.Entry<String, BaseType> entry : legacyMap)
                {
                    String targetCategory = OPTION_CATEGORIES.get(entry.getKey());

                    if (targetCategory == null)
                    {
                        continue;
                    }

                    MapType targetMap = root.getMap(targetCategory);

                    if (!targetMap.has(entry.getKey()))
                    {
                        targetMap.put(entry.getKey(), entry.getValue());
                    }

                    root.put(targetCategory, targetMap);
                }

                root.remove(legacyCategory);
                changed = true;
            }

            if (!changed)
            {
                return;
            }

            Path source = file.toPath();
            Path backup = source.resolveSibling(file.getName() + BACKUP_SUFFIX);

            if (!Files.exists(backup))
            {
                Files.copy(source, backup);
            }

            Path temp = source.resolveSibling(file.getName() + TEMP_SUFFIX);

            try
            {
                DataToString.write(temp.toFile(), root, true);
                Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
                Files.deleteIfExists(temp);

                throw e;
            }

            BBSPlusPlusMod.LOGGER.info("已把 BBS Cubed 设置迁移到新的功能分类，旧文件备份：{}", backup);
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("迁移 BBS Cubed 设置分类失败，继续使用原文件：{}", file.getAbsolutePath(), e);
        }
    }

    private static void map(String category, String... options)
    {
        for (String option : options)
        {
            OPTION_CATEGORIES.put(option, category);
        }
    }
}
