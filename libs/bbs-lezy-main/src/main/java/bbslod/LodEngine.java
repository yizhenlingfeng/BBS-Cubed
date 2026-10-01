package bbslod;

import mchorse.bbs_mod.api.client.events.FilmEvents;
import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Caps how many of a scene's forms get drawn per frame with focus-distance support.
 *
 * <p>Uses camera look-direction (frustum weighting) so that actors in the camera's field of view
 * are prioritized over actors behind the camera, eliminating ghost renders and missing replays.
 * Tracks both position and rotation with a sensitive deadband for smooth, responsive movement.
 * The model of a selected replay obeys LOD and focus culling rules, while its transform gizmo
 * remains visible in the editor.</p>
 */
public class LodEngine
{
    /** Forms whose visible runtimeValue was set to false this frame, restored on RENDER_AFTER. */
    private static final Set<Form> touched = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Forms determined by the latest ranking to be outside the render limit / focus window. */
    private static final Set<Form> culled = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final List<Candidate> candidates = new ArrayList<>();

    private static BaseFilmController lastController;
    private static double lastCamX = Double.NaN;
    private static double lastCamY = Double.NaN;
    private static double lastCamZ = Double.NaN;
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static double lastFocus = -1D;
    private static int lastLimit = -1;
    private static boolean lastEnabled = false;
    private static int lastEntityCount = -1;
    private static boolean budgetValid = false;

    /** Movement deadband: ~0.1 blocks. */
    private static final double MOVE_THRESHOLD_SQ = 0.01D;

    /** Rotation deadband: 0.5 degrees. */
    private static final float ROTATION_THRESHOLD = 0.5F;

    public static void register()
    {
        FormRenderEvents.BEFORE.register(LodEngine::before);
        FilmEvents.RENDER_AFTER.register(LodEngine::onRenderAfter);
        FilmEvents.SHUTDOWN.register(LodEngine::onShutdown);
    }

    /**
     * Intercepts form rendering: if this form was ranked as culled for the current frame,
     * set its visible runtime value to false so its model, shadow and nametag are skipped.
     * The editor gizmo does not check form.visible, so the transform gizmo remains visible
     * on selected replays even when the model is culled.
     */
    private static void before(Form form, FormRenderingContext context)
    {
        if (context.ui || !budgetValid || !LodSettings.enabled.get() || !form.visible.get())
        {
            return;
        }

        if (culled.contains(form))
        {
            form.visible.setRuntimeValue(Boolean.FALSE);
            touched.add(form);
        }
    }

    /**
     * Ranks this controller's forms by distance/focus to the camera with view-direction weighting.
     */
    private static void onRenderAfter(BaseFilmController controller, WorldRenderContext context)
    {
        clearOverrides(controller);

        boolean enabled = LodSettings.enabled.get();
        int limit = LodSettings.renderLimit.get();

        if (controller != lastController)
        {
            lastController = controller;
            reset();
        }

        budgetValid = enabled && limit > 0;

        if (!budgetValid)
        {
            culled.clear();
            return;
        }

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        double focus = LodSettings.focusDistance.get();
        int entityCount = controller.getEntities().size();

        double dx = camPos.x - lastCamX;
        double dy = camPos.y - lastCamY;
        double dz = camPos.z - lastCamZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        float dYaw = Math.abs(yaw - lastYaw);
        float dPitch = Math.abs(pitch - lastPitch);

        boolean settingsChanged = enabled != lastEnabled || limit != lastLimit || focus != lastFocus || entityCount != lastEntityCount;
        boolean camMoved = distSq >= MOVE_THRESHOLD_SQ;
        boolean camRotated = dYaw >= ROTATION_THRESHOLD || dPitch >= ROTATION_THRESHOLD;

        /* Skip re-ranking only when camera is completely stationary and settings are unchanged */
        if (!settingsChanged && !camMoved && !camRotated && !candidates.isEmpty())
        {
            return;
        }

        lastCamX = camPos.x;
        lastCamY = camPos.y;
        lastCamZ = camPos.z;
        lastYaw = yaw;
        lastPitch = pitch;
        lastFocus = focus;
        lastLimit = limit;
        lastEnabled = enabled;
        lastEntityCount = entityCount;

        /* Compute forward gaze direction from camera yaw and pitch */
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double lookX = -Math.sin(yawRad) * cosPitch;
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * cosPitch;

        int count = 0;
        for (IEntity entity : controller.getEntities().values())
        {
            Form form = entity.getForm();

            if (form == null || form.anchor.get().hasTarget())
            {
                continue;
            }

            double fx = entity.getX() - camPos.x;
            double fy = entity.getY() - camPos.y;
            double fz = entity.getZ() - camPos.z;
            double dist = Math.sqrt(fx * fx + fy * fy + fz * fz);
            double invDist = dist > 0.0001D ? 1.0D / dist : 0.0D;
            double dot = (fx * lookX + fy * lookY + fz * lookZ) * invDist;

            double err = focus > 0D ? Math.abs(dist - focus) : dist;
            double score;

            /* Frustum weighting:
             * dot < 0.1 means the actor is behind or outside the camera's view — heavy penalty so it
             * never steals quota from actors the user is looking at.
             * dot >= 0.1 slightly favors actors toward screen center. */
            if (dot < 0.1D)
            {
                score = err + 1000.0D * (0.1D - dot);
            }
            else
            {
                score = err + (1.0D - dot) * 0.5D;
            }

            if (count < candidates.size())
            {
                candidates.get(count).set(form, score);
            }
            else
            {
                candidates.add(new Candidate(form, score));
            }
            count++;
        }

        while (candidates.size() > count)
        {
            candidates.remove(candidates.size() - 1);
        }

        candidates.sort(null);

        /* Update culled set for the next frame's BEFORE pass */
        culled.clear();
        int effectiveLimit = Math.min(limit, count);
        for (int i = effectiveLimit; i < count; i++)
        {
            culled.add(candidates.get(i).form);
        }
    }

    private static void onShutdown(BaseFilmController controller)
    {
        clearOverrides(controller);
        lastController = null;
        reset();
    }

    private static void reset()
    {
        lastCamX = Double.NaN;
        lastCamY = Double.NaN;
        lastCamZ = Double.NaN;
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        lastFocus = -1D;
        lastLimit = -1;
        lastEntityCount = -1;
        lastEnabled = false;
        budgetValid = false;
        culled.clear();
    }

    /**
     * Clears every runtime override the controller's root forms carry.
     */
    private static void clearOverrides(BaseFilmController controller)
    {
        for (Form form : touched)
        {
            form.visible.setRuntimeValue(null);
        }

        touched.clear();
    }

    private static class Candidate implements Comparable<Candidate>
    {
        public Form form;
        public double score;

        public Candidate(Form form, double score)
        {
            this.form = form;
            this.score = score;
        }

        public void set(Form form, double score)
        {
            this.form = form;
            this.score = score;
        }

        @Override
        public int compareTo(Candidate other)
        {
            return Double.compare(this.score, other.score);
        }
    }
}
