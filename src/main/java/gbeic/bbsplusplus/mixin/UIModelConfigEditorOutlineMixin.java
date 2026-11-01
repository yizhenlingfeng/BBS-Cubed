package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.outline.OutlineConfigHolder;
import gbeic.bbsplusplus.client.outline.OutlineEmissiveTexture;
import gbeic.bbsplusplus.client.outline.OutlineLimbValue;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.model_editor.UIModelBoneList;
import mchorse.bbs_mod.ui.model_editor.UIModelConfigEditor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the outline controls to the model editor's Bones tab, under BBS's bone visibility row.
 *
 * <p>The selected bone owns its limb override: enabling the limb toggle creates an
 * {@link OutlineLimbValue} for that bone, disabling it removes the entry. The old free-form
 * limb list is intentionally gone, so a model cannot accumulate stale bone names.</p>
 */
@Mixin(value = UIModelConfigEditor.class, remap = true)
public abstract class UIModelConfigEditorOutlineMixin
{
    @Shadow
    private ModelConfig data;

    @Shadow
    private UIElement bonePanel;

    @Shadow
    private UIModelBoneList bones;

    @Shadow
    private UIScrollView[] pages;

    @Unique
    private UISection bbspp$outlineSection;

    @Inject(method = "fillBone", at = @At("TAIL"))
    private void bbspp$addOutlineSection(CallbackInfo ci)
    {
        if (!(this.data instanceof OutlineConfigHolder outline) || this.bonePanel == null)
        {
            return;
        }

        String bone = this.bones == null ? null : this.bones.getCurrentFirst();

        this.bbspp$outlineSection = this.bbspp$buildSection(outline, bone);
        this.bbspp$attachSection(this.bbspp$outlineSection);
        this.bonePanel.resize();
        this.bbspp$resizeBonePage();
    }

    @Unique
    private UISection bbspp$buildSection(OutlineConfigHolder outline, String bone)
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

        if (bone == null || bone.isEmpty())
        {
            section.fields.add(UI.row(UI.label(L10n.lang("bbs.ui.model_editor.outline.select_bone"))));

            return section;
        }

        OutlineLimbValue limb = outline.bbspp$getOutlineLimb(bone);
        UIToggle limbToggle = new UIToggle(L10n.lang("bbs.ui.model_editor.outline.limb"), limb != null, toggle ->
        {
            outline.bbspp$setOutlineLimbEnabled(bone, toggle.getValue());
            this.bbspp$refreshSection(outline, bone);
        });

        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.limb_bone")),
            UI.label(L10n.lang(bone))
        ));
        section.fields.add(UI.row(limbToggle.h(20)));

        if (limb == null)
        {
            return section;
        }

        UIColor limbColor = new UIColor(argb ->
        {
            limb.color.set(new Color().set(argb));
            OutlineEmissiveTexture.invalidate();
        });

        limbColor.withAlpha();
        limbColor.direction(Direction.LEFT);
        limbColor.setColor(limb.color.get().getARGBColor());

        UITrackpad limbWidth = new UITrackpad(value -> limb.width.set(value.floatValue()));

        limbWidth.limit(0D, 10D).increment(1D).delayedInput();
        limbWidth.setValue(limb.width.get());

        UITrackpad limbGlow = new UITrackpad(value ->
        {
            limb.glow.set(value.floatValue());
            OutlineEmissiveTexture.invalidate();
        });

        limbGlow.limit(0D, 255D).increment(1D).delayedInput();
        limbGlow.setValue(limb.glow.get());
        limbGlow.tooltip(L10n.lang("bbs.ui.model_editor.outline.glow.tooltip"), Direction.LEFT);

        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.color")),
            limbColor.h(20)
        ));
        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.width")),
            limbWidth.h(20)
        ));
        section.fields.add(UI.row(
            UI.label(L10n.lang("bbs.ui.model_editor.outline.glow")),
            limbGlow.h(20)
        ));

        return section;
    }

    @Unique
    private void bbspp$refreshSection(OutlineConfigHolder outline, String bone)
    {
        if (this.bbspp$outlineSection != null)
        {
            this.bonePanel.remove(this.bbspp$outlineSection);
        }

        this.bbspp$outlineSection = this.bbspp$buildSection(outline, bone);
        this.bbspp$attachSection(this.bbspp$outlineSection);
        this.bonePanel.resize();
        this.bbspp$resizeBonePage();
    }

    @Unique
    private void bbspp$attachSection(UISection section)
    {
        List<UIToggle> toggles = this.bonePanel.getChildren(UIToggle.class);

        if (toggles.isEmpty())
        {
            this.bonePanel.add(section);
        }
        else
        {
            this.bonePanel.addAfter(toggles.get(0), section);
        }
    }

    @Unique
    private void bbspp$resizeBonePage()
    {
        if (this.pages == null)
        {
            return;
        }

        int index = UIModelConfigEditor.Tab.BONES.ordinal();

        if (index >= 0 && index < this.pages.length && this.pages[index] != null)
        {
            this.pages[index].resize();
            this.pages[index].scroll.clamp();
        }
    }
}
