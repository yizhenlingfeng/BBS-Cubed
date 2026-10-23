package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * {@link UIKeyframeDopeSheet} 的访问器：轨道排序拖动（{@link UIKeyframeDopeSheetMixin}）
 * 需要读取行 Y 缓存、判断行可见性与折叠箭头命中，并在重排后刷新滚动缓存。
 */
@Mixin(value = UIKeyframeDopeSheet.class, remap = false)
public interface UIKeyframeDopeSheetAccessor
{
    @Accessor(value = "sheetYCache", remap = false)
    Map<UIKeyframeSheet, Integer> bbspp$getSheetYCache();

    @Accessor(value = "sectionYCache", remap = false)
    Map<UIKeyframeSheet.Section, Integer> bbspp$getSectionYCache();

    @Invoker(value = "isVisible", remap = false)
    boolean bbspp$isVisible(UIKeyframeSheet sheet);

    @Invoker(value = "isFoldToggleHit", remap = false)
    boolean bbspp$isFoldToggleHit(UIContext context, UIKeyframeSheet sheet, int y, int labelWidth);

    @Invoker(value = "updateScrollSize", remap = false)
    void bbspp$updateScrollSize();
}
