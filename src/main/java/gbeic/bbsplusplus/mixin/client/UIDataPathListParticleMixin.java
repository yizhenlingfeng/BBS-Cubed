package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.BBSFSloveCML;
import gbeic.bbsplusplus.ui.particles.ParticlePlusUIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
@Mixin(value = UIDataPathList.class, priority = 1002, remap = false)
public abstract class UIDataPathListParticleMixin extends UIList<DataPath>
{
    @Shadow
    private Set<DataPath> hierarchy;

    @Shadow
    private DataPath path;

    @Shadow
    private Icon fileIcon;

    @Shadow
    public abstract void updateStrings();

    @Unique
    private String bbspp_cml$searchQuery = "";

    @Unique
    private final Set<String> bbspp_cml$favorites = new LinkedHashSet<>();

    @Unique
    private final List<String> bbspp_cml$recent = new ArrayList<>();

    @Unique
    private final Path bbspp_cml$favoritesFile = FabricLoader.getInstance().getConfigDir().resolve("bbs_particle_addon_favorites.txt");

    @Unique
    private final Path bbspp_cml$recentFile = FabricLoader.getInstance().getConfigDir().resolve("bbs_particle_addon_recent.txt");

    protected UIDataPathListParticleMixin(Consumer<List<DataPath>> callback)
    {
        super(callback);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbspp_cml$initializeParticleBrowser(Consumer<List<DataPath>> callback, CallbackInfo ci)
    {
        this.bbspp_cml$loadData();
        this.context((menu) ->
        {
            if (this.fileIcon != Icons.PARTICLE)
            {
                return;
            }

            DataPath current = this.getCurrentFirst();

            if (current == null || current.folder)
            {
                return;
            }

            String value = current.toString();
            boolean favorite = this.bbspp_cml$favorites.contains(value);

            menu.action(Icons.FAVORITE, favorite ? ParticlePlusUIKeys.REMOVE_FAVORITE : ParticlePlusUIKeys.ADD_FAVORITE, () ->
            {
                if (favorite)
                {
                    this.bbspp_cml$favorites.remove(value);
                }
                else
                {
                    this.bbspp_cml$favorites.add(value);
                }

                this.bbspp_cml$save(this.bbspp_cml$favoritesFile, this.bbspp_cml$favorites);
                this.updateStrings();
            });
        });
    }

    @Inject(method = "open", at = @At("HEAD"))
    private void bbspp_cml$recordRecent(DataPath dataPath, CallbackInfo ci)
    {
        if (this.fileIcon != Icons.PARTICLE || dataPath == null || dataPath.folder)
        {
            return;
        }

        String value = dataPath.toString();

        this.bbspp_cml$recent.remove(value);
        this.bbspp_cml$recent.add(0, value);

        while (this.bbspp_cml$recent.size() > 10)
        {
            this.bbspp_cml$recent.remove(this.bbspp_cml$recent.size() - 1);
        }

        this.bbspp_cml$save(this.bbspp_cml$recentFile, this.bbspp_cml$recent);
    }

    @Inject(method = "updateStrings", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$updateParticlePaths(CallbackInfo ci)
    {
        if (this.fileIcon != Icons.PARTICLE || !this.bbspp_cml$searchQuery.isEmpty())
        {
            return;
        }

        Set<DataPath> paths = new HashSet<>();

        if (this.bbspp_cml$isVirtualFolder("bbspa_favorites"))
        {
            paths.add(this.bbspp_cml$backPath());
            this.bbspp_cml$favorites.forEach((value) -> paths.add(new DataPath(value)));
        }
        else if (this.bbspp_cml$isVirtualFolder("bbspa_recent"))
        {
            paths.add(this.bbspp_cml$backPath());
            this.bbspp_cml$recent.forEach((value) -> paths.add(new DataPath(value)));
        }
        else
        {
            if (!this.path.strings.isEmpty())
            {
                paths.add(this.bbspp_cml$backPath());
            }

            for (DataPath dataPath : this.hierarchy)
            {
                if (dataPath.strings.isEmpty() || dataPath.strings.get(0).startsWith("bbspa_"))
                {
                    continue;
                }

                if (dataPath.startsWith(this.path, 1))
                {
                    paths.add(dataPath);
                }
                else if (dataPath.startsWith(this.path) && !dataPath.equals(this.path))
                {
                    paths.add(dataPath.getTo(this.path.strings.size() + 1));
                }
            }

            if (this.path.strings.isEmpty())
            {
                DataPath favorites = new DataPath(true);
                DataPath recent = new DataPath(true);

                favorites.strings.add("bbspa_favorites");
                recent.strings.add("bbspa_recent");
                paths.add(favorites);
                paths.add(recent);
            }
        }

        this.list.clear();
        this.list.addAll(paths);
        this.sort();
        this.update();
        ci.cancel();
    }

    @Override
    public void filter(String filter)
    {
        if (this.fileIcon != Icons.PARTICLE)
        {
            super.filter(filter);

            return;
        }

        String normalized = filter.trim().toLowerCase();

        this.bbspp_cml$searchQuery = normalized;

        if (normalized.isEmpty())
        {
            this.updateStrings();
            super.filter("");

            return;
        }

        boolean showAll = normalized.equals("*");

        this.list.clear();

        for (DataPath dataPath : this.hierarchy)
        {
            if (!dataPath.folder && (showAll || dataPath.getLast().toLowerCase().contains(normalized)))
            {
                this.list.add(dataPath);
            }
        }

        this.sort();
        this.update();
        this.scroll.updateTarget();
    }

    @Override
    protected boolean sortElements()
    {
        this.list.sort((a, b) ->
        {
            if (this.fileIcon == Icons.PARTICLE)
            {
                boolean aFavorite = this.bbspp_cml$isVirtualPath(a, "bbspa_favorites");
                boolean bFavorite = this.bbspp_cml$isVirtualPath(b, "bbspa_favorites");

                if (aFavorite != bFavorite) return aFavorite ? -1 : 1;

                boolean aRecent = this.bbspp_cml$isVirtualPath(a, "bbspa_recent");
                boolean bRecent = this.bbspp_cml$isVirtualPath(b, "bbspa_recent");

                if (aRecent != bRecent) return aRecent ? -1 : 1;
            }

            /* UIDataPathList 原版排序。非粒子列表也必须在这个覆盖方法里执行，
             * 因为调用 UIList 的父实现会直接返回 false，导致影片名称按 HashSet
             * 的不稳定顺序显示。 */
            if (a.folder != b.folder) return a.folder ? -1 : 1;
            if (a.toString().endsWith("/..")) return -1;
            if (b.toString().endsWith("/..")) return 1;

            return NaturalOrderComparator.compare(true, a.toString(), b.toString());
        });

        return true;
    }

    @Inject(method = "setCurrentFile", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$selectSearchResult(String path, CallbackInfo ci)
    {
        if (this.fileIcon != Icons.PARTICLE)
        {
            return;
        }

        boolean searching = !this.bbspp_cml$searchQuery.isEmpty();
        boolean virtualFolder = this.bbspp_cml$isVirtualFolder("bbspa_favorites") || this.bbspp_cml$isVirtualFolder("bbspa_recent");

        if (searching || virtualFolder)
        {
            int index = this.list.indexOf(new DataPath(path));

            if (index >= 0)
            {
                this.setIndex(index);
            }

            ci.cancel();
        }
    }

    @Inject(
        method = "renderElementPart(Lmchorse/bbs_mod/ui/framework/UIContext;Lmchorse/bbs_mod/utils/DataPath;IIIZZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbspp_cml$renderParticlePath(UIContext context, DataPath element, int i, int x, int y, boolean hover, boolean selected, CallbackInfo ci)
    {
        if (this.fileIcon != Icons.PARTICLE)
        {
            return;
        }

        context.batcher.icon(element.folder ? Icons.FOLDER : this.fileIcon, x, y);

        boolean searching = !this.bbspp_cml$searchQuery.isEmpty();
        boolean virtualFolder = this.bbspp_cml$isVirtualFolder("bbspa_favorites") || this.bbspp_cml$isVirtualFolder("bbspa_recent");

        if ((searching || virtualFolder) && !element.folder && element.strings.size() > 1)
        {
            String filename = element.getLast();
            String folder = element.getParent() + "/";
            int color = selected ? 0x000088ff : hover ? 0x00ddddff : 0xffffffff;
            int textY = y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2;

            context.batcher.textShadow(filename, x + 16, textY, color);

            int folderColor = selected ? 0x000088ff : 0xff888888;
            int filenameWidth = context.batcher.getFont().getWidth(filename);

            context.batcher.textShadow("  " + folder, x + 16 + filenameWidth, textY, folderColor);
            ci.cancel();
        }
    }

    @Inject(
        method = "elementToString(Lmchorse/bbs_mod/ui/framework/UIContext;ILmchorse/bbs_mod/utils/DataPath;)Ljava/lang/String;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbspp_cml$particlePathLabel(UIContext context, int i, DataPath element, CallbackInfoReturnable<String> cir)
    {
        if (this.fileIcon != Icons.PARTICLE || element == null)
        {
            return;
        }

        if (this.bbspp_cml$isVirtualPath(element, "bbspa_favorites"))
        {
            cir.setReturnValue("\u2605 " + ParticlePlusUIKeys.FAVORITES.get() + "/");

            return;
        }

        if (this.bbspp_cml$isVirtualPath(element, "bbspa_recent"))
        {
            cir.setReturnValue("\u23f1 " + ParticlePlusUIKeys.RECENT.get() + "/");

            return;
        }

        if (element.folder)
        {
            String last = element.getLast();

            cir.setReturnValue(last.equals("..") ? "../" : last + "/[" + this.bbspp_cml$countFiles(element) + "]");
        }
        else
        {
            cir.setReturnValue(element.getLast());
        }
    }

    @Unique
    private void bbspp_cml$loadData()
    {
        this.bbspp_cml$load(this.bbspp_cml$favoritesFile, this.bbspp_cml$favorites);
        this.bbspp_cml$load(this.bbspp_cml$recentFile, this.bbspp_cml$recent);
    }

    @Unique
    private void bbspp_cml$load(Path file, java.util.Collection<String> output)
    {
        if (!Files.exists(file))
        {
            return;
        }

        try
        {
            output.clear();

            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
            {
                String trimmed = line.trim();

                if (!trimmed.isEmpty())
                {
                    output.add(trimmed);
                }
            }
        }
        catch (IOException exception)
        {
            BBSFSloveCML.LOGGER.warn("[Particle+] Failed to read {}", file, exception);
        }
    }

    @Unique
    private void bbspp_cml$save(Path file, java.util.Collection<String> values)
    {
        try
        {
            Files.createDirectories(file.getParent());
            Files.write(file, values, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException exception)
        {
            BBSFSloveCML.LOGGER.warn("[Particle+] Failed to write {}", file, exception);
        }
    }

    @Unique
    private DataPath bbspp_cml$backPath()
    {
        DataPath back = this.path.copy();

        back.strings.add("..");

        return back;
    }

    @Unique
    private boolean bbspp_cml$isVirtualFolder(String name)
    {
        return this.bbspp_cml$searchQuery.isEmpty() && this.bbspp_cml$isVirtualPath(this.path, name);
    }

    @Unique
    private boolean bbspp_cml$isVirtualPath(DataPath value, String name)
    {
        return value.strings.size() == 1 && value.strings.get(0).equals(name);
    }

    @Unique
    private int bbspp_cml$countFiles(DataPath folder)
    {
        int count = 0;

        for (DataPath dataPath : this.hierarchy)
        {
            if (!dataPath.folder && dataPath.startsWith(folder))
            {
                count++;
            }
        }

        return count;
    }
}
