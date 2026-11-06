package gbeic.bbsplusplus.client.outline.geometry;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.GroupOutlineHolder;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.WeldValue;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 * vertices to a {@link VertexSink}, expressed in the frame supplied by the {@code base} matrix.
 * It deliberately knows nothing about {@code BufferBuilder}, render pipelines or shaders: 1.20.x
 * and 1.21.11 each provide their own sink. That keeps the geometry, the bone maths and the
 * rim/weld/alpha rules identical on both Minecraft versions.</p>
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

    /** Passed to {@link #emit} as the face filter to mean "do not filter, draw every face". */
    public static final int FACE_FILTER_ALL = -1;

    /**
     * Rendered extent below which an axis counts as flat, in blocks (the same unit as the
     * {@code realMin}/{@code realMax} bounds, which are already divided by 16).
     *
     * <p>Blockbench writes zero-thickness planes and lines all the time, so this has to be an
     * epsilon rather than a plain {@code == 0F} check. The value is far below anything a model
     * can meaningfully author (0.0016 model pixels), so it only ever catches collapsed cubes.</p>
     */
    private static final float MIN_EXTENT = 1.0E-4F;

    /** Cached {@code bone -> mirrored weld faces} tables, keyed by config identity. */
    private static final Map<ModelConfig, CachedWelds> WELD_CACHE = new IdentityHashMap<>();
    private static final int MAX_WELD_CACHE = 64;
    private static final Map<String, Set<String>> NO_WELDS = Collections.emptyMap();

    private record CachedWelds(long signature, Map<String, Set<String>> byBone)
    {
    }

    private OutlineGeometry()
    {
    }

    /**
     * One draw: a group, its outline colour/width/glow and the colour already split into floats.
     *
     * <p>{@code glow} is the LabPBR emission strength (0..254) and only reaches the shader through
     * the {@code _s} companion texture. {@code vanillaGlow} is the strength of the plain full-bright
     * overlay (0 = off, 255 = full), which needs no shader pack.</p>
     *
     * <p>{@code faceArgb} holds one ARGB entry per {@code FACE_*} index, or is {@code null} when the
     * whole shell uses {@code argb}. An entry of {@link BoneOutlineHolder#DEFAULT_FACE_COLOR} means
     * "follow {@code argb}" for that face.</p>
     *
     * <p>{@code layer} is 0 for the plain outline and 1 for the overlay ring. Both go into the same
     * buffers; the renderer draws the wider spec first so the two read as nested rings.</p>
     */
    public record Spec(ModelGroup group, int argb, float width, float glow, float vanillaGlow,
        float r, float g, float b, float a, boolean faceCull, int[] faceArgb, int layer)
    {
    }

    /** Receives vertices in the frame supplied by the {@code base} matrix. */
    @FunctionalInterface
    public interface VertexSink
    {
        void vertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float a);
    }

    /**
     * Collects the visible groups that have at least one active outline layer.
     *
     * <p>Outline settings live on the bone's {@code PoseTransform} (see {@link GroupOutlineHolder})
     * and are mirrored onto {@link ModelGroup} whenever a pose is applied. There is no model-wide
     * toggle: every bone decides for itself. A bone with both layers on yields two specs sharing
     * one group.</p>
     */
    public static List<Spec> collect(Model model)
    {
        if (model == null)
        {
            return Collections.emptyList();
        }

        List<Spec> specs = new ArrayList<>();

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null || !group.isVisible() || group.cubes == null || group.cubes.isEmpty())
            {
                continue;
            }

            GroupOutlineHolder holder = (GroupOutlineHolder) group;

            for (int layer = 0; layer < 2; layer++)
            {
                if (!holder.bbspp_cml$hasOutlineLayer(layer))
                {
                    continue;
                }

                float width = holder.bbspp_cml$getOutlineShellWidth(layer);

                if (width <= 0F)
                {
                    continue;
                }

                int argb = holder.bbspp_cml$getOutlineColor(layer).getARGBColor();
                int[] faces = holder.bbspp_cml$hasOutlineFaceColors(layer)
                    ? holder.bbspp_cml$getOutlineFaceColors(layer) : null;

                specs.add(new Spec(group, argb, width, holder.bbspp_cml$getOutlineGlow(layer),
                    holder.bbspp_cml$getOutlineVanillaGlow(layer),
                    channel(argb, 16), channel(argb, 8), channel(argb, 0), channel(argb, 24),
                    holder.bbspp_cml$getOutlineFaceCull(layer), faces, layer));
            }
        }

        return specs;
    }

    /** Cheap pre-check so a model without any outlined bone does not build an empty entry. */
    public static boolean hasAnyOutline(Model model)
    {
        if (model == null)
        {
            return false;
        }

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null || !group.isVisible() || group.cubes == null || group.cubes.isEmpty())
            {
                continue;
            }

            GroupOutlineHolder holder = (GroupOutlineHolder) group;

            for (int layer = 0; layer < 2; layer++)
            {
                if (holder.bbspp_cml$hasOutlineLayer(layer) && holder.bbspp_cml$getOutlineShellWidth(layer) > 0F)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Bone -> detail threshold used by {@link OutlineDecalFilter}. Only groups with an active
     * outline contribute; the filter still keeps their small cubes when a bigger cube hosts them.
     */
    public static Map<String, Float> decalSizes(Model model)
    {
        Map<String, Float> sizes = new LinkedHashMap<>();

        if (model == null)
        {
            return sizes;
        }

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null || group.id == null || group.cubes == null || group.cubes.isEmpty())
            {
                continue;
            }

            GroupOutlineHolder holder = (GroupOutlineHolder) group;

            /* One threshold per model, but either layer being active is enough: a bone running
             * only the overlay layer needs its decals filtered too. */
            boolean active = holder.bbspp_cml$getOutlineDecalSize() > 0F
                && ((holder.bbspp_cml$hasOutlineLayer(0) && holder.bbspp_cml$getOutlineShellWidth(0) > 0F)
                    || (holder.bbspp_cml$hasOutlineLayer(1) && holder.bbspp_cml$getOutlineShellWidth(1) > 0F));

            if (active)
            {
                sizes.put(group.id, holder.bbspp_cml$getOutlineDecalSize());
            }
        }

        return sizes;
    }

    private static float channel(int argb, int shift)
    {
        return ((argb >> shift) & 0xFF) / 255F;
    }

    /** Whether any face of a table overrides the shell colour, i.e. the early-out must not fire. */
    private static boolean hasVisibleFace(int[] faceArgb)
    {
        if (faceArgb == null)
        {
            return false;
        }

        for (int argb : faceArgb)
        {
            if (argb != BoneOutlineHolder.DEFAULT_FACE_COLOR && (argb >>> 24) != 0)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Every distinct colour the faces of one spec resolve to (its own entry, or the layer colour
     * when the entry is the "follow the layer" sentinel). The glow pass needs this because it draws
     * one batch per colour so each face gets the matching emissive texture. Built in face order so
     * the batches keep a stable draw order between frames.
     */
    public static List<Integer> effectiveFaceColors(Spec spec)
    {
        int[] faces = spec.faceArgb();

        if (faces == null)
        {
            return Collections.singletonList(spec.argb());
        }

        List<Integer> colors = new ArrayList<>(2);

        for (int argb : faces)
        {
            /* A face the user never touched keeps following the layer colour, so it belongs to
             * the layer's batch rather than to a batch of its own. */
            int color = argb == BoneOutlineHolder.DEFAULT_FACE_COLOR ? spec.argb() : argb;

            if (!colors.contains(color))
            {
                colors.add(color);
            }
        }

        if (colors.isEmpty())
        {
            colors.add(spec.argb());
        }

        return colors;
    }

    /**
     * Emits the shell for every collected spec.
     *
     * @param base            the caller's base matrix (the stack at {@code ModelInstance.render} entry)
     * @param formAlpha       the form's own opacity, multiplied into every outline pixel
     * @param faceRects       per-cube alpha coverage rectangles from {@link OutlineAlphaMask}; null = no clipping
     * @param decalCubes      small cubes buried inside larger ones that should not get a rim
     * @param materialVisible hides cubes whose material is switched off in the material tab
     * @param needsNormals    the entity/LabPBR path reads normals; the {@code POSITION_COLOR}
     *                        passes have no normal attribute and pass false to skip the work
     */
    public static void emit(Model model, ModelConfig config, List<Spec> specs, Matrix4f base, float formAlpha,
                            VertexSink sink, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decalCubes,
                            Predicate<String> materialVisible, boolean needsNormals)
    {
        emit(model, config, specs, base, formAlpha, sink, faceRects, decalCubes, materialVisible, needsNormals,
            FACE_FILTER_ALL);
    }

    /**
     * Emits the shell, optionally restricted to the faces that resolve to one colour.
     *
     * <p>{@code faceFilter} is used by the emission passes, which draw one batch per colour so a
     * per-face colour can be painted with the matching emissive texture. Passing
     * {@link #FACE_FILTER_ALL} emits every face, which is what the plain shell wants.</p>
     */
    public static void emit(Model model, ModelConfig config, List<Spec> specs, Matrix4f base, float formAlpha,
                            VertexSink sink, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decalCubes,
                            Predicate<String> materialVisible, boolean needsNormals, int faceFilter)
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

        Emitter emitter = new Emitter(sink, needsNormals, faceFilter);
        Set<ModelCube> skip = decalCubes == null ? Collections.emptySet() : decalCubes;
        Map<String, Set<String>> welds = weldTable(config);

        for (Spec spec : specs)
        {
            Matrix4f groupMatrix = matrices.get(spec.group());

            if (groupMatrix == null)
            {
                continue;
            }

            Set<String> welded = welds.getOrDefault(spec.group().id, Collections.emptySet());
            float alpha = spec.a() * formAlpha;

            if (alpha <= 0F && !hasVisibleFace(spec.faceArgb()))
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

                emitter.cube(groupMatrix, cube, spec.width(), spec.r(), spec.g(), spec.b(), alpha, formAlpha, welded,
                    faceRects == null ? null : faceRects.get(cube), spec.faceCull(), spec.faceArgb(), spec.argb());
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

    /**
     * Builds (and caches) the whole bone-to-welded-faces table for one config.
     *
     * <p>The old implementation walked every weld once per spec and allocated a fresh
     * {@code HashSet} each time. This version walks the weld list once per config change and
     * reuses immutable sets.</p>
     */
    static Map<String, Set<String>> weldTable(ModelConfig config)
    {
        if (config == null || config.welds == null)
        {
            return NO_WELDS;
        }

        long signature = weldSignature(config);
        CachedWelds cached = WELD_CACHE.get(config);

        if (cached != null && cached.signature == signature)
        {
            return cached.byBone;
        }

        Map<String, Set<String>> byBone = new HashMap<>();

        for (WeldValue weld : config.welds.getAllTyped())
        {
            if (weld == null)
            {
                continue;
            }

            addWeldedFace(byBone, weld.sourceBone.get(), weld.sourceFace.get());
            addWeldedFace(byBone, weld.targetBone.get(), weld.targetFace.get());
        }

        for (Map.Entry<String, Set<String>> entry : byBone.entrySet())
        {
            entry.setValue(Collections.unmodifiableSet(entry.getValue()));
        }

        Map<String, Set<String>> result = Collections.unmodifiableMap(byBone);
        if (WELD_CACHE.size() >= MAX_WELD_CACHE)
        {
            WELD_CACHE.clear();
        }

        WELD_CACHE.put(config, new CachedWelds(signature, result));

        return result;
    }

    private static void addWeldedFace(Map<String, Set<String>> byBone, String bone, String face)
    {
        if (bone == null || bone.isEmpty() || face == null || face.isEmpty())
        {
            return;
        }

        byBone.computeIfAbsent(bone, key -> new HashSet<>()).add(mirrorFace(face));
    }

    private static long weldSignature(ModelConfig config)
    {
        long hash = 1125899906842597L;
        int count = 0;

        for (WeldValue weld : config.welds.getAllTyped())
        {
            count++;

            if (weld == null)
            {
                hash = hash * 31 + 1;
                continue;
            }

            hash = hash * 31 + stringHash(weld.sourceBone.get());
            hash = hash * 31 + stringHash(weld.sourceFace.get());
            hash = hash * 31 + stringHash(weld.targetBone.get());
            hash = hash * 31 + stringHash(weld.targetFace.get());
        }

        return hash * 31 + count;
    }

    private static int stringHash(String value)
    {
        return value == null ? 0 : value.hashCode();
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
        private final boolean needsNormals;
        private final Matrix4f matrix = new Matrix4f();
        private final Matrix3f normalMatrix = new Matrix3f();
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();
        private final float[] corners = new float[12];
        private boolean doubleSided;

        /* Colour of the face about to be emitted. cube() sets these right before each quad() call,
         * so one set of scratch fields is enough. */
        private float faceR;
        private float faceG;
        private float faceB;
        private float faceA;
        private int faceEffective;

        /* When not FACE_FILTER_ALL only the faces that resolved to this exact ARGB are emitted, so
         * one glow batch does not redraw every face of the shell. */
        private final int faceFilter;

        private Emitter(VertexSink sink, boolean needsNormals, int faceFilter)
        {
            this.sink = sink;
            this.needsNormals = needsNormals;
            this.faceFilter = faceFilter;
        }

        private boolean wantsFace()
        {
            return this.faceFilter == FACE_FILTER_ALL || this.faceFilter == this.faceEffective;
        }

        /**
         * Resolves the colour of one shell face. {@code faceArgb} is either {@code null} (the whole
         * shell uses the layer colour) or a table of six ARGB entries, where an entry equal to
         * {@link BoneOutlineHolder#DEFAULT_FACE_COLOR} keeps following the layer colour. The alpha
         * is multiplied by the form opacity here because each face may carry its own.
         */
        private void pickFace(int[] faceArgb, int layerArgb, int face, float r, float g, float b, float a,
                              float formAlpha)
        {
            this.faceR = r;
            this.faceG = g;
            this.faceB = b;
            this.faceA = a;
            this.faceEffective = layerArgb;

            if (faceArgb == null)
            {
                return;
            }

            int argb = faceArgb[face];

            if (argb == BoneOutlineHolder.DEFAULT_FACE_COLOR)
            {
                return;
            }

            this.faceEffective = argb;
            this.faceR = channel(argb, 16);
            this.faceG = channel(argb, 8);
            this.faceB = channel(argb, 0);
            this.faceA = channel(argb, 24) * formAlpha;
        }

        private void cube(Matrix4f parent, ModelCube cube, float width, float r, float g, float b, float a,
                          float formAlpha, Set<String> welded, float[][] faceRects, boolean faceCull,
                          int[] faceArgb, int layerArgb)
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

            /* A face only exists while both of its in-plane axes have some extent. Blockbench
             * happily writes 0×0×1 markers and other collapsed cubes; vanilla draws nothing for
             * them because the quad has no area, but the shell above inflates every axis by
             * {@code off}, which would turn each of those dead faces back into a one-pixel
             * sliver on screen. Measure the real (inflate-included) bounds, not the shell. */
            boolean flatX = Math.abs(realMaxX - realMinX) < MIN_EXTENT;
            boolean flatY = Math.abs(realMaxY - realMinY) < MIN_EXTENT;
            boolean flatZ = Math.abs(realMaxZ - realMinZ) < MIN_EXTENT;
            boolean hasXY = !flatX && !flatY;
            boolean hasYZ = !flatY && !flatZ;
            boolean hasXZ = !flatX && !flatZ;

            /* The shell is an inward-facing box, so culling it leaves only the far walls and the
             * body hides the rest - that is what turns a solid ring into a rim. A flat cube or a
             * decal has no thickness to hide anything, so with culling on it disappears at some
             * angles; those bones emit both windings instead (see {@link #quad}). */
            this.doubleSided = !faceCull;

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

            if (this.needsNormals)
            {
                this.normalMatrix.set(this.matrix).invert().transpose();
            }

            if (!welded.contains("front") && cube.back != null && hasXY)
            {
                this.pickFace(faceArgb, layerArgb, FACE_FRONT, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, FACE_FRONT, 0F, 0F, -1F, this.faceR, this.faceG, this.faceB, this.faceA,
                        maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ);
                }
            }

            if (!welded.contains("right") && cube.left != null && hasYZ)
            {
                this.pickFace(faceArgb, layerArgb, FACE_RIGHT, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, FACE_RIGHT, 1F, 0F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
                        maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ);
                }
            }

            if (!welded.contains("back") && cube.front != null && hasXY)
            {
                this.pickFace(faceArgb, layerArgb, FACE_BACK, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, FACE_BACK, 0F, 0F, 1F, this.faceR, this.faceG, this.faceB, this.faceA,
                        minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
                }
            }

            if (!welded.contains("left") && cube.right != null && hasYZ)
            {
                this.pickFace(faceArgb, layerArgb, FACE_LEFT, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, FACE_LEFT, -1F, 0F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
                        minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
                }
            }

            if (!welded.contains("top") && cube.bottom != null && hasXZ)
            {
                this.pickFace(faceArgb, layerArgb, FACE_TOP, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, FACE_TOP, 0F, 1F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
                        maxX, maxY, minZ, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ);
                }
            }

            if (!welded.contains("bottom") && cube.top != null && hasXZ)
            {
                this.pickFace(faceArgb, layerArgb, FACE_BOTTOM, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, FACE_BOTTOM, 0F, -1F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
                        minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
                }
            }
        }

        private void quad(float[][] faceRects, int face, float nx, float ny, float nz, float r, float g, float b, float a,
                          float x0, float y0, float z0, float x1, float y1, float z1,
                          float x2, float y2, float z2, float x3, float y3, float z3)
        {
            /* A face painted with a fully transparent colour is simply not drawn; without this
             * check it would still cost six vertices (twelve when double sided). */
            if (a <= 0F)
            {
                return;
            }

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

                if (this.doubleSided)
                {
                    this.vertex(x0, y0, z0, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(x2, y2, z2, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(x1, y1, z1, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(x0, y0, z0, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(x3, y3, z3, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(x2, y2, z2, -nx, -ny, -nz, r, g, b, a);
                }

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

                if (this.doubleSided)
                {
                    this.vertex(ax, ay, az, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(cx, cy, cz, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(bx, by, bz, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(ax, ay, az, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(dx, dy, dz, -nx, -ny, -nz, r, g, b, a);
                    this.vertex(cx, cy, cz, -nx, -ny, -nz, r, g, b, a);
                }
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

            if (this.needsNormals)
            {
                this.normal.set(nx, ny, nz);
                this.normalMatrix.transform(this.normal);

                if (this.normal.lengthSquared() > 1.0E-12F)
                {
                    this.normal.normalize();
                }

                this.sink.vertex(this.position.x, this.position.y, this.position.z,
                    this.normal.x, this.normal.y, this.normal.z, r, g, b, a);
            }
            else
            {
                this.sink.vertex(this.position.x, this.position.y, this.position.z, 0F, 0F, 0F, r, g, b, a);
            }
        }
    }

    public static int cubeFace(int shellFace)
    {
        return SHELL_TO_CUBE_FACE[shellFace];
    }
}
