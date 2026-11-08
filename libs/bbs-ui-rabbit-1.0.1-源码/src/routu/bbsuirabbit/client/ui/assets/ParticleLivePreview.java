package routu.bbsuirabbit.client.ui.assets;

import routu.bbsuirabbit.mixin.UIModelRendererAccessor;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.expiration.ParticleComponentKillPlane;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.particles.UIParticleSchemeRenderer;
import mchorse.bbs_mod.utils.manager.BaseManager;

/**
 * One shared live particle emitter, reusable wherever a thumbnail should play: the
 * scheme is loaded through BaseManager (the shipped jar lacks the covariant override),
 * duplicated and completed with the kill plane renderUserModel dereferences. Renders by
 * hand — never mounted into an element tree.
 */
public class ParticleLivePreview
{
    private final UIParticleSchemeRenderer preview = new UIParticleSchemeRenderer();
    private String effectId;
    private long lastFrame;
    private ParticleScheme scheme;
    /* Burst effects (emitter_lifetime_once / rate_instant) must be re-armed to replay */
    private boolean producedThisRun;
    private long emptySince;
    private long restartAt;
    private boolean quiet;

    public ParticleLivePreview()
    {
        this.preview.grid = false;
    }

    /* Smoothed auto-framing: tiny effects were ending up as 1-3px dots at the edge. */
    private float cx, cy, cz;
    private float radius = 0.3F;
    private boolean framed;

    public void clear()
    {
        if (this.effectId != null && this.preview.emitter != null)
        {
            this.preview.emitter.particles.clear();
        }

        this.effectId = null;
        this.scheme = null;
        this.quiet = false;
    }

    /** Live particles drawn this frame — false = the caller should paint the sprite. */
    public boolean hasParticles()
    {
        return this.preview.emitter != null && !this.preview.emitter.particles.isEmpty();
    }

    /** Fresh emitter at tick "now" — re-arms one-shot bursts and skips the catch-up. */
    private void restart(UIContext context)
    {
        if (this.scheme == null)
        {
            return;
        }

        this.preview.setScheme(this.scheme);
        this.cx = this.cy = this.cz = 0F;
        this.radius = 0.3F;
        this.framed = true;
        this.producedThisRun = false;
        this.emptySince = 0;
        this.restartAt = System.currentTimeMillis();

        ((UIModelRendererAccessor) this.preview).rabbit$setTick(context.getTick());
    }

    /** True when the live preview actually drew; false = caller should draw the static sprite. */
    public boolean render(UIContext context, String effectId, Link textureOverride, int x, int y, int w, int h)
    {
        if (effectId == null)
        {
            return false;
        }

        if (!effectId.equals(this.effectId))
        {
            this.effectId = effectId;

            try
            {
                Object loaded = ((BaseManager<?>) BBSModClient.getParticles()).load(effectId);

                if (!(loaded instanceof ParticleScheme scheme))
                {
                    this.effectId = null;

                    return false;
                }

                ParticleScheme copy = ParticleScheme.dupe(scheme);

                copy.getOrCreate(ParticleComponentKillPlane.class);
                this.scheme = copy;
                this.restart(context);
            }
            catch (Throwable t)
            {
                this.effectId = null;

                return false;
            }
        }

        if (this.preview.emitter == null || this.preview.emitter.scheme == null
            || this.preview.emitter.scheme.get(ParticleComponentKillPlane.class) == null)
        {
            return false;
        }


        /* A long pause without a live frame re-arms the emitter rather than a dead view */
        long now = System.currentTimeMillis();

        if (now - this.lastFrame > 250)
        {
            this.restart(context);
        }

        this.lastFrame = now;

        /* Track the particles' bounds with asymmetric smoothing: grow fast, shrink slow */
        double minX = Double.MAX_VALUE, minY = minX, minZ = minX;
        double maxX = -Double.MAX_VALUE, maxY = maxX, maxZ = maxX;

        for (mchorse.bbs_mod.particles.emitter.Particle particle : this.preview.emitter.particles)
        {
            org.joml.Vector3d pos = particle.getGlobalPosition(this.preview.emitter);

            minX = Math.min(minX, pos.x); minY = Math.min(minY, pos.y); minZ = Math.min(minZ, pos.z);
            maxX = Math.max(maxX, pos.x); maxY = Math.max(maxY, pos.y); maxZ = Math.max(maxZ, pos.z);
        }

        if (minX <= maxX)
        {
            float tx = (float) ((minX + maxX) / 2), ty = (float) ((minY + maxY) / 2), tz = (float) ((minZ + maxZ) / 2);
            float tr = (float) (Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ) / 2 * 1.25 + 0.03);

            this.cx += (tx - this.cx) * 0.2F;
            this.cy += (ty - this.cy) * 0.2F;
            this.cz += (tz - this.cz) * 0.2F;
            this.radius += (tr - this.radius) * (tr > this.radius ? 0.5F : 0.05F);
        }

        /* Framing applies every frame — empty emitters would otherwise sit at the reset() defaults */
        this.preview.setPosition(this.cx, this.cy, this.cz);

        float fov = this.preview.camera.fov;

        if (fov > Math.PI)
        {
            fov = (float) Math.toRadians(fov);
        }

        if (fov > 0.01F)
        {
            this.preview.setDistance((float) (this.radius / Math.tan(fov / 2)));
        }

        /* Loop one-shots while hovered: once a burst finished and the emitter sat empty
         * for a while, arm it again — unless it never produced anything (broken scheme). */
        boolean running = this.preview.emitter.running;

        if (!this.preview.emitter.particles.isEmpty())
        {
            this.producedThisRun = true;
            this.emptySince = 0;
        }
        else if (this.emptySince == 0)
        {
            this.emptySince = now;
        }

        if (this.quiet && now - this.restartAt > 1500)
        {
            this.quiet = false;
        }

        if (!this.quiet && this.producedThisRun && this.emptySince != 0 && (!running || now - this.emptySince >= 400))
        {
            this.restart(context);
        }

        this.preview.emitter.texture = textureOverride;
        /* Grow the rect so the debug readout at the bottom edge lands outside our clip */
        return LivePreviewClip.render(context, this.preview.area, x, y, w, h, () -> this.preview.render(context));
    }
}
