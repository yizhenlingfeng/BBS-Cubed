package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.film.FilmLibraryDefaultLocation;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryPathList;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 影片库路径列表的数据层扩展：层级查询、排序、搜索与打开影片。
 */
@Mixin(UIDataPathList.class)
public abstract class UIDataPathListFilmLibraryMixin extends UIList<DataPath> implements IFilmLibraryPathList
{
    @Shadow private Set<DataPath> hierarchy;
    @Shadow private DataPath path;
    @Shadow private DataPath previousPath;

    @Unique private boolean bbspp$filmLibraryMode;
    @Unique private boolean bbspp$applyDefaultWhenFilled;
    @Unique private boolean bbspp$sortDescending;
    @Unique private final DataPath bbspp$currentFolder = new DataPath(true);
    @Unique private final Set<DataPath> bbspp$knownFolders = new HashSet<>();
    @Unique private int bbspp$filmLibraryRevision;

    public UIDataPathListFilmLibraryMixin(Consumer<java.util.List<DataPath>> callback)
    {
        super(callback);
    }

    @Override
    public void bbspp$setFilmLibraryMode(boolean enabled)
    {
        this.bbspp$filmLibraryMode = enabled;

        if (!enabled)
        {
            this.bbspp$applyDefaultWhenFilled = false;
        }
    }

    @Override
    public void bbspp$applyFilmLibraryDefaultWhenFilled()
    {
        this.bbspp$applyDefaultWhenFilled = true;
    }

    @Override
    public boolean bbspp$isFilmLibraryMode()
    {
        return this.bbspp$filmLibraryMode;
    }

    @Override
    public void bbspp$showFilmLibraryFolder(DataPath folder)
    {
        this.bbspp$currentFolder.copy(folder == null ? DataPath.EMPTY : folder);
        this.bbspp$currentFolder.folder = true;
        this.bbspp$fillCurrentFilmLibraryView();
    }

    @Override
    public Collection<DataPath> bbspp$getFilmLibraryFolders()
    {
        Set<DataPath> folders = new HashSet<>(this.bbspp$knownFolders);

        for (DataPath dataPath : this.hierarchy)
        {
            int folderDepth = dataPath.folder ? dataPath.strings.size() : dataPath.strings.size() - 1;

            for (int i = 1; i <= folderDepth; i++)
            {
                DataPath folder = dataPath.getTo(i);

                folder.folder = true;
                folders.add(folder);
            }
        }

        List<DataPath> sorted = new ArrayList<>(folders);

        sorted.sort((a, b) -> NaturalOrderComparator.compare(true, a.toString(), b.toString()));

        return sorted;
    }

    @Override
    public Collection<DataPath> bbspp$getFilmLibraryFilms(DataPath folder)
    {
        List<DataPath> films = new ArrayList<>();

        if (folder == null)
        {
            return films;
        }

        for (DataPath dataPath : this.hierarchy)
        {
            if (dataPath.folder || dataPath.strings.size() != folder.strings.size() + 1)
            {
                continue;
            }

            boolean child = true;

            for (int i = 0; i < folder.strings.size(); i++)
            {
                if (!folder.strings.get(i).equals(dataPath.strings.get(i)))
                {
                    child = false;
                    break;
                }
            }

            if (child)
            {
                films.add(dataPath);
            }
        }

        return films;
    }

    @Override
    public Collection<DataPath> bbspp$getFilmLibraryAllFilms()
    {
        List<DataPath> films = new ArrayList<>();

        for (DataPath dataPath : this.hierarchy)
        {
            if (!dataPath.folder)
            {
                films.add(dataPath);
            }
        }

        return films;
    }

    @Override
    public void bbspp$openFilmLibraryFilm(DataPath film)
    {
        if (film != null && !film.folder)
        {
            ((UIDataPathListOpenAccessor) (Object) this).bbspp$open(film);
        }
    }

    @Override
    public int bbspp$getFilmLibraryRevision()
    {
        return this.bbspp$filmLibraryRevision;
    }

    @Override
    public DataPath bbspp$getFilmLibraryFolder()
    {
        return this.bbspp$currentFolder.copy();
    }

