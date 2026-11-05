package gbeic.bbsplusplus.client.outline.geometry;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Alpha-coverage clipping for the negative-volume shell.
 *
 * <p>The shell is pure geometry: any cube with origin/size grows a rim, regardless of its
 * texture. The model itself goes through {@code rendertype_entity_translucent}, whose fragment
 * shader discards alpha below 0.1, so unpainted texels neither write colour nor depth and the
 * shell leaks through them. This class reads the texture back on the CPU and builds, per cube
 * face, a list of textured rectangles that are actually covered.</p>
 *
 * <p>Each face's four UVs define a linear (s,t) -> (u,v) map. The class walks the UV bounding
 * box texel by texel, merges consecutive covered texels on each row into runs, maps every run
 * back to (s,t), and emits {@code (minS,maxS,minT,maxT)} quads. The shell renderer bilinearly
 * subdivides each face with those rectangles, so the rim follows the cutout exactly.</p>
 *
 * <p>Everything is cached by {@link Texture} identity and by cube identity. All texture
 * readback must happen before drawing starts: binding the model texture in the middle of the
 * glow pass would replace the 1x1 outline texture on unit 0.</p>
 */
public final class OutlineAlphaMask
{
    /** Matches {@code rendertype_entity_translucent}'s discard threshold. */
    private static final float ALPHA_THRESHOLD = 0.1F;
    /** Corner-to-quad-vertex position tolerance, in blocks (1 model pixel = 1/16). */
    private static final float EPS = 1.0E-3F;
    /** Maximum texels examined for one face before falling back to "no clipping". */
    private static final int MAX_TEXELS = 16 * 16 * 4;
    /** Maximum rectangles per face; beyond this the face falls back to "no clipping". */
    private static final int MAX_RECTS = 1024;
    /** Face entirely transparent: skip it. */
    private static final float[] EMPTY = new float[0];

    private static final Map<Texture, Pixels> PIXELS = new IdentityHashMap<>();
    private static final Set<Texture> FAILED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Texture> OPAQUE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ModelCube, CubeRects> MASKS = new IdentityHashMap<>();
    private static final float[] CORNERS = new float[12];

    /**
     * Bumped whenever the cached masks stop being valid (texture reload/deletion, model edits).
     * The outline shell cache folds this into its key so stale geometry is never reused.
     */
    private static long version;

    private OutlineAlphaMask()
    {
    }

    static long version()
    {
        return version;
    }

    /** Drops native pixel buffers and all cached masks. Call on resource reload and texture deletion. */
    public static void invalidate()
    {
        version++;

        for (Pixels pixels : PIXELS.values())
        {
            if (pixels != null)
            {
                pixels.delete();
            }
        }

        PIXELS.clear();
        FAILED.clear();
        OPAQUE.clear();
        MASKS.clear();
    }

    /** Drops only the cached per-cube masks (model geometry/UV edits). */
    public static void invalidateMasks()
    {
        version++;
        MASKS.clear();
    }

    /** Drops only the cached data belonging to one deleted texture. */
    public static void invalidate(Texture texture)
    {
        if (texture == null)
        {
            return;
        }

        version++;

        Pixels pixels = PIXELS.remove(texture);

        if (pixels != null)
        {
            pixels.delete();
        }

        FAILED.remove(texture);
        OPAQUE.remove(texture);
        MASKS.entrySet().removeIf(entry -> entry.getValue().textureId == texture.id);
    }

    /** Returns the texture pixels, or null when the texture is missing/opaque/unreadable (no clipping). */
    public static Pixels pixels(Texture texture)
    {
        if (texture == null || !texture.isValid() || texture.width <= 0 || texture.height <= 0)
        {
            return null;
        }

        if (FAILED.contains(texture) || OPAQUE.contains(texture))
        {
            return null;
        }

        Pixels cached = PIXELS.get(texture);

        if (cached != null)
        {
            return cached;
        }

        try
        {
            int savedActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int savedBinding = 0;

            try
            {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                savedBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

                try
                {
                    int[] packState = pushPackState();

                    try
                    {
                        cached = Texture.pixelsFromTexture(texture);
                    }
                    finally
                    {
                        popPackState(packState);
                    }
                }
                finally
                {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedBinding);
                }
            }
            finally
            {
                GL13.glActiveTexture(savedActive);
            }
        }
        catch (Throwable throwable)
        {
            cached = null;
        }

