package routu.bbsuirabbit.client.ui.film;

import routu.bbsuirabbit.client.ui.film.FilmLibraryDefaultLocation;
import routu.bbsuirabbit.client.ui.film.IFilmLibraryPathList;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIAllFilmsNavItem
extends UIElement {
    private final IKey label;
    private final UITextbox search;
    private final UIDataPathList list;

    public UIAllFilmsNavItem(IKey label, UITextbox search, UIDataPathList list) {
        this.label = label;
        this.search = search;
        this.list = list;
        this.context(menu -> {
            UIDataPathList patt0$temp = this.list;
            if (patt0$temp instanceof IFilmLibraryPathList) {
                IFilmLibraryPathList pathList = (IFilmLibraryPathList)patt0$temp;
                this.search.setText("");
                pathList.rabbit$showAllFilmLibraryFilms();
            }
            if (!FilmLibraryDefaultLocation.isAllFilms()) {
                menu.action(Icons.BOOKMARK, L10n.lang((String)"rabbit.ui.film.library.set_default_open"), FilmLibraryDefaultLocation::setAllFilms);
            }
        });
    }

    protected boolean subMouseClicked(UIContext context) {
        UIDataPathList uIDataPathList;
        if (context.mouseButton == 0 && this.area.isInside(context) && (uIDataPathList = this.list) instanceof IFilmLibraryPathList) {
            IFilmLibraryPathList pathList = (IFilmLibraryPathList)uIDataPathList;
            this.search.setText("");
            pathList.rabbit$showAllFilmLibraryFilms();
            return true;
        }
        if (context.mouseButton == 1 && this.area.isInside(context) && (uIDataPathList = this.list) instanceof IFilmLibraryPathList) {
            IFilmLibraryPathList pathList = (IFilmLibraryPathList)uIDataPathList;
            this.search.setText("");
            pathList.rabbit$showAllFilmLibraryFilms();
        }
        return false;
    }

    public void render(UIContext context) {
        IFilmLibraryPathList pathList;
        boolean active;
        UIDataPathList uIDataPathList = this.list;
        boolean bl = active = uIDataPathList instanceof IFilmLibraryPathList && (pathList = (IFilmLibraryPathList)uIDataPathList).rabbit$isShowingAllFilmLibraryFilms();
        if (active) {
            context.batcher.box((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), 0x44000000 | (Integer)BBSSettings.primaryColor.get());
        }
        FontRenderer font = context.batcher.getFont();
        int color = active ? -1 : -7829368;
        String text = font.limitToWidth(this.label.get(), this.area.w - 28);
        context.batcher.icon(Icons.FILM, color, (float)(this.area.x + 10), (float)this.area.my(), 0.5f, 0.5f);
        context.batcher.textShadow(text, (float)(this.area.x + 24), (float)this.area.my(font.getHeight()), color);
        super.render(context);
    }
}
