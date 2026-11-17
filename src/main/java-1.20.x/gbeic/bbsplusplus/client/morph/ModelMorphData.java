package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一条 Model 关键帧的 Model Morph 配置。
 *
 * <p>它控制「当前 Model 关键帧 -&gt; 下一个 Model 关键帧」这一段的形变，
 * 不建立独立轨道。关闭时 BBS 原版模型切换逻辑完全不变。</p>
 *
 * <p>序列化使用 BBS 原生 {@link MapType}，作为关键帧数据里的一个子树保存，
 * 因此旧影片（没有这个子树）加载后默认关闭 Morph。</p>
 */
public final class ModelMorphData
{
    public static final int VERSION = 1;
    public static final String DATA_KEY = "bbsppp_model_morph";

    private int version = VERSION;
    private boolean enabled;
    private String sourceModel = "";
    private String targetModel = "";
    private final List<MappingGroup> groups = new ArrayList<>();
    private boolean textureSync = true;

    /** 默认允许带提示地播放不完整映射：未映射的源 Cube 保持原几何，不做静默丢弃。 */
    private boolean allowIncomplete = true;

    public boolean enabled()
    {
        return this.enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.invalidateHash();
        this.enabled = enabled;
    }

    public String sourceModel()
    {
        return this.sourceModel;
    }

    public void setSourceModel(String sourceModel)
    {
        this.invalidateHash();
        this.sourceModel = sourceModel == null ? "" : sourceModel;
    }

    public String targetModel()
    {
        return this.targetModel;
    }

    public void setTargetModel(String targetModel)
    {
        this.invalidateHash();
        this.targetModel = targetModel == null ? "" : targetModel;
    }

    public List<MappingGroup> groups()
    {
        return Collections.unmodifiableList(this.groups);
    }

    public void setGroups(List<MappingGroup> groups)
    {
        this.invalidateHash();
        this.groups.clear();

        if (groups != null)
        {
            for (MappingGroup group : groups)
            {
                if (group != null)
                {
                    this.groups.add(this.bindGroup(group.copy()));
                }
            }
        }
    }

    public void addGroup(MappingGroup group)
    {
        this.invalidateHash();
        if (group != null)
        {
            this.groups.add(this.bindGroup(group.copy()));
        }
    }

    public boolean textureSync()
    {
        return this.textureSync;
    }

    public void setTextureSync(boolean textureSync)
    {
        this.invalidateHash();
        this.textureSync = textureSync;
    }


    /**
     * 是否允许映射不完整时仍然播放形变。
     *
     * <p>默认开启：未映射的源 Cube 保持原几何继续渲染，缺目标的一侧压扁到中心，
     * 绝不会静默丢弃模型几何；关键帧面板同时给出完成度提示。
     * 关闭后不完整映射会安全回退到原版模型硬切换。</p>
     */
    public boolean allowIncomplete()
    {
        return this.allowIncomplete;
    }

    public void setAllowIncomplete(boolean allowIncomplete)
    {
        this.invalidateHash();
        this.allowIncomplete = allowIncomplete;
    }

    public ModelMorphData copy()
    {
        ModelMorphData copy = new ModelMorphData();

        copy.version = this.version;
        copy.enabled = this.enabled;
        copy.sourceModel = this.sourceModel;
        copy.targetModel = this.targetModel;
        copy.textureSync = this.textureSync;
        copy.allowIncomplete = this.allowIncomplete;
        for (MappingGroup group : this.groups)
        {
            copy.groups.add(copy.bindGroup(group.copy()));
        }

        return copy;
    }

    public MapType toData()
    {
        MapType map = new MapType();

        map.putInt("version", this.version);
        map.putBool("enabled", this.enabled);
        map.putString("source_model", this.sourceModel);
        map.putString("target_model", this.targetModel);
        map.putBool("texture_sync", this.textureSync);
        map.putBool("allow_incomplete", this.allowIncomplete);

        ListType groupsData = new ListType();

        for (MappingGroup group : this.groups)
        {
            groupsData.add(group.toData());
        }

        map.put("groups", groupsData);

        return map;
    }

    public static ModelMorphData fromData(BaseType data)
    {
        ModelMorphData result = new ModelMorphData();

        if (!(data instanceof MapType map))
        {
            return result;
        }

        result.version = Math.max(1, map.getInt("version", VERSION));
        result.enabled = map.getBool("enabled", false);
        result.sourceModel = map.getString("source_model", "");
        result.targetModel = map.getString("target_model", "");
        result.textureSync = map.getBool("texture_sync", true);
        result.allowIncomplete = map.getBool("allow_incomplete", true);

        ListType groupsData = map.getList("groups", new ListType());

        for (int i = 0; i < groupsData.size(); i++)
        {
            MappingGroup group = MappingGroup.fromData(groupsData.get(i));

            if (group != null)
            {
                result.groups.add(result.bindGroup(group));
            }
        }

        /* 兼容早期开发原型：映射曾以 JSON 字符串直接挂在关键帧上。 */
        if (result.groups.isEmpty() && map.has("mapping_json"))
        {
            String legacy = map.getString("mapping_json", "");

            for (MappingGroup group : LegacyMorphMapping.parse(legacy))
            {
                result.groups.add(result.bindGroup(group));
            }
        }

        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof ModelMorphData other))
        {
            return false;
        }

        return this.version == other.version
            && this.enabled == other.enabled
            && this.textureSync == other.textureSync
            && this.allowIncomplete == other.allowIncomplete
            && Objects.equals(this.sourceModel, other.sourceModel)
            && Objects.equals(this.targetModel, other.targetModel)
            && this.groups.equals(other.groups);
    }

    /** 缓存的哈希值；{@code 0} 表示需要重算。 */
    private transient int cachedHash;

    @Override

    /**
     * 结构哈希。
     *
     * <p>结果会缓存：这个方法在渲染热路径上被每帧调用（用于判断几何是否可复用），
     * 而它需要遍历全部映射组与其中的 Cube 引用。映射组较多时，
     * 每帧重算会产生可观的开销，表现为形变期间画面卡顿。</p>
     */
    public int hashCode()
    {
        if (this.cachedHash != 0)
        {
            return this.cachedHash;
        }

        this.cachedHash = Objects.hash(this.version, this.enabled, this.textureSync, this.allowIncomplete,
            this.sourceModel, this.targetModel, this.groups);

        /* 0 是「未计算」的哨兵值，真实哈希为 0 时改用 1 避免每次都重算。 */
        if (this.cachedHash == 0)
        {
            this.cachedHash = 1;
        }

        return this.cachedHash;
    }

    /**
     * 让缓存的哈希失效。
     *
     * <p>所有会改变结构的写入口都必须调用它，否则缓存会返回过期值。</p>
     */
    public void invalidateHash()
    {
        this.cachedHash = 0;
    }

    private MappingGroup bindGroup(MappingGroup group)
    {
        group.setMutationListener(this::invalidateHash);

        return group;
    }
}
