package gbeic.bbsplusplus.mixin.client.accessor;

import gbeic.bbsplusplus.api.FilmsControllerAccessor;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 {@link Films} 的私有 controllers 列表 —— 流体伪装的实体交互
 * 需要遍历所有正在播放的 film controller 找到 form 拥有者与互动实体
 * （CML 的 Films 有 getControllers()，bbs-fs 没有）。
 */
@Mixin(value = Films.class, remap = false)
public interface FilmsAccessor extends FilmsControllerAccessor
{
    @Accessor(value = "controllers", remap = false)
    List<BaseFilmController> bbspp_cml$getControllers();
}
