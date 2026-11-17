package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * 读取早期开发原型保存的 JSON 字符串映射，避免用户在本地测试影片里留下的数据丢失。
 *
 * <p>正式版本不再让用户编辑 JSON；这里只做一次性导入。</p>
 */
public final class LegacyMorphMapping
{
    private LegacyMorphMapping()
    {
    }

    public static List<MappingGroup> parse(String json)
    {
        List<MappingGroup> groups = new ArrayList<>();

        if (json == null || json.isBlank())
        {
            return groups;
        }

        try
        {
            if (!(DataToString.fromString(json) instanceof MapType map))
            {
                return groups;
            }

            if (map.get("pairs") instanceof ListType pairs)
            {
                for (int i = 0; i < pairs.size(); i++)
                {
                    if (pairs.get(i) instanceof MapType entry)
                    {
                        String source = entry.getString("source", "");
                        String target = entry.getString("target", "");

                        if (!source.isEmpty() && !target.isEmpty())
                        {
                            MappingGroup group = new MappingGroup();

                            group.setSources(List.of(new MappingGroup.CubeRef(source, "")));
                            group.setTargets(List.of(new MappingGroup.CubeRef(target, "")));
                            groups.add(group);
                        }
                    }
                }
            }

            if (groups.isEmpty() && !map.has("pairs") && !map.has("groups"))
            {
                for (String key : map.keys())
                {
                    BaseType value = map.get(key);

                    if (value != null && value.isString())
                    {
                        MappingGroup group = new MappingGroup();

                        group.setSources(List.of(new MappingGroup.CubeRef(key, "")));
                        group.setTargets(List.of(new MappingGroup.CubeRef(value.asString(), "")));
                        groups.add(group);
                    }
                }
            }
        }
        catch (Exception ignored)
        {
            /* 旧草稿损坏时安全忽略，保持 Morph 关闭或由用户重新建立映射。 */
        }

        return groups;
    }
}
