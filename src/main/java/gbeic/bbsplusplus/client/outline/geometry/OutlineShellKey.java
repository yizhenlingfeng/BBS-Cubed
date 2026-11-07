package gbeic.bbsplusplus.client.outline.geometry;

import gbeic.bbsplusplus.api.GroupOutlineHolder;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Version-independent reuse key for the outline shell.
 *
 * <p>Everything that can change the baked positions or colours goes in; the caller's stack (1.20.x
 * passes it as the model-view matrix, 1.21.11 folds it in separately), the form opacity and the
 * form colour do not.</p>
 */
public final class OutlineShellKey
{
    private static final long SEED = 1125899906842597L;

    /**
     * Quantisation step for animated pose values. A rebuild re-emits every shell vertex, so
     * sub-pixel jitter from IK or physics settling would otherwise force a rebuild every frame.
     * 1/4096 of a block or quaternion component stays below one pixel at normal camera distances.
     */
    private static final float POSE_QUANT = 4096F;

    private OutlineShellKey()
    {
    }

    /** A computed key plus the geometry-only part, used to drop stale alpha masks after edits. */
    public record Key(long value, long geometryHash)
    {
    }

    public static Key of(Model model, ModelConfig config, Link textureLink, Predicate<String> materialVisible)
    {
        Predicate<String> visible = materialVisible == null ? material -> true : materialVisible;
        Map<String, Boolean> visibility = new HashMap<>();
        long pose = SEED;
        long geometry = SEED;
        long outline = SEED;

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null)
            {
                continue;
            }

            pose = pose * 31 + stringHash(group.id);
            pose = pose * 31 + (group.isVisible() ? 1 : 0);

            Transform current = group.current;
            Transform initial = group.initial;
            /* Current is the animated half: quantise it so sub-visible jitter cannot rebuild the
             * shell. Initial only changes when the file is edited, so it stays exact. The
             * constraint stages write orient/offset separately from current (see ModelGroup), so
             * those get the same treatment. */
            pose = pose * 31 + (current == null ? 0 : quantizedPoseHash(current));
            pose = pose * 31 + (initial == null ? 0 : initial.contentHash());
            pose = pose * 31 + (group.orient == null ? 0 : quantizedQuatHash(group.orient));
            pose = pose * 31 + (group.offset == null ? 0 : quantizedVectorHash(group.offset));

            GroupOutlineHolder holder = (GroupOutlineHolder) group;

            outline = outline * 31 + stringHash(group.id);
            outline = layerHash(outline, holder, 0);
            outline = layerHash(outline, holder, 1);
            /* Decal filtering is model-wide (one set of hidden cubes per model), so it only ever
             * reads the main layer's threshold and is hashed once. */
            outline = outline * 31 + Float.floatToIntBits(holder.bbspp_cml$getOutlineDecalSize());

            geometry = geometry * 31 + stringHash(group.id);

            if (group.cubes == null)
            {
                continue;
            }

