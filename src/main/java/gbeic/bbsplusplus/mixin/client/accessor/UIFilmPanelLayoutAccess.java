package gbeic.bbsplusplus.mixin.client.accessor;

import gbeic.bbsplusplus.api.UIFilmPanelLayoutAccessor;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 注意:不要 @Invoker("getDockPanelIcon")——接口实现会注入同名 public 方法,与 private 方法冲突导致无限递归。
 * 面板注册表在 2.5 迁移到 panel.dock(UIDockLayout 公有字段)。
 */
@Mixin(value = UIFilmPanel.class, remap = false)
public interface UIFilmPanelLayoutAccess extends UIFilmPanelLayoutAccessor
{
    @Override
    @Accessor(value = "selectedMainEditorPanel", remap = false)
    UIElement bbspp_cml$getSelectedMainEditorPanel();

    @Override
    @Invoker(value = "getCurrentFilmLayoutRoot", remap = false)
    EditorLayoutNode bbspp_cml$getCurrentFilmLayoutRoot();

    @Override
    @Invoker(value = "setCurrentFilmLayoutRoot", remap = false)
    void bbspp_cml$setCurrentFilmLayoutRoot(EditorLayoutNode root);
}
