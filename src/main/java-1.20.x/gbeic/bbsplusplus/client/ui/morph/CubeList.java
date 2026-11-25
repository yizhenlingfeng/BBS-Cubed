package gbeic.bbsplusplus.client.ui.morph;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.colors.Colors;
import gbeic.bbsplusplus.client.morph.ModelMorphSkeleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 源/目标一侧的 Cube 树。
 *
 * <p>把两侧模型的骨骼/Cube 层级压平成一个可选列表：骨骼行是分支、Cube 行是叶子，
 * 由缩进与展开箭头表现层级。列表本身不持有映射数据，行底色所需的
 * 「冲突 / 选中 / 已映射」三组集合通过 {@link Host} 向主面板查询。</p>
 */
final class CubeList extends UIList<CubeRow>
{
    private static final int ROW_H = UIStringList.DEFAULT_HEIGHT;

    /** 主面板向列表暴露的行状态；避免列表反向依赖编辑器的全部细节。 */
    interface Host
    {
        Set<String> invalidKeys(boolean sourceSide);

        Set<String> selectedKeys(boolean sourceSide);

        Set<String> mappedKeys(boolean sourceSide);
    }

    private final ModelMorphSkeleton skeleton;
    private final boolean sourceSide;
    private final Host host;
    private final Map<String, Boolean> expanded = new LinkedHashMap<>();
    private String query = "";

    CubeList(ModelMorphSkeleton skeleton, boolean sourceSide, Host host, Runnable callback)
    {
        super((rows) -> callback.run());

        this.skeleton = skeleton;
        this.sourceSide = sourceSide;
        this.host = host;
        this.scroll.scrollItemSize = ROW_H;
        this.multi();
        this.background();

        if (skeleton != null)
        {
            this.rebuild();
        }
    }

    /* ---- 构建与搜索 ---- */

    private void rebuild()
    {
        Set<String> keep = new LinkedHashSet<>(this.pickedCubeKeys());
        List<CubeRow> rows = new ArrayList<>();

        if (this.skeleton != null)
        {
            for (ModelMorphSkeleton.GroupSpec group : this.skeleton.groups())
            {
                /* 跳过不含 Cube 的组：它们只承载层级关系，用户无法对它们做任何映射操作，
                   列出来只会淹没真正可选的 Cube（大型模型里这类空组往往占绝大多数）。 */
                if (group.cubes().isEmpty())
                {
                    continue;
                }

                int depth = MorphMappingSelection.depthOf(group.path());

                /* 搜索时强制展开：命中的 Cube 若藏在折叠分支里，用户会以为没搜到。
                 * 折叠箭头也一并按展开状态绘制，否则箭头与可见子行会自相矛盾。 */
                boolean open = this.isOpen(group.path()) || !this.query.isEmpty();
                CubeRow bone = CubeRow.bone(group, depth, open);

                rows.add(bone);

                if (open)
                {
                    for (ModelMorphSkeleton.CubeSpec cube : group.cubes())
                    {
                        rows.add(CubeRow.cube(group, cube, depth + 1));
                    }
                }
            }
        }

        this.deselect();
        this.setList(rows);
        this.restore(keep);
    }

    /**
     * 搜索时强制展开所有骨骼：否则命中的 Cube 可能藏在折叠分支里，用户会以为没搜到。
     */
    @Override
    public void filter(String filter)
    {
        String text = filter == null ? "" : filter;

        if (!text.equals(this.query))
        {
            this.query = text;
            this.rebuild();
        }

        super.filter(text);
    }

    private boolean isOpen(String path)
    {
        Boolean open = this.expanded.get(path);

        return open == null || open;
    }

    private void restore(Set<String> keep)
    {
        if (keep.isEmpty())
        {
            return;
        }

        List<CubeRow> picked = new ArrayList<>();

        for (CubeRow row : this.getList())
        {
            if (!row.bone() && keep.contains(row.key()))
            {
                picked.add(row);
            }
            else if (row.bone() && !row.cubes().isEmpty() && keep.containsAll(row.cubes()))
            {
                picked.add(row);
            }
        }

        if (!picked.isEmpty())
        {
            this.setCurrent(picked);
        }
    }

