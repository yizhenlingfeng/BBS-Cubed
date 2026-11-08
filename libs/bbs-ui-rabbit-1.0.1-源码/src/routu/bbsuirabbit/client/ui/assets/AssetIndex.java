package routu.bbsuirabbit.client.ui.assets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The folder tree and listing logic of the asset workbenches (models, particles, audio).
 * Pure data — no Minecraft classes — so it can be unit tested.
 */
public final class AssetIndex
{
    public enum Sort
    {
        NAME, RECENT
    }

    public static final class Folder
    {
        public String path;
        public String name;
        public Folder parent;
        public final List<Folder> children = new ArrayList<>();
        /** Ids sitting directly in this folder. */
        public final List<String> ids = new ArrayList<>();
    }

    private final Folder root;
    private final Map<String, Folder> byPath = new LinkedHashMap<>();
    private final List<String> all = new ArrayList<>();

    private AssetIndex(Folder root)
    {
        this.root = root;
    }

    public Folder root()
    {
        return this.root;
    }

    public Folder find(String path)
    {
        if (path == null || path.isEmpty())
        {
            return this.root;
        }

        return this.byPath.get(path);
    }

    /** Ids under a folder, counting every descendant. */
    public int count(String path)
    {
        Folder folder = this.find(path);

        return folder == null ? 0 : count(folder);
    }

    private int count(Folder folder)
    {
        int n = folder.ids.size();

        for (Folder child : folder.children)
        {
            n += count(child);
        }

        return n;
    }

    /**
     * The ids under {@code path} (recursive), filtered and sorted for display: {@code query}
     * is a case-insensitive substring on the full id; {@code favoritesOnly} keeps ids in
     * {@code favorites}; RECENT ordering puts the {@code recentOrder} sequence first and
     * names the rest.
     */
    public List<String> list(String path, String query, Sort sort, List<String> recentOrder, Set<String> favorites, boolean favoritesOnly)
    {
        Folder folder = this.find(path);
        List<String> out = new ArrayList<>();

        if (folder == null)
        {
            return out;
        }

        String q = query == null ? "" : query.trim().toLowerCase();

        collect(folder, out);

        out.removeIf((id) ->
        {
            if (favoritesOnly && (favorites == null || !favorites.contains(id)))
            {
                return true;
            }

            return !q.isEmpty() && !id.toLowerCase().contains(q);
        });

        Comparator<String> byName = Comparator.comparing(AssetIndex::displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.naturalOrder());

        if (sort == Sort.RECENT && recentOrder != null && !recentOrder.isEmpty())
        {
            Map<String, Integer> rank = new LinkedHashMap<>();

            for (int i = 0; i < recentOrder.size(); i++)
            {
                rank.putIfAbsent(recentOrder.get(i), i);
            }

            out.sort((a, b) ->
            {
                Integer ra = rank.get(a);
                Integer rb = rank.get(b);

                if (ra != null && rb != null)
                {
                    return ra - rb;
                }

                if (ra != null) return -1;
                if (rb != null) return 1;

                return byName.compare(a, b);
            });
        }
        else
        {
            out.sort(byName);
        }

        return out;
    }

    private void collect(Folder folder, List<String> out)
    {
        out.addAll(folder.ids);

        for (Folder child : folder.children)
        {
            this.collect(child, out);
        }
    }

    /** The file's last segment without its extension ("particles/fire/big.json" → "big"). */
    public static String displayName(String id)
    {
        int slash = id == null ? -1 : id.lastIndexOf('/');
        String name = slash >= 0 ? id.substring(slash + 1) : id;
        int dot = name == null ? -1 : name.lastIndexOf('.');

        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Builds the tree: '/' splits paths, empty segments are skipped, sibling folders sort by
     * name ignoring case, the root's path is "".
     */
    public static AssetIndex build(Collection<String> ids)
    {
        AssetIndex index = new AssetIndex(new Folder());

        index.root.path = "";
        index.root.name = "";
        index.byPath.put("", index.root);

        if (ids == null)
        {
            return index;
        }

        for (String id : ids)
        {
            if (id == null || id.isEmpty())
            {
                continue;
            }

            index.all.add(id);
            index.folderOf(id).ids.add(id);
        }

        index.sortChildren(index.root);

        return index;
    }

    private Folder folderOf(String id)
    {
        String[] segments = id.split("/");
        StringBuilder path = new StringBuilder();
        Folder folder = this.root;

        for (int i = 0; i < segments.length - 1; i++)
        {
            String segment = segments[i];

            if (segment.isEmpty())
            {
                continue;
            }

            if (path.length() > 0)
            {
                path.append('/');
            }

            path.append(segment);

            String full = path.toString();
            Folder next = this.byPath.get(full);

            if (next == null)
            {
                next = new Folder();
                next.path = full;
                next.name = segment;
                next.parent = folder;

                folder.children.add(next);
                this.byPath.put(full, next);
            }

            folder = next;
        }

        return folder;
    }

    private void sortChildren(Folder folder)
    {
        folder.children.sort(Comparator.comparing(f -> f.name, String.CASE_INSENSITIVE_ORDER));

        for (Folder child : folder.children)
        {
            this.sortChildren(child);
        }
    }
}
