package routu.bbsuirabbit.ui.morphing;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/**
 * The forms filed into one virtual folder ({@link MorphLayout#pins}), shown as the folder's
 * own category. Built fresh on every palette rebuild from copies of the models' own forms;
 * it takes drops (the list turns them into pins) but never gives its forms away — dragging
 * one out copies it, "move back" in the context menu un-files it.
 */
public class VirtualFolderFormCategory extends FormCategory
{
    public final String folderId;

    public VirtualFolderFormCategory(String folderId, String label, List<Form> forms)
    {
        super(IKey.constant(label), new ValueBoolean("bbspp_" + folderId, true));

        this.folderId = folderId;
        this.icon(Icons.FOLDER);

        for (Form form : forms)
        {
            super.insertForm(this.getDirectForms().size(), form);
        }
    }

    /** The ref a form is filed by, or null for forms that can't be filed (only models so far). */
    public static String refOf(Form form)
    {
        if (form instanceof ModelForm model && !model.model.get().isEmpty())
        {
            return "model:" + model.model.get();
        }

        return null;
    }

    @Override
    public boolean canModify(Form form)
    {
        return form == null;
    }

    /** A paste lands as a pin; the list rebuild then shows the model's own form. */
    @Override
    public void insertForm(int index, Form form)
    {
        String ref = refOf(form);

        if (ref == null)
        {
            return;
        }

        MorphLayoutStorage.get().pin(ref, this.folderId, index);
        MorphLayoutStorage.save();
        super.insertForm(index, form);
    }
}
