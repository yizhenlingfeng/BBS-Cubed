package perspective.better_camera_perspective.mixin.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.StableIds;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.ReplayContextAction;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v1.0.1：接管 UIAnchorKeyframeFactory.displayActors（锚点「选择角色」列表）。
 *
 * 相比 BBS 原逻辑：
 *  1. 不再按实体实例化状态过滤 —— 影片全部演员（含新建、未实例化的）都能选中；
 *  2. 按 Replay.category（组/类别）分组做二级选择（一级 = 类别，二级 = 演员，带返回）；
 *  3. 菜单可滚动（条目 ≥12 自动出现搜索过滤框）。
 *
 * v1.0.3：锚点列表显示名改为「屎山叠屎山」方案 —— 直接拼可辨识名，不强改数据：
 *  「ID串 - formId - 伪装名」（如 071cb8c1 - bbs:model - XY）
 *  伪装名来源（按优先级）：自定义名(label) → 表单名(form.name) → 资产库同类型伪装名
 *  → 影片内同类型演员的可辨识名；都没有时省略名称段（保持 ID串 - formId）。
 *  这样每个条目仍由 ID 前缀保证唯一可区分，名称段负责可辨识；
 *  资产库伪装的名称反查仅发生在锚点列表内部，不会误伤资产库界面（不回归集体覆盖 bug）。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIAnchorKeyframeFactory.class)
public class UIAnchorKeyframeFactoryMixin {
    @Inject(method = "displayActors", at = @At("HEAD"), cancellable = true)
    private static void bcp$displayActors(UIContext context, Map<String, IEntity> entities, String value, Consumer<String> callback, CallbackInfo ci) {
        bcp$showActorList(context, value, callback);
        ci.cancel();
    }

