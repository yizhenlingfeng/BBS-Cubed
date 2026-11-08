package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.film.IFilmLibraryPathList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
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

@Mixin(value={UIDataPathList.class})
public abstract class UIDataPathListFilmLibraryMixin
extends UIList<DataPath>
implements IFilmLibraryPathList {
    @Shadow
    private Set<DataPath> hierarchy;
    @Shadow
    private DataPath path;
    @Shadow
    private DataPath previousPath;
    @Unique
    private boolean rabbit$filmLibraryMode;
    @Unique
    private boolean rabbit$showFullPaths;
    @Unique
    private boolean rabbit$sortDescending;
    @Unique
    private boolean rabbit$showAllFilms = true;
    @Unique
    private final DataPath rabbit$currentFolder = new DataPath(true);
    @Unique
    private final Set<DataPath> rabbit$knownFolders = new HashSet<DataPath>();
    @Unique
    private String rabbit$selectAfterRefresh = "";

    public UIDataPathListFilmLibraryMixin(Consumer<List<DataPath>> callback) {
        super(callback);
    }

    @Override
    public void rabbit$setFilmLibraryMode(boolean enabled) {
        this.rabbit$filmLibraryMode = enabled;
    }

    @Override
    public boolean rabbit$isFilmLibraryMode() {
        return this.rabbit$filmLibraryMode;
    }

    @Override
    public void rabbit$filterFilmLibrary(String query) {
        if (!this.rabbit$filmLibraryMode) {
            this.filter(query);
            return;
        }
        String normalized = query == null ? "" : query.trim().toLowerCase();
        this.rabbit$showFullPaths = !normalized.isEmpty() && this.rabbit$showAllFilms;
        this.list.clear();
        if (normalized.isEmpty()) {
            this.rabbit$fillCurrentFilmLibraryView();
        } else {
            for (DataPath dataPath : this.hierarchy) {
                if (dataPath.folder || !this.rabbit$showAllFilms && !dataPath.startsWith(this.rabbit$currentFolder, 1)) continue;
                String full = dataPath.toString().toLowerCase();
                String last = dataPath.getLast().toLowerCase();
                if (!full.contains(normalized) && !last.contains(normalized)) continue;
                this.list.add(dataPath);
            }
        }
        this.rabbit$clearFilmLibrarySelection();
        this.sort();
        this.update();
    }

    @Override
    public void rabbit$showAllFilmLibraryFilms() {
        this.rabbit$showAllFilms = true;
        this.rabbit$currentFolder.copy(DataPath.EMPTY);
        this.rabbit$fillCurrentFilmLibraryView();
    }

    @Override
    public void rabbit$showFilmLibraryFolder(DataPath folder) {
        this.rabbit$showAllFilms = false;
        this.rabbit$currentFolder.copy(folder == null ? DataPath.EMPTY : folder);
        this.rabbit$currentFolder.folder = true;
        this.rabbit$fillCurrentFilmLibraryView();
    }

    @Override
    public Collection<DataPath> rabbit$getFilmLibraryFolders() {
        HashSet<DataPath> folders = new HashSet<DataPath>(this.rabbit$knownFolders);
        for (DataPath dataPath : this.hierarchy) {
            int folderDepth = dataPath.folder ? dataPath.strings.size() : dataPath.strings.size() - 1;
            for (int i = 1; i <= folderDepth; ++i) {
                DataPath folder = dataPath.getTo(i);
                folder.folder = true;
                folders.add(folder);
            }
        }
        ArrayList<DataPath> sorted = new ArrayList<DataPath>(folders);
        sorted.sort((a, b) -> NaturalOrderComparator.compare((boolean)true, (String)a.toString(), (String)b.toString()));
        return sorted;
    }

    @Override
    public boolean rabbit$isShowingAllFilmLibraryFilms() {
        return this.rabbit$showAllFilms;
    }

    @Override
    public DataPath rabbit$getFilmLibraryFolder() {
        return this.rabbit$currentFolder.copy();
    }

    @Override
    public void rabbit$selectFilmLibraryAfterRefresh(String id) {
        this.rabbit$selectAfterRefresh = id == null ? "" : id;
    }

    @Override
    public boolean rabbit$isFilmLibraryFolderEmpty(DataPath folder) {
        if (folder == null) {
            return true;
        }
        for (DataPath dataPath : this.hierarchy) {
            if (!dataPath.startsWith(folder) || dataPath.equals((Object)folder)) continue;
            return false;
        }
        for (DataPath dataPath : this.rabbit$knownFolders) {
            if (!dataPath.startsWith(folder) || dataPath.equals((Object)folder)) continue;
            return false;
        }
        return true;
    }

    @Override
    public void rabbit$rememberFilmLibraryFolder(DataPath folder) {
        if (folder != null && !folder.strings.isEmpty()) {
            DataPath copy = folder.copy();
            copy.folder = true;
            this.rabbit$knownFolders.add(copy);
        }
    }

    @Override
    public void rabbit$forgetFilmLibraryFolder(DataPath folder) {
        if (folder != null) {
            this.rabbit$knownFolders.removeIf(known -> known.equals((Object)folder) || known.startsWith(folder));
        }
    }

    @Override
    public void rabbit$renameKnownFilmLibraryFolder(DataPath folder, String name) {
        if (folder == null || folder.strings.isEmpty() || name == null || name.trim().isEmpty()) {
            return;
        }
        ArrayList<DataPath> renamed = new ArrayList<DataPath>();
        String from = folder.toString();
        DataPath parent = folder.getParent();
        String prefix = parent.strings.isEmpty() ? name : String.valueOf(parent) + "/" + name;
        for (DataPath known : this.rabbit$knownFolders) {
            if (!known.equals((Object)folder) && !known.startsWith(folder)) continue;
            String current = known.toString();
            DataPath copy = new DataPath((String)(current.equals(from) ? prefix : prefix + "/" + current.substring((from + "/").length())));
            copy.folder = true;
            renamed.add(copy);
        }
        this.rabbit$forgetFilmLibraryFolder(folder);
        this.rabbit$knownFolders.addAll(renamed);
    }

    @Override
    public void rabbit$setFilmLibrarySortDescending(boolean descending) {
        this.rabbit$sortDescending = descending;
        this.sort();
        this.update();
    }

    @Override
    public boolean rabbit$isFilmLibrarySortDescending() {
        return this.rabbit$sortDescending;
    }

    protected boolean subKeyPressed(UIContext context) {
        if (this.rabbit$filmLibraryMode && context.isPressed(256) && this.isSelected()) {
            this.rabbit$clearFilmLibrarySelection();
            return true;
        }
        return super.subKeyPressed(context);
    }

    @Inject(method={"subMouseClicked"}, at={@At(value="HEAD")}, remap=true)
    private void rabbit$deselectOnFilmLibraryEmptyClick(UIContext context, CallbackInfoReturnable<Boolean> cir) {
        if (this.rabbit$filmLibraryMode && this.area.isInside(context) && this.getIndexAtCursor(context) < 0) {
            this.rabbit$clearFilmLibrarySelection();
        }
    }

    @Unique
    private void rabbit$clearFilmLibrarySelection() {
        this.deselect();
        this.previousPath = null;
    }

    @Unique
    private void rabbit$fillCurrentFilmLibraryView() {
        DataPath selected;
        this.list.clear();
        if (this.rabbit$showAllFilms) {
            for (DataPath dataPath : this.hierarchy) {
                if (dataPath.folder) continue;
                this.list.add(dataPath);
            }
        } else {
            for (DataPath dataPath : this.hierarchy) {
                if (dataPath.folder || !dataPath.startsWith(this.rabbit$currentFolder, 1)) continue;
                this.list.add(dataPath);
            }
        }
        this.path.copy(this.rabbit$currentFolder);
        this.rabbit$showFullPaths = this.rabbit$showAllFilms;
        this.rabbit$clearFilmLibrarySelection();
        this.sort();
        if (!this.rabbit$selectAfterRefresh.isEmpty() && this.list.contains(selected = new DataPath(this.rabbit$selectAfterRefresh))) {
            this.setCurrentScroll(selected);
            this.rabbit$selectAfterRefresh = "";
        }
        this.update();
    }

    @Unique
    private void rabbit$captureKnownFilmLibraryFolders() {
        for (DataPath dataPath : this.hierarchy) {
            int folderDepth = dataPath.folder ? dataPath.strings.size() : dataPath.strings.size() - 1;
            for (int i = 1; i <= folderDepth; ++i) {
                DataPath folder = dataPath.getTo(i);
                folder.folder = true;
                this.rabbit$knownFolders.add(folder);
            }
        }
    }

    @Inject(method={"fill"}, at={@At(value="RETURN")}, remap=true)
    private void rabbit$showFilmsAfterFill(Collection<String> hierarchy, CallbackInfo ci) {
        if (this.rabbit$filmLibraryMode) {
            this.rabbit$captureKnownFilmLibraryFolders();
            this.rabbit$fillCurrentFilmLibraryView();
        }
    }

    @Inject(method={"sortElements"}, at={@At(value="RETURN")}, remap=true)
    private void rabbit$sortFilmLibrary(CallbackInfoReturnable<Boolean> cir) {
        if (!this.rabbit$filmLibraryMode) {
            return;
        }
        this.list.sort((a, b) -> {
            if (a.folder && !b.folder) {
                return -1;
            }
            if (b.folder && !a.folder) {
                return 1;
            }
            if (a.toString().endsWith("/..")) {
                return -1;
            }
            if (b.toString().endsWith("/..")) {
                return 1;
            }
            int result = NaturalOrderComparator.compare((boolean)true, (String)a.toString(), (String)b.toString());
            return this.rabbit$sortDescending ? -result : result;
        });
    }

    @Inject(method={"elementToString(Lmchorse/bbs_mod/ui/framework/UIContext;ILmchorse/bbs_mod/utils/DataPath;)Ljava/lang/String;"}, at={@At(value="HEAD")}, cancellable=true, remap=true)
    private void rabbit$showFullPathWhenSearching(UIContext context, int i, DataPath element, CallbackInfoReturnable<String> cir) {
        if (this.rabbit$filmLibraryMode && this.rabbit$showFullPaths) {
            cir.setReturnValue(element.toString() + (element.folder ? "/" : ""));
        }
    }
}
