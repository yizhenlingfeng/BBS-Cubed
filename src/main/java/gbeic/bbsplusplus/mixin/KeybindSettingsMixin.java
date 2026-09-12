package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.ui.utils.keys.KeybindSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 将 BBS++ 自定义快捷键所在的 SnowUIKeys 类注册到 BBS 的按键绑定扫描列表中。
 *
 * BBS 原版的 KeybindSettings 通过反射扫描指定类中的 KeyCombo 静态字段，
 * 将其收集到按键绑定设置界面中。原版只扫描 mchorse.bbs_mod.ui.Keys 类，
 * 本 Mixin 在 registerClasses() 执行完成后追加 SnowUIKeys.class，
 * 使其中的自定义快捷键（如 Shift+W 选择整列关键帧）也出现在
 * UI 按键绑定设置的"关键帧编辑器"分类栏中，且可被用户修改。
 */
@Mixin(KeybindSettings.class)
public abstract class KeybindSettingsMixin
{
    @Shadow public static List<Class<?>> classes;

    @Inject(method = "registerClasses", at = @At("TAIL"), remap = false)
    private static void bbspp$registerSnowUIKeys(CallbackInfo ci)
    {
        classes.add(SnowUIKeys.class);
    }
}
