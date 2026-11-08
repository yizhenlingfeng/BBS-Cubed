package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.client.ui.home.Gfx;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The tree sidebar of the new morphing panel: groups and folders per {@link MorphNavTree},
 * fold arrows, custom category icons, hit counters while searching, form drop targets and
 * user-category reordering. Rendered by hand (no child row elements) so indentation,
 * the sliding selection capsule and the icon rail stay cheap.
 */
public class UIBBSPPCategorySidebar extends UIElement
{
    public static final int ROW = 20;
    public static final int INDENT = 10;
    public static final int RAIL = 26;
    public static final int MIN_WIDTH = 80;
    public static final int MAX_WIDTH = 300;

    private final UIBBSPPFormList list;

    private MorphNavTree<UIFormCategory> tree;
    private final Set<String> expanded = new HashSet<>();
    private boolean expandedLoaded;

    public MorphNavTree.Node<UIFormCategory> selected;
    private MorphNavTree.Node<UIFormCategory> highlighted;
    private MorphNavTree.Node<UIFormCategory> menuNode;

    private final Scroll scroll = new Scroll(new Area());
    private final UIDraggable resizer;

    /** Scroll to put back once the rows are counted (-1 = none); setting it earlier gets clamped to 0. */
    private double pendingScroll = -1;

    private int width = 150;
    private boolean widthLoaded;
    private boolean wasResizing;

    /* Flattened rows for the current frame */
    private final List<Row> rows = new ArrayList<>();

    /* The drop target the sidebar reported last frame, so it can clear a stale one */
    private UIFormCategory lastDropTarget;

    /* Free node drag inside the sidebar (the layout overlay persists the result) */
    private MorphNavTree.Node<UIFormCategory> pressedNode;
    private boolean nodeDragging;
    private int pressY;
    private MorphNavTree.Node<UIFormCategory> dropParent;   /* null = roots */
    private int dropIndex = -1;
    private MorphNavTree.Node<UIFormCategory> dropIntoNode;

    /* Session toggle: show layout-hidden nodes dimmed so they can be unhidden */
    private boolean showHidden;

    /* The open folder-colour picker; its edits are saved once it closes */
    private mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPicker colorPicker;
    private boolean colorDirty;

    public boolean isShowingHidden()
    {
        return this.showHidden;
    }

    /* Sliding selection capsule */
    private float capsuleY = -1;
    private float capsuleH = ROW;
    private long lastFrame;

    private static class Row
    {
        MorphNavTree.Node<UIFormCategory> node;
        int depth;
        boolean hasChildren;
        boolean newCategory;

        Row(MorphNavTree.Node<UIFormCategory> node, int depth)
        {
            this.node = node;
            this.depth = depth;
            this.hasChildren = !node.children.isEmpty();
        }

        Row()
        {
            this.newCategory = true;
        }
    }

