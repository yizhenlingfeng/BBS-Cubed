package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.outline.OutlineConfigHolder;
import gbeic.bbsplusplus.client.outline.OutlineEmissiveTexture;
import gbeic.bbsplusplus.client.outline.OutlineLimbValue;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.model_editor.UIModelConfigEditor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Appends the outline section to the model editor's General tab.
 *
 * <p>{@code fillGeneral} rebuilds the whole body on every model switch, so this mixin reconstructs
 * the section (and its limb rows) from the currently bound {@link ModelConfig} each time. The
 * section is appended after BBS's own rows and then the page is resized again, because
 * {@code fillGeneral} has already laid the page out before the injection returns.</p>
 */
@Mixin(value = UIModelConfigEditor.class, remap = true)
public abstract class UIModelConfigEditorOutlineMixin
{
    @Shadow
    private ModelConfig data;

    @Shadow
    private UIElement generalBody;

    @Shadow
    private UIScrollView[] pages;

    @Inject(method = "fillGeneral", at = @At("TAIL"))
    private void bbspp$addOutlineSection(CallbackInfo ci)
    {
        if (!(this.data instanceof OutlineConfigHolder outline))
        {
            return;
        }

        UISection section = this.bbspp$buildSection(outline);

        if (section == null)
        {
            return;
        }

        UIElement divider = new UIElement();

        divider.h(6);

        this.generalBody.add(divider, section);
        this.generalBody.resize();
        this.bbspp$resizeGeneralPage();
    }

    @Unique
    private UISection bbspp$buildSection(OutlineConfigHolder outline)
    {
        UISection section = new UISection(L10n.lang("bbs.ui.model_editor.outline"));

        UIToggle all = new UIToggle(L10n.lang("bbs.ui.model_editor.outline.all"), outline.bbspp$isOutlineEnabled(), toggle ->
        {
            outline.bbspp$setOutlineEnabled(toggle.getValue());
        });

        UIColor color = new UIColor(argb ->
        {
            outline.bbspp$setOutlineColor(argb);
            OutlineEmissiveTexture.invalidate();
        });

        color.withAlpha();
        color.direction(Direction.LEFT);
        color.setColor(outline.bbspp$getOutlineColor());

        UITrackpad width = new UITrackpad(value ->
        {
            outline.bbspp$setOutlineWidth(value.floatValue());
        });

        width.limit(0D, 10D).increment(1D).delayedInput();
        width.setValue(outline.bbspp$getOutlineWidth());
        width.tooltip(L10n.lang("bbs.ui.model_editor.outline.width.tooltip"), Direction.LEFT);

        UITrackpad glow = new UITrackpad(value ->
        {
            outline.bbspp$setOutlineGlow(value.floatValue());
            OutlineEmissiveTexture.invalidate();
        });

        glow.limit(0D, 255D).increment(1D).delayedInput();
        glow.setValue(outline.bbspp$getOutlineGlow());
        glow.tooltip(L10n.lang("bbs.ui.model_editor.outline.glow.tooltip"), Direction.LEFT);

        UITrackpad decalSize = new UITrackpad(value ->
        {
            outline.bbspp$setOutlineDecalSize(value.floatValue());
        });

        decalSize.limit(0D, 16D).increment(1D).delayedInput();
        decalSize.setValue(outline.bbspp$getOutlineDecalSize());
        decalSize.tooltip(L10n.lang("bbs.ui.model_editor.outline.decal_size.tooltip"), Direction.LEFT);

        UIElement limbContainer = new UIElement();

        limbContainer.column().stretch().vertical();

        section.fields.add(UI.row(all.h(20)));
        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.color")),
            color.h(20)
        ));
        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.width")),
            width.h(20)
        ));
        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.glow")),
            glow.h(20)
        ));
        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.decal_size")),
            decalSize.h(20)
        ));
        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.limb")),
            this.bbspp$addLimbIcon(outline, limbContainer)
        ));
        section.fields.add(limbContainer);

        this.bbspp$rebuildLimbs(limbContainer, outline);

        return section;
    }

    @Unique
    private UIIcon bbspp$addLimbIcon(OutlineConfigHolder outline, UIElement container)
    {
        UIIcon add = new UIIcon(Icons.ADD, button ->
        {
            outline.bbspp$addOutlineLimb();
            this.bbspp$rebuildLimbs(container, outline);
        });

        add.tooltip(L10n.lang("bbs.ui.model_editor.outline.add"), Direction.LEFT);
        add.wh(16, 16);

        return add;
    }

    @Unique
    private void bbspp$rebuildLimbs(UIElement container, OutlineConfigHolder outline)
    {
        container.removeAll();

        List<OutlineLimbValue> limbs = outline.bbspp$getOutlineLimbs();

        for (int i = 0; i < limbs.size(); i++)
        {
            int index = i;
            OutlineLimbValue limb = limbs.get(i);

            UIBonePicker picker = this.bbspp$invokeBonePicker(
                () -> limb.bone.get(),
                bone -> limb.bone.set(bone != null ? bone : ""),
                this::bbspp$resizeGeneralPage
            );

            UIColor color = new UIColor(argb -> limb.color.set(new Color().set(argb)));

            color.withAlpha();
            color.direction(Direction.LEFT);
            color.setColor(limb.color.get().getARGBColor());

            UITrackpad width = new UITrackpad(value -> limb.width.set(value.floatValue()));

            width.limit(0D, 10D).increment(1D).delayedInput();
            width.setValue(limb.width.get());

            UITrackpad glow = new UITrackpad(value -> limb.glow.set(value.floatValue()));

            glow.limit(0D, 255D).increment(1D).delayedInput();
            glow.setValue(limb.glow.get());
            glow.tooltip(L10n.lang("bbs.ui.model_editor.outline.glow.tooltip"), Direction.LEFT);

            UIIcon remove = new UIIcon(Icons.REMOVE, button ->
            {
                outline.bbspp$removeOutlineLimb(index);
                this.bbspp$rebuildLimbs(container, outline);
            });

            remove.tooltip(L10n.lang("bbs.ui.model_editor.outline.remove"), Direction.LEFT);
            remove.wh(16, 16);

            container.add(UI.row(
                UI.label(L10n.lang("bbs.ui.model_editor.outline.limb_bone")),
                picker.h(20),
                color.h(20),
                width.h(20),
                remove
            ));
            container.add(UI.row(
                UI.label(L10n.lang("bbs.ui.model_editor.outline.glow")),
                glow.h(20)
            ));

            if (i < limbs.size() - 1)
            {
                UIElement gap = new UIElement();

                gap.h(4);
                container.add(gap);
            }
        }

        container.resize();
        this.bbspp$resizeGeneralPage();
    }

    @Unique
    private void bbspp$resizeGeneralPage()
    {
        if (this.pages == null)
        {
            return;
        }

        int index = UIModelConfigEditor.Tab.GENERAL.ordinal();

        if (index >= 0 && index < this.pages.length && this.pages[index] != null)
        {
            this.pages[index].resize();
            this.pages[index].scroll.clamp();
        }
    }

    @Invoker("bonePicker")
    protected abstract UIBonePicker bbspp$invokeBonePicker(Supplier<String> getter, Consumer<String> setter, Runnable refresh);
}
