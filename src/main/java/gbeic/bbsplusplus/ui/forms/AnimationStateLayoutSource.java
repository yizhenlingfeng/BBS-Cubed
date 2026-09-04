package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.BBSFSloveCMLClient;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Persistent layout source for the animation-state editor's reusable dock.
 * BBS 2.5 的 ILayoutSource 契约:树 + 隐藏面板集都由 source 提供,树不可变,
 * 分隔条拖动经 setRoot 回写;getHiddenPanels 必须返回副本(dock 会原位改写后回传)。
 */
public class AnimationStateLayoutSource implements ILayoutSource
{
    public static final String PREVIEW = "statePreview";
    public static final String TIMELINE = "stateTimeline";
    public static final String INSPECTOR = "stateInspector";

    /** 默认布局横向分割:预览面板占编辑区宽度的 72%,其余归右侧的时间线/检查器一列。 */
    private static final float PREVIEW_WIDTH_RATIO = 0.72F;

    /** 默认布局右列纵向分割:时间线占该列高度的 72%,其余归下方的检查器。 */
    private static final float TIMELINE_HEIGHT_RATIO = 0.72F;

    private EditorLayoutNode root;
    private Set<String> hidden = new HashSet<>();

    public AnimationStateLayoutSource()
    {
        this.root = this.loadRoot();
        this.hidden = this.loadHidden();
    }

    public static EditorLayoutNode createDefault()
    {
        return new EditorLayoutNode.SplitterNode(
            true,
            PREVIEW_WIDTH_RATIO,
            new EditorLayoutNode.PanelNode(PREVIEW),
            new EditorLayoutNode.SplitterNode(
                false,
                TIMELINE_HEIGHT_RATIO,
                new EditorLayoutNode.PanelNode(TIMELINE),
                new EditorLayoutNode.PanelNode(INSPECTOR)
            )
        );
    }

    private EditorLayoutNode loadRoot()
    {
        String value = CMLSettings.animationStateLayout == null ? null : CMLSettings.animationStateLayout.get();

        if (value == null || value.isBlank())
        {
            return createDefault();
        }

        try
        {
            EditorLayoutNode layout = EditorLayoutNode.fromData(DataToString.mapFromString(value));

            return layout == null ? createDefault() : layout;
        }
        catch (Throwable t)
        {
            /* 配置里的布局串可能来自旧版本或被手工改坏,解析失败退回默认布局即可,不影响编辑器可用性。 */
            BBSFSloveCMLClient.LOGGER.debug("[AnimationStateLayout] 布局配置解析失败,回退默认布局", t);

            return createDefault();
        }
    }

    private Set<String> loadHidden()
    {
        String value = CMLSettings.animationStateHiddenPanels == null ? null : CMLSettings.animationStateHiddenPanels.get();

        if (value == null || value.isBlank())
        {
            return new HashSet<>();
        }

        try
        {
            Set<String> ids = new HashSet<>();

            for (var id : DataToString.listFromString(value))
            {
                if (id != null && id.isString() && !id.asString().isEmpty())
                {
                    ids.add(id.asString());
                }
            }

            return ids;
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.debug("[AnimationStateLayout] 隐藏面板配置解析失败,按无隐藏处理", t);

            return new HashSet<>();
        }
    }

    public void save()
    {
        this.saveRoot();
        this.saveHidden();
    }

    private void saveRoot()
    {
        if (CMLSettings.animationStateLayout == null || this.root == null)
        {
            return;
        }

        String serialized = DataToString.toString(this.root.toData(), false);

        if (!serialized.equals(CMLSettings.animationStateLayout.get()))
        {
            CMLSettings.animationStateLayout.set(serialized);
        }
    }

    private void saveHidden()
    {
        if (CMLSettings.animationStateHiddenPanels == null)
        {
            return;
        }

        String serialized = "";

        if (!this.hidden.isEmpty())
        {
            ListType list = new ListType();

            for (String id : this.hidden)
            {
                list.addString(id);
            }

            serialized = DataToString.toString(list, false);
        }

        if (!serialized.equals(CMLSettings.animationStateHiddenPanels.get()))
        {
            CMLSettings.animationStateHiddenPanels.set(serialized);
        }
    }

    @Override
    public EditorLayoutNode getRoot()
    {
        return this.root;
    }

    @Override
    public void setRoot(EditorLayoutNode root)
    {
        this.root = root == null ? createDefault() : root;
        this.saveRoot();
    }

    @Override
    public EditorLayoutNode getDefault()
    {
        return createDefault();
    }

    @Override
    public Set<String> getHiddenPanels()
    {
        return new HashSet<>(this.hidden);
    }

    @Override
    public void setHiddenPanels(Set<String> hidden)
    {
        this.hidden = hidden == null ? new HashSet<>() : new HashSet<>(hidden);
        this.saveHidden();
    }
}
