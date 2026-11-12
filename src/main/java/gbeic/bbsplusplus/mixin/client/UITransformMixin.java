package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.TransformPivotEditor;
import gbeic.bbsplusplus.settings.ModSettings;
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
 * 受 BBS_snow 设置的 {@code ModSettings.pivotTransform} 开关控制。</p>
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

    @Unique
    private double bbspp_cml$stepT = Double.NaN;

    @Unique
    private double bbspp_cml$stepS = Double.NaN;

    @Unique
    private double bbspp_cml$stepR = Double.NaN;

    @Unique
    private double bbspp_cml$stepP = Double.NaN;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createPivotRow(CallbackInfo ci)
    {
        this.bbspp_cml$refreshTransformSteps();

        if (ModSettings.pivotTransform != null && !ModSettings.pivotTransform.get())
        {
            return;
        }

        double pivotStep = ModSettings.getTransformPivotStep();

        this.bbspp_cml$px = new UITrackpad((value) -> this.bbspp_cml$internalSetP()).block().increment(pivotStep).onlyNumbers();
        this.bbspp_cml$py = new UITrackpad((value) -> this.bbspp_cml$internalSetP()).block().increment(pivotStep).onlyNumbers();
        this.bbspp_cml$pz = new UITrackpad((value) -> this.bbspp_cml$internalSetP()).block().increment(pivotStep).onlyNumbers();

        UIIcon iconP = new UIIcon(Icons.SPHERE, null);

        iconP.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        iconP.disabledColor = Colors.WHITE;
        iconP.hoverColor = Colors.WHITE;
        iconP.setEnabled(false);

        this.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, iconP, this.bbspp_cml$px, this.bbspp_cml$py, this.bbspp_cml$pz));
    }

    /**
     * 把 BBS Cubed 设置里的四个步长写进 T/S/R 三行与追加的 P 行的轨道板。
     *
     * <p>只改 {@code increment}——即点按字段两侧箭头、或按住 Ctrl 拖动时
     * 每次改变的数值，正好是 BBS 用 {@code block()}/{@code factor()}/{@code degrees()}
     * 预设出来的那一个（1/16、0.25、15）。普通拖动（{@code normal}/{@code weak}/{@code strong}）
     * 保持原版手感不变。四元数旋转行在 {@code UIPropTransform} 的构造里晚于本注入创建，
     * 因此不会被波及。</p>
     *
     * <p>只在设置值真正变化时写入，因此可以每次同步数值时无脑调用。</p>
     */
    @Unique
    private void bbspp_cml$refreshTransformSteps()
    {
        UITransform self = (UITransform) (Object) this;
        double translate = ModSettings.getTransformTranslateStep();
        double scale = ModSettings.getTransformScaleStep();
        double rotate = ModSettings.getTransformRotateStep();

        if (translate != this.bbspp_cml$stepT)
        {
            this.bbspp_cml$stepT = translate;

            self.tx.increment(translate);
            self.ty.increment(translate);
            self.tz.increment(translate);
        }

        if (scale != this.bbspp_cml$stepS)
        {
            this.bbspp_cml$stepS = scale;

            self.sx.increment(scale);
            self.sy.increment(scale);
            self.sz.increment(scale);
        }

        if (rotate != this.bbspp_cml$stepR)
        {
            this.bbspp_cml$stepR = rotate;

            self.rx.increment(rotate);
            self.ry.increment(rotate);
            self.rz.increment(rotate);
        }

        double pivot = ModSettings.getTransformPivotStep();

        if (pivot != this.bbspp_cml$stepP)
        {
            this.bbspp_cml$stepP = pivot;

            if (this.bbspp_cml$px != null)
            {
                this.bbspp_cml$px.increment(pivot);
                this.bbspp_cml$py.increment(pivot);
                this.bbspp_cml$pz.increment(pivot);
            }
        }
    }

    /*
     * 三个 fill 方法每次从模型同步数值时都会经过，借它们让设置改动无需重开面板就能生效。
     */
    @Inject(method = "fillT", at = @At("HEAD"), remap = false)
    private void bbspp_cml$onFillT(double x, double y, double z, CallbackInfo ci)
    {
        this.bbspp_cml$refreshTransformSteps();
    }

    @Inject(method = "fillS", at = @At("HEAD"), remap = false)
    private void bbspp_cml$onFillS(double x, double y, double z, CallbackInfo ci)
    {
        this.bbspp_cml$refreshTransformSteps();
    }

    @Inject(method = "fillR", at = @At("HEAD"), remap = false)
    private void bbspp_cml$onFillR(double x, double y, double z, CallbackInfo ci)
    {
        this.bbspp_cml$refreshTransformSteps();
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
