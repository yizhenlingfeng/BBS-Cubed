package routu.bbsuirabbit.client.ui.home;

import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.library.LibraryEntry;
import routu.bbsuirabbit.mixin.FolderManagerRepositoryAccessor;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueRecentData;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.repos.FolderManagerRepository;
import mchorse.bbs_mod.utils.repos.IRepository;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * BBS++'s film library home: what the film panel shows on an empty tab, in place of BBS's landing
 * screen (and of any add-on's restyle of it).
 *
 * <p>Most of it is drawn by hand: a sidebar (all / recent / favourites and the folder tree), a
 * header and toolbar (search, sort, list or grid), the film list, and an inspector for the selected
 * film with a generated poster and its camera timeline. Hit regions are collected while drawing and
 * answered on the next click, so drawing and interaction live side by side.</p>
 */
public class UIFilmLibraryHome extends UIElement
{
    private static final String L = "rabbit.ui.film.home.";

    private static final int SCOPE_ALL = 0;
    private static final int SCOPE_RECENT = 1;
    private static final int SCOPE_FAVORITES = 2;
    private static final int SCOPE_FOLDER = 3;

    private static final float ROW_H = 22;
    private static final int STUDIO_PINK = 0xfffd1b73;
    private static final Link STUDIO_LOGO = Link.assets("textures/bbs_ui_rabbit/routu_logo.png");
    /** The studio (BBS-Rabbit) brand ships with its logo; a build without the logo file shows the plain BBS++ brand. */
    private static final boolean STUDIO_BRAND = UIFilmLibraryHome.class.getClassLoader().getResource("assets/bbs/assets/textures/bbs_ui_rabbit/routu_logo.png") != null;
    private static final float HEADER_ROW_H = 18;
    private static final float CARD_MIN_W = 128;
    private static final float GAP = 8;

    private final UIFilmPanel panel;
    private final FilmLibraryStore store = FilmLibraryStore.INSTANCE;
    private final FilmThumbnails thumbs = FilmThumbnails.INSTANCE;
    /** The home on screen, for images dropped onto a film (see {@link #acceptDroppedFiles}). */
    private static WeakReference<UIFilmLibraryHome> active = new WeakReference<>(null);
    private final UITextbox search;

    /* Data */
    private final List<String> films = new ArrayList<>();
    private final Set<String> filmSet = new HashSet<>();
    private final TreeSet<String> folders = new TreeSet<>();
    private final Map<String, Integer> folderCounts = new HashMap<>();
    private final Set<String> expanded = new HashSet<>();
    private List<String> visible = new ArrayList<>();
    private boolean namesKnown;
    private boolean dirty = true;
    private int seenVersion = -1;
    private String lastQuery = "";
    private boolean autoSelected;
    /** Ask for the names on the first frame shown, not while the panel is still being built. */
    private boolean needsNames;

    /* View state */
    private int scope = SCOPE_ALL;
    private String folder = "";
    /** Only films with this colour label (0 = any). */
    private int labelFilter;
    private int seenThumbVersion = -1;
    private String selected;

    /* Animation */
    private long lastFrame;
    private float dt;
    private float time;
    private long shownAt;
    private long listChangedAt;
    private long selectedAt;
    private final Map<String, Float> anims = new HashMap<>();
    private final Map<String, Long> pops = new HashMap<>();
    private float scroll;
    private float scrollTarget;
    private float sideScroll;
    private float sideScrollTarget;
    private float detailScroll;
    private float detailScrollTarget;
    private float selY = -1;
    private float sideSelY = -1;
    private float fade = 1F;
    private float lastScrollTime;
    private final FilmPresence presence = new FilmPresence();
    /* Hover frames: which film is being scrubbed, where the mouse last moved, which frame shows */
    private String scrubId;
    private float scrubMouse = -1;
    private long scrubMovedAt;
    private int scrubSlot = -1;
    private int scrubPrev = -1;
    private long scrubChangedAt;
    /* Ambience: the selected film's colour and picture, carried across the whole home */
    private int glowColor = -1;
    private FilmThumbnails.Thumb ambient;
    private FilmThumbnails.Thumb ambientPrev;
    private long ambientAt;
    private final Map<String, Integer> presenceColors = new HashMap<>();

    /* Interaction */
    private final List<Hit> hits = new ArrayList<>();
    private List<Hit> lastHits = new ArrayList<>();
    private String hot;
    private String hotSince;
    private long hotAt;
    private String lastClickKey;
    private long lastClickAt;
    private boolean scrollToSelected;

    /* Layout (resize) */
    private float sx, sy, sw, sh;
    private float mx, my, mw, mh;
    private float dx, dy, dw, dh;
    private boolean wide;
    private float listX, listY, listW, listH;
    private float searchX, searchY, searchW;
    private float toolbarY;
    private final Area laidOut = new Area();

    public UIFilmLibraryHome(UIFilmPanel panel)
    {
        this.panel = panel;

        this.search = new UITextbox(1000, (text) -> this.dirty = true);
        this.search.background(false);
        this.search.placeholder(L10n.lang(L + "search"));
        this.search.relative(this).set(0, 0, 10, 20);
        this.add(this.search);

        this.store.load();
    }

    public static boolean isHomeOn()
    {
        return RabbitSettings.isOn(RabbitSettings.filmLibraryHome);
    }

    /* Text */

    private static String tr(String key)
    {
        return L10n.lang(L + key).get();
    }

    private static String tr(String key, Object... args)
    {
        return L10n.lang(L + key).format(args).get();
    }

    private static IKey key(String key)
    {
        return L10n.lang(L + key);
    }

    /* Visibility and data */

    @Override
    public void setVisible(boolean visible)
    {
        boolean was = this.isVisible();

        super.setVisible(visible);

        if (visible && !was)
        {
            this.shownAt = System.currentTimeMillis();
            this.listChangedAt = this.shownAt;
            this.dirty = true;
            this.needsNames = true;
        }
    }

    /** The repository's answer: every film id, and empty folders as "path/". */
    public void fillNames(Collection<String> names)
    {
        this.films.clear();
        this.filmSet.clear();
        this.folders.clear();
        this.folderCounts.clear();

        for (String name : names)
        {
            if (name.endsWith("/"))
            {
                this.addFolderChain(name);

                continue;
            }

            this.films.add(name);
            this.filmSet.add(name);

            int slash = name.lastIndexOf('/');

            if (slash > 0)
            {
                this.addFolderChain(name.substring(0, slash + 1));
            }
        }

        for (String film : this.films)
        {
            for (int i = film.indexOf('/'); i >= 0; i = film.indexOf('/', i + 1))
            {
                this.folderCounts.merge(film.substring(0, i + 1), 1, Integer::sum);
            }
        }

        if (this.scope == SCOPE_FOLDER && !this.folders.contains(this.folder))
        {
            this.scope = SCOPE_ALL;
            this.folder = "";
        }

        this.namesKnown = true;
        this.dirty = true;
        this.store.refresh(this.repository(), this.films);

        if (!this.autoSelected && this.selected == null)
        {
            this.autoSelected = true;

            for (ValueRecentData.Entry entry : this.recentEntries())
            {
                if (this.filmSet.contains(entry.id))
                {
                    this.select(entry.id, true);

                    break;
                }
            }
        }

        if (this.selected != null && !this.filmSet.contains(this.selected))
        {
            this.selected = null;
        }
    }

    private void addFolderChain(String path)
    {
        for (int i = path.indexOf('/'); i >= 0; i = path.indexOf('/', i + 1))
        {
            this.folders.add(path.substring(0, i + 1));
        }
    }

    /** Ask for the names as usual, then read a local folder directly: an add-on caching the key list (bbs-lezy) answers requestNames with the list from before the change. */
    private void refreshNames()
    {
        this.panel.requestNames();

        if (this.repository() instanceof FolderManagerRepository<?> local)
        {
            this.panel.fillNames(((FolderManagerRepositoryAccessor) local).rabbit$getManager().getKeys());
        }
    }

    private IRepository<?> repository()
    {
        return this.panel.getType().getRepository();
    }

    private List<ValueRecentData.Entry> recentEntries()
    {
        return BBSSettings.recentData.get(this.panel.getRecentType());
    }

    private void rebuild()
    {
        String query = this.search.getText().trim().toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        Map<String, Long> recent = new HashMap<>();

        for (ValueRecentData.Entry entry : this.recentEntries())
        {
            recent.putIfAbsent(entry.id, entry.time);
        }

        for (String id : this.films)
        {
            if (this.scope == SCOPE_RECENT && !recent.containsKey(id))
            {
                continue;
            }

            if (this.scope == SCOPE_FAVORITES && !this.store.isFavorite(id))
            {
                continue;
            }

            if (this.scope == SCOPE_FOLDER && !id.startsWith(this.folder))
            {
                continue;
            }

            if (this.labelFilter != 0 && this.thumbs.label(id) != this.labelFilter)
            {
                continue;
            }

            if (!query.isEmpty())
            {
                FilmMeta meta = this.store.get(id);
                boolean match = id.toLowerCase(Locale.ROOT).contains(query)
                    || (meta != null && meta.description.toLowerCase(Locale.ROOT).contains(query));

                if (!match)
                {
                    continue;
                }
            }

            list.add(id);
        }

        int sort = this.scope == SCOPE_RECENT ? FilmLibraryStore.SORT_RECENT : this.store.sort;
        boolean descending = this.scope == SCOPE_RECENT || this.store.descending;
        Comparator<String> byName = Comparator.comparing((String id) -> baseName(id).toLowerCase(Locale.ROOT)).thenComparing((id) -> id);
        Comparator<String> comparator;

        if (sort == FilmLibraryStore.SORT_NAME)
        {
            comparator = byName;
        }
        else
        {
            /* Metadata is filled in by a background loader: read every key once up front, so a value
             * arriving mid-sort can't make the comparator inconsistent (TimSort throws on that). */
            int field = sort == FilmLibraryStore.SORT_CREATED ? 1 : (sort == FilmLibraryStore.SORT_DURATION ? 2 : 0);
            Map<String, Long> keys = new HashMap<>();

            for (String id : list)
            {
                keys.put(id, sort == FilmLibraryStore.SORT_RECENT ? recent.getOrDefault(id, 0L) : this.metaLong(id, field));
            }

            comparator = Comparator.comparingLong((String id) -> keys.get(id)).thenComparing(byName);
        }

        list.sort(descending ? comparator.reversed() : comparator);

        boolean changedSet = !new HashSet<>(list).equals(new HashSet<>(this.visible)) || !query.equals(this.lastQuery);

        this.visible = list;
        this.lastQuery = query;

        if (changedSet)
        {
            this.listChangedAt = System.currentTimeMillis();
            this.scrollTarget = 0;
            this.selY = -1;
        }
    }

    private long metaLong(String id, int field)
    {
        FilmMeta meta = this.store.get(id);

        if (meta == null)
        {
            return 0;
        }

        return field == 0 ? meta.modified : (field == 1 ? meta.created : meta.duration);
    }

    /* Actions */

    private void select(String id, boolean scrollTo)
    {
        if (id != null && id.equals(this.selected))
        {
            return;
        }

        this.selected = id;
        this.selectedAt = System.currentTimeMillis();
        this.detailScrollTarget = 0;
        this.scrollToSelected = scrollTo;
    }

    private void open(String id)
    {
        if (id == null)
        {
            return;
        }

        UIUtils.playClick();
        this.store.invalidate(id);
        this.panel.pickData(id);
    }

    private String currentFolderPrefix()
    {
        if (this.scope == SCOPE_FOLDER)
        {
            return this.folder;
        }

        return "";
    }

    private boolean taken(String id)
    {
        return this.filmSet.contains(id) || this.folders.contains(id + "/");
    }

    private void prompt(String title, String message, String initial, Consumer<String> callback)
    {
        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(key(title), key(message), (text) ->
        {
            String value = text.trim();

            if (value.isEmpty())
            {
                this.getContext().notifyError(key("error.empty"));

                return;
            }

            callback.accept(value);
        });

        prompt.text.filename();

        if (initial != null)
        {
            prompt.text.setText(initial);
        }

        UIOverlay.addOverlay(this.getContext(), prompt);
    }

    private void newFilm()
    {
        String prefix = this.currentFolderPrefix();

        this.prompt("new_film.title", "new_film.message", null, (name) ->
        {
            String id = prefix + name;

            if (this.taken(id))
            {
                this.getContext().notifyError(key("error.exists"));

                return;
            }

            Film film = (Film) this.repository().create(id);

            this.panel.fillDefaultData(film);
            this.panel.overlay.namesList.addFile(id);
            this.panel.fill(film);
            this.panel.forceSave();
            this.refreshNames();
        });
    }

    private void rename(String id)
    {
        String parent = parentOf(id);

        this.prompt("rename.title", "rename.message", baseName(id), (name) ->
        {
            String target = parent + name;

            if (target.equals(id))
            {
                return;
            }

            if (this.taken(target))
            {
                this.getContext().notifyError(key("error.exists"));

                return;
            }

            this.moveFilm(id, target);
        });
    }

    private void moveFilm(String id, String target)
    {
        this.repository().rename(id, target);
        this.panel.onDataRenamed(id, target);
        this.store.renamed(id, target);

        if (id.equals(this.selected))
        {
            this.selected = target;
        }

        this.refreshNames();
    }

    private void duplicate(String id)
    {
        String parent = parentOf(id);

        this.prompt("duplicate.title", "duplicate.message", baseName(id) + tr("copy_suffix"), (name) ->
        {
            String target = parent + name;

            if (this.taken(target))
            {
                this.getContext().notifyError(key("error.exists"));

                return;
            }

            IRepository<?> repository = this.repository();

            repository.load(id, (data) ->
            {
                if (!(data instanceof Film film))
                {
                    this.getContext().notifyError(key("error.failed"));

                    return;
                }

                film.stampCreationTimeNow();
                repository.save(target, film.toData().asMap());
                this.select(target, true);
                this.refreshNames();
            });
        });
    }

    private void delete(String id)
    {
        UIConfirmOverlayPanel confirm = new UIConfirmOverlayPanel(key("delete.title"), L10n.lang(L + "delete.message").format(baseName(id)), (yes) ->
        {
            if (!yes)
            {
                return;
            }

            int index = this.visible.indexOf(id);

            this.repository().delete(id);
            this.panel.onDataRemoved(id);
            this.store.removed(id);

            if (id.equals(this.selected))
            {
                List<String> rest = new ArrayList<>(this.visible);

                rest.remove(id);
                this.selected = rest.isEmpty() ? null : rest.get(Math.max(0, Math.min(index, rest.size() - 1)));
                this.selectedAt = System.currentTimeMillis();
            }

            this.refreshNames();
        });

        UIOverlay.addOverlay(this.getContext(), confirm);
    }

    private void toggleFavorite(String id)
    {
        this.store.toggleFavorite(id);
        this.pops.put(id, System.currentTimeMillis());
        this.dirty = true;
        UIUtils.playClick(this.store.isFavorite(id) ? 1.3F : 0.9F);
    }

    private void newFolder(String parent)
    {
        this.prompt("new_folder.title", "new_folder.message", null, (name) ->
        {
            String path = parent + name;

            if (this.folders.contains(path + "/") || this.filmSet.contains(path))
            {
                this.getContext().notifyError(key("error.exists"));

                return;
            }

            if (!parent.isEmpty())
            {
                this.expanded.add(parent);
            }

            this.repository().addFolder(path, (ok) ->
            {
                if (ok)
                {
                    this.refreshNames();
                }
                else
                {
                    this.getContext().notifyError(key("error.failed"));
                }
            });
        });
    }

    private void renameFolder(String path)
    {
        String trimmed = path.substring(0, path.length() - 1);
        String parent = parentOf(trimmed);

        this.prompt("rename_folder.title", "rename_folder.message", baseName(trimmed), (name) ->
        {
            String target = parent + name;

            if (this.folders.contains(target + "/") || this.filmSet.contains(target))
            {
                this.getContext().notifyError(key("error.exists"));

                return;
            }

            this.repository().renameFolder(path, target, (ok) ->
            {
                if (!ok)
                {
                    this.getContext().notifyError(key("error.failed"));

                    return;
                }

                this.panel.onDataFolderRenamed(path, target);
                this.store.folderRenamed(path, target);

                if (this.folder.startsWith(path))
                {
                    this.folder = target + "/" + this.folder.substring(path.length());
                }

                if (this.selected != null && this.selected.startsWith(path))
                {
                    this.selected = target + "/" + this.selected.substring(path.length());
                }

                this.refreshNames();
            });
        });
    }

    private void deleteFolder(String path)
    {
        UIConfirmOverlayPanel confirm = new UIConfirmOverlayPanel(key("delete_folder.title"), L10n.lang(L + "delete_folder.message").format(baseName(path.substring(0, path.length() - 1))), (yes) ->
        {
            if (!yes)
            {
                return;
            }

            this.repository().deleteFolder(path, (ok) ->
            {
                if (!ok)
                {
                    this.getContext().notifyError(key("error.folder_not_empty"));

                    return;
                }

                this.panel.onDataFolderRemoved(path);

                if (this.scope == SCOPE_FOLDER && this.folder.startsWith(path))
                {
                    this.setScope(SCOPE_ALL, "");
                }

                this.refreshNames();
            });
        });

        UIOverlay.addOverlay(this.getContext(), confirm);
    }

    private void openFolderOnDisk(String path)
    {
        File root = this.repository().getFolder();

        if (root != null)
        {
            UIUtils.openFolder(path.isEmpty() ? root : new File(root, path));
        }
    }

    private void setScope(int scope, String folder)
    {
        if (this.scope == scope && this.folder.equals(folder))
        {
            return;
        }

        this.scope = scope;
        this.folder = folder;
        this.dirty = true;
        UIUtils.playClick();
    }

    private void setSort(int sort)
    {
        if (this.store.sort == sort)
        {
            this.store.descending = !this.store.descending;
        }
        else
        {
            this.store.sort = sort;
            this.store.descending = sort != FilmLibraryStore.SORT_NAME;
        }

        this.store.save();
        this.dirty = true;
    }

    private void filmMenu(String id)
    {
        this.select(id, false);

        UIContext context = this.getContext();
        boolean favorite = this.store.isFavorite(id);

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.PLAY, key("open"), () -> this.open(id));
            menu.action(Icons.EDIT, key("rename"), () -> this.rename(id));
            menu.action(Icons.DUPE, key("duplicate"), () -> this.duplicate(id));
            menu.action(Icons.FOLDER, key("move"), () -> this.moveMenu(id));
            menu.action(favorite ? Icons.FAVORITE : Icons.FAVORITE, key(favorite ? "unfavorite" : "favorite"), () -> this.toggleFavorite(id));

            if (FilmThumbnails.enabled())
            {
                menu.action(Icons.IMAGE, key("cover"), () -> this.coverMenu(id));
                menu.action(Icons.COLOR, key("label"), () -> this.labelMenu(id));
            }

            menu.action(Icons.MORE, key("show_in_manager"), () -> this.panel.showInList(id));

            if (this.repository().getFolder() != null)
            {
                menu.action(Icons.FOLDER, key("open_folder"), () -> this.openFolderOnDisk(parentOf(id)));
            }

            menu.action(Icons.REMOVE, key("delete"), 0xff4059, () -> this.delete(id));
        });
    }

    /* Covers and colour labels */

    private void coverMenu(String id)
    {
        LibraryEntry entry = this.thumbs.entry(id);
        boolean chosen = entry != null && (entry.coverKind == LibraryEntry.COVER_MANUAL || entry.coverKind == LibraryEntry.COVER_CUSTOM);

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.FOLDER, key("cover.pick"), () -> this.pickCover(id));

            if (chosen)
            {
                menu.action(Icons.REFRESH, key("cover.reset"), () ->
                {
                    this.thumbs.resetCover(id);
                    UIUtils.playClick();
                });
            }
        });
    }

    private void pickCover(String id)
    {
        if (!this.thumbs.canWrite())
        {
            this.getContext().notifyError(L10n.lang("rabbit.film.set_cover.unavailable"));

            return;
        }

        UIFileDialogs.pickFile(key("cover.pick_title"), null, new String[] {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"}, key("cover.images"), (file) -> this.setCustomCover(id, file));
    }

    private void setCustomCover(String id, File file)
    {
        UIContext context = this.getContext();

        this.thumbs.putCustomCover(id, file, () ->
        {
            this.pops.put("cover:" + id, System.currentTimeMillis());

            if (context != null)
            {
                context.notifySuccess(key("cover.set"));
            }
        }, () ->
        {
            if (context != null)
            {
                context.notifyError(key("cover.failed"));
            }
        });
    }

    private void labelMenu(String id)
    {
        int current = this.thumbs.label(id);

        this.getContext().replaceContextMenu((menu) ->
        {
            for (int label = 1; label <= LibraryEntry.LABELS; label++)
            {
                final int value = label;

                menu.action(current == label ? Icons.CHECKMARK : Icons.CIRCLE, key("label." + label), FilmThumbnails.labelColor(label), () -> this.setLabel(id, value));
            }

            if (current != 0)
            {
                menu.action(Icons.CLOSE, key("label.0"), () -> this.setLabel(id, 0));
            }
        });
    }

    private void setLabel(String id, int label)
    {
        if (!this.thumbs.canWrite())
        {
            this.getContext().notifyError(L10n.lang("rabbit.film.set_cover.unavailable"));

            return;
        }

        if (this.thumbs.label(id) == label)
        {
            label = 0;
        }

        this.thumbs.setLabel(id, label);
        this.pops.put("label:" + id, System.currentTimeMillis());
        this.dirty = true;
        UIUtils.playClick(label == 0 ? 0.9F : 1.2F + label * 0.04F);
    }

    private void setLabelFilter(int label)
    {
        this.labelFilter = this.labelFilter == label ? 0 : label;
        this.dirty = true;
        UIUtils.playClick();
    }

    /** Images dropped on the window: onto a film in the library, they become its cover. */
    public static boolean acceptDroppedFiles(String[] paths)
    {
        UIFilmLibraryHome home = active.get();

        if (home == null || !home.isVisible() || !FilmThumbnails.enabled() || home.getContext() == null || paths == null)
        {
            return false;
        }

        File image = null;

        for (String path : paths)
        {
            String lower = path.toLowerCase(Locale.ROOT);

            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp") || lower.endsWith(".gif"))
            {
                image = new File(path);

                break;
            }
        }

        if (image == null)
        {
            return false;
        }

        UIContext context = home.getContext();
        Hit hit = home.hitAt(context.mouseX, context.mouseY);
        String id = hit == null ? null : home.filmOf(hit.key);

        if (id == null)
        {
            return false;
        }

        if (!home.thumbs.canWrite())
        {
            context.notifyError(L10n.lang("rabbit.film.set_cover.unavailable"));

            return true;
        }

        home.select(id, false);
        home.setCustomCover(id, image);

        return true;
    }

    /** The film a hit region belongs to, if any. */
    private String filmOf(String hitKey)
    {
        for (String prefix : new String[] {"card:", "row:", "presence:", "star:", "covercta:"})
        {
            if (hitKey.startsWith(prefix))
            {
                return hitKey.substring(prefix.length());
            }
        }

        return hitKey.equals("poster") ? this.selected : null;
    }

    private void moveMenu(String id)
    {
        String current = parentOf(id);

        this.getContext().replaceContextMenu((menu) ->
        {
            if (!current.isEmpty())
            {
                menu.action(Icons.FOLDER, key("root"), () -> this.moveTo(id, ""));
            }

            for (String path : this.folders)
            {
                if (!path.equals(current))
                {
                    menu.action(Icons.FOLDER, IKey.constant(path.substring(0, path.length() - 1)), () -> this.moveTo(id, path));
                }
            }
        });
    }

    private void moveTo(String id, String folder)
    {
        String target = folder + baseName(id);

        if (this.taken(target))
        {
            this.getContext().notifyError(key("error.exists"));

            return;
        }

        this.moveFilm(id, target);
    }

    private void folderMenu(String path)
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.ADD, key("new_film"), () ->
            {
                this.setScope(SCOPE_FOLDER, path);
                this.newFilm();
            });
            menu.action(Icons.FOLDER, key("new_subfolder"), () -> this.newFolder(path));
            menu.action(Icons.EDIT, key("rename"), () -> this.renameFolder(path));

            if (this.repository().getFolder() != null)
            {
                menu.action(Icons.FOLDER, key("open_folder"), () -> this.openFolderOnDisk(path));
            }

            menu.action(Icons.REMOVE, key("delete"), 0xff4059, () -> this.deleteFolder(path));
        });
    }

    private void sortMenu()
    {
        int[] modes = {FilmLibraryStore.SORT_MODIFIED, FilmLibraryStore.SORT_NAME, FilmLibraryStore.SORT_CREATED, FilmLibraryStore.SORT_DURATION, FilmLibraryStore.SORT_RECENT};

        this.getContext().replaceContextMenu((menu) ->
        {
            for (int mode : modes)
            {
                boolean active = this.store.sort == mode;

                menu.action(active ? Icons.CHECKMARK : Icons.NONE, key("sort." + mode), active, () ->
                {
                    this.store.sort = mode;
                    this.store.descending = mode != FilmLibraryStore.SORT_NAME;
                    this.store.save();
                    this.dirty = true;
                });
            }

            menu.action(this.store.descending ? Icons.ARROW_UP : Icons.ARROW_DOWN, key(this.store.descending ? "ascending" : "descending"), () ->
            {
                this.store.descending = !this.store.descending;
                this.store.save();
                this.dirty = true;
            });
        });
    }

    /* Input */

    private static final class Hit
    {
        final String key;
        final float x, y, w, h;
        Runnable click;
        Runnable right;
        Runnable dbl;
        String tooltip;

        Hit(String key, float x, float y, float w, float h)
        {
            this.key = key;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(float px, float py)
        {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
        }
    }

    private Hit hit(String key, float x, float y, float w, float h)
    {
        Hit hit = new Hit(key, x, y, w, h);

        this.hits.add(hit);

        return hit;
    }

    private Hit hitAt(float px, float py)
    {
        for (int i = this.lastHits.size() - 1; i >= 0; i--)
        {
            Hit hit = this.lastHits.get(i);

            if (hit.contains(px, py))
            {
                return hit;
            }
        }

        return null;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        Hit hit = this.hitAt(context.mouseX, context.mouseY);

        if (hit == null)
        {
            return true;
        }

        if (context.mouseButton == 1)
        {
            if (hit.right != null)
            {
                hit.right.run();
            }

            return true;
        }

        if (context.mouseButton != 0)
        {
            return true;
        }

        long now = System.currentTimeMillis();
        boolean isDouble = hit.key.equals(this.lastClickKey) && now - this.lastClickAt < 320;

        this.lastClickKey = hit.key;
        this.lastClickAt = isDouble ? 0 : now;

        if (isDouble && hit.dbl != null)
        {
            hit.dbl.run();
        }
        else if (hit.click != null)
        {
            hit.click.run();
        }

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        float amount = (float) context.mouseWheel * 34F;

        if (context.mouseX < this.sx + this.sw)
        {
            this.sideScrollTarget -= amount;
        }
        else if (context.mouseX >= this.dx && context.mouseY >= this.dy && context.mouseX < this.dx + this.dw && context.mouseY < this.dy + this.dh)
        {
            this.detailScrollTarget -= amount;
        }
        else
        {
            this.scrollTarget -= amount;
            this.lastScrollTime = this.time;
        }

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (!this.isVisible() || UIOverlay.has(context))
        {
            return false;
        }

        if (context.isPressed(GLFW.GLFW_KEY_F) && Window.isCtrlPressed())
        {
            context.focus(this.search);
            this.search.selectAll(context);

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_N) && Window.isCtrlPressed())
        {
            this.newFilm();

            return true;
        }

        if (this.search.isFocused())
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.search.setText("");
                this.search.unfocus(context);
                this.dirty = true;

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_DOWN) || context.isPressed(GLFW.GLFW_KEY_ENTER))
            {
                this.search.unfocus(context);

                if (context.isPressed(GLFW.GLFW_KEY_ENTER) && this.visible.size() == 1)
                {
                    this.open(this.visible.get(0));
                }
                else if (!this.visible.isEmpty())
                {
                    this.select(this.visible.get(0), true);
                }

                return true;
            }

            return false;
        }

        int index = this.selected == null ? -1 : this.visible.indexOf(this.selected);
        int step = this.store.grid ? Math.max(1, this.gridColumns()) : 1;

        if (context.isPressed(GLFW.GLFW_KEY_DOWN) || (this.store.grid && context.isPressed(GLFW.GLFW_KEY_RIGHT)))
        {
            int s = context.isPressed(GLFW.GLFW_KEY_DOWN) ? step : 1;

            this.moveSelection(index < 0 ? 0 : Math.min(this.visible.size() - 1, index + s));

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_UP) || (this.store.grid && context.isPressed(GLFW.GLFW_KEY_LEFT)))
        {
            int s = context.isPressed(GLFW.GLFW_KEY_UP) ? step : 1;

            this.moveSelection(index < 0 ? 0 : Math.max(0, index - s));

            return true;
        }

        if (this.selected != null)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
            {
                this.open(this.selected);

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_F2))
            {
                this.rename(this.selected);

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_DELETE))
            {
                this.delete(this.selected);

                return true;
            }
        }

        return false;
    }

    private void moveSelection(int index)
    {
        if (index >= 0 && index < this.visible.size())
        {
            this.select(this.visible.get(index), true);
            UIUtils.playClick(1.6F);
        }
    }

    /* Layout */

    /** Runs once this element's area is known and before the children (the search box) are laid out. */
    @Override
    protected void afterResizeApplied()
    {
        this.layout();
    }

    private void layout()
    {
        float x = this.area.x, y = this.area.y, w = this.area.w, h = this.area.h;

        this.laidOut.copy(this.area);
        float pad = 10;

        this.sw = Math.max(126, Math.min(168, w * 0.17F));
        this.sx = x + pad;
        this.sy = y + pad;
        this.sh = h - pad * 2;

        float mainX = this.sx + this.sw + pad;
        float mainR = x + w - pad;

        this.wide = w >= 720 && w > h * 1.2F;

        if (this.wide)
        {
            this.dw = Math.max(214, Math.min(290, w * 0.26F));
            this.dx = mainR - this.dw;
            this.dy = y + pad;
            this.dh = h - pad * 2;
            this.mx = mainX;
            this.my = y + pad;
            this.mw = this.dx - pad - mainX;
            this.mh = h - pad * 2;
        }
        else
        {
            this.dh = Math.max(150, Math.min(214, h * 0.38F));
            this.dx = mainX;
            this.dw = mainR - mainX;
            this.dy = y + h - pad - this.dh;
            this.mx = mainX;
            this.my = y + pad;
            this.mw = mainR - mainX;
            this.mh = this.dy - pad - this.my;
        }

        this.toolbarY = this.my + 44;
        this.searchX = this.mx;
        this.searchY = this.toolbarY;
        this.searchW = Math.max(60, this.mw - 96 - 50 - 12 - 28);

        this.listX = this.mx;
        this.listY = this.toolbarY + 22 + 18;
        this.listW = this.mw;
        this.listH = Math.max(40, this.my + this.mh - this.listY);

        this.search.set((int) (this.searchX + 20 - x), (int) (this.searchY + 1 - y), (int) (this.searchW - 40), 20);
    }

    private int gridColumns()
    {
        float inner = this.listW - 16;

        return Math.max(1, (int) ((inner + GAP) / (CARD_MIN_W + GAP)));
    }

    /* Animation helpers */

    private float anim(String key, float target, float speed)
    {
        float value = this.anims.getOrDefault(key, target == 0 ? 0F : 0F);

        value = Gfx.approach(value, target, speed, this.dt);
        this.anims.put(key, value);

        return value;
    }

    private float hover(String key)
    {
        return this.anim("h:" + key, key.equals(this.hot) ? 1F : 0F, 16F);
    }

    private float intro(float delay)
    {
        float t = (System.currentTimeMillis() - this.shownAt) / 1000F - delay;

        return Gfx.easeOut(t / 0.5F);
    }

    private int c(int argb)
    {
        return Gfx.mulA(argb, this.fade);
    }

    private void section(UIContext context, float delay)
    {
        float p = this.intro(delay);

        this.fade = p;
        context.batcher.getContext().method_51448().method_22903();
        context.batcher.getContext().method_51448().method_46416(0F, (1F - p) * 12F, 0F);
    }

    private void endSection(UIContext context)
    {
        context.batcher.getContext().method_51448().method_22909();
        this.fade = 1F;
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        long now = System.nanoTime();

        this.dt = this.lastFrame == 0 ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1_000_000_000F);
        this.lastFrame = now;
        this.time += this.dt;

        if ((int) (this.time * 2) != (int) ((this.time - this.dt) * 2))
        {
            this.store.tick();
        }

        if (this.needsNames)
        {
            this.needsNames = false;
            this.refreshNames();
        }

        if (this.store.getVersion() != this.seenVersion)
        {
            this.seenVersion = this.store.getVersion();
            this.dirty = true;
        }

        if (active.get() != this)
        {
            active = new WeakReference<>(this);
        }

        this.thumbs.tick();

        if (this.thumbs.getVersion() != this.seenThumbVersion)
        {
            this.seenThumbVersion = this.thumbs.getVersion();

            if (this.labelFilter != 0)
            {
                this.dirty = true;
            }
        }

        if (this.dirty)
        {
            this.dirty = false;
            this.rebuild();
        }

        /* Shown before the next layout pass: lay out now rather than draw one frame from stale numbers */
        if (!this.laidOut.equals(this.area))
        {
            this.layout();
            this.search.resize();
        }

        /* What is under the mouse, from the regions drawn last frame */
        boolean blocked = UIOverlay.has(context) || context.hasContextMenu();
        Hit under = blocked || !this.area.isInside(context) ? null : this.hitAt(context.mouseX, context.mouseY);
        String hotNow = under == null ? null : under.key;

        if (hotNow == null ? this.hotSince != null : !hotNow.equals(this.hotSince))
        {
            this.hotSince = hotNow;
            this.hotAt = System.currentTimeMillis();
        }

        this.hot = hotNow;
        this.hits.clear();

        Batcher2D b = context.batcher;

        this.renderBackdrop(b);

        this.section(context, 0F);
        this.renderSidebar(context, b);
        this.endSection(context);

        this.section(context, 0.06F);
        this.renderHeader(context, b);
        this.endSection(context);

        this.section(context, 0.12F);
        this.renderToolbar(context, b);
        this.endSection(context);

        this.section(context, 0.18F);
        this.renderList(context, b);
        this.endSection(context);

        this.section(context, this.wide ? 0.1F : 0.24F);
        this.renderDetail(context, b);
        this.endSection(context);

        /* The search box is a real text field; it sits on the pill drawn by the toolbar */
        super.render(context);

        this.renderTooltip(context, b, under);

        List<Hit> swap = this.lastHits;

        this.lastHits = new ArrayList<>(this.hits);
        swap.clear();
    }

    private void renderBackdrop(Batcher2D b)
    {
        float x = this.area.x, y = this.area.y, w = this.area.w, h = this.area.h;

        b.box(x, y, x + w, y + h, Gfx.deep(0.9F));
        b.gradientVBox(x, y, x + w, y + h, Gfx.base(0.55F), Gfx.sunken(0.75F));

        b.clip(this.area, this.getContext());

        float t = this.time;

        /* The selected film's picture fills the room, far out of focus, and dissolves to the next */
        FilmThumbnails.Thumb picture = this.selected != null && FilmThumbnails.enabled() ? this.thumbs.cover(this.selected) : null;
        long now = System.currentTimeMillis();

        if (picture != this.ambient)
        {
            this.ambientPrev = this.ambient;
            this.ambient = picture;
            this.ambientAt = now;
        }

        float mix = Gfx.easeInOut((now - this.ambientAt) / 1100F);
        float strength = Gfx.light() ? 0.2F : 0.3F;
        float[] uv = window(1.3F, 0.1F * (float) Math.sin(t * 0.045F), 0.07F * (float) Math.cos(t * 0.06F));

        if (this.ambientPrev != null && mix < 1F)
        {
            Gfx.imageRound(b, this.ambientPrev.texture, x, y, w, h, 0, uv[0], uv[1], uv[2], uv[3], Gfx.alpha(0xffffff, strength * (1F - mix)), Gfx.alpha(0xffffff, strength * 0.5F * (1F - mix)), 5.5F);
        }

        if (this.ambient != null)
        {
            Gfx.imageRound(b, this.ambient.texture, x, y, w, h, 0, uv[0], uv[1], uv[2], uv[3], Gfx.alpha(0xffffff, strength * mix), Gfx.alpha(0xffffff, strength * 0.5F * mix), 5.5F);
        }

        /* The glows lean toward the film's colour */
        int accent = Gfx.lerp(0xff000000 | BBSSettings.primaryColor.get(), 0xff000000 | this.selectedGlow(), this.selected != null ? 0.65F : 0F) & 0xffffff;
        int second = Gfx.hsv(hueOf(accent) + 0.12F, 0.55F, 0.9F) & 0xffffff;
        float glow = Gfx.light() ? 0.10F : 0.13F;

        Gfx.radial(b, x + w * (0.22F + 0.06F * (float) Math.sin(t * 0.21F)), y + h * (0.18F + 0.05F * (float) Math.cos(t * 0.17F)), Math.max(w, h) * 0.55F, Gfx.alpha(accent, glow), Gfx.alpha(accent, 0F));
        Gfx.radial(b, x + w * (0.82F + 0.05F * (float) Math.cos(t * 0.13F)), y + h * (0.9F + 0.04F * (float) Math.sin(t * 0.19F)), Math.max(w, h) * 0.5F, Gfx.alpha(second, glow * 0.8F), Gfx.alpha(second, 0F));
        Gfx.radial(b, x + w * (0.55F + 0.2F * (float) Math.sin(t * 0.07F)), y + h * 0.5F, Math.max(w, h) * 0.35F, Gfx.alpha(accent, glow * 0.45F), Gfx.alpha(accent, 0F));

        b.unclip(this.getContext());
    }

    private static float hueOf(int rgb)
    {
        float r = (rgb >> 16 & 0xff) / 255F, g = (rgb >> 8 & 0xff) / 255F, bl = (rgb & 0xff) / 255F;
        float max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
        float d = max - min;

        if (d == 0)
        {
            return 0;
        }

        float h;

        if (max == r) h = ((g - bl) / d) % 6;
        else if (max == g) h = (bl - r) / d + 2;
        else h = (r - g) / d + 4;

        return h / 6F;
    }

    private void card(Batcher2D b, float x, float y, float w, float h, float r, float alpha)
    {
        Gfx.shadow(b, x, y + 2, w, h, r, 10, c(Gfx.alpha(0x000000, Gfx.light() ? 0.10F : 0.28F)));
        Gfx.roundRectV(b, x, y, w, h, r, c(Gfx.raised(alpha)), c(Gfx.base(alpha)));
        Gfx.roundOutline(b, x, y, w, h, r, 1F, c(Gfx.wash(Gfx.light() ? 0.10F : 0.07F)));
    }

    /* Sidebar */

    private void renderSidebar(UIContext context, Batcher2D b)
    {
        float x = this.sx, y = this.sy, w = this.sw, h = this.sh;

        this.card(b, x, y, w, h, 10, 0.82F);

        float listTop = STUDIO_BRAND ? this.renderStudioBrand(context, b, x, y, w, h) : this.renderClassicBrand(b, x, y);
        float footerH = 46;
        float listBottom = y + h - footerH;
        float contentH = this.sidebarContentHeight();
        float maxScroll = Math.max(0, contentH - (listBottom - listTop));

        this.sideScrollTarget = Math.max(0, Math.min(maxScroll, this.sideScrollTarget));
        this.sideScroll = Gfx.approach(this.sideScroll, this.sideScrollTarget, 18F, this.dt);

        b.clip((int) x, (int) listTop, (int) w, (int) (listBottom - listTop), context);

        float cy = listTop - this.sideScroll;

        Gfx.text(b, tr("browse"), x + 12, cy + 4, c(Gfx.inkMuted(1F)));
        cy += 16;

        int recentCount = 0;

        for (ValueRecentData.Entry entry : this.recentEntries())
        {
            if (this.filmSet.contains(entry.id)) recentCount++;
        }

        int favoriteCount = 0;

        for (String id : this.films)
        {
            if (this.store.isFavorite(id)) favoriteCount++;
        }

        float[] rowYs = new float[3];
        Icon[] icons = {Icons.LIST, Icons.TIME, Icons.FAVORITE};
        String[] labels = {tr("all"), tr("recent"), tr("favorites")};
        int[] counts = {this.films.size(), recentCount, favoriteCount};
        float selectedRowY = -1;

        for (int i = 0; i < 3; i++)
        {
            rowYs[i] = cy;

            if (this.scope == i)
            {
                selectedRowY = cy;
            }

            cy += 22;
        }

        float labelRowY = -1;

        if (FilmThumbnails.enabled())
        {
            labelRowY = cy + 3;
            cy += 20;
        }

        cy += 8;

        float folderHeaderY = cy;

        cy += 18;

        List<String> treeRows = this.folderRows();
        float[] folderYs = new float[treeRows.size()];

        for (int i = 0; i < treeRows.size(); i++)
        {
            folderYs[i] = cy;

            if (this.scope == SCOPE_FOLDER && treeRows.get(i).equals(this.folder))
            {
                selectedRowY = cy;
            }

            cy += 20;
        }

        /* The selection pill glides between rows */
        if (selectedRowY >= 0)
        {
            float target = selectedRowY + this.sideScroll;

            this.sideSelY = this.sideSelY < 0 ? target : Gfx.approach(this.sideSelY, target, 20F, this.dt);

            float py = this.sideSelY - this.sideScroll;
            float ph = this.scope == SCOPE_FOLDER ? 20 : 22;

            Gfx.shadow(b, x + 6, py + 1, w - 12, ph - 2, 7, 6, c(Gfx.accent(0.35F)));
            Gfx.roundRectH(b, x + 6, py + 1, w - 12, ph - 2, 7, c(Gfx.accent(0.95F)), c(Gfx.lerp(Gfx.accent(), 0xffffffff, 0.12F)));
        }
        else
        {
            this.sideSelY = -1;
        }

        for (int i = 0; i < 3; i++)
        {
            final int scope = i;
            float ry = rowYs[i];
            boolean active = this.scope == i;
            Hit hit = this.hitClipped("nav:" + i, x + 6, ry + 1, w - 12, 20, listTop, listBottom);
            float hv = this.hover("nav:" + i);

            if (hit != null)
            {
                hit.click = () -> this.setScope(scope, "");
            }

            if (!active && hv > 0.01F)
            {
                Gfx.roundRect(b, x + 6, ry + 1, w - 12, 20, 7, c(Gfx.wash(0.07F * hv)));
            }

            int ink = active ? Gfx.onAccent() : Gfx.lerp(Gfx.inkSoft(1F), Gfx.ink(1F), hv);

            Gfx.icon(b, icons[i], x + 12 + hv * 1.5F, ry + 3, c(ink));
            Gfx.text(b, Gfx.fit(b, labels[i], (int) (w - 64)), x + 31 + hv * 1.5F, ry + 7, c(ink));
            this.countPill(b, String.valueOf(counts[i]), x + w - 12, ry + 6, active);
        }

        if (labelRowY >= 0)
        {
            this.labelFilterRow(b, x, labelRowY, w, listTop, listBottom);
        }

        /* Folders header with the "new folder" button */
        Gfx.text(b, tr("folders"), x + 12, folderHeaderY + 4, c(Gfx.inkMuted(1F)));

        Hit add = this.hitClipped("newfolder", x + w - 26, folderHeaderY - 1, 16, 16, listTop, listBottom);
        float addHover = this.hover("newfolder");

        if (add != null)
        {
            add.click = () -> this.newFolder(this.scope == SCOPE_FOLDER ? this.folder : "");
            add.tooltip = tr("new_folder");
        }

        Gfx.roundRect(b, x + w - 26, folderHeaderY - 1, 16, 16, 5, c(Gfx.wash(0.04F + 0.1F * addHover)));
        Gfx.icon(b, Icons.ADD, x + w - 18, folderHeaderY + 7, 1F, c(Gfx.lerp(Gfx.inkSoft(1F), Gfx.accent(), addHover)));

        if (treeRows.isEmpty())
        {
            Gfx.text(b, tr("no_folders"), x + 14, folderHeaderY + 22, c(Gfx.inkMuted(0.8F)));
        }

        for (int i = 0; i < treeRows.size(); i++)
        {
            String path = treeRows.get(i);
            float ry = folderYs[i];
            int depth = depth(path);
            boolean active = this.scope == SCOPE_FOLDER && path.equals(this.folder);
            boolean hasChildren = this.hasSubfolders(path);
            boolean open = this.expanded.contains(path);
            float hv = this.hover("folder:" + path);
            Hit hit = this.hitClipped("folder:" + path, x + 6, ry, w - 12, 20, listTop, listBottom);

            if (hit != null)
            {
                hit.click = () -> this.setScope(SCOPE_FOLDER, path);
                hit.dbl = () -> this.toggleExpanded(path);
                hit.right = () -> this.folderMenu(path);
            }

            if (!active && hv > 0.01F)
            {
                Gfx.roundRect(b, x + 6, ry, w - 12, 20, 7, c(Gfx.wash(0.07F * hv)));
            }

            float ix = x + 10 + depth * 10;
            int ink = active ? Gfx.onAccent() : Gfx.lerp(Gfx.inkSoft(1F), Gfx.ink(1F), hv);

            if (hasChildren)
            {
                Hit chevron = this.hitClipped("chev:" + path, ix - 2, ry + 2, 12, 16, listTop, listBottom);
                float turn = this.anim("chev:" + path, open ? 1F : 0F, 14F);

                if (chevron != null)
                {
                    chevron.click = () -> this.toggleExpanded(path);
                }

                this.chevron(b, ix + 3, ry + 10, turn, c(Gfx.mulA(ink, 0.8F)));
            }

            Gfx.icon(b, Icons.FOLDER, ix + 9, ry + 2, c(active ? ink : Gfx.lerp(Gfx.accent(0.75F), Gfx.accent(), hv)));

            String name = baseName(path.substring(0, path.length() - 1));
            Integer count = this.folderCounts.get(path);
            String countText = count == null ? "0" : String.valueOf(count);
            int nameW = (int) (x + w - 14 - Gfx.width(b, countText) - 8 - (ix + 27));

            Gfx.text(b, Gfx.fit(b, name, Math.max(10, nameW)), ix + 27, ry + 6, c(ink));
            Gfx.text(b, countText, x + w - 14 - Gfx.width(b, countText), ry + 6, c(active ? Gfx.mulA(ink, 0.75F) : Gfx.inkMuted(0.9F)));
        }

        b.unclip(context);

        /* Scroll fade at the edges of the list */
        if (this.sideScroll > 1)
        {
            b.gradientVBox(x + 1, listTop, x + w - 1, listTop + 10, c(Gfx.raised(0.9F)), c(Gfx.raised(0F)));
        }

        if (this.sideScroll < maxScroll - 1)
        {
            b.gradientVBox(x + 1, listBottom - 10, x + w - 1, listBottom, c(Gfx.base(0F)), c(Gfx.base(0.9F)));
        }

        /* Footer: the data manager and the folder on disk */
        float fy = y + h - footerH + 4;

        b.box(x + 10, fy - 4, x + w - 10, fy - 3, c(Gfx.wash(0.07F)));
        this.footerRow(b, "manager", Icons.MORE, tr("data_manager"), x, fy, w, () -> this.panel.openDataManager());

        if (this.repository().getFolder() != null)
        {
            this.footerRow(b, "disk", Icons.FOLDER, tr("open_films_folder"), x, fy + 20, w, () -> this.openFolderOnDisk(this.scope == SCOPE_FOLDER ? this.folder : ""));
        }
    }

    /** "Film library / BBS++" beside a small accent badge: the brand of builds without the studio logo. */
    private float renderClassicBrand(Batcher2D b, float x, float y)
    {
        float bx = x + 10, by = y + 10;

        Gfx.shadow(b, bx, by + 1, 20, 20, 6, 6, c(Gfx.accent(0.45F)));
        Gfx.roundRect(b, bx, by, 20, 20, 6, c(Gfx.lerp(Gfx.accent(), 0xffffffff, 0.25F)), c(Gfx.accent()), c(Gfx.accent()), c(Gfx.lerp(Gfx.accent(), 0xff000000, 0.25F)));
        Gfx.icon(b, Icons.FILM, bx + 10, by + 10, 1F, c(Gfx.onAccent()));
        Gfx.text(b, tr("library"), bx + 27, by + 2, c(Gfx.ink(1F)));
        Gfx.text(b, "BBS-Rabbit", bx + 27, by + 12, c(Gfx.inkMuted(0.9F)));

        return y + 40;
    }

    /** The studio badge; returns where the sidebar list starts. */
    private float renderStudioBrand(UIContext context, Batcher2D b, float x, float y, float w, float h)
    {
        /* Brand: the studio badge, the full logo centred on a dark plate (its cream strokes need a
         * dark ground in either theme), with the name under it. It is alive: the logo floats over a
         * breathing glow, pixel motes drift up through the plate and now and then the logo glitches into its two colours. */
        float bpx = x + 8, bpy = y + 8, bpw = w - 16;
        float lh = Math.max(44, Math.min(76, h * 0.17F));
        float lw = lh * 168F / 236F;
        float bph = lh + 55;
        float bh = this.hover("brand");
        float t = this.time;
        float breathe = 0.5F + 0.5F * (float) Math.sin(t * 1.3F);
        float lift = bh * 2F;
        Hit brand = this.hit("brand", bpx, bpy, bpw, bph);

        brand.click = () -> this.setScope(SCOPE_ALL, "");
        brand.tooltip = tr("studio");

        Gfx.shadow(b, bpx, bpy + 2, bpw, bph, 10, 7 + 3 * breathe + 6 * bh, c(Gfx.alpha(STUDIO_PINK, 0.16F + 0.08F * breathe + 0.22F * bh)));
        Gfx.roundRectV(b, bpx, bpy, bpw, bph, 10, c(0xff272b35), c(0xff121418));

        b.clip((int) bpx, (int) bpy, (int) bpw, (int) bph, context);

        float cx = bpx + bpw / 2F;
        float ly = bpy + 12 + (float) Math.sin(t * 1.6F) * 1.3F - lift;

        /* Glow behind the logo, and a cooler light slowly circling it */
        Gfx.radial(b, cx, ly + lh * 0.55F, lh * (0.85F + 0.08F * breathe), c(Gfx.alpha(STUDIO_PINK, 0.2F + 0.1F * breathe + 0.12F * bh)), c(Gfx.alpha(STUDIO_PINK, 0F)));
        Gfx.radial(b, cx + (float) Math.cos(t * 0.7F) * lw * 0.55F, ly + lh * 0.45F + (float) Math.sin(t * 0.7F) * lh * 0.25F, lh * 0.5F, c(Gfx.alpha(0xf2ead8, 0.07F + 0.04F * bh)), c(Gfx.alpha(0xf2ead8, 0F)));

        /* Pixel motes rising through the plate, faster while hovered */
        float rate = 1F + 0.8F * bh;

        for (int i = 0; i < 16; i++)
        {
            float seed = i * 0.618034F;
            float speed = (7F + 7F * frac(seed * 7.13F)) * rate;
            float cycle = frac(t * speed / bph + frac(seed * 3.17F));
            float size = 1.2F + 1.4F * frac(seed * 5.71F);
            float mx = bpx + 6 + frac(seed * 13.37F) * (bpw - 12) + (float) Math.sin(t * 0.9F + i * 1.7F) * 3F;
            float my = bpy + bph - cycle * (bph + 6);
            float alpha = (float) Math.sin(cycle * Math.PI) * (0.45F + 0.25F * bh);
            int color = i % 3 == 0 ? 0xf2ead8 : STUDIO_PINK & 0xffffff;

            Gfx.roundRect(b, mx, my, size, size, 0.4F, c(Gfx.alpha(color, alpha)));
        }

        /* A light sweep crosses the plate every few seconds */
        float period = 7F;
        float phase = (t % period) / period;
        float sweepW = bpw * 0.45F;

        Gfx.sweep(b, bpx - sweepW + phase * (bpw + sweepW * 2) * 2.2F, bpy, bpy + bph, sweepW, bph * 0.45F, c(Gfx.alpha(0xffffff, 0F)), c(Gfx.alpha(0xffffff, 0.07F)));
        b.unclip(context);

        Gfx.roundRectV(b, bpx + 1, bpy + 1, bpw - 2, bph * 0.4F, 9, c(Gfx.alpha(0xffffff, 0.05F)), c(Gfx.alpha(0xffffff, 0F)));
        Gfx.roundOutline(b, bpx, bpy, bpw, bph, 10, 1F, c(Gfx.alpha(STUDIO_PINK, 0.3F + 0.12F * breathe + 0.35F * bh)));

        /* Now and then the logo glitches: it splits into pink and cyan and jitters, for a blink */
        float glitchT = t % 6.5F;
        float lx0 = cx - lw / 2F;

        if (glitchT < 0.18F)
        {
            float split = 1.6F * (float) Math.sin(glitchT / 0.18F * Math.PI);
            float jitter = ((int) (glitchT * 60F) % 2 == 0 ? 1F : -1F) * 0.8F;

            Gfx.image(b, STUDIO_LOGO, lx0 - split + jitter, ly, lw, lh, c(0x99ff2a7a));
            Gfx.image(b, STUDIO_LOGO, lx0 + split + jitter, ly, lw, lh, c(0x9940e8ff));
            Gfx.image(b, STUDIO_LOGO, lx0 + jitter * 0.5F, ly, lw, lh, c(0xccffffff));
        }
        else
        {
            Gfx.image(b, STUDIO_LOGO, lx0, ly, lw, lh, c(0xffffffff));
        }

        /* The name; its letters ripple while the plate is hovered */
        String brandName = "BBS-Rabbit";
        float nameScale = 1.25F;
        float nameY = bpy + 12 + lh + 9;
        float nx = cx - Gfx.width(b, brandName) * nameScale / 2F;

        if (bh > 0.01F)
        {
            for (int i = 0; i < brandName.length(); i++)
            {
                String ch = String.valueOf(brandName.charAt(i));
                float wave = (float) Math.sin(t * 6F - i * 0.6F) * 1.2F * bh;

                Gfx.text(b, ch, nx, nameY + wave, nameScale, c(Gfx.lerp(0xfff2f4f8, STUDIO_PINK, 0.25F * bh * (0.5F + 0.5F * (float) Math.sin(t * 6F - i * 0.6F)))));
                nx += Gfx.width(b, ch) * nameScale;
            }
        }
        else
        {
            Gfx.text(b, brandName, nx, nameY, nameScale, c(0xfff2f4f8));
        }

        String studio = tr("studio_short");
        String dot = "  ·  ";
        String library = tr("library");
        float lineW = Gfx.width(b, studio) + Gfx.width(b, dot) + Gfx.width(b, library);
        float lx = cx - lineW / 2F;
        float lineY = nameY + 15;

        Gfx.text(b, studio, lx, lineY, c(STUDIO_PINK));
        Gfx.text(b, dot, lx + Gfx.width(b, studio), lineY, c(0xff5a606c));
        Gfx.text(b, library, lx + Gfx.width(b, studio) + Gfx.width(b, dot), lineY, c(0xff7d8594));

        return bpy + bph + 8;
    }

    private static float frac(float v)
    {
        return v - (float) Math.floor(v);
    }

    /** Sidebar: one dot per colour label; clicking one shows only those films, again to show all. */
    private void labelFilterRow(Batcher2D b, float x, float y, float w, float clipTop, float clipBottom)
    {
        int[] counts = new int[LibraryEntry.LABELS + 1];

        for (String id : this.films)
        {
            counts[this.thumbs.label(id)]++;
        }

        float left = x + 16, right = x + w - 16;
        float step = (right - left) / (LibraryEntry.LABELS - 1);
        float cy = y + 7;

        if (this.labelFilter != 0)
        {
            /* A soft track behind the row says a filter is on */
            Gfx.roundRect(b, x + 6, y - 1, w - 12, 16, 8, c(Gfx.wash(0.05F)));
        }

        for (int i = 1; i <= LibraryEntry.LABELS; i++)
        {
            final int value = i;
            float cx = left + (i - 1) * step;
            String key = "flabel:" + i;
            Hit hit = this.hitClipped(key, cx - step / 2F, y - 1, step, 16, clipTop, clipBottom);

            if (hit != null)
            {
                hit.click = () -> this.setLabelFilter(value);
                hit.tooltip = tr("label_filter", tr("label." + i), counts[i]);
            }

            float hv = this.hover(key);
            float on = this.anim("flabel-on:" + i, this.labelFilter == i ? 1F : 0F, 14F);
            int color = 0xff000000 | FilmThumbnails.labelColor(i);
            float dim = this.labelFilter != 0 && this.labelFilter != i ? 0.35F : (counts[i] == 0 ? 0.4F : 1F);
            float radius = 3.2F + 1F * hv + 0.8F * on;

            if (on > 0.01F)
            {
                Gfx.radial(b, cx, cy, radius + 7F, c(Gfx.alpha(color, 0.35F * on)), c(Gfx.alpha(color, 0F)));
                Gfx.circleRing(b, cx, cy, radius + 2.6F, 1.2F, c(Gfx.alpha(color, on)));
            }

            Gfx.circle(b, cx, cy, radius, c(Gfx.mulA(color, Math.max(dim, hv))));
        }
    }

    private void footerRow(Batcher2D b, String key, Icon icon, String label, float x, float y, float w, Runnable action)
    {
        float hv = this.hover(key);

        this.hit(key, x + 6, y, w - 12, 18).click = action;

        if (hv > 0.01F)
        {
            Gfx.roundRect(b, x + 6, y, w - 12, 18, 6, c(Gfx.wash(0.07F * hv)));
        }

        int ink = Gfx.lerp(Gfx.inkMuted(1F), Gfx.ink(1F), hv);

        Gfx.icon(b, icon, x + 12, y + 1, c(ink));
        Gfx.text(b, Gfx.fit(b, label, (int) (w - 44)), x + 31, y + 5, c(ink));
    }

    private Hit hitClipped(String key, float x, float y, float w, float h, float top, float bottom)
    {
        float y1 = Math.max(y, top), y2 = Math.min(y + h, bottom);

        if (y2 <= y1)
        {
            return null;
        }

        return this.hit(key, x, y1, w, y2 - y1);
    }

    private void countPill(Batcher2D b, String text, float right, float y, boolean active)
    {
        int tw = Gfx.width(b, text);
        float pw = tw + 8;

        Gfx.roundRect(b, right - pw, y - 2, pw, 12, 6, c(active ? Gfx.alpha(0x000000, 0.18F) : Gfx.wash(0.07F)));
        Gfx.text(b, text, right - pw + 4, y, c(active ? Gfx.mulA(Gfx.onAccent(), 0.9F) : Gfx.inkMuted(1F)));
    }

    /** A small triangle pointing right, turned down by {@code turn} (0..1). */
    private void chevron(Batcher2D b, float cx, float cy, float turn, int color)
    {
        double angle = turn * Math.PI / 2;
        float[][] points = {{-1.5F, -3F}, {2.5F, 0F}, {-1.5F, 3F}};
        float[] p = new float[6];

        for (int i = 0; i < 3; i++)
        {
            p[i * 2] = cx + (float) (points[i][0] * Math.cos(angle) - points[i][1] * Math.sin(angle));
            p[i * 2 + 1] = cy + (float) (points[i][0] * Math.sin(angle) + points[i][1] * Math.cos(angle));
        }

        Gfx.triangle(b, p[0], p[1], p[2], p[3], p[4], p[5], color);
    }

    private float sidebarContentHeight()
    {
        List<String> rows = this.folderRows();

        return 16 + 22 * 3 + (FilmThumbnails.enabled() ? 20 : 0) + 8 + 18 + Math.max(1, rows.size()) * 20 + 6;
    }

    private List<String> folderRows()
    {
        List<String> rows = new ArrayList<>();

        for (String path : this.folders)
        {
            String parent = parentOf(path.substring(0, path.length() - 1));
            boolean show = true;

            /* Shown when every ancestor is unfolded */
            for (String p = parent; !p.isEmpty(); p = parentOf(p.substring(0, p.length() - 1)))
            {
                if (!this.expanded.contains(p))
                {
                    show = false;

                    break;
                }
            }

            if (show)
            {
                rows.add(path);
            }
        }

        return rows;
    }

    private boolean hasSubfolders(String path)
    {
        String next = this.folders.higher(path);

        return next != null && next.startsWith(path);
    }

    private void toggleExpanded(String path)
    {
        if (!this.expanded.remove(path))
        {
            this.expanded.add(path);
        }
    }

    /* Header */

    private String scopeTitle()
    {
        switch (this.scope)
        {
            case SCOPE_RECENT: return tr("recent");
            case SCOPE_FAVORITES: return tr("favorites");
            case SCOPE_FOLDER: return baseName(this.folder.substring(0, Math.max(0, this.folder.length() - 1)));
            default: return tr("all");
        }
    }

    private void renderHeader(UIContext context, Batcher2D b)
    {
        float x = this.mx, y = this.my, w = this.mw;

        /* Breadcrumb: library > folder > subfolder */
        float bx = x + 2;
        float hv = this.hover("crumb:root");
        String root = tr("library");

        this.hit("crumb:root", bx - 2, y - 1, Gfx.width(b, root) + 4, 11).click = () -> this.setScope(SCOPE_ALL, "");
        Gfx.text(b, root, bx, y, c(Gfx.lerp(Gfx.inkMuted(1F), Gfx.accent(), hv)));
        bx += Gfx.width(b, root) + 4;

        List<String[]> crumbs = new ArrayList<>();

        if (this.scope == SCOPE_FOLDER)
        {
            String path = "";

            for (String part : this.folder.split("/"))
            {
                if (part.isEmpty()) continue;

                path += part + "/";
                crumbs.add(new String[] {part, path});
            }
        }
        else
        {
            crumbs.add(new String[] {this.scopeTitle(), null});
        }

        for (int i = 0; i < crumbs.size(); i++)
        {
            String[] crumb = crumbs.get(i);
            boolean last = i == crumbs.size() - 1;

            this.chevron(b, bx + 3, y + 4, 0F, c(Gfx.inkMuted(0.8F)));
            bx += 10;

            if (crumb[1] != null && !last)
            {
                String target = crumb[1];
                float h2 = this.hover("crumb:" + target);

                this.hit("crumb:" + target, bx - 2, y - 1, Gfx.width(b, crumb[0]) + 4, 11).click = () -> this.setScope(SCOPE_FOLDER, target);
                Gfx.text(b, crumb[0], bx, y, c(Gfx.lerp(Gfx.inkMuted(1F), Gfx.accent(), h2)));
            }
            else
            {
                Gfx.text(b, crumb[0], bx, y, c(Gfx.inkSoft(1F)));
            }

            bx += Gfx.width(b, crumb[0]) + 4;
        }

        /* Title and summary */
        String title = this.scopeTitle();
        float scale = 1.6F;

        float room = w - Gfx.width(b, tr("new_film")) - 34 - 16;

        title = Gfx.fit(b, title, (int) (room / scale));
        Gfx.text(b, title, x + 1, y + 14, scale, c(Gfx.ink(1F)));

        long total = 0;

        for (String id : this.visible)
        {
            FilmMeta meta = this.store.get(id);

            if (meta != null) total += meta.duration;
        }

        String summary = tr("count", this.visible.size()) + (total > 0 ? "  ·  " + tr("total", formatTicks(total)) : "");

        if (this.labelFilter != 0)
        {
            summary += "  ·  " + tr("label_only", tr("label." + this.labelFilter));
        }

        float summaryX = x + 2 + Gfx.width(b, title) * scale + 10;

        if (room - (summaryX - x) > 30)
        {
            Gfx.text(b, Gfx.fit(b, summary, (int) (room - (summaryX - x))), summaryX, y + 20, c(Gfx.inkMuted(1F)));
        }

        /* New film: the primary action */
        String label = tr("new_film");
        float bw = Gfx.width(b, label) + 34;
        float bh = 22;
        float bxr = x + w - bw;
        float by = y + 10;
        float h = this.hover("newfilm");
        float lift = h * 1.5F;

        this.hit("newfilm", bxr, by, bw, bh).click = this::newFilm;
        Gfx.shadow(b, bxr, by + 2 - lift, bw, bh, 8, 8 + h * 6, c(Gfx.accent(0.30F + 0.35F * h)));
        Gfx.roundRect(b, bxr, by - lift, bw, bh, 8, c(Gfx.lerp(Gfx.accent(), 0xffffffff, 0.18F + 0.08F * h)), c(Gfx.accent()), c(Gfx.accent()), c(Gfx.lerp(Gfx.accent(), 0xff000000, 0.12F)));
        Gfx.roundRectV(b, bxr + 1, by - lift + 1, bw - 2, bh / 2F, 7, c(Gfx.alpha(0xffffff, 0.16F)), c(Gfx.alpha(0xffffff, 0F)));
        Gfx.icon(b, Icons.ADD, bxr + 13, by - lift + bh / 2F, 1F, c(Gfx.onAccent()));
        Gfx.text(b, label, bxr + 23, by - lift + 7, c(Gfx.onAccent()));
    }

    /* Toolbar */

    private void renderToolbar(UIContext context, Batcher2D b)
    {
        float y = this.toolbarY;
        float focus = this.anim("search-focus", this.search.isFocused() ? 1F : 0F, 14F);
        float hv = this.hover("search");

        /* Search pill; the text field itself is a child drawn on top */
        this.hit("search", this.searchX, y, this.searchW, 22).click = () -> context.focus(this.search);

        if (focus > 0.01F)
        {
            Gfx.shadow(b, this.searchX, y, this.searchW, 22, 9, 6 * focus, c(Gfx.accent(0.35F * focus)));
        }

        Gfx.roundRect(b, this.searchX, y, this.searchW, 22, 9, c(Gfx.deep(0.75F + 0.15F * focus)));
        Gfx.roundOutline(b, this.searchX, y, this.searchW, 22, 9, 1F, c(Gfx.lerp(Gfx.wash(0.08F + 0.06F * hv), Gfx.accent(0.9F), focus)));
        Gfx.icon(b, Icons.SEARCH, this.searchX + 11, y + 11, 1F, c(Gfx.lerp(Gfx.inkMuted(1F), Gfx.accent(), focus)));

        if (!this.search.getText().isEmpty())
        {
            float cx = this.searchX + this.searchW - 12;
            float ch = this.hover("search-clear");

            this.hit("search-clear", cx - 8, y + 3, 16, 16).click = () ->
            {
                this.search.setText("");
                this.dirty = true;
            };
            Gfx.circle(b, cx, y + 11, 6, c(Gfx.wash(0.08F + 0.1F * ch)));
            Gfx.icon(b, Icons.CLOSE, cx, y + 11, 0.75F, c(Gfx.inkSoft(1F)));
        }

        /* Sort */
        float sx = this.searchX + this.searchW + 6;
        float sw = 96;
        float sh = this.hover("sort");
        String sortLabel = tr("sort." + this.store.sort);

        this.hit("sort", sx, y, sw, 22).click = this::sortMenu;
        Gfx.roundRect(b, sx, y, sw, 22, 9, c(Gfx.raised(0.7F + 0.2F * sh)));
        Gfx.roundOutline(b, sx, y, sw, 22, 9, 1F, c(Gfx.wash(0.08F + 0.08F * sh)));
        Gfx.text(b, Gfx.fit(b, sortLabel, (int) sw - 34), sx + 9, y + 7, c(Gfx.ink(0.95F)));
        Gfx.icon(b, this.store.descending ? Icons.ARROW_DOWN : Icons.ARROW_UP, sx + sw - 23, y + 11, 0.75F, c(Gfx.inkMuted(1F)));
        this.chevron(b, sx + sw - 10, y + 11, 1F, c(Gfx.inkSoft(1F)));

        /* List or grid, with a pill that slides between them */
        float vx = sx + sw + 6;
        float vw = 50;
        float slide = this.anim("view", this.store.grid ? 1F : 0F, 16F);

        Gfx.roundRect(b, vx, y, vw, 22, 9, c(Gfx.deep(0.7F)));
        Gfx.roundOutline(b, vx, y, vw, 22, 9, 1F, c(Gfx.wash(0.08F)));
        Gfx.shadow(b, vx + 2 + slide * 23, y + 2, 23, 18, 7, 4, c(Gfx.accent(0.35F)));
        Gfx.roundRect(b, vx + 2 + slide * 23, y + 2, 23, 18, 7, c(Gfx.accent()));

        Hit list = this.hit("view:list", vx, y, vw / 2, 22);
        Hit grid = this.hit("view:grid", vx + vw / 2, y, vw / 2, 22);

        list.click = () -> this.setGrid(false);
        list.tooltip = tr("view_list");
        grid.click = () -> this.setGrid(true);
        grid.tooltip = tr("view_grid");

        Gfx.icon(b, Icons.LIST, vx + 2 + 11.5F, y + 11, 1F, c(Gfx.lerp(Gfx.onAccent(), Gfx.inkSoft(1F), slide)));
        Gfx.icon(b, Icons.GALLERY, vx + 25 + 11.5F, y + 11, 1F, c(Gfx.lerp(Gfx.inkSoft(1F), Gfx.onAccent(), slide)));

        /* Film check (体检): what the films of this world need and can't find */
        float hx = vx + vw + 6;
        float hh = this.hover("health");
        Hit health = this.hit("health", hx, y, 22, 22);

        health.click = this::openHealthCheck;
        health.tooltip = tr("health");
        Gfx.roundRect(b, hx, y, 22, 22, 9, c(Gfx.raised(0.7F + 0.2F * hh)));
        Gfx.roundOutline(b, hx, y, 22, 22, 9, 1F, c(Gfx.wash(0.08F + 0.08F * hh)));
        Gfx.icon(b, Icons.EXCLAMATION, hx + 11, y + 11, 1F, c(Gfx.lerp(Gfx.inkSoft(1F), Gfx.accent(), hh)));

        /* Count and hint */
        float ty = y + 28;
        String count = tr("count", this.visible.size());

        Gfx.text(b, count, this.mx + 2, ty, c(Gfx.inkMuted(1F)));

        String hint = tr("hint");

        if (Gfx.width(b, hint) < this.mw - Gfx.width(b, count) - 30)
        {
            Gfx.text(b, hint, this.mx + this.mw - Gfx.width(b, hint) - 2, ty, c(Gfx.inkMuted(0.8F)));
        }
    }

    private void openHealthCheck()
    {
        UIUtils.playClick();
        UIOverlay.addOverlay(this.getContext(), new UIFilmHealthOverlay(this.repository().getFolder(), this::open), 0.6F, 0.8F);
    }

    private void setGrid(boolean grid)
    {
        if (this.store.grid != grid)
        {
            this.store.grid = grid;
            this.store.save();
            this.listChangedAt = System.currentTimeMillis();
            this.selY = -1;
            this.scrollToSelected = true;
            UIUtils.playClick();
        }
    }

    /* List */

    private void renderList(UIContext context, Batcher2D b)
    {
        float x = this.listX, y = this.listY, w = this.listW, h = this.listH;

        this.card(b, x, y, w, h, 10, 0.72F);

        if (!this.namesKnown)
        {
            this.renderLoading(b, x, y, w, h);

            return;
        }

        if (this.visible.isEmpty())
        {
            this.renderEmpty(b, x, y, w, h);

            return;
        }

        if (this.store.grid)
        {
            this.renderGrid(context, b, x, y, w, h);
        }
        else
        {
            this.renderRows(context, b, x, y, w, h);
        }
    }

    private float[] columns(float x, float w)
    {
        /* name, location, duration, modified (x positions), and the end of the name column */
        float pad = 12;
        float modifiedW = 84;
        float durationW = 50;
        boolean showLocation = w > 380;
        float locationW = showLocation ? Math.max(70, Math.min(170, w * 0.24F)) : 0;
        float modifiedX = x + w - pad - modifiedW;
        float durationX = modifiedX - durationW;
        float locationX = durationX - locationW;

        return new float[] {x + pad, locationX, durationX, modifiedX, showLocation ? 1 : 0};
    }

    private void renderRows(UIContext context, Batcher2D b, float x, float y, float w, float h)
    {
        float[] col = this.columns(x, w);
        float headerY = y + 4;

        /* Column headers, clickable to sort */
        this.columnHeader(b, "col:name", tr("col.name"), col[0] + this.rowLead(), headerY, FilmLibraryStore.SORT_NAME);

        if (col[4] > 0)
        {
            Gfx.text(b, tr("col.location"), col[1], headerY + 4, c(Gfx.inkMuted(1F)));
        }

        this.columnHeader(b, "col:duration", tr("col.duration"), col[2], headerY, FilmLibraryStore.SORT_DURATION);
        this.columnHeader(b, "col:modified", tr("col.modified"), col[3], headerY, FilmLibraryStore.SORT_MODIFIED);
        b.box(x + 8, y + HEADER_ROW_H + 2, x + w - 8, y + HEADER_ROW_H + 3, c(Gfx.wash(0.07F)));

        float top = y + HEADER_ROW_H + 4;
        float viewH = h - HEADER_ROW_H - 8;
        float contentH = this.visible.size() * ROW_H;
        float maxScroll = Math.max(0, contentH - viewH);
        int selectedIndex = this.selected == null ? -1 : this.visible.indexOf(this.selected);

        if (this.scrollToSelected && selectedIndex >= 0)
        {
            float rowTop = selectedIndex * ROW_H;

            if (rowTop < this.scrollTarget) this.scrollTarget = rowTop;
            if (rowTop + ROW_H > this.scrollTarget + viewH) this.scrollTarget = rowTop + ROW_H - viewH;

            this.scrollToSelected = false;
        }

        this.scrollTarget = Math.max(0, Math.min(maxScroll, this.scrollTarget));
        this.scroll = Gfx.approach(this.scroll, this.scrollTarget, 20F, this.dt);

        b.clip((int) x, (int) top, (int) w, (int) viewH, context);

        int first = Math.max(0, (int) (this.scroll / ROW_H));
        int last = Math.min(this.visible.size() - 1, (int) ((this.scroll + viewH) / ROW_H) + 1);

        /* Gliding selection highlight */
        if (selectedIndex >= 0)
        {
            float target = selectedIndex * ROW_H;

            this.selY = this.selY < 0 ? target : Gfx.approach(this.selY, target, 22F, this.dt);

            float ry = top + this.selY - this.scroll;
            float pulse = 1F - Gfx.easeOut((System.currentTimeMillis() - this.selectedAt) / 400F);

            Gfx.shadow(b, x + 6, ry + 1, w - 12, ROW_H - 2, 7, 4 + pulse * 6, c(Gfx.accent(0.25F + 0.25F * pulse)));
            Gfx.roundRectH(b, x + 6, ry + 1, w - 12, ROW_H - 2, 7, c(Gfx.accent(0.32F)), c(Gfx.accent(0.12F)));
            Gfx.roundOutline(b, x + 6, ry + 1, w - 12, ROW_H - 2, 7, 1F, c(Gfx.accent(0.55F)));
            Gfx.roundRect(b, x + 7, ry + 5, 2.5F, ROW_H - 10, 1.2F, c(Gfx.accent()));
        }
        else
        {
            this.selY = -1;
        }

        long since = System.currentTimeMillis() - this.listChangedAt;

        for (int i = first; i <= last; i++)
        {
            String id = this.visible.get(i);
            float ry = top + i * ROW_H - this.scroll;
            float appear = Gfx.easeOut((since - Math.min(14, i - first) * 22) / 260F);
            float keep = this.fade;

            this.fade = keep * appear;
            this.renderRow(b, id, i, x, ry + (1F - appear) * 6F, w, col, top, top + viewH);
            this.fade = keep;
        }

        b.unclip(context);

        this.scrollbar(b, x + w - 5, top + 2, viewH - 4, contentH, viewH, this.scroll);
    }

    private void columnHeader(Batcher2D b, String key, String label, float x, float y, int sort)
    {
        float hv = this.hover(key);
        boolean active = this.scope != SCOPE_RECENT && this.store.sort == sort;
        int lw = Gfx.width(b, label);

        this.hit(key, x - 3, y, lw + 18, 14).click = () -> this.setSort(sort);

        if (hv > 0.01F)
        {
            Gfx.roundRect(b, x - 4, y, lw + 18, 14, 5, c(Gfx.wash(0.06F * hv)));
        }

        Gfx.text(b, label, x, y + 4, c(active ? Gfx.ink(1F) : Gfx.lerp(Gfx.inkMuted(1F), Gfx.inkSoft(1F), hv)));

        if (active)
        {
            Gfx.icon(b, this.store.descending ? Icons.ARROW_DOWN : Icons.ARROW_UP, x + lw + 7, y + 8, 0.6F, c(Gfx.accent()));
        }
    }

    private void renderRow(Batcher2D b, String id, int index, float x, float y, float w, float[] col, float clipTop, float clipBottom)
    {
        boolean selected = id.equals(this.selected);
        String key = "row:" + id;
        Hit hit = this.hitClipped(key, x + 6, y + 1, w - 12, ROW_H - 2, clipTop, clipBottom);
        float hv = Math.max(this.hover(key), this.hover("presence:" + id));
        FilmMeta meta = this.store.get(id);
        boolean favorite = this.store.isFavorite(id);
        List<FilmPresence.Viewer> viewers = this.presence.get(id);

        if (hit != null)
        {
            hit.click = () -> this.select(id, false);
            hit.dbl = () -> this.open(id);
            hit.right = () -> this.filmMenu(id);
        }

        if (!selected && hv > 0.01F)
        {
            Gfx.roundRect(b, x + 6, y + 1, w - 12, ROW_H - 2, 7, c(Gfx.wash(0.055F * hv)));
        }

        float live = this.anim("live:" + id, viewers.isEmpty() ? 0F : 1F, 5F);

        if (live > 0.01F)
        {
            int liveColor = this.presenceColor(id, viewers);
            float breath = 0.5F + 0.5F * (float) Math.sin(this.time * 2.4F);

            Gfx.roundRectH(b, x + 6, y + 1, w - 12, ROW_H - 2, 7, c(Gfx.alpha(0x000000, 0F)), c(Gfx.alpha(liveColor, (0.14F + 0.08F * breath) * live)));
            Gfx.roundRect(b, x + w - 9.5F, y + 5, 2.5F, ROW_H - 10, 1.2F, c(Gfx.alpha(liveColor, (0.7F + 0.3F * breath) * live)));
        }

        float cy = y + ROW_H / 2F;
        int hue = filmColor(id);
        int label = this.thumbs.label(id);

        if (FilmThumbnails.enabled())
        {
            this.rowThumbnail(b, id, label, col[0] - 2 + hv, cy, hv, selected);
        }
        else
        {
            Gfx.icon(b, Icons.FILM, col[0] + 8 + hv, cy, 1F, c(selected ? Gfx.accent() : Gfx.lerp(Gfx.mulA(hue, 0.85F), hue, hv)));
        }

        /* Name, with the favourite star at the end of the column */
        float nameX = col[0] + this.rowLead() + hv;
        float nameEnd = (col[4] > 0 ? col[1] : col[2]) - 22;
        float presenceW = this.presenceWidth(b, viewers, 10, this.hover("presence:" + id));
        /* Room for the heads fanned out, so hovering them doesn't re-trim the name */
        float presenceRoom = this.presenceWidth(b, viewers, 10, 1F);
        String name = Gfx.fit(b, baseName(id), (int) (nameEnd - nameX - (presenceRoom > 0 ? presenceRoom + 6 : 0)));

        Gfx.text(b, name, nameX, cy - 4, c(selected ? Gfx.ink(1F) : Gfx.lerp(Gfx.inkSoft(1F), Gfx.ink(1F), hv)));
        this.presenceInline(b, id, viewers, nameEnd - 2 - presenceW, cy, clipTop, clipBottom);
        this.star(b, id, nameEnd + 8, cy, favorite, Math.max(hv, selected ? 0.6F : 0F), clipTop, clipBottom);

        if (col[4] > 0)
        {
            String parent = parentOf(id);
            String location = parent.isEmpty() ? "/" : parent.substring(0, parent.length() - 1);

            Gfx.text(b, Gfx.fit(b, location, (int) (col[2] - col[1] - 10)), col[1], cy - 4, c(Gfx.inkMuted(1F)));
        }

        if (meta == null)
        {
            this.shimmer(b, col[2], cy - 3, 28, 6, index);
            this.shimmer(b, col[3], cy - 3, 52, 6, index + 3);
        }
        else
        {
            Gfx.text(b, formatTicks(meta.duration), col[2], cy - 4, c(selected ? Gfx.accent() : Gfx.inkSoft(1F)));
            Gfx.text(b, Gfx.fit(b, formatDate(meta.modified), (int) (x + w - col[3] - 10)), col[3], cy - 4, c(Gfx.inkSoft(selected ? 1F : 0.85F)));
        }
    }

    private float rowLead()
    {
        return FilmThumbnails.enabled() ? 30 : 22;
    }

    /** List row: a little 16:9 picture of the film (or its colours), ringed in its label colour. */
    private void rowThumbnail(Batcher2D b, String id, int label, float x, float cy, float hover, boolean selected)
    {
        float w = 24 + hover * 1.5F, h = w * 9F / 16F;
        float y = cy - h / 2F;
        FilmThumbnails.Thumb cover = this.thumbs.cover(id);
        float shown = this.anim("img:" + id, cover != null ? 1F : 0F, 6F);
        float labelIn = this.anim("lbl:" + id, label != 0 ? 1F : 0F, 12F);

        if (shown < 0.995F)
        {
            float hue = hueOf(id);

            Gfx.roundRect(b, x, y, w, h, 3, c(Gfx.hsv(hue, 0.55F, 0.62F)), c(Gfx.hsv(hue + 0.08F, 0.65F, 0.40F)), c(Gfx.hsv(hue - 0.06F, 0.7F, 0.28F)), c(Gfx.hsv(hue + 0.14F, 0.75F, 0.14F)));
            Gfx.icon(b, Icons.FILM, x + w / 2F, cy, 0.6F, c(Gfx.alpha(0xffffff, 0.8F * (1F - shown))));
        }

        if (cover != null)
        {
            Gfx.imageRound(b, cover.texture, x, y, w, h, 3, 0F, 0F, 1F, 1F, c(Gfx.alpha(0xffffff, Gfx.easeInOut(shown))));
        }

        int rim = selected ? Gfx.accent(0.9F) : Gfx.alpha(0xffffff, 0.14F + 0.12F * hover);

        if (labelIn > 0.01F)
        {
            int color = 0xff000000 | FilmThumbnails.labelColor(label != 0 ? label : this.lastLabel(id));

            rim = Gfx.lerp(rim, color, labelIn);
            Gfx.shadow(b, x, y, w, h, 3, 3 * labelIn * this.labelPop(id), c(Gfx.alpha(color, 0.45F * labelIn)));
        }

        Gfx.roundOutline(b, x, y, w, h, 3, labelIn > 0.5F ? 1.25F : 1F, c(rim));
    }

    /** The favourite star: hidden until hovered unless set, and it pops when toggled. */
    private void star(Batcher2D b, String id, float cx, float cy, boolean favorite, float reveal, float clipTop, float clipBottom)
    {
        String key = "star:" + id;
        float hv = this.hover(key);
        Hit hit = this.hitClipped(key, cx - 8, cy - 8, 16, 16, clipTop, clipBottom);

        if (hit != null)
        {
            hit.click = () -> this.toggleFavorite(id);
            hit.tooltip = tr(favorite ? "unfavorite" : "favorite");
        }

        Long popped = this.pops.get(id);
        float pop = popped == null ? 1F : Gfx.easeOutBack((System.currentTimeMillis() - popped) / 360F);
        float scale = (0.85F + 0.15F * hv) * (popped == null ? 1F : 0.4F + 0.6F * pop);
        int gold = 0xffffc23d;

        if (favorite)
        {
            if (popped != null && pop < 1F)
            {
                Gfx.radial(b, cx, cy, 12 * pop, c(Gfx.alpha(gold, 0.5F * (1F - pop))), c(Gfx.alpha(gold, 0F)));
            }

            Gfx.icon(b, Icons.FAVORITE, cx, cy, scale, c(gold));
        }
        else if (reveal > 0.01F || hv > 0.01F)
        {
            Gfx.icon(b, Icons.FAVORITE, cx, cy, scale, c(Gfx.lerp(Gfx.inkMuted(0.7F * reveal), gold, hv)));
        }
    }

    /* Other players in the film (BBS Together) */

    private static final int MAX_HEADS = 3;
    private static final int LIVE = 0xff4cd98a;

    /** Heads overlap by a third and fan out while hovered; more than three end in "+N". */
    private float headsWidth(Batcher2D b, int count, float size, float spread)
    {
        int shown = Math.min(count, MAX_HEADS);
        float width = size + (shown - 1) * size * (0.66F + 0.5F * spread);

        if (count > shown)
        {
            width += 3 + Gfx.width(b, "+" + (count - shown)) + 6;
        }

        return width;
    }

    private void heads(Batcher2D b, List<FilmPresence.Viewer> viewers, float x, float cy, float size, float spread, int gap, boolean onPoster)
    {
        int shown = Math.min(viewers.size(), MAX_HEADS);
        float step = size * (0.66F + 0.5F * spread);
        float y = cy - size / 2F;

        for (int i = 0; i < shown; i++)
        {
            FilmPresence.Viewer viewer = viewers.get(i);
            float hx = x + i * step;

            /* A rim in the colour underneath parts each head from the one it overlaps */
            float ring = size >= 11 ? 1.5F : 1F;

            Gfx.roundRect(b, hx - ring - 1, y - ring - 1, size + ring * 2 + 2, size + ring * 2 + 2, 3.5F, c(gap));
            Gfx.roundRect(b, hx - ring, y - ring, size + ring * 2, size + ring * 2, 2.5F, c(0xff000000 | viewer.color));
            b.texturedBox(viewer.skin, c(0xffffffff), hx, y, size, size, 8, 8, 16, 16, 64, 64);
            b.texturedBox(viewer.skin, c(0xffffffff), hx, y, size, size, 40, 8, 48, 16, 64, 64);
        }

        if (viewers.size() > shown)
        {
            String more = "+" + (viewers.size() - shown);
            float mx = x + (shown - 1) * step + size + 3;
            float mw = Gfx.width(b, more) + 6;

            Gfx.roundRect(b, mx, cy - 5.5F, mw, 11, 5.5F, c(onPoster ? Gfx.alpha(0xffffff, 0.16F) : Gfx.wash(0.09F)));
            Gfx.text(b, more, mx + 3, cy - 4, c(onPoster ? 0xfff2f4f8 : Gfx.inkSoft(1F)));
        }
    }

    /** "Live" dot: steady core, a ring that keeps breathing out of it. */
    private void liveDot(Batcher2D b, float cx, float cy)
    {
        this.liveDot(b, cx, cy, 2F);
    }

    private void liveDot(Batcher2D b, float cx, float cy, float r)
    {
        float t = (this.time % 1.6F) / 1.6F;

        Gfx.circle(b, cx, cy, r + r * 1.8F * Gfx.easeOut(t), c(Gfx.alpha(LIVE, 0.4F * (1F - t))));
        Gfx.circle(b, cx, cy, r, c(LIVE));
    }

    /** The colour of the people in a film; with several, it drifts from one player's colour to the next. */
    private int presenceColor(String id, List<FilmPresence.Viewer> viewers)
    {
        if (viewers.isEmpty())
        {
            Integer last = this.presenceColors.get(id);

            return last == null ? LIVE & 0xffffff : last;
        }

        int color;

        if (viewers.size() == 1)
        {
            color = viewers.get(0).color;
        }
        else
        {
            float t = this.time / 2.2F;
            int i = (int) t;
            float blend = Gfx.easeInOut(Gfx.clamp01((t - i - 0.55F) / 0.45F));

            color = Gfx.lerp(0xff000000 | viewers.get(i % viewers.size()).color, 0xff000000 | viewers.get((i + 1) % viewers.size()).color, blend) & 0xffffff;
        }

        this.presenceColors.put(id, color);

        return color;
    }

    /** Light enough to read on the card in either theme. */
    private static int readable(int rgb)
    {
        return Gfx.lerp(0xff000000 | rgb, Gfx.light() ? 0xff000000 : 0xffffffff, Gfx.light() ? 0.25F : 0.3F);
    }

    private void presenceHit(String id, List<FilmPresence.Viewer> viewers, float x, float y, float w, float h, float clipTop, float clipBottom)
    {
        Hit hit = this.hitClipped("presence:" + id, x, y, w, h, clipTop, clipBottom);

        if (hit != null)
        {
            hit.click = () -> this.select(id, false);
            hit.dbl = () -> this.open(id);
            hit.right = () -> this.filmMenu(id);
            hit.tooltip = tr("presence", FilmPresence.names(viewers));
        }
    }

    /** Width of the list row's presence (dot + heads), 0 when nobody else is in the film. */
    private float presenceWidth(Batcher2D b, List<FilmPresence.Viewer> viewers, float size, float spread)
    {
        return viewers.isEmpty() ? 0 : 9 + this.headsWidth(b, viewers.size(), size, spread);
    }

    /** List row: dot and heads right-aligned in the name column, in front of the star. */
    private void presenceInline(Batcher2D b, String id, List<FilmPresence.Viewer> viewers, float x, float cy, float clipTop, float clipBottom)
    {
        if (viewers.isEmpty())
        {
            return;
        }

        float spread = this.hover("presence:" + id);
        float w = this.presenceWidth(b, viewers, 10, spread);

        this.liveDot(b, x + 2.5F, cy);
        this.heads(b, viewers, x + 9, cy, 10, spread, Gfx.raised(1F), false);
        this.presenceHit(id, viewers, x - 3, cy - 8, w + 6, 16, clipTop, clipBottom);
    }

    /** Poster: a dark pill in the top left corner, opposite the duration badge. */
    private void presenceBadge(Batcher2D b, String id, float x, float y, boolean big, float clipTop, float clipBottom)
    {
        List<FilmPresence.Viewer> viewers = this.presence.get(id);

        if (viewers.isEmpty())
        {
            return;
        }

        float spread = this.hover("presence:" + id);
        float size = big ? 16 : 12;
        float h = size + 7;
        float px = x + (big ? 7 : 4);
        float py = y + (big ? 7 : 5) + 3;
        float dot = big ? 3F : 2.5F;
        float headsX = px + 7 + dot * 2 + 5;
        float w = headsX - px + this.headsWidth(b, viewers.size(), size, spread) + 5;
        float cy = py + h / 2F;
        int color = this.presenceColor(id, viewers);
        float breath = 0.5F + 0.5F * (float) Math.sin(this.time * 2.4F);

        /* Dark glass tinted with the player's colour from the left, ringed in it */
        Gfx.shadow(b, px, py, w, h, h / 2F, 4 + 3 * breath, c(Gfx.alpha(color, 0.35F + 0.2F * breath)));
        Gfx.roundRectH(b, px, py, w, h, h / 2F, c(Gfx.alpha(Gfx.lerp(0xff000000, 0xff000000 | color, 0.45F), 0.82F)), c(Gfx.alpha(0x000000, 0.62F)));
        Gfx.roundOutline(b, px, py, w, h, h / 2F, 1F, c(Gfx.alpha(color, 0.55F + 0.3F * breath)));
        this.liveDot(b, px + 7 + dot, cy, dot);
        this.heads(b, viewers, headsX, cy, size, spread, Gfx.alpha(0x121317, 0.9F), true);
        this.presenceHit(id, viewers, px, py, w, h, clipTop, clipBottom);
    }

    private void shimmer(Batcher2D b, float x, float y, float w, float h, int seed)
    {
        float t = (this.time * 0.9F + seed * 0.07F) % 1.6F;
        float glow = (float) Math.max(0, Math.sin(t / 1.6F * Math.PI));

        Gfx.roundRect(b, x, y, w, h, h / 2F, c(Gfx.wash(0.06F + 0.07F * glow)));
    }

    private void scrollbar(Batcher2D b, float x, float y, float h, float content, float view, float scroll)
    {
        if (content <= view + 1)
        {
            return;
        }

        float active = this.anim("scrollbar", this.time - this.lastScrollTime < 0.9F ? 1F : 0.35F, 6F);
        float thumbH = Math.max(18, h * view / content);
        float thumbY = y + (h - thumbH) * (scroll / (content - view));

        Gfx.roundRect(b, x, thumbY, 3, thumbH, 1.5F, c(Gfx.wash(0.12F + 0.25F * active)));
    }

    private void renderGrid(UIContext context, Batcher2D b, float x, float y, float w, float h)
    {
        int cols = this.gridColumns();
        float inner = w - 16;
        float cardW = (inner - GAP * (cols - 1)) / cols;
        float posterH = cardW * 9F / 16F;
        float cardH = posterH + 32;
        int rows = (this.visible.size() + cols - 1) / cols;
        float top = y + 8;
        float viewH = h - 16;
        float contentH = rows * (cardH + GAP) - GAP;
        float maxScroll = Math.max(0, contentH - viewH);
        int selectedIndex = this.selected == null ? -1 : this.visible.indexOf(this.selected);

        if (this.scrollToSelected && selectedIndex >= 0)
        {
            float rowTop = (selectedIndex / cols) * (cardH + GAP);

            if (rowTop < this.scrollTarget) this.scrollTarget = rowTop;
            if (rowTop + cardH > this.scrollTarget + viewH) this.scrollTarget = rowTop + cardH - viewH;

            this.scrollToSelected = false;
        }

        this.scrollTarget = Math.max(0, Math.min(maxScroll, this.scrollTarget));
        this.scroll = Gfx.approach(this.scroll, this.scrollTarget, 20F, this.dt);

        b.clip((int) x, (int) top - 4, (int) w, (int) viewH + 8, context);

        long since = System.currentTimeMillis() - this.listChangedAt;
        int firstRow = Math.max(0, (int) (this.scroll / (cardH + GAP)));
        int lastRow = Math.min(rows - 1, (int) ((this.scroll + viewH) / (cardH + GAP)) + 1);

        for (int r = firstRow; r <= lastRow; r++)
        {
            for (int col = 0; col < cols; col++)
            {
                int i = r * cols + col;

                if (i >= this.visible.size())
                {
                    break;
                }

                String id = this.visible.get(i);
                float cx = x + 8 + col * (cardW + GAP);
                float cy = top + r * (cardH + GAP) - this.scroll;
                float appear = Gfx.easeOut((since - Math.min(16, i - firstRow * cols) * 28) / 300F);
                float keep = this.fade;

                this.fade = keep * appear;
                this.renderCard(b, id, i, cx, cy + (1F - appear) * 10F, cardW, posterH, cardH, top - 4, top + viewH + 4);
                this.fade = keep;
            }
        }

        b.unclip(context);

        this.scrollbar(b, x + w - 5, top + 2, viewH - 4, contentH, viewH, this.scroll);
    }

    private void renderCard(Batcher2D b, String id, int index, float x, float y, float w, float posterH, float h, float clipTop, float clipBottom)
    {
        String key = "card:" + id;
        boolean selected = id.equals(this.selected);
        float hv = Math.max(this.hover(key), this.hover("presence:" + id));
        float sel = this.anim("sel:" + id, selected ? 1F : 0F, 14F);
        float lift = hv * 2.5F;
        Hit hit = this.hitClipped(key, x, y, w, h, clipTop, clipBottom);
        FilmMeta meta = this.store.get(id);

        if (hit != null)
        {
            hit.click = () -> this.select(id, false);
            hit.dbl = () -> this.open(id);
            hit.right = () -> this.filmMenu(id);
        }

        y -= lift;

        /* Reached for, the card comes forward a little and leans toward the mouse */
        UIContext context = this.getContext();
        float lean = Math.max(-1F, Math.min(1F, (context.mouseX - (x + w / 2F)) / (w / 2F))) * hv;
        float grow = 1F + 0.03F * hv;
        float ccx = x + w / 2F, ccy = y + h / 2F;

        b.getContext().method_51448().method_22903();
        b.getContext().method_51448().method_46416(ccx + lean * 1.5F, ccy, 0F);
        b.getContext().method_51448().method_22905(grow, grow, 1F);
        b.getContext().method_51448().method_46416(-ccx, -ccy, 0F);

        /* Someone else is in this film: the card breathes in their colour */
        List<FilmPresence.Viewer> viewers = this.presence.get(id);
        float live = this.anim("live:" + id, viewers.isEmpty() ? 0F : 1F, 5F);
        int liveColor = this.presenceColor(id, viewers);
        float breath = 0.5F + 0.5F * (float) Math.sin(this.time * 2.4F);

        Gfx.shadow(b, x, y + 3 + lift, w, h, 9, 8 + hv * 6, c(Gfx.alpha(0x000000, (Gfx.light() ? 0.12F : 0.35F) + hv * 0.1F)));

        /* The cover's own light spills around the card when it is reached for */
        int mood = this.moodOf(id);

        if (mood >= 0 && hv > 0.01F)
        {
            Gfx.radial(b, x + w / 2F + lean * w * 0.15F, y + h * 0.45F, w * 0.8F, c(Gfx.alpha(mood, 0.38F * hv)), c(Gfx.alpha(mood, 0F)));
            Gfx.shadow(b, x, y + 2, w, h, 9, 8 + 10 * hv, c(Gfx.alpha(mood, 0.6F * hv)));
        }

        if (live > 0.01F)
        {
            Gfx.shadow(b, x, y, w, h, 9, (9 + 7 * breath) * live, c(Gfx.alpha(liveColor, (0.42F + 0.28F * breath) * live)));
        }

        if (sel > 0.01F)
        {
            int selColor = mood >= 0 ? Gfx.lerp(Gfx.accent(), 0xff000000 | mood, 0.6F) : Gfx.accent();

            Gfx.shadow(b, x, y, w, h, 9, (8 + 3 * breath) * sel, c(Gfx.alpha(selColor, (0.5F + 0.15F * breath) * sel)));
        }

        int body = Gfx.lerp(Gfx.raised(0.9F), Gfx.lerp(Gfx.raised(0.95F), Gfx.accent(0.95F), 0.18F), sel);

        Gfx.roundRect(b, x, y, w, h, 9, c(Gfx.lerp(body, Gfx.lerp(body, 0xff000000 | liveColor, 0.16F), live)));

        this.poster(b, id, meta, x + 3, y + 3, w - 6, posterH - 3, 7, hv, false);
        this.presenceBadge(b, id, x + 3, y + 3, false, clipTop, clipBottom);

        /* Colour label: a dot leading the name */
        int label = this.thumbs.label(id);
        float labelIn = this.anim("lbl:" + id, label != 0 ? 1F : 0F, 12F);
        float nameX = x + 8;

        if (labelIn > 0.01F)
        {
            int labelColor = FilmThumbnails.labelColor(label != 0 ? label : this.lastLabel(id));
            float pop = this.labelPop(id);

            Gfx.circle(b, x + 11, y + posterH + 10, 2.6F * labelIn * pop, c(0xff000000 | labelColor));
            Gfx.radial(b, x + 11, y + posterH + 10, 6F * labelIn, c(Gfx.alpha(labelColor, 0.3F * labelIn)), c(Gfx.alpha(labelColor, 0F)));
            nameX += 8 * labelIn;
        }

        String name = Gfx.fit(b, baseName(id), (int) (w - 30 - (nameX - x - 8)));

        Gfx.text(b, name, nameX, y + posterH + 6, c(selected ? Gfx.ink(1F) : Gfx.lerp(Gfx.inkSoft(1F), Gfx.ink(1F), hv)));

        String sub = meta == null ? "" : formatDate(meta.modified);

        if (!viewers.isEmpty())
        {
            /* Status line instead of the date: who is in it right now */
            this.liveDot(b, x + 11, y + posterH + 21, 2F);
            Gfx.text(b, Gfx.fit(b, tr("editing", FilmPresence.names(viewers)), (int) (w - 34)), x + 18, y + posterH + 17, c(readable(liveColor)));
        }
        else if (meta == null)
        {
            this.shimmer(b, x + 8, y + posterH + 18, 46, 5, index);
        }
        else
        {
            Gfx.text(b, Gfx.fit(b, sub, (int) (w - 16)), x + 8, y + posterH + 17, c(Gfx.inkMuted(1F)));
        }

        this.star(b, id, x + w - 12, y + posterH + 10, this.store.isFavorite(id), Math.max(hv, sel * 0.6F), clipTop, clipBottom);
        int rim = Gfx.wash(0.07F + 0.06F * hv);

        if (labelIn > 0.01F)
        {
            rim = Gfx.lerp(rim, Gfx.alpha(FilmThumbnails.labelColor(label != 0 ? label : this.lastLabel(id)), 0.55F), labelIn * 0.75F);
        }

        Gfx.roundOutline(b, x, y, w, h, 9, sel > 0.5F ? 1.5F : 1F, c(Gfx.lerp(Gfx.lerp(rim, 0xff000000 | liveColor, live * (0.55F + 0.35F * breath)), Gfx.accent(), sel)));

        if (live > 0.01F)
        {
            /* A halo just outside the card, apart from the selection outline */
            Gfx.roundOutline(b, x - 2.5F, y - 2.5F, w + 5, h + 5, 11, 1F, c(Gfx.alpha(liveColor, (0.35F + 0.4F * breath) * live)));
        }

        b.getContext().method_51448().method_22909();
    }

    /* Colour labels */

    private final Map<String, Integer> lastLabels = new HashMap<>();

    /** The label a film had last, so a removed one can fade out in its own colour. */
    private int lastLabel(String id)
    {
        int label = this.thumbs.label(id);

        if (label != 0)
        {
            this.lastLabels.put(id, label);

            return label;
        }

        return this.lastLabels.getOrDefault(id, 0);
    }

    /** A label dot springs in when the label is set. */
    private float labelPop(String id)
    {
        this.lastLabel(id);

        Long popped = this.pops.get("label:" + id);

        if (popped == null)
        {
            return 1F;
        }

        float t = (System.currentTimeMillis() - popped) / 420F;

        return t >= 1F ? 1F : 0.4F + 0.6F * Gfx.easeOutBack(t);
    }

    /* The generated poster */

    private static float hueOf(String id)
    {
        int hash = baseName(id).hashCode();

        hash ^= hash >>> 16;

        return ((hash * 0.618034F) % 1F + 1F) % 1F;
    }

    private static int filmColor(String id)
    {
        return Gfx.hsv(hueOf(id), 0.45F, 0.95F);
    }

    /**
     * The film's poster: its cover picture when there is one (faded in over the generated poster
     * while it arrives), with the hover frames on top while hovered; else the generated poster.
     */
    private void poster(Batcher2D b, String id, FilmMeta meta, float x, float y, float w, float h, float r, float hover, boolean big)
    {
        FilmThumbnails.Thumb cover = FilmThumbnails.enabled() ? this.thumbs.cover(id) : null;
        float shown = this.anim("img:" + id, cover != null ? 1F : 0F, 6F);

        if (shown < 0.995F || cover == null)
        {
            this.generatedPoster(b, id, meta, x, y, w, h, r, hover, big);
        }

        if (cover != null && shown > 0.005F)
        {
            float keep = this.fade;

            this.fade = keep * Gfx.easeInOut(shown);
            this.picturePoster(b, id, meta, cover, x, y, w, h, r, hover, big);
            this.fade = keep;
        }
    }

    /** The mood colour of a film's cover (for glows), or -1 without one. */
    private int moodOf(String id)
    {
        FilmThumbnails.Thumb cover = FilmThumbnails.enabled() ? this.thumbs.cover(id) : null;

        return cover == null ? -1 : cover.mood;
    }

    /** UVs of a centred window into a picture, zoomed by {@code zoom} and nudged by {@code panX/panY} (fractions). */
    private static float[] window(float zoom, float panX, float panY)
    {
        float half = 0.5F / Math.max(1F, zoom);
        float cx = Math.max(half, Math.min(1F - half, 0.5F + panX));
        float cy = Math.max(half, Math.min(1F - half, 0.5F + panY));

        return new float[] {cx - half, cy - half, cx + half, cy + half};
    }

    private void picturePoster(Batcher2D b, String id, FilmMeta meta, FilmThumbnails.Thumb cover, float x, float y, float w, float h, float r, float hover, boolean big)
    {
        UIContext context = this.getContext();
        float hue = hueOf(id);
        float mx = context.mouseX, my = context.mouseY;
        boolean inside = mx >= x && mx < x + w && my >= y && my < y + h && !UIOverlay.has(context) && !context.hasContextMenu();
        float fx = Gfx.clamp01((mx - x) / w);
        float fy = Gfx.clamp01((my - y) / h);
        /* The card and the inspector can show the same film: each keeps its own motion */
        String owner = id + (big ? "#big" : "#card");
        long now = System.currentTimeMillis();

        /* Tilt: the picture slides against the mouse like a window onto a deeper scene, eased */
        float tilt = this.anim("tilt:" + owner, inside ? 1F : 0F, 7F);
        float tx = this.anim("tx:" + owner, inside ? fx - 0.5F : 0F, 6F);
        float ty = this.anim("ty:" + owner, inside ? fy - 0.5F : 0F, 6F);
        /* The big poster holds still (only its light moves); cards push in and slide */
        float parallaxX = big ? 0F : -tx * 0.07F;
        float parallaxY = big ? 0F : -ty * 0.06F;
        float zoom = big ? 1F : 1.02F + hover * 0.08F + tilt * 0.03F;
        float[] uv = window(zoom, parallaxX, parallaxY);

        Gfx.imageRound(b, cover.texture, x, y, w, h, r, uv[0], uv[1], uv[2], uv[3], c(0xffffffff));

        int[] slots = RabbitSettings.isOn(RabbitSettings.filmThumbnailHover) ? this.thumbs.frameSlots(id) : new int[0];

        /* Hover frames: scrub with the mouse; cards also play on by themselves once it rests */
        boolean scrubbing = inside && slots.length >= 2;
        float scrub = this.anim("scrub:" + owner, scrubbing ? 1F : 0F, scrubbing ? 9F : 6F);

        if (scrubbing)
        {
            if (!owner.equals(this.scrubId))
            {
                this.scrubId = owner;
                this.scrubMouse = mx;
                this.scrubMovedAt = now;
                this.scrubSlot = -1;
                this.scrubPrev = -1;
            }

            if (Math.abs(mx - this.scrubMouse) > 0.5F)
            {
                this.scrubMouse = mx;
                this.scrubMovedAt = now;
            }

            int byMouse = Math.min(slots.length - 1, (int) (fx * slots.length));
            float idle = (now - this.scrubMovedAt) / 1000F - 0.9F;
            int index = idle <= 0 || big ? byMouse : (byMouse + 1 + (int) (idle / 0.85F)) % slots.length;
            int slot = slots[index];

            if (slot != this.scrubSlot)
            {
                this.scrubPrev = this.scrubSlot;
                this.scrubSlot = slot;
                this.scrubChangedAt = now;
            }
        }
        else if (owner.equals(this.scrubId) && scrub < 0.01F)
        {
            this.scrubId = null;
        }

        if (scrub > 0.01F && owner.equals(this.scrubId) && this.scrubSlot >= 0)
        {
            float cross = Gfx.easeOut((now - this.scrubChangedAt) / 160F);
            FilmThumbnails.Thumb previous = this.scrubPrev >= 0 ? this.thumbs.frame(id, this.scrubPrev) : null;
            FilmThumbnails.Thumb current = this.thumbs.frame(id, this.scrubSlot);
            float[] su = big ? uv : window(1.04F + tilt * 0.04F, parallaxX, parallaxY);

            if (previous != null && cross < 1F)
            {
                Gfx.imageRound(b, previous.texture, x, y, w, h, r, su[0], su[1], su[2], su[3], c(Gfx.alpha(0xffffff, scrub)));
            }

            if (current != null)
            {
                Gfx.imageRound(b, current.texture, x, y, w, h, r, su[0], su[1], su[2], su[3], c(Gfx.alpha(0xffffff, scrub * (previous == null ? 1F : cross))));
            }
        }

        /* Shade the bottom (and a little the top) so the badges read on any picture */
        Gfx.roundRect(b, x, y + h * 0.5F, w, h * 0.5F, r, c(Gfx.alpha(0x000000, 0F)), c(Gfx.alpha(0x000000, 0F)), c(Gfx.alpha(0x000000, big ? 0.42F : 0.5F)), c(Gfx.alpha(0x000000, big ? 0.42F : 0.5F)));
        Gfx.roundRect(b, x, y, w, h * 0.3F, r, c(Gfx.alpha(0x000000, 0.22F)), c(Gfx.alpha(0x000000, 0.22F)), c(Gfx.alpha(0x000000, 0F)), c(Gfx.alpha(0x000000, 0F)));

        b.clip((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w), (int) Math.ceil(h), context);

        /* Glare: a soft light that follows the mouse across the glass */
        if (tilt > 0.01F)
        {
            float gx = x + (0.5F + tx) * w, gy = y + (0.5F + ty) * h;

            Gfx.radial(b, gx, gy, Math.max(w, h) * 0.6F, c(Gfx.alpha(0xffffff, 0.16F * tilt)), c(Gfx.alpha(0xffffff, 0F)));
        }

        /* Light passing over it: always on the big poster, while hovered on cards */
        float sweepAlpha = big ? 0.1F : 0.08F * hover;

        if (sweepAlpha > 0.005F)
        {
            float period = big ? 7F : 4.5F;
            float phase = ((this.time + hue * period) % period) / period;
            float sweepW = w * 0.4F;

            Gfx.sweep(b, x - sweepW + phase * (w + sweepW * 2) * 1.5F, y, y + h, sweepW, h * 0.5F, c(Gfx.alpha(0xffffff, 0F)), c(Gfx.alpha(0xffffff, sweepAlpha * (1F - scrub))));
        }

        float pad = big ? 10 : 6;

        if (scrub > 0.01F && owner.equals(this.scrubId))
        {
            /* Where in the film the frame is: one tick per frame along the bottom */
            float gap = 2;
            float segW = (w - pad * 2 - gap * (slots.length - 1)) / slots.length;
            float sy = y + h - (big ? 7 : 5);

            for (int i = 0; i < slots.length; i++)
            {
                boolean on = slots[i] == this.scrubSlot;
                float sx = x + pad + i * (segW + gap);

                Gfx.roundRect(b, sx, sy - (on ? 0.5F : 0F), segW, on ? 2.5F : 1.5F, 1F, c(Gfx.alpha(0xffffff, scrub * (on ? 0.95F : 0.32F))));
            }
        }
        else if (meta != null && meta.duration > 0 && meta.clips.length > 0 && hover > 0.01F)
        {
            /* The camera timeline, only while hovered, a thin glass strip */
            float lx = x + pad;
            float lw = w - pad * 2;
            float lh = 6;
            float ly = y + h - lh - 5;
            int layers = Math.min(meta.layers, 2);
            float layerH = lh / layers;
            float alpha = hover * (1F - scrub);

            Gfx.roundRect(b, lx - 2, ly - 2, lw + 4, lh + 4, 3, c(Gfx.alpha(0x000000, 0.3F * alpha)));

            for (int i = 0; i < meta.clips.length / 4; i++)
            {
                int layer = meta.clips[i * 4 + 2];

                if (layer >= layers)
                {
                    continue;
                }

                float cx1 = lx + lw * meta.clips[i * 4] / (float) meta.duration;
                float cx2 = lx + lw * Math.min(meta.duration, meta.clips[i * 4] + meta.clips[i * 4 + 1]) / (float) meta.duration;

                Gfx.roundRect(b, cx1, ly + layer * layerH + 0.5F, Math.max(1.5F, cx2 - cx1 - 0.6F), layerH - 1F, Math.min(1.5F, layerH / 2F), c(Gfx.alpha(Gfx.lerp(0xff000000 | meta.clips[i * 4 + 3], 0xffffffff, 0.3F), 0.85F * alpha)));
            }

            float p = (this.time * 0.18F + hue) % 1F;

            b.box(lx + lw * p, ly - 2, lx + lw * p + 1, ly + lh + 2, c(Gfx.alpha(0xffffff, 0.85F * alpha)));
        }

        b.unclip(context);

        /* Play mark: only when reaching for it */
        float reveal = hover * (1F - scrub * 0.85F);

        if (reveal > 0.01F)
        {
            float pr = (big ? 15 : 9) + hover * 2;
            float pcx = x + w / 2F, pcy = y + h / 2F - (big ? 4 : 2);

            Gfx.radial(b, pcx, pcy, pr * 2.2F, c(Gfx.alpha(0x000000, 0.3F * reveal)), c(Gfx.alpha(0x000000, 0F)));
            Gfx.circle(b, pcx, pcy, pr, c(Gfx.alpha(0xffffff, 0.2F * reveal)));
            Gfx.circleRing(b, pcx, pcy, pr, 1F, c(Gfx.alpha(0xffffff, 0.7F * reveal)));
            Gfx.icon(b, Icons.PLAY, pcx + 0.5F, pcy, big ? 1.2F : 0.8F, c(Gfx.alpha(0xffffff, 0.95F * reveal)));
        }

        /* Duration badge */
        if (meta != null && meta.duration > 0)
        {
            String text = formatTicks(meta.duration);
            float tw = Gfx.width(b, text) + 8;
            float bx = x + w - tw - (big ? 7 : 4);
            float by = y + (big ? 7 : 4);

            Gfx.roundRect(b, bx, by, tw, 11, 4, c(Gfx.alpha(0x000000, 0.5F)));
            Gfx.text(b, text, bx + 4, by + 2, c(0xfff2f4f8));
        }

        /* A fresh cover lands with a short glint */
        Long popped = this.pops.get("cover:" + id);

        if (popped != null)
        {
            float pop = (System.currentTimeMillis() - popped) / 600F;

            if (pop < 1F)
            {
                Gfx.roundOutline(b, x - 2 * pop, y - 2 * pop, w + 4 * pop, h + 4 * pop, r + 2 * pop, 1.5F, c(Gfx.alpha(0xffffff, 0.7F * (1F - pop))));
            }
            else
            {
                this.pops.remove("cover:" + id);
            }
        }

        Gfx.roundOutline(b, x, y, w, h, r, 1F, c(Gfx.alpha(0xffffff, 0.12F + 0.1F * hover)));
    }

    private void generatedPoster(Batcher2D b, String id, FilmMeta meta, float x, float y, float w, float h, float r, float hover, boolean big)
    {
        float hue = hueOf(id);
        int tl = Gfx.hsv(hue, 0.55F, 0.62F);
        int tr = Gfx.hsv(hue + 0.08F, 0.65F, 0.40F);
        int bl = Gfx.hsv(hue - 0.06F, 0.7F, 0.28F);
        int br = Gfx.hsv(hue + 0.14F, 0.75F, 0.14F);

        Gfx.roundRect(b, x, y, w, h, r, c(tl), c(tr), c(bl), c(br));

        UIContext context = this.getContext();

        b.clip((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w), (int) Math.ceil(h), context);

        float t = this.time + hue * 10F;
        float glowR = Math.max(w, h) * 0.6F;

        Gfx.radial(b, x + w * (0.3F + 0.08F * (float) Math.sin(t * 0.5F)), y + h * (0.32F + 0.08F * (float) Math.cos(t * 0.4F)), glowR, c(Gfx.alpha(Gfx.hsv(hue - 0.03F, 0.35F, 1F), 0.45F)), c(Gfx.alpha(Gfx.hsv(hue, 0.35F, 1F), 0F)));
        Gfx.radial(b, x + w * 0.9F, y + h * 1.05F, glowR * 0.8F, c(Gfx.accent(0.35F)), c(Gfx.accent(0F)));

        /* Film strip edges */
        float strip = big ? 7 : 5;
        float hole = big ? 4 : 3;

        b.box(x, y, x + w, y + strip, c(Gfx.alpha(0x000000, 0.32F)));
        b.box(x, y + h - strip, x + w, y + h, c(Gfx.alpha(0x000000, 0.32F)));

        float step = big ? 11 : 8;
        float offset = (this.time * (big ? 6 : 0) + hover * 6) % step;

        for (float hx = x + 3 - offset; hx < x + w; hx += step)
        {
            Gfx.roundRect(b, hx, y + (strip - hole) / 2F, hole + 1, hole, 1F, c(Gfx.alpha(0xffffff, 0.22F)));
            Gfx.roundRect(b, hx, y + h - strip + (strip - hole) / 2F, hole + 1, hole, 1F, c(Gfx.alpha(0xffffff, 0.22F)));
        }

        /* Camera timeline across the bottom */
        if (meta != null && meta.duration > 0 && meta.clips.length > 0)
        {
            float lx = x + (big ? 10 : 6);
            float lw = w - (big ? 20 : 12);
            float lh = big ? 16 : 9;
            float ly = y + h - strip - lh - (big ? 6 : 3);
            int layers = Math.min(meta.layers, big ? 4 : 3);
            float layerH = lh / layers;

            Gfx.roundRect(b, lx - 2, ly - 2, lw + 4, lh + 4, 3, c(Gfx.alpha(0x000000, 0.28F)));

            for (int i = 0; i < meta.clips.length / 4; i++)
            {
                int layer = meta.clips[i * 4 + 2];

                if (layer >= layers)
                {
                    continue;
                }

                float cx1 = lx + lw * meta.clips[i * 4] / (float) meta.duration;
                float cx2 = lx + lw * Math.min(meta.duration, meta.clips[i * 4] + meta.clips[i * 4 + 1]) / (float) meta.duration;
                float cy1 = ly + layer * layerH;

                Gfx.roundRect(b, cx1, cy1 + 0.5F, Math.max(1.5F, cx2 - cx1 - 0.6F), layerH - 1F, Math.min(1.5F, layerH / 2F), c(Gfx.alpha(Gfx.lerp(0xff000000 | meta.clips[i * 4 + 3], 0xffffffff, 0.25F), 0.9F)));
            }

            /* A playhead that runs along while hovered or selected */
            if (big || hover > 0.01F)
            {
                float p = (this.time * 0.18F + hue) % 1F;
                float px = lx + lw * p;

                b.box(px, ly - 2, px + 1, ly + lh + 2, c(Gfx.alpha(0xffffff, (big ? 0.85F : 0.85F * hover))));
            }
        }

        /* Light sweep */
        float period = 6F;
        float phase = ((this.time + hue * period) % period) / period;
        float sweepW = w * 0.35F;
        float sx = x - sweepW + phase * (w + sweepW * 2) * 1.6F;

        Gfx.sweep(b, sx, y, y + h, sweepW, h * 0.5F, c(Gfx.alpha(0xffffff, 0F)), c(Gfx.alpha(0xffffff, big ? 0.10F : 0.08F)));

        b.unclip(context);

        /* Play mark in the middle */
        float pr = (big ? 15 : 9) + hover * 2;
        float pcx = x + w / 2F, pcy = y + h / 2F - (big ? 4 : 2);

        Gfx.radial(b, pcx, pcy, pr * 2.2F, c(Gfx.alpha(0x000000, 0.28F)), c(Gfx.alpha(0x000000, 0F)));
        Gfx.circle(b, pcx, pcy, pr, c(Gfx.alpha(0xffffff, 0.16F + 0.12F * hover)));
        Gfx.circleRing(b, pcx, pcy, pr, 1F, c(Gfx.alpha(0xffffff, 0.45F + 0.3F * hover)));
        Gfx.icon(b, Icons.PLAY, pcx + 0.5F, pcy, big ? 1.2F : 0.8F, c(Gfx.alpha(0xffffff, 0.95F)));

        /* Duration badge */
        if (meta != null && meta.duration > 0)
        {
            String text = formatTicks(meta.duration);
            float tw = Gfx.width(b, text) + 8;
            float bx = x + w - tw - (big ? 7 : 4);
            float by = y + strip + 3;

            Gfx.roundRect(b, bx, by, tw, 11, 4, c(Gfx.alpha(0x000000, 0.55F)));
            Gfx.text(b, text, bx + 4, by + 2, c(0xfff2f4f8));
        }

        Gfx.roundOutline(b, x, y, w, h, r, 1F, c(Gfx.alpha(0xffffff, 0.10F)));
    }

    /* Inspector */

    private void renderDetail(UIContext context, Batcher2D b)
    {
        float x = this.dx, y = this.dy, w = this.dw, h = this.dh;

        this.card(b, x, y, w, h, 10, 0.82F);

        String id = this.selected;

        if (id != null && this.filmSet.contains(id))
        {
            this.detailAmbience(b, id, x, y, w, h);
        }

        if (id == null || !this.filmSet.contains(id))
        {
            this.renderNoSelection(b, x, y, w, h);

            return;
        }

        FilmMeta meta = this.store.get(id);
        float swap = Gfx.easeOut((System.currentTimeMillis() - this.selectedAt) / 320F);
        float keep = this.fade;

        this.fade = keep * swap;

        if (this.wide)
        {
            this.renderDetailTall(context, b, id, meta, x, y + (1F - swap) * 8F, w, h);
        }
        else
        {
            this.renderDetailWide(context, b, id, meta, x + (1F - swap) * 8F, y, w, h);
        }

        this.fade = keep;
    }

    private void renderDetailTall(UIContext context, Batcher2D b, String id, FilmMeta meta, float x, float y, float w, float h)
    {
        float pad = 10;
        float posterW = w - pad * 2;
        float posterH = posterW * 9F / 16F;
        float buttonsH = 22 + 6 + 20 + pad;
        float bodyTop = y + pad;
        float bodyBottom = y + h - buttonsH - 4;
        float hvPoster = this.hover("poster");

        b.clip((int) x, (int) bodyTop, (int) w, (int) (bodyBottom - bodyTop), context);

        float maxScroll = Math.max(0, this.detailContentHeight(b, id, meta, w, posterH) - (bodyBottom - bodyTop));

        this.detailScrollTarget = Math.max(0, Math.min(maxScroll, this.detailScrollTarget));
        this.detailScroll = Gfx.approach(this.detailScroll, this.detailScrollTarget, 18F, this.dt);

        float cy = bodyTop - this.detailScroll;
        Hit poster = this.hitClipped("poster", x + pad, cy, posterW, posterH, bodyTop, bodyBottom);

        if (poster != null)
        {
            poster.dbl = () -> this.open(id);
            poster.click = () -> {};
            poster.tooltip = tr("open_hint");
        }

        this.posterGlow(b, id, x + pad, cy, posterW, posterH);
        this.poster(b, id, meta, x + pad, cy, posterW, posterH, 8, hvPoster, true);
        this.presenceBadge(b, id, x + pad, cy, true, bodyTop, bodyBottom);
        this.coverButton(b, id, x + pad, cy, posterW, posterH, hvPoster, bodyTop, bodyBottom);
        cy += posterH + 10;

        cy = this.detailTitle(b, id, x + pad, cy, w - pad * 2, bodyTop, bodyBottom);
        cy = this.detailLabels(b, id, x + pad, cy, w - pad * 2, bodyTop, bodyBottom);
        cy = this.detailStats(b, meta, x + pad, cy, w - pad * 2, 1);
        cy = this.detailDescription(b, meta, x + pad, cy + 6, w - pad * 2);
        this.detailTimeline(b, meta, x + pad, cy + 8, w - pad * 2);

        b.unclip(context);

        if (this.detailScroll < maxScroll - 1)
        {
            b.gradientVBox(x + 1, bodyBottom - 12, x + w - 1, bodyBottom, c(Gfx.base(0F)), c(Gfx.base(0.85F)));
        }

        this.detailButtons(b, id, x + pad, y + h - buttonsH, w - pad * 2);
    }

    private void renderDetailWide(UIContext context, Batcher2D b, String id, FilmMeta meta, float x, float y, float w, float h)
    {
        float pad = 10;
        float posterH = h - pad * 2;
        float posterW = Math.min(posterH * 16F / 9F, w * 0.36F);

        posterH = Math.min(posterH, posterW * 9F / 16F + 30);

        float hvPoster = this.hover("poster");
        Hit poster = this.hit("poster", x + pad, y + pad, posterW, posterH);

        poster.dbl = () -> this.open(id);
        poster.click = () -> {};
        poster.tooltip = tr("open_hint");

        this.posterGlow(b, id, x + pad, y + pad, posterW, posterH);
        this.poster(b, id, meta, x + pad, y + pad, posterW, posterH, 8, hvPoster, true);
        this.presenceBadge(b, id, x + pad, y + pad, true, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        this.coverButton(b, id, x + pad, y + pad, posterW, posterH, hvPoster, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        float ix = x + pad + posterW + 14;
        float iw = x + w - pad - ix;

        /* Room enough: the actions get a column of their own on the right */
        if (iw > 380)
        {
            float aw = 118;
            float ax = x + w - pad - aw;

            b.box(ax - 10, y + pad, ax - 9, y + h - pad, c(Gfx.wash(0.07F)));
            this.detailActionsColumn(b, id, ax, y + pad, aw);
            iw = ax - 20 - ix;

            float bodyTop = y + pad;
            float bodyBottom = y + h - pad;

            b.clip((int) ix - 2, (int) bodyTop, (int) iw + 4, (int) (bodyBottom - bodyTop), context);

            float maxScroll = Math.max(0, this.detailContentHeight(b, id, meta, iw + 20, 0) - (bodyBottom - bodyTop));

            this.detailScrollTarget = Math.max(0, Math.min(maxScroll, this.detailScrollTarget));
            this.detailScroll = Gfx.approach(this.detailScroll, this.detailScrollTarget, 18F, this.dt);

            float cy = bodyTop - this.detailScroll;

            cy = this.detailTitle(b, id, ix, cy, iw, bodyTop, bodyBottom);
            cy = this.detailLabels(b, id, ix, cy, iw, bodyTop, bodyBottom);
            cy = this.detailStats(b, meta, ix, cy, iw, iw > 260 ? 2 : 1);
            cy = this.detailDescription(b, meta, ix, cy + 6, iw);
            this.detailTimeline(b, meta, ix, cy + 8, iw);

            b.unclip(context);

            return;
        }

        float buttonsH = 22 + 6 + 20;
        float bodyTop = y + pad;
        float bodyBottom = y + h - pad - buttonsH - 4;

        b.clip((int) ix - 2, (int) bodyTop, (int) iw + 4, (int) (bodyBottom - bodyTop), context);

        float maxScroll = Math.max(0, this.detailContentHeight(b, id, meta, iw + 20, 0) - (bodyBottom - bodyTop));

        this.detailScrollTarget = Math.max(0, Math.min(maxScroll, this.detailScrollTarget));
        this.detailScroll = Gfx.approach(this.detailScroll, this.detailScrollTarget, 18F, this.dt);

        float cy = bodyTop - this.detailScroll;

        cy = this.detailTitle(b, id, ix, cy, iw, bodyTop, bodyBottom);
        cy = this.detailLabels(b, id, ix, cy, iw, bodyTop, bodyBottom);
        cy = this.detailStats(b, meta, ix, cy, iw, iw > 300 ? 2 : 1);
        this.detailDescription(b, meta, ix, cy + 6, iw);

        b.unclip(context);

        this.detailButtons(b, id, ix, y + h - pad - buttonsH, iw);
    }

    private float detailContentHeight(Batcher2D b, String id, FilmMeta meta, float w, float posterH)
    {
        float inner = w - 20;
        int lines = meta == null || meta.description.isEmpty() ? 1 : Math.min(6, b.getFont().wrap(meta.description, (int) inner).size());

        return (posterH > 0 ? posterH + 10 : 0) + 34 + (FilmThumbnails.enabled() ? 20 : 0) + 6 * 18 + 6 + 14 + lines * 11 + (posterH > 0 ? 50 : 0) + 6;
    }

    private float detailTitle(Batcher2D b, String id, float x, float y, float w, float clipTop, float clipBottom)
    {
        float scale = 1.35F;
        String name = baseName(id);
        int maxW = (int) ((w - 24) / scale);

        Gfx.text(b, Gfx.fit(b, name, maxW), x, y, scale, c(Gfx.ink(1F)));
        this.star(b, id, x + w - 7, y + 6, this.store.isFavorite(id), 1F, clipTop, clipBottom);

        String parent = parentOf(id);
        String path = parent.isEmpty() ? tr("root") : parent.substring(0, parent.length() - 1);

        Gfx.icon(b, Icons.FOLDER, x + 5, y + 21, 0.6F, c(Gfx.inkMuted(1F)));
        Gfx.text(b, Gfx.fit(b, path, (int) w - 14), x + 12, y + 17, c(Gfx.inkMuted(1F)));

        return y + 32;
    }

    /** The selected film's colour, easing from one film to the next (cover mood, else its hue). */
    private int selectedGlow()
    {
        int target = 0xff000000 | (this.selected == null ? BBSSettings.primaryColor.get() : (this.moodOf(this.selected) >= 0 ? this.moodOf(this.selected) : Gfx.hsv(hueOf(this.selected), 0.65F, 0.95F)));

        this.glowColor = this.glowColor == -1 ? target : Gfx.lerp(this.glowColor, target, 1F - (float) Math.exp(-3.5F * this.dt));

        return this.glowColor & 0xffffff;
    }

    /** The big poster's light: a breathing halo and two slow blooms in the film's colour behind it. */
    private void posterGlow(Batcher2D b, String id, float x, float y, float w, float h)
    {
        int color = this.selectedGlow();
        float t = this.time;
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 0.9F);
        float cx = x + w / 2F, cy = y + h / 2F;
        float strength = Gfx.light() ? 0.7F : 1F;

        Gfx.radial(b, cx + w * 0.2F * (float) Math.sin(t * 0.31F), cy + h * 0.12F * (float) Math.cos(t * 0.27F), w * 0.78F, c(Gfx.alpha(color, (0.2F + 0.08F * breath) * strength)), c(Gfx.alpha(color, 0F)));
        Gfx.radial(b, cx - w * 0.22F * (float) Math.cos(t * 0.23F), cy + h * 0.18F * (float) Math.sin(t * 0.35F), w * 0.6F, c(Gfx.alpha(color, (0.14F + 0.06F * breath) * strength)), c(Gfx.alpha(color, 0F)));
        Gfx.shadow(b, x, y + 3, w, h, 8, 12 + 5 * breath, c(Gfx.alpha(color, (0.5F + 0.15F * breath) * strength)));
    }

    /** The detail card's top: the selected cover, out of focus and drifting, fading into the card. */
    private void detailAmbience(Batcher2D b, String id, float x, float y, float w, float h)
    {
        FilmThumbnails.Thumb cover = FilmThumbnails.enabled() ? this.thumbs.cover(id) : null;
        float shown = this.anim("dambient:" + id, cover != null ? 1F : 0F, 4F);

        if (cover == null || shown < 0.01F)
        {
            return;
        }

        float t = this.time;
        float[] uv = window(1.35F, 0.08F * (float) Math.sin(t * 0.07F), 0.06F * (float) Math.cos(t * 0.05F));
        float alpha = (Gfx.light() ? 0.32F : 0.45F) * shown;

        Gfx.imageRound(b, cover.texture, x + 1, y + 1, w - 2, h * 0.7F, 9, uv[0], uv[1], uv[2], uv[3], c(Gfx.alpha(0xffffff, alpha)), c(Gfx.alpha(0xffffff, 0F)), 4.5F);
    }

    /** "Change cover" glass pill on the big poster, shown while it is hovered. */
    private void coverButton(Batcher2D b, String id, float x, float y, float w, float h, float posterHover, float clipTop, float clipBottom)
    {
        if (!FilmThumbnails.enabled())
        {
            return;
        }

        String key = "covercta:" + id;
        float hv = this.hover(key);
        float reveal = this.anim("covercta", Math.max(posterHover, hv) > 0.3F ? 1F : 0F, 10F);

        if (reveal < 0.01F)
        {
            return;
        }

        String label = tr("cover.change");
        float bw = Gfx.width(b, label) + 26;
        float bh = 16;
        float bx = x + 8;
        float by = y + h - bh - 24 + (1F - reveal) * 4F;
        Hit hit = this.hitClipped(key, bx, by, bw, bh, clipTop, clipBottom);

        if (hit != null)
        {
            hit.click = () -> this.coverMenu(id);
            hit.tooltip = tr("cover.drop_hint");
        }

        float keep = this.fade;

        this.fade = keep * reveal;
        Gfx.shadow(b, bx, by + 1, bw, bh, bh / 2F, 4 + 3 * hv, c(Gfx.alpha(0x000000, 0.35F)));
        Gfx.roundRect(b, bx, by, bw, bh, bh / 2F, c(Gfx.alpha(Gfx.lerp(0xff101216, Gfx.accent(), 0.25F * hv), 0.72F + 0.15F * hv)));
        Gfx.roundOutline(b, bx, by, bw, bh, bh / 2F, 1F, c(Gfx.alpha(0xffffff, 0.22F + 0.3F * hv)));
        Gfx.icon(b, Icons.IMAGE, bx + 10, by + bh / 2F, 0.75F, c(0xfff2f4f8));
        Gfx.text(b, label, bx + 19, by + 4, c(0xfff2f4f8));
        this.fade = keep;
    }

    /** Colour label picker: a row of dots, the film's own ringed; clicking it again clears it. */
    private float detailLabels(Batcher2D b, String id, float x, float y, float w, float clipTop, float clipBottom)
    {
        if (!FilmThumbnails.enabled())
        {
            return y;
        }

        int current = this.thumbs.label(id);
        float step = 13F;
        float right = x + w - 5;

        Gfx.text(b, tr("label"), x, y + 4, c(Gfx.inkMuted(1F)));

        for (int i = LibraryEntry.LABELS; i >= 1; i--)
        {
            final int value = i;
            float cx = right - (LibraryEntry.LABELS - i) * step;
            float cy = y + 8;
            String key = "dlabel:" + i;
            Hit hit = this.hitClipped(key, cx - 6.5F, cy - 6.5F, 13, 13, clipTop, clipBottom);

            if (hit != null)
            {
                hit.click = () -> this.setLabel(id, value);
                hit.tooltip = tr("label." + i);
            }

            float hv = this.hover(key);
            float on = this.anim("dlabel-on:" + i, current == i ? 1F : 0F, 14F);
            int color = 0xff000000 | FilmThumbnails.labelColor(i);
            float radius = 3.6F + 0.9F * hv + 0.5F * on;

            if (on > 0.01F)
            {
                Gfx.radial(b, cx, cy, radius + 6F, c(Gfx.alpha(color, 0.3F * on)), c(Gfx.alpha(color, 0F)));
                Gfx.circleRing(b, cx, cy, radius + 2.6F, 1.2F, c(Gfx.alpha(color, 0.95F * on)));
            }

            Gfx.circle(b, cx, cy, radius, c(current != 0 && current != i ? Gfx.mulA(color, 0.45F + 0.55F * hv) : color));
        }

        return y + 20;
    }

    private float detailStats(Batcher2D b, FilmMeta meta, float x, float y, float w, int columns)
    {
        String[][] rows = {
            {tr("stat.duration"), meta == null ? null : formatTicks(meta.duration)},
            {tr("stat.modified"), meta == null ? null : formatDate(meta.modified)},
            {tr("stat.created"), meta == null ? null : (meta.created > 0 ? formatDate(meta.created) : "—")},
            {tr("stat.replays"), meta == null ? null : String.valueOf(meta.replays)},
            {tr("stat.clips"), meta == null ? null : String.valueOf(meta.cameraClips)},
            {tr("stat.time_spent"), meta == null ? null : formatSpent(meta.timeSpent)}
        };

        float colW = (w - (columns - 1) * 12) / columns;
        int perColumn = (rows.length + columns - 1) / columns;

        for (int i = 0; i < rows.length; i++)
        {
            int col = i / perColumn;
            int row = i % perColumn;
            float rx = x + col * (colW + 12);
            float ry = y + row * 18;

            b.box(rx, ry, rx + colW, ry + 1, c(Gfx.wash(0.07F)));
            Gfx.text(b, rows[i][0], rx, ry + 6, c(Gfx.inkMuted(1F)));

            if (rows[i][1] == null)
            {
                this.shimmer(b, rx + colW - 40, ry + 7, 40, 6, i);
            }
            else
            {
                String value = Gfx.fit(b, rows[i][1], (int) (colW - Gfx.width(b, rows[i][0]) - 8));

                Gfx.text(b, value, rx + colW - Gfx.width(b, value), ry + 6, c(Gfx.ink(1F)));
            }
        }

        b.box(x, y + perColumn * 18, x + w, y + perColumn * 18 + 1, c(Gfx.wash(0.07F)));

        return y + perColumn * 18 + 2;
    }

    private float detailDescription(Batcher2D b, FilmMeta meta, float x, float y, float w)
    {
        Gfx.text(b, tr("description"), x, y + 2, c(Gfx.inkMuted(1F)));
        y += 14;

        if (meta == null || meta.description.isEmpty())
        {
            Gfx.text(b, tr("no_description"), x, y, c(Gfx.inkMuted(0.7F)));

            return y + 11;
        }

        List<String> lines = b.getFont().wrap(meta.description, (int) w);
        int max = Math.min(6, lines.size());

        for (int i = 0; i < max; i++)
        {
            String line = lines.get(i);

            if (i == max - 1 && lines.size() > max)
            {
                line = Gfx.fit(b, line + "…", (int) w);
            }

            Gfx.text(b, line, x, y + i * 11, c(Gfx.inkSoft(1F)));
        }

        return y + max * 11;
    }

    private void detailTimeline(Batcher2D b, FilmMeta meta, float x, float y, float w)
    {
        if (meta == null || meta.duration <= 0 || meta.clips.length == 0)
        {
            return;
        }

        Gfx.text(b, tr("timeline"), x, y + 2, c(Gfx.inkMuted(1F)));
        y += 14;

        int layers = Math.min(meta.layers, 4);
        float layerH = 7;
        float th = layers * layerH + 4;

        Gfx.roundRect(b, x, y, w, th, 4, c(Gfx.deep(0.8F)));

        for (int i = 0; i < meta.clips.length / 4; i++)
        {
            int layer = meta.clips[i * 4 + 2];

            if (layer >= layers)
            {
                continue;
            }

            float x1 = x + 2 + (w - 4) * meta.clips[i * 4] / (float) meta.duration;
            float x2 = x + 2 + (w - 4) * Math.min(meta.duration, meta.clips[i * 4] + meta.clips[i * 4 + 1]) / (float) meta.duration;
            float y1 = y + 2 + layer * layerH;
            int color = 0xff000000 | meta.clips[i * 4 + 3];

            Gfx.roundRectV(b, x1, y1 + 0.5F, Math.max(1.5F, x2 - x1 - 0.8F), layerH - 1, 2, c(Gfx.lerp(color, 0xffffffff, 0.2F)), c(color));
        }

        float p = (this.time * 0.12F) % 1F;
        float px = x + 2 + (w - 4) * p;

        b.box(px, y - 1, px + 1, y + th + 1, c(Gfx.accent()));
        Gfx.circle(b, px + 0.5F, y - 1, 2, c(Gfx.accent()));
    }

    private void detailButtons(Batcher2D b, String id, float x, float y, float w)
    {
        /* Open: the primary action, the arrow nudges forward on hover */
        float h = this.hover("open");
        String label = tr("open");

        this.hit("open", x, y, w, 22).click = () -> this.open(id);
        Gfx.shadow(b, x, y + 2, w, 22, 8, 6 + 6 * h, c(Gfx.accent(0.25F + 0.3F * h)));
        Gfx.roundRect(b, x, y, w, 22, 8, c(Gfx.lerp(Gfx.accent(), 0xffffffff, 0.16F + 0.08F * h)), c(Gfx.accent()), c(Gfx.accent()), c(Gfx.lerp(Gfx.accent(), 0xff000000, 0.12F)));
        Gfx.roundRectV(b, x + 1, y + 1, w - 2, 10, 7, c(Gfx.alpha(0xffffff, 0.15F)), c(Gfx.alpha(0xffffff, 0F)));

        float lw = Gfx.width(b, label);
        float tx = x + (w - lw - 14) / 2F;

        Gfx.text(b, label, tx, y + 7, c(Gfx.onAccent()));
        Gfx.icon(b, Icons.ARROW_RIGHT, tx + lw + 9 + h * 3, y + 11, 0.8F, c(Gfx.onAccent()));

        /* Secondary actions */
        float by = y + 28;
        float bw = (w - 12) / 3F;

        this.secondary(b, "rename", Icons.EDIT, tr("rename"), x, by, bw, () -> this.rename(id));
        this.secondary(b, "duplicate", Icons.DUPE, tr("duplicate"), x + bw + 6, by, bw, () -> this.duplicate(id));
        this.secondary(b, "more", Icons.MORE, tr("more"), x + (bw + 6) * 2, by, bw, () -> this.filmMenu(id));
    }

    private void detailActionsColumn(Batcher2D b, String id, float x, float y, float w)
    {
        float h = this.hover("open");
        String label = tr("open");

        this.hit("open", x, y, w, 24).click = () -> this.open(id);
        Gfx.shadow(b, x, y + 2, w, 24, 8, 6 + 6 * h, c(Gfx.accent(0.25F + 0.3F * h)));
        Gfx.roundRect(b, x, y, w, 24, 8, c(Gfx.lerp(Gfx.accent(), 0xffffffff, 0.16F + 0.08F * h)), c(Gfx.accent()), c(Gfx.accent()), c(Gfx.lerp(Gfx.accent(), 0xff000000, 0.12F)));
        Gfx.roundRectV(b, x + 1, y + 1, w - 2, 11, 7, c(Gfx.alpha(0xffffff, 0.15F)), c(Gfx.alpha(0xffffff, 0F)));

        float lw = Gfx.width(b, label);
        float tx = x + (w - lw - 14) / 2F;

        Gfx.text(b, label, tx, y + 8, c(Gfx.onAccent()));
        Gfx.icon(b, Icons.ARROW_RIGHT, tx + lw + 9 + h * 3, y + 12, 0.8F, c(Gfx.onAccent()));

        this.secondary(b, "rename", Icons.EDIT, tr("rename"), x, y + 32, w, () -> this.rename(id));
        this.secondary(b, "duplicate", Icons.DUPE, tr("duplicate"), x, y + 58, w, () -> this.duplicate(id));
        this.secondary(b, "more", Icons.MORE, tr("more"), x, y + 84, w, () -> this.filmMenu(id));
    }

    private void secondary(Batcher2D b, String key, Icon icon, String label, float x, float y, float w, Runnable action)
    {
        float h = this.hover(key);

        this.hit(key, x, y, w, 20).click = action;
        Gfx.roundRect(b, x, y - h, w, 20, 7, c(Gfx.lerp(Gfx.raised(0.9F), Gfx.lerp(Gfx.raised(1F), Gfx.accent(), 0.15F), h)));
        Gfx.roundOutline(b, x, y - h, w, 20, 7, 1F, c(Gfx.lerp(Gfx.wash(0.09F), Gfx.accent(0.7F), h)));

        String text = Gfx.fit(b, label, (int) (w - 24));
        float tw = Gfx.width(b, text);
        float content = 14 + tw;
        float cx = x + (w - content) / 2F;

        Gfx.icon(b, icon, cx + 5, y - h + 10, 0.75F, c(Gfx.lerp(Gfx.inkSoft(1F), Gfx.ink(1F), h)));
        Gfx.text(b, text, cx + 14, y - h + 6, c(Gfx.lerp(Gfx.inkSoft(1F), Gfx.ink(1F), h)));
    }

    private void renderNoSelection(Batcher2D b, float x, float y, float w, float h)
    {
        float cx = x + w / 2F, cy = y + h / 2F - 16;
        float bob = (float) Math.sin(this.time * 1.6F) * 2F;

        Gfx.radial(b, cx, cy + bob, 34, c(Gfx.accent(0.22F)), c(Gfx.accent(0F)));
        Gfx.roundRect(b, cx - 16, cy - 12 + bob, 32, 24, 6, c(Gfx.raised(1F)));
        Gfx.roundOutline(b, cx - 16, cy - 12 + bob, 32, 24, 6, 1F, c(Gfx.accent(0.6F)));
        Gfx.icon(b, Icons.FILM, cx, cy + bob, 1F, c(Gfx.accent()));

        String title = tr("no_selection");
        String sub = tr("no_selection_sub");

        Gfx.text(b, title, cx - Gfx.width(b, title) / 2F, cy + 22, c(Gfx.ink(0.9F)));
        Gfx.text(b, Gfx.fit(b, sub, (int) w - 20), cx - Math.min(w - 20, Gfx.width(b, sub)) / 2F, cy + 34, c(Gfx.inkMuted(1F)));
    }

    private void renderEmpty(Batcher2D b, float x, float y, float w, float h)
    {
        String title;
        String sub;
        Icon icon;
        boolean offerNew = false;

        if (!this.search.getText().trim().isEmpty())
        {
            title = tr("empty.search");
            sub = tr("empty.search_sub");
            icon = Icons.SEARCH;
        }
        else if (this.labelFilter != 0)
        {
            title = tr("empty.label", tr("label." + this.labelFilter));
            sub = tr("empty.label_sub");
            icon = Icons.COLOR;
        }
        else if (this.scope == SCOPE_FAVORITES)
        {
            title = tr("empty.favorites");
            sub = tr("empty.favorites_sub");
            icon = Icons.FAVORITE;
        }
        else if (this.scope == SCOPE_RECENT)
        {
            title = tr("empty.recent");
            sub = tr("empty.recent_sub");
            icon = Icons.TIME;
        }
        else if (this.scope == SCOPE_FOLDER)
        {
            title = tr("empty.folder");
            sub = tr("empty.folder_sub");
            icon = Icons.FOLDER;
            offerNew = true;
        }
        else
        {
            title = tr("empty.all");
            sub = tr("empty.all_sub");
            icon = Icons.FILM;
            offerNew = true;
        }

        float cx = x + w / 2F, cy = y + h / 2F - (offerNew ? 24 : 12);
        float bob = (float) Math.sin(this.time * 1.4F) * 2.5F;

        Gfx.radial(b, cx, cy + bob, 46, c(Gfx.accent(0.25F)), c(Gfx.accent(0F)));
        Gfx.circle(b, cx, cy + bob, 20, c(Gfx.raised(1F)));
        Gfx.circleRing(b, cx, cy + bob, 20, 1F, c(Gfx.accent(0.6F)));
        Gfx.icon(b, icon, cx, cy + bob, 1.25F, c(Gfx.accent()));
        Gfx.text(b, title, cx - Gfx.width(b, title) / 2F, cy + 30, c(Gfx.ink(1F)));
        Gfx.text(b, sub, cx - Gfx.width(b, sub) / 2F, cy + 43, c(Gfx.inkMuted(1F)));

        if (offerNew)
        {
            String label = tr("new_film");
            float bw = Gfx.width(b, label) + 34;
            float bx = cx - bw / 2F, by = cy + 60;
            float hv = this.hover("empty-new");

            this.hit("empty-new", bx, by, bw, 22).click = this::newFilm;
            Gfx.shadow(b, bx, by + 2, bw, 22, 8, 6 + 6 * hv, c(Gfx.accent(0.3F + 0.3F * hv)));
            Gfx.roundRect(b, bx, by - hv, bw, 22, 8, c(Gfx.lerp(Gfx.accent(), 0xffffffff, 0.15F + 0.1F * hv)));
            Gfx.icon(b, Icons.ADD, bx + 13, by - hv + 11, 1F, c(Gfx.onAccent()));
            Gfx.text(b, label, bx + 23, by - hv + 7, c(Gfx.onAccent()));
        }
    }

    private void renderLoading(Batcher2D b, float x, float y, float w, float h)
    {
        float cx = x + w / 2F, cy = y + h / 2F;

        for (int i = 0; i < 8; i++)
        {
            double a = this.time * 4 + i * Math.PI / 4;
            float alpha = (i + 1) / 8F;

            Gfx.circle(b, cx + (float) Math.cos(a) * 10, cy + (float) Math.sin(a) * 10, 2F, c(Gfx.accent(alpha)));
        }

        String text = tr("loading");

        Gfx.text(b, text, cx - Gfx.width(b, text) / 2F, cy + 20, c(Gfx.inkMuted(1F)));
    }

    private void renderTooltip(UIContext context, Batcher2D b, Hit under)
    {
        if (under == null || under.tooltip == null || System.currentTimeMillis() - this.hotAt < 450)
        {
            return;
        }

        float alpha = Gfx.easeOut((System.currentTimeMillis() - this.hotAt - 450) / 150F);
        String text = under.tooltip;
        float tw = Gfx.width(b, text) + 10;
        float tx = Math.min(context.mouseX + 8, this.area.ex() - tw - 4);
        float ty = context.mouseY + 14;

        Gfx.shadow(b, tx, ty, tw, 15, 5, 5, Gfx.alpha(0x000000, 0.3F * alpha));
        Gfx.roundRect(b, tx, ty, tw, 15, 5, Gfx.raised(0.97F * alpha));
        Gfx.roundOutline(b, tx, ty, tw, 15, 5, 1F, Gfx.wash(0.12F * alpha));
        Gfx.text(b, text, tx + 5, ty + 4, Gfx.ink(alpha));
    }

    /* Formatting */

    static String baseName(String id)
    {
        int slash = id.lastIndexOf('/');

        return slash >= 0 ? id.substring(slash + 1) : id;
    }

    static String parentOf(String id)
    {
        int slash = id.lastIndexOf('/');

        return slash >= 0 ? id.substring(0, slash + 1) : "";
    }

    private static int depth(String folderPath)
    {
        int depth = -1;

        for (int i = 0; i < folderPath.length(); i++)
        {
            if (folderPath.charAt(i) == '/') depth++;
        }

        return Math.max(0, depth);
    }

    static String formatTicks(long ticks)
    {
        long seconds = Math.max(0, ticks) / 20;
        long h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;

        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    private static String formatSpent(long ticks)
    {
        long minutes = ticks / 20 / 60;

        if (minutes < 1)
        {
            return tr("spent.less");
        }

        if (minutes < 60)
        {
            return tr("spent.minutes", minutes);
        }

        return tr("spent.hours", minutes / 60, minutes % 60);
    }

    private static String formatDate(long millis)
    {
        if (millis <= 0)
        {
            return "—";
        }

        LocalDateTime date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        String clock = String.format("%02d:%02d", date.getHour(), date.getMinute());

        if (date.toLocalDate().equals(today))
        {
            return tr("date.today", clock);
        }

        if (date.toLocalDate().equals(today.minusDays(1)))
        {
            return tr("date.yesterday", clock);
        }

        if (date.getYear() == today.getYear())
        {
            return tr("date.this_year", date.getMonthValue(), date.getDayOfMonth());
        }

        return tr("date.full", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }
}
