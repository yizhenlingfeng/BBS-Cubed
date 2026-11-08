package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.mixin.UIFormListAccessor;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.ModelFormCategory;
import mchorse.bbs_mod.forms.categories.RecentFormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BBS++'s morphing palette: the 2.7 top bar and status line kept on top, a tree sidebar on
 * the left, the native category list in the middle and a fold-away details drawer on the
 * right. Two navigation modes share the layout — "directory" keeps every category in one
 * long list and the sidebar scrolls to the picked node; "focus" swaps the list's contents
 * to the picked node's subtree.
 */
public class UIBBSPPFormList extends UIFormList
{
    private static final int TOP = UIFormList.BAR_HEIGHT + UIFormList.STATUS_HEIGHT;
    private static final int RAIL_WIDTH = 26;
    private static final int MIN_FOR_SIDEBAR = 420;
    private static final int MIN_FOR_DRAWER = 560;
    private static final int DRAWER_WIDTH = 200;

    public static final int MODE_DIRECTORY = 0;
    public static final int MODE_FOCUS = 1;

    public UIBBSPPCategorySidebar sidebar;
    public UIBBSPPFormDetails drawer;
    public UIElement contentArea;

    public UIIcon openModelsBtn;
    public UIIcon scopeBtn;
    public UIIcon modeBtn;
    public UIIcon drawerBtn;

    private MorphNavTree<UIFormCategory> tree;

    /** Focus mode's current node; in directory mode the last node picked in the sidebar. */
    private MorphNavTree.Node<UIFormCategory> currentNode;

    /** Directory mode: the node the list must still scroll to. */
    private MorphNavTree.Node<UIFormCategory> pendingNode;

    /** The node at the viewport's top, mirrored to the sidebar while scrolling. */
    private MorphNavTree.Node<UIFormCategory> highlightNode;

    /** Scroll destination of a pending node jump (-1 = at rest); highlight stays put meanwhile. */
    private int pendingScrollTarget = -1;
    private double lastScrollValue = -1;

    /** Search scoping: remembered node at search start ("current" scope). */
    private MorphNavTree.Node<UIFormCategory> scopeNode;
    private boolean scopeCurrent;

    private boolean searching;
    private boolean rebuilding;

    /** The search as the categories got it, for the folder categories made after the native filter. */
    private String searchText = "";

    /** A pin change asks for one rebuild on the next frame (not mid mouse event). */
    private boolean folderRebuild;
    private long lastRender;

    /* Where the last palette was left (this game session only): every "pick" opens a new
     * palette, and the native scroll-to-selected lands near the top for a replay's tweaked
     * form (no library entry equals it, so it goes to "recent") */
    private static double savedScroll = -1;
    private static double savedSidebarScroll = -1;
    private static int savedMode = -1;
    private static String savedNode = "";

    /* The category at the top and how far into it: the "recent" row on top can grow by one
     * between two palettes, a raw pixel scroll would then drift */
    private static String savedTopNode = "";
    private static double savedTopOffset;

    /** Set while the last view still has to be put back once the categories are laid out. */
    private boolean pendingRestore;

    public UIBBSPPFormList(IUIFormList palette)
    {
        super(palette);

        this.removeAll();

        /* The stock search box is swapped for one whose Enter picks the first hit and whose
         * Esc clears the text before unfocusing */
        this.search = new BBSPPSearchBox(100, this::onSearchText);
        this.search.keys().register(mchorse.bbs_mod.ui.Keys.FORMS_FOCUS, this::focusSearchInput);

        this.openModelsBtn = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder((File) BBSMod.getAssetsPath("models")));
        this.openModelsBtn.tooltip(L10n.lang("studio.ui.utility.open_models_folder"));
        this.openModelsBtn.w(20);

        this.scopeBtn = new UIIcon(Icons.SEARCH, (b) -> this.toggleScope());
        this.scopeBtn.tooltip(L10n.lang("rabbit.ui.morph.scope"), Direction.TOP);
        this.scopeBtn.w(20);

        this.modeBtn = new UIIcon(Icons.LAYOUT, (b) -> this.toggleMode());
        this.modeBtn.tooltip(L10n.lang("rabbit.ui.morph.mode"), Direction.TOP);
        this.modeBtn.w(20);

