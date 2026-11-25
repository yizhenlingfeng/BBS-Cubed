package gbeic.bbsplusplus.client.ui.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphData;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 映射编辑器的撤销/重做栈。
 *
 * <p>所有改动入口都应在修改数据<b>之前</b>调用 {@link #push()} 保存快照，否则撤销会跳过那一步。
 * 快照是 {@link ModelMorphData#copy()} 的深拷贝，与当前编辑的数据互不影响。</p>
 */
final class MorphMappingHistory
{
    private static final int LIMIT = 64;

    /** 与编辑器交换当前数据的最小接口。 */
    interface Host
    {
        ModelMorphData data();

        void apply(ModelMorphData snapshot);
    }

    private final Host host;
    private final Deque<ModelMorphData> undoStack = new ArrayDeque<>();
    private final Deque<ModelMorphData> redoStack = new ArrayDeque<>();

    MorphMappingHistory(Host host)
    {
        this.host = host;
    }

    void push()
    {
        this.undoStack.push(this.host.data().copy());
        this.redoStack.clear();

        while (this.undoStack.size() > LIMIT)
        {
            this.undoStack.removeLast();
        }
    }

    void undo()
    {
        if (this.undoStack.isEmpty())
        {
            return;
        }

        this.redoStack.push(this.host.data().copy());
        this.host.apply(this.undoStack.pop());
    }

    void redo()
    {
        if (this.redoStack.isEmpty())
        {
            return;
        }

        this.undoStack.push(this.host.data().copy());
        this.host.apply(this.redoStack.pop());
    }

    boolean canUndo()
    {
        return !this.undoStack.isEmpty();
    }

    boolean canRedo()
    {
        return !this.redoStack.isEmpty();
    }

    void clear()
    {
        this.undoStack.clear();
        this.redoStack.clear();
    }
}
