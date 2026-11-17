package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.presets.DataManager;


/**
 * 映射预设管理：与 {@code poses.json} 完全同构。
 *
 * <p>文件放在<b>源模型目录</b>同级：{@code assets/<models>/<模型>/morph_mapping.json}，
 * 结构是「预设名 → 映射数据」的字典，因此同一个源模型可以保存多套方案
 * （对应不同目标模型，甚至同一目标模型的多个变体）——正如 {@code poses.json}
 * 里一个模型可以有多个姿势预设。</p>
 *
 * <p>复用 BBS 原版 {@link DataManager}：读写、缓存、文件定位、JSON 序列化
 * 全部走同一套实现，行为与 Pose / IK 预设一致。</p>
 */
public final class MorphMappingPresets extends DataManager
{
    public static final MorphMappingPresets INSTANCE = new MorphMappingPresets();

    /** 预设文件名（与 poses.json 同级）。 */
    public static final String FILE = "morph_mapping.json";

    private MorphMappingPresets()
    {
    }

    @Override
    protected Link getFile(String group)
    {
        return Link.assets(ModelManager.MODELS_PREFIX + group + "/" + FILE);
    }

    /**
     * 读取某个预设。
     *
     * @return 预设数据；不存在返回 {@code null}
     */
    public static MapType read(String sourceModel, String preset)
    {
        if (sourceModel == null || sourceModel.isEmpty() || preset == null || preset.isEmpty())
        {
            return null;
        }

        MapType data = INSTANCE.getData(sourceModel);

        return data.has(preset) ? data.getMap(preset) : null;
    }

    /**
     * 保存一个预设。
     *
     * @return 是否成功
     */
    public static boolean write(String sourceModel, String preset, MapType mapping)
    {
        if (sourceModel == null || sourceModel.isEmpty() || preset == null || preset.isEmpty() || mapping == null)
        {
            return false;
        }

        INSTANCE.saveData(sourceModel, preset, mapping);

        return true;
    }

    public static boolean delete(String sourceModel, String preset)
    {
        if (sourceModel == null || sourceModel.isEmpty() || preset == null || preset.isEmpty())
        {
            return false;
        }

        MapType data = INSTANCE.getData(sourceModel);

        if (!data.has(preset))
        {
            return false;
        }

        data.remove(preset);
        INSTANCE.saveData(sourceModel, preset, null);

        return true;
    }
}