    /* ---- 选择语义 ---- */

    /**
     * 点击骨骼行时整块骨骼一起选：映射通常按骨骼组织（一条腿、一段机身），
     * 逐个 Cube 点选在这种场景下太慢。再点一次则整组取消。
     */
    @Override
    protected void applySelectionOnClick(CubeRow item, int index)
    {
        if (!item.bone())
        {
            super.applySelectionOnClick(item, index);

            return;
        }

        List<CubeRow> group = new ArrayList<>();

        group.add(item);

        for (CubeRow row : this.getList())
        {
            if (!row.bone() && row.path().equals(item.path()))
            {
                group.add(row);
            }
        }

        boolean allPicked = true;

        for (CubeRow row : group)
        {
            /* 直接用 selection 判断：current.contains(index) 内部走 identity，
               与 indexOf 的 equals 口径混用是脆弱写法。 */
            if (!this.selection.contains(row))
            {
                allPicked = false;

                break;
            }
        }

        if (allPicked)
        {
            this.deselect();
        }
        else
        {
            this.setCurrent(group);
        }
    }

    /**
     * 按 Cube key 选中对应行，并把它滚进视野。
     *
     * <p>必须自己滚动：{@code setCurrent} 完全不触碰 {@code scroll}，
     * 而树的行数远多于可见行数，选中行常在视口外——用户会以为「点了没反应」。</p>
     *
     * @param additive Ctrl 语义：切换该行的选中状态，保留其它已选行
     * @param range    Shift 语义：从当前锚点范围选择到该行
     * @return 是否命中
     */
    boolean pickByKey(String cubeKey, boolean additive, boolean range)
    {
        if (cubeKey == null || cubeKey.isEmpty())
        {
            this.deselect();

            return false;
        }

        /* 过滤时 visible 下标与 backing 下标不一致，而滚动数学只对未过滤列表成立。 */
        if (!this.query.isEmpty())
        {
            this.filter("");
        }

        int index = this.indexOfCube(cubeKey);

        /* 命中行可能藏在折叠的骨骼下：展开、重建，再找一次。 */
        if (index < 0 && this.expandFor(cubeKey))
        {
            index = this.indexOfCube(cubeKey);
        }

        if (index < 0)
        {
            this.deselect();

            return false;
        }

        /* 与 BBS 原版 UIList#applySelectionOnClick 的语义保持一致：
           Shift 范围选择、Ctrl 切换、否则单选。 */
        if (range)
        {
            this.selection.range(this.getList().get(index), null, this.visible());
            this.scrollIntoView(index);
            this.fireCallback();
        }
        else if (additive)
        {
            this.toggleIndex(index);
            this.scrollIntoView(index);
            this.fireCallback();
        }
        else
        {
            /* pick(int) 内部已 fireCallback()。 */
            this.pick(index);
            this.scrollIntoView(index);
        }

        return true;
    }

    /**
     * 选中一组 Cube key 对应的行（供映射行联动使用）。
     *
     * <p>先清过滤与折叠：命中行若藏在折叠的骨骼下，用户会以为没选中。</p>
     */
    void pickByKeys(Collection<String> cubeKeys)
    {
        if (cubeKeys == null || cubeKeys.isEmpty())
        {
            this.deselect();

            return;
        }

        if (!this.query.isEmpty())
        {
            this.filter("");
        }

        List<CubeRow> rows = this.getList();
        List<CubeRow> picked = new ArrayList<>();

        for (CubeRow row : rows)
        {
            if (!row.bone() && cubeKeys.contains(row.key()))
            {
                picked.add(row);
            }
        }

        /* 命中行可能藏在折叠的骨骼下：展开相关骨骼后重建，再找一次。 */
        if (picked.isEmpty())
        {
            boolean expandedAny = false;

            for (CubeRow row : rows)
            {
                if (row.bone())
                {
                    for (String key : cubeKeys)
                    {
                        if (row.cubes().contains(key))
                        {
                            this.expanded.put(row.path(), Boolean.TRUE);
                            expandedAny = true;

                            break;
                        }
                    }
                }
            }

            if (expandedAny)
            {
                this.rebuild();

                for (CubeRow row : this.getList())
                {
                    if (!row.bone() && cubeKeys.contains(row.key()))
                    {
                        picked.add(row);
                    }
                }
            }
        }

        if (picked.isEmpty())
        {
            this.deselect();

            return;
        }

        this.setCurrent(picked);
        this.scrollIntoView(this.getList().indexOf(picked.get(0)));
    }

