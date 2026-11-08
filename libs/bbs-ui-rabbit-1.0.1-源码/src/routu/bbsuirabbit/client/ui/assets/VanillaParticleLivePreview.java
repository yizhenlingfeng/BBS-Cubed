package routu.bbsuirabbit.client.ui.assets;

import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * One shared renderer for hovering vanilla particle forms in the morph palette — the
 * scene ticks inside the viewport (its particles never enter the world), so this is the
 * same live-preview trick as {@link ParticleLivePreview} with the form driving it.
 */
public class VanillaParticleLivePreview
{
    private final UIFormRenderer renderer = new UIFormRenderer();
    private VanillaParticleForm framedForm;

    public VanillaParticleLivePreview()
    {
        this.renderer.grid = false;
    }

    public void clear()
    {
        this.framedForm = null;
        this.renderer.form = null;
    }

    public boolean render(UIContext context, VanillaParticleForm form, int x, int y, int w, int h)
    {
        if (form == null)
        {
            return false;
        }

        this.renderer.form = form;

        if (form != this.framedForm)
        {
            this.framedForm = form;

            float r = Math.max(Math.max(form.offsetX.get(), form.offsetY.get()), Math.max(form.offsetZ.get(), form.velocity.get() * 8));

            r = Math.max(0.5F, Math.min(4F, r + 0.35F));

            this.renderer.setPosition(0, 0, 0);
            this.renderer.setRotation(30, 15);

            float fov = this.renderer.camera.fov;

            if (fov > Math.PI)
            {
                fov = (float) Math.toRadians(fov);
            }

            if (fov > 0.01F)
            {
                this.renderer.setDistance((float) (r / Math.tan(fov / 2)));
            }
        }

        return LivePreviewClip.render(context, this.renderer.area, x, y, w, h, () -> this.renderer.render(context));
    }
}
