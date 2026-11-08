package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.film.IFilmLibraryPathList;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.film.UIFilmOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.DataPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The film library's "all films" view lists films from every folder at once, while the list's own
 * path stays at the root. A film renamed or duplicated there stays in the folder it came from
 * instead of landing in the root, and a new film is selected once the list refreshes.
 *
 * <p>2.5.2 hooked UISelectionScreen#renameData(from, to) / #dupeSelected(from, to) /
 * #addNewData(name, data). In 2.7 the same operations are UIDataOverlayPanel#renameData(name),
 * #dupeData(name) and #addNewData(context, name, data), acting on the open film; the target name
 * is corrected before 2.7 runs them.</p>
 */
@Mixin(value = UIDataOverlayPanel.class, remap = false)
public abstract class UIDataOverlayPanelFilmLibraryMixin extends UICRUDOverlayPanel
{
    @Shadow(remap = false)
    protected UIDataDashboardPanel<?> panel;

    private UIDataOverlayPanelFilmLibraryMixin()
    {
        super(null, null);
    }

    @ModifyVariable(method = "renameData(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, remap = false)
    private String rabbit$renameFilmInsideOriginalFolder(String name)
    {
        return this.rabbit$keepInOriginalFolder(name);
    }

    @ModifyVariable(method = "dupeData(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, remap = false)
    private String rabbit$dupeFilmInsideOriginalFolder(String name)
    {
        return this.rabbit$keepInOriginalFolder(name);
    }

    @Inject(method = "addNewData(Lmchorse/bbs_mod/ui/framework/UIContext;Ljava/lang/String;Lmchorse/bbs_mod/data/types/MapType;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$selectCreatedFilmAfterRefresh(UIContext context, String name, MapType mapType, CallbackInfo ci)
    {
        IFilmLibraryPathList pathList = this.rabbit$libraryList();

        if (pathList == null)
        {
            return;
        }

        String current = this.namesList.getPath().toString();

        if (name == null || name.trim().isEmpty() || (!current.isEmpty() && current.equals(name)))
        {
            context.notifyError(UIKeys.PANELS_MODALS_EMPTY);
            ci.cancel();

            return;
        }

        if (!this.namesList.hasInHierarchy(name))
        {
            pathList.rabbit$selectFilmLibraryAfterRefresh(name);
        }
    }

    @Unique
    private IFilmLibraryPathList rabbit$libraryList()
    {
        if ((Object) this instanceof UIFilmOverlayPanel && this.namesList instanceof IFilmLibraryPathList pathList && pathList.rabbit$isFilmLibraryMode())
        {
            return pathList;
        }

        return null;
    }

    @Unique
    private String rabbit$keepInOriginalFolder(String name)
    {
        IFilmLibraryPathList pathList = this.rabbit$libraryList();

        if (pathList == null || !pathList.rabbit$isShowingAllFilmLibraryFilms() || this.panel.getData() == null || name == null || name.trim().isEmpty())
        {
            return name;
        }

        DataPath parent = new DataPath(this.panel.getData().getId()).getParent();

        if (parent.strings.isEmpty())
        {
            return name;
        }

        return parent + "/" + new DataPath(name).getLast();
    }
}
