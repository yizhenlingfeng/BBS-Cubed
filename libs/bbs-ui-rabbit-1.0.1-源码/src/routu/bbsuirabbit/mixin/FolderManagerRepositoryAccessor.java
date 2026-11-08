package routu.bbsuirabbit.mixin;

import mchorse.bbs_mod.utils.manager.FolderManager;
import mchorse.bbs_mod.utils.repos.FolderManagerRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches into FolderManagerRepository so the film library home can read the real on-disk keys directly. */
@Mixin(value = FolderManagerRepository.class, remap = false)
public interface FolderManagerRepositoryAccessor
{
    @Accessor(value = "manager", remap = false)
    FolderManager<?> rabbit$getManager();
}