        this.drawerBtn = new UIIcon(Icons.RIGHT_HANDLE, (b) -> this.toggleDrawer());
        this.drawerBtn.tooltip(L10n.lang("rabbit.ui.morph.drawer"), Direction.TOP);
        this.drawerBtn.w(20);

        this.bar.removeAll();

        if (this.categoryFilter != null)
        {
            this.bar.add(this.categoryFilter);
        }

        this.bar.add(this.collapseAll, this.expandAll, this.search, this.scopeBtn, this.modeBtn, this.drawerBtn, this.edit, this.openModelsBtn, this.close);

        this.sidebar = new UIBBSPPCategorySidebar(this);
        this.sidebar.relative(this).x(0).y(TOP).w(150).h(1F, -TOP);

        this.contentArea = new UIElement();
        this.drawer = new UIBBSPPFormDetails(this);
        this.drawer.relative(this).y(TOP).h(1F, -TOP).w(DRAWER_WIDTH);

        this.forms.full(this.contentArea);
        this.contentArea.add(this.forms);

        /* The sidebar must render after the content: its drop target report has to be the
         * last word, after the grids had their say */
        this.add(this.bar, this.contentArea, this.drawer, this.sidebar);
    }

    /* Bar bookkeeping for the palette mixin */

    public boolean isOwnBarElement(Object element)
    {
        return element == this.openModelsBtn || element == this.scopeBtn || element == this.modeBtn || element == this.drawerBtn;
    }

    /* Modes */

    public boolean isFocusMode()
    {
        return this.navMode() == MODE_FOCUS;
    }

    private int navMode()
    {
        return RabbitSettings.morphingNavMode == null ? MODE_DIRECTORY : RabbitSettings.morphingNavMode.get();
    }

    public boolean isSearching()
    {
        return this.searching;
    }

    public MorphNavTree<UIFormCategory> getTree()
    {
        return this.tree;
    }

    public MorphNavTree.Node<UIFormCategory> getCurrentNode()
    {
        return this.currentNode;
    }

    private void toggleMode()
    {
        if (RabbitSettings.morphingNavMode == null)
        {
            return;
        }

        RabbitSettings.morphingNavMode.set(this.navMode() == MODE_DIRECTORY ? MODE_FOCUS : MODE_DIRECTORY);
        MorphingDefaultCategory.save(RabbitSettings.morphingNavMode);

        if (this.isFocusMode() && this.currentNode == null && this.tree != null && !this.tree.roots().isEmpty())
        {
            this.currentNode = this.tree.roots().get(0);
        }

        this.rebuildContent();
    }

    private void toggleScope()
    {
        this.scopeCurrent = !this.scopeCurrent;

        if (this.scopeCurrent && this.scopeNode == null)
        {
            this.scopeNode = this.isFocusMode() ? this.currentNode : this.highlightNode;
        }

        if (this.searching)
        {
            this.rebuildContent();
        }
    }

    private void toggleDrawer()
    {
        if (RabbitSettings.morphingDrawerOpen == null)
        {
            return;
        }

        RabbitSettings.morphingDrawerOpen.set(!RabbitSettings.morphingDrawerOpen.get());
        MorphingDefaultCategory.save(RabbitSettings.morphingDrawerOpen);
        this.resize();
    }

    /* Search */

    /** The replacement textbox's callback — mirrors the private stock search path. */
    private void onSearchText(String raw)
    {
        String s = raw == null ? "" : raw.trim();

        for (UIFormCategory category : this.categories())
        {
            category.search(s);
        }

        this.afterSearch(s);
    }

    private List<UIFormCategory> categories()
    {
        return ((UIFormListAccessor) this).getCategories();
    }

    /** Called by UIFormListMixin after the native filter, and by our own search box. */
    public void afterSearch(String raw)
    {
        boolean was = this.searching;

        this.searchText = raw == null ? "" : raw.trim();

        this.searching = raw != null && !raw.trim().isEmpty();

        if (this.searching && !was)
        {
            this.scopeNode = this.isFocusMode() ? this.currentNode : this.highlightNode;
        }

        this.rebuildContent();
    }

    /* Content building */

    @Override
    public void setupForms(FormCategories formCategories)
    {
        /* The native setup runs the search filter, which reaches afterSearch →
         * rebuildContent — while our node references still point at the old tree. Skip the
         * intermediate rebuild and do one pass at the end. */
        this.rebuilding = true;

        try
        {
            super.setupForms(formCategories);
        }
        finally
        {
            this.rebuilding = false;
        }

        if (this.sidebar == null)
        {
            return;
        }

        List<UIFormCategory> cats = this.categories();

        this.addFolderCategories(cats);
        this.tree = this.buildTree(cats);

        /* Node references held between rebuilds re-resolve by id on the new tree */
        this.currentNode = this.remap(this.currentNode);
        this.highlightNode = this.remap(this.highlightNode);
        this.scopeNode = this.remap(this.scopeNode);
        this.pendingNode = this.remap(this.pendingNode);

        if (this.isFocusMode() && this.currentNode == null && !this.tree.roots().isEmpty())
        {
            this.currentNode = this.tree.roots().get(0);
        }

        /* Order the categories themselves — and the scroll view's children — like the
         * sidebar walks them, so the long list and the tree agree */
        List<UIFormCategory> order = this.tree.order();
        Set<UIFormCategory> inTree = new HashSet<>(order);

        cats.retainAll(inTree);

        for (UIFormCategory cat : order)
        {
            if (!cats.contains(cat))
            {
                cats.add(cat);
            }
        }

        cats.sort((a, b) -> Integer.compare(order.indexOf(a), order.indexOf(b)));

        this.sidebar.setTree(this.tree);
        this.rebuildContent();
    }

    /**
     * One category per virtual folder holding the forms filed into it — copies of the
     * models' own forms, in the folder's order. Empty ones still exist: they are what a
     * sidebar drop onto the folder lands in. Their own categories hide filed forms
     * (UIFormCategoryMixin), so each model is listed once.
     */
    private void addFolderCategories(List<UIFormCategory> cats)
    {
        MorphLayout layout = MorphLayoutStorage.get();

        if (layout.folders.isEmpty())
        {
            return;
        }

        Map<String, Form> models = new HashMap<>();

        for (UIFormCategory cat : cats)
        {
            if (cat.category instanceof ModelFormCategory)
            {
                for (Form form : cat.category.getForms())
                {
                    String ref = VirtualFolderFormCategory.refOf(form);

                    if (ref != null)
                    {
                        models.putIfAbsent(ref, form);
                    }
                }
            }
        }

        for (Map.Entry<String, String> folder : layout.folders.entrySet())
        {
            List<Form> forms = new ArrayList<>();

            for (String ref : layout.pins.getOrDefault(folder.getKey(), List.of()))
            {
                Form form = models.get(ref);

                if (form != null)
                {
                    forms.add(FormUtils.copy(form));
                }
            }

            UIFormCategory ui = new VirtualFolderFormCategory(folder.getKey(), folder.getValue(), forms).createUI(this);

            ui.search(this.searchText);
            cats.add(ui);
        }
    }

    /** Rebuilds the palette on the next frame, after a pin change. */
    public void requestFolderRebuild()
    {
        this.folderRebuild = true;
    }

    private MorphNavTree.Node<UIFormCategory> remap(MorphNavTree.Node<UIFormCategory> node)
    {
        return node == null || this.tree == null ? null : this.tree.find(node.id);
    }

    /** Fills the scroll view per mode: everything (directory), a subtree (focus), hits (search). */
    private void rebuildContent()
    {
        if (this.tree == null || this.sidebar == null || this.rebuilding)
        {
            return;
        }

        this.forms.removeAll();

        for (UIFormCategory cat : this.categories())
        {
            if (!this.isShown(cat))
            {
                continue;
            }

            this.forms.add(cat);
            cat.setVisible(true);
        }

        this.forms.resize();
        this.resize();
        this.resize();
    }

    private boolean isShown(UIFormCategory cat)
    {
        if (this.searching)
        {
            if (this.scopeCurrent && this.scopeNode != null)
            {
                if (!this.tree.subtree(this.scopeNode).contains(cat))
                {
                    return false;
                }
            }

            /* Directory mode shows everything and lets the native empty-category folding
             * hide misses; focus mode pulls in every category that has a hit */
            return !this.isFocusMode() || !cat.getForms().isEmpty();
        }

        /* Layout-hidden nodes leave the content list (searches and the session toggle
         * bring them back) — in both directory and focus mode */
        MorphNavTree.Node<UIFormCategory> node = this.tree.nodeOf(cat);

        if (node != null && this.tree.isHidden(node) && !this.sidebar.isShowingHidden())
        {
            return false;
        }

        if (this.isFocusMode())
        {
            return this.currentNode != null && this.tree.subtree(this.currentNode).contains(cat);
        }

        return true;
    }

    /* Classification into the nav tree */

    private MorphNavTree<UIFormCategory> buildTree(List<UIFormCategory> cats)
    {
        List<MorphNavTree.Entry<UIFormCategory>> entries = new ArrayList<>();

        for (UIFormCategory cat : cats)
        {
            entries.add(new MorphNavTree.Entry<>(cat.category.visible.getId(), kindOf(cat), folderOf(cat), cat));
        }

        MorphNavTree<UIFormCategory> tree = MorphNavTree.build(entries, this::groupLabel);

        /* Leaf rows carry the category's own title */
        for (MorphNavTree.Entry<UIFormCategory> entry : entries)
        {
            MorphNavTree.Node<UIFormCategory> node = tree.find("cat:" + entry.categoryId());

            if (node != null)
            {
                node.label = entry.payload().category.getProcessedTitle();
            }
        }

        /* Attached categories (favorites/recent on their group) refine the group label:
         * recent keeps BBS's own title */
        MorphNavTree.Node<UIFormCategory> recent = tree.find("group:recent");

        if (recent != null && !recent.payloads.isEmpty())
        {
            recent.label = recent.payloads.get(0).category.getProcessedTitle();
        }

        tree.applyLayout(MorphLayoutStorage.get());

        return tree;
    }

    private String groupLabel(String key)
    {
        return switch (key)
        {
            case "models" -> UIKeys.FORMS_CATEGORIES_MODELS.get();
            case "particles" -> UIKeys.FORMS_CATEGORIES_PARTICLES.get();
            case "packs" -> UIKeys.FORMS_CATEGORIES_MODELS_PACKS.get();
            case "favorites" -> L10n.lang("rabbit.ui.morph.favorites").get();
            case "user" -> L10n.lang("rabbit.ui.morph.user_categories").get();
            case "mobs" -> L10n.lang("rabbit.ui.morph.mobs").get();
            case "recent" -> L10n.lang("rabbit.ui.morph.recent").get();
            case "extra" -> L10n.lang("rabbit.ui.morph.extra").get();
            default -> key;
        };
    }

    private MorphNavTree.Kind kindOf(UIFormCategory cat)
    {
        FormCategory category = cat.category;
        String id = category.visible.getId();

        if (category instanceof VirtualFolderFormCategory)
        {
            return MorphNavTree.Kind.VIRTUAL;
        }

        if (category instanceof FavoriteFormCategory)
        {
            return MorphNavTree.Kind.FAVORITES;
        }

        if (category instanceof RecentFormCategory)
        {
            return MorphNavTree.Kind.RECENT;
        }

        if (category instanceof UserFormCategory)
        {
            return MorphNavTree.Kind.USER;
        }

        if (category instanceof ModelFormCategory)
        {
            return MorphNavTree.Kind.MODEL;
        }

        if (id.equals("mobs_animals") || id.equals("mobs_neutral") || id.equals("mobs_hostile") || id.equals("mobs_misc"))
        {
            return MorphNavTree.Kind.MOB;
        }

        if (id.equals("extra"))
        {
            return MorphNavTree.Kind.EXTRA;
        }

        if (!category.getForms().isEmpty() && category.getForms().get(0) instanceof ParticleForm)
        {
            return MorphNavTree.Kind.PARTICLE;
        }

        return MorphNavTree.Kind.OTHER;
    }

    private String folderOf(UIFormCategory cat)
    {
        String id = cat.category.visible.getId();
        MorphNavTree.Kind kind = this.kindOf(cat);

        if (kind == MorphNavTree.Kind.MODEL && id.startsWith("models_"))
        {
            return parentPath(id.substring("models_".length()));
        }

        if (kind == MorphNavTree.Kind.PARTICLE)
        {
            return parentPath(id);
        }

        if (kind == MorphNavTree.Kind.VIRTUAL)
        {
            return ((VirtualFolderFormCategory) cat.category).folderId;
        }

        return "";
    }

    private static String parentPath(String id)
    {
        int slash = id.lastIndexOf('/');

        return slash >= 0 ? id.substring(0, slash) : "";
    }

    /* Sidebar callbacks */

    public void onNodeSelected(MorphNavTree.Node<UIFormCategory> node)
    {
        if (node == null)
        {
            return;
        }

        this.currentNode = node;
        this.sidebar.select(node);

        if (RabbitSettings.morphingLastNode != null)
        {
            RabbitSettings.morphingLastNode.set(node.id);
            MorphingDefaultCategory.save(RabbitSettings.morphingLastNode);
        }

        if (this.isFocusMode())
        {
            this.rebuildContent();
        }
        else
        {
            /* The scroll happens in render(), once the categories are laid out */
            this.pendingNode = node;
            this.highlightNode = node;
            this.sidebar.highlight(node);
        }
    }

    /* Opening position */

    private boolean bbsppIsReopening()
    {
        return System.currentTimeMillis() - this.lastRender > 500;
    }

    @Override
    public void setSelected(Form form)
    {
        boolean reopening = this.bbsppIsReopening();

        super.setSelected(form);

        if (reopening)
        {
            this.applyOpenPosition();
        }
    }

    private void applyOpenPosition()
    {
        if (this.tree == null || this.sidebar == null)
        {
            return;
        }

        String def = MorphingDefaultCategory.get();
        MorphNavTree.Node<UIFormCategory> node = null;

        if (!def.isEmpty() && !def.equals(MorphingDefaultCategory.HOME))
        {
            node = this.tree.find(def);

            /* Older settings stored a category's visible id */
            if (node == null)
            {
                node = this.tree.findByCategoryId(def);
            }
        }

        if (node != null)
        {
            this.onNodeSelected(node);

            return;
        }

        if (this.isFocusMode())
        {
            UIFormCategory selectedCat = this.getSelectedCategory();

            if (selectedCat != null)
            {
                node = this.tree.nodeOf(selectedCat);
            }

            if (node == null && RabbitSettings.morphingLastNode != null)
            {
                node = this.tree.find(RabbitSettings.morphingLastNode.get());
            }

            if (node == null && !this.tree.roots().isEmpty())
            {
                node = this.tree.roots().get(0);
            }

            if (node != null)
            {
                this.onNodeSelected(node);
            }
        }

        this.restoreLastView();
    }

    /** Without a default category, open where the last palette was left (same mode, and in focus mode the same node). */
    private void restoreLastView()
    {
        if (savedScroll < 0 || savedMode != this.navMode())
        {
            return;
        }

        if (this.isFocusMode() && (this.currentNode == null || !this.currentNode.id.equals(savedNode)))
        {
            return;
        }

        this.pendingRestore = true;

        if (savedSidebarScroll >= 0)
        {
            this.sidebar.restoreScroll(savedSidebarScroll);
        }
    }

    private void saveLastView()
    {
        if (this.tree == null || this.searching || this.pendingNode != null || this.pendingScrollTarget >= 0 || this.pendingRestore || this.forms.area.w <= 0)
        {
            return;
        }

        double scroll = this.forms.scroll.getScroll();
        UIFormCategory top = this.categoryAt((int) scroll + this.forms.area.y);
        MorphNavTree.Node<UIFormCategory> topNode = top == null ? null : this.tree.nodeOf(top);

        savedScroll = scroll;
        savedMode = this.navMode();
        savedNode = this.currentNode == null ? "" : this.currentNode.id;
        savedTopNode = topNode == null ? "" : topNode.id;
        savedTopOffset = topNode == null ? 0 : scroll - (top.area.y - this.forms.area.y);

        if (this.sidebar.isVisible() && !this.sidebar.hasPendingScroll())
        {
            savedSidebarScroll = this.sidebar.getScrollValue();
        }
    }

    /* Selection by keyboard */

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_F) && !Window.isCtrlPressed() && !context.isFocused()
            && (this.forms.area.isInside(context) || this.sidebar.area.isInside(context)))
        {
            if (this.selection.isGroup())
            {
                FavoriteForms.toggleAll(new ArrayList<>(this.selection.getItems()));
            }
            else
            {
                Form selected = this.getSelected();

                if (selected != null)
                {
                    FavoriteForms.toggle(selected);
                }
            }

            this.reconcile();

            return true;
        }

        return super.subKeyPressed(context);
    }

    /** Enter in the search box: pick the first hit like a click, and bring it into view. */
    private void selectFirstHit()
    {
        for (IUIElement element : this.forms.getChildren())
        {
            if (!(element instanceof UIFormCategory cat) || cat.getForms().isEmpty())
            {
                continue;
            }

            Form form = cat.getForms().get(0);

            cat.select(form, true);
            this.selection.set(form, cat.category);

            int index = cat.getForms().indexOf(form);
            int y = cat.area.y - this.forms.area.y + cat.getLayout().getY(index);

            this.forms.scroll.scrollIntoView(y);

            return;
        }
    }

    /* Drops */

    @Override
    public void dropForms(UIFormCategory target, int insertion, List<Form> forms)
    {
        FormCategory into = target.category;

        if (into instanceof VirtualFolderFormCategory folder)
        {
            this.fileForms(folder, target, insertion, forms);

            return;
        }
        boolean intoFav = into instanceof FavoriteFormCategory;

        /* Between the favourites and the rest only copies travel — a favourite dragged out
         * is a copy for the target, a form dropped in stays where it was */
        List<Form> copies = new ArrayList<>();
        List<Form> rest = new ArrayList<>();

        for (Form form : forms)
        {
            FormCategory from = this.categoryOf(form);
            boolean fromFav = from instanceof FavoriteFormCategory;

            if (intoFav != fromFav)
            {
                copies.add(form);
            }
            else
            {
                rest.add(form);
            }
        }

        if (copies.isEmpty())
        {
            super.dropForms(target, insertion, forms);

            return;
        }

        int index = Math.min(Math.max(insertion, 0), into.getForms().size());

        for (Form form : copies)
        {
            into.insertForm(index++, FormUtils.copy(form));
        }

        if (!rest.isEmpty())
        {
            super.dropForms(target, insertion, rest);
        }

        this.reconcile();
    }

    /**
     * Files models into a virtual folder (or rearranges the folder's own): only the models
     * section's forms and other folders' forms qualify — a user category's configured model
     * would lose its settings, since a folder lists the model's own form.
     */
    private void fileForms(VirtualFolderFormCategory folder, UIFormCategory target, int insertion, List<Form> forms)
    {
        List<String> refs = new ArrayList<>();

        for (Form form : forms)
        {
            FormCategory from = this.categoryOf(form);
            String ref = VirtualFolderFormCategory.refOf(form);

            if (ref != null && !refs.contains(ref) && (from instanceof ModelFormCategory || from instanceof VirtualFolderFormCategory))
            {
                refs.add(ref);
            }
        }

        if (refs.isEmpty())
        {
            return;
        }

        /* The slot is named by the first form after the caret that stays put */
        List<Form> view = target.getForms();
        String before = null;

        for (int i = Math.max(insertion, 0); i < view.size(); i++)
        {
            String ref = VirtualFolderFormCategory.refOf(view.get(i));

            if (ref != null && !refs.contains(ref))
            {
                before = ref;

                break;
            }
        }

        MorphLayout layout = MorphLayoutStorage.get();

        for (String ref : refs)
        {
            layout.unpin(ref);
        }

        List<String> filed = layout.pins.getOrDefault(folder.folderId, List.of());
        int index = before == null || !filed.contains(before) ? filed.size() : filed.indexOf(before);

        for (String ref : refs)
        {
            layout.pin(ref, folder.folderId, index++);
        }

        MorphLayoutStorage.save();
        this.requestFolderRebuild();
    }

    /** Fallback for a drop the grids couldn't release (e.g. focus mode showing no grid). */
    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        super.subMouseReleased(context);

        if (this.drag.isPressed() && this.drag.isActive() && this.drag.getTarget() instanceof UIFormCategory target)
        {
            this.dropForms(target, this.drag.getInsertion(), new ArrayList<>(this.drag.getItems()));
            this.drag.reset();
        }

        return false;
    }

    /* Layout */

    @Override
    public void resize()
    {
        if (this.sidebar == null || this.contentArea == null || this.drawer == null)
        {
            super.resize();

            return;
        }

        boolean rail = this.area.w < MIN_FOR_SIDEBAR;
        int sw = rail ? RAIL_WIDTH : this.sidebar.getSidebarWidth();
        int dw = this.drawerOpen() && this.area.w >= MIN_FOR_DRAWER ? DRAWER_WIDTH : 0;

        this.sidebar.relative(this).x(0).y(TOP).w(sw).h(1F, -TOP);
        this.drawer.relative(this).x(1F, -dw).y(TOP).w(dw).h(1F, -TOP);
        this.drawer.setVisible(dw > 0);
        this.contentArea.relative(this).x(sw).y(TOP).w(1F, -sw - dw).h(1F, -TOP);

        /* Focus mode makes the fold-all buttons meaningless */
        this.collapseAll.setVisible(!this.isFocusMode());
        this.expandAll.setVisible(!this.isFocusMode());

        super.resize();
    }

    private boolean drawerOpen()
    {
        return RabbitSettings.morphingDrawerOpen == null || RabbitSettings.morphingDrawerOpen.get();
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        this.lastRender = System.currentTimeMillis();

        if (this.folderRebuild)
        {
            this.folderRebuild = false;
            this.setupForms(mchorse.bbs_mod.BBSModClient.getFormCategories());
        }

        /* Empty favorites/recent/virtual folders fold away in directory mode; checked per frame since a
         * drop or a favorite toggle can change it without a rebuild */
        if (this.tree != null && !this.isFocusMode())
        {
            boolean changed = false;

            for (UIFormCategory cat : this.categories())
            {
                if (!(cat.category instanceof FavoriteFormCategory) && !(cat.category instanceof RecentFormCategory) && !(cat.category instanceof VirtualFolderFormCategory))
                {
                    continue;
                }

                boolean want = !cat.category.getForms().isEmpty();

                if (cat.isVisible() != want)
                {
                    cat.setVisible(want);
                    changed = true;
                }
            }

            if (changed)
            {
                this.resize();
            }
        }

        this.updateLastMargin();

        super.render(context);

        /* After the native scroll-to-selected of this same frame, so ours wins without a visible jump */
        if (this.pendingRestore && this.forms.area.w > 0)
        {
            this.resize();
            this.resize();
            this.forms.scroll.setScroll(this.restoredScroll());
            this.pendingRestore = false;
        }

        /* A sidebar pick in directory mode scrolls the node into view — here, where the
         * categories are laid out, like the native pendingScrollToSelected */
        if (this.pendingNode != null && this.forms.area.w > 0)
        {
            List<UIFormCategory> subtree = this.tree.subtree(this.pendingNode);

            if (!subtree.isEmpty())
            {
                /* Off-screen categories still carry stale heights — lay them out first,
                 * the same reason the native scrollToSelectedForm re-resizes twice */
                this.resize();
                this.resize();

                /* An empty folder category is folded away — aim at the first one shown */
                UIFormCategory first = subtree.get(0);

                for (UIFormCategory cat : subtree)
                {
                    if (cat.isVisible())
                    {
                        first = cat;

                        break;
                    }
                }

                this.pendingScrollTarget = first.area.y - this.forms.area.y;
                this.lastScrollValue = -1;

                this.forms.scroll.scrollTo(this.pendingScrollTarget);
            }

            this.pendingNode = null;
        }

        /* While the smooth scroll is still travelling, the viewport top is meaningless —
         * mirroring it now would snap the capsule back before it catches up. The jump is
         * over once it arrives or stops moving (a target past the end gets clamped). */
        double scroll = this.forms.scroll.getScroll();

        if (this.pendingScrollTarget >= 0 && (Math.abs(scroll - this.pendingScrollTarget) < 1 || scroll == this.lastScrollValue))
        {
            this.pendingScrollTarget = -1;
        }

        this.lastScrollValue = scroll;

        /* Mirror the viewport's top category onto the sidebar */
        if (this.pendingScrollTarget < 0 && this.tree != null && !this.isFocusMode() && !this.searching)
        {
            this.updateHighlight();
        }

        this.saveLastView();
    }

    /**
     * Directory mode pads the last visible category so even a tail category shorter than a
     * screenful can reach the viewport's top — without it the sidebar can never scroll to
     * or highlight it. Focus mode and searches keep the stock 20px tail margin.
     */
    private void updateLastMargin()
    {
        List<IUIElement> children = this.forms.getChildren();
        UIFormCategory last = null;

        for (IUIElement element : children)
        {
            if (element instanceof UIFormCategory cat && cat.isVisible())
            {
                last = cat;
            }
        }

        boolean pad = !this.isFocusMode() && !this.searching;
        boolean changed = false;

        for (IUIElement element : children)
        {
            if (!(element instanceof UIFormCategory cat))
            {
                continue;
            }

            int bottom = 0;

            if (cat == last)
            {
                bottom = pad ? Math.max(20, this.forms.area.h - cat.area.h) : 20;
            }

            if (cat.margin.bottom != bottom)
            {
                cat.marginBottom(bottom);
                changed = true;
            }
        }

        if (changed)
        {
            this.forms.resize();
        }
    }

    private double restoredScroll()
    {
        if (!savedTopNode.isEmpty())
        {
            for (IUIElement element : this.forms.getChildren())
            {
                if (element instanceof UIFormCategory cat && cat.isVisible() && cat.area.h > 0)
                {
                    MorphNavTree.Node<UIFormCategory> node = this.tree.nodeOf(cat);

                    if (node != null && node.id.equals(savedTopNode))
                    {
                        return cat.area.y - this.forms.area.y + savedTopOffset;
                    }
                }
            }
        }

        return savedScroll;
    }

    /** The first shown category reaching below the given screen y. */
    private UIFormCategory categoryAt(int y)
    {
        for (IUIElement element : this.forms.getChildren())
        {
            if (element instanceof UIFormCategory cat && cat.isVisible() && cat.area.h > 0 && cat.area.ey() > y)
            {
                return cat;
            }
        }

        return null;
    }

    private void updateHighlight()
    {
        int top = (int) this.forms.scroll.getScroll() + this.forms.area.y;

        for (IUIElement element : this.forms.getChildren())
        {
            if (!(element instanceof UIFormCategory cat) || !cat.isVisible() || cat.area.h <= 0)
            {
                continue;
            }

            if (cat.area.ey() > top)
            {
                MorphNavTree.Node<UIFormCategory> node = this.tree.nodeOf(cat);

                if (node != this.highlightNode)
                {
                    this.highlightNode = node;
                    this.sidebar.highlight(node);
                }

                return;
            }
        }
    }

    /* The search textbox with Enter/Esc behaviour */
    private class BBSPPSearchBox extends UITextbox
    {
        BBSPPSearchBox(int maxLength, java.util.function.Consumer<String> callback)
        {
            super(maxLength, callback);

            this.placeholder(UIKeys.FORMS_LIST_SEARCH);
        }

        @Override
        public boolean subKeyPressed(UIContext context)
        {
            if (this.isFocused())
            {
                if (context.isPressed(GLFW.GLFW_KEY_ENTER))
                {
                    selectFirstHit();

                    return true;
                }

                if (context.isPressed(GLFW.GLFW_KEY_ESCAPE) && !this.getText().isEmpty())
                {
                    this.setText("");

                    if (this.callback != null)
                    {
                        this.callback.accept("");
                    }

                    return true;
                }
            }

            return super.subKeyPressed(context);
        }
    }
}
