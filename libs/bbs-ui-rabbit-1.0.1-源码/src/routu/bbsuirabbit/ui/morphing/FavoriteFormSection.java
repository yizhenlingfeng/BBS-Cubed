package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitSettings;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.sections.FormSection;

import java.util.Collections;
import java.util.List;

/**
 * Holds the single {@link FavoriteFormCategory}. Registered through
 * {@code FormCategories.registerSection} so every palette rebuild recreates it — the file
 * is re-read in the category's constructor. When the new morphing panel is off the section
 * reports no categories, so the stock layout never sees it.
 */
public class FavoriteFormSection extends FormSection
{
    private FavoriteFormCategory category;

    public FavoriteFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        this.category = new FavoriteFormCategory(this.parent.preferences.visible(FavoriteFormCategory.ID));
    }

    @Override
    public List<FormCategory> getCategories()
    {
        boolean on = RabbitSettings.newMorphingPanel != null && RabbitSettings.newMorphingPanel.get();

        return !on || this.category == null ? Collections.emptyList() : Collections.singletonList(this.category);
    }
}
