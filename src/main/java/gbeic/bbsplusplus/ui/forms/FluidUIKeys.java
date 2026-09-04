package gbeic.bbsplusplus.ui.forms;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * 流体伪装 UI 的翻译键常量 —— 键名与 CML 完全一致（bbs.fluid.*），
 * 翻译由插件的 strings/&lt;lang&gt;.json 提供（bbs-fs 的 UIKeys 没有这些常量）。
 */
public class FluidUIKeys
{
    public static final IKey FLUID_TITLE = L10n.lang("bbs.forms.editors.fluid.title");

    public static final IKey FLUID_FLOW_SPEED = L10n.lang("bbs.fluid.flow_speed");
    public static final IKey FLUID_TURBULENCE = L10n.lang("bbs.fluid.turbulence");
    public static final IKey FLUID_PHYSICS_SENSITIVITY = L10n.lang("bbs.fluid.physics_sensitivity");
    public static final IKey FLUID_OPACITY = L10n.lang("bbs.fluid.opacity");
    public static final IKey FLUID_FILL_BLOCK = L10n.lang("bbs.fluid.fill_block");
    public static final IKey FLUID_WAVE_AMPLITUDE = L10n.lang("bbs.fluid.wave_amplitude");
    public static final IKey FLUID_WAVE_FREQUENCY = L10n.lang("bbs.fluid.wave_frequency");
    public static final IKey FLUID_DROP_SIZE = L10n.lang("bbs.fluid.drop_size");
    public static final IKey FLUID_SURFACE_TENSION = L10n.lang("bbs.fluid.surface_tension");
    public static final IKey FLUID_VISCOSITY = L10n.lang("bbs.fluid.viscosity");
    public static final IKey FLUID_PRESETS = L10n.lang("bbs.fluid.presets");
    public static final IKey FLUID_PRESET_CUSTOM = L10n.lang("bbs.fluid.preset.custom");
    public static final IKey FLUID_PRESET_CALM_OCEAN = L10n.lang("bbs.fluid.preset.calm_ocean");
    public static final IKey FLUID_PRESET_STORMY_OCEAN = L10n.lang("bbs.fluid.preset.stormy_ocean");
    public static final IKey FLUID_PRESET_VISCOUS_DROP = L10n.lang("bbs.fluid.preset.viscous_drop");
    public static final IKey FLUID_MODE_LABEL = L10n.lang("bbs.fluid.mode");
    public static final IKey FLUID_MODE_FULL_OCEAN = L10n.lang("bbs.fluid.mode.full_ocean");
    public static final IKey FLUID_MODE_WATER_DROP = L10n.lang("bbs.fluid.mode.water_drop");
    public static final IKey FLUID_MODE_PROCEDURAL = L10n.lang("bbs.fluid.mode.procedural");
    public static final IKey FLUID_CATEGORY_COMMON = L10n.lang("bbs.fluid.category.common");
    public static final IKey FLUID_CATEGORY_OCEAN = L10n.lang("bbs.fluid.category.ocean");
    public static final IKey FLUID_CATEGORY_DROP = L10n.lang("bbs.fluid.category.drop");
    public static final IKey FLUID_SUBDIVISIONS = L10n.lang("bbs.forms.editors.fluid.subdivisions");
    public static final IKey FLUID_SMOOTH_SHADING = L10n.lang("bbs.forms.editors.fluid.smooth_shading");
    public static final IKey FLUID_DEBUG = L10n.lang("bbs.fluid.debug");
    public static final IKey FLUID_SIZE_X = L10n.lang("bbs.fluid.size_x");
    public static final IKey FLUID_SIZE_Y = L10n.lang("bbs.fluid.size_y");
    public static final IKey FLUID_SIZE_Z = L10n.lang("bbs.fluid.size_z");
}
