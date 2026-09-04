package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 {@link Films} 的私有 controllers 列表。
 */
@Mixin(value = Films.class, remap = false)
public interface FilmsControllerAccessor
{
    @Accessor(value = "controllers", remap = false)
    List<BaseFilmController> bbspp_cml$getControllers();
}
