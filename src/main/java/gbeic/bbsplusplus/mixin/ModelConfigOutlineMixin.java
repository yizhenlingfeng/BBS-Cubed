package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.outline.OutlineConfigHolder;
import gbeic.bbsplusplus.client.outline.OutlineLimbList;
import gbeic.bbsplusplus.client.outline.OutlineLimbValue;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds the outline value tree to a model's {@code ModelConfig}.
 *
 * <p>The values are ordinary children of the config's {@code ValueGroup}, so they are written
 * to the model's {@code config.json}, loaded back by {@code ModelConfig.fromData} and take part
 * in the editor's undo history. The serialised keys deliberately match Cool_pack's outline
 * keys, which lets an existing Cool_pack model config keep its outline settings.</p>
 */
@Mixin(value = ModelConfig.class, remap = false)
public abstract class ModelConfigOutlineMixin implements OutlineConfigHolder
{
    @Unique
    private ValueBoolean bbspp$outlineEnabled;

    @Unique
    private ValueColor bbspp$outlineColor;

    @Unique
    private ValueFloat bbspp$outlineWidth;

    @Unique
    private ValueFloat bbspp$outlineGlow;

    @Unique
    private ValueFloat bbspp$outlineDecalSize;

    @Unique
    private OutlineLimbList bbspp$outlineLimbs;

    @Unique
    private boolean bbspp$outlineValuesAdded;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp$addOutlineValues(CallbackInfo ci)
    {
        this.bbspp$ensureOutlineValues();
    }

    @Unique
    private void bbspp$ensureOutlineValues()
    {
        if (this.bbspp$outlineEnabled == null)
        {
            this.bbspp$outlineEnabled = new ValueBoolean("outline_enabled", false);
            this.bbspp$outlineColor = new ValueColor("outline_color", Color.white());
            this.bbspp$outlineWidth = new ValueFloat("outline_width", 1F, 0F, 10F);
            this.bbspp$outlineGlow = new ValueFloat("outline_glow", 51F, 0F, 255F);
            this.bbspp$outlineDecalSize = new ValueFloat("outline_decal_size", 4F, 0F, 16F);
            this.bbspp$outlineLimbs = new OutlineLimbList("outline_limbs");
        }

        if (!this.bbspp$outlineValuesAdded)
        {
            ModelConfig self = (ModelConfig) (Object) this;

            self.add(this.bbspp$outlineEnabled);
            self.add(this.bbspp$outlineColor);
            self.add(this.bbspp$outlineWidth);
            self.add(this.bbspp$outlineGlow);
            self.add(this.bbspp$outlineDecalSize);
            self.add(this.bbspp$outlineLimbs);

            this.bbspp$outlineValuesAdded = true;
        }
    }

    @Override
    public boolean bbspp$isOutlineEnabled()
    {
        this.bbspp$ensureOutlineValues();

        return this.bbspp$outlineEnabled.get();
    }

    @Override
    public void bbspp$setOutlineEnabled(boolean enabled)
    {
        this.bbspp$ensureOutlineValues();
        this.bbspp$outlineEnabled.set(enabled);
    }

    @Override
    public int bbspp$getOutlineColor()
    {
        this.bbspp$ensureOutlineValues();

        return this.bbspp$outlineColor.get().getARGBColor();
    }

    @Override
    public void bbspp$setOutlineColor(int argb)
    {
        this.bbspp$ensureOutlineValues();
        this.bbspp$outlineColor.set(new Color().set(argb));
    }

    @Override
    public float bbspp$getOutlineWidth()
    {
        this.bbspp$ensureOutlineValues();

        return this.bbspp$outlineWidth.get();
    }

    @Override
    public void bbspp$setOutlineWidth(float width)
    {
        this.bbspp$ensureOutlineValues();
        this.bbspp$outlineWidth.set(width);
    }

    @Override
    public float bbspp$getOutlineGlow()
    {
        this.bbspp$ensureOutlineValues();

        return this.bbspp$outlineGlow.get();
    }

    @Override
    public void bbspp$setOutlineGlow(float glow)
    {
        this.bbspp$ensureOutlineValues();
        this.bbspp$outlineGlow.set(glow);
    }

    @Override
    public float bbspp$getOutlineDecalSize()
    {
        this.bbspp$ensureOutlineValues();

        return this.bbspp$outlineDecalSize.get();
    }

    @Override
    public void bbspp$setOutlineDecalSize(float size)
    {
        this.bbspp$ensureOutlineValues();
        this.bbspp$outlineDecalSize.set(size);
    }

    @Override
    public List<OutlineLimbValue> bbspp$getOutlineLimbs()
    {
        this.bbspp$ensureOutlineValues();

        return this.bbspp$outlineLimbs.getAllTyped();
    }

    @Override
    public void bbspp$addOutlineLimb()
    {
        this.bbspp$ensureOutlineValues();

        BaseValue.edit(this.bbspp$outlineLimbs, limbs ->
        {
            OutlineLimbValue limb = new OutlineLimbValue(String.valueOf(limbs.getList().size()));

            /* A new limb starts at the global glow so it looks like the rest of the model. */
            limb.glow.set(this.bbspp$outlineGlow.get());

            limbs.add(limb);
            limbs.sync();
        });
    }

    @Override
    public void bbspp$removeOutlineLimb(int index)
    {
        this.bbspp$ensureOutlineValues();

        BaseValue.edit(this.bbspp$outlineLimbs, limbs ->
        {
            List<OutlineLimbValue> values = limbs.getAllTyped();

            if (index >= 0 && index < values.size())
            {
                values.remove(index);
                limbs.sync();
            }
        });
    }
}
