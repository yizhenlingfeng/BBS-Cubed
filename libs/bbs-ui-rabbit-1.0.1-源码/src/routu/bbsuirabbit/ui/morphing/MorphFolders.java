package routu.bbsuirabbit.ui.morphing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.resources.Link;

import java.io.File;

/**
 * The folder on disk behind a morph palette entry, for "open containing folder": the folder a
 * particle effect sits in, the folder of a billboard/extruded texture or a video. Models are
 * left out of {@link #ofForm} — BBS's own cell menu already opens a model's folder. Null
 * whenever there is no real directory (resource-pack models, mobs, blocks...).
 */
public final class MorphFolders
{
    private MorphFolders()
    {}

    public static File ofForm(Form form)
    {
        if (form instanceof ParticleForm particle && !particle.effect.get().trim().isEmpty())
        {
            String effect = particle.effect.get().trim();
            int slash = effect.lastIndexOf('/');

            return dir(BBSMod.getAssetsPath("particles/" + (slash >= 0 ? effect.substring(0, slash) : "")));
        }

        if (form instanceof BillboardForm billboard)
        {
            return parentOf(billboard.texture.get());
        }

        if (form instanceof ExtrudedForm extruded)
        {
            return parentOf(extruded.texture.get());
        }

        if (form instanceof VideoForm video)
        {
            return parentOf(video.video.get());
        }

        return null;
    }

    /** A real folder node of the sidebar ("group:models[/a/b]", "group:particles[/a]"); virtual folders have none. */
    public static File ofNode(String nodeId)
    {
        if (nodeId.equals("group:models") || nodeId.startsWith("group:models/"))
        {
            return dir(BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + nodeId.substring("group:models".length()).replaceFirst("^/", "")));
        }

        if (nodeId.equals("group:particles") || nodeId.startsWith("group:particles/"))
        {
            return dir(BBSMod.getAssetsPath("particles/" + nodeId.substring("group:particles".length()).replaceFirst("^/", "")));
        }

        return null;
    }

    private static File parentOf(Link link)
    {
        if (link == null)
        {
            return null;
        }

        try
        {
            File file = BBSMod.getProvider().getFile(link);

            return file == null ? null : dir(file.getParentFile());
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static File dir(File file)
    {
        return file != null && file.isDirectory() ? file : null;
    }
}
