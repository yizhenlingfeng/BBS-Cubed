package gbeic.bbsplusplus.client.ui.morph;

import gbeic.bbsplusplus.client.morph.MappingGroup;
import gbeic.bbsplusplus.client.morph.ModelMorphData;
import gbeic.bbsplusplus.client.morph.MorphMappingPresets;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

/**
 * 右侧的映射卡片列表。
 *
 * <p>每行显示一条映射组：类型与源、以及目标。行文本与行颜色的规则与编辑器无关，
 * 通过 {@link Host} 拿到当前模型与数据、以及撤销/变更回调。</p>
 */
final class MappingList extends UIList<MappingGroup>
{
    private static final int ROW_H = UIStringList.DEFAULT_HEIGHT;

    /** 主面板向列表暴露的数据与操作。 */
    interface Host
    {
        String sourceModel();

        ModelMorphData data();

        void pushUndo();

        /** 用一个载入的映射数据整体替换当前组列表（ctrl+V 预设后调用）。 */
        void loadPreset(ModelMorphData loaded);

        void onPicked(List<MappingGroup> rows);
    }

    private final Host host;

    MappingList(Host host)
    {
        super(host::onPicked);

        this.host = host;
        this.scroll.scrollItemSize = ROW_H;
        this.background();

        /* 右键菜单直接使用 BBS 原版 UIDataContextMenu：
           这正是 poses.json 用的那套组件——文件放在源模型同级，
           内含「预设名 → 映射数据」字典，因此同一源模型可以保存多套方案。 */
        this.context(() -> new UIDataContextMenu(
            MorphMappingPresets.INSTANCE,
            host.sourceModel(),
            () -> host.data().toData(),
            (mapping) ->
            {
                ModelMorphData loaded = ModelMorphData.fromData(mapping);

                host.pushUndo();
                host.loadPreset(loaded);
            }));
    }

    /**
     * 映射行的显示文本。
     *
     * <p>单行放不下 1:N 的完整目标列表（会被截断成 {@code [a, b, c...}），
     * 因此改为两行：首行是类型与源，次行是目标。映射表列宽较窄，
     * 两行比横向挤压更易读。</p>
     */
    @Override
    protected String elementToString(UIContext context, int i, MappingGroup element)
    {
        StringBuilder builder = new StringBuilder();

        if (!element.isSupported())
        {
            builder.append("! ");
        }

        builder.append(element.type().label()).append("  ");

        int sources = element.sources().size();
        int targets = element.targets().size();

        /* 多源时在首行标出数量，避免用户以为只有第一个源。 */
        builder.append(describe(element.sources()));

        if (sources > 1)
        {
            builder.append(" (").append(sources).append(')');
        }

        builder.append("\n  -> ").append(describe(element.targets()));

        if (targets > 1)
        {
            builder.append(" (").append(targets).append(')');
        }

        return builder.toString();
    }

    private String describe(List<MappingGroup.CubeRef> refs)
    {
        if (refs.isEmpty())
        {
            return "-";
        }

        StringBuilder builder = new StringBuilder();
        boolean bracket = refs.size() > 1;

        if (bracket)
        {
            builder.append('[');
        }

        for (int i = 0; i < refs.size(); i++)
        {
            if (i > 0)
            {
                builder.append(", ");
            }

            builder.append(MorphMappingSelection.readableRef(refs.get(i).key()));
        }

        if (bracket)
        {
            builder.append(']');
        }

        return builder.toString();
    }

    /** 不支持的映射组（历史数据里的 N:N / 空组）用红色左边条标出，避免它们看起来像正常数据。 */
    @Override
    protected int rowColor(MappingGroup element)
    {
        /* RowStyle 只在颜色带 alpha 时才画左边条，因此必须补上不透明度 */
        return element.isSupported() ? 0 : (Colors.A100 | Colors.RED);
    }
}
