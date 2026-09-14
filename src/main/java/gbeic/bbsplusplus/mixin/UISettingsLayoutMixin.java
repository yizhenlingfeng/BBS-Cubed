package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.ui.morphing.UIBlockbenchPathRow;
import mchorse.bbs_mod.settings.ui.UISettingsLayout;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 把 Blockbench.exe 路径设置项注册成「整行宽度」的路径行（文本框 + 浏览按钮），
 * 与 BBS 本体注册 ffmpeg 编码器路径（UIEncoderPathRow）同一套机制。
 *
 * <p>BBS 在 {@link UISettingsLayout#build()} 里把特殊行按 Value 实例登记到 rows 表；
 * 本 Mixin 在该方法 TAIL 追加我们自己的那一行。rows 是懒加载、且在设置注册完成后才首次访问，
 * 因此 {@link BBSAddonsSettings#blockbenchPath} 此时必然已存在。</p>
 */
@Mixin(value = UISettingsLayout.class, remap = false)
public abstract class UISettingsLayoutMixin
{
    @Shadow
    private static Map<BaseValue, UISettingsLayout.IValueRow> rows;

    @Inject(method = "build", at = @At("TAIL"), remap = false)
    private static void bbspp$registerBlockbenchPathRow(CallbackInfo ci)
    {
        if (BBSAddonsSettings.blockbenchPath != null)
        {
            rows.put(BBSAddonsSettings.blockbenchPath,
                new UIBlockbenchPathRow(BBSAddonsSettings.blockbenchPath));
        }
    }
}
