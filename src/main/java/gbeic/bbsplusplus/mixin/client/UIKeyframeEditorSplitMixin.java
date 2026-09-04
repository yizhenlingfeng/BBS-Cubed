package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;
import java.util.function.Function;

/** Adds a draggable timeline/property split to embedded keyframe editors. */
@Mixin(value = UIKeyframeEditor.class, remap = false)
public abstract class UIKeyframeEditorSplitMixin
{
    @Shadow
    public UIKeyframes view;

    @Shadow
    public UIKeyframeFactory editor;

    @Shadow
    private UIElement target;

    @Unique
    private UIDraggable bbspp_cml$splitHandle;

    @Unique
    private double bbspp_cml$savedMin;

    @Unique
    private double bbspp_cml$savedMax;

    @Unique
    private boolean bbspp_cml$hasSavedViewport;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createSplitHandle(
        Function<Consumer<Keyframe>, UIKeyframes> factory,
        CallbackInfo ci
    )
    {
        UIKeyframeEditor self = (UIKeyframeEditor) (Object) this;

        this.bbspp_cml$splitHandle = new UIDraggable((context) ->
        {
            if (this.target != null || self.area.w <= 0)
            {
                return;
            }

            float split = (context.mouseX - self.area.x) / (float) self.area.w;
            split = Math.max(0.2F, Math.min(0.8F, split));

            if (CMLSettings.keyframeEditorTimelineRatio != null)
            {
                CMLSettings.keyframeEditorTimelineRatio.set(split);
            }

            this.bbspp_cml$applySplitLayout();
        });
        this.bbspp_cml$splitHandle
            .hoverOnly()
            .cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR)
            .rendering((context) ->
            {
                if (this.target == null && this.bbspp_cml$splitHandle.isVisible())
                {
                    int x = this.bbspp_cml$splitHandle.area.mx();
                    context.batcher.box(x, self.area.y, x + 1, self.area.ey(), Colors.ACTIVE | Colors.A75);
                }
            });

        self.add(this.bbspp_cml$splitHandle);
        this.bbspp_cml$applySplitLayout();
    }

    @Inject(method = "target", at = @At("TAIL"), remap = false)
    private void bbspp_cml$refreshSplitVisibility(
        UIElement target,
        CallbackInfoReturnable<UIKeyframeEditor> cir
    )
    {
        this.bbspp_cml$applySplitLayout();
    }

    @Inject(
        method = "pickKeyframe(Lmchorse/bbs_mod/utils/keyframes/Keyframe;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void bbspp_cml$saveViewportBeforePick(Keyframe keyframe, CallbackInfo ci)
    {
        this.bbspp_cml$hasSavedViewport = false;

        if (this.view == null || this.view.area.w <= 0)
        {
            return;
        }

        double min = this.view.getXAxis().getMinValue();
        double max = this.view.getXAxis().getMaxValue();

        if (Double.isFinite(min) && Double.isFinite(max) && max > min)
        {
            this.bbspp_cml$savedMin = min;
            this.bbspp_cml$savedMax = max;
            this.bbspp_cml$hasSavedViewport = true;
        }
    }

    @Inject(
        method = "pickKeyframe(Lmchorse/bbs_mod/utils/keyframes/Keyframe;)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$refreshSplitAfterPick(Keyframe keyframe, CallbackInfo ci)
    {
        this.bbspp_cml$applySplitLayout();

        if (this.bbspp_cml$hasSavedViewport)
        {
            this.view.getXAxis().view(this.bbspp_cml$savedMin, this.bbspp_cml$savedMax);
            this.bbspp_cml$hasSavedViewport = false;
        }
    }

    @Unique
    private float bbspp_cml$getTimelineRatio()
    {
        float ratio = CMLSettings.keyframeEditorTimelineRatio == null
            ? 0.65F
            : CMLSettings.keyframeEditorTimelineRatio.get();

        return Math.max(0.2F, Math.min(0.8F, ratio));
    }

    @Unique
    private void bbspp_cml$applySplitLayout()
    {
        if (this.bbspp_cml$splitHandle == null)
        {
            return;
        }

        UIKeyframeEditor self = (UIKeyframeEditor) (Object) this;
        boolean embedded = this.target == null;

        this.bbspp_cml$splitHandle.setVisible(embedded);
        this.bbspp_cml$splitHandle.setEnabled(embedded);

        if (!embedded)
        {
            return;
        }

        float split = this.bbspp_cml$getTimelineRatio();

        this.view.resetFlex().relative(self).x(0).y(0).w(split).h(1F);
        this.bbspp_cml$splitHandle
            .resetFlex()
            .relative(self)
            .x(split)
            .y(0)
            .w(6)
            .h(1F)
            .anchorX(0.5F);

        if (this.editor != null)
        {
            this.editor.resetFlex().relative(self).x(split).y(0).w(1F - split).h(1F);
        }

        /* Keep the six-pixel hit target above the property factory at the shared edge. */
        this.bbspp_cml$splitHandle.removeFromParent();
        self.add(this.bbspp_cml$splitHandle);

        if (self.area.w > 0 && self.area.h > 0)
        {
            double min = this.view.getXAxis().getMinValue();
            double max = this.view.getXAxis().getMaxValue();
            boolean restore = Double.isFinite(min) && Double.isFinite(max) && max > min;

            self.resize();

            if (restore)
            {
                this.view.getXAxis().view(min, max);
            }
        }
    }
}
