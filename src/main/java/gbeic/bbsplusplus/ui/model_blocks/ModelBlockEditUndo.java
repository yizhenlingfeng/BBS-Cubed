package gbeic.bbsplusplus.ui.model_blocks;

import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.undo.IUndo;

/**
 * 模型方块的单条撤销记录，采用全量快照：保存修改前后的 {@link MapType}，
 * undo/redo 时通过 {@link ModelProperties#fromData(MapType)} 恢复。
 * 1 秒内的连续编辑自动合并为一条记录。
 */
public class ModelBlockEditUndo implements IUndo<ModelProperties>
{
    private static final long MERGE_WINDOW_MS = 1000;

    private final MapType before;
    private MapType after;
    private final long timestamp;
    private boolean mergeable = true;

    public ModelBlockEditUndo(MapType before, MapType after)
    {
        this.before = before;
        this.after = after;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public IUndo<ModelProperties> noMerging()
    {
        this.mergeable = false;
        return this;
    }

    @Override
    public boolean isMergeable(IUndo<ModelProperties> undo)
    {
        return this.mergeable
            && undo instanceof ModelBlockEditUndo other
            && (other.timestamp - this.timestamp) < MERGE_WINDOW_MS;
    }

    @Override
    public void merge(IUndo<ModelProperties> undo)
    {
        if (undo instanceof ModelBlockEditUndo other)
        {
            this.after = other.after;
        }
    }

    @Override
    public void undo(ModelProperties context)
    {
        context.fromData(this.before);
    }

    @Override
    public void redo(ModelProperties context)
    {
        context.fromData(this.after);
    }

    @Override
    public String toString()
    {
        return "Edit model block";
    }
}
