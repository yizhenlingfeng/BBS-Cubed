package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.film.FilmLibraryDragDrop;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryPathList;
import gbeic.bbsplusplus.client.ui.film.UIFilmLibraryFolderTree;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItems;
import mchorse.bbs_mod.utils.DataPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 影片库列表的拖拽投放与内部排序屏蔽。
 * <p>
 * 影片库只允许把影片拖到左侧文件夹树，不需要原版的列表内重新排序；同时释放事件会按子元素
 * 顺序分发给所有元素，列表自身的 {@code release()} 可能先于文件夹树执行并把拖拽状态清空，
 * 因此这里在 {@code subMouseReleased} 开头、拖拽状态还在时直接完成投放。
 * </p>
 */
@Mixin(value = UIItems.class, remap = true)
public abstract class UIItemsFilmLibraryDragMixin
{
    @Inject(method = "reportDropTarget", at = @At("HEAD"), cancellable = true, remap = true)
    private void bbspp$disableFilmLibraryInternalReorder(int x, int y, CallbackInfo ci)
    {
        if ((Object) this instanceof IFilmLibraryPathList pathList && pathList.bbspp$isFilmLibraryMode())
        {
            ci.cancel();
        }
    }

    @Inject(method = "subMouseReleased", at = @At("HEAD"), remap = true)
    private void bbspp$dropFilmsOnFolderTree(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!((Object) this instanceof UIDataPathList list))
        {
            return;
        }

        FilmLibraryDragDrop.Binding binding = FilmLibraryDragDrop.get(list);

        if (binding == null || !list.isDragging())
        {
            return;
        }

        UIFilmLibraryFolderTree tree = binding.tree();
        DataPath folder = tree.bbspp$getDropFolder(context);

        if (folder == null)
        {
            return;
        }

        List<DataPath> films = new ArrayList<>();

        for (Object item : list.drag.getItems())
        {
            if (item instanceof DataPath path && !path.folder)
            {
                films.add(path);
            }
        }

        if (films.isEmpty())
        {
            return;
        }

        list.drag.reset();
        binding.support().moveFilms(films, folder);
    }
}