        if (cached == null || cached.width <= 0 || cached.height <= 0)
        {
            FAILED.add(texture);

            return null;
        }

        if (!hasTransparentTexel(cached))
        {
            OPAQUE.add(texture);
            cached.delete();

            return null;
        }

        PIXELS.put(texture, cached);

        return cached;
    }

    private static boolean hasTransparentTexel(Pixels pixels)
    {
        for (int y = 0; y < pixels.height; y++)
        {
            for (int x = 0; x < pixels.width; x++)
            {
                Color color = pixels.getColor(x, y);

                if (color != null && color.a < ALPHA_THRESHOLD)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** Precomputes all six coverage faces for every cube in these groups. */
    public static Map<ModelCube, float[][]> build(Pixels pixels, Texture texture, Collection<ModelGroup> groups)
    {
        if (pixels == null || texture == null || groups == null)
        {
            return null;
        }

        Map<ModelCube, float[][]> out = new IdentityHashMap<>();

        for (ModelGroup group : groups)
        {
            if (group == null || group.cubes == null)
            {
                continue;
            }

            for (ModelCube cube : group.cubes)
            {
                if (cube != null)
                {
                    out.put(cube, rects(pixels, texture, cube));
                }
            }
        }

        return out;
    }

    private static float[][] rects(Pixels pixels, Texture texture, ModelCube cube)
    {
        CubeRects cached = MASKS.get(cube);

        if (cached != null && cached.textureId == texture.id)
        {
            return cached.faces;
        }

        float[][] faces = new float[6][];

        for (int face = 0; face < 6; face++)
        {
            faces[face] = faceRects(pixels, cube, face);
        }

        MASKS.put(cube, new CubeRects(texture.id, faces));

        return faces;
    }

    private static float[] faceRects(Pixels pixels, ModelCube cube, int face)
    {
        if (cube.origin == null || cube.size == null)
        {
            return null;
        }

        float inflate = cube.inflate;
        float ax = (cube.origin.x - inflate) / 16F;
        float ay = (cube.origin.y - inflate) / 16F;
        float az = (cube.origin.z - inflate) / 16F;
        float bx = (cube.origin.x + cube.size.x + inflate) / 16F;
        float by = (cube.origin.y + cube.size.y + inflate) / 16F;
        float bz = (cube.origin.z + cube.size.z + inflate) / 16F;
        float loX = Math.min(ax, bx);
        float hiX = Math.max(ax, bx);
        float loY = Math.min(ay, by);
        float hiY = Math.max(ay, by);
        float loZ = Math.min(az, bz);
        float hiZ = Math.max(az, bz);

        fillCorners(face, loX, hiX, loY, hiY, loZ, hiZ);

        ModelQuad quad = quadForFace(cube, face);

        if (quad == null || quad.vertices == null || quad.vertices.size() < 4)
        {
            return null;
        }

        boolean[] used = new boolean[quad.vertices.size()];
        float[] uvs = new float[8];

        for (int corner = 0; corner < 4; corner++)
        {
            int index = matchVertex(quad, CORNERS[corner * 3], CORNERS[corner * 3 + 1], CORNERS[corner * 3 + 2], used);

            if (index < 0)
            {
                return null;
            }

            ModelVertex vertex = quad.vertices.get(index);

            if (vertex == null || vertex.uv == null)
            {
                return null;
            }

            used[index] = true;
            uvs[corner * 2] = vertex.uv.x;
            uvs[corner * 2 + 1] = vertex.uv.y;
        }

        float u0 = uvs[0];
        float v0 = uvs[1];
        float u1 = uvs[2];
        float v1 = uvs[3];
        float u3 = uvs[6];
        float v3 = uvs[7];
        float esu = u1 - u0;
        float esv = v1 - v0;
        float etu = u3 - u0;
        float etv = v3 - v0;
        float det = esu * etv - esv * etu;

        if (Math.abs(det) < 1.0E-9F)
        {
            return null;
        }

        float umin = Math.min(Math.min(u0, u1), Math.min(uvs[4], u3));
        float umax = Math.max(Math.max(u0, u1), Math.max(uvs[4], u3));
        float vmin = Math.min(Math.min(v0, v1), Math.min(uvs[5], v3));
        float vmax = Math.max(Math.max(v0, v1), Math.max(uvs[5], v3));

        int width = pixels.width;
        int height = pixels.height;
        int x0 = (int) Math.floor(umin * width + 1.0E-4F);
        int x1 = (int) Math.ceil(umax * width - 1.0E-4F) - 1;
        int y0 = (int) Math.floor(vmin * height + 1.0E-4F);
        int y1 = (int) Math.ceil(vmax * height - 1.0E-4F) - 1;

        if (x1 < x0 || y1 < y0)
        {
            return EMPTY;
        }

        long count = (long) (x1 - x0 + 1) * (y1 - y0 + 1);

        if (count <= 0L || count > MAX_TEXELS)
        {
            return null;
        }

        float[] out = new float[16];
        int length = 0;

        for (int y = y0; y <= y1; y++)
        {
            int runStart = -1;

            for (int x = x0; x <= x1 + 1; x++)
            {
                boolean opaque = x <= x1 && isOpaque(pixels, x, y);

                if (opaque && runStart < 0)
                {
                    runStart = x;
                }
                else if (!opaque && runStart >= 0)
                {
                    if (length + 4 > out.length)
                    {
                        float[] bigger = new float[out.length * 2];

                        System.arraycopy(out, 0, bigger, 0, length);
                        out = bigger;
                    }

                    float ua = runStart / (float) width;
                    float ub = x / (float) width;
                    float va = y / (float) height;
                    float vb = (y + 1) / (float) height;
                    float minS = 1F;
                    float maxS = 0F;
                    float minT = 1F;
                    float maxT = 0F;
                    float[] uvCorners = { ua, va, ub, va, ub, vb, ua, vb };

                    for (int i = 0; i < 8; i += 2)
                    {
                        float s = ((uvCorners[i] - u0) * etv - (uvCorners[i + 1] - v0) * etu) / det;
                        float t = (esu * (uvCorners[i + 1] - v0) - esv * (uvCorners[i] - u0)) / det;

                        minS = Math.min(minS, s);
                        maxS = Math.max(maxS, s);
                        minT = Math.min(minT, t);
                        maxT = Math.max(maxT, t);
                    }

                    minS = Math.max(0F, minS);
                    maxS = Math.min(1F, maxS);
                    minT = Math.max(0F, minT);
                    maxT = Math.min(1F, maxT);

                    if (maxS - minS > 1.0E-5F && maxT - minT > 1.0E-5F)
                    {
                        if ((length / 4) >= MAX_RECTS)
                        {
                            return null;
                        }

                        out[length++] = minS;
                        out[length++] = maxS;
                        out[length++] = minT;
                        out[length++] = maxT;
                    }

                    runStart = -1;
                }
            }
        }

        if (length == 0)
        {
            return EMPTY;
        }

        float[] result = new float[length];

        System.arraycopy(out, 0, result, 0, length);

        return result;
    }

    private static boolean isOpaque(Pixels pixels, int x, int y)
    {
        int tx = Math.floorMod(x, pixels.width);
        int ty = Math.floorMod(y, pixels.height);
        Color color = pixels.getColor(tx, ty);

        return color != null && color.a >= ALPHA_THRESHOLD;
    }

    private static ModelQuad quadForFace(ModelCube cube, int face)
    {
        if (cube.quads == null || cube.quads.isEmpty())
        {
            return null;
        }

        int target = OutlineGeometry.cubeFace(face);
        int index = 0;
        Object[] uvOrder = { cube.front, cube.right, cube.back, cube.left, cube.top, cube.bottom };

        for (int i = 0; i < 6; i++)
        {
            if (uvOrder[i] == null)
            {
                continue;
            }

            if (i == target)
            {
                if (index >= 0 && index < cube.quads.size())
                {
                    ModelQuad candidate = cube.quads.get(index);

                    if (matchesCorners(candidate))
                    {
                        return candidate;
                    }
                }

                break;
            }

            index++;
        }

        return quadWithCorners(cube);
    }

    private static boolean matchesCorners(ModelQuad quad)
    {
        if (quad == null || quad.vertices == null || quad.vertices.size() < 4)
        {
            return false;
        }

        boolean[] used = new boolean[quad.vertices.size()];

        for (int corner = 0; corner < 4; corner++)
        {
            int index = matchVertex(quad, CORNERS[corner * 3], CORNERS[corner * 3 + 1], CORNERS[corner * 3 + 2], used);

            if (index < 0)
            {
                return false;
            }

            used[index] = true;
        }

        return true;
    }

    private static ModelQuad quadWithCorners(ModelCube cube)
    {
        if (cube.quads == null)
        {
            return null;
        }

        for (ModelQuad quad : cube.quads)
        {
            if (matchesCorners(quad))
            {
                return quad;
            }
        }

        return null;
    }

    private static int matchVertex(ModelQuad quad, float x, float y, float z, boolean[] used)
    {
        for (int index = 0; index < quad.vertices.size(); index++)
        {
            if (used[index])
            {
                continue;
            }

            ModelVertex vertex = quad.vertices.get(index);

            if (vertex == null || vertex.vertex == null)
            {
                continue;
            }

            if (Math.abs(vertex.vertex.x - x) < EPS && Math.abs(vertex.vertex.y - y) < EPS && Math.abs(vertex.vertex.z - z) < EPS)
            {
                return index;
            }
        }

        return -1;
    }

    private static void fillCorners(int face, float loX, float hiX, float loY, float hiY, float loZ, float hiZ)
    {
        switch (face)
        {
            case 0: setCorners(loX, hiY, hiZ, hiX, hiY, hiZ, hiX, loY, hiZ, loX, loY, hiZ); break;
            case 1: setCorners(loX, hiY, loZ, loX, hiY, hiZ, loX, loY, hiZ, loX, loY, loZ); break;
            case 2: setCorners(hiX, hiY, loZ, loX, hiY, loZ, loX, loY, loZ, hiX, loY, loZ); break;
            case 3: setCorners(hiX, hiY, hiZ, hiX, hiY, loZ, hiX, loY, loZ, hiX, loY, hiZ); break;
            case 4: setCorners(loX, loY, hiZ, hiX, loY, hiZ, hiX, loY, loZ, loX, loY, loZ); break;
            default: setCorners(hiX, hiY, hiZ, loX, hiY, hiZ, loX, hiY, loZ, hiX, hiY, loZ); break;
        }
    }

    private static void setCorners(float x0, float y0, float z0, float x1, float y1, float z1,
                                   float x2, float y2, float z2, float x3, float y3, float z3)
    {
        CORNERS[0] = x0;
        CORNERS[1] = y0;
        CORNERS[2] = z0;
        CORNERS[3] = x1;
        CORNERS[4] = y1;
        CORNERS[5] = z1;
        CORNERS[6] = x2;
        CORNERS[7] = y2;
        CORNERS[8] = z2;
        CORNERS[9] = x3;
        CORNERS[10] = y3;
        CORNERS[11] = z3;
    }

    /**
     * 1.21.11 hazard: vanilla's GlCommandEncoder.copyTextureToBuffer leaves GL_PACK_ROW_LENGTH set to
     * the width of whatever texture it downloaded (and a PIXEL_PACK_BUFFER may still be bound). A
     * later glGetTexImage whose destination buffer is sized {@code width * height * 4} then gets
     * {@code rowLength} pixels written per row - straight past the end of the buffer. The driver dies
     * with EXCEPTION_ACCESS_VIOLATION, which no Java catch block can intercept.
     *
     * <p>Upstream solves this with {@code mchorse.bbs_mod.graphics.PixelPackState}; that class only
     * exists in the 1.21.11 line, so this shared file keeps its own tiny copy (raw GL, no BBS type).
     * Restoring the previous state afterwards keeps vanilla's bookkeeping intact.</p>
     */
    private static int[] pushPackState()
    {
        int[] saved = new int[] {
            GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),
            GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS),
            GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),
            GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),
            GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)
        };

        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

        return saved;
    }

    private static void popPackState(int[] saved)
    {
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, saved[0]);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, saved[1]);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, saved[2]);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, saved[3]);
        GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, saved[4]);
    }

    private record CubeRects(int textureId, float[][] faces)
    {
    }
}