    private int indexOfCube(String cubeKey)
    {
        List<CubeRow> rows = this.getList();

        for (int i = 0; i < rows.size(); i++)
        {
            CubeRow row = rows.get(i);

            if (!row.bone() && row.key().equals(cubeKey))
            {
                return i;
            }
        }

        return -1;
    }

    private boolean expandFor(String cubeKey)
    {
        for (CubeRow row : this.getList())
        {
            if (row.bone() && row.cubes().contains(cubeKey))
            {
                this.expanded.put(row.path(), Boolean.TRUE);
                this.rebuild();

                return true;
            }
        }

        return false;
    }

    Set<String> pickedCubeKeys()
    {
        Set<String> keys = new LinkedHashSet<>();

        for (CubeRow row : this.getCurrent())
        {
            if (row.bone())
            {
                keys.addAll(row.cubes());
            }
            else
            {
                keys.add(row.key());
            }
        }

        return keys;
    }

    /* ---- 树表现 ---- */

    @Override
    protected int indent(CubeRow element)
    {
        return element.depth() * this.indentStep();
    }

    @Override
    protected int indentStep()
    {
        return 10;
    }

    @Override
    protected Boolean branch(CubeRow element)
    {
        return element.bone() ? Boolean.valueOf(element.open()) : null;
    }

    @Override
    protected void toggle(CubeRow element)
    {
        if (!element.bone())
        {
            return;
        }

        this.expanded.put(element.path(), !element.open());
        this.rebuild();
    }

    @Override
    protected String elementToString(UIContext context, int i, CubeRow element)
    {
        return element.text();
    }

    /**
     * 先让基类画行底色（条纹与选中高亮），再叠一层映射状态色。
     *
     * <p>映射状态色在文字之前绘制，因此不会盖住文字；红色优先级最高，
     * 因为冲突/失效是必须处理的问题，而「已映射」只是信息。</p>
     */
    @Override
    public void renderListElement(UIContext context, CubeRow element, int i, int x, int y, boolean hover, boolean selected)
    {
        int height = this.scroll.scrollItemSize;

        RowStyle.row(context.batcher, x, y, this.area.w, height, 0, false, hover, selected);

        int tint = this.tint(element);

        if (tint != 0)
        {
            context.batcher.box(x, y, x + this.area.w, y + height, tint);
        }

        this.renderElementPart(context, element, i, x, y, hover, selected);
    }

    private int tint(CubeRow row)
    {
        Set<String> invalid = this.host.invalidKeys(this.sourceSide);
        Set<String> selectedKeys = this.host.selectedKeys(this.sourceSide);
        Set<String> mapped = this.host.mappedKeys(this.sourceSide);
        List<String> keys = row.bone() ? row.cubes() : List.of(row.key());

        if (MorphMappingSelection.containsAny(invalid, keys))
        {
            return Colors.A50 | Colors.RED;
        }

        if (MorphMappingSelection.containsAny(selectedKeys, keys))
        {
            return Colors.A50 | BBSSettings.primaryColor.get();
        }

        if (MorphMappingSelection.containsAny(mapped, keys))
        {
            return Colors.A25 | BBSSettings.primaryColor.get();
        }

        return 0;
    }
}
