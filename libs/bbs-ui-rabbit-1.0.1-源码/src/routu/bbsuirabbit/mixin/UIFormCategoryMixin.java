package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.ui.morphing.FavoriteForms;
import routu.bbsuirabbit.ui.morphing.UIBBSPPFormList;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import routu.bbsuirabbit.ui.morphing.MorphCellNames;
import routu.bbsuirabbit.ui.morphing.MorphLayout;
import routu.bbsuirabbit.ui.morphing.MorphLayoutStorage;
import routu.bbsuirabbit.ui.morphing.VirtualFolderFormCategory;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.ModelFormCategory;
import routu.bbsuirabbit.ui.morphing.MorphCellState;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = {UIFormCategory.class})
public abstract class UIFormCategoryMixin
{
    @Shadow(remap = false)
    private int viewMod;

    /** The pin version this category's search view was filtered against. */
    @Unique
    private int rabbit$pinVersion = -1;

    /* A pin change re-filters the cached view (the category's own forms didn't change) */
    @Inject(method = "getForms", at = @At("HEAD"), remap = false)
    private void rabbit$refilterOnPinChange(CallbackInfoReturnable<List<Form>> cir)
    {
        if (this.rabbit$pinVersion != MorphLayout.pinVersion)
        {
            this.rabbit$pinVersion = MorphLayout.pinVersion;
            this.viewMod = -1;
        }
    }

    /* In the BBS++ palette, a model filed into a virtual folder is listed there, not here */
    @WrapOperation(method = {"getForms", "renderHeader"}, at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/forms/categories/FormCategory;getForms()Ljava/util/List;"), remap = false)
    private List<Form> rabbit$withoutFiled(FormCategory category, Operation<List<Form>> original)
    {
        List<Form> forms = original.call(category);
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (!(category instanceof ModelFormCategory) || !(self.list instanceof UIBBSPPFormList))
        {
            return forms;
        }

        MorphLayout layout = MorphLayoutStorage.get();

        if (layout.pins.isEmpty())
        {
            return forms;
        }

        List<Form> out = new ArrayList<>(forms.size());

        for (Form form : forms)
        {
            String ref = VirtualFolderFormCategory.refOf(form);

            if (ref == null || layout.pinnedFolder(ref) == null)
            {
                out.add(form);
            }
        }

        return out;
    }

    /** Focus mode keeps every shown category open: no folding by header click. */
    @Inject(method = {"toggle"}, at = {@At(value = "HEAD")}, cancellable = true, remap = true)
    private void rabbit$cancelToggleInFocusMode(CallbackInfo ci)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (self.list instanceof UIBBSPPFormList list && list.isFocusMode())
        {
            ci.cancel();
        }
    }

