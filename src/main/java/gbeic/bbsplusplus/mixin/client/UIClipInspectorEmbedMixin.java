package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.UIReplayKeyframeEmbedHelper;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 效果面板(inspector)在"选中变化"后会消失的修复(新 Bug2)。
 *
 * <p>根因:UIFilmPanelMiniWindowMixin 的 embed 只在 editArea 浮动那一刻执行一次,把 inspector
 * reparent 进 editArea 小窗。但选中剪辑/关键帧/replay 时,原生重建 inspector 的方法会用
 * {@code this.add(inspector)} 把 inspector 重新挂回 UIClipsPanel(主编辑器小窗内),
 * 覆盖了 embed 的 reparent → inspector 渲染子树与 flex 参考跨小窗 → 不显示。</p>
 *
 * <p>此 mixin 在 pickClip 重建点的 TAIL 重新 embed:命中 PANEL_EDIT 已浮动条件时,把新 inspector
 * reparent 回 editArea 小窗。</p>
 */
@Mixin(value = UIClipsPanel.class, remap = false)
public abstract class UIClipInspectorEmbedMixin
{
    @Inject(method = "pickClip(Lmchorse/bbs_mod/utils/clips/Clip;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$reembedAfterPickClip(Clip clip, CallbackInfo ci)
    {
        UIReplayKeyframeEmbedHelper.reembedClipInspector((UIClipsPanel) (Object) this);
    }
}
