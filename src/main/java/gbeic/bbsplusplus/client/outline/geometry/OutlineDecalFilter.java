package gbeic.bbsplusplus.client.outline.geometry;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Finds tiny cubes that are buried inside a much larger cube and therefore should not get their
 * own outline. Eyes, brows, pupils and similar decals sit on (or just inside) the head; a rim
 * around each of them reads as a ring floating inside the face.
 *
 * <p>A cube is skipped when its largest axis is at most {@code maxDetailSize} model pixels, it has
 * positive volume, and another cube with at least eight times its volume contains it (with half a
 * model pixel of tolerance). Setting the decal size to 0 disables the filter.</p>
 */
public final class OutlineDecalFilter
{
    private static final float MIN_VOLUME_RATIO = 8F;
    private static final float TOLERANCE = 0.5F;

    private OutlineDecalFilter()
    {
    }

    public static Set<ModelCube> find(Model model, float maxDetailSize)
    {
        if (model == null || maxDetailSize <= 0F)
        {
            return Collections.emptySet();
        }

        Set<ModelCube> details = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null || group.cubes == null)
            {
                continue;
            }

            for (ModelCube cube : group.cubes)
            {
                if (cube == null || cube.origin == null || cube.size == null)
                {
                    continue;
                }

                float sx = Math.abs(cube.size.x);
                float sy = Math.abs(cube.size.y);
                float sz = Math.abs(cube.size.z);
                float volume = sx * sy * sz;

                if (volume <= 0F || Math.max(sx, Math.max(sy, sz)) > maxDetailSize)
                {
                    continue;
                }

                if (hasHost(model, cube, volume))
                {
                    details.add(cube);
                }
            }
        }

        return details;
    }

    private static boolean hasHost(Model model, ModelCube detail, float detailVolume)
    {
        float[] box = box(detail);

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null || group.cubes == null)
            {
                continue;
            }

            for (ModelCube host : group.cubes)
            {
                if (host == null || host == detail || host.origin == null || host.size == null)
                {
                    continue;
                }

                float[] other = box(host);
                float volume = (other[3] - other[0]) * (other[4] - other[1]) * (other[5] - other[2]);

                if (volume < detailVolume * MIN_VOLUME_RATIO)
                {
                    continue;
                }

                if (other[0] - TOLERANCE <= box[0] && other[1] - TOLERANCE <= box[1] && other[2] - TOLERANCE <= box[2]
                    && other[3] + TOLERANCE >= box[3] && other[4] + TOLERANCE >= box[4] && other[5] + TOLERANCE >= box[5])
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** Normalized bounds in model pixels: {minX,minY,minZ,maxX,maxY,maxZ}. */
    private static float[] box(ModelCube cube)
    {
        Vector3f origin = cube.origin;
        Vector3f size = cube.size;
        float inflate = cube.inflate;
        float ax = origin.x - inflate;
        float ay = origin.y - inflate;
        float az = origin.z - inflate;
        float bx = origin.x + size.x + inflate;
        float by = origin.y + size.y + inflate;
        float bz = origin.z + size.z + inflate;

        return new float[]{
            Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
            Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)
        };
    }
}
