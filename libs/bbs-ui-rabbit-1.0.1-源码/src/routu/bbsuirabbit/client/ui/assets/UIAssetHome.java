package routu.bbsuirabbit.client.ui.assets;

import routu.bbsuirabbit.RabbitMod;
import routu.bbsuirabbit.client.ui.home.Gfx;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueRecentData;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.class_308;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The asset workbench that replaces the landing screen of the model, particle and audio
 * editors: header, folder tree, recent strip and the grid/list of entries, styled after
 * the film library home. Click detection is uniform: every interactive patch registers a
 * {@link Hit} while rendering and input only reads those.
 */
public class UIAssetHome extends UIElement
{
    private static final int HEADER = 64;
    private static final int TREE_W = 160;
    private static final int TREE_ROW = 20;
    private static final int LIST_ROW = 32;
    private static final int PAD = 12;

    /* Hit kinds */
    private static final int HIT_ENTRY = 0;
    private static final int HIT_STAR = 1;
    private static final int HIT_NEW = 2;
    private static final int HIT_SORT_NAME = 3;
    private static final int HIT_SORT_RECENT = 4;
    private static final int HIT_RECENT = 5;
    private static final int HIT_FOLDER_BTN = 6;
    private static final int HIT_MORE_BTN = 7;

    private static final class Hit
    {
        final Area area = new Area();
        String id;
        int kind;
    }

    private final AssetHomeSource source;
    private final ILandingHost host;
    private final AssetHomePrefs.Entry prefs;

    private final UITextbox search;

    private AssetIndex index;
    private boolean loading = true;
    private Set<String> known = new HashSet<>();

    private final Scroll treeScroll = new Scroll(new Area());
    private final Scroll mainScroll = new Scroll(new Area());
    private final List<TreeRow> treeRows = new ArrayList<>();

    /* Per-frame clickable patches, filled while rendering */
    private final List<Hit> hits = new ArrayList<>();
    private int hitCount;
    private final Area hoverThumb = new Area();
    private String hoverThumbId;

    private String menuId;
    private int menuKind;
    private String hoveredId;

    /* One-shot guards: a broken source disables its previews for the session, not the game */
    private boolean thumbBroken;
    private boolean tickBroken;

    /* displayPath (tree/breadcrumb) vs real id; identical for every source but audio */
    private final Map<String, String> displayToId = new HashMap<>();
    private final Map<String, String> idToDisplay = new HashMap<>();
    private final Map<String, Float> hoverT = new HashMap<>();
    private float capsuleY = -1;
    private long lastFrame = -1;
    private int loadDots;

    /* visibleIds cache */
    private List<String> lastIds = new ArrayList<>();
    private String lastSig = "";
    private int indexGen;

    private static class TreeRow
    {
        AssetIndex.Folder folder;
        int depth;
        boolean special;
        boolean favorites;
        boolean hasChildren;
    }

    public UIAssetHome(AssetHomeSource source)
    {
        this.source = source;
        this.host = source.host();
        this.prefs = AssetHomePrefs.of(this.host.getRecentType());

        this.treeScroll.scrollSpeed = TREE_ROW;
        this.treeScroll.cancelScrolling();
        this.mainScroll.scrollSpeed = 24;
        this.mainScroll.cancelScrolling();

        this.search = new HomeSearch(200, (s) -> {});
        this.search.background(false);
        this.search.placeholder(L10n.lang("rabbit.ui.assets.search"));

        this.add(this.search);
        this.context(this::buildHomeMenu);
    }

    private void buildHomeMenu(ContextMenuManager menu)
    {
        if (this.menuKind == 1)
        {
            this.buildMoreMenu(menu);
        }
        else
        {
            this.buildEntryMenu(menu);
        }
    }

    private void buildMoreMenu(ContextMenuManager menu)
    {
        menu.action(Icons.DISCORD, IKey.constant("Discord"), () -> UIUtils.openWebLink(UILandingScreen.DISCORD_LINK));
        menu.action(Icons.PLAY, UIKeys.SUPPORTERS_TUTORIALS, () -> UIUtils.openWebLink(UILandingScreen.TUTORIALS_LINK));
        menu.action(Icons.HELP, UIKeys.SUPPORTERS_WIKI, () -> UIUtils.openWebLink(UILandingScreen.WIKI_LINK));
        menu.action(Icons.FOLDER, L10n.lang("rabbit.ui.assets.data_manager"), () -> this.host.openDataManager());
    }

    public void fillNames(Collection<String> names)
    {
        this.loading = false;

        List<String> display = new ArrayList<>(names.size());

        this.displayToId.clear();
        this.idToDisplay.clear();

        for (String id : names)
        {
            String path = this.source.displayPath(id);

            display.add(path);
            this.displayToId.put(path, id);
            this.idToDisplay.put(id, path);
        }

        this.index = AssetIndex.build(display);
        this.known = new HashSet<>(names);
        this.indexGen++;
        AudioAssetSource.clearCache();
    }

    private String real(String display)
    {
        return this.displayToId.getOrDefault(display, display);
    }

