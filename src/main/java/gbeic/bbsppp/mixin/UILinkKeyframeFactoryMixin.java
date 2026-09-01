package gbeic.bbsppp.mixin;

import gbeic.bbsppp.client.texture.ModelBindingGeometry;
import gbeic.bbsppp.client.ui.keyframes.UITextureTweenOrigin3DPicker;
import gbeic.bbsppp.keyframes.ITextureTweenKeyframe;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UILinkKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Link 关键帧编辑面板的纹理补间控制。
 *
 * <p>注入目标是 {@link UILinkKeyframeFactory} 构造函数尾部。只有模型形态的原生纹理轨道会显示这些控件；
 * 补间参数直接挂在当前纹理关键帧上，用于控制它到下一个纹理关键帧之间的过渡。</p>
 */
@Mixin(value = UILinkKeyframeFactory.class, remap = false)
public abstract class UILinkKeyframeFactoryMixin extends UIKeyframeFactory<Link>
{
    public UILinkKeyframeFactoryMixin(Keyframe<Link> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
    }

    /**
     * 注入目标：Link 关键帧 UI 面板构造完成后。
     * 注入原因：原版面板只有选择贴图按钮，没有段落级补间控制。
     * 修改行为：对模型原生纹理轨道追加模式、像素块与扩散起点控件，并写入所有被选中的纹理关键帧。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbsppp$addTextureTweenToggle(Keyframe<Link> keyframe, UIKeyframes editor, CallbackInfo ci)
    {
        ModelForm modelForm = findModelForm(editor, keyframe);

        if (!isTextureTweenTrack(keyframe) || modelForm == null)
        {
            return;
        }

        ITextureTweenKeyframe tween = (ITextureTweenKeyframe) keyframe;
        int[] modeValue = {tween.bbsppp$getTextureTweenMode()};
        int[] blockSizeValue = {tween.bbsppp$getTextureTweenBlockSize()};
        Runnable[] refreshControls = new Runnable[1];
        UIToggle flash = new UIToggle(L10n.lang("bbsppp.ui.keyframes.texture_tween_flash"), (button) ->
        {
            setFlash(editor, button.getValue());

            if (refreshControls[0] != null)
            {
                refreshControls[0].run();
            }
        });

        flash.setValue(tween.bbsppp$isTextureTweenFlashEnabled());
        flash.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_flash.tooltip"));

        UILabel flashColorLabel = UI.label(L10n.lang("bbsppp.ui.keyframes.texture_tween_flash_color"));
        UIColor flashColor = new UIColor((color) -> setFlashColor(editor, color)).withAlpha();
        flashColor.setColor(tween.bbsppp$getTextureTweenFlashColor());
        flashColor.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_flash_color.tooltip"));

        UIToggle reverse = new UIToggle(L10n.lang("bbsppp.ui.keyframes.texture_tween_reverse"), (button) ->
        {
            setReverse(editor, button.getValue());
        });

        reverse.setValue(tween.bbsppp$isTextureTweenReverseEnabled());
        reverse.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_reverse.tooltip"));

        UIToggle pbrGlow = new UIToggle(L10n.lang("bbsppp.ui.keyframes.texture_tween_pbr_glow"), (button) ->
        {
            setPbrGlow(editor, button.getValue());

            if (refreshControls[0] != null)
            {
                refreshControls[0].run();
            }
        });

        pbrGlow.setValue(tween.bbsppp$isTextureTweenPbrGlowEnabled());
        pbrGlow.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_pbr_glow.tooltip"));

        UILabel pbrGlowStrengthLabel = UI.label(L10n.lang("bbsppp.ui.keyframes.texture_tween_pbr_glow_strength"));
        UITrackpad pbrGlowStrength = new UITrackpad((value) -> setPbrGlowStrength(editor, value.intValue()))
            .integer().limit(0, 100).values(1D).delayedInput();

        pbrGlowStrength.setValue(tween.bbsppp$getTextureTweenPbrGlowStrength());
        pbrGlowStrength.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_pbr_glow_strength.tooltip"));

        UIButton blockSize = new UIButton(getBlockSizeLabel(blockSizeValue[0]), (button) ->
        {
            int next = nextBlockSize(blockSizeValue[0]);

            blockSizeValue[0] = next;
            setBlockSize(editor, next);
            button.label = getBlockSizeLabel(next);
        });

        blockSize.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_block_size.tooltip"));

        UILabel dissipateIntensityLabel = UI.label(L10n.lang("bbsppp.ui.keyframes.texture_tween_dissipate_intensity"));
        UITrackpad dissipateIntensity = new UITrackpad((value) -> setDissipateIntensity(editor, value.intValue()))
            .integer().limit(0, 100).values(1D).delayedInput();

        dissipateIntensity.setValue(tween.bbsppp$getTextureTweenDissipateIntensity());
        dissipateIntensity.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_dissipate_intensity.tooltip"));

        UIButton mode = new UIButton(getModeLabel(modeValue[0]), (button) ->
        {
            int next = nextMode(modeValue[0]);

            modeValue[0] = next;
            button.label = getModeLabel(next);

            if (refreshControls[0] != null)
            {
                refreshControls[0].run();
            }

            setMode(editor, next);
        });

        mode.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_mode.tooltip"));
        refreshControls[0] = () -> updateModeControls(this.scroll, mode, reverse, flash, flashColorLabel, flashColor,
            pbrGlow, pbrGlowStrengthLabel, pbrGlowStrength, blockSize,
            dissipateIntensityLabel, dissipateIntensity, modeValue[0]);

        this.scroll.add(mode);
        refreshControls[0].run();

        this.scroll.add(UI.label(L10n.lang("bbsppp.ui.keyframes.texture_tween_origin")));

        if (ModelBindingGeometry.create(modelForm) == null)
        {
            UILabel unsupported = UI.label(L10n.lang("bbsppp.ui.keyframes.texture_tween_origin.unsupported"));

            unsupported.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_origin.unsupported.tooltip"));
            this.scroll.add(unsupported);
        }
        else
        {
            UITextureTweenOrigin3DPicker origin = new UITextureTweenOrigin3DPicker(
                modelForm,
                (point) -> setOrigin(editor, point.x, point.y, point.z),
                editor::cacheKeyframes,
                editor::submitKeyframes
            ).setValue(tween.bbsppp$getTextureTweenOriginX(), tween.bbsppp$getTextureTweenOriginY(),
                tween.bbsppp$getTextureTweenOriginZ());

            origin.tooltip(L10n.lang("bbsppp.ui.keyframes.texture_tween_origin.tooltip"));
            this.scroll.add(origin);
        }
    }

    private static boolean isTextureTweenTrack(Keyframe<?> keyframe)
    {
        if (keyframe.getParent() == null)
        {
            return false;
        }

        String id = keyframe.getParent().getId();

        return "texture".equals(id) || id.endsWith("/texture");
    }

    private static void setMode(UIKeyframes editor, int mode)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenMode(mode);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void setOrigin(UIKeyframes editor, float x, float y, float z)
    {
        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenOrigin(x, y, z);
                }
            }
        }

    }

    private static ModelForm findModelForm(UIKeyframes editor, Keyframe<?> keyframe)
    {
        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (sheet.channel != keyframe.getParent())
            {
                continue;
            }

            return findModelForm(sheet);
        }

        return null;
    }

    private static boolean isTextureTweenTrack(String id)
    {
        return "texture".equals(id) || id.endsWith("/texture");
    }

    private static boolean isModelTextureSheet(UIKeyframeSheet sheet)
    {
        return sheet != null && isTextureTweenTrack(sheet.channel.getId()) && findModelForm(sheet) != null;
    }

    private static ModelForm findModelForm(UIKeyframeSheet sheet)
    {
        if (sheet == null)
        {
            return null;
        }

        Form form = sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);

        return form instanceof ModelForm modelForm ? modelForm : null;
    }

    private static void setFlash(UIKeyframes editor, boolean enabled)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenFlashEnabled(enabled);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void setFlashColor(UIKeyframes editor, int color)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenFlashColor(color);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void setReverse(UIKeyframes editor, boolean enabled)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenReverseEnabled(enabled);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void setBlockSize(UIKeyframes editor, int size)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenBlockSize(size);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void setPbrGlow(UIKeyframes editor, boolean enabled)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenPbrGlowEnabled(enabled);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void setPbrGlowStrength(UIKeyframes editor, int strength)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenPbrGlowStrength(strength);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void setDissipateIntensity(UIKeyframes editor, int intensity)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!isModelTextureSheet(sheet))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof ITextureTweenKeyframe tween)
                {
                    tween.bbsppp$setTextureTweenDissipateIntensity(intensity);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static void updateModeControls(UIScrollView scroll, UIButton mode, UIToggle reverse, UIToggle flash,
                                           UILabel flashColorLabel, UIColor flashColor, UIToggle pbrGlow,
                                           UILabel pbrGlowStrengthLabel, UITrackpad pbrGlowStrength, UIButton blockSize,
                                           UILabel dissipateIntensityLabel, UITrackpad dissipateIntensity,
                                           int modeValue)
    {
        boolean changed = false;

        if (isDissipateMode(modeValue))
        {
            changed |= addAfterIfMissing(scroll, mode, reverse);
            changed |= addAfterIfMissing(scroll, reverse, flash);

            if (flash.getValue())
            {
                changed |= addAfterIfMissing(scroll, flash, flashColorLabel);
                changed |= addAfterIfMissing(scroll, flashColorLabel, flashColor);
                changed |= addAfterIfMissing(scroll, flashColor, pbrGlow);

                if (pbrGlow.getValue())
                {
                    changed |= addAfterIfMissing(scroll, pbrGlow, pbrGlowStrengthLabel);
                    changed |= addAfterIfMissing(scroll, pbrGlowStrengthLabel, pbrGlowStrength);
                }
                else
                {
                    changed |= removeIfPresent(pbrGlowStrength);
                    changed |= removeIfPresent(pbrGlowStrengthLabel);
                }
            }
            else
            {
                changed |= removeIfPresent(pbrGlowStrength);
                changed |= removeIfPresent(pbrGlowStrengthLabel);
                changed |= removeIfPresent(pbrGlow);
                changed |= removeIfPresent(flashColor);
                changed |= removeIfPresent(flashColorLabel);
            }

            UIElement blockAnchor = pbrGlowStrength.hasParent() ? pbrGlowStrength
                : pbrGlow.hasParent() ? pbrGlow : flashColor.hasParent() ? flashColor : flash;

            changed |= addAfterIfMissing(scroll, blockAnchor, blockSize);
            changed |= addAfterIfMissing(scroll, blockSize, dissipateIntensityLabel);
            changed |= addAfterIfMissing(scroll, dissipateIntensityLabel, dissipateIntensity);
        }
        else if (modeValue == ITextureTweenKeyframe.TEXTURE_TWEEN_PIXEL_DISSOLVE)
        {
            changed |= removeIfPresent(pbrGlowStrength);
            changed |= removeIfPresent(pbrGlowStrengthLabel);
            changed |= removeIfPresent(pbrGlow);
            changed |= removeIfPresent(flashColor);
            changed |= removeIfPresent(flashColorLabel);
            changed |= removeIfPresent(flash);
            changed |= removeIfPresent(reverse);
            changed |= removeIfPresent(dissipateIntensity);
            changed |= removeIfPresent(dissipateIntensityLabel);
            changed |= addAfterIfMissing(scroll, mode, blockSize);
        }
        else
        {
            changed |= removeIfPresent(pbrGlowStrength);
            changed |= removeIfPresent(pbrGlowStrengthLabel);
            changed |= removeIfPresent(pbrGlow);
            changed |= removeIfPresent(flashColor);
            changed |= removeIfPresent(flashColorLabel);
            changed |= removeIfPresent(flash);
            changed |= removeIfPresent(reverse);
            changed |= removeIfPresent(blockSize);
            changed |= removeIfPresent(dissipateIntensity);
            changed |= removeIfPresent(dissipateIntensityLabel);
        }

        if (changed)
        {
            scroll.resize();
        }
    }

    private static boolean addAfterIfMissing(UIScrollView scroll, UIElement anchor, UIElement element)
    {
        if (element.hasParent())
        {
            return false;
        }

        scroll.addAfter(anchor, element);

        return true;
    }

    private static boolean removeIfPresent(UIElement element)
    {
        if (!element.hasParent())
        {
            return false;
        }

        element.removeFromParent();

        return true;
    }

    private static boolean isDissipateMode(int mode)
    {
        return mode == ITextureTweenKeyframe.TEXTURE_TWEEN_DISSIPATE;
    }

    private static int nextMode(int mode)
    {
        if (mode < ITextureTweenKeyframe.TEXTURE_TWEEN_OFF || mode > ITextureTweenKeyframe.TEXTURE_TWEEN_DISSIPATE)
        {
            return ITextureTweenKeyframe.TEXTURE_TWEEN_OFF;
        }

        return mode == ITextureTweenKeyframe.TEXTURE_TWEEN_DISSIPATE
            ? ITextureTweenKeyframe.TEXTURE_TWEEN_OFF
            : mode + 1;
    }

    private static int nextBlockSize(int size)
    {
        return switch (size)
        {
            case 1 -> 3;
            case 3 -> 5;
            case 5 -> 7;
            case 7 -> 9;
            default -> 1;
        };
    }

    private static IKey getBlockSizeLabel(int size)
    {
        return L10n.lang("bbsppp.ui.keyframes.texture_tween_block_size").format(size, size);
    }

    private static IKey getModeLabel(int mode)
    {
        String key = switch (mode)
        {
            case ITextureTweenKeyframe.TEXTURE_TWEEN_OFF -> "bbsppp.ui.keyframes.texture_tween_mode.off";
            case ITextureTweenKeyframe.TEXTURE_TWEEN_DISSIPATE -> "bbsppp.ui.keyframes.texture_tween_mode.dissipate";
            case ITextureTweenKeyframe.TEXTURE_TWEEN_PIXEL_DISSOLVE -> "bbsppp.ui.keyframes.texture_tween_mode.pixel_dissolve";
            case ITextureTweenKeyframe.TEXTURE_TWEEN_COLOR -> "bbsppp.ui.keyframes.texture_tween_mode.color";
            default -> "bbsppp.ui.keyframes.texture_tween_mode.off";
        };

        return L10n.lang(key);
    }
}
