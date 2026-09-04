package gbeic.bbsplusplus.premiere;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Premiere 导出弹窗：对齐「导出为动画」的紧凑表单风格。
 * <ul>
 *   <li>提示文字在输入框 placeholder 内</li>
 *   <li>底部仅一个「导出」按钮</li>
 * </ul>
 */
public class UIPremiereExportOverlayPanel extends UIOverlayPanel
{
    public final UITextbox folderName;
    public final UITextbox exportPath;
    public final UIButton export;

    private String resultFolderName;
    private String resultExportPath;
    private boolean confirmed;

    public UIPremiereExportOverlayPanel()
    {
        super(PremiereUIKeys.EXPORT);

        String defaultPath = BBSSettings.videoExportPath.get();

        if (defaultPath == null || defaultPath.trim().isEmpty())
        {
            try
            {
                defaultPath = BBSRendering.getVideoFolder().getAbsolutePath();
            }
            catch (Exception e)
            {
                defaultPath = "";
            }
        }

        this.folderName = new UITextbox();
        this.folderName.setText("");
        this.folderName.placeholder(PremiereUIKeys.FOLDER_NAME);

        this.exportPath = new UITextbox();
        this.exportPath.setText(defaultPath == null ? "" : defaultPath);
        this.exportPath.placeholder(PremiereUIKeys.EXPORT_PATH);

        this.export = new UIButton(PremiereUIKeys.EXPORT_ACTION, (b) ->
        {
            this.resultFolderName = this.folderName.getText();
            this.resultExportPath = this.exportPath.getText();
            this.confirmed = true;
            this.close();
        });

        UIScrollView scroll = UI.scrollView(5, 6,
            this.folderName,
            this.exportPath,
            this.export.marginTop(6)
        );

        scroll.full(this.content);
        this.content.add(scroll);
    }

    public boolean isConfirmed()
    {
        return this.confirmed;
    }

    public String getFolderName()
    {
        return this.resultFolderName;
    }

    public String getExportPath()
    {
        return this.resultExportPath;
    }
}
