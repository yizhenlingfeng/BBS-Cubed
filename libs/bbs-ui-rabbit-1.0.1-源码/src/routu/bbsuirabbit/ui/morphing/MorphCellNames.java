package routu.bbsuirabbit.ui.morphing;

import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Name strips of the BBS++ morph palette's cells: one switch per kind of form (category
 * "morph_names"). On, a cell says its name at every zoom — narrow cells get a compact strip;
 * off, the cell shows no name strip at all (the cursor still names it on hover).
 */
public final class MorphCellNames
{
    public static ValueBoolean model;
    public static ValueBoolean particle;
    public static ValueBoolean vanillaParticle;
    public static ValueBoolean mob;
    public static ValueBoolean block;
    public static ValueBoolean item;
    public static ValueBoolean billboard;
    public static ValueBoolean extruded;
    public static ValueBoolean label;
    public static ValueBoolean structure;
    /** Anchor, trail, video, framebuffer and forms added by other mods. */
    public static ValueBoolean other;

    private MorphCellNames()
    {}

    public static void register(SettingsBuilder builder)
    {
        builder.category("morph_names", Icons.FONT);
        model = builder.getBoolean("morph_name_model", true);
        particle = builder.getBoolean("morph_name_particle", true);
        vanillaParticle = builder.getBoolean("morph_name_vanilla_particle", true);
        mob = builder.getBoolean("morph_name_mob", true);
        block = builder.getBoolean("morph_name_block", true);
        item = builder.getBoolean("morph_name_item", true);
        billboard = builder.getBoolean("morph_name_billboard", true);
        extruded = builder.getBoolean("morph_name_extruded", true);
        label = builder.getBoolean("morph_name_label", true);
        structure = builder.getBoolean("morph_name_structure", true);
        other = builder.getBoolean("morph_name_other", true);
    }

    /** Whether cells of this form's kind carry a name strip. */
    public static boolean shows(Form form)
    {
        ValueBoolean toggle = toggle(form);

        return toggle == null || toggle.get();
    }

    private static ValueBoolean toggle(Form form)
    {
        if (form instanceof ModelForm) return model;
        if (form instanceof ParticleForm) return particle;
        if (form instanceof VanillaParticleForm) return vanillaParticle;
        if (form instanceof MobForm) return mob;
        if (form instanceof BlockForm) return block;
        if (form instanceof ItemForm) return item;
        if (form instanceof ExtrudedForm) return extruded;
        if (form instanceof BillboardForm) return billboard;
        if (form instanceof LabelForm) return label;
        if (form instanceof StructureForm) return structure;

        return other;
    }

    /**
     * The name an unnamed particle cell says instead of its raw id: a BBS particle's effect
     * file name, a vanilla particle's particle type. {@code null} = use the form's own name.
     */
    public static String particleName(Form form)
    {
        if (!form.name.get().trim().isEmpty())
        {
            return null;
        }

        if (form instanceof ParticleForm particle)
        {
            return lastSegment(particle.effect.get().trim());
        }

        if (form instanceof VanillaParticleForm vanilla && vanilla.settings.get() != null && vanilla.settings.get().particle != null)
        {
            return vanilla.settings.get().particle.method_12832();
        }

        return null;
    }

    /** What a narrow cell's compact strip says: the user's name, else the id's last segment without namespace. */
    public static String compactLabel(Form form)
    {
        String name = form.name.get().trim();

        if (!name.isEmpty())
        {
            return name;
        }

        String particle = particleName(form);

        if (particle != null)
        {
            return particle;
        }

        String id = lastSegment(form.getDisplayName());
        int colon = id.indexOf(':');

        return colon >= 0 && colon < id.length() - 1 ? id.substring(colon + 1) : id;
    }

    private static String lastSegment(String id)
    {
        int slash = id.lastIndexOf('/');

        return slash >= 0 && slash < id.length() - 1 ? id.substring(slash + 1) : id;
    }
}
