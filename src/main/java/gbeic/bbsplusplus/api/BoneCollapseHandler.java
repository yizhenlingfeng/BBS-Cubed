package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * 由 {@code UIKeyframeDopeSheetMixin} 实现的 duck 接口 —— 把"骨骼折叠箭头
 * 点击命中处理"暴露给 {@code UIKeyframesMixin} 在事件链上游调用。
 *
 * <p>为什么在上游：BBS-PoseCurve-Addon 在 dope sheet 的 {@code mouseClicked}
 * HEAD 把整个标签行点击改成打开曲线编辑并 cancel，同层用 mixin priority
 * 竞争顺序不可靠；而 {@code UIKeyframes.subMouseClicked} 是
 * {@code graph.mouseClicked} 的调用者，在这里检测必然先于任何 dope sheet
 * 层的拦截。</p>
 */
public interface BoneCollapseHandler
{
    /**
     * 若光标命中某个骨骼行的折叠箭头则切换折叠并返回 true（调用方应吞掉该次点击）。
     */
    boolean bbspp_cml$handleCollapseClick(UIContext context);
}
