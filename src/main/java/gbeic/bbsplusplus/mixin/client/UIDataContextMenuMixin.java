package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.utils.PresetDataOperations;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenuBar;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.presets.DataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 给 {@link UIDataContextMenu}（姿势/约束/IK/物理/形态键等预设右键菜单）追加
 * CML 的"重命名/移除预设"两个按钮。全局生效于所有使用该菜单的界面（与 CML 一致）。
 *
 * <p>删除走 {@link UIConfirmOverlayPanel} 确认，重命名走 {@link UIPromptOverlayPanel}
 * 输入（预填原名、文件名过滤）；实际数据操作经 {@code PresetDataOperations}
 * （FS 的 DataManager 无 removeData/renameData API，插件补齐）。</p>
 *
 * <p>宽度自适应：原版 {@code setMouse} 按 {@code row.getChildren().size()} 计算
 * 菜单宽度，追加按钮后自动变宽，无需额外处理。</p>
 */
@Mixin(value = UIDataContextMenu.class, remap = false)
public abstract class UIDataContextMenuMixin extends UIElement
{
    @Shadow(remap = false)
    public UIContextMenuBar bar;

    @Shadow(remap = false)
    public UISearchList<String> entries;

    @Shadow(remap = false)
    private DataManager manager;

    @Shadow(remap = false)
    private String group;

    @Shadow(remap = false)
    private MapType data;

    @Shadow(remap = false)
    protected abstract void fillPoses();

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addRemoveRename(DataManager manager, String group, Supplier<MapType> supplier, Consumer<MapType> callback, CallbackInfo ci)
    {
        this.bar.register(new MenuIcon(Icons.EDIT, UIKeys.GENERAL_RENAME, MenuVerb.Slot.COMMON, this::bbspp_cml$renameEntry));
        this.bar.register(new MenuIcon(Icons.REMOVE, UIKeys.GENERAL_REMOVE, MenuVerb.Slot.COMMON, this::bbspp_cml$removeEntry));
    }

    @Unique
    private String bbspp_cml$getSelectedKey()
    {
        return this.entries.list.getCurrentFirst();
    }

    @Unique
    private void bbspp_cml$removeEntry()
    {
        String key = this.bbspp_cml$getSelectedKey();

        if (key == null || key.isEmpty())
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(UIKeys.GENERAL_REMOVE, UIKeys.PANELS_MODALS_REMOVE, (confirm) ->
        {
            if (confirm)
            {
                PresetDataOperations.removeData(this.manager, this.group, key);
                this.data = this.manager.getData(this.group);
                this.entries.search.setText("");
                this.fillPoses();
            }
        }));
    }

    @Unique
    private void bbspp_cml$renameEntry()
    {
        String key = this.bbspp_cml$getSelectedKey();

        if (key == null || key.isEmpty())
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(UIKeys.GENERAL_RENAME, UIKeys.PANELS_MODALS_RENAME, (newKey) ->
        {
            PresetDataOperations.renameData(this.manager, this.group, key, newKey);
            this.data = this.manager.getData(this.group);
            this.entries.search.setText("");
            this.fillPoses();
        });

        panel.text.setText(key);
        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }
}
