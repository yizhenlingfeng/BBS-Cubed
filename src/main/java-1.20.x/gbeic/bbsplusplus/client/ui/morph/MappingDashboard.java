package gbeic.bbsplusplus.client.ui.morph;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;

/**
 * 形变映射编辑器专用的 dashboard 宿主，只承载一个面板并复用打开前的菜单返回。
 */
final class MappingDashboard extends UIDashboard
{
    private final UIBaseMenu previousMenu;
    private UICubeMappingEditorPanel editorPanel;

    MappingDashboard(UIBaseMenu previousMenu)
    {
        super();
        this.previousMenu = previousMenu;
    }

    @Override
    protected void registerPanels()
    {
        /* This dashboard is a dedicated host for one editor, so skip the unrelated BBS panels. */
    }

    void setEditorPanel(UICubeMappingEditorPanel editorPanel)
    {
        this.editorPanel = editorPanel;
    }

    @Override
    public void onOpen(UIBaseMenu oldMenu)
    {
        super.onOpen(oldMenu);
        this.setPanel(this.editorPanel);
    }

    @Override
    protected void closeMenu()
    {
        if (this.previousMenu == null)
        {
            super.closeMenu();

            return;
        }

        if (!this.main.isVisible())
        {
            this.main.setVisible(true);
        }

        UIScreen.open(this.previousMenu);
    }
}