    public UIBBSPPCategorySidebar(UIBBSPPFormList list)
    {
        this.list = list;

        this.scroll.scrollSpeed = ROW;
        this.scroll.cancelScrolling();

        this.resizer = new UIDraggable((context) ->
        {
            int w = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, context.mouseX - this.area.x));

            if (w != this.width)
            {
                this.width = w;
                this.w(w);

                if (this.getParent() != null)
                {
                    this.getParent().resize();
                }
            }
        });

        this.add(this.resizer);

        this.context(this::buildMenu);
    }

    public int getSidebarWidth()
    {
        this.loadWidth();

        return this.width;
    }

    private void loadWidth()
    {
        if (!this.widthLoaded && RabbitSettings.morphingSidebarWidth != null)
        {
            this.widthLoaded = true;
            this.width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, RabbitSettings.morphingSidebarWidth.get()));
        }
    }

    private void saveWidth()
    {
        if (RabbitSettings.morphingSidebarWidth != null && RabbitSettings.morphingSidebarWidth.get() != this.width)
        {
            RabbitSettings.morphingSidebarWidth.set(this.width);
            MorphingDefaultCategory.save(RabbitSettings.morphingSidebarWidth);
        }
    }

    public void setTree(MorphNavTree<UIFormCategory> tree)
    {
        this.tree = tree;

        if (!this.expandedLoaded)
        {
            this.expandedLoaded = true;
            this.expanded.clear();

            if (RabbitSettings.morphingExpandedNodes != null)
            {
                for (String line : RabbitSettings.morphingExpandedNodes.get().split("\n"))
                {
                    if (!line.isEmpty())
                    {
                        this.expanded.add(line);
                    }
                }
            }
        }

        /* Drop expansion and selection state pointing at nodes that no longer exist */
        this.expanded.removeIf((id) -> (id.startsWith("group:") || id.startsWith("vf:")) && this.tree.find(id) == null && !id.equals("group:user") && !id.equals("group:models") && !id.equals("group:mobs"));

        if (this.selected != null && this.tree.find(this.selected.id) != this.selected)
        {
            this.selected = this.tree.find(this.selected.id);
        }

        if (this.highlighted != null)
        {
            this.highlighted = this.tree.find(this.highlighted.id);
        }
    }

    private void saveExpanded()
    {
        if (RabbitSettings.morphingExpandedNodes != null)
        {
            RabbitSettings.morphingExpandedNodes.set(String.join("\n", this.expanded));
            MorphingDefaultCategory.save(RabbitSettings.morphingExpandedNodes);
        }
    }

    public boolean isExpanded(MorphNavTree.Node<UIFormCategory> node)
    {
        if (this.expanded.contains(node.id))
        {
            return true;
        }

        /* While searching, groups holding a hit unfold themselves (not persisted) */
        return this.list.isSearching() && this.subtreeHits(node) > 0;
    }

    /** Visible subtrees only: hidden nodes are skipped unless searching/shown anyway. */
    private int subtreeHits(MorphNavTree.Node<UIFormCategory> node)
    {
        if (this.tree == null)
        {
            return 0;
        }

        int hits = 0;

        for (MorphNavTree.Node<UIFormCategory> n : this.tree.nodesOf(node))
        {
            if (this.tree.isHidden(n) && !this.list.isSearching() && !this.showHidden)
            {
                continue;
            }

            for (UIFormCategory cat : n.payloads)
            {
                hits += cat.getForms().size();
            }
        }

        return hits;
    }

    public void expand(MorphNavTree.Node<UIFormCategory> node)
    {
        if (node != null && !node.children.isEmpty() && this.expanded.add(node.id))
        {
            this.saveExpanded();
        }
    }

    private void toggleExpanded(MorphNavTree.Node<UIFormCategory> node)
    {
        if (!this.expanded.remove(node.id))
        {
            this.expanded.add(node.id);
        }

        this.saveExpanded();
    }

    /** The node the directory mode mirrors the scroll position to. */
    public void highlight(MorphNavTree.Node<UIFormCategory> node)
    {
        this.highlighted = node;
    }

    public void clearHighlight()
    {
        this.highlighted = null;
    }

    public void select(MorphNavTree.Node<UIFormCategory> node)
    {
        this.selected = node;
    }

    public double getScrollValue()
    {
        return this.scroll.getScroll();
    }

    public void restoreScroll(double value)
    {
        this.pendingScroll = value;
    }

    public boolean hasPendingScroll()
    {
        return this.pendingScroll >= 0;
    }

    /* Rows */

    private void rebuildRows()
    {
        this.rows.clear();

        if (this.tree == null)
        {
            return;
        }

        boolean rail = this.isRail();

        for (MorphNavTree.Node<UIFormCategory> root : this.tree.roots())
        {
            this.addRows(root, 0, rail);
        }
    }

    private void addRows(MorphNavTree.Node<UIFormCategory> node, int depth, boolean rail)
    {
        if (this.tree.isHidden(node) && !this.list.isSearching() && !this.showHidden)
        {
            return;
        }

        Row row = new Row(node, rail ? 0 : depth);

        this.rows.add(row);

        if (rail || !this.isExpanded(node))
        {
            return;
        }

        for (MorphNavTree.Node<UIFormCategory> child : node.children)
        {
            this.addRows(child, depth + 1, rail);
        }

        if (node.id.equals("group:user"))
        {
            this.rows.add(new Row());
        }
    }

    private boolean isRail()
    {
        return this.list.area.w < 420;
    }

    private MorphNavTree.Node<UIFormCategory> rowAt(int y)
    {
        int index = (int) ((y - this.area.y + this.scroll.getScroll()) / ROW);

        return index >= 0 && index < this.rows.size() ? this.rows.get(index).node : null;
    }

    private Row rowEntryAt(int y)
    {
        int index = (int) ((y - this.area.y + this.scroll.getScroll()) / ROW);

        return index >= 0 && index < this.rows.size() ? this.rows.get(index) : null;
    }

    private int rowY(int index)
    {
        return this.area.y + index * ROW - (int) this.scroll.getScroll();
    }

    private int nodeRowIndex(MorphNavTree.Node<UIFormCategory> node)
    {
        for (int i = 0; i < this.rows.size(); i++)
        {
            if (this.rows.get(i).node == node)
            {
                return i;
            }
        }

        return -1;
    }

    /* Input */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        boolean rail = this.isRail();

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (!this.area.isInside(context))
        {
            return false;
        }

        if (context.mouseButton == 1)
        {
            if (this.nodeDragging)
            {
                this.nodeDragging = false;
                this.pressedNode = null;

                return true;
            }

            this.menuNode = rail ? this.rowAt(context.mouseY) : this.rowAt(context.mouseY);

            return super.subMouseClicked(context);
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        if (rail)
        {
            MorphNavTree.Node<UIFormCategory> node = this.rowAt(context.mouseY);

            if (node != null)
            {
                this.list.onNodeSelected(node);
            }

            return true;
        }

        Row row = this.rowEntryAt(context.mouseY);

        if (row == null)
        {
            this.nodeDragging = false;
            this.pressedNode = null;

            return true;
        }

        int contentX = context.mouseX - this.area.x;
        int indent = row.depth * INDENT;
        int arrowX = indent + UIList.ROW_PADDING;

        if (row.newCategory)
        {
            this.promptNewCategory();

            return true;
        }

        if (row.node == null)
        {
            return true;
        }

        if (row.hasChildren && contentX >= arrowX && contentX < arrowX + UIList.ARROW_SLOT + 4)
        {
            this.toggleExpanded(row.node);

            return true;
        }

        /* Any node may drag (not the rail, not mid-search, not from the fold arrow) */
        if (!rail && !this.list.isSearching())
        {
            this.pressedNode = row.node;
            this.pressY = context.mouseY;
        }

        this.list.onNodeSelected(row.node);

        return true;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.scroll.mouseReleased(context);

        if (this.nodeDragging)
        {
            this.finishNodeDrag();
        }

        this.pressedNode = null;

        return super.subMouseReleased(context);
    }

    @Override
    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && this.scroll.mouseScroll(context))
        {
            return this;
        }

        return super.childrenMouseScrolled(context);
    }

    /* Context menu */

    private void buildMenu(ContextMenuManager menu)
    {
        FormCategories formCategories = BBSModClient.getFormCategories();
        UserFormSection userForms = formCategories.getUserForms();
        MorphNavTree.Node<UIFormCategory> node = this.menuNode;

        if (node == null || node.id.equals("group:user"))
        {
            this.addCategoryAction(menu, userForms, formCategories);
        }

        if (node == null)
        {
            return;
        }

        if (node.id.equals("group:favorites"))
        {
            UIFormCategory fav = node.payloads.isEmpty() ? null : node.payloads.get(0);

            if (fav != null && !fav.category.getForms().isEmpty())
            {
                menu.action(Icons.TRASH, L10n.lang("rabbit.ui.morph.clear_favorites"), () ->
                {
                    UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
                        L10n.lang("rabbit.ui.morph.clear_favorites"),
                        L10n.lang("rabbit.ui.morph.clear_favorites_confirm"),
                        (confirmed) ->
                        {
                            if (confirmed)
                            {
                                fav.category.clearForms();
                            }
                        }
                    ));
                });
            }
        }

        boolean isFolderGroup = node.id.startsWith("group:models") || node.id.startsWith("group:particles");

        /* Real folders only — a virtual folder is display data, nothing on disk */
        java.io.File folder = MorphFolders.ofNode(node.id);

        if (folder != null)
        {
            menu.action(Icons.FOLDER, L10n.lang("rabbit.ui.morph.open_folder"), () -> mchorse.bbs_mod.ui.utils.UIUtils.openFolder(folder));
        }

        this.addColorActions(menu, node);

        if (isFolderGroup)
        {
            menu.action(Icons.EXPAND_ALL, L10n.lang("rabbit.ui.morph.expand_all_folders"), () -> this.setFoldersExpanded(node, true));
            menu.action(Icons.COLLAPSE_ALL, L10n.lang("rabbit.ui.morph.collapse_all_folders"), () -> this.setFoldersExpanded(node, false));
        }

        if (!node.payloads.isEmpty())
        {
            UIFormCategory cat = node.payloads.get(0);

            if (MorphingDefaultCategory.isDefault(node.id) || MorphingDefaultCategory.isDefault(cat.category.visible.getId()))
            {
                menu.action(Icons.BOOKMARK, L10n.lang("rabbit.ui.morph.clear_default_open"), () -> MorphingDefaultCategory.set(MorphingDefaultCategory.HOME));
            }
            else
            {
                menu.action(Icons.BOOKMARK, L10n.lang("rabbit.ui.morph.set_default_open"), () -> MorphingDefaultCategory.set(node.id));
            }

            menu.action(Icons.EDIT, L10n.lang("rabbit.ui.morph.change_category_icon"), () ->
            {
                String id = cat.category.visible.getId();
                Icon current = MorphCategoryIconStorage.get(id, cat.category.icon);

                UIOverlay.addOverlay(this.getContext(), new UIBBSPPIconPickerOverlayPanel(
                    L10n.lang("rabbit.ui.morph.select_category_icon"), current,
                    (icon) -> MorphCategoryIconStorage.set(id, icon)), 430, 320);
            });

            if (cat.category instanceof UserFormCategory user)
            {
                this.addUserCategoryActions(menu, node, user, userForms, formCategories);
            }
        }

        this.addLayoutActions(menu, node);
    }

    /** Rename/delete for user categories (unchanged behavior). */
    private void addUserCategoryActions(ContextMenuManager menu, MorphNavTree.Node<UIFormCategory> node, UserFormCategory user, UserFormSection userForms, FormCategories formCategories)
    {
        menu.action(Icons.EDIT, L10n.lang("rabbit.ui.morph.rename_category"), () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
                L10n.lang("rabbit.ui.morph.rename_category"),
                L10n.lang("rabbit.ui.morph.rename_category_desc"),
                (str) ->
                {
                    if (!str.isEmpty())
                    {
                        user.title = IKey.constant(str);
                        userForms.writeUserCategories(user);
                        this.list.setupForms(formCategories);
                    }
                }
            ));
        });

        menu.action(Icons.TRASH, L10n.lang("rabbit.ui.morph.remove_category"), () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
                L10n.lang("rabbit.ui.morph.remove_category"),
                L10n.lang("rabbit.ui.morph.remove_category_confirm"),
                (confirmed) ->
                {
                    if (confirmed)
                    {
                        userForms.removeUserCategory(user);
                        this.list.setupForms(formCategories);
                    }
                }
            ));
        });
    }

    /* Folder / own-category colour */

    private void addColorActions(ContextMenuManager menu, MorphNavTree.Node<UIFormCategory> node)
    {
        if (!MorphLayout.canColor(node.id))
        {
            return;
        }

        MorphLayout layout = MorphLayoutStorage.get();

        menu.action(Icons.COLOR, L10n.lang("rabbit.ui.morph.set_color"), () -> this.openColorPicker(node.id));

        if (layout.colors.containsKey(node.id))
        {
            menu.action(Icons.CLOSE, L10n.lang("rabbit.ui.morph.clear_color"), () ->
            {
                layout.colors.remove(node.id);
                MorphLayoutStorage.save();
            });
        }
    }

    /** BBS's own picker at the cursor; the tint shows live, the file is written on close. */
    private void openColorPicker(String nodeId)
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        if (this.colorPicker != null)
        {
            this.colorPicker.removeFromParent();
        }

        MorphLayout layout = MorphLayoutStorage.get();
        Integer current = layout.colors.get(nodeId);
        mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPicker picker = new mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPicker((color) ->
        {
            layout.colors.put(nodeId, color | 0xFF000000);
            this.colorDirty = true;
        });

        picker.setColor(current == null ? Gfx.accent() & 0xFFFFFF : current & 0xFFFFFF);
        context.menu.overlay.add(picker);
        picker.setup(context.mouseX, context.mouseY);
        picker.bounds(context.menu.main, 2);
        picker.resize();

        this.colorPicker = picker;
    }

    /** The tinted backdrop of a row / header: a wash of the colour plus a solid stripe on the left. */
    public static void renderTint(Batcher2D batcher, int x, int y, int w, int h, int color, boolean own)
    {
        int rgb = color & 0xFFFFFF;

        batcher.box(x, y, x + w, y + h, (own ? 0x50000000 : 0x24000000) | rgb);
        batcher.box(x, y, x + 3, y + h, (own ? 0xFF000000 : 0x90000000) | rgb);
    }

    /* Layout overlay menu entries (virtual folders, hiding, resets). */
    private void addLayoutActions(ContextMenuManager menu, MorphNavTree.Node<UIFormCategory> node)
    {
        MorphLayout layout = MorphLayoutStorage.get();

        boolean container = node != null && MorphLayout.canContain(node.id);

        if (container || node == null)
        {
            String parentId = node == null ? "" : node.id;

            menu.action(Icons.ADD, L10n.lang("rabbit.ui.morph.new_folder"), () ->
            {
                UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
                    L10n.lang("rabbit.ui.morph.new_folder"),
                    L10n.lang("rabbit.ui.morph.new_folder_desc"),
                    (str) ->
                    {
                        if (str.isEmpty())
                        {
                            return;
                        }

                        String vf = MorphLayout.newFolderId();

                        layout.folders.put(vf, str);
                        layout.parents.put(vf, parentId);
                        layout.orders.computeIfAbsent(parentId, (k) -> new java.util.ArrayList<>()).add(vf);
                        MorphLayoutStorage.save();

                        if (!parentId.isEmpty())
                        {
                            this.expanded.add(parentId);
                            this.saveExpanded();
                        }

                        this.list.setupForms(BBSModClient.getFormCategories());
                    }
                ));
            });
        }

        if (node == null)
        {
            menu.action(Icons.VISIBLE, L10n.lang(this.showHidden ? "rabbit.ui.morph.hide_hidden_items" : "rabbit.ui.morph.show_hidden_items"), () ->
            {
                this.showHidden = !this.showHidden;
                this.list.setupForms(BBSModClient.getFormCategories());
            });

            return;
        }

        if (layout.folders.containsKey(node.id))
        {
            menu.action(Icons.EDIT, L10n.lang("rabbit.ui.morph.rename_folder"), () ->
            {
                UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
                    L10n.lang("rabbit.ui.morph.rename_folder"),
                    L10n.lang("rabbit.ui.morph.new_folder_desc"),
                    (str) ->
                    {
                        if (!str.isEmpty())
                        {
                            layout.folders.put(node.id, str);
                            MorphLayoutStorage.save();
                            this.list.setupForms(BBSModClient.getFormCategories());
                        }
                    }
                ));
            });

            menu.action(Icons.TRASH, L10n.lang("rabbit.ui.morph.dissolve_folder"), () ->
            {
                UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
                    L10n.lang("rabbit.ui.morph.dissolve_folder"),
                    L10n.lang("rabbit.ui.morph.dissolve_folder_confirm"),
                    (confirmed) ->
                    {
                        if (confirmed)
                        {
                            layout.dissolve(this.tree, node.id);
                            MorphLayoutStorage.save();
                            this.list.setupForms(BBSModClient.getFormCategories());
                        }
                    }
                ));
            });
        }

        if (layout.parents.containsKey(node.id))
        {
            menu.action(Icons.MOVE_TO, L10n.lang("rabbit.ui.morph.move_back"), () ->
            {
                layout.parents.remove(node.id);
                MorphLayoutStorage.save();
                this.list.setupForms(BBSModClient.getFormCategories());
            });
        }

        if (layout.orders.containsKey(node.id))
        {
            menu.action(Icons.COLLAPSE_ALL, L10n.lang("rabbit.ui.morph.reset_order"), () ->
            {
                layout.orders.remove(node.id);
                MorphLayoutStorage.save();
                this.list.setupForms(BBSModClient.getFormCategories());
            });
        }

        if (!node.id.equals("group:favorites") && !node.id.equals("group:recent"))
        {
            boolean hidden = layout.hidden.contains(node.id);

            menu.action(hidden ? Icons.INVISIBLE : Icons.VISIBLE, L10n.lang(hidden ? "rabbit.ui.morph.unhide" : "rabbit.ui.morph.hide"), () ->
            {
                if (hidden)
                {
                    layout.hidden.remove(node.id);
                }
                else
                {
                    layout.hidden.add(node.id);
                }

                MorphLayoutStorage.save();
                this.list.setupForms(BBSModClient.getFormCategories());
            });
        }

        menu.action(Icons.VISIBLE, L10n.lang(this.showHidden ? "rabbit.ui.morph.hide_hidden_items" : "rabbit.ui.morph.show_hidden_items"), () ->
        {
            this.showHidden = !this.showHidden;
            this.list.setupForms(BBSModClient.getFormCategories());
        });
    }

    private void setFoldersExpanded(MorphNavTree.Node<UIFormCategory> node, boolean expand)
    {
        for (MorphNavTree.Node<UIFormCategory> child : node.children)
        {
            if (expand)
            {
                this.expanded.add(child.id);
            }
            else
            {
                this.expanded.remove(child.id);
            }

            this.setFoldersExpanded(child, expand);
        }

        this.saveExpanded();
    }

    private void addCategoryAction(ContextMenuManager menu, UserFormSection userForms, FormCategories formCategories)
    {
        menu.action(Icons.ADD, L10n.lang("rabbit.ui.morph.new_category"), () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
                L10n.lang("rabbit.ui.morph.new_category"),
                L10n.lang("rabbit.ui.morph.new_category_desc"),
                (str) ->
                {
                    userForms.addUserCategory(new UserFormCategory(IKey.constant(str), formCategories.preferences.visible(UUID.randomUUID().toString()), userForms));
                    this.list.setupForms(formCategories);
                }
            ));
        });
    }

    private void promptNewCategory()
    {
        FormCategories formCategories = BBSModClient.getFormCategories();
        UserFormSection userForms = formCategories.getUserForms();

        UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
            L10n.lang("rabbit.ui.morph.new_category"),
            L10n.lang("rabbit.ui.morph.new_category_desc"),
            (str) ->
            {
                userForms.addUserCategory(new UserFormCategory(IKey.constant(str), formCategories.preferences.visible(UUID.randomUUID().toString()), userForms));
                this.list.setupForms(formCategories);
            }
        ));
    }

    /* Free node reordering */

    private void updateNodeDrag(UIContext context)
    {
        this.dropParent = null;
        this.dropIndex = -1;
        this.dropIntoNode = null;

        if (this.pressedNode == null)
        {
            return;
        }

        if (!this.nodeDragging && Math.abs(context.mouseY - this.pressY) > 4)
        {
            this.nodeDragging = true;
        }

        if (!this.nodeDragging)
        {
            return;
        }

        /* Keep the rows moving when the cursor hugs the edges */
        if (context.mouseY < this.area.y + ROW)
        {
            this.scroll.scrollBy(-ROW / 2);
            this.scroll.clamp();
        }
        else if (context.mouseY > this.area.ey() - ROW)
        {
            this.scroll.scrollBy(ROW / 2);
            this.scroll.clamp();
        }

        /* The row under the cursor splits into before / into / after zones */
        Row target = null;
        int targetTop = 0;

        for (int i = 0; i < this.rows.size(); i++)
        {
            Row row = this.rows.get(i);
            int y = this.rowY(i);

            if (row.node != null && context.mouseY < y + ROW)
            {
                target = row;
                targetTop = y;

                break;
            }
        }

        MorphNavTree.Node<UIFormCategory> parent;
        int index;

        if (target == null)
        {
            /* Below the last row: drop at the end of the roots */
            parent = null;
            index = this.tree.roots().size();
        }
        else
        {
            MorphNavTree.Node<UIFormCategory> node = target.node;

            if (node == this.pressedNode || this.isDescendant(node, this.pressedNode))
            {
                return;
            }

            int zone = context.mouseY - targetTop;

            if (zone < ROW / 4)
            {
                parent = node.parent;
                index = this.childIndex(parent, node);
            }
            else if (zone >= ROW * 3 / 4)
            {
                parent = node.parent;
                index = this.childIndex(parent, node) + 1;
            }
            else if (MorphLayout.canContain(node.id))
            {
                parent = node;
                index = node.children.size();
                this.dropIntoNode = node;
            }
            else if (zone < ROW / 2)
            {
                parent = node.parent;
                index = this.childIndex(parent, node);
            }
            else
            {
                parent = node.parent;
                index = this.childIndex(parent, node) + 1;
            }
        }

        /* group:user only accepts user-category leaves */
        if (parent != null && parent.id.equals("group:user") && !this.isUserLeaf(this.pressedNode))
        {
            return;
        }

        this.dropParent = parent;
        this.dropIndex = index;
    }

    private boolean isUserLeaf(MorphNavTree.Node<UIFormCategory> node)
    {
        return node.payload != null && node.payload.category instanceof UserFormCategory && node.children.isEmpty();
    }

    private boolean isDescendant(MorphNavTree.Node<UIFormCategory> node, MorphNavTree.Node<UIFormCategory> ancestor)
    {
        for (MorphNavTree.Node<UIFormCategory> n = node.parent; n != null; n = n.parent)
        {
            if (n == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    private int childIndex(MorphNavTree.Node<UIFormCategory> parent, MorphNavTree.Node<UIFormCategory> node)
    {
        List<MorphNavTree.Node<UIFormCategory>> children = parent == null ? this.tree.roots() : parent.children;

        return children.indexOf(node);
    }

    private void finishNodeDrag()
    {
        if (this.dropIndex < 0 || this.pressedNode == null)
        {
            this.nodeDragging = false;

            return;
        }

        MorphLayout layout = MorphLayoutStorage.get();
        String parentId = this.dropParent == null ? "" : this.dropParent.id;

        /* Reordering inside group:user stays on the native user-category path. The caret
         * is a tree-child index; moveUserCategory wants a position in section.categories
         * (they diverge once a category lives outside the group), so map through it. */
        if (parentId.equals("group:user") && this.isUserLeaf(this.pressedNode))
        {
            UserFormSection section = BBSModClient.getFormCategories().getUserForms();
            UserFormCategory user = (UserFormCategory) this.pressedNode.payload.category;
            int from = section.categories.indexOf(user);
            List<MorphNavTree.Node<UIFormCategory>> groupChildren = this.dropParent.children;
            int to;

            if (this.dropIndex >= groupChildren.size())
            {
                to = groupChildren.isEmpty() ? section.categories.size()
                    : section.categories.indexOf(groupChildren.get(groupChildren.size() - 1).payload.category) + 1;
            }
            else
            {
                MorphNavTree.Node<UIFormCategory> after = groupChildren.get(this.dropIndex);

                to = after.payload == null ? section.categories.size() : section.categories.indexOf(after.payload.category);
            }

            boolean hadOverride = layout.parents.remove(this.pressedNode.id) != null;

            layout.orders.remove("group:user");
            MorphLayoutStorage.save();

            if (from != -1 && to != from && to != from + 1)
            {
                section.moveUserCategory(user, to);
            }

            if (hadOverride || (from != -1 && to != from && to != from + 1))
            {
                this.list.setupForms(BBSModClient.getFormCategories());
            }

            this.nodeDragging = false;

            return;
        }

        /* Recompute the caret after the dragged node leaves its current slot */
        List<MorphNavTree.Node<UIFormCategory>> siblings = this.dropParent == null ? this.tree.roots() : this.dropParent.children;
        int caret = this.dropIndex;
        int oldIndex = siblings.indexOf(this.pressedNode);

        if (oldIndex >= 0 && oldIndex < caret)
        {
            caret--;
        }

        if (this.pressedNode.id.equals(parentId))
        {
            this.nodeDragging = false;

            return;
        }

        if (parentId.equals(this.pressedNode.naturalParentId))
        {
            layout.parents.remove(this.pressedNode.id);
        }
        else
        {
            layout.parents.put(this.pressedNode.id, parentId);
        }

        List<String> order = new java.util.ArrayList<>();

        for (MorphNavTree.Node<UIFormCategory> sibling : siblings)
        {
            if (sibling != this.pressedNode)
            {
                order.add(sibling.id);
            }
        }

        order.add(Math.max(0, Math.min(caret, order.size())), this.pressedNode.id);
        layout.orders.put(parentId, order);
        MorphLayoutStorage.save();

        if (this.dropParent != null)
        {
            this.expanded.add(parentId);
            this.saveExpanded();
        }

        this.list.setupForms(BBSModClient.getFormCategories());
        this.nodeDragging = false;
    }

    /* Rendering */

    @Override
    public void resize()
    {
        super.resize();

        this.loadWidth();

        int w = this.isRail() ? RAIL : this.width;

        this.w(w);
        this.resizer.area.set(this.area.ex() - 4, this.area.y, 4, this.area.h);
        this.scroll.area.copy(this.area);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.tree == null)
        {
            return;
        }

        /* Folder colours are written once the picker closes, not on every drag step */
        if (this.colorDirty && (this.colorPicker == null || !this.colorPicker.hasParent()))
        {
            this.colorDirty = false;
            this.colorPicker = null;
            MorphLayoutStorage.save();
        }

        /* Save the width once the drag ends */
        if (this.wasResizing && !this.resizer.isDragging())
        {
            this.saveWidth();
        }

        this.wasResizing = this.resizer.isDragging();

        if (this.pressedNode != null && !mchorse.bbs_mod.graphics.window.Window.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            if (this.nodeDragging)
            {
                this.finishNodeDrag();
            }

            this.pressedNode = null;
        }

        this.updateNodeDrag(context);
        this.rebuildRows();
        this.scroll.drag(context.mouseX, context.mouseY);

        boolean rail = this.isRail();
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Gfx.deep(0.85F));

        this.scroll.scrollSize = this.rows.size() * ROW;
        this.scroll.clamp();

        if (this.pendingScroll >= 0)
        {
            this.scroll.setScroll(this.pendingScroll);
            this.pendingScroll = -1;
        }

        /* The drop target for dragged forms: the row under the cursor offering a modifiable
         * category. Reported here (the sidebar renders after the grids) so it isn't wiped.
         * When no row qualifies this frame, a target we set last frame has to go, or a
         * release over the drawer/empty space would still land in it. */
        Row dropRow = null;
        UIFormCategory dropTarget = null;

        if (this.list.drag.isActive() && this.area.isInside(context))
        {
            dropRow = this.rowEntryAt(context.mouseY);
            dropTarget = dropRow == null || dropRow.node == null ? null : this.dropTargetOf(dropRow.node);

            if (dropTarget != null)
            {
                this.list.drag.setTarget(dropTarget, dropTarget.getForms().size());
            }
            else
            {
                dropRow = null;
            }
        }

        if (this.lastDropTarget != null && dropTarget == null && this.list.drag.getTarget() == this.lastDropTarget)
        {
            this.list.drag.clearTarget();
        }

        this.lastDropTarget = dropTarget;

        batcher.clip(this.area, context);

        /* Sliding capsule for the effective selection (picked node or scroll highlight) */
        MorphNavTree.Node<UIFormCategory> effective = this.list.isFocusMode() ? this.selected : this.highlighted;
        MorphNavTree.Node<UIFormCategory> capsuleNode = this.visibleNodeFor(effective);
        int capsuleIndex = capsuleNode == null ? -1 : this.nodeRowIndex(capsuleNode);

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - this.lastFrame) / 1000F);

        this.lastFrame = now;

        if (capsuleIndex >= 0)
        {
            float targetY = this.rowY(capsuleIndex);

            if (this.capsuleY < 0)
            {
                this.capsuleY = targetY;
            }

            this.capsuleY = Gfx.approach(this.capsuleY, targetY, 900F, dt);

            Gfx.roundRect(batcher, this.area.x + 2, this.capsuleY + 1, this.area.w - 4, ROW - 2, 5, Gfx.accent(0.35F));
            batcher.box(this.area.x + 2, this.capsuleY + 1, this.area.x + 4, this.capsuleY + ROW - 1, Gfx.accent());
        }
        else
        {
            this.capsuleY = -1;
        }

        /* Rows set the sidebar tooltip only when they own it this frame */
        boolean tooltipSet = false;

        for (int i = 0; i < this.rows.size(); i++)
        {
            int y = this.rowY(i);

            if (y + ROW <= this.area.y || y >= this.area.ey())
            {
                continue;
            }

            tooltipSet |= this.renderRow(context, this.rows.get(i), y, rail, dropRow == this.rows.get(i), capsuleNode);
        }

        if (!tooltipSet && context.tooltip.element == this)
        {
            this.tooltip((mchorse.bbs_mod.ui.framework.tooltips.ITooltip) null);
            context.tooltip.set(context, null);
        }

        /* Drop indicator + ghost of the row being dragged */
        if (this.nodeDragging)
        {
            if (this.dropIntoNode != null)
            {
                int index = this.nodeRowIndex(this.dropIntoNode);

                if (index >= 0)
                {
                    int y = this.rowY(index);

                    Gfx.roundOutline(batcher, this.area.x + 1, y + 1, this.area.w - 2, ROW - 2, 5, 1, Gfx.accent(0.9F));
                    batcher.box(this.area.x, y, this.area.ex(), y + ROW, Gfx.accent(0.12F));
                }
            }
            else if (this.dropIndex >= 0)
            {
                int lineY = this.caretLineY();

                if (lineY >= this.area.y - 2 && lineY <= this.area.ey() + 2)
                {
                    int indent = this.dropParent == null ? 0 : this.rowDepthOf(this.dropParent) + 1;

                    batcher.box(this.area.x + 2 + UIList.ROW_PADDING + indent * INDENT, lineY - 1, this.area.ex() - 2, lineY + 1, Gfx.accent());
                }
            }

            /* Translucent ghost follows the cursor */
            if (this.pressedNode != null)
            {
                int gy = context.mouseY - ROW / 2;
                Icon icon = this.iconOf(this.pressedNode);
                int color = 0xAAFFFFFF;

                batcher.clip(this.area, context);
                Gfx.roundRect(batcher, this.area.x + 4, gy, this.area.w - 8, ROW, 5, Gfx.accent(0.25F));
                batcher.icon(icon, color, this.area.x + 14, gy + ROW / 2F, 0.5F, 0.5F);
                batcher.text(font.limitToWidth(this.pressedNode.label, this.area.w - 46), this.area.x + 26, gy + (ROW - font.getHeight()) / 2 + 1, color);
                batcher.unclip(context);
            }
        }

        batcher.unclip(context);

        this.scroll.renderScrollbar(batcher);

        int resizerColor = this.resizer.isDragging() || this.resizer.area.isInside(context) ? Gfx.accent() : Gfx.divider(0.6F);

        batcher.box(this.area.ex() - 1, this.area.y, this.area.ex(), this.area.ey(), resizerColor);

        super.render(context);
    }

    /** Nearest ancestor (or the node itself) that currently has a row. */
    private MorphNavTree.Node<UIFormCategory> visibleNodeFor(MorphNavTree.Node<UIFormCategory> node)
    {
        while (node != null && this.nodeRowIndex(node) == -1)
        {
            node = node.parent;
        }

        return node;
    }

    /** The modifiable category a drop on this node would go to — only the node's own. */
    private UIFormCategory dropTargetOf(MorphNavTree.Node<UIFormCategory> node)
    {
        for (UIFormCategory cat : node.payloads)
        {
            if (cat.category.canModify(null))
            {
                return cat;
            }
        }

        return null;
    }

    /** The y of the before/after caret inside the current drop parent. */
    private int caretLineY()
    {
        int seen = 0;

        for (int i = 0; i < this.rows.size(); i++)
        {
            Row row = this.rows.get(i);

            if (row.node == null || row.node == this.pressedNode || this.isDescendant(row.node, this.pressedNode))
            {
                continue;
            }

            if (row.node.parent == this.dropParent && seen++ == this.dropIndex)
            {
                return this.rowY(i);
            }
        }

        /* Past every child: the caret sits under the last row on screen */
        for (int i = this.rows.size() - 1; i >= 0; i--)
        {
            Row row = this.rows.get(i);

            if (row.node != null && row.node != this.pressedNode && !this.isDescendant(row.node, this.pressedNode))
            {
                return this.rowY(i) + ROW;
            }
        }

        return this.area.y;
    }

    private int rowDepthOf(MorphNavTree.Node<UIFormCategory> node)
    {
        int depth = 0;

        for (MorphNavTree.Node<UIFormCategory> n = node.parent; n != null; n = n.parent)
        {
            depth++;
        }

        return depth;
    }

    /** Renders one row; returns true when the row claimed the sidebar tooltip. */
    private boolean renderRow(UIContext context, Row row, int y, boolean rail, boolean dropTarget, MorphNavTree.Node<UIFormCategory> capsuleNode)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        boolean hovered = context.mouseX >= this.area.x && context.mouseX <= this.area.ex() && context.mouseY >= y && context.mouseY < y + ROW && this.area.isInside(context);

        if (row.newCategory)
        {
            if (hovered)
            {
                batcher.box(this.area.x, y, this.area.ex(), y + ROW, Gfx.wash(0.08F));
            }

            int x = this.area.x + UIList.ROW_PADDING + (row.depth + 1) * INDENT;

            batcher.icon(Icons.ADD, Gfx.inkMuted(0.9F), x + 8, y + ROW / 2F, 0.5F, 0.5F);
            batcher.text(L10n.lang("rabbit.ui.morph.new_category").get(), x + UIList.ICON_SLOT, y + (ROW - font.getHeight()) / 2 + 1, Gfx.inkMuted(0.9F));

            return false;
        }

        MorphNavTree.Node<UIFormCategory> node = row.node;
        int x = this.area.x + UIList.ROW_PADDING + row.depth * INDENT;
        int my = y + ROW / 2;
        int hits = this.subtreeHits(node);
        boolean dimmed = this.list.isSearching() && hits == 0;
        MorphLayout layout = MorphLayoutStorage.get();
        int tint = layout.colorOf(node);

        if (tint != 0 && capsuleNode == node)
        {
            /* The selection capsule keeps its own fill; the colour stays as the stripe */
            batcher.box(this.area.x, y, this.area.x + 3, y + ROW, tint | 0xFF000000);
        }
        else if (tint != 0)
        {
            renderTint(batcher, this.area.x, y, this.area.w, ROW, tint, layout.colors.containsKey(node.id));
        }

        if (hovered && capsuleNode != node)
        {
            batcher.box(this.area.x, y, this.area.ex(), y + ROW, Gfx.wash(0.08F));
        }

        if (dropTarget)
        {
            Gfx.roundOutline(batcher, this.area.x + 1, y + 1, this.area.w - 2, ROW - 2, 5, 1, Gfx.accent(0.9F));
            batcher.box(this.area.x, y, this.area.ex(), y + ROW, Gfx.accent(0.12F));
        }

        if (rail)
        {
            Icon icon = this.iconOf(node);
            int color = dimmed ? Gfx.inkMuted(0.35F) : Gfx.inkSoft(1F);

            if (capsuleNode == node)
            {
                color = Gfx.onAccent();
            }

            batcher.icon(icon, color, this.area.mx(), my, 0.5F, 0.5F);

            if (hovered)
            {
                this.tooltip(IKey.raw(node.label));
                context.tooltip.area.set(this.area.x, y, this.area.w, ROW);

                return true;
            }

            return false;
        }

        if (row.hasChildren)
        {
            UISection.renderArrow(context, x + UIList.ARROW_SLOT / 2F, my, this.isExpanded(node), dimmed ? Gfx.inkMuted(0.35F) : Gfx.inkMuted(0.9F));
        }

        int iconX = x + UIList.ARROW_SLOT + UIList.ICON_SLOT / 2;
        Icon icon = this.iconOf(node);
        int textColor = capsuleNode == node ? Gfx.onAccent() : (dimmed ? Gfx.inkMuted(0.35F) : Gfx.ink(1F));

        batcher.icon(icon, dimmed ? Gfx.inkMuted(0.35F) : (capsuleNode == node ? Gfx.onAccent() : Gfx.inkSoft(1F)), iconX, my, 0.5F, 0.5F);

        int textX = x + UIList.ARROW_SLOT + UIList.ICON_SLOT + 2;
        int textY = y + (ROW - font.getHeight()) / 2 + 1;
        String label = node.label;

        /* Count capsule on the right edge */
        String count = String.valueOf(hits);
        int countW = font.getWidth(count);
        int pillW = countW + 8;
        int pillX = this.area.ex() - 4 - pillW;

        int maxTextW = pillX - 4 - textX;
        String drawn = font.getWidth(label) > maxTextW ? font.limitToWidth(label, maxTextW) : label;
        boolean claimed = false;

        if (hovered && !drawn.equals(label))
        {
            this.tooltip(IKey.raw(label), Math.min(300, font.getWidth(label)), mchorse.bbs_mod.utils.Direction.TOP);
            context.tooltip.area.set(this.area.x, y, this.area.w, ROW);
            claimed = true;
        }

        batcher.textShadow(drawn, textX, textY, textColor);

        Gfx.roundRect(batcher, pillX, y + (ROW - 14) / 2F, pillW, 14, 7, dimmed ? Gfx.wash(0.05F) : Gfx.wash(0.10F));
        batcher.text(count, pillX + 4, y + (ROW - font.getHeight()) / 2 + 1, dimmed ? Gfx.inkMuted(0.35F) : Gfx.inkMuted(0.9F));

        return claimed;
    }

    private Icon iconOf(MorphNavTree.Node<UIFormCategory> node)
    {
        if (!node.payloads.isEmpty())
        {
            UIFormCategory cat = node.payloads.get(0);

            return MorphCategoryIconStorage.get(cat.category.visible.getId(), cat.category.icon);
        }

        return switch (node.id)
        {
            case "group:favorites" -> Icons.FAVORITE;
            case "group:recent" -> Icons.TIME;
            case "group:user" -> Icons.USER;
            case "group:models" -> Icons.POSE;
            case "group:mobs" -> Icons.CHICKEN;
            case "group:particles" -> Icons.PARTICLE;
            default -> Icons.FOLDER;
        };
    }
}
