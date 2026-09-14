package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UIPathRow;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;

/**
 * Blockbench.exe 路径的设置行：文本框 + 文件夹「浏览」按钮，
 * 与 BBS 本体选择 ffmpeg 编码器路径（UIEncoderPathRow）同一套交互。
 *
 * <p>点击按钮弹系统文件选择器，只列 {@code *.exe}；选中后写回设置。
 * 整行右键可打开当前 exe 所在的文件夹。</p>
 */
public class UIBlockbenchPathRow extends UIPathRow
{
    public UIBlockbenchPathRow(ValueString path)
    {
        super(path);
    }

    @Override
    protected IKey getTooltip()
    {
        return BlockbenchUIKeys.PICK;
    }

    @Override
    protected void pick(UITextbox textbox)
    {
        File current = this.getCurrentExe();

        UIFileDialogs.pickFile(BlockbenchUIKeys.DIALOG_TITLE, current,
            new String[] {"*.exe"}, BlockbenchUIKeys.DIALOG_DESC,
            (file) -> this.set(textbox, file));
    }

    @Override
    protected void context(ContextMenuManager menu, UIElement element, UITextbox textbox)
    {
        File current = this.getCurrentExe();

        if (current != null)
        {
            menu.action(Icons.FOLDER, BlockbenchUIKeys.OPEN_EXE_FOLDER,
                () -> UIUtils.openFolder(current.getAbsoluteFile().getParentFile()));
        }
    }

    /** 路径当前指向的 exe 文件；未填或不是文件时返回 null（让对话框开在默认位置）。 */
    private File getCurrentExe()
    {
        String value = this.path == null ? null : this.path.get();

        if (value == null || value.trim().isEmpty())
        {
            return null;
        }

        File file = new File(value.trim());

        return file.isFile() ? file : null;
    }
}
