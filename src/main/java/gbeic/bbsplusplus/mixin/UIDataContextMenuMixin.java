package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import gbeic.bbsplusplus.mixin.UIContextMenuBarAccessor;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenuBar;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.presets.DataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 在预设右键菜单（UIDataContextMenu）中添加"自动保存"开关。
 * <p>
 * 该菜单被 IK 链、物理骨骼、骨骼限制和姿势四个页面共用。
 * 启用时锁定当前预设为自动保存目标，后续面板修改经防抖后自动写入该预设。
 * </p>
 * <p>
 * 开启时若未手动选中预设，依次 fallback：当前数据匹配的预设 → 列表第一个预设。
 * </p>
 */
@Mixin(value = UIDataContextMenu.class, remap = true)
public abstract class UIDataContextMenuMixin extends UIElement
{
    @Shadow public UISearchList entries;
    @Shadow public UIContextMenuBar bar;
    @Shadow private java.util.function.Supplier<MapType> supplier;
    @Shadow private MapType data;

    @Unique private String bbspp$type;
    @Unique private MenuIcon bbspp$autoSaveIcon;

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

        this.bbspp$autoSaveIcon = new MenuIcon(Icons.SAVE, L10n.lang("bbspp.ui.preset.auto_save"), MenuVerb.Slot.COMMON, () ->
        {
            if (this.bbspp$type == null)
            {
                return;
            }
            boolean newState = !AutoSavePresetState.isEnabled(this.bbspp$type);

            if (newState)
            {
                String selected = bbspp$getSelectedPresetName();
                if (selected == null || selected.isEmpty())
                {
                    selected = bbspp$getCurrentActivePreset();
                }
                if (selected == null || selected.isEmpty())
                {
                    selected = bbspp$getFirstPreset();
                }
                if (selected == null || selected.isEmpty())
                {
                    UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(
                            L10n.lang("bbspp.ui.preset.auto_save"),
                            L10n.lang("bbspp.ui.preset.auto_save_no_selection")));
                    return;
                }
                AutoSavePresetState.setSelectedPreset(this.bbspp$type, selected);
            }

            AutoSavePresetState.setEnabled(this.bbspp$type, newState);
        });
        this.bar.register(this.bbspp$autoSaveIcon);
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
     * render() 中同步按钮颜色（关闭灰/开启白）及目标预设选中态。
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void bbspp$onRender(UIContext context, CallbackInfo ci)
    {
        if (this.bbspp$type == null)
        {
            return;
        }

        /* 同步按钮颜色：开启白色，关闭灰色 */
        if (this.bbspp$autoSaveIcon != null && this.bar != null)
        {
            UIContextMenuBarAccessor accessor = (UIContextMenuBarAccessor) this.bar;
            List<MenuIcon> icons = accessor.bbspp$getIcons();
            List<UIIcon> buttons = accessor.bbspp$getButtons();
            int idx = icons.indexOf(this.bbspp$autoSaveIcon);
            if (idx >= 0 && idx < buttons.size())
            {
                UIIcon btn = buttons.get(idx);
                btn.iconColor = AutoSavePresetState.isEnabled(this.bbspp$type) ? -1 : 0xFF6E6E6E;
            }
        }

        if (this.entries == null || this.entries.list == null)
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
        this.entries.list.setCurrent(target);
    }

    @Unique
    private String bbspp$getSelectedPresetName()
    {
        if (this.entries == null || this.entries.list == null)
        {
            return null;
        }
        Object current = this.entries.list.getCurrentFirst();
        if (current instanceof String name && !name.isEmpty())
        {
            return name;
        }
        return null;
    }

    /**
     * 通过 supplier.get() 获取当前数据快照，在 data.keys() 中深比较匹配当前预设。
     */
    @Unique
    private String bbspp$getCurrentActivePreset()
    {
        if (this.supplier == null || this.data == null)
        {
            return null;
        }
        MapType current;
        try
        {
            current = this.supplier.get();
        }
        catch (Exception e)
        {
            return null;
        }
        if (current == null)
        {
            return null;
        }
        for (String key : this.data.keys())
        {
            MapType presetData = this.data.getMap(key);
            if (presetData != null && mchorse.bbs_mod.data.types.BaseType.equals(presetData, current))
            {
                return key;
            }
        }
        return null;
    }

    @Unique
    private String bbspp$getFirstPreset()
    {
        if (this.data == null)
        {
            return null;
        }
        for (String key : this.data.keys())
        {
            if (key != null && !key.isEmpty())
            {
                return key;
            }
        }
        return null;
    }

    @Unique
    private void bbspp$captureSelectedPreset()
    {
        String name = bbspp$getSelectedPresetName();
        if (name != null)
        {
            AutoSavePresetState.setSelectedPreset(this.bbspp$type, name);
        }
    }
}
