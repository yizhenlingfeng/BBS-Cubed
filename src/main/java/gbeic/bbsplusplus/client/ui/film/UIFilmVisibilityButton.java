package gbeic.bbsplusplus.client.ui.film;

import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import gbeic.bbsplusplus.ui.film.FilmVisibilityUIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

/**
 * 影片面板动作栏里的「可见性」按钮。
 * <p>
 * 可见性菜单只对已打开的影片有意义；Landing 影片库主页没有影片数据时按钮应当置灰且不可点击，
 * 而不是仍然弹出一个操作不到任何影片的菜单。点击状态和绘制颜色都在 {@link #isEnabled()} 里
 * 动态判断，因此不需要额外按帧同步。
 * </p>
 */
public class UIFilmVisibilityButton extends UIIcon
{
    private final UIFilmPanel panel;

    public UIFilmVisibilityButton(UIFilmPanel panel)
    {
        super(Icons.VISIBLE, (button) -> FilmVisibilityController.openMenu(panel.getContext()));

        this.panel = panel;
        this.tooltip(FilmVisibilityUIKeys.TITLE, Direction.BOTTOM);
    }

    @Override
    public boolean isEnabled()
    {
        return super.isEnabled() && this.panel.getData() != null;
    }
}