    /** 一级列表：按类别分组（"" 归入「未分类」）；单组时直接平铺。 */
    private static void bcp$showActorList(UIContext context, String value, Consumer<String> callback) {
        List<UIFilmPanel> children = context.menu.main.getChildren(UIFilmPanel.class);
        UIFilmPanel panel = children.isEmpty() ? null : children.get(0);
        List<Replay> replays = panel != null ? panel.getData().replays.getList() : List.of();

        // 资产库伪装名（formId → 可辨识名），只构建一次，供全部条目复用
        Map<String, String> assetNames = bcp$assetNames();

        Map<String, List<Replay>> groups = new LinkedHashMap<>();
        for (Replay replay : replays) {
            String category = Replay.normalizeCategory(replay.category.get());
            groups.computeIfAbsent(category, k -> new ArrayList<>()).add(replay);
        }

        if (groups.size() <= 1) {
            List<Replay> list = groups.isEmpty() ? List.of() : groups.values().iterator().next();
            bcp$showActors(context, value, callback, list, assetNames);
            return;
        }

        UISimpleContextMenu replayMenu = new UISimpleContextMenu();
        replayMenu.actions.scroll.scrollItemSize = 30;
        context.replaceContextMenu(menu -> {
            menu.custom(replayMenu);
            menu.autoKeys();
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, 16728153, () -> callback.accept(""));
            for (Map.Entry<String, List<Replay>> entry : groups.entrySet()) {
                IKey title = entry.getKey().isEmpty() ? L10n.lang("bbs.ui.camera.panels.anchor_uncategorized") : IKey.raw(entry.getKey());
                List<Replay> list = entry.getValue();
                menu.action(Icons.FOLDER, title, () -> bcp$showGroup(context, value, callback, list, assetNames));
            }
        });
    }

    /** 二级：组内演员列表（带「返回」）。 */
    private static void bcp$showGroup(UIContext context, String value, Consumer<String> callback, List<Replay> list, Map<String, String> assetNames) {
        UISimpleContextMenu replayMenu = new UISimpleContextMenu();
        replayMenu.actions.scroll.scrollItemSize = 30;
        context.replaceContextMenu(menu -> {
            menu.custom(replayMenu);
            menu.autoKeys();
            menu.action(Icons.ARROW_LEFT, L10n.lang("bbs.ui.camera.panels.back"), () -> bcp$showActorList(context, value, callback));
            for (Replay replay : list) {
                bcp$addActor(menu, replay, value, callback, list, assetNames);
            }
        });
    }

    /** 平铺：全部演员。 */
    private static void bcp$showActors(UIContext context, String value, Consumer<String> callback, List<Replay> list, Map<String, String> assetNames) {
        UISimpleContextMenu replayMenu = new UISimpleContextMenu();
        replayMenu.actions.scroll.scrollItemSize = 30;
        context.replaceContextMenu(menu -> {
            menu.custom(replayMenu);
            menu.autoKeys();
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, 16728153, () -> callback.accept(""));
            for (Replay replay : list) {
                bcp$addActor(menu, replay, value, callback, list, assetNames);
            }
        });
    }

    /** 单个演员条目（复用 BBS 的 ReplayContextAction，显示缩略图 + 显示名）。 */
    private static void bcp$addActor(ContextMenuManager menu, Replay replay, String value, Consumer<String> callback, List<Replay> all, Map<String, String> assetNames) {
        int color = replay.getId().equals(value) ? BBSSettings.primaryColor(0) : 0;
        menu.action(new ReplayContextAction(replay, IKey.raw(bcp$displayName(replay, all, assetNames)), () -> callback.accept(replay.getId()), color));
    }

    /** 构建显示名：ID串 - formId - 伪装名（伪装名拿不到则省略名称段）。 */
    private static String bcp$displayName(Replay replay, List<Replay> all, Map<String, String> assetNames) {
        Form form = replay.form.get();
        String formId = form == null ? "?" : form.getFormId();
        String base = replay.getId() + " - " + formId;

        String name = bcp$lookupName(replay, form, all, assetNames);
        return name == null ? base : base + " - " + name;
    }

    /** 可辨识名来源（优先级）：label → form.name → 资产库同类型伪装名 → 影片内同类型借用。 */
    private static String bcp$lookupName(Replay replay, Form form, List<Replay> all, Map<String, String> assetNames) {
        String label = replay.label.get();
        if (!label.isEmpty() && !bcp$isCode(label)) {
            return label;
        }

        if (form != null) {
            String name = form.name.get();
            if (!name.isEmpty() && !bcp$isCode(name)) {
                return name;
            }

            String assetName = assetNames.get(form.getFormId());
            if (assetName != null) {
                return assetName;
            }

            String formId = form.getFormId();
            for (Replay other : all) {
                if (other == replay) {
                    continue;
                }
                Form otherForm = other.form.get();
                if (otherForm != null && otherForm.getFormId().equals(formId)) {
                    String otherLabel = other.label.get();
                    if (!otherLabel.isEmpty() && !bcp$isCode(otherLabel)) {
                        return otherLabel;
                    }
                    String otherName = otherForm.name.get();
                    if (!otherName.isEmpty() && !bcp$isCode(otherName)) {
                        return otherName;
                    }
                }
            }
        }

        return null;
    }

    /** 收集资产库伪装名：用户表单分类里 formId → 第一个可辨识的表单名。 */
    private static Map<String, String> bcp$assetNames() {
        Map<String, String> map = new HashMap<>();
        try {
            FormCategories categories = BBSModClient.getFormCategories();
            if (categories != null) {
                UserFormSection userForms = categories.getUserForms();
                if (userForms != null) {
                    for (UserFormCategory category : userForms.categories) {
                        if (category == null) {
                            continue;
                        }
                        for (Form form : category.getForms()) {
                            if (form == null) {
                                continue;
                            }
                            String name = form.name.get();
                            if (name.isEmpty() || bcp$isCode(name)) {
                                continue;
                            }
                            map.putIfAbsent(form.getFormId(), name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 资产库不可用时不阻塞列表显示
        }
        return map;
    }

    /** 判断字符串是否是「数据码」样式（纯 8 位稳定 ID，或 "8位hex - xxx"）。 */
    private static boolean bcp$isCode(String s) {
        if (StableIds.isStableId(s)) {
            return true;
        }
        return s.matches("(?i)[0-9a-f]{8}\\s*-\\s*.*");
    }
}
