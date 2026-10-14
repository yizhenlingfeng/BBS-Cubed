package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.BcpVisibility;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.2.1-Alpha → v1.3.2：「可视性设置」面板（overlay）。
 *
 * 从摄像机编辑器右键添加剪辑菜单中的「可视性设置」项打开。
 * 每个剪辑一行开关；列表固定 3 列网格排列，单元格间距翻倍（8px）。
 * 调整即时生效并写盘；每次筛选后列表自动重新排列（可见剪辑排前，隐藏剪辑沉底）；
 * 底部「返回」关闭面板。
 */
@Environment(EnvType.CLIENT)
public class BcpVisibilityOverlayPanel extends UIOverlayPanel {
    /** 固定列数 */
    private static final int COLUMNS = 3;
    /** 网格间距（px），v1.3.2 由 4 翻倍到 8 */
    private static final int GRID_GAP = 8;

    private final UIScrollView scroll;

    public BcpVisibilityOverlayPanel() {
        super(L10n.lang("bbs.ui.camera.visibility_settings"));

        // 剪辑列表（3 列网格 + 可滚动）
        this.scroll = UI.scrollView(4, 6);
        this.scroll.relative(this.content).xy(0, 0).w(1.0F).h(1.0F, -32);
        this.content.add(this.scroll);

        // 返回按钮
        UIButton back = new UIButton(L10n.lang("bbs.ui.camera.panels.back"), b -> this.close());
        back.relative(this.content).x(1.0F, -90).y(1.0F, -26).w(80).h(20);
        this.content.add(back);

        this.rebuild();
    }

    /** 重建列表（初始构建 / 筛选调整后自动重排） */
    private void rebuild() {
        this.scroll.removeAll();

        UIElement grid = new UIElement();
        grid.relative(this.scroll).w(1.0F);
        GridResizer.apply(grid, GRID_GAP).items(COLUMNS);

        MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
        if (factory != null) {
            // 筛选后自动重排：可见剪辑排前，隐藏剪辑沉底
            List<Link> keys = new ArrayList<>();
            for (Link key : factory.getKeys()) {
                keys.add(key);
            }
            keys.sort((a, b) -> Boolean.compare(BcpVisibility.getVisible(b), BcpVisibility.getVisible(a)));

            for (Link key : keys) {
                UIToggle toggle = new UIToggle(
                    UIKeys.C_CLIP.get(key),
                    BcpVisibility.getVisible(key),
                    t -> {
                        BcpVisibility.setVisible(key, t.getValue());
                        this.rebuild();
                    }
                );
                toggle.relative(grid).w(1.0F).h(20);
                grid.add(toggle);
            }
        }

        this.scroll.add(grid);
        this.scroll.resize();
    }
}
