package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.SettingsBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CML 设置注册占位 Mixin。
 *
 * <p>原实现在此向 BBS 主设置追加 bbs_snow 分类，现已将全部 CML 设置迁移到
 * BBS Cubed 独立设置模块（由 {@link gbeic.bbsplusplus.BBSPlusPlusSettings} 注册），
 * 分属 BBS 增强 / CML 增强 / 导出增强等分类，不再需要往 bbs 主设置里注入分类。
 * 保留此 Mixin 是为了避免修改 mixins.json 注册列表，方法体保持空操作。</p>
 */
@Mixin(value = BBSSettings.class, remap = false)
public class BBSSettingsCMLMixin
{
    @Inject(method = "register", at = @At("TAIL"), remap = false)
    private static void bbspp_cml$appendCMLSettings(SettingsBuilder builder, CallbackInfo ci)
    {
        // CML 设置已迁移到 BBS Cubed 独立设置模块，此处不再向 bbs 主设置追加分类。
    }
}
