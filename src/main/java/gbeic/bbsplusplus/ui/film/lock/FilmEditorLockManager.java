package gbeic.bbsplusplus.ui.film.lock;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.api.UIFilmPanelLayoutAccessor;
import gbeic.bbsplusplus.mixin.client.accessor.ValueGroupForeignAccess;
import gbeic.bbsplusplus.ui.film.PovEditorAccess;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Per-film editor lock.
 *
 * <p>Each editor (camera / replay / optional POV) gets an extra right-click entry
 * which locks or unlocks that mode. Locking only filters the {@code ·} cycle and
 * dims the editor's action-bar icon; the mode itself stays fully usable and can
 * still be selected manually. The state is stored per film inside the film data
 * ({@code bbspp:editor_locks}).</p>
 */
public class FilmEditorLockManager
{
    public static final String FOREIGN_KEY = "bbspp:editor_locks";

    private static final String LOCKED_KEY = "locked";
    private static final int LOCKED_COLOR = 0xff777777;

    private static final Set<FilmEditorLockManager> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final UIFilmPanel panel;
    private final Map<FilmEditorMode, Binding> bindings = new LinkedHashMap<>();
    private final Set<FilmEditorMode> locked = new LinkedHashSet<>();

    private boolean installed;
    private boolean updating;

    public FilmEditorLockManager(UIFilmPanel panel)
    {
        this.panel = panel;
        ACTIVE.add(this);
    }

    /**
     * Called once per client tick. The POV addon installs its editor at the end of
     * {@code UIFilmPanel}'s constructor, so the manager keeps looking for it until
     * it appears.
     */
    public static void tickAll()
    {
        if (ACTIVE.isEmpty())
        {
            return;
        }

        for (FilmEditorLockManager manager : new ArrayList<>(ACTIVE))
        {
            manager.tick();
        }
    }

    private void tick()
    {
        if (this.installed && !this.hasPov())
        {
            this.syncPov();
        }
    }

    public void install()
    {
        if (this.installed)
        {
            return;
        }

        this.installed = true;

        this.addBinding(FilmEditorMode.CAMERA, this.panel.cameraEditor, this.panel.openCameraEditor);
        this.addBinding(FilmEditorMode.REPLAY, this.panel.replayEditor, this.panel.openReplayEditor);
        this.syncPov();
        this.replaceCycleKeybind();
        this.onVisibilityUpdated(this.panel.getData() != null);

        BBSPlusPlusMod.LOGGER.info("[EditorLock] installed, modes={}", this.bindings.keySet());
    }

    /* --------------------------------------------------------------------- */
    /* Hooks                                                                 */
    /* --------------------------------------------------------------------- */

    public void onFilmFilled()
    {
        if (!this.installed)
        {
            return;
        }

        this.readLocks();
        this.syncPov();
        this.applyStates();
    }

    public void onPanelShown()
    {
        if (!this.installed || this.updating)
        {
            return;
        }

        this.syncPov();
        this.applyStates();
    }

    /** 对应 updateMainEditorVisibility TAIL：同步 POV 绑定与锁定视觉状态。 */
    public void onVisibilityUpdated(boolean hasFilm)
    {
        if (!this.installed || this.updating)
        {
            return;
        }

        this.updating = true;

        try
        {
            this.syncPov();
            this.applyStates();
        }
        finally
        {
            this.updating = false;
        }
    }

    /* --------------------------------------------------------------------- */
    /* POV                                                                   */
    /* --------------------------------------------------------------------- */

    public boolean hasPov()
    {
        return this.bindings.containsKey(FilmEditorMode.POV);
    }

    public void syncPov()
    {
        if (this.bindings.containsKey(FilmEditorMode.POV))
        {
            return;
        }

        UIElement editor = PovEditorAccess.editor(this.panel);
        UIIcon icon = PovEditorAccess.icon(this.panel);

        if (editor == null || icon == null)
        {
            return;
        }

        this.addBinding(FilmEditorMode.POV, editor, icon);
        this.applyStates();

        BBSPlusPlusMod.LOGGER.info("[EditorLock] POV timeline attached");
    }

    /* --------------------------------------------------------------------- */
    /* Bindings and menu                                                     */
    /* --------------------------------------------------------------------- */

    private void addBinding(FilmEditorMode mode, UIElement editor, UIIcon icon)
    {
        if (editor == null || icon == null || this.bindings.containsKey(mode))
        {
            return;
        }

        this.bindings.put(mode, new Binding(mode, editor, icon));
        icon.context((menu) -> this.fillMenu(menu, mode));
    }

    private void fillMenu(ContextMenuManager menu, FilmEditorMode mode)
    {
        if (this.panel.getData() == null)
        {
            return;
        }

        if (this.locked.contains(mode))
        {
            menu.action(Icons.UNLOCKED, FilmEditorLockUIKeys.UNLOCK, () -> this.toggle(mode));
        }
        else if (this.unlockedCount() > 1)
        {
            menu.action(Icons.LOCKED, FilmEditorLockUIKeys.LOCK, () -> this.toggle(mode));
        }
    }

    private void toggle(FilmEditorMode mode)
    {
        if (this.panel.getData() == null)
        {
            return;
        }

        if (this.locked.contains(mode))
        {
            this.locked.remove(mode);
        }
        else
        {
            if (this.unlockedCount() <= 1)
            {
                return;
            }

            this.locked.add(mode);
        }

        this.applyStates();
        this.writeToFilm();

        Film film = this.panel.getData();

        if (film != null && film.getId() != null)
        {
            this.panel.save();
        }
    }

