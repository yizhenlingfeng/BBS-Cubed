package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
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
 * 在预设右键菜单（UIDataContextMenu）中添加"自动保存"开关。
 * <p>
 * 该菜单被 IK 链、物理骨骼、骨骼限制和姿势四个页面共用。
 * 启用自动保存后，用户在列表中选中的预设会成为自动保存目标，
 * 后续面板修改会经防抖后自动写入该预设。
 * </p>
 * <p>
 * 关键：fillPoses() 只在构造函数中调用一次，render() 有 scrolledToCurrent 缓存，
 * 因此选中状态同步必须放在 render() 中，每次渲染都检查。
 * </p>
 */
@Mixin(value = UIDataContextMenu.class, remap = true)
public abstract class UIDataContextMenuMixin
{
    @Shadow private DataManager manager;
    @Shadow public UISearchList entries;
    @Shadow public UIElement row;

    @Unique private UIIcon bbspp$autoSaveIcon;
    @Unique private String bbspp$type;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addAutoSaveButton(DataManager manager, String group,
                                         Supplier<MapType> supplier,
                                         Consumer<MapType> callback,
                                         CallbackInfo ci)
    {
        this.bbspp$type = AutoSavePresetState.typeFromManager(manager);
        if (this.bbspp$type == null && manager != null)
        {
            this.bbspp$type = manager.getClass().getName();
        }

        this.bbspp$autoSaveIcon = new UIIcon(Icons.SAVE, (btn) ->
        {
            if (this.bbspp$type == null)
            {
                return;
            }
            boolean newState = !AutoSavePresetState.isEnabled(this.bbspp$type);
            AutoSavePresetState.setEnabled(this.bbspp$type, newState);
            btn.active(newState);

            if (newState)
            {
                bbspp$captureSelectedPreset();
            }
        });
        // 关闭时暗淡（半透明白），开启时高亮（纯白）
        this.bbspp$autoSaveIcon.iconColor(0x60FFFFFF);
        this.bbspp$autoSaveIcon.activeColor(0xFFFFFFFF);
        this.bbspp$autoSaveIcon.hoverColor(0xFFFFFFFF);
        this.bbspp$autoSaveIcon.tooltip(L10n.lang("bbspp.ui.preset.auto_save"));
        this.bbspp$autoSaveIcon.active(AutoSavePresetState.isEnabled(this.bbspp$type));
        this.row.add(this.bbspp$autoSaveIcon);
    }

    @Inject(method = "send", at = @At("HEAD"))
    private void bbspp$onPresetSend(MapType data, CallbackInfo ci)
    {
        if (this.bbspp$type == null)
        {
            return;
        }
        if (AutoSavePresetState.isEnabled(this.bbspp$type))
        {
            bbspp$captureSelectedPreset();
        }
    }

    /**
     * render() 每次渲染都调用，在这里同步按钮状态和选中状态。
     * 自动保存启用时用 setCurrentDirect 强制选中目标预设（不触发应用回调）。
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void bbspp$onRender(UIContext context, CallbackInfo ci)
    {
        if (this.bbspp$autoSaveIcon != null && this.bbspp$type != null)
        {
            this.bbspp$autoSaveIcon.active(AutoSavePresetState.isEnabled(this.bbspp$type));
        }

        if (this.bbspp$type == null || this.entries == null || this.entries.list == null)
        {
            return;
        }
        if (!AutoSavePresetState.isEnabled(this.bbspp$type))
        {
            return;
        }
        String target = AutoSavePresetState.getSelectedPreset(this.bbspp$type);
        if (target == null || target.isEmpty())
        {
            return;
        }
        this.entries.list.setCurrentDirect(target);
    }

    @Unique
    private void bbspp$captureSelectedPreset()
    {
        if (this.entries == null || this.entries.list == null)
        {
            return;
        }
        Object current = this.entries.list.getCurrentFirst();
        if (current instanceof String name && !name.isEmpty())
        {
            AutoSavePresetState.setSelectedPreset(this.bbspp$type, name);
        }
    }
}
