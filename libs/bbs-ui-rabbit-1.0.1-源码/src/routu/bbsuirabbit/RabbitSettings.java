package routu.bbsuirabbit;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import routu.bbsuirabbit.ui.morphing.MorphCellNames;

/**
 * BBS UI Rabbit's settings (BBS settings panel → "肉兔界面", file {@code settings/bbs_ui_rabbit.json}).
 * Every switch turns one interface back into BBS's own.
 */
public class RabbitSettings
{
    public static final String ID = "bbs_ui_rabbit";

    /* Launcher */

    /** The game opens on the BBS-Rabbit launcher instead of vanilla's title screen, world list and server list. */
    public static ValueBoolean titleLauncher;
    /** How much bigger than BBS's UI scale the launcher is drawn. */
    public static ValueFloat launcherScale;

    /** A row of resource pack presets on the vanilla resource pack screen. */
    public static ValueBoolean packPresets;

    /* Film library */

    /** The film panel's empty tab shows the film library home instead of BBS's landing screen. */
    public static ValueBoolean filmLibraryHome;
    /** The film data overlay (open/rename/duplicate films) gets the library layout: folder tree, search, sort. */
    public static ValueBoolean newFilmLibraryUi;
    /** The film library shows covers, hover frames and colour labels (kept by the world's host). */
    public static ValueBoolean filmThumbnails;
    /** Covers and hover frames are captured from the film preview while editing. */
    public static ValueBoolean filmThumbnailAuto;
    /** Hovering a film card in the library plays through its frames. */
    public static ValueBoolean filmThumbnailHover;
    public static ValueInt filmLibrarySortMode;
    public static ValueString filmLibraryDefaultLocation;

    /* Morphing */

    /** The form picker becomes the tree sidebar + long category list + details drawer. */
    public static ValueBoolean newMorphingPanel;
    public static ValueString morphingDefaultCategory;
    /** Morphing panel navigation: 0 = directory (one long list, sidebar scrolls to it), 1 = focus (one node at a time). */
    public static ValueInt morphingNavMode;
    public static ValueInt morphingSidebarWidth;
    public static ValueBoolean morphingDrawerOpen;
    /** Expanded sidebar node ids, one per line. */
    public static ValueString morphingExpandedNodes;
    /** Sidebar node the morphing panel was last left on ("" = none). */
    public static ValueString morphingLastNode;

    /* Editors */

    /** The model/particle/audio editors' empty tab shows the asset workbench home. */
    public static ValueBoolean assetHome;

    public static void register(SettingsBuilder builder)
    {
        builder.category("launcher", Icons.GLOBE);
        titleLauncher = builder.getBoolean("title_launcher", true);
        launcherScale = builder.getFloat("launcher_scale", 1.3F, 0.75F, 2.5F);
        packPresets = builder.getBoolean("pack_presets", true);

        builder.category("film_library", Icons.FILM);
        filmLibraryHome = builder.getBoolean("film_library_home", true);
        newFilmLibraryUi = builder.getBoolean("new_film_library_ui", true);
        filmThumbnails = builder.getBoolean("film_thumbnails", true);
        filmThumbnailAuto = builder.getBoolean("film_thumbnail_auto", true);
        filmThumbnailHover = builder.getBoolean("film_thumbnail_hover", true);
        filmLibrarySortMode = (ValueInt) builder.getInt("film_library_sort_mode", 0, 0, 1).invisible();
        filmLibraryDefaultLocation = (ValueString) builder.getString("film_library_default_location", "all").invisible();

        builder.category("morphing", Icons.MORPH);
        newMorphingPanel = builder.getBoolean("new_morphing_panel", true);
        morphingDefaultCategory = (ValueString) builder.getString("morphing_default_category", "home").invisible();
        morphingNavMode = (ValueInt) builder.getInt("morphing_nav_mode", 0, 0, 1).invisible();
        morphingSidebarWidth = (ValueInt) builder.getInt("morphing_sidebar_width", 150, 80, 300).invisible();
        morphingDrawerOpen = (ValueBoolean) builder.getBoolean("morphing_drawer_open", true).invisible();
        morphingExpandedNodes = (ValueString) builder.getString("morphing_expanded_nodes", "group:user\ngroup:models\ngroup:mobs").invisible();
        morphingLastNode = (ValueString) builder.getString("morphing_last_node", "").invisible();

        MorphCellNames.register(builder);

        builder.category("editors", Icons.EDIT);
        assetHome = builder.getBoolean("asset_home", true);
    }

    public static boolean isOn(ValueBoolean value)
    {
        return value != null && value.get();
    }
}
