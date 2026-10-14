package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * v1.2.4-Alpha：自适应多列右键菜单的单元格。
 *
 * 复用 BBS 的 {@link ContextAction#render} 绘制（图标 + 文本 + 悬停高亮 / 彩色标注），
 * 点击后执行动作并关闭当前右键菜单。
 */
@Environment(EnvType.CLIENT)
public class GridActionCell extends UIElement {
    public final ContextAction action;

    public GridActionCell(ContextAction action) {
        this.action = action;
    }

    @Override
    public void render(UIContext context) {
        super.render(context);
        this.action.render(
            context,
            context.batcher.getFont(),
            this.area.x, this.area.y, this.area.w, this.area.h,
            this.area.isInside(context),
            false
        );
    }

    @Override
    public boolean subMouseClicked(UIContext context) {
        if (this.area.isInside(context)) {
            this.action.runnable.run();
            if (this.getContext() != null && this.getContext().contextMenu != null) {
                this.getContext().contextMenu.dismiss();
            }
            return true;
        }
        return super.subMouseClicked(context);
    }
}