    private List<String> recentIds()
    {
        List<String> out = new ArrayList<>();

        for (Object entry : BBSSettings.recentData.get(this.host.getRecentType()))
        {
            String id = ((ValueRecentData.Entry) entry).id;

            if (this.known.contains(id))
            {
                out.add(id);
            }

            if (out.size() >= 8)
            {
                break;
            }
        }

        return out;
    }

    private List<String> visibleIds()
    {
        if (this.index == null)
        {
            return new ArrayList<>();
        }

        String q = this.search.getText().trim();
        String sig = this.indexGen + "|" + this.prefs.folder + "|" + q + "|" + this.prefs.sort + "|"
            + this.prefs.favorites.size() + "|" + BBSSettings.recentData.get(this.host.getRecentType()).size();

        if (!sig.equals(this.lastSig))
        {
            this.lastSig = sig;

            boolean favsOnly = this.prefs.folder.equals("@");
            String folder = favsOnly ? "" : this.prefs.folder;
            Set<String> favDisplay = new HashSet<>();

            for (String fav : this.prefs.favorites)
            {
                favDisplay.add(this.idToDisplay.getOrDefault(fav, fav));
            }

            this.lastIds = this.index.list(folder, q, this.prefs.sort, this.recentIds(), favDisplay, favsOnly);
        }

        return this.lastIds;
    }

    /* Hit helpers */

    private Area hit(int kind, String id, int x, int y, int w, int h)
    {
        Hit hit;

        if (this.hitCount < this.hits.size())
        {
            hit = this.hits.get(this.hitCount);
        }
        else
        {
            hit = new Hit();
            this.hits.add(hit);
        }

        this.hitCount++;
        hit.kind = kind;
        hit.id = id;

        /* Cards scrolled half under the header must not be clickable through it */
        if (kind == HIT_ENTRY || kind == HIT_RECENT || kind == HIT_STAR)
        {
            Area main = this.mainScroll.area;
            int x1 = Math.max(x, main.x);
            int y1 = Math.max(y, main.y);
            int x2 = Math.min(x + w, main.ex());
            int y2 = Math.min(y + h, main.ey());

            hit.area.set(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
        }
        else
        {
            hit.area.set(x, y, w, h);
        }

        return hit.area;
    }

    private Hit hitAt(int mx, int my, int... kinds)
    {
        for (int i = 0; i < this.hitCount; i++)
        {
            Hit hit = this.hits.get(i);

            if (!hit.area.isInside(mx, my))
            {
                continue;
            }

            for (int kind : kinds)
            {
                if (hit.kind == kind)
                {
                    return hit;
                }
            }
        }

        return null;
    }

    /* Layout */

    @Override
    public void resize()
    {
        super.resize();

        int y = this.area.y + (HEADER - 22) / 2;
        int right = this.area.ex() - PAD - 30; /* more button */
        right -= 30; /* folder button */
        right -= this.host.getCreateLabel() != null ? 92 : 0; /* new pill */
        right -= 92; /* sort segments */
        this.search.area.set(right - 180, y, 180, 22);

        this.rebuildTreeRows();

        this.treeScroll.area.set(this.area.x, this.area.y + HEADER, TREE_W, this.area.h - HEADER);
        this.mainScroll.area.set(this.area.x + TREE_W, this.area.y + HEADER, this.area.w - TREE_W, this.area.h - HEADER);
    }

    private void rebuildTreeRows()
    {
        this.treeRows.clear();

        if (this.index == null)
        {
            return;
        }

        TreeRow favs = new TreeRow();

        favs.special = true;
        favs.favorites = true;
        this.treeRows.add(favs);

        TreeRow all = new TreeRow();

        all.special = true;
        this.treeRows.add(all);

        for (AssetIndex.Folder child : this.index.root().children)
        {
            this.addTreeRows(child, 0);
        }
    }

    private void addTreeRows(AssetIndex.Folder folder, int depth)
    {
        TreeRow row = new TreeRow();

        row.folder = folder;
        row.depth = depth;
        row.hasChildren = !folder.children.isEmpty();
        this.treeRows.add(row);

        if (this.prefs.expanded.contains(folder.path))
        {
            for (AssetIndex.Folder child : folder.children)
            {
                this.addTreeRows(child, depth + 1);
            }
        }
    }

    /* Input */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        if (context.mouseButton == 1)
        {
            Hit hit = this.hitAt(context.mouseX, context.mouseY, HIT_ENTRY, HIT_RECENT, HIT_STAR, HIT_MORE_BTN);

            this.menuId = hit == null ? null : hit.id;
            this.menuKind = hit != null && hit.kind == HIT_MORE_BTN ? 1 : 0;

            return super.subMouseClicked(context);
        }

        if (context.mouseButton != 0)
        {
            return super.subMouseClicked(context);
        }

        Hit star = this.hitAt(context.mouseX, context.mouseY, HIT_STAR);

        if (star != null)
        {
            this.toggleFavorite(star.id);

            return true;
        }

        Hit hit = this.hitAt(context.mouseX, context.mouseY, HIT_ENTRY, HIT_RECENT, HIT_NEW, HIT_SORT_NAME, HIT_SORT_RECENT, HIT_FOLDER_BTN, HIT_MORE_BTN);

        if (hit != null)
        {
            switch (hit.kind)
            {
                case HIT_ENTRY, HIT_RECENT -> this.host.pickData(hit.id);
                case HIT_NEW -> this.host.addNewData(context);
                case HIT_SORT_NAME -> this.setSort(AssetIndex.Sort.NAME);
                case HIT_SORT_RECENT -> this.setSort(AssetIndex.Sort.RECENT);
                case HIT_FOLDER_BTN ->
                {
                    File folder = this.host.getDataFolder();

                    if (folder != null)
                    {
                        UIUtils.openFolder(folder);
                    }
                }
                case HIT_MORE_BTN ->
                {
                    this.menuKind = 1;

                    UIContextMenu menu = this.createContextMenu(context);

                    if (menu != null && !menu.isEmpty())
                    {
                        context.setContextMenu(menu);
                    }
                }
            }

            return true;
        }

        TreeRow row = this.treeRowAt(context.mouseY);

        if (row != null && context.mouseX < this.area.x + TREE_W)
        {
            int arrowX = this.area.x + 4 + row.depth * 10;

            if (row.hasChildren && context.mouseX >= arrowX && context.mouseX < arrowX + 12)
            {
                if (!this.prefs.expanded.remove(row.folder.path))
                {
                    this.prefs.expanded.add(row.folder.path);
                }

                AssetHomePrefs.save();
                this.rebuildTreeRows();

                return true;
            }

            this.prefs.folder = row.favorites ? "@" : (row.special ? "" : row.folder.path);
            AssetHomePrefs.save();

            return true;
        }

        return super.subMouseClicked(context);
    }

