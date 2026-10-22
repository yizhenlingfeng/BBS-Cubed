package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.utils.DataPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(UIDataPathList.class)
public interface UIDataPathListOpenAccessor
{
    @Invoker("open")
    void bbspp$open(DataPath path);
}
