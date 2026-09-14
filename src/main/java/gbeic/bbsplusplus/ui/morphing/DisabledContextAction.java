package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 右键菜单里「置灰不可点」的条目：文字与图标画成灰色，点击不执行任何动作。
 *
 * <p>用于 Blockbench 路径未配置 / 模型不支持时，让用户能看到这个选项存在但知道当前不可用。
 * 实现上把 runnable 传 null——{@code UISimpleContextMenu} 在 runnable 为 null 时不会触发点击。</p>
 */
public class DisabledContextAction extends ContextAction
{
    public DisabledContextAction(Icon icon, IKey label)
    {
        super(icon, label, null);
    }

    @Override
    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        /* 不画 hover 高亮背景 */
        context.batcher.icon(this.icon, x + 2, y + h / 2, 0, 0.5F);
        context.batcher.text(this.label.get(), x + 22, y + (h - font.getHeight()) / 2 + 1, Colors.GRAY, false);
    }
}