            for (ModelCube cube : group.cubes)
            {
                geometry = cubeHash(geometry, cube, visibility, visible);
            }
        }

        long weld = config == null ? 0 : OutlineGeometry.weldTable(config).hashCode();
        long hash = pose;
        hash = hash * 31 + geometry;
        hash = hash * 31 + outline;
        hash = hash * 31 + weld;

        Texture texture = textureLink == null ? null : BBSModClient.getTextures().getTexture(textureLink);
        int textureId = 0;
        int textureValid = 0;

        if (texture != null && texture != BBSModClient.getTextures().getError())
        {
            textureId = texture.id;
            textureValid = texture.isValid() ? 1 : 0;
        }

        long alphaVersion = OutlineAlphaMask.version();
        int iris = BBSRendering.isIrisShadersEnabled() ? 1 : 0;

        hash = hash * 31 + textureId;
        hash = hash * 31 + textureValid;
        hash = hash * 31 + alphaVersion;
        /* The resident list of glow specs is only filled while a shader pack is active, so toggling
         * one on has to invalidate the shell - otherwise a model edited without shaders keeps the
         * emission-less entry until something else happens to rebuild it. */
        hash = hash * 31 + iris;

        return new Key(hash, geometry);
    }

    private static long quantizedPoseHash(Transform transform)
    {
        long hash = SEED;

        hash = hash * 31 + quantize(transform.translate.x);
        hash = hash * 31 + quantize(transform.translate.y);
        hash = hash * 31 + quantize(transform.translate.z);
        hash = hash * 31 + quantize(transform.scale.x);
        hash = hash * 31 + quantize(transform.scale.y);
        hash = hash * 31 + quantize(transform.scale.z);
        hash = hash * 31 + quantize(transform.rotate.x);
        hash = hash * 31 + quantize(transform.rotate.y);
        hash = hash * 31 + quantize(transform.rotate.z);
        hash = hash * 31 + quantize(transform.quat.x);
        hash = hash * 31 + quantize(transform.quat.y);
        hash = hash * 31 + quantize(transform.quat.z);
        hash = hash * 31 + quantize(transform.quat.w);

        return hash * 31 + transform.rotationMode.ordinal();
    }

    private static long quantizedQuatHash(Quaternionf quat)
    {
        long hash = SEED;

        hash = hash * 31 + quantize(quat.x);
        hash = hash * 31 + quantize(quat.y);
        hash = hash * 31 + quantize(quat.z);

        return hash * 31 + quantize(quat.w);
    }

    private static long quantizedVectorHash(Vector3f vector)
    {
        long hash = SEED;

        hash = hash * 31 + quantize(vector.x);
        hash = hash * 31 + quantize(vector.y);

        return hash * 31 + quantize(vector.z);
    }

    private static int quantize(float value)
    {
        return Math.round(value * POSE_QUANT);
    }

    /** Hash of the caller's stack matrix, for backends that bake it into the cached vertices. */
    public static long baseHash(Matrix4f matrix)
    {
        long hash = SEED;

        hash = hash * 31 + Float.floatToIntBits(matrix.m00());
        hash = hash * 31 + Float.floatToIntBits(matrix.m01());
        hash = hash * 31 + Float.floatToIntBits(matrix.m02());
        hash = hash * 31 + Float.floatToIntBits(matrix.m03());
        hash = hash * 31 + Float.floatToIntBits(matrix.m10());
        hash = hash * 31 + Float.floatToIntBits(matrix.m11());
        hash = hash * 31 + Float.floatToIntBits(matrix.m12());
        hash = hash * 31 + Float.floatToIntBits(matrix.m13());
        hash = hash * 31 + Float.floatToIntBits(matrix.m20());
        hash = hash * 31 + Float.floatToIntBits(matrix.m21());
        hash = hash * 31 + Float.floatToIntBits(matrix.m22());
        hash = hash * 31 + Float.floatToIntBits(matrix.m23());
        hash = hash * 31 + Float.floatToIntBits(matrix.m30());
        hash = hash * 31 + Float.floatToIntBits(matrix.m31());
        hash = hash * 31 + Float.floatToIntBits(matrix.m32());

        return hash * 31 + Float.floatToIntBits(matrix.m33());
    }

    /**
     * Mixes one outline layer into the key.
     *
     * <p>Every value the geometry or the baked vertex colours depend on has to be here, or editing
     * it would leave the old VBO on screen until something unrelated forces a rebuild. The face
     * table is stored as a <em>copied-out</em> view, so the per-face colours are hashed even when
     * only one of them differs.</p>
     */
    private static long layerHash(long hash, GroupOutlineHolder holder, int layer)
    {
        hash = hash * 31 + (holder.bbspp_cml$hasOutlineLayer(layer) ? 1 : 0);
        hash = hash * 31 + holder.bbspp_cml$getOutlineColor(layer).getARGBColor();
        hash = hash * 31 + Float.floatToIntBits(holder.bbspp_cml$getOutlineShellWidth(layer));
        hash = hash * 31 + Float.floatToIntBits(holder.bbspp_cml$getOutlineGlow(layer));
        hash = hash * 31 + (holder.bbspp_cml$getOutlineFaceCull(layer) ? 1 : 0);

        for (int face = 0; face < gbeic.bbsplusplus.api.BoneOutlineHolder.FACE_COUNT; face++)
        {
            hash = hash * 31 + holder.bbspp_cml$getOutlineFaceColor(layer, face);
        }

        /* Ramps change the baked vertex colours (and the emissive batch id), so they belong in the
         * key exactly like the colours do; a null ramp hashes to a stable zero. */
        hash = hash * 31 + holder.bbspp_cml$getOutlineGradientHash(layer);

        /* A ramped layer is sampled along the camera's screen axes, so its vertices only hold for
         * the frame they were baked in: including that frame here rebuilds the shell as the camera
         * turns and leaves it alone while it does not. Solid layers never see this value. */
        if (holder.bbspp_cml$hasOutlineLayer(layer)
            && holder.bbspp_cml$getOutlineShellWidth(layer) > 0F
            && holder.bbspp_cml$hasOutlineGradients(layer))
        {
            hash = hash * 31 + OutlineGeometry.screenFrameHash();
        }

        return hash;
    }

    private static long cubeHash(long hash, ModelCube cube, Map<String, Boolean> visibility,
                                 Predicate<String> visible)
    {
        if (cube == null || cube.origin == null || cube.size == null)
        {
            return hash * 31 + 1;
        }

        hash = hash * 31 + Float.floatToIntBits(cube.origin.x);
        hash = hash * 31 + Float.floatToIntBits(cube.origin.y);
        hash = hash * 31 + Float.floatToIntBits(cube.origin.z);
        hash = hash * 31 + Float.floatToIntBits(cube.size.x);
        hash = hash * 31 + Float.floatToIntBits(cube.size.y);
        hash = hash * 31 + Float.floatToIntBits(cube.size.z);
        hash = vectorHash(hash, cube.pivot);
        hash = vectorHash(hash, cube.rotate);
        hash = hash * 31 + Float.floatToIntBits(cube.inflate);
        hash = hash * 31 + stringHash(cube.material);
        hash = uvHash(hash, cube.front);
        hash = uvHash(hash, cube.right);
        hash = uvHash(hash, cube.back);
        hash = uvHash(hash, cube.left);
        hash = uvHash(hash, cube.top);
        hash = uvHash(hash, cube.bottom);

        String material = cube.material == null ? "" : cube.material;
        boolean materialShown = visibility.computeIfAbsent(material, key -> visible.test(key));

        return hash * 31 + (materialShown ? 1 : 0);
    }

    private static long vectorHash(long hash, Vector3f vector)
    {
        if (vector == null)
        {
            return hash * 31 + 1;
        }

        hash = hash * 31 + Float.floatToIntBits(vector.x);
        hash = hash * 31 + Float.floatToIntBits(vector.y);

        return hash * 31 + Float.floatToIntBits(vector.z);
    }

    private static long uvHash(long hash, ModelUV uv)
    {
        if (uv == null)
        {
            return hash * 31 + 1;
        }

        hash = hash * 31 + Float.floatToIntBits(uv.origin.x);
        hash = hash * 31 + Float.floatToIntBits(uv.origin.y);
        hash = hash * 31 + Float.floatToIntBits(uv.size.x);
        hash = hash * 31 + Float.floatToIntBits(uv.size.y);

        return hash * 31 + Float.floatToIntBits(uv.rotation);
    }

    private static int stringHash(String value)
    {
        return value == null ? 0 : value.hashCode();
    }
}
