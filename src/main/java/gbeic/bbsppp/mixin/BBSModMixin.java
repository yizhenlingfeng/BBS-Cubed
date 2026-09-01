package gbeic.bbsppp.mixin;

import gbeic.bbsppp.BBSPPPSettings;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.function.Consumer;

/**
 * 在 BBS 主设置对象创建完成后追加 BBSPPP 私有设置项。
 * <p>
 * 早先直接注入 {@code BBSSettings.register(...)} 会受其它插件 Mixin 顺序影响：
 * 如果 BBSPPP 先于 BBS++ 执行，就拿不到 {@code bbspp} 分类，开关会静默消失。
 * 这里改为注入 {@link BBSMod#setupConfig(Icon, String, File, Consumer)} 返回点，
 * 此时 BBS++ 已经完成分类创建，BBSPPP 可以稳定把私有开关挂到同一个设置页里。
 * </p>
 */
@Mixin(BBSMod.class)
public class BBSModMixin
{
    /**
     * 注入目标：BBS 完成单个设置模块创建后。
     * 注入原因：需要在 BBS++ 设置分类稳定存在之后，再追加 BBSPPP 的私有开关。
     * 修改行为：当设置模块是主 {@code bbs} 配置时，将“导出音频字幕”开关注入 {@code bbspp} 分类。
     */
    @Inject(method = "setupConfig", at = @At("RETURN"), remap = false)
    private static void bbsppp$registerPrivateSettings(Icon icon, String id, File destination, Consumer<SettingsBuilder> registerer, CallbackInfoReturnable<Settings> cir)
    {
        BBSPPPSettings.registerIntoBBSPlusPlus(cir.getReturnValue(), true);
    }
}
