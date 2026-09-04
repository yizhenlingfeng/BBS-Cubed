package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * 由 {@code UIBaseMenu} mixin 实现,提供非模态小窗宿主层(位于 main 与 overlay 之间)。
 */
public interface FloatLayerAccess
{
    UIElement bbspp_cml$getFloatLayer();
}
