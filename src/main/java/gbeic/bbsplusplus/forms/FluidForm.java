package gbeic.bbsplusplus.forms;

import gbeic.bbsplusplus.api.TextureGradeProvider;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.LinkUtils;

public class FluidForm extends Form implements TextureGradeProvider
{
    public enum FluidMode
    {
        FULL_OCEAN, WATER_DROP, PROCEDURAL
    }

    /* bbs-fs 2.3.1 无 CML 的 ValueEnum，以 ValueInt 存 FluidMode 序号 */
    public final ValueInt mode = new ValueInt("mode", FluidMode.WATER_DROP.ordinal(), 0, FluidMode.values().length - 1);

    /* Common */
    public final ValueFloat flowSpeed = new ValueFloat("flowSpeed", 1F);
    public final ValueFloat turbulence = new ValueFloat("turbulence", 0.1F);
    public final ValueColor color = new ValueColor("color", new Color(0.0f, 0.5f, 1.0f, 0.8f));
    public final ValueFloat opacity = new ValueFloat("opacity", 0.8F);
    public final ValueLink texture = new ValueLink("texture", LinkUtils.create("minecraft:textures/block/water_still.png"));
    public final ValueColor textureTint = new ValueColor("texture_tint", new Color(1F, 1F, 1F, 0F));
    public final ValueFloat textureWhiten = new ValueFloat("texture_whiten", 0F, 0F, 1F);
    public final ValueFloat physicsSensitivity = new ValueFloat("physicsSensitivity", 1F);
    public final ValueInt subdivisions = new ValueInt("subdivisions", 1, 1, 8);
    public final ValueBoolean smoothShading = new ValueBoolean("smoothShading", true);
    public final ValueBoolean debug = new ValueBoolean("debug", false);

    /* Full Ocean */
    public final ValueFloat sizeX = new ValueFloat("sizeX", 10F);
    public final ValueFloat sizeY = new ValueFloat("sizeY", 1F);
    public final ValueFloat sizeZ = new ValueFloat("sizeZ", 10F);
    public final ValueBoolean fillBlock = new ValueBoolean("fillBlock", false);
    public final ValueFloat waveAmplitude = new ValueFloat("waveAmplitude", 0.5F);
    public final ValueFloat waveFrequency = new ValueFloat("waveFrequency", 1F);

    /* Water Drop */
    public final ValueFloat surfaceTension = new ValueFloat("surfaceTension", 0.5F);
    public final ValueFloat dropSize = new ValueFloat("dropSize", 1F);
    public final ValueFloat viscosity = new ValueFloat("viscosity", 0.5F);

    public FluidForm()
    {
        super();

        this.add(this.mode);
        this.add(this.flowSpeed);
        this.add(this.turbulence);
        this.add(this.color);
        this.add(this.opacity);
        this.add(this.texture);
        this.add(this.textureTint);
        this.add(this.textureWhiten);
        this.add(this.physicsSensitivity);
        this.add(this.subdivisions);
        this.add(this.smoothShading);
        this.add(this.debug);

        this.add(this.sizeX);
        this.add(this.sizeY);
        this.add(this.sizeZ);
        this.add(this.fillBlock);
        this.add(this.waveAmplitude);
        this.add(this.waveFrequency);

        this.add(this.surfaceTension);
        this.add(this.dropSize);
        this.add(this.viscosity);
    }

    public FluidMode getFluidMode()
    {
        FluidMode[] values = FluidMode.values();

        return values[MathUtils.clamp(this.mode.get(), 0, values.length - 1)];
    }

    public void setFluidMode(FluidMode mode)
    {
        this.mode.set(mode.ordinal());
    }

    @Override
    public ValueColor bbspp_cml$getTextureTint()
    {
        return this.textureTint;
    }

    @Override
    public ValueFloat bbspp_cml$getTextureWhiten()
    {
        return this.textureWhiten;
    }
}
