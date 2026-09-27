package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.model_blocks.ModelBlockEditUndo;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.utils.undo.UndoManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为模型方块预览主页（{@link UIModelBlockPanel}）添加撤回撤销。
 * 原版该面板完全没有 undo/redo，所有修改直接写入 {@link ModelProperties} 且不保存快照。
 *
 * <p>方案：全量快照对比。加载方块时保存基线，在鼠标释放、切换方块、关闭面板时
 * 对比当前状态与基线，有变化则推入 {@link UndoManager}。undo/redo 恢复快照后
 * 刷新 UI 并同步服务端。</p>
 */
@Mixin(value = UIModelBlockPanel.class, remap = true)
public abstract class UIModelBlockPanelUndoMixin
{
    @Shadow private ModelBlockEntity modelBlock;
    @Shadow public UIPropTransform transform;

    @Shadow private void fillData() {}
    @Shadow private void save(ModelBlockEntity modelBlock) {}

    @Unique private UndoManager<ModelProperties> bbspp$undoManager;
    @Unique private MapType bbspp$baseline;
    @Unique private boolean bbspp$undoing;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbspp$initUndo(UIDashboard dashboard, CallbackInfo ci)
    {
        this.bbspp$undoManager = new UndoManager<>(100);

        UIModelBlockPanel self = (UIModelBlockPanel)(Object)this;
        self.add(new UIUndoKeys(this::bbspp$undo, this::bbspp$redo).full(self));
    }

    /** 切换方块前提交当前方块的修改，避免丢失。 */
    @Inject(method = "fill", at = @At("HEAD"))
    private void bbspp$onFillHead(ModelBlockEntity modelBlock, boolean select, CallbackInfo ci)
    {
        if (!this.bbspp$undoing) this.bbspp$checkpoint();
    }

    /** 加载方块后保存基线快照。 */
    @Inject(method = "fill", at = @At("RETURN"))
    private void bbspp$onFillReturn(ModelBlockEntity modelBlock, boolean select, CallbackInfo ci)
    {
        if (!this.bbspp$undoing) this.bbspp$baseline = this.bbspp$snapshot();
    }

    /** 鼠标释放时 checkpoint：覆盖 Gizmo 拖拽与控件操作结束。 */
    @Inject(method = "subMouseReleased", at = @At("RETURN"))
    private void bbspp$onMouseReleased(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.bbspp$undoing) this.bbspp$checkpoint();
    }

    /** 关闭面板前 checkpoint，兜底键盘输入等非鼠标修改。 */
    @Inject(method = "saveTouchedBlocks", at = @At("HEAD"))
    private void bbspp$onSaveTouchedBlocks(CallbackInfo ci)
    {
        if (!this.bbspp$undoing) this.bbspp$checkpoint();
    }

    @Unique
    private void bbspp$undo()
    {
        this.bbspp$applyUndo(false);
    }

    @Unique
    private void bbspp$redo()
    {
        this.bbspp$applyUndo(true);
    }

    private void bbspp$applyUndo(boolean redo)
    {
        if (this.modelBlock == null || this.bbspp$undoManager == null) return;

        if (!redo) this.bbspp$checkpoint();

        this.bbspp$undoing = true;
        try
        {
            ModelProperties properties = this.modelBlock.getProperties();
            boolean applied = redo
                ? this.bbspp$undoManager.redo(properties)
                : this.bbspp$undoManager.undo(properties);

            if (applied)
            {
                this.bbspp$refreshUI();
                this.save(this.modelBlock);
                this.bbspp$baseline = this.bbspp$snapshot();
            }
        }
        finally
        {
            this.bbspp$undoing = false;
        }
    }

    /** 对比当前状态与基线，有变化则推入撤销栈。 */
    @Unique
    private void bbspp$checkpoint()
    {
        if (this.modelBlock == null || this.bbspp$baseline == null) return;

        MapType current = this.bbspp$snapshot();
        if (!current.equals(this.bbspp$baseline))
        {
            this.bbspp$undoManager.pushUndo(new ModelBlockEditUndo(this.bbspp$baseline, current));
            this.bbspp$baseline = current;
        }
    }

    @Unique
    private MapType bbspp$snapshot()
    {
        MapType snapshot = new MapType();
        if (this.modelBlock != null)
        {
            this.modelBlock.getProperties().toData(snapshot);
        }
        return snapshot;
    }

    /**
     * 刷新 UI。必须先调用 {@link UIPropTransform#gestureStopped()} 重置 {@code hasFilled}，
     * 否则 setTransform() 的 "nothing moved" 快捷路径会跳过刷新，导致数据已恢复但数值不同步。
     */
    @Unique
    private void bbspp$refreshUI()
    {
        if (this.transform != null) this.transform.gestureStopped();
        this.fillData();
    }
}
