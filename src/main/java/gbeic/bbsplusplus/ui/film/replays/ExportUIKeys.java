package gbeic.bbsplusplus.ui.film.replays;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * pose 关键帧导出动画功能的翻译键 —— 插件自有功能(CML 也没有),
 * 用 bbspp. 前缀避免与 bbs 官方键位冲突,翻译由插件的
 * strings/&lt;lang&gt;.json 提供。
 */
public class ExportUIKeys
{
    public static final IKey CONTEXT_EXPORT = L10n.lang("bbspp.ui.export_animation.context");

    public static final IKey TITLE = L10n.lang("bbspp.ui.export_animation.title");
    public static final IKey NEW_FILE = L10n.lang("bbspp.ui.export_animation.new_file");
    public static final IKey FILE_NAME = L10n.lang("bbspp.ui.export_animation.file_name");
    public static final IKey ANIMATION_NAME = L10n.lang("bbspp.ui.export_animation.animation_name");
    public static final IKey LOOP = L10n.lang("bbspp.ui.export_animation.loop");
    public static final IKey BAKE_IK = L10n.lang("bbspp.ui.export_animation.bake_ik");
    public static final IKey BAKE_IK_TOOLTIP = L10n.lang("bbspp.ui.export_animation.bake_ik_tooltip");
    public static final IKey EXPORT = L10n.lang("bbspp.ui.export_animation.export");
    public static final IKey SUCCESS = L10n.lang("bbspp.ui.export_animation.success");
    public static final IKey SUCCESS_IK = L10n.lang("bbspp.ui.export_animation.success_ik");
    public static final IKey FAILED = L10n.lang("bbspp.ui.export_animation.failed");

    public static final IKey SEARCH = L10n.lang("bbspp.ui.export_animation.search");
    public static final IKey COPY = L10n.lang("bbspp.ui.export_animation.copy");
    public static final IKey PASTE = L10n.lang("bbspp.ui.export_animation.paste");
    public static final IKey OPEN_FOLDER = L10n.lang("bbspp.ui.export_animation.open_folder");
}
