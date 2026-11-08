package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.util.SettingsCategoryMigration;
import gbeic.bbsplusplus.util.SettingsFileSanitizer;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.SettingsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * BBS 设置加载前的文件预处理。
 * <p>
 * BBS 主设置：BBSFS 2.4 的颜色列表读取逻辑仍会在设置重载时把文件里的颜色追加到旧列表后面，
 * 导致 {@code recent_colors} 和 {@code favorite_colors} 被重复写入；本补丁在解析前用流式方式
 * 瘦身主设置文件。BBS Cubed 设置：在解析前把旧分类键迁移到新的功能分类。
 * </p>
 */
@Mixin(SettingsManager.class)
public abstract class SettingsManagerMixin
{
    /**
     * 注入目标：{@code SettingsManager#load(Settings, File)} 入口。
     * 注入原因：颜色数组瘦身与分类迁移都必须在 {@code DataToString.read(file)} 完整解析之前完成。
     */
    @Inject(method = "load", at = @At("HEAD"), remap = true)
    private void bbspp$prepareSettingsFile(Settings settings, File file, CallbackInfoReturnable<Boolean> cir)
    {
        if (settings == null)
        {
            return;
        }

        if ("bbs".equals(settings.getId()))
        {
            SettingsFileSanitizer.sanitizeBbsSettings(file);
        }
        else if (SettingsCategoryMigration.SETTINGS_ID.equals(settings.getId()))
        {
            SettingsCategoryMigration.migrate(file);
        }
    }
}
