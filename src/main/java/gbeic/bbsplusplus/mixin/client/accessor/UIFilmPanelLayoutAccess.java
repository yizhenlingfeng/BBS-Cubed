package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link UIFilmPanel} 的当前布局根与当前主编辑器面板，供编辑器锁定与小窗支持使用。
 *
 * <p>不能用 {@code @Invoker("getDockPanelIcon")}：接口会注入同名的 public 方法，
 * 与 private 方法冲突导致无限递归。面板注册表由 {@code panel.dock} 持有，
 * 即 {@code UIDockLayout} 的公有字段。</p>
 */
@Mixin(value = UIFilmPanel.class, remap = false)
public interface UIFilmPanelLayoutAccess
{
    @Accessor(value = "selectedMainEditorPanel", remap = false)
    UIElement bbspp_cml$getSelectedMainEditorPanel();

    @Invoker(value = "getCurrentFilmLayoutRoot", remap = false)
    EditorLayoutNode bbspp_cml$getCurrentFilmLayoutRoot();

    @Invoker(value = "setCurrentFilmLayoutRoot", remap = false)
    void bbspp_cml$setCurrentFilmLayoutRoot(EditorLayoutNode root);
}