    @Inject(method = {"isExpanded"}, at = {@At(value = "HEAD")}, cancellable = true, remap = true)
    private void rabbit$alwaysExpandedInFocusMode(CallbackInfoReturnable<Boolean> cir)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (self.list instanceof UIBBSPPFormList list && list.isFocusMode())
        {
            cir.setReturnValue(true);
        }
    }

    /** Appends the favourite actions to the context menu the category built. */
    @Inject(method = {"buildContextMenu"}, at = {@At(value = "RETURN")}, remap = true)
    private void rabbit$addFavoriteActions(ContextMenuManager menu, CallbackInfo ci)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (!(self.list instanceof UIBBSPPFormList))
        {
            return;
        }

        Form form = self.getContextForm();

        java.io.File folder = form == null ? null : routu.bbsuirabbit.ui.morphing.MorphFolders.ofForm(form);

        if (folder != null)
        {
            menu.action(Icons.FOLDER, L10n.lang("rabbit.ui.morph.open_folder"), () -> mchorse.bbs_mod.ui.utils.UIUtils.openFolder(folder));
        }

        if (self.category instanceof VirtualFolderFormCategory && form != null)
        {
            List<Form> forms = self.isGroupContext() ? new ArrayList<>(self.list.selection.getItems()) : List.of(form);
            UIBBSPPFormList list = (UIBBSPPFormList) self.list;

            menu.action(Icons.UNDO, L10n.lang("rabbit.ui.morph.move_back"), () ->
            {
                for (Form filed : forms)
                {
                    String ref = VirtualFolderFormCategory.refOf(filed);

                    if (ref != null)
                    {
                        MorphLayoutStorage.get().unpin(ref);
                    }
                }

                MorphLayoutStorage.save();
                list.requestFolderRebuild();
            });
        }

        if (self.isGroupContext())
        {
            String count = String.valueOf(self.list.selection.size());

            menu.action(Icons.FAVORITE, L10n.lang("rabbit.ui.morph.favorite_selected").format(count), () ->
            {
                FavoriteForms.toggleAll(new ArrayList<>(self.list.selection.getItems()));
                self.list.reconcile();
            });
        }
        else if (form != null)
        {
            boolean favorited = FavoriteForms.isFavorite(form);

            menu.action(Icons.FAVORITE, L10n.lang(favorited ? "rabbit.ui.morph.unfavorite" : "rabbit.ui.morph.favorite"), () ->
            {
                FavoriteForms.toggle(form);
                self.list.reconcile();
            });
        }
    }

    /* A category under a coloured folder (or a coloured category of the user's) carries the
     * colour on its header in the BBS++ palette — drawn over the header ground, under the text */
    @Inject(method = "renderHeader", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/elements/utils/RowStyle;row(Lmchorse/bbs_mod/ui/framework/elements/utils/Batcher2D;IIIIIZZZ)V"), remap = false)
    private void rabbit$tintHeader(UIContext context, CallbackInfo ci)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;
        int tint = this.rabbit$tint();

        if (tint != 0)
        {
            routu.bbsuirabbit.ui.morphing.UIBBSPPCategorySidebar.renderTint(context.batcher, self.area.x, self.area.y, self.area.w, 20, tint, true);
        }
    }

    /* ...and a lighter wash of it behind the cells, with the header's stripe running down the side */
    @Inject(method = "renderContent", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/forms/categories/UIFormCategory;renderCells(Lmchorse/bbs_mod/ui/framework/UIContext;)V"), remap = false)
    private void rabbit$tintCells(UIContext context, CallbackInfo ci)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;
        int tint = this.rabbit$tint();

        if (tint != 0 && self.area.h > 20)
        {
            int rgb = tint & 0xFFFFFF;
            int y = self.area.y + 20;

            context.batcher.box(self.area.x, y, self.area.ex(), self.area.ey(), 0x22000000 | rgb);
            context.batcher.box(self.area.x, y, self.area.x + 3, self.area.ey(), 0xB0000000 | rgb);
        }
    }

    /** The colour this category carries in the BBS++ palette (its own or its nearest coloured folder's), 0 = none. */
    @Unique
    private int rabbit$tint()
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (!(self.list instanceof UIBBSPPFormList list) || list.getTree() == null)
        {
            return 0;
        }

        routu.bbsuirabbit.ui.morphing.MorphNavTree.Node<UIFormCategory> node = list.getTree().nodeOf(self);

        return node == null ? 0 : MorphLayoutStorage.get().colorOf(node);
    }

    /* Marks the cell being rendered so the FormCellRenderer mixin can upgrade particle
     * cells (live preview, compact caption) — only inside the BBS++ palette. */
    @Inject(method = {"renderCell"}, at = {@At(value = "HEAD")}, remap = false)
    private void rabbit$morphCellEnter(UIContext context, Form form, int x, int y, int w, int h, CellState state, CallbackInfo ci)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (self.list instanceof UIBBSPPFormList)
        {
            MorphCellState.enter(form, state);
        }
    }

    @Inject(method = {"renderCell"}, at = {@At(value = "RETURN")}, remap = false)
    private void rabbit$morphCellLeave(UIContext context, Form form, int x, int y, int w, int h, CellState state, CallbackInfo ci)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (self.list instanceof UIBBSPPFormList)
        {
            MorphCellState.leave();

            if (w < mchorse.bbs_mod.ui.forms.FormCellRenderer.NAME_THRESHOLD && MorphCellNames.shows(form))
            {
                String label = MorphCellNames.compactLabel(form);
                mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D batcher = context.batcher;
                mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer font = batcher.getFont();

                batcher.box(x, y + h - 10, x + w, y + h, state.hover || state.selected ? 0xB0000000 : 0x80000000);
                routu.bbsuirabbit.client.ui.home.Gfx.text(batcher,
                    font.limitToWidth(label, Math.max(4, (int) (w / 0.75F) - 6)),
                    x + 3, y + h - 8, 0.75F, state.hover || state.selected ? 0xFFFFFFFF : routu.bbsuirabbit.client.ui.home.Gfx.inkSoft(1F));
            }
        }
    }

    /* The cursor names an unnamed particle (BBS or vanilla) by its effect / type, as the cell does. */
    @Inject(method = "caption(Lmchorse/bbs_mod/forms/forms/Form;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$particleCaption(Form form, CallbackInfoReturnable<String> cir)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (self.list instanceof UIBBSPPFormList && form != null)
        {
            String name = MorphCellNames.particleName(form);

            if (name != null)
            {
                cir.setReturnValue(name);
            }
        }
    }

    /* A kind whose name strip is switched off still gets named by the cursor on hover. */
    @Inject(method = "showsCaption(Lmchorse/bbs_mod/ui/framework/UIContext;Lmchorse/bbs_mod/forms/forms/Form;I)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$captionWhenStripOff(UIContext context, Form form, int cellWidth, CallbackInfoReturnable<Boolean> cir)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        if (self.list instanceof UIBBSPPFormList && form != null && !MorphCellNames.shows(form))
        {
            cir.setReturnValue(false);
        }
    }
}
