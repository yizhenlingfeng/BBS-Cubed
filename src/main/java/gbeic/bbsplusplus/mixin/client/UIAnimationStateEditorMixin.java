package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.forms.AnimationFileAutoSyncSupport;
import gbeic.bbsplusplus.ui.forms.AnimationStateEditorSupport;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 动画状态关键帧编辑器增强。
 * <p>
 * BBS 2.6 已用 TrackCatalog 原生构建骨骼/IK 轨道行并用 FoldState 管理折叠，
 * 这里只挂接原生没有的右键能力（Pose 烘焙为逐骨骼肢体轨道、清空 IK 轨道）
 * 以及自定义布局的准备/挂接时机。
 */
@Mixin(value = UIAnimationStateEditor.class, remap = false)
public abstract class UIAnimationStateEditorMixin
{
    @Shadow
    public UIKeyframeEditor keyframeEditor;

    @Shadow
    public UIFormEditor editor;

    @Shadow
    private AnimationState state;

    @Unique
    private AnimationStateEditorSupport bbspp_cml$support;

    @Unique
    private AnimationFileAutoSyncSupport bbspp_cml$autoSync;

    @Unique
    private AnimationStateEditorSupport bbspp_cml$support()
    {
        if (this.bbspp_cml$support == null)
        {
            this.bbspp_cml$support = new AnimationStateEditorSupport(
                (UIAnimationStateEditor) (Object) this,
                this.editor,
                () -> this.keyframeEditor,
                () -> this.state
            );
        }

        return this.bbspp_cml$support;
    }

    @Unique
    private AnimationFileAutoSyncSupport bbspp_cml$autoSync()
    {
        if (this.bbspp_cml$autoSync == null)
        {
            this.bbspp_cml$autoSync = new AnimationFileAutoSyncSupport(
                (UIAnimationStateEditor) (Object) this,
                this.editor,
                () -> this.state
            );
        }

        return this.bbspp_cml$autoSync;
    }

    @Inject(method = "setState", at = @At("HEAD"), remap = false)
    private void bbspp_cml$beforeSetState(AnimationState nextState, CallbackInfo ci)
    {
        this.bbspp_cml$support().beforeSetState();
        /* 在轨道构建之前同步：新建状态并把动画烘焙进 pose 轨道，随后原生流程直接展示 */
        this.bbspp_cml$autoSync().onSetState(nextState);
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp_cml$autoSyncTick(UIContext context, CallbackInfo ci)
    {
        this.bbspp_cml$autoSync().tick();
    }

    @Inject(method = "setState", at = @At("TAIL"), remap = false)
    private void bbspp_cml$finishStateKeyframeSetup(AnimationState nextState, CallbackInfo ci)
    {
        this.bbspp_cml$support().finishStateKeyframeSetup(nextState);
    }
}
