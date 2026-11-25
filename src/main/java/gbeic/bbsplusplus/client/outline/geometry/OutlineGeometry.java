package gbeic.bbsplusplus.client.outline.geometry;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.GroupOutlineHolder;
import gbeic.bbsplusplus.api.OutlineGradient;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
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
    static final int[] SHELL_TO_CUBE_FACE = { FACE_BACK, FACE_LEFT, FACE_FRONT, FACE_RIGHT, FACE_BOTTOM, FACE_TOP };


    /**
     * In-plane axes of every cube face, as {@code {axis, sign}} with axis 0=X, 1=Y, 2=Z and the
     * sign pointing along the face's on-screen "right" / "up".
     *
     * <p>A per-face ramp cannot use model X/Y for all six faces, because a face's up and right flip
     * around the cube: the front face sees -X as its right, the back face sees +X, and so on. A
     * negative sign makes {@link #rampT} measure from the max side instead, so the ramp still starts
     * at the face's own top-left corner.</p>
     */
    static final int[][] FACE_H_AXIS =
    {
        { 0, -1 }, /* FRONT: -X */
        { 2, -1 }, /* RIGHT: -Z */
        { 0, 1 },  /* BACK: +X */
        { 2, 1 },  /* LEFT: +Z */
        { 0, 1 },  /* TOP: +X */
        { 0, -1 }, /* BOTTOM: -X */
    };

    static final int[][] FACE_V_AXIS =
    {
        { 1, 1 },  /* FRONT: +Y */
        { 1, 1 },  /* RIGHT: +Y */
        { 1, 1 },  /* BACK: +Y */
        { 1, 1 },  /* LEFT: +Y */
        { 2, -1 }, /* TOP: -Z */
        { 2, -1 }, /* BOTTOM: -Z */
    };

    /**
     * Rendered extent below which an axis counts as flat, in blocks (the same unit as the
     * {@code realMin}/{@code realMax} bounds, which are already divided by 16).
     *
     * <p>Blockbench writes zero-thickness planes and lines all the time, so this has to be an
     * epsilon rather than a plain {@code == 0F} check. The value is far below anything a model
     * can meaningfully author (0.0016 model pixels), so it only ever catches collapsed cubes.</p>
     */
    static final float MIN_EXTENT = 1.0E-4F;



    private OutlineGeometry()
    {
    }

    /**
     * One draw: a group, its outline colour/width/glow and the colour already split into floats.
     *
     * <p>{@code glow} is the LabPBR emission strength (0..254) and only reaches the shader through
     * the {@code _s} companion texture.</p>
     *
     * <p>{@code faceArgb} holds one ARGB entry per {@code FACE_*} index, or is {@code null} when the
     * whole shell uses {@code argb}. An entry of {@link BoneOutlineHolder#DEFAULT_FACE_COLOR} means
     * "follow {@code argb}" for that face.</p>
     *
     * <p>{@code layer} is 0 for the plain outline and 1 for the overlay ring. Both go into the same
     * buffers; the renderer draws the wider spec first so the two read as nested rings.</p>
     */
    public record Spec(ModelGroup group, int argb, float width, float glow,
        float r, float g, float b, float a, boolean faceCull, int[] faceArgb, int layer,
        OutlineGradient gradient, OutlineGradient[] faceGradients)
    {
        /** Whether any of the six faces carries its own ramp (the face table beats the layer ramp). */
        public boolean hasFaceRamp()
        {
            if (this.faceGradients == null)
            {
                return false;
            }

            for (OutlineGradient gradient : this.faceGradients)
            {
                if (gradient != null)
                {
                    return true;
                }
            }

            return false;
        }
    }

    /** Receives vertices in the frame supplied by the {@code base} matrix. */
    @FunctionalInterface
    public interface VertexSink
    {
        void vertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float a);

        /**
         * Same, plus the ramp coordinate. The LabPBR gradient pass binds a 64x1 ramp texture and
         * needs {@code u} to read it; every other sink keeps the plain overload and drops it.
         */
        default void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v,
                            float r, float g, float b, float a)
        {
            vertex(x, y, z, nx, ny, nz, r, g, b, a);
        }
    }

    /**
     * One emissive batch identity: either a solid ARGB or a whole ramp.
     *
     * <p>The LabPBR pass has to bind one diffuse texture per batch, so a gradient cannot be split
     * into its per-vertex colours the way the colour passes are. Instead the ramp itself is the
     * batch and the vertices carry a {@code u} coordinate.</p>
     */
    public record FacePaint(int argb, OutlineGradient gradient)
    {
        public static FacePaint solid(int argb)
        {
            return new FacePaint(argb, null);
        }

        public static FacePaint ramp(OutlineGradient gradient)
        {
            return new FacePaint(0, gradient);
        }

        public boolean isRamp()
        {
            return this.gradient != null;
        }

        public long id()
        {
            return this.gradient != null ? this.gradient.hash() : this.argb;
        }
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
                OutlineGradient gradient = holder.bbspp_cml$getOutlineGradient(layer);
                OutlineGradient[] faceGradients = null;

                /* Only materialise the per-face table when at least one face actually has a ramp,
                 * so the common solid-colour shell allocates nothing extra. */
                if (holder.bbspp_cml$hasOutlineGradients(layer))
                {
                    faceGradients = new OutlineGradient[BoneOutlineHolder.FACE_COUNT];

                    for (int face = 0; face < BoneOutlineHolder.FACE_COUNT; face++)
                    {
                        faceGradients[face] = holder.bbspp_cml$getOutlineFaceGradient(layer, face);
                    }
                }

                specs.add(new Spec(group, argb, width, holder.bbspp_cml$getOutlineGlow(layer),
                    channel(argb, 16), channel(argb, 8), channel(argb, 0), channel(argb, 24),
                    holder.bbspp_cml$getOutlineFaceCull(layer), faces, layer, gradient, faceGradients));
            }
        }

        return specs;
    }

    /**
     * True when at least one visible, non-empty bone has an active ramped layer. Only those
     * layers depend on the camera's screen axes and need {@link OutlineScreenFrame#updateScreenFrame}.
     */
    public static boolean hasAnyOutlineGradient(Model model)
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
                if (holder.bbspp_cml$hasOutlineLayer(layer)
                    && holder.bbspp_cml$getOutlineShellWidth(layer) > 0F
                    && holder.bbspp_cml$hasOutlineGradients(layer))
                {
                    return true;
                }
            }
        }

        return false;
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

    static float channel(int argb, int shift)
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
     * Every distinct paint the faces of one spec resolve to: this face's ramp, this face's colour,
     * or the layer's ramp / colour for the "follow the layer" sentinel. The emission pass needs this
     * because it draws one batch per paint so each face gets the matching emissive texture. Built in
     * face order so the batches keep a stable draw order between frames.
     */
    public static List<FacePaint> effectiveFacePaints(Spec spec)
    {
        int[] faces = spec.faceArgb();
        OutlineGradient[] ramps = spec.faceGradients();

        if (faces == null && ramps == null)
        {
            return Collections.singletonList(paintOf(spec.gradient(), spec.argb()));
        }

        List<FacePaint> paints = new ArrayList<>(2);

        for (int face = 0; face < 6; face++)
        {
            FacePaint paint;

            if (ramps != null && ramps[face] != null)
            {
                paint = FacePaint.ramp(ramps[face]);
            }
            else
            {
                /* A face the user never touched keeps following the layer, so it belongs to the
                 * layer's batch rather than to a batch of its own. */
                int argb = faces == null ? BoneOutlineHolder.DEFAULT_FACE_COLOR : faces[face];
                boolean own = argb != BoneOutlineHolder.DEFAULT_FACE_COLOR;

                paint = own ? FacePaint.solid(argb) : paintOf(spec.gradient(), spec.argb());
            }

            if (!paints.contains(paint))
            {
                paints.add(paint);
            }
        }

        if (paints.isEmpty())
        {
            paints.add(paintOf(spec.gradient(), spec.argb()));
        }

        return paints;
    }

    private static FacePaint paintOf(OutlineGradient gradient, int argb)
    {
        return gradient == null ? FacePaint.solid(argb) : FacePaint.ramp(gradient);
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
            null);
    }

    /**
     * Emits the shell, optionally restricted to the faces that resolve to one paint.
     *
     * <p>{@code faceFilter} is used by the emission passes, which draw one batch per paint so a
     * per-face colour or a ramp can be painted with the matching emissive texture. Passing
     * {@code null} emits every face, which is what the plain shell wants.</p>
     */
    public static void emit(Model model, ModelConfig config, List<Spec> specs, Matrix4f base, float formAlpha,
                            VertexSink sink, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decalCubes,
                            Predicate<String> materialVisible, boolean needsNormals, FacePaint faceFilter)
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

        OutlineEmitter emitter = new OutlineEmitter(sink, needsNormals, faceFilter);
        Set<ModelCube> skip = decalCubes == null ? Collections.emptySet() : decalCubes;
        Map<String, Set<String>> welds = OutlineWeldTable.weldTable(config);
        float[] bounds = needsBounds(specs) ? computeBounds(specs, matrices) : null;

        for (Spec spec : specs)
        {
            Matrix4f groupMatrix = matrices.get(spec.group());

            if (groupMatrix == null)
            {
                continue;
            }

            Set<String> welded = welds.getOrDefault(spec.group().id, Collections.emptySet());
            float alpha = spec.a() * formAlpha;

            if (alpha <= 0F && !hasVisibleFace(spec.faceArgb()) && !hasVisibleRamp(spec))
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
                    faceRects == null ? null : faceRects.get(cube), spec.faceCull(), spec.faceArgb(), spec.argb(),
                    spec.gradient(), spec.faceGradients(), bounds);
            }
        }
    }

    /** Whether any spec carries a ramp, i.e. whether the model bounds are worth computing. */
    private static boolean needsBounds(List<Spec> specs)
    {
        for (Spec spec : specs)
        {
            if (spec.gradient() != null || spec.hasFaceRamp())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Model-root AABB of everything the shell covers, used to normalise the gradient parameter.
     *
     * <p>{@code t} has to be a property of the model, not of one cube: a bone made of three cubes
     * must show one continuous ramp across all of them. The bounds therefore come from the whole
     * spec list, measured in the same frame the sink receives (group matrices applied, the caller's
     * {@code base} not). Authoring the ramp in model space also keeps it stable while the model
     * animates: rotating a bone moves its geometry through the ramp instead of dragging it along.</p>
     */
    private static float[] computeBounds(List<Spec> specs, Map<ModelGroup, Matrix4f> matrices)
    {
        float[] bounds = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        Matrix4f local = new Matrix4f();
        Vector3f corner = new Vector3f();
        Set<ModelCube> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Spec spec : specs)
        {
            Matrix4f groupMatrix = matrices.get(spec.group());

            if (groupMatrix == null)
            {
                continue;
            }

            for (ModelCube cube : spec.group().cubes)
            {
                if (cube == null || cube.origin == null || cube.size == null || !seen.add(cube))
                {
                    continue;
                }

                cubeMatrix(local, groupMatrix, cube);
                float inflate = cube.inflate;

                for (int i = 0; i < 8; i++)
                {
                    corner.set(
                        ((i & 1) == 0 ? cube.origin.x - inflate : cube.origin.x + cube.size.x + inflate) / 16F,
                        ((i & 2) == 0 ? cube.origin.y - inflate : cube.origin.y + cube.size.y + inflate) / 16F,
                        ((i & 4) == 0 ? cube.origin.z - inflate : cube.origin.z + cube.size.z + inflate) / 16F);
                    local.transformPosition(corner);

                    bounds[0] = Math.min(bounds[0], corner.x);
                    bounds[1] = Math.min(bounds[1], corner.y);
                    bounds[2] = Math.min(bounds[2], corner.z);
                    bounds[3] = Math.max(bounds[3], corner.x);
                    bounds[4] = Math.max(bounds[4], corner.y);
                    bounds[5] = Math.max(bounds[5], corner.z);
                }
            }
        }

        if (bounds[0] > bounds[3])
        {
            /* No geometry at all: a collapsed box keeps every parameter at 0.5. */
            return new float[] { 0F, 0F, 0F, 0F, 0F, 0F };
        }

        return bounds;
    }

    /** Group matrix composed with the cube's own pivot rotation, matching what {@code cube()} uses. */
    private static void cubeMatrix(Matrix4f out, Matrix4f parent, ModelCube cube)
    {
        out.set(parent);

        Vector3f pivot = cube.pivot;

        out.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        Vector3f rotate = cube.rotate;

        if (rotate != null && (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F))
        {
            out.rotateZ((float) Math.toRadians(rotate.z));
            out.rotateY((float) Math.toRadians(rotate.y));
            out.rotateX((float) Math.toRadians(rotate.x));
        }

        out.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    /** Whether any face of the spec resolves to a ramp, which keeps a transparent layer alive. */
    private static boolean hasVisibleRamp(Spec spec)
    {
        if (spec.gradient() != null)
        {
            return true;
        }

        OutlineGradient[] faces = spec.faceGradients();

        if (faces == null)
        {
            return false;
        }

        for (OutlineGradient gradient : faces)
        {
            if (gradient != null)
            {
                return true;
            }
        }

        return false;
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


    /** Reusable per-thread emitter; avoids allocating vectors per vertex. */

    public static int cubeFace(int shellFace)
    {
        return SHELL_TO_CUBE_FACE[shellFace];
    }
}
