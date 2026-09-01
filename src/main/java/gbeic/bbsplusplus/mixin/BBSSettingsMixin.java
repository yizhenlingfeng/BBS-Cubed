package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.SettingsBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BBS 设置注册占位 Mixin。
 * <p>
 * 原实现在此向 BBS 主设置追加 bbspp 分类，现已将 BBS++ 设置提升为
 * 独立设置模块（由 {@link gbeic.bbsplusplus.BBSPlusPlusSettings} 注册），
 * 不再需要往 bbs 主设置里注入分类。保留此 Mixin 是为了避免修改
 * mixins.json 注册列表，方法体保持空操作。
 * </p>
 */
@Mixin(BBSSettings.class)
public class BBSSettingsMixin
{
    @Inject(method = "register(Lmchorse/bbs_mod/settings/SettingsBuilder;)V", at = @At("TAIL"), remap = true)
    private static void afterRegister(SettingsBuilder builder, CallbackInfo ci)
    {
        // BBS++ 设置已迁移到独立设置模块，此处不再向 bbs 主设置追加分类。
    }
}
