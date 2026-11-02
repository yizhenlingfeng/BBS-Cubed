package gbeic.bbsplusplus.client.outline.geometry;

import gbeic.bbsplusplus.client.outline.OutlineConfigHolder;
import gbeic.bbsplusplus.client.outline.OutlineLimbValue;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
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

    private OutlineShellKey()
    {
    }

    /** A computed key plus the geometry-only part, used to drop stale alpha masks after edits. */
    public record Key(long value, long geometryHash)
    {
    }

    public static Key of(Model model, ModelConfig config, OutlineConfigHolder outline, Link textureLink,
                         Predicate<String> materialVisible)
    {
        Predicate<String> visible = materialVisible == null ? material -> true : materialVisible;
        Map<String, Boolean> visibility = new HashMap<>();
        long pose = SEED;
        long geometry = SEED;

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
            pose = pose * 31 + (current == null ? 0 : current.contentHash());
            pose = pose * 31 + (initial == null ? 0 : initial.contentHash());
            pose = pose * 31 + (group.orient == null ? 0 : group.orient.hashCode());
            pose = pose * 31 + (group.offset == null ? 0 : group.offset.hashCode());

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

        long hash = pose;
        hash = hash * 31 + geometry;
        hash = hash * 31 + (outline.bbspp$isOutlineEnabled() ? 1 : 0);
        hash = hash * 31 + outline.bbspp$getOutlineColor();
        hash = hash * 31 + Float.floatToIntBits(outline.bbspp$getOutlineWidth());
        hash = hash * 31 + Float.floatToIntBits(outline.bbspp$getOutlineGlow());
        hash = hash * 31 + Float.floatToIntBits(outline.bbspp$getOutlineDecalSize());

        List<OutlineLimbValue> limbs = outline.bbspp$getOutlineLimbs();

        if (limbs != null)
        {
            hash = hash * 31 + limbs.size();

            for (OutlineLimbValue limb : limbs)
            {
                if (limb == null)
                {
                    continue;
                }

                hash = hash * 31 + stringHash(limb.bone.get());
                hash = hash * 31 + limb.color.get().getARGBColor();
                hash = hash * 31 + Float.floatToIntBits(limb.width.get());
                hash = hash * 31 + Float.floatToIntBits(limb.glow.get());
            }
        }

        hash = hash * 31 + (config == null ? 0 : OutlineGeometry.weldTable(config).hashCode());

        Texture texture = textureLink == null ? null : BBSModClient.getTextures().getTexture(textureLink);
        int textureId = 0;
        int textureValid = 0;

        if (texture != null && texture != BBSModClient.getTextures().getError())
        {
            textureId = texture.id;
            textureValid = texture.isValid() ? 1 : 0;
        }

        hash = hash * 31 + textureId;
        hash = hash * 31 + textureValid;
        hash = hash * 31 + OutlineAlphaMask.version();

        return new Key(hash, geometry);
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
