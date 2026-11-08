package routu.bbsuirabbit.ui.morphing;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The favourites category: user-managed (canModify) but persisted by {@link FavoriteForms}
 * rather than the user-categories file. It deliberately doesn't mark the palette dirty —
 * dropping a form in shouldn't trigger a full rebuild of the category list.
 */
public class FavoriteFormCategory extends FormCategory
{
    public static final String ID = "bbspp_favorites";

    public FavoriteFormCategory(ValueBoolean visibility)
    {
        super(L10n.lang("rabbit.ui.morph.favorites"), visibility);

        this.icon(Icons.FAVORITE);

        for (Form form : FavoriteForms.load())
        {
            super.insertForm(this.getDirectForms().size(), form);
        }

        FavoriteForms.bind(this);
    }

    @Override
    public boolean canModify(Form form)
    {
        return true;
    }

    @Override
    public void insertForm(int index, Form form)
    {
        super.insertForm(index, form);
        FavoriteForms.save();
    }

    @Override
    public void replaceForm(int index, Form form)
    {
        super.replaceForm(index, form);
        FavoriteForms.save();
    }

    @Override
    public void moveForm(Form form, int to)
    {
        super.moveForm(form, to);
        FavoriteForms.save();
    }

    @Override
    public void removeForm(Form form)
    {
        super.removeForm(form);
        FavoriteForms.save();
    }

    @Override
    public void clearForms()
    {
        super.clearForms();
        FavoriteForms.save();
    }
}
