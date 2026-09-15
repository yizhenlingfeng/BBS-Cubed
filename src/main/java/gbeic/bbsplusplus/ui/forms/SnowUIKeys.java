package gbeic.bbsplusplus.ui.forms;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import org.lwjgl.glfw.GLFW;

/**
 * BBS_snow 自有 UI 翻译键(bbspp. 前缀),翻译由插件的
 * strings/<lang>.json 提供。
 */
public class SnowUIKeys
{
    public static final IKey ANIMATION_STATE_PANEL_PREVIEW = L10n.lang("bbspp.ui.animation_state.panel_preview");
    public static final IKey ANIMATION_STATE_PANEL_TIMELINE = L10n.lang("bbspp.ui.animation_state.panel_timeline");
    public static final IKey ANIMATION_STATE_PANEL_INSPECTOR = L10n.lang("bbspp.ui.animation_state.panel_inspector");
    public static final IKey MOLANG_SHARED = L10n.lang("bbspp.ui.actions.molang_shared");
    public static final IKey MOLANG_SHARED_TOOLTIP = L10n.lang("bbspp.ui.actions.molang_shared_tooltip");
    public static final IKey ACTIONS_LOOP_BEYOND = L10n.lang("bbspp.ui.actions.loop_beyond");
    public static final IKey ACTIONS_LOOP_INTERVAL = L10n.lang("bbspp.ui.actions.loop_interval");

    public static final IKey AUDIO_IMPORT_STARTED = L10n.lang("bbspp.ui.audio_import.started");
    public static final IKey AUDIO_IMPORT_DONE = L10n.lang("bbspp.ui.audio_import.done");
    public static final IKey AUDIO_IMPORT_PARTIAL = L10n.lang("bbspp.ui.audio_import.partial");
    public static final IKey AUDIO_IMPORT_FAILED = L10n.lang("bbspp.ui.audio_import.failed");

    public static final IKey BONE_PRIORITY = L10n.lang("bbspp.ui.bone_priority.title");
    public static final IKey BONE_PRIORITY_EXPANDED_LIMB = L10n.lang("bbspp.ui.bone_priority.expanded_limb");
    public static final IKey BONE_PRIORITY_DEFAULT = L10n.lang("bbspp.ui.bone_priority.default");

    public static final IKey MINI_WINDOW_CONVERT = L10n.lang("bbspp.ui.mini_window.convert");
    public static final IKey MINI_WINDOW_RESTORE = L10n.lang("bbspp.ui.mini_window.restore");
    public static final IKey MINI_WINDOW_COLLAPSE = L10n.lang("bbspp.ui.mini_window.collapse");

    public static final IKey PICK_BONE = L10n.lang("bbspp.ui.forms.pick_bone");
    public static final IKey PICK_BONE_ACTIVE = L10n.lang("bbspp.ui.forms.pick_bone_active");
    public static final IKey KEYFRAMES_SELECT_COLUMN = L10n.lang("bbspp.ui.keyframes.select_column");
    public static final KeyCombo KEYFRAMES_SELECT_COLUMN_KEY = new KeyCombo(
        "keyframes_select_column",
        KEYFRAMES_SELECT_COLUMN,
        GLFW.GLFW_KEY_W,
        GLFW.GLFW_KEY_LEFT_SHIFT
    ).categoryKey("keyframes");

    /* Film controller: 与上一个镜头模式循环 */
    public static final IKey TOGGLE_PREVIOUS_CAMERA_MODE = L10n.lang("bbspp.ui.film.toggle_previous_camera_mode");
    public static final KeyCombo TOGGLE_PREVIOUS_CAMERA_MODE_KEY = new KeyCombo(
        "toggle_previous_camera_mode",
        TOGGLE_PREVIOUS_CAMERA_MODE,
        GLFW.GLFW_KEY_R
    ).categoryKey("film_controller");
}