    private void applyStates()
    {
        for (Binding binding : this.bindings.values())
        {
            boolean isLocked = this.locked.contains(binding.mode);

            /* 锁定模式仍可点击：锁定只过滤 `·` 循环。 */
            binding.icon.callback = binding.originalCallback;
            binding.icon.iconColor(isLocked ? LOCKED_COLOR : Colors.WHITE);
            binding.icon.hoverColor(isLocked ? LOCKED_COLOR : Colors.LIGHTEST_GRAY);
        }
    }

    /* --------------------------------------------------------------------- */
    /* Editor cycle                                                          */
    /* --------------------------------------------------------------------- */

    private void replaceCycleKeybind()
    {
        for (Keybind keybind : this.panel.keys().keybinds)
        {
            if (keybind.callback == null)
            {
                continue;
            }

            if (keybind.getKeyCombo().equals(Keys.FILM_CONTROLLER_CYCLE_EDITORS.getKeyCombo())
                && keybind.getLabel() == Keys.FILM_CONTROLLER_CYCLE_EDITORS.label)
            {
                keybind.callback = this::cycle;

                return;
            }
        }

        BBSPlusPlusMod.LOGGER.warn("[EditorLock] cycle keybind not found for combo '{}'", Keys.FILM_CONTROLLER_CYCLE_EDITORS.getKeyCombo());
    }

    private void cycle()
    {
        FilmEditorMode selected = this.selectedMode();
        FilmEditorMode next = this.nextUnlocked(selected == null ? -1 : selected.ordinal());

        if (next == null)
        {
            return;
        }

        this.show(next);
        UIUtils.playClick();
    }

    private void show(FilmEditorMode mode)
    {
        Binding binding = this.bindings.get(mode);

        if (binding != null)
        {
            this.panel.showPanel(binding.editor);
        }
    }

    private FilmEditorMode selectedMode()
    {
        UIElement selected = this.access().bbspp_cml$getSelectedMainEditorPanel();

        for (Map.Entry<FilmEditorMode, Binding> entry : this.bindings.entrySet())
        {
            if (entry.getValue().editor == selected)
            {
                return entry.getKey();
            }
        }

        return null;
    }

    private FilmEditorMode nextUnlocked(int afterOrdinal)
    {
        FilmEditorMode[] modes = FilmEditorMode.values();

        for (int i = 1; i <= modes.length; i++)
        {
            FilmEditorMode mode = modes[Math.floorMod(afterOrdinal + i, modes.length)];

            if (!this.bindings.containsKey(mode) || this.locked.contains(mode))
            {
                continue;
            }

            /* 浮动中的编辑器不在 main 壳里，切过去会让主区空白，循环时跳过。 */
            if (this.panel.main != null && this.bindings.get(mode).editor.getParent() != this.panel.main)
            {
                continue;
            }

            return mode;
        }

        return null;
    }

    private int unlockedCount()
    {
        int count = 0;

        for (FilmEditorMode mode : this.bindings.keySet())
        {
            if (!this.locked.contains(mode))
            {
                count++;
            }
        }

        return count;
    }

    /* --------------------------------------------------------------------- */
    /* Per-film persistence                                                  */
    /* --------------------------------------------------------------------- */

    private void readLocks()
    {
        this.locked.clear();

        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        Map<String, BaseType> foreign = ((ValueGroupForeignAccess) (Object) film).bbspp$getForeign();
        BaseType raw = foreign == null ? null : foreign.get(FOREIGN_KEY);

        if (!(raw instanceof MapType map))
        {
            return;
        }

        BaseType lockedRaw = map.get(LOCKED_KEY);

        if (!(lockedRaw instanceof ListType list))
        {
            return;
        }

        for (int i = 0; i < list.size(); i++)
        {
            FilmEditorMode mode = FilmEditorMode.byId(list.getString(i, ""));

            if (mode != null)
            {
                this.locked.add(mode);
            }
        }

        /* Never leave every available editor locked out of the cycle. */
        if (!this.bindings.isEmpty() && this.unlockedCount() == 0)
        {
            this.locked.remove(this.bindings.keySet().iterator().next());
        }
    }

    private void writeToFilm()
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        ValueGroupForeignAccess access = (ValueGroupForeignAccess) (Object) film;
        Map<String, BaseType> foreign = access.bbspp$getForeign();

        if (foreign == null)
        {
            foreign = new LinkedHashMap<>();
            access.bbspp$setForeign(foreign);
        }

        MapType data = new MapType();
        ListType list = new ListType();

        for (FilmEditorMode mode : this.locked)
        {
            list.add(new StringType(mode.id()));
        }

        data.put(LOCKED_KEY, list);
        foreign.put(FOREIGN_KEY, data);
    }

    private UIFilmPanelLayoutAccessor access()
    {
        return (UIFilmPanelLayoutAccessor) (Object) this.panel;
    }

    /* --------------------------------------------------------------------- */
    /* Data                                                                  */
    /* --------------------------------------------------------------------- */

    private static final class Binding
    {
        private final FilmEditorMode mode;
        private final UIElement editor;
        private final UIIcon icon;
        private final Consumer<UIIcon> originalCallback;

        private Binding(FilmEditorMode mode, UIElement editor, UIIcon icon)
        {
            this.mode = mode;
            this.editor = editor;
            this.icon = icon;
            this.originalCallback = icon.callback;
        }
    }
}
