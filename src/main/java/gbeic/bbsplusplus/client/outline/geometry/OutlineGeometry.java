package gbeic.bbsplusplus.client.outline.geometry;

import gbeic.bbsplusplus.client.outline.OutlineConfigHolder;
import gbeic.bbsplusplus.client.outline.OutlineLimbValue;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.WeldValue;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The shared, loader-independent half of the outline renderer.
 *
 * <p>This class walks a cubic model, builds the negative-volume shell geometry and hands raw
 * world-space vertices to a {@link VertexSink}. It deliberately knows nothing about
 * {@code BufferBuilder}, render pipelines or shaders: 1.20.x and 1.21.11 each provide their own
 * sink. That keeps the geometry, the bone maths and the rim/weld/alpha rules identical on both
 * Minecraft versions.</p>
 *
 * <p>The shell is a "negative volume": the real cube bounds, inflated by {@code 0.25 * width}
 * model pixels, are written with min/max swapped, so the six faces point inwards. With back-face
 * culling the body hides the near half and only the rim around the silhouette survives. This is
 * what makes a box outline instead of a solid box drawn over the model.</p>
 */
public final class OutlineGeometry
{
    /** Face indices follow BBS {@code ModelCube.generateQuads}: front, right, back, left, top, bottom. */
    public static final int FACE_FRONT = 0;
    public static final int FACE_RIGHT = 1;
    public static final int FACE_BACK = 2;
    public static final int FACE_LEFT = 3;
    public static final int FACE_TOP = 4;
    public static final int FACE_BOTTOM = 5;

    /** Shell face -> the cube face it really covers (the shell points the other way). */
    private static final int[] SHELL_TO_CUBE_FACE = { FACE_BACK, FACE_LEFT, FACE_FRONT, FACE_RIGHT, FACE_BOTTOM, FACE_TOP };

    private static final String[] FACE_NAMES = { "front", "right", "back", "left", "top", "bottom" };

    private OutlineGeometry()
    {
    }

    /** One draw: a group, its outline colour/width/glow and the colour already split into floats. */
    public record Spec(ModelGroup group, int argb, float width, float glow, float r, float g, float b, float a)
    {
    }

