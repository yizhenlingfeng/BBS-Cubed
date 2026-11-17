package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一条 Cube 映射组。
 *
 * <p>源集合与目标集合一起保存，因此 1→N 克隆分化和 N→1 完整汇聚都是一条记录，
 * 而不是拆成多条互相独立的 1→1 关系。映射类型由两侧数量自动推导，用户不需要选择。</p>
 */
public final class MappingGroup
{
    public enum Type
    {
        ONE_TO_ONE("1:1"),
        ONE_TO_MANY("1:N"),
        MANY_TO_ONE("N:1"),
        MANY_TO_MANY("N:N"),
        EMPTY("-");

        private final String label;

        Type(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return this.label;
        }

        public boolean isSupported()
        {
            return this == ONE_TO_ONE || this == ONE_TO_MANY || this == MANY_TO_ONE;
        }
    }

    /**
     * 稳定的 Cube 引用：完整骨骼路径 + Cube 名/序号，附带几何指纹用于模型资源变化后的有效性检查。
     * 不依赖 Cube 数组下标。
     */
    public record CubeRef(String key, String signature)
    {
        public CubeRef
        {
            key = key == null ? "" : key;
            signature = signature == null ? "" : signature;
        }
    }

    private String id;
    private final List<CubeRef> sources = new ArrayList<>();
    private final List<CubeRef> targets = new ArrayList<>();
    private Runnable mutationListener = () -> {};

    public MappingGroup()
    {
        this(UUID.randomUUID().toString());
    }

    public MappingGroup(String id)
    {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
    }

    public String id()
    {
        return this.id;
    }

    public List<CubeRef> sources()
    {
        return Collections.unmodifiableList(this.sources);
    }

    public List<CubeRef> targets()
    {
        return Collections.unmodifiableList(this.targets);
    }

    public void setSources(List<CubeRef> refs)
    {
        replace(this.sources, refs);
        this.mutationListener.run();
    }

    public void setTargets(List<CubeRef> refs)
    {
        replace(this.targets, refs);
        this.mutationListener.run();
    }

    void setMutationListener(Runnable mutationListener)
    {
        this.mutationListener = mutationListener == null ? () -> {} : mutationListener;
    }

    private static void replace(List<CubeRef> destination, List<CubeRef> source)
    {
        destination.clear();

        if (source == null)
        {
            return;
        }

        for (CubeRef ref : source)
        {
            if (ref != null && !ref.key().isEmpty() && !contains(destination, ref.key()))
            {
                destination.add(ref);
            }
        }
    }

    private static boolean contains(List<CubeRef> refs, String key)
    {
        for (CubeRef ref : refs)
        {
            if (ref.key().equals(key))
            {
                return true;
            }
        }

        return false;
    }

    public Type type()
    {
        int sourceCount = this.sources.size();
        int targetCount = this.targets.size();

        if (sourceCount == 0 || targetCount == 0)
        {
            return Type.EMPTY;
        }

        if (sourceCount == 1 && targetCount == 1)
        {
            return Type.ONE_TO_ONE;
        }

        if (sourceCount == 1)
        {
            return Type.ONE_TO_MANY;
        }

        if (targetCount == 1)
        {
            return Type.MANY_TO_ONE;
        }

        return Type.MANY_TO_MANY;
    }

    public boolean isSupported()
    {
        return this.type().isSupported();
    }

    public MappingGroup copy()
    {
        MappingGroup copy = new MappingGroup(this.id);

        copy.sources.addAll(this.sources);
        copy.targets.addAll(this.targets);

        return copy;
    }

    public MapType toData()
    {
        MapType map = new MapType();

        map.putString("id", this.id);
        map.put("sources", writeRefs(this.sources));
        map.put("targets", writeRefs(this.targets));

        return map;
    }

    public static MappingGroup fromData(BaseType data)
    {
        if (!(data instanceof MapType map))
        {
            return null;
        }

        MappingGroup group = new MappingGroup(map.getString("id", ""));

        group.setSources(readRefs(map.get("sources")));
        group.setTargets(readRefs(map.get("targets")));

        return group;
    }

    private static ListType writeRefs(List<CubeRef> refs)
    {
        ListType list = new ListType();

        for (CubeRef ref : refs)
        {
            MapType entry = new MapType();

            entry.putString("key", ref.key());
            entry.putString("signature", ref.signature());
            list.add(entry);
        }

        return list;
    }

    private static List<CubeRef> readRefs(BaseType data)
    {
        List<CubeRef> refs = new ArrayList<>();

        if (data instanceof ListType list)
        {
            for (int i = 0; i < list.size(); i++)
            {
                BaseType element = list.get(i);

                if (element instanceof MapType map)
                {
                    refs.add(new CubeRef(map.getString("key", ""), map.getString("signature", "")));
                }
                else if (element != null && element.isString())
                {
                    refs.add(new CubeRef(element.asString(), ""));
                }
            }
        }

        return refs;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof MappingGroup other))
        {
            return false;
        }

        return Objects.equals(this.id, other.id)
            && this.sources.equals(other.sources)
            && this.targets.equals(other.targets);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.id, this.sources, this.targets);
    }
}
