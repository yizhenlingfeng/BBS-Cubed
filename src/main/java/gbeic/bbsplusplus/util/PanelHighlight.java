package gbeic.bbsplusplus.util;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * BBS 2.6 移除了 UIDashboardPanels.renderHighlight，这里按 2.5 的原实现复刻
 * 底边高亮（2px 实色条 + 向上淡出的纵向渐变），供迷你窗标签与预览按钮使用。
 */
public class PanelHighlight
{
    public static void bottom(Batcher2D batcher, Area area)
    {
        int color = BBSSettings.primaryColor.get();
        int bar = Colors.A100 | color;
        int near = Colors.A75 | color;
        int far = color;
        int t = 2;

        batcher.box(area.x, area.ey() - t, area.ex(), area.ey(), bar);
        batcher.gradientVBox(area.x, area.y, area.ex(), area.ey() - t, far, near);
    }
}
