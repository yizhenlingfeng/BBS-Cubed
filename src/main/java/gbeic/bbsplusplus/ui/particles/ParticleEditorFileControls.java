package gbeic.bbsplusplus.ui.particles;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.particles.ParticleMaterial;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;

/** Adds file commands and snapshot-based edit history to the particle editor. */
public final class ParticleEditorFileControls extends UIElement
{
    private static final long HISTORY_DEBOUNCE_MS = 300L;

    private final UIParticleSchemePanel panel;
    private final UIIcon saveAs;
    private final UIIcon history;

    private UndoManager<ValueGroup> undoManager = new UndoManager<>(100);
    private ParticleScheme trackedScheme;
    private MapType stableSnapshot;
    private MapType pendingBefore;
    private MapType pendingAfter;
    private long lastChangeTime;
    private boolean applyingHistory;

    public static void attach(UIParticleSchemePanel panel)
    {
        new ParticleEditorFileControls(panel);
    }

    private ParticleEditorFileControls(UIParticleSchemePanel panel)
    {
        this.panel = panel;
        this.saveAs = new UIIcon(Icons.COPY, (button) -> this.openSaveAs());
        this.history = new UIIcon(Icons.LIST, (button) -> this.openHistory());

        panel.openOverlay.both(Icons.FOLDER).tooltip(ParticlePlusUIKeys.OPEN, Direction.LEFT);
        panel.saveIcon.tooltip(UIKeys.GENERAL_SAVE, Direction.LEFT);
        this.saveAs.tooltip(ParticlePlusUIKeys.SAVE_AS, Direction.LEFT);
        this.history.tooltip(ParticlePlusUIKeys.HISTORY, Direction.LEFT);
        panel.iconBar.addAfter(panel.saveIcon, this.saveAs);
        panel.iconBar.addAfter(this.saveAs, this.history);

        panel.keys().register(Keys.UNDO, this::undo)
            .active(() -> panel.getData() != null)
            .category(UIKeys.SNOWSTORM_TITLE);
        panel.keys().register(Keys.REDO, this::redo)
            .active(() -> panel.getData() != null)
            .category(UIKeys.SNOWSTORM_TITLE);

        this.noCulling();
        this.relative(panel).w(1F).h(1F);
        panel.add(this);
        this.syncTrackedScheme();
    }

    @Override
    public void render(UIContext context)
    {
        this.syncTrackedScheme();
        this.observeChanges();

        boolean enabled = this.panel.getData() != null;

        this.saveAs.setEnabled(enabled);
        this.history.setEnabled(enabled);
        super.render(context);
    }

    private void syncTrackedScheme()
    {
        ParticleScheme current = this.panel.getData();

        if (current == this.trackedScheme)
        {
            return;
        }

        this.trackedScheme = current;
        this.undoManager = new UndoManager<>(100);
        this.stableSnapshot = current == null ? null : this.snapshot(current);
        this.clearPending();
    }

    private void observeChanges()
    {
        if (this.applyingHistory || this.trackedScheme == null)
        {
            return;
        }

        MapType current = this.snapshot(this.trackedScheme);

        if (current.equals(this.stableSnapshot))
        {
            this.clearPending();
            return;
        }

        if (this.pendingBefore == null)
        {
            this.pendingBefore = this.copy(this.stableSnapshot);
            this.pendingAfter = current;
            this.lastChangeTime = System.currentTimeMillis();
            return;
        }

        if (!current.equals(this.pendingAfter))
        {
            this.pendingAfter = current;
            this.lastChangeTime = System.currentTimeMillis();
        }
        else if (System.currentTimeMillis() - this.lastChangeTime >= HISTORY_DEBOUNCE_MS)
        {
            this.commitPending();
        }
    }

    private void flushPending()
    {
        this.syncTrackedScheme();
        this.observeChanges();
        this.commitPending();
    }

    private void commitPending()
    {
        if (this.pendingBefore == null || this.pendingAfter == null)
        {
            return;
        }

        this.undoManager.pushUndo(new ParticleSnapshotUndo(this, this.pendingBefore, this.pendingAfter));
        this.stableSnapshot = this.copy(this.pendingAfter);
        this.clearPending();
    }

