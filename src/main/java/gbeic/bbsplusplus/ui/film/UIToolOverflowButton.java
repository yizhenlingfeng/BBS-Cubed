package gbeic.bbsplusplus.ui.film;

import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * 预览工具栏最右侧的「工具收纳」按钮（三横线图标）。
 *
 * <p>每帧做两件事：把收纳清单差量同步到各工具按钮的可见性（有变化才重排横条），
 * 以及驱动展开栏的悬停/固定/宽限收起状态机。左键固定或解除固定（回调），右键
 * 弹出收纳配置清单（由 Mixin 通过 {@code context(...)} 挂上）。</p>
 */
public class UIToolOverflowButton extends UIIcon
{
    private final UIFilmPreview preview;
    private final UIToolOverflowPopup popup;

    public UIToolOverflowButton(UIFilmPreview preview, UIToolOverflowPopup popup)
    {
        super(Icons.MORE, (b) -> popup.togglePinned());

        this.preview = preview;
        this.popup = popup;
    }

    @Override
    public void render(UIContext context)
    {
        /* 收纳清单 → 工具按钮可见性（差量同步，仅在变化时重排横条） */
        FilmToolbarOverflow.syncVisibility(this.preview);

        /* 悬停展开 / 宽限收起状态机 */
        this.popup.updateHover(context, this.area);

        super.render(context);
    }
}
