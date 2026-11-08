package routu.bbsuirabbit.mixin;

import java.util.List;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={UIFormList.class}, remap=true)
public interface UIFormListAccessor {
    @Accessor(value="categories")
    public List<UIFormCategory> getCategories();
}
