package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.TransformPivotEditor;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITransform;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 {@link UITransform}（XYZ 变换编辑界面基类）追加 CML 的"中心点"行：
 * SPHERE 图标 + P 的 xyz 三个轨道板，样式/布局与原版 T/S/R/R2 行一致，
 * 追加在它们之后。所有使用该界面的地方（pose 编辑、pose/transform 关键帧、
 * form 通用变换、身体部位、模型方块等）自动获得该行。
 *
 * <p>写回经 {@link TransformPivotEditor#bbspp_cml$setP}（本类给默认空实现，
 * {@code UIPropTransformMixin} 在子类覆盖为写 transform.pivot + 回调通知）。
 * 受 BBS_snow 设置的 {@code CMLSettings.pivotTransform} 开关控制。</p>
 */
@Mixin(value = UITransform.class, remap = false)
public abstract class UITransformMixin extends UIElement implements TransformPivotEditor
{
    @Unique
    private UITrackpad bbspp_cml$px;

    @Unique
    private UITrackpad bbspp_cml$py;

    @Unique
    private UITrackpad bbspp_cml$pz;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createPivotRow(CallbackInfo ci)
    {
        if (CMLSettings.pivotTransform != null && !CMLSettings.pivotTransform.get())
        {
            return;
        }

        this.bbspp_cml$px = new UITrackpad((value) -> this.bbspp_cml$internalSetP()).block().onlyNumbers();
        this.bbspp_cml$py = new UITrackpad((value) -> this.bbspp_cml$internalSetP()).block().onlyNumbers();
        this.bbspp_cml$pz = new UITrackpad((value) -> this.bbspp_cml$internalSetP()).block().onlyNumbers();

        UIIcon iconP = new UIIcon(Icons.SPHERE, null);

        iconP.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        iconP.disabledColor = Colors.WHITE;
        iconP.hoverColor = Colors.WHITE;
        iconP.setEnabled(false);

        this.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, iconP, this.bbspp_cml$px, this.bbspp_cml$py, this.bbspp_cml$pz));
    }

    @Unique
    private void bbspp_cml$internalSetP()
    {
        this.bbspp_cml$setP(this.bbspp_cml$px.getValue(), this.bbspp_cml$py.getValue(), this.bbspp_cml$pz.getValue());
    }

    @Override
    public void bbspp_cml$fillP(double x, double y, double z)
    {
        if (this.bbspp_cml$px == null)
        {
            return;
        }

        this.bbspp_cml$px.setValue(x);
        this.bbspp_cml$py.setValue(y);
        this.bbspp_cml$pz.setValue(z);
    }

    @Override
    public void bbspp_cml$setP(double x, double y, double z)
    {
        /* 基类默认 no-op；UIPropTransform 子类覆盖为写回 transform.pivot */
    }
}
