package routu.bbsuirabbit.ui.morphing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The morphing panel's sidebar tree: which form categories sit under which groups and
 * folders, in which order. Pure data — no Minecraft classes — so the layout can be unit
 * tested. A node can carry categories ({@link Node#payloads}) AND have children, e.g.
 * "models" holds the root-level model categories and one child per folder.
 */
public class MorphNavTree<T>
{
    public enum Kind
    {
        FAVORITES, RECENT, USER, MODEL, MOB, EXTRA, PARTICLE, OTHER,
        /** A virtual folder's own category of filed forms; {@code folder} is the vf id. */
        VIRTUAL
    }

    /**
     * One category handed to the tree. {@code folder} only means something for MODEL and
     * PARTICLE entries ("" = the group's own root); everyone else passes "".
     */
    public record Entry<T>(String categoryId, Kind kind, String folder, T payload)
    {}

    public static class Node<T>
    {
        public String id;
        public String label;
        public Node<T> parent;
        public final List<Node<T>> children = new ArrayList<>();
        /** The first category hanging on this node, or null. */
        public T payload;
        /** Every category hanging on this node, in attach order. */
        public final List<T> payloads = new ArrayList<>();
        /** The parent {@link #build} assigned ("" for roots) — never rewritten by a layout. */
        public String naturalParentId = "";

        public boolean isLeaf()
        {
            return this.children.isEmpty();
        }

        /** Chain of group labels from the top node down to this one ("models/a/b"). */
        public String path()
        {
            List<String> parts = new ArrayList<>();

            for (Node<T> n = this; n != null; n = n.parent)
            {
                if (n.id.startsWith("group:") || n.id.startsWith("vf:"))
                {
                    parts.add(0, n.label);
                }
            }

            return String.join("/", parts);
        }
    }

    private final List<Node<T>> roots = new ArrayList<>();
    private final Map<String, Node<T>> byId = new LinkedHashMap<>();
    private MorphLayout layout;
    private final Map<String, Node<T>> byCategory = new LinkedHashMap<>();
    private final Map<T, Node<T>> byPayload = new IdentityHashMap<>();
    /** VIRTUAL entries, hung on their folder once {@link #applyLayout} made it. */
    private final List<Entry<T>> virtual = new ArrayList<>();

    public List<Node<T>> roots()
    {
        return this.roots;
    }

    /** Every payload in sidebar order: a node's own categories before its children (DFS). */
    public List<T> order()
    {
        List<T> out = new ArrayList<>();

        for (Node<T> root : this.roots)
        {
            this.collect(root, out);
        }

        return out;
    }

    /** The node's payloads plus every descendant's, DFS order. */
    public List<T> subtree(Node<T> node)
    {
        List<T> out = new ArrayList<>();

        this.collect(node, out);

        return out;
    }

    private void collect(Node<T> node, List<T> out)
    {
        out.addAll(node.payloads);

        for (Node<T> child : node.children)
        {
            this.collect(child, out);
        }
    }

    public Node<T> find(String nodeId)
    {
        return this.byId.get(nodeId);
    }

    /** The nodes of a subtree — itself first, descendants DFS. */
    public List<Node<T>> nodesOf(Node<T> node)
    {
        List<Node<T>> out = new ArrayList<>();

        collectNodes(node, out);

        return out;
    }

    private void collectNodes(Node<T> node, List<Node<T>> out)
    {
        out.add(node);

        for (Node<T> child : node.children)
        {
            this.collectNodes(child, out);
        }
    }

    /** The node or an ancestor was hidden via the layout overlay. */
    public boolean isHidden(Node<T> node)
    {
        if (this.layout == null)
        {
            return false;
        }

        for (Node<T> n = node; n != null; n = n.parent)
        {
            if (this.layout.hidden.contains(n.id))
            {
                return true;
            }
        }

        return false;
    }

    /** The node a payload hangs on. */
    public Node<T> nodeOf(T payload)
    {
        return this.byPayload.get(payload);
    }

    /** The node a category id ended up on — group node for attached ones. */
    public Node<T> findByCategoryId(String categoryId)
    {
        return this.byCategory.get(categoryId);
    }

    public int count(Node<T> node, ToIntFunction<T> counter)
    {
        int sum = 0;

        for (T payload : this.subtree(node))
        {
            sum += counter.applyAsInt(payload);
        }

        return sum;
    }

    /**
     * Builds the tree. {@code label} translates the group keys "favorites", "recent",
     * "user", "models", "mobs", "extra", "particles" and "packs" (the last for the "cem"
     * folder segment). Leaf nodes are labelled with their category id; callers that know
     * the category can overwrite {@link Node#label} afterwards.
     */
    public static <T> MorphNavTree<T> build(List<Entry<T>> entries, Function<String, String> label)
    {
        MorphNavTree<T> tree = new MorphNavTree<>();

        List<Entry<T>> favorites = byKind(entries, Kind.FAVORITES);
        List<Entry<T>> recent = byKind(entries, Kind.RECENT);
        List<Entry<T>> users = byKind(entries, Kind.USER);
        List<Entry<T>> models = byKind(entries, Kind.MODEL);
        List<Entry<T>> mobs = byKind(entries, Kind.MOB);
        List<Entry<T>> extras = byKind(entries, Kind.EXTRA);
        List<Entry<T>> particles = byKind(entries, Kind.PARTICLE);
        List<Entry<T>> others = byKind(entries, Kind.OTHER);

        tree.virtual.addAll(byKind(entries, Kind.VIRTUAL));

        if (!favorites.isEmpty())
        {
            Node<T> node = tree.group(null, "favorites", label.apply("favorites"));

            tree.attachAll(node, favorites);
        }

        if (!recent.isEmpty())
        {
            Node<T> node = tree.group(null, "recent", label.apply("recent"));

            tree.attachAll(node, recent);
        }

        if (!users.isEmpty())
        {
            Node<T> node = tree.group(null, "user", label.apply("user"));

            tree.leaves(node, users);
        }

        if (!models.isEmpty())
        {
            tree.folders(null, "models", label.apply("models"), models, label);
        }

        if (!mobs.isEmpty())
        {
            Node<T> node = tree.group(null, "mobs", label.apply("mobs"));

            tree.leaves(node, mobs);
        }

        for (Entry<T> entry : extras)
        {
            tree.leaf(null, entry);
        }

        if (!particles.isEmpty())
        {
            tree.folders(null, "particles", label.apply("particles"), particles, label);
        }

        for (Entry<T> entry : others)
        {
            tree.leaf(null, entry);
        }

        return tree;
    }

    private static <T> List<Entry<T>> byKind(List<Entry<T>> entries, Kind kind)
    {
        List<Entry<T>> out = new ArrayList<>();

        for (Entry<T> entry : entries)
        {
            if (entry.kind() == kind)
            {
                out.add(entry);
            }
        }

        return out;
    }

    /** A group node "group:name" directly under {@code parent} (or at the top). */
    private Node<T> group(Node<T> parent, String name, String label)
    {
        String id = (parent == null ? "group:" : parent.id + "/") + name;
        Node<T> node = new Node<>();

        node.id = id;
        node.label = label;
        node.parent = parent;

        this.register(node);

        if (parent == null)
        {
            this.roots.add(node);
        }
        else
        {
            parent.children.add(node);
        }

        return node;
    }

    private void register(Node<T> node)
    {
        this.byId.put(node.id, node);
    }

    /** Hangs the entries' categories on the node itself. */
    private void attachAll(Node<T> node, List<Entry<T>> entries)
    {
        for (Entry<T> entry : entries)
        {
            node.payloads.add(entry.payload());

            if (node.payload == null)
            {
                node.payload = entry.payload();
            }

            this.byCategory.put(entry.categoryId(), node);
            this.byPayload.put(entry.payload(), node);
        }
    }

    /** One "cat:<id>" leaf child per entry, keeping input order. */
    private void leaves(Node<T> parent, List<Entry<T>> entries)
    {
        for (Entry<T> entry : entries)
        {
            this.leaf(parent, entry);
        }
    }

    private void leaf(Node<T> parent, Entry<T> entry)
    {
        Node<T> node = new Node<>();

        node.id = "cat:" + entry.categoryId();
        node.label = entry.categoryId();
        node.parent = parent;

        this.register(node);

        if (parent == null)
        {
            this.roots.add(node);
        }
        else
        {
            parent.children.add(node);
        }

        node.payloads.add(entry.payload());
        node.payload = entry.payload();

        this.byCategory.put(entry.categoryId(), node);
        this.byPayload.put(entry.payload(), node);
    }

    /**
     * A folder group ("models", "particles"): entries whose folder is "" hang on the group
     * node, the rest build one child node per path segment, so "a/b" nests under "a" even
     * when "a" itself has no category. Children are sorted by segment name, ignoring case.
     */
    private void folders(Node<T> parent, String name, String label, List<Entry<T>> entries, Function<String, String> labelFn)
    {
        Node<T> group = this.group(parent, name, label);
        Map<String, Node<T>> folders = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>();

        for (Entry<T> entry : entries)
        {
            String folder = entry.folder() == null ? "" : entry.folder();

            if (folder.isEmpty())
            {
                this.attachAll(group, List.of(entry));

                continue;
            }

            /* Materialize every segment of the path, so a gap still gets a plain group node */
            String[] segments = folder.split("/");
            StringBuilder path = new StringBuilder();

            for (String segment : segments)
            {
                String parentPath = path.toString();

                if (path.length() > 0)
                {
                    path.append('/');
                }

                path.append(segment);

                String full = path.toString();

                if (!folders.containsKey(full))
                {
                    Node<T> node = new Node<>();

                    node.id = "group:" + name + "/" + full;
                    node.label = segment.equals("cem") ? labelFn.apply("packs") : segment;
                    node.parent = parentPath.isEmpty() ? group : folders.get(parentPath);

                    this.register(node);
                    node.parent.children.add(node);
                    folders.put(full, node);
                    paths.add(full);
                }
            }

            this.attachAll(folders.get(folder), List.of(entry));
        }

        /* Every folder node sorts its children by the last segment's name, ignoring case */
        Comparator<Node<T>> bySegment = Comparator.comparing(n -> segmentOf(n.id), String.CASE_INSENSITIVE_ORDER);

        group.children.sort(bySegment);

        for (String p : paths)
        {
            folders.get(p).children.sort(bySegment);
        }
    }

    private static String segmentOf(String nodeId)
    {
        int slash = nodeId.lastIndexOf('/');

        return slash >= 0 ? nodeId.substring(slash + 1) : nodeId;
    }

    /**
     * Applies the display overlay: virtual folders appear, re-parented nodes move, stored
     * child orders win over the natural ones. Anything pointing at a missing node or an
     * impossible target is left stored but ignored, so a later rebuild can pick it up.
     */
    public void applyLayout(MorphLayout layout)
    {
        this.layout = layout;

        /* Natural parents must be recorded before anything moves */
        for (Node<T> root : this.roots)
        {
            this.recordNatural(root, "");
        }

        /* Virtual folders start at the top; their parent entries move them below */
        for (Map.Entry<String, String> e : layout.folders.entrySet())
        {
            if (this.byId.containsKey(e.getKey()))
            {
                continue;
            }

            Node<T> node = new Node<>();

            node.id = e.getKey();
            node.label = e.getValue();
            node.naturalParentId = "";

            this.register(node);
            this.roots.add(node);
        }

        /* A folder's filed forms hang on the folder itself, before its child nodes */
        for (Entry<T> entry : this.virtual)
        {
            Node<T> folder = this.byId.get(entry.folder());

            if (folder != null && folder.id.startsWith("vf:"))
            {
                this.attachAll(folder, List.of(entry));
            }
        }

        for (Map.Entry<String, String> e : layout.parents.entrySet())
        {
            Node<T> node = this.byId.get(e.getKey());

            if (node == null)
            {
                continue;
            }

            Node<T> target = e.getValue().isEmpty() ? null : this.byId.get(e.getValue());

            if (!e.getValue().isEmpty() && target == null)
            {
                continue;
            }

            if (target != null && (!MorphLayout.canContain(target.id) || target == node || this.isAncestor(node, target)))
            {
                continue;
            }

            if (node.parent == null)
            {
                this.roots.remove(node);
            }
            else
            {
                node.parent.children.remove(node);
            }

            node.parent = target;

            if (target == null)
            {
                this.roots.add(node);
            }
            else
            {
                target.children.add(node);
            }
        }

        for (Map.Entry<String, List<String>> e : layout.orders.entrySet())
        {
            List<Node<T>> children = e.getKey().isEmpty() ? this.roots : (this.byId.get(e.getKey()) == null ? null : this.byId.get(e.getKey()).children);

            if (children == null)
            {
                continue;
            }

            List<Node<T>> first = new ArrayList<>();

            for (String id : e.getValue())
            {
                Node<T> child = this.byId.get(id);

                if (child != null && children.contains(child) && !first.contains(child))
                {
                    first.add(child);
                }
            }

            if (first.isEmpty())
            {
                continue;
            }

            List<Node<T>> rest = new ArrayList<>(children);

            rest.removeAll(first);
            children.clear();
            children.addAll(first);
            children.addAll(rest);
        }
    }

    private void recordNatural(Node<T> node, String parentId)
    {
        node.naturalParentId = parentId;

        for (Node<T> child : node.children)
        {
            this.recordNatural(child, node.id);
        }
    }

    private boolean isAncestor(Node<T> ancestor, Node<T> node)
    {
        for (Node<T> n = node; n != null; n = n.parent)
        {
            if (n == ancestor)
            {
                return true;
            }
        }

        return false;
    }
}
