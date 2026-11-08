package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.client.ui.home.Gfx;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataStringifier;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import net.minecraft.class_310;

import java.util.ArrayList;
import java.util.List;

/**
 * The right-hand details drawer of the new morphing panel: a draggable 3D preview of the
 * picked form, its name/id/hotkey/category path, and the same actions the context menu
 * offers (edit, favourite, copies, model folder, model editor). With several forms picked
 * it shows the count and a favourite-all button instead.
 */
public class UIBBSPPFormDetails extends UIElement
{
    private final UIBBSPPFormList list;
    private final UIFormRenderer preview;
    private final List<DrawerButton> buttons = new ArrayList<>();
    private final List<DrawerButton> groupButtons = new ArrayList<>();

    private Form form;
    private boolean favorited;
    private String path = "";

    private class DrawerButton extends UIElement
    {
        final Icon icon;
        IKey label;
        final Runnable action;
        float hover;

        DrawerButton(Icon icon, IKey label, Runnable action)
        {
            this.icon = icon;
            this.label = label;
            this.action = action;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                this.action.run();

                return true;
            }

            return false;
        }

        @Override
        public void render(UIContext context)
        {
            boolean inside = this.area.isInside(context);

            this.hover = Gfx.approach(this.hover, inside ? 1F : 0F, 6F, 0.016F);

            Gfx.roundRect(context.batcher, this.area.x, this.area.y, this.area.w, this.area.h, 5, Gfx.wash(0.06F + 0.10F * this.hover));

            int my = (int) this.area.my();

            context.batcher.icon(this.icon, Gfx.inkSoft(1F), this.area.x + 12, my, 0.5F, 0.5F);
            context.batcher.text(this.label.get(), this.area.x + 24, my - context.batcher.getFont().getHeight() / 2 + 1, Gfx.ink(1F));
        }
    }

    public UIBBSPPFormDetails(UIBBSPPFormList list)
    {
        this.list = list;

        this.preview = new UIFormRenderer();
        this.preview.grid = false;

        this.buttons.add(new DrawerButton(Icons.EDIT, L10n.lang("rabbit.ui.morph.details.edit"), () -> this.list.palette.toggleEditor()));
        this.buttons.add(new DrawerButton(Icons.FAVORITE, L10n.lang("rabbit.ui.morph.favorite"), () ->
        {
            if (this.form != null)
            {
                FavoriteForms.toggle(this.form);
                this.list.reconcile();
            }
        }));
        this.buttons.add(new DrawerButton(Icons.COPY, L10n.lang("rabbit.ui.morph.details.copy_form"), () ->
        {
            if (this.form != null)
            {
                Window.setClipboard(FormUtils.toData(this.form));
            }
        }));
        this.buttons.add(new DrawerButton(Icons.COPY, L10n.lang("rabbit.ui.morph.details.copy_command"), this::copyMorphCommand));
        this.buttons.add(new DrawerButton(Icons.FOLDER, L10n.lang("rabbit.ui.morph.details.open_model_folder"), () ->
        {
            if (this.form instanceof ModelForm modelForm && !modelForm.model.get().isEmpty())
            {
                UIUtils.openFolder(BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelForm.model.get() + "/"));
            }
        }));
        this.buttons.add(new DrawerButton(Icons.POSE, L10n.lang("rabbit.ui.morph.details.open_model_editor"), this::openModelEditor));

        this.groupButtons.add(new DrawerButton(Icons.FAVORITE, L10n.lang("rabbit.ui.morph.favorite_selected"), () ->
        {
            FavoriteForms.toggleAll(new ArrayList<>(this.list.selection.getItems()));
            this.list.reconcile();
        }));

        this.add(this.preview);

        for (DrawerButton button : this.buttons)
        {
            this.add(button);
        }

        for (DrawerButton button : this.groupButtons)
        {
            this.add(button);
        }
    }

    private void copyMorphCommand()
    {
        if (this.form == null || class_310.method_1551().field_1724 == null)
        {
            return;
        }

        MapType data = FormUtils.toData(this.form);
        DataStringifier stringifier = new DataStringifier();
        String name = class_310.method_1551().field_1724.method_7334().getName();

        stringifier.jsonLike();
        stringifier.indent = "";

        Window.setClipboard("/bbs morph " + name + " " + stringifier.toString(data));
    }

    private void openModelEditor()
    {
        if (!(this.form instanceof ModelForm modelForm))
        {
            return;
        }

        UIMorphingPanel morphing = this.getParent(UIMorphingPanel.class);
        String model = modelForm.model.get();

        if (morphing == null || model.isEmpty())
        {
            return;
        }

        UIDashboard dashboard = morphing.dashboard;
        UIModelEditorPanel panel = dashboard.getPanel(UIModelEditorPanel.class);

        dashboard.setPanel(panel);
        panel.pickData(model);
    }

    @Override
    public void resize()
    {
        super.resize();

        int pad = 8;
        int previewH = Math.min(this.area.w - pad * 2, 160);

        this.preview.area.set(this.area.x + pad, this.area.y + pad, this.area.w - pad * 2, previewH);

        int by = this.area.ey() - pad - this.buttons.size() * 26;

        for (DrawerButton button : this.buttons)
        {
            button.area.set(this.area.x + pad, by, this.area.w - pad * 2, 22);
            by += 26;
        }

        int gy = this.area.ey() - pad - this.groupButtons.size() * 26;

        for (DrawerButton button : this.groupButtons)
        {
            button.area.set(this.area.x + pad, gy, this.area.w - pad * 2, 22);
            gy += 26;
        }
    }

    @Override
    public void render(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Gfx.deep(0.85F));
        batcher.box(this.area.x, this.area.y, this.area.x + 1, this.area.ey(), Gfx.divider(0.6F));

        Form selected = this.list.getSelected();
        boolean group = this.list.selection.isGroup() && this.list.selection.size() > 1;
        boolean modelActions = selected instanceof ModelForm modelForm && !modelForm.model.get().isEmpty();

        this.form = selected;
        this.preview.form = selected;
        this.preview.setVisible(selected != null && !group);

        for (int i = 0; i < this.buttons.size(); i++)
        {
            boolean show = selected != null && !group;

            /* The last two buttons are model-only */
            if (i >= 4)
            {
                show = show && modelActions && (i == 4 || this.getParent(UIMorphingPanel.class) != null);
            }

            this.buttons.get(i).setVisible(show);
        }

        for (DrawerButton button : this.groupButtons)
        {
            button.setVisible(group);
        }

        /* Children (preview + visible buttons) draw and handle their own clicks */
        super.render(context);

        if (group)
        {
            int y = this.area.y + 16;

            batcher.textShadow(L10n.lang("rabbit.ui.morph.selected_count").format(String.valueOf(this.list.selection.size())).get(), this.area.x + 10, y, Gfx.ink(1F));

            return;
        }

        if (selected == null)
        {
            String hint = L10n.lang("rabbit.ui.morph.empty_details").get();
            String favHint = L10n.lang("rabbit.ui.morph.favorite_hint").get();

            batcher.text(font.limitToWidth(hint, this.area.w - 16), this.area.x + 10, this.area.y + 16, Gfx.inkMuted(0.9F));
            batcher.text(font.limitToWidth(favHint, this.area.w - 16), this.area.x + 10, this.area.y + 30, Gfx.inkMuted(0.7F));

            return;
        }

        int y = this.preview.area.ey() + 8;

        this.favorited = FavoriteForms.isFavorite(selected);
        this.path = this.pathOf(selected);

        this.buttons.get(1).label = L10n.lang(this.favorited ? "rabbit.ui.morph.unfavorite" : "rabbit.ui.morph.favorite");

        String name = font.limitToWidth(selected.getDisplayName(), this.area.w - 20);

        batcher.textShadow(name, this.area.x + 10, y, Gfx.ink(1F));
        y += font.getHeight() + 4;

        String id = font.limitToWidth(selected.getFormId(), this.area.w - 20);

        batcher.text(id, this.area.x + 10, y, Gfx.inkMuted(0.9F));
        y += font.getHeight() + 6;

        if (selected.hotkey.get() > 0)
        {
            batcher.textCard(KeyCodes.getName(selected.hotkey.get()), this.area.x + 10, y, Gfx.ink(1F), Gfx.accent(0.5F), 2);
            y += font.getHeight() + 10;
        }

        if (!this.path.isEmpty())
        {
            String path = font.limitToWidth(this.path, this.area.w - 20);

            batcher.text(path, this.area.x + 10, y, Gfx.inkMuted(0.7F));
        }
    }

    /** The node's ancestor group chain, drawn as "models › a › b". */
    private String pathOf(Form form)
    {
        if (this.list.getTree() == null)
        {
            return "";
        }

        for (UIFormCategory cat : ((routu.bbsuirabbit.mixin.UIFormListAccessor) this.list).getCategories())
        {
            if (cat.category.getForms().contains(form) || cat.getForms().contains(form))
            {
                MorphNavTree.Node<UIFormCategory> node = this.list.getTree().nodeOf(cat);

                if (node == null)
                {
                    return "";
                }

                List<String> parts = new ArrayList<>();

                for (MorphNavTree.Node<UIFormCategory> n = node; n != null; n = n.parent)
                {
                    parts.add(0, n.label);
                }

                return String.join(" › ", parts);
            }
        }

        return "";
    }
}