    private void setSort(AssetIndex.Sort sort)
    {
        if (this.prefs.sort != sort)
        {
            this.prefs.sort = sort;
            AssetHomePrefs.save();
        }
    }

    @Override
    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context))
        {
            if (context.isPressed(GLFW.GLFW_KEY_LEFT_CONTROL) && !this.source.listStyle() && this.mainScroll.area.isInside(context))
            {
                this.prefs.cell = Math.max(64, Math.min(160, this.prefs.cell + (context.mouseWheel > 0 ? 8 : -8)));
                AssetHomePrefs.save();

                return this;
            }

            if (context.mouseX < this.area.x + TREE_W && this.treeScroll.mouseScroll(context))
            {
                return this;
            }

            if (this.mainScroll.mouseScroll(context))
            {
                return this;
            }
        }

        return super.childrenMouseScrolled(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.treeScroll.mouseReleased(context);
        this.mainScroll.mouseReleased(context);

        return super.subMouseReleased(context);
    }

    private TreeRow treeRowAt(int y)
    {
        int index = (int) ((y - this.treeScroll.area.y + this.treeScroll.getScroll()) / TREE_ROW);

        return index >= 0 && index < this.treeRows.size() ? this.treeRows.get(index) : null;
    }

    private boolean toggleFavorite(String id)
    {
        if (!this.prefs.favorites.remove(id))
        {
            this.prefs.favorites.add(id);
        }

        AssetHomePrefs.save();

        return true;
    }

    /* Context menu on entries */

    private void buildEntryMenu(ContextMenuManager menu)
    {
        String id = this.menuId;

        if (id == null)
        {
            return;
        }

        menu.action(Icons.PLAY, L10n.lang("rabbit.ui.assets.open"), () -> this.host.pickData(id));

        boolean fav = this.prefs.favorites.contains(id);

        menu.action(Icons.FAVORITE, L10n.lang(fav ? "rabbit.ui.assets.unfavorite" : "rabbit.ui.assets.favorite"), () -> this.toggleFavorite(id));
        menu.action(Icons.SEARCH, L10n.lang("rabbit.ui.assets.show_in_manager"), () -> this.host.showInList(id));

        File folder = this.host.getDataFolder();
        int slash = id.lastIndexOf('/');

        if (folder != null && slash > 0)
        {
            File parent = new File(folder, id.substring(0, slash));

            if (parent.isDirectory())
            {
                menu.action(Icons.FOLDER, L10n.lang("rabbit.ui.assets.open_parent_folder"), () -> UIUtils.openFolder(parent));
            }
        }

        menu.action(Icons.COPY, L10n.lang("rabbit.ui.assets.copy_id"), () -> mchorse.bbs_mod.graphics.window.Window.setClipboard(id));

        if (this.recentIds().contains(id))
        {
            menu.action(Icons.REMOVE, L10n.lang("rabbit.ui.assets.remove_recent"), () -> BBSSettings.recentData.forget(this.host.getRecentType(), id));
        }

        this.source.extraMenu(menu, id);
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        Batcher2D batcher = context.batcher;

        this.hitCount = 0;
        this.hoveredId = null;
        this.hoverThumbId = null;

        batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Gfx.base(1F));

        this.renderHeader(context);
        this.renderTree(context);
        this.renderMain(context);

        super.render(context);

        /* Live particle preview plays over the hovered card's thumbnail - a source that
         * blows up once (runtime API drift, corrupt data) is disabled for the session */
        if (!this.tickBroken)
        {
            GLStateGuard guard = GLStateGuard.capture(context);

            try
            {
                this.source.tick(context, this.hoverThumbId, this.hoverThumb.x, this.hoverThumb.y, this.hoverThumb.w, this.hoverThumb.h);
            }
            catch (Throwable t)
            {
                guard.restore(context);
                this.tickBroken = true;
                RabbitMod.LOGGER.warn("Asset home preview tick failed; live preview disabled for this panel", t);
            }
        }
    }

    private void renderHeader(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int y = this.area.y;

        batcher.box(this.area.x, y, this.area.ex(), y + HEADER, Gfx.raised(0.6F));
        batcher.box(this.area.x, y + HEADER - 1, this.area.ex(), y + HEADER, Gfx.divider(0.6F));

        /* Icon badge */
        Gfx.roundRect(batcher, this.area.x + PAD, y + (HEADER - 32) / 2, 32, 32, 8, Gfx.accent(0.18F));
        batcher.icon(this.source.icon(), Gfx.accent(), this.area.x + PAD + 16, y + HEADER / 2, 0.5F, 0.5F);

        int tx = this.area.x + PAD + 42;

        Gfx.text(batcher, this.host.getTitle().get(), tx, y + 10, 1.5F, Gfx.ink(1F));

        int total = this.index == null ? 0 : this.index.count("");
        String sub = L10n.lang("rabbit.ui.assets.total_sub").format(total, this.prefs.favorites.size()).get();

        batcher.text(sub, tx, y + 34, Gfx.inkMuted(0.9F));

        /* Right-aligned controls are measured right to left (more, folder, new pill,
         * sort), the search box takes what is left so nothing runs off the edge */
        int controls = 28 + (this.host.getDataFolder() != null ? 32 : 0) + 100 + 12;

        if (this.host.getCreateLabel() != null)
        {
            controls += font.getWidth("＋ " + this.host.getCreateLabel().get()) + 22 + 8;
        }

        int titleEnd = tx + Math.max((int) (font.getWidth(this.host.getTitle().get()) * 1.5F), font.getWidth(sub)) + 32;
        int searchEnd = this.area.ex() - PAD - controls;
        int searchW = Math.max(60, Math.min(180, searchEnd - titleEnd));

        this.search.area.set(searchEnd - searchW, this.search.area.y, searchW, this.search.area.h);

        /* Search capsule around the textbox */
        Area s = this.search.area;

        Gfx.roundRect(batcher, s.x - 20, s.y - 2, s.w + 28, s.h + 4, (s.h + 4) / 2F, Gfx.deep(0.7F));
        batcher.icon(Icons.SEARCH, Gfx.inkMuted(0.8F), s.x - 11, s.y + s.h / 2F, 0.5F, 0.5F);

        /* Sort segments */
        int sortW = 44;
        int sx = s.ex() + 12;
        int sy = s.y - 2;
        int sh = s.h + 4;

        Gfx.roundRect(batcher, sx, sy, sortW * 2 + 4, sh, 8, Gfx.deep(0.7F));
        this.hit(HIT_SORT_NAME, null, sx, sy, sortW + 2, sh);
        this.hit(HIT_SORT_RECENT, null, sx + sortW + 2, sy, sortW + 2, sh);

        String name = L10n.lang("rabbit.ui.assets.sort_name").get();
        String recent = L10n.lang("rabbit.ui.assets.sort_recent").get();
        boolean nameOn = this.prefs.sort == AssetIndex.Sort.NAME;

        if (nameOn)
        {
            Gfx.roundRect(batcher, sx + 2, sy + 2, sortW, sh - 4, 6, Gfx.accent(0.85F));
        }
        else
        {
            Gfx.roundRect(batcher, sx + sortW + 2, sy + 2, sortW, sh - 4, 6, Gfx.accent(0.85F));
        }

        batcher.text(name, sx + 2 + (sortW - font.getWidth(name)) / 2, sy + (sh - font.getHeight()) / 2 + 1, nameOn ? Gfx.onAccent() : Gfx.inkMuted(0.9F));
        batcher.text(recent, sx + sortW + 2 + (sortW - font.getWidth(recent)) / 2, sy + (sh - font.getHeight()) / 2 + 1, nameOn ? Gfx.inkMuted(0.9F) : Gfx.onAccent());

        /* New pill */
        int bx = sx + sortW * 2 + 12;

        if (this.host.getCreateLabel() != null)
        {
            String label = "＋ " + this.host.getCreateLabel().get();
            int w = font.getWidth(label) + 22;
            boolean hov = context.mouseX >= bx && context.mouseX <= bx + w && context.mouseY >= sy && context.mouseY <= sy + sh;

            Gfx.roundRect(batcher, bx, sy, w, sh, 8, hov ? Gfx.accent() : Gfx.accent(0.75F));
            batcher.text(label, bx + (w - font.getWidth(label)) / 2, sy + (sh - font.getHeight()) / 2 + 1, Gfx.onAccent());
            this.hit(HIT_NEW, null, bx, sy, w, sh);

            bx += w + 8;
        }

        /* Folder + more round buttons */
        if (this.host.getDataFolder() != null)
        {
            boolean hov = context.mouseX >= bx && context.mouseX <= bx + 28 && context.mouseY >= sy && context.mouseY <= sy + sh;

            Gfx.circle(batcher, bx + 14, sy + sh / 2F, 13, hov ? Gfx.accent(0.3F) : Gfx.wash(0.1F));
            batcher.icon(Icons.FOLDER, Gfx.ink(1F), bx + 14, sy + sh / 2F, 0.5F, 0.5F);
            this.hit(HIT_FOLDER_BTN, null, bx, sy, 28, sh);

            bx += 32;
        }

        boolean hovM = context.mouseX >= bx && context.mouseX <= bx + 28 && context.mouseY >= sy && context.mouseY <= sy + sh;

        Gfx.circle(batcher, bx + 14, sy + sh / 2F, 13, hovM ? Gfx.accent(0.3F) : Gfx.wash(0.1F));
        batcher.icon(Icons.MORE, Gfx.ink(1F), bx + 14, sy + sh / 2F, 0.5F, 0.5F);
        this.hit(HIT_MORE_BTN, null, bx, sy, 28, sh);
    }

    private void renderTree(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        Area area = this.treeScroll.area;

        this.rebuildTreeRows();
        this.treeScroll.scrollSize = this.treeRows.size() * TREE_ROW;
        this.treeScroll.clamp();
        this.treeScroll.drag(context.mouseX, context.mouseY);

        batcher.box(area.x, area.y, area.ex(), area.ey(), Gfx.deep(0.5F));
        batcher.box(area.ex() - 1, area.y, area.ex(), area.ey(), Gfx.divider(0.5F));

        /* Sliding accent capsule behind the selected row */
        int selIndex = -1;
        String selected = this.prefs.folder;

        for (int i = 0; i < this.treeRows.size(); i++)
        {
            TreeRow row = this.treeRows.get(i);
            String rowId = row.favorites ? "@" : (row.special ? "" : row.folder.path);

            if (rowId.equals(selected))
            {
                selIndex = i;

                break;
            }
        }

        if (selIndex >= 0)
        {
            float target = area.y + selIndex * TREE_ROW - (float) this.treeScroll.getScroll();

            if (this.capsuleY < 0)
            {
                this.capsuleY = target;
            }

            long now = System.nanoTime() / 1000000;
            float dt = Math.min(0.1F, this.lastFrame < 0 ? 0.016F : (now - this.lastFrame) / 1000F);

            this.lastFrame = now;
            this.capsuleY = Gfx.approach(this.capsuleY, target, 900F, dt);
            Gfx.roundRect(batcher, area.x + 2, this.capsuleY + 1, area.w - 4, TREE_ROW - 2, 5, Gfx.accent(0.35F));
            batcher.box(area.x + 2, this.capsuleY + 1, area.x + 4, this.capsuleY + TREE_ROW - 1, Gfx.accent());
        }

        batcher.clip(area, context);

        for (int i = 0; i < this.treeRows.size(); i++)
        {
            TreeRow row = this.treeRows.get(i);
            int y = area.y + i * TREE_ROW - (int) this.treeScroll.getScroll();

            if (y + TREE_ROW <= area.y || y >= area.ey())
            {
                continue;
            }

            String rowId = row.favorites ? "@" : (row.special ? "" : row.folder.path);
            boolean sel = i == selIndex;
            boolean hovered = context.mouseX >= area.x && context.mouseX <= area.ex() && context.mouseY >= y && context.mouseY < y + TREE_ROW && this.area.isInside(context);

            if (!sel && hovered)
            {
                batcher.box(area.x, y, area.ex(), y + TREE_ROW, Gfx.wash(0.08F));
            }

            int x = area.x + 4 + row.depth * 10;
            int my = y + TREE_ROW / 2;

            if (row.hasChildren)
            {
                mchorse.bbs_mod.ui.framework.elements.UISection.renderArrow(context, x + 5, my, this.prefs.expanded.contains(row.folder.path), Gfx.inkMuted(0.9F));
            }

            x += 12;

            mchorse.bbs_mod.ui.utils.icons.Icon icon = row.favorites ? Icons.FAVORITE : (row.special ? Icons.LIST : Icons.FOLDER);

            batcher.icon(icon, sel ? Gfx.onAccent() : Gfx.inkSoft(1F), x + 8, my, 0.5F, 0.5F);

            String label = row.favorites ? L10n.lang("rabbit.ui.morph.favorites").get() : (row.special ? L10n.lang("rabbit.ui.assets.all").get() : row.folder.name);
            int count = row.favorites ? this.prefs.favorites.size() : (row.special ? this.index.count("") : this.index.count(row.folder.path));
            int textColor = sel ? Gfx.onAccent() : Gfx.ink(1F);
            String countStr = String.valueOf(count);
            int pillW = font.getWidth(countStr) + 8;
            int maxW = area.ex() - 8 - pillW - x - 18;

            batcher.textShadow(font.limitToWidth(label, maxW), x + 18, y + (TREE_ROW - font.getHeight()) / 2 + 1, textColor);

            Gfx.roundRect(batcher, area.ex() - 6 - pillW, y + (TREE_ROW - 14) / 2, pillW, 14, 7, Gfx.wash(0.1F));
            batcher.text(countStr, area.ex() - 6 - pillW + 4, y + (TREE_ROW - font.getHeight()) / 2 + 1, Gfx.inkMuted(0.8F));
        }

        batcher.unclip(context);

        this.treeScroll.renderScrollbar(batcher);
    }

    private void renderMain(UIContext context)
    {
        Area main = this.mainScroll.area;
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        this.mainScroll.drag(context.mouseX, context.mouseY);

        if (this.loading)
        {
            this.loadDots = (this.loadDots + 1) % 60;
            String dots = ".".repeat(this.loadDots / 20 + 1);
            String text = L10n.lang("rabbit.ui.assets.loading").get() + " " + dots;

            batcher.text(text, main.mx() - font.getWidth(text) / 2, main.my() - font.getHeight() / 2, Gfx.inkMuted(0.9F));

            return;
        }

        List<String> ids = this.visibleIds();
        List<String> recent = this.recentStrip();
        int recentH = recent.isEmpty() ? 0 : 16 + 96 + this.cardCaption() + 16;
        int contentH = recentH + (this.source.listStyle() ? 26 + ids.size() * LIST_ROW : 26 + rowsOf(ids.size(), main) * (this.prefs.cell + this.cardCaption() + 8)) + 60;

        this.mainScroll.scrollSize = Math.max(0, contentH);
        this.mainScroll.clamp();

        batcher.clip(main, context);

        try
        {
            this.renderMainClipped(context, main, recent, recentH, ids);
        }
        finally
        {
            batcher.unclip(context);
        }

        this.mainScroll.renderScrollbar(batcher);
    }

    private void renderMainClipped(UIContext context, Area main, List<String> recent, int recentH, List<String> ids)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int scroll = (int) this.mainScroll.getScroll();
        int y = main.y + 12 - scroll;

        if (!recent.isEmpty())
        {
            batcher.icon(Icons.TIME, Gfx.accent(), main.x + PAD + 8, y + 6, 0.5F, 0.5F);
            batcher.textShadow(L10n.lang("rabbit.ui.assets.recent").get(), main.x + PAD + 18, y + 2, Gfx.inkSoft(1F));

            int rx = main.x + PAD;
            int ry = y + 18;

            for (String id : recent)
            {
                this.renderCard(context, this.idToDisplay.getOrDefault(id, id), rx, ry, 96, 96 + this.cardCaption(), true);
                this.hit(HIT_RECENT, id, rx, ry, 96, 96 + this.cardCaption());
                rx += 104;
            }
        }

        /* Breadcrumb + count pill */
        int cy = main.y + recentH + 4 - scroll;
        String crumb = this.prefs.folder.equals("@") ? L10n.lang("rabbit.ui.morph.favorites").get() : (this.prefs.folder.isEmpty() ? L10n.lang("rabbit.ui.assets.all").get() : this.prefs.folder.replace('/', '›'));

        batcher.textShadow(crumb, main.x + PAD, cy + 4, Gfx.inkSoft(1F));

        String countStr = String.valueOf(ids.size());
        int pillW = font.getWidth(countStr) + 10;
        int pillX = main.x + PAD + font.getWidth(crumb) + 10;

        Gfx.roundRect(batcher, pillX, cy + 1, pillW, 14, 7, Gfx.wash(0.1F));
        batcher.text(countStr, pillX + 5, cy + 4, Gfx.inkMuted(0.9F));

        if (ids.isEmpty())
        {
            this.renderEmpty(context, main);
        }
        else if (this.source.listStyle())
        {
            this.renderList(context, ids, main, recentH + 24, scroll);
        }
        else
        {
            this.renderGrid(context, ids, main, recentH + 24, scroll);
        }
    }

    private List<String> recentStrip()
    {
        if (!this.prefs.folder.isEmpty() || !this.search.getText().trim().isEmpty())
        {
            return List.of();
        }

        return this.recentIds();
    }

    private int cardCaption()
    {
        return this.prefs.cell >= 96 ? 30 : 20;
    }

    private int rowsOf(int n, Area main)
    {
        int cols = Math.max(1, (main.w - PAD) / (this.prefs.cell + 8));

        return (n + cols - 1) / cols;
    }

    private void renderGrid(UIContext context, List<String> ids, Area main, int top, int scroll)
    {
        int cell = this.prefs.cell;
        int gap = 8;
        int cols = Math.max(1, (main.w - PAD) / (cell + gap));
        int cap = this.cardCaption();

        for (int i = 0; i < ids.size(); i++)
        {
            int col = i % cols;
            int row = i / cols;
            int x = main.x + PAD + col * (cell + gap);
            int y = main.y + top + row * (cell + cap + gap) - scroll;

            if (y + cell + cap <= main.y || y >= main.ey())
            {
                continue;
            }

            this.renderCard(context, ids.get(i), x, y, cell, cell + cap, false);
            this.hit(HIT_ENTRY, this.real(ids.get(i)), x, y, cell, cell + cap);
        }
    }

    private void renderList(UIContext context, List<String> ids, Area main, int top, int scroll)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");

        for (int i = 0; i < ids.size(); i++)
        {
            int y = main.y + top + i * LIST_ROW - scroll;

            if (y + LIST_ROW <= main.y || y >= main.ey())
            {
                continue;
            }

            String id = ids.get(i);
            float t = this.hover(id, context.mouseX >= main.x && context.mouseX <= main.ex() && context.mouseY >= y && context.mouseY < y + LIST_ROW - 3);
            int lift = Math.round(2 * t);

            if (i % 2 == 0)
            {
                Gfx.roundRect(batcher, main.x + 2, y, main.w - 4, LIST_ROW - 3, 6, Gfx.wash(0.04F));
            }

            if (t > 0.001F)
            {
                this.hoveredId = id;
                Gfx.roundRect(batcher, main.x + 2, y - lift, main.w - 4, LIST_ROW - 3, 6, Gfx.wash(0.08F));
                Gfx.roundOutline(batcher, main.x + 2, y - lift, main.w - 4, LIST_ROW - 3, 6, 1, Gfx.accent(0.5F * t));
            }

            Gfx.roundRect(batcher, main.x + 8, y + 3 - lift, 24, 24, 6, Gfx.accent(0.18F));
            batcher.icon(Icons.SOUND, Gfx.accent(), main.x + 20, y + 15 - lift, 0.5F, 0.5F);

            int textX = main.x + 40;
            String name = AssetIndex.displayName(id);
            String folder = id.lastIndexOf('/') > 0 ? id.substring(0, id.lastIndexOf('/')) : "";

            batcher.textShadow(font.limitToWidth(name, main.w - 220), textX, y + 4 - lift, Gfx.ink(1F));
            batcher.text(font.limitToWidth(folder, main.w - 220), textX, y + 17 - lift, Gfx.inkMuted(0.7F));

            String real = this.real(id);

            /* Waveform column between the name and the extension pill (audio only) */
            if (this.source instanceof AudioAssetSource)
            {
                int wx = main.ex() - 360;
                int wy = y + 7 - lift;
                int wh = LIST_ROW - 16;
                mchorse.bbs_mod.audio.Waveform waveform = AudioAssetSource.waveformOf(real);

                if (waveform != null)
                {
                    waveform.render(batcher, Gfx.accent(0.9F), wx, wy, 180, wh, 0, AudioAssetSource.durationOf(real));
                }
                else if (!AudioAssetSource.isFailed(real))
                {
                    batcher.box(wx, wy + wh / 2, wx + 180, wy + wh / 2 + 1, Gfx.wash(0.15F));
                    this.source.requestThumb(real);
                }
            }

            String ext = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1).toUpperCase() : "";
            int px = main.ex() - 170;

            if (!ext.isEmpty())
            {
                int pillW = font.getWidth(ext) + 8;

                Gfx.roundRect(batcher, px, y + 9 - lift, pillW, 14, 7, Gfx.wash(0.1F));
                batcher.text(ext, px + 4, y + 12 - lift, Gfx.inkMuted(0.9F));
            }

            File file = AudioAssetSource.fileOf(real);

            if (file != null)
            {
                batcher.text(fmt.format(new Date(file.lastModified())), main.ex() - 100, y + 4 - lift, Gfx.inkMuted(0.8F));

                String stats = sizeOf(file.length());
                float dur = AudioAssetSource.durationOf(real);

                if (dur > 0)
                {
                    stats += " - " + (int) (dur / 60) + ":" + String.format("%02d", (int) (dur % 60));
                }

                batcher.text(stats, main.ex() - 100, y + 17 - lift, Gfx.inkMuted(0.8F));
            }

            this.hit(HIT_ENTRY, real, main.x + 2, y, main.w - 4, LIST_ROW - 3);
        }
    }

    private static String sizeOf(long bytes)
    {
        if (bytes >= 1 << 20) return String.format("%.1f MB", bytes / 1048576.0);
        if (bytes >= 1 << 10) return String.format("%.0f KB", bytes / 1024.0);

        return bytes + " B";
    }

    private float hover(String id, boolean hovered)
    {
        float t = this.hoverT.getOrDefault(id, 0F);
        float target = hovered ? 1F : 0F;
        float next = t + (target - t) * 0.3F;

        this.hoverT.put(id, next);

        return next;
    }

    private void renderEmpty(UIContext context, Area main)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int cx = main.x + main.w / 2;
        int cy = main.y + main.h / 2 - 40;
        boolean canNew = this.host.getCreateLabel() != null;
        int h = canNew ? 120 : 80;

        Gfx.roundRect(batcher, cx - 130, cy - 40, 260, h, 12, Gfx.raised(0.5F));
        Gfx.roundRect(batcher, cx - 20, cy - 28, 40, 40, 10, Gfx.accent(0.18F));
        batcher.icon(this.source.icon(), Gfx.accent(), cx, cy - 8, 0.5F, 0.5F);

        String text = L10n.lang("rabbit.ui.assets.empty").get();

        batcher.text(text, cx - font.getWidth(text) / 2, cy + 22, Gfx.inkMuted(0.9F));

        if (canNew)
        {
            String label = "＋ " + this.host.getCreateLabel().get();
            int w = font.getWidth(label) + 24;
            int bx = cx - w / 2;
            int by = cy + 42;
            boolean hov = context.mouseX >= bx && context.mouseX <= bx + w && context.mouseY >= by && context.mouseY <= by + 26;

            Gfx.roundRect(batcher, bx, by, w, 26, 7, hov ? Gfx.accent() : Gfx.accent(0.7F));
            batcher.text(label, cx - font.getWidth(label) / 2, by + 8, Gfx.onAccent());
            this.hit(HIT_NEW, null, bx, by, w, 26);
        }
    }

    private void renderCard(UIContext context, String id, int x, int y, int w, int h, boolean recent)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        boolean hovered = context.mouseX >= x && context.mouseX <= x + w && context.mouseY >= y && context.mouseY <= y + h && this.mainScroll.area.isInside(context);
        float t = this.hover(id, hovered);
        int lift = Math.round(3 * t);
        int cy = y - lift;
        int thumbH = recent ? h - 20 : h - this.cardCaption();

        if (hovered && !recent)
        {
            this.hoveredId = id;
        }

        Gfx.shadow(batcher, x, cy, w, h, 8, 4 + (int) (2 * t), (0x40 + (int) (0x50 * t)) << 24);
        Gfx.roundRect(batcher, x, cy, w, h, 8, Gfx.raised(0.9F));

        Gfx.roundRect(batcher, x + 3, cy + 3, w - 6, thumbH - 6, 6, Gfx.deep(0.7F));
        Gfx.radial(batcher, x + w / 2F, cy + thumbH / 2F, w * 0.7F, Gfx.accent(0.05F + 0.08F * t), 0);

        String real = this.real(id);

        /* Static sprite stays under the live preview (particles overlay on top) */
        boolean liveHere = hovered && !recent;

        if (liveHere)
        {
            this.hoverThumbId = real;
            this.hoverThumb.set(x + 3, cy + 3, w - 6, thumbH - 6);
        }

        if (!this.thumbBroken && (!liveHere || !(this.source instanceof ParticleAssetSource)))
        {
            class_308.method_24211();

            GLStateGuard guard = GLStateGuard.capture(context);

            try
            {
                this.source.renderThumb(context, real, x + 3, cy + 3, w - 6, thumbH - 6, hovered);
                this.source.requestThumb(real);
            }
            catch (Throwable th)
            {
                guard.restore(context);
                this.thumbBroken = true;
                RabbitMod.LOGGER.warn("Asset home thumbnail failed; previews disabled for this panel", th);
            }
            finally
            {
                class_308.method_24210();
            }
        }

        String name = AssetIndex.displayName(id);

        batcher.text(font.limitToWidth(name, w - 8), x + 6, cy + thumbH + 3, t > 0.5F ? Gfx.ink(1F) : Gfx.inkSoft(1F));

        if (thumbH >= 96 - 24 && w >= 96)
        {
            String folder = id.lastIndexOf('/') > 0 ? id.substring(0, id.lastIndexOf('/')) : "";

            batcher.text(font.limitToWidth(folder, w - 8), x + 6, cy + thumbH + font.getHeight() + 6, Gfx.inkMuted(0.6F));
        }

        if (t > 0.01F)
        {
            Gfx.roundOutline(batcher, x, cy, w, h, 8, 1, Gfx.accent(0.9F * t));
        }

        /* Favourite star — follows hover alpha */
        if (hovered || this.prefs.favorites.contains(real))
        {
            int sx = x + w - 18;
            int sy = cy + 4;
            boolean fav = this.prefs.favorites.contains(real);

            Gfx.circle(batcher, sx + 8, sy + 8, 9, fav ? Gfx.accent(0.9F) : Gfx.wash(0.15F + 0.15F * t));
            batcher.icon(Icons.FAVORITE, fav ? Gfx.onAccent() : Gfx.ink(0.4F + 0.6F * t), sx + 8, sy + 8, 0.5F, 0.5F);

            if (!recent)
            {
                this.hit(HIT_STAR, real, sx - 2, sy - 2, 20, 20);
            }
        }
    }

    private class HomeSearch extends UITextbox
    {
        HomeSearch(int maxLength, java.util.function.Consumer<String> callback)
        {
            super(maxLength, callback);
        }

        @Override
        public boolean subKeyPressed(UIContext context)
        {
            if (this.isFocused())
            {
                if (context.isPressed(GLFW.GLFW_KEY_ENTER))
                {
                    List<String> ids = visibleIds();

                    if (!ids.isEmpty())
                    {
                        host.pickData(ids.get(0));
                    }

                    return true;
                }

                if (context.isPressed(GLFW.GLFW_KEY_ESCAPE) && !this.getText().isEmpty())
                {
                    this.setText("");

                    return true;
                }
            }

            return super.subKeyPressed(context);
        }
    }
}