    /** Receives world-space vertices in triangle order. */
    @FunctionalInterface
    public interface VertexSink
    {
        void vertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float a);
    }

    /**
     * Collects the groups that should get an outline.
     *
     * <p>Global settings apply to every visible group; a limb entry replaces the global colour,
     * width and glow for the group with the same bone name. Width 0 disables either kind.</p>
     *
     * <p>The global toggle is not a master switch for limb entries. It only controls whether the
     * whole model receives the global outline; an enabled limb still renders on its own.</p>
     */
    public static List<Spec> collect(Model model, ModelConfig config, OutlineConfigHolder outline, Set<ModelCube> decalCubes)
    {
        if (model == null || config == null || outline == null)
        {
            return Collections.emptyList();
        }

        Map<ModelGroup, Spec> specs = new LinkedHashMap<>();
        int globalArgb = outline.bbspp$getOutlineColor();
        float globalWidth = outline.bbspp$getOutlineWidth();

        if (outline.bbspp$isOutlineEnabled() && globalWidth > 0F)
        {
            for (ModelGroup group : model.getAllGroups())
            {
                if (group == null || !group.isVisible() || group.cubes == null || group.cubes.isEmpty())
                {
                    continue;
                }

                specs.put(group, new Spec(group, globalArgb, globalWidth, outline.bbspp$getOutlineGlow(),
                    channel(globalArgb, 16), channel(globalArgb, 8), channel(globalArgb, 0), channel(globalArgb, 24)));
            }
        }

        for (OutlineLimbValue limb : outline.bbspp$getOutlineLimbs())
        {
            if (limb == null)
            {
                continue;
            }

            String bone = limb.bone.get();

            if (bone == null || bone.isEmpty())
            {
                continue;
            }

            ModelGroup group = model.getGroup(bone);

            if (group == null || !group.isVisible() || group.cubes == null || group.cubes.isEmpty())
            {
                continue;
            }

            float width = limb.width.get();

            if (width <= 0F)
            {
                continue;
            }

            int argb = limb.color.get().getARGBColor();

            specs.put(group, new Spec(group, argb, width, limb.glow.get(),
                channel(argb, 16), channel(argb, 8), channel(argb, 0), channel(argb, 24)));
        }

        return new ArrayList<>(specs.values());
    }

    private static float channel(int argb, int shift)
    {
        return ((argb >> shift) & 0xFF) / 255F;
    }

    /**
     * Emits the shell for every collected spec.
     *
     * @param base           the caller's model-view matrix (the stack at {@code ModelInstance.render} entry)
     * @param formAlpha      the form's own opacity, multiplied into every outline pixel
     * @param faceRects      per-cube alpha coverage rectangles from {@link OutlineAlphaMask}; null = no clipping
     * @param decalCubes     small cubes buried inside larger ones that should not get a rim
     */
    public static void emit(Model model, ModelConfig config, List<Spec> specs, Matrix4f base, float formAlpha,
                            VertexSink sink, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decalCubes)
    {
        emit(model, config, specs, base, formAlpha, sink, faceRects, decalCubes, material -> true);
    }

    /**
     * Same as above, but lets the caller hide cubes whose material is switched off in the
     * material tab (otherwise their rims would remain visible around invisible geometry).
     */
    public static void emit(Model model, ModelConfig config, List<Spec> specs, Matrix4f base, float formAlpha,
                            VertexSink sink, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decalCubes,
                            Predicate<String> materialVisible)
    {
        if (model == null || config == null || specs == null || specs.isEmpty() || sink == null)
        {
            return;
        }

        Predicate<String> visible = materialVisible == null ? material -> true : materialVisible;
        Map<ModelGroup, Matrix4f> matrices = new IdentityHashMap<>();
        Matrix4f root = new Matrix4f(base);

        for (ModelGroup group : model.topGroups)
        {
            collectGroupMatrices(group, root, matrices);
        }

        Emitter emitter = new Emitter(sink);
        Set<ModelCube> skip = decalCubes == null ? Collections.emptySet() : decalCubes;

        for (Spec spec : specs)
        {
            Matrix4f groupMatrix = matrices.get(spec.group());

            if (groupMatrix == null)
            {
                continue;
            }

            Set<String> welded = collectWeldedFaces(config, spec.group().id);
            float alpha = spec.a() * formAlpha;

            if (alpha <= 0F)
            {
                continue;
            }

            for (ModelCube cube : spec.group().cubes)
            {
                if (cube == null || cube.origin == null || cube.size == null || skip.contains(cube)
                    || !visible.test(cube.material))
                {
                    continue;
                }

                emitter.cube(groupMatrix, cube, spec.width(), spec.r(), spec.g(), spec.b(), alpha, welded,
                    faceRects == null ? null : faceRects.get(cube));
            }
        }
    }

    private static void collectGroupMatrices(ModelGroup group, Matrix4f parent, Map<ModelGroup, Matrix4f> out)
    {
        if (group == null)
        {
            return;
        }

        Matrix4f matrix = new Matrix4f(parent);

        applyGroupTransform(matrix, group);
        out.put(group, matrix);

        if (group.children != null)
        {
            for (ModelGroup child : group.children)
            {
                collectGroupMatrices(child, matrix, out);
            }
        }
    }

    /** Mirrors {@code ICubicRenderer.applyGroupTransformations}, in the same order. */
    private static void applyGroupTransform(Matrix4f matrix, ModelGroup group)
    {
        if (group.offset != null)
        {
            matrix.translate(group.offset.x, group.offset.y, group.offset.z);
        }

        Transform current = group.current;
        Transform initial = group.initial;

        if (current != null && initial != null)
        {
            Vector3f translate = current.translate;
            Vector3f pivot = initial.translate;

            if (translate != null && pivot != null)
            {
                matrix.translate(-(translate.x - pivot.x) / 16F, (translate.y - pivot.y) / 16F, (translate.z - pivot.z) / 16F);
                matrix.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
            }
        }

        if (current != null)
        {
            if (group.orient != null)
            {
                matrix.rotate(group.orient);
            }
            else if (current.rotationMode == Transform.RotationMode.QUATERNION)
            {
                matrix.rotate(current.quat);
            }
            else
            {
                Vector3f rotate = current.rotate;

                if (rotate != null && (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F))
                {
                    matrix.rotate(Matrices.toLocalRotationZYXDegrees(rotate));
                }
            }

            Vector3f scale = current.scale;

            if (scale != null)
            {
                matrix.scale(scale.x, scale.y, scale.z);
            }
        }

        if (initial != null && initial.translate != null)
        {
            Vector3f pivot = initial.translate;

            matrix.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
        }
    }

    /** Collects the welded seams of a bone, mapped to the shell's reversed face names. */
    public static Set<String> collectWeldedFaces(ModelConfig config, String bone)
    {
        Set<String> out = new java.util.HashSet<>();

        if (config == null || bone == null || config.welds == null)
        {
            return out;
        }

        for (WeldValue weld : config.welds.getAllTyped())
        {
            if (weld == null)
            {
                continue;
            }

            if (bone.equals(weld.sourceBone.get()))
            {
                addMirroredFace(out, weld.sourceFace.get());
            }

            if (bone.equals(weld.targetBone.get()))
            {
                addMirroredFace(out, weld.targetFace.get());
            }
        }

        return out;
    }

    private static void addMirroredFace(Set<String> out, String face)
    {
        if (face != null && !face.isEmpty())
        {
            out.add(mirrorFace(face));
        }
    }

    private static String mirrorFace(String face)
    {
        switch (face.toLowerCase())
        {
            case "front": return "back";
            case "back": return "front";
            case "right": return "left";
            case "left": return "right";
            case "top": return "bottom";
            case "bottom": return "top";
            default: return face.toLowerCase();
        }
    }

    /** Reusable per-thread emitter; avoids allocating vectors per vertex. */
    private static final class Emitter
    {
        private final VertexSink sink;
        private final Matrix4f matrix = new Matrix4f();
        private final Matrix3f normalMatrix = new Matrix3f();
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();
        private final float[] corners = new float[12];

        private Emitter(VertexSink sink)
        {
            this.sink = sink;
        }

        private void cube(Matrix4f parent, ModelCube cube, float width, float r, float g, float b, float a,
                          Set<String> welded, float[][] faceRects)
        {
            float off = 0.25F * width / 16F;
            float inflate = cube.inflate;
            float realMinX = (cube.origin.x - inflate) / 16F;
            float realMinY = (cube.origin.y - inflate) / 16F;
            float realMinZ = (cube.origin.z - inflate) / 16F;
            float realMaxX = (cube.origin.x + cube.size.x + inflate) / 16F;
            float realMaxY = (cube.origin.y + cube.size.y + inflate) / 16F;
            float realMaxZ = (cube.origin.z + cube.size.z + inflate) / 16F;

            float minX = Math.max(realMinX, realMaxX) + off;
            float minY = Math.max(realMinY, realMaxY) + off;
            float minZ = Math.max(realMinZ, realMaxZ) + off;
            float maxX = Math.min(realMinX, realMaxX) - off;
            float maxY = Math.min(realMinY, realMaxY) - off;
            float maxZ = Math.min(realMinZ, realMaxZ) - off;

            this.matrix.set(parent);

            Vector3f pivot = cube.pivot;

            this.matrix.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

            Vector3f rotate = cube.rotate;

            if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
            {
                this.matrix.rotateZ((float) Math.toRadians(rotate.z));
                this.matrix.rotateY((float) Math.toRadians(rotate.y));
                this.matrix.rotateX((float) Math.toRadians(rotate.x));
            }

            this.matrix.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
            this.normalMatrix.set(this.matrix).invert().transpose();

            if (!welded.contains("front") && cube.back != null)
            {
                this.quad(faceRects, FACE_FRONT, 0F, 0F, -1F, r, g, b, a,
                    maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ);
            }

            if (!welded.contains("right") && cube.left != null)
            {
                this.quad(faceRects, FACE_RIGHT, 1F, 0F, 0F, r, g, b, a,
                    maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ);
            }

            if (!welded.contains("back") && cube.front != null)
            {
                this.quad(faceRects, FACE_BACK, 0F, 0F, 1F, r, g, b, a,
                    minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
            }

            if (!welded.contains("left") && cube.right != null)
            {
                this.quad(faceRects, FACE_LEFT, -1F, 0F, 0F, r, g, b, a,
                    minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
            }

            if (!welded.contains("top") && cube.bottom != null)
            {
                this.quad(faceRects, FACE_TOP, 0F, 1F, 0F, r, g, b, a,
                    maxX, maxY, minZ, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ);
            }

            if (!welded.contains("bottom") && cube.top != null)
            {
                this.quad(faceRects, FACE_BOTTOM, 0F, -1F, 0F, r, g, b, a,
                    minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
            }
        }

        private void quad(float[][] faceRects, int face, float nx, float ny, float nz, float r, float g, float b, float a,
                          float x0, float y0, float z0, float x1, float y1, float z1,
                          float x2, float y2, float z2, float x3, float y3, float z3)
        {
            float[] rects = faceRects == null ? null : faceRects[face];

            if (rects != null && rects.length == 0)
            {
                return;
            }

            this.corners[0] = x0;
            this.corners[1] = y0;
            this.corners[2] = z0;
            this.corners[3] = x1;
            this.corners[4] = y1;
            this.corners[5] = z1;
            this.corners[6] = x2;
            this.corners[7] = y2;
            this.corners[8] = z2;
            this.corners[9] = x3;
            this.corners[10] = y3;
            this.corners[11] = z3;

            if (rects == null)
            {
                this.vertex(x0, y0, z0, nx, ny, nz, r, g, b, a);
                this.vertex(x1, y1, z1, nx, ny, nz, r, g, b, a);
                this.vertex(x2, y2, z2, nx, ny, nz, r, g, b, a);
                this.vertex(x0, y0, z0, nx, ny, nz, r, g, b, a);
                this.vertex(x2, y2, z2, nx, ny, nz, r, g, b, a);
                this.vertex(x3, y3, z3, nx, ny, nz, r, g, b, a);

                return;
            }

            for (int i = 0; i + 3 < rects.length; i += 4)
            {
                float s0 = rects[i];
                float s1 = rects[i + 1];
                float t0 = rects[i + 2];
                float t1 = rects[i + 3];
                float ax = this.bilinear(0, 3, 6, 9, s0, t0);
                float ay = this.bilinear(1, 4, 7, 10, s0, t0);
                float az = this.bilinear(2, 5, 8, 11, s0, t0);
                float bx = this.bilinear(0, 3, 6, 9, s1, t0);
                float by = this.bilinear(1, 4, 7, 10, s1, t0);
                float bz = this.bilinear(2, 5, 8, 11, s1, t0);
                float cx = this.bilinear(0, 3, 6, 9, s1, t1);
                float cy = this.bilinear(1, 4, 7, 10, s1, t1);
                float cz = this.bilinear(2, 5, 8, 11, s1, t1);
                float dx = this.bilinear(0, 3, 6, 9, s0, t1);
                float dy = this.bilinear(1, 4, 7, 10, s0, t1);
                float dz = this.bilinear(2, 5, 8, 11, s0, t1);

                this.vertex(ax, ay, az, nx, ny, nz, r, g, b, a);
                this.vertex(bx, by, bz, nx, ny, nz, r, g, b, a);
                this.vertex(cx, cy, cz, nx, ny, nz, r, g, b, a);
                this.vertex(ax, ay, az, nx, ny, nz, r, g, b, a);
                this.vertex(cx, cy, cz, nx, ny, nz, r, g, b, a);
                this.vertex(dx, dy, dz, nx, ny, nz, r, g, b, a);
            }
        }

        private float bilinear(int i0, int i1, int i2, int i3, float s, float t)
        {
            float w0 = (1F - s) * (1F - t);
            float w1 = s * (1F - t);
            float w2 = s * t;
            float w3 = (1F - s) * t;

            return this.corners[i0] * w0 + this.corners[i1] * w1 + this.corners[i2] * w2 + this.corners[i3] * w3;
        }

        private void vertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float a)
        {
            this.position.set(x, y, z);
            this.matrix.transformPosition(this.position);
            this.normal.set(nx, ny, nz);
            this.normalMatrix.transform(this.normal);

            if (this.normal.lengthSquared() > 1.0E-12F)
            {
                this.normal.normalize();
            }

            this.sink.vertex(this.position.x, this.position.y, this.position.z,
                this.normal.x, this.normal.y, this.normal.z, r, g, b, a);
        }
    }

    public static String faceName(int face)
    {
        return FACE_NAMES[face];
    }

    public static int cubeFace(int shellFace)
    {
        return SHELL_TO_CUBE_FACE[shellFace];
    }

    public static Collection<ModelCube> cubes(Model model)
    {
        List<ModelCube> out = new ArrayList<>();

        for (ModelGroup group : model.getAllGroups())
        {
            if (group != null && group.cubes != null)
            {
                out.addAll(group.cubes);
            }
        }

        return out;
    }
}
