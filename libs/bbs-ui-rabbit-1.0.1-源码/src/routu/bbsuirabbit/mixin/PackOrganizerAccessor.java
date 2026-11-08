package routu.bbsuirabbit.mixin;

import net.minecraft.class_3283;
import net.minecraft.class_3288;
import net.minecraft.class_5369;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Vanilla's pack screen state: the selected packs (top first), the available ones, and their manager. */
@Mixin(value = class_5369.class, remap = false)
public interface PackOrganizerAccessor
{
    @Accessor(value = "field_25455", remap = false)
    List<class_3288> rabbit$getEnabled();

    @Accessor(value = "field_25456", remap = false)
    List<class_3288> rabbit$getAvailable();

    @Accessor(value = "field_25626", remap = false)
    class_3283 rabbit$getManager();
}