    @Override
    public boolean bbspp$isFilmLibraryFolderEmpty(DataPath folder)
    {
        if (folder == null)
        {
            return true;
        }

        for (DataPath dataPath : this.hierarchy)
        {
            if (dataPath.startsWith(folder) && !dataPath.equals(folder))
            {
                return false;
            }
        }

        for (DataPath dataPath : this.bbspp$knownFolders)
        {
            if (dataPath.startsWith(folder) && !dataPath.equals(folder))
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public void bbspp$rememberFilmLibraryFolder(DataPath folder)
    {
        if (folder != null && !folder.strings.isEmpty())
        {
            DataPath copy = folder.copy();

            copy.folder = true;
            this.bbspp$knownFolders.add(copy);
        }
    }

    @Override
    public void bbspp$forgetFilmLibraryFolder(DataPath folder)
    {
        if (folder != null)
        {
            this.bbspp$knownFolders.removeIf((known) -> known.equals(folder) || known.startsWith(folder));
        }
    }

    @Override
    public void bbspp$renameKnownFilmLibraryFolder(DataPath folder, String name)
    {
        if (folder == null || folder.strings.isEmpty() || name == null || name.trim().isEmpty())
        {
            return;
        }

        List<DataPath> renamed = new ArrayList<>();
        String from = folder.toString();
        DataPath parent = folder.getParent();
        String prefix = parent.strings.isEmpty() ? name : parent + "/" + name;

        for (DataPath known : this.bbspp$knownFolders)
        {
            if (!known.equals(folder) && !known.startsWith(folder))
            {
                continue;
            }

            String current = known.toString();
            DataPath copy = new DataPath(current.equals(from) ? prefix : prefix + "/" + current.substring((from + "/").length()));

            copy.folder = true;
            renamed.add(copy);
        }

        this.bbspp$forgetFilmLibraryFolder(folder);
        this.bbspp$knownFolders.addAll(renamed);
    }

    @Override
    public void bbspp$setFilmLibrarySortDescending(boolean descending)
    {
        this.bbspp$sortDescending = descending;
        this.sort();
        this.update();
    }

    @Unique
    private void bbspp$clearFilmLibrarySelection()
    {
        this.deselect();
        this.previousPath = null;
    }

    @Unique
    private void bbspp$fillCurrentFilmLibraryView()
    {
        this.list.clear();

        for (DataPath dataPath : this.hierarchy)
        {
            if (!dataPath.folder && dataPath.startsWith(this.bbspp$currentFolder, 1))
            {
                this.list.add(dataPath);
            }
        }

        this.path.copy(this.bbspp$currentFolder);
        this.bbspp$clearFilmLibrarySelection();
        this.sort();
        this.update();
    }

    @Unique
    private void bbspp$captureKnownFilmLibraryFolders()
    {
        for (DataPath dataPath : this.hierarchy)
        {
            int folderDepth = dataPath.folder ? dataPath.strings.size() : dataPath.strings.size() - 1;

            for (int i = 1; i <= folderDepth; i++)
            {
                DataPath folder = dataPath.getTo(i);

                folder.folder = true;
                this.bbspp$knownFolders.add(folder);
            }
        }
    }

    /**
     * 注入目标：{@link UIDataPathList#fill(Collection)} 返回后。
     * 注入原因：原版填充后会回到根目录；影片库只需要层级数据，列视图自行按文件夹分列。
     * 修改后的行为：影片库模式下刷新隐藏列表，并递增列视图使用的版本号。
     */
    @Inject(method = "fill", at = @At("RETURN"), remap = true)
    private void bbspp$showFilmsAfterFill(Collection<String> hierarchy, CallbackInfo ci)
    {
        if (this.bbspp$filmLibraryMode)
        {
            this.bbspp$captureKnownFilmLibraryFolders();

            if (this.bbspp$applyDefaultWhenFilled)
            {
                this.bbspp$applyDefaultWhenFilled = false;
                this.bbspp$applyDefaultFilmLibraryLocation();
            }

            this.bbspp$fillCurrentFilmLibraryView();
            this.bbspp$filmLibraryRevision++;
        }
    }

    @Unique
    private void bbspp$applyDefaultFilmLibraryLocation()
    {
        DataPath folder = FilmLibraryDefaultLocation.getFolder();

        if (folder == null || (!folder.strings.isEmpty() && !this.bbspp$getFilmLibraryFolders().contains(folder)))
        {
            this.bbspp$currentFolder.copy(DataPath.EMPTY);

            return;
        }

        this.bbspp$currentFolder.copy(folder);
        this.bbspp$currentFolder.folder = true;
    }

    /**
     * 注入目标：{@link UIDataPathList#sortElements()} 返回前。
     * 注入原因：影片库需要保存名称排序偏好，而原版列表只提供固定自然升序。
     * 修改后的行为：影片库模式下按文件夹优先、返回上级优先、名称升降序重新排序。
     */
    @Inject(method = "sortElements", at = @At("RETURN"), remap = true)
    private void bbspp$sortFilmLibrary(CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.bbspp$filmLibraryMode)
        {
            return;
        }

        this.list.sort((a, b) ->
        {
            if (a.folder && !b.folder) return -1;
            if (b.folder && !a.folder) return 1;

            if (a.toString().endsWith("/..")) return -1;
            if (b.toString().endsWith("/..")) return 1;

            int result = NaturalOrderComparator.compare(true, a.toString(), b.toString());

            return this.bbspp$sortDescending ? -result : result;
        });
    }
}
