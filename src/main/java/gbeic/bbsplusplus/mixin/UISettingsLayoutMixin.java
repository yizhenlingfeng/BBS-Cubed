package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.settings.ModSettings;
import gbeic.bbsplusplus.ui.morphing.UIBlockbenchPathRow;
import gbeic.bbsplusplus.ui.settings.UIProjectSettingsRow;
import gbeic.bbsplusplus.ui.settings.UIStepValueRow;
import mchorse.bbs_mod.settings.ui.UISettingsLayout;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 给 BBS 的设置界面追加本模组的自定义行：Blockbench.exe 路径行，以及四个 XYZ 变换步长行。
 *
 * <p>BBS 在 {@link UISettingsLayout#build()} 里把特殊行按 Value 实例登记到 rows 表，本 Mixin
 * 在该方法 TAIL 追加我们自己的行；rows 是懒加载、且在设置注册完成后才首次访问，此时相关
 * Value 必然已存在。</p>
 *
 * <p>另一个注入点是 {@link UISettingsLayout#getRow(BaseValue)}：自定义行绕开了
 * {@code UIValueMap.create()} 里挂的右键「重置数值」，这里把本模组的行包一层
 * {@link UIProjectSettingsRow} 补齐，其它设置模块不受影响。</p>
 */
@Mixin(value = UISettingsLayout.class, remap = false)
public abstract class UISettingsLayoutMixin
{
    @Shadow
    private static Map<BaseValue, UISettingsLayout.IValueRow> rows;

    @Inject(method = "build", at = @At("TAIL"), remap = false)
    private static void bbspp$registerCustomRows(CallbackInfo ci)
    {
        if (ModSettings.blockbenchPath != null)
        {
            rows.put(ModSettings.blockbenchPath, new UIBlockbenchPathRow(ModSettings.blockbenchPath));
        }

        /* 原版数值框普通拖动是 0.25/像素，调 0.062 这种小数太粗，这里换成贴合量级的步长 */
        if (ModSettings.transformTranslateStep != null)
        {
            rows.put(ModSettings.transformTranslateStep,
                UIStepValueRow.of(ModSettings.transformTranslateStep, 0.005D, 0.001D, 0.05D, 0.005D));
        }

        if (ModSettings.transformScaleStep != null)
        {
            rows.put(ModSettings.transformScaleStep,
                UIStepValueRow.of(ModSettings.transformScaleStep, 0.01D, 0.001D, 0.1D, 0.01D));
        }

        if (ModSettings.transformRotateStep != null)
        {
            rows.put(ModSettings.transformRotateStep,
                UIStepValueRow.of(ModSettings.transformRotateStep, 1D, 0.1D, 5D, 1D));
        }

        if (ModSettings.transformPivotStep != null)
        {
            rows.put(ModSettings.transformPivotStep,
                UIStepValueRow.of(ModSettings.transformPivotStep, 0.005D, 0.001D, 0.05D, 0.005D));
        }
    }

    /** 让本模组设置模块里的自定义行也能右键「重置数值」；标准行由 BBS 自己挂，不经这里。 */
    @Inject(method = "getRow", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp$wrapProjectSettingsRow(BaseValue value, CallbackInfoReturnable<UISettingsLayout.IValueRow> cir)
    {
        UISettingsLayout.IValueRow wrapped = UIProjectSettingsRow.wrap(cir.getReturnValue());

        if (wrapped != null)
        {
            cir.setReturnValue(wrapped);
        }
    }
}