    private void clearPending()
    {
        this.pendingBefore = null;
        this.pendingAfter = null;
        this.lastChangeTime = 0L;
    }

    private void undo()
    {
        this.flushPending();

        if (this.trackedScheme != null && this.undoManager.undo(this.trackedScheme))
        {
            UIUtils.playClick();
        }
    }

    private void redo()
    {
        this.flushPending();

        if (this.trackedScheme != null && this.undoManager.redo(this.trackedScheme))
        {
            UIUtils.playClick();
        }
    }

    private void openHistory()
    {
        this.flushPending();

        if (this.trackedScheme != null)
        {
            UIOverlay.addOverlay(
                this.panel.getContext(),
                new UIUndoHistoryOverlay(
                    ParticlePlusUIKeys.HISTORY_TITLE,
                    this.undoManager,
                    this.panel::getData,
                    null
                ),
                200,
                0.6F
            );
        }
    }

    private void openSaveAs()
    {
        ParticleScheme data = this.panel.getData();

        if (data == null)
        {
            return;
        }

        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
            ParticlePlusUIKeys.SAVE_AS,
            ParticlePlusUIKeys.SAVE_AS_DESCRIPTION,
            this::saveAs
        );
        String id = data.getId();
        int slash = id == null ? -1 : id.lastIndexOf('/');

        prompt.text.setText(id == null ? "" : id.substring(slash + 1));
        prompt.text.filename();
        UIOverlay.addOverlay(this.panel.getContext(), prompt);
    }

    private void saveAs(String filename)
    {
        ParticleScheme source = this.panel.getData();

        if (source == null || filename == null || filename.trim().isEmpty())
        {
            return;
        }

        String id = this.panel.overlay.namesList.getPath(filename.trim()).toString();

        if (this.panel.overlay.namesList.hasInHierarchy(id))
        {
            this.panel.getContext().notifyError(ParticlePlusUIKeys.SAVE_AS_EXISTS.format(id));
            return;
        }

        this.flushPending();
        this.panel.save();

        ParticleScheme copy = ParticleScheme.dupe(source);

        if (copy == null)
        {
            return;
        }

        copy.setId(id);
        this.panel.overlay.namesList.addFile(id);
        this.panel.fill(copy);
        this.panel.forceSave();
        this.panel.requestNames();
        this.panel.getContext().notifySuccess(ParticlePlusUIKeys.SAVE_AS_SUCCESS.format(id));
    }

    private MapType snapshot(ParticleScheme scheme)
    {
        return scheme.toData().asMap();
    }

    private MapType copy(MapType data)
    {
        return data == null ? null : (MapType) data.copy();
    }

    private void applySnapshot(MapType snapshot)
    {
        ParticleScheme target = this.panel.getData();

        if (target == null)
        {
            return;
        }

        String id = target.getId();

        this.applyingHistory = true;

        try
        {
            target.identifier = "";
            target.material = ParticleMaterial.OPAQUE;
            target.texture = ParticleScheme.DEFAULT_TEXTURE;
            target.curves.clear();
            target.components.clear();
            target.fromData(this.copy(snapshot));
            target.setId(id);
            this.stableSnapshot = this.copy(snapshot);
            this.clearPending();
            this.panel.fill(target);
        }
        finally
        {
            this.applyingHistory = false;
        }
    }

    private static final class ParticleSnapshotUndo implements IUndo<ValueGroup>
    {
        private final ParticleEditorFileControls controls;
        private final MapType before;
        private final MapType after;

        private ParticleSnapshotUndo(ParticleEditorFileControls controls, MapType before, MapType after)
        {
            this.controls = controls;
            this.before = controls.copy(before);
            this.after = controls.copy(after);
        }

        @Override
        public IUndo<ValueGroup> noMerging()
        {
            return this;
        }

        @Override
        public boolean isMergeable(IUndo<ValueGroup> undo)
        {
            return false;
        }

        @Override
        public void merge(IUndo<ValueGroup> undo)
        {}

        @Override
        public void undo(ValueGroup context)
        {
            this.controls.applySnapshot(this.before);
        }

        @Override
        public void redo(ValueGroup context)
        {
            this.controls.applySnapshot(this.after);
        }

        @Override
        public String toString()
        {
            return ParticlePlusUIKeys.HISTORY_ENTRY.get();
        }
    }
}
