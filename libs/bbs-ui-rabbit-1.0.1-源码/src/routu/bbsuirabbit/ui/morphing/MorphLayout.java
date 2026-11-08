package routu.bbsuirabbit.ui.morphing;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The display-only overlay for the morphing sidebar: re-parented nodes, custom child
 * order, virtual folders and hidden nodes. Pure data — nothing here touches a real file
 * path, so a layout can never break a particle texture or a model link.
 */
public class MorphLayout
{
    /** nodeId → parent nodeId ("" = top level), only for nodes away from their natural parent. */
    public final Map<String, String> parents = new LinkedHashMap<>();

    /** parentId ("" = roots) → ordered child node ids; unlisted children keep natural order. */
    public final Map<String, List<String>> orders = new LinkedHashMap<>();

    /** Virtual folder id ("vf:…") → label; its parent lives in {@link #parents} (missing = root). */
    public final Map<String, String> folders = new LinkedHashMap<>();

    /** Node ids hidden by the user (descendants follow). */
    public final Set<String> hidden = new LinkedHashSet<>();

    /**
     * Single forms filed into a virtual folder: vf id → ordered form refs ("model:&lt;key&gt;").
     * A ref lives in at most one folder; its own category stops listing it while the folder
     * exists. Display only, like everything here — the model never moves on disk.
     */
    public final Map<String, List<String>> pins = new LinkedHashMap<>();

    /** Node id → RGB tint (opaque ARGB) the user gave a folder or one of their categories. */
    public final Map<String, Integer> colors = new LinkedHashMap<>();

    /** Bumped on every pin change, so cached category views know to re-filter. */
    public static int pinVersion;

    /** The vf a ref is filed into, or null — only while that folder still exists. */
    public String pinnedFolder(String ref)
    {
        for (Map.Entry<String, List<String>> e : this.pins.entrySet())
        {
            if (e.getValue().contains(ref))
            {
                return this.folders.containsKey(e.getKey()) ? e.getKey() : null;
            }
        }

        return null;
    }

    /** Files the ref into the folder at {@code index} (clamped), taking it out of any other. */
    public void pin(String ref, String vfId, int index)
    {
        this.unpin(ref);

        List<String> refs = this.pins.computeIfAbsent(vfId, (k) -> new java.util.ArrayList<>());

        refs.add(Math.max(0, Math.min(index, refs.size())), ref);
        pinVersion++;
    }

    /** Sends the ref back to its own category. */
    public void unpin(String ref)
    {
        java.util.Iterator<Map.Entry<String, List<String>>> it = this.pins.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<String, List<String>> e = it.next();

            if (e.getValue().remove(ref) && e.getValue().isEmpty())
            {
                it.remove();
            }
        }

        pinVersion++;
    }

    /** Whether the user may tint the node: any sidebar node — folders, groups and categories alike. */
    public static boolean canColor(String nodeId)
    {
        return nodeId.startsWith("vf:") || nodeId.startsWith("group:") || nodeId.startsWith("cat:");
    }

    /** The tint a node shows: its own, else the nearest tinted ancestor's; 0 = none. */
    public int colorOf(MorphNavTree.Node<?> node)
    {
        for (MorphNavTree.Node<?> n = node; n != null; n = n.parent)
        {
            Integer color = this.colors.get(n.id);

            if (color != null)
            {
                return color;
            }
        }

        return 0;
    }

    public static String newFolderId()
    {
        return "vf:" + UUID.randomUUID();
    }

    /** Whether a node may hold children (virtual folders and groups except the two fixed ones). */
    public static boolean canContain(String nodeId)
    {
        if (nodeId.equals("group:favorites") || nodeId.equals("group:recent"))
        {
            return false;
        }

        return nodeId.startsWith("group:") || nodeId.startsWith("vf:");
    }

    /**
     * Removes the virtual folder: its children move to the vf's parent at the vf's slot.
     * The child's parent override is kept only when it still differs from the natural one.
     * Only stored data changes — the live tree rebuild picks the result up.
     */
    public void dissolve(MorphNavTree<?> tree, String vfId)
    {
        MorphNavTree.Node<?> vf = tree.find(vfId);

        if (vf == null || !this.folders.containsKey(vfId))
        {
            return;
        }

        String parentId = vf.parent == null ? "" : vf.parent.id;
        List<String> childIds = new java.util.ArrayList<>();

        for (MorphNavTree.Node<?> child : vf.children)
        {
            childIds.add(child.id);
            String natural = child.naturalParentId == null ? "" : child.naturalParentId;

            if (natural.equals(parentId))
            {
                this.parents.remove(child.id);
            }
            else
            {
                this.parents.put(child.id, parentId);
            }
        }

        /* The parent keeps its child list with the vf replaced by its own children. Seed
         * from the live tree — a missing orders entry means "natural order", not "empty". */
        List<String> siblings = new java.util.ArrayList<>();

        if (this.orders.containsKey(parentId))
        {
            siblings.addAll(this.orders.get(parentId));
        }
        else
        {
            java.util.List<? extends MorphNavTree.Node<?>> children = vf.parent == null ? tree.roots() : vf.parent.children;

            for (MorphNavTree.Node<?> child : children)
            {
                siblings.add(child.id);
            }
        }

        int slot = siblings.indexOf(vfId);

        siblings.remove(vfId);
        siblings.addAll(slot < 0 ? siblings.size() : slot, childIds);
        this.orders.put(parentId, siblings);

        /* Filed forms follow into a parent folder; under anything else they go home */
        List<String> filed = this.pins.remove(vfId);

        if (filed != null && parentId.startsWith("vf:"))
        {
            this.pins.computeIfAbsent(parentId, (k) -> new java.util.ArrayList<>()).addAll(filed);
        }

        pinVersion++;

        this.folders.remove(vfId);
        this.colors.remove(vfId);
        this.parents.remove(vfId);
        this.orders.remove(vfId);
        this.hidden.remove(vfId);
    }
}
