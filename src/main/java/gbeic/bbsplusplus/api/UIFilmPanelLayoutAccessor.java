package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link UIFilmPanel} 的主编辑器选择与影片布局根给运行时代码使用。
 *
 * <p>2.5 起 UIFilmPanel 的面板注册迁移到 {@code panel.dock}(UIDockLayout,
 * 公有字段),布局刷新走 dock 公有 API;此处仅保留仍为私有的成员。</p>
 */
@Mixin(value = UIFilmPanel.class, remap = false)
public interface UIFilmPanelLayoutAccessor
{
    @Accessor(value = "selectedMainEditorPanel", remap = false)
    UIElement bbspp_cml$getSelectedMainEditorPanel();

    @Invoker(value = "getCurrentFilmLayoutRoot", remap = false)
    EditorLayoutNode bbspp_cml$getCurrentFilmLayoutRoot();

    @Invoker(value = "setCurrentFilmLayoutRoot", remap = false)
    void bbspp_cml$setCurrentFilmLayoutRoot(EditorLayoutNode root);
}
