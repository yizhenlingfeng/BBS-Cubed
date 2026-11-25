package gbeic.bbsplusplus.client.outline.geometry;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.OutlineGradient;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Set;

/** Emits one shell cube as triangles into an OutlineGeometry.VertexSink (D-25). */
    final class OutlineEmitter
    {
        private final OutlineGeometry.VertexSink sink;
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

        /* Ramp of the face about to be emitted, if any. When it is set, vertex() samples it instead
         * of using the flat face colour, and the sampled u doubles as the LabPBR texture coordinate. */
        private OutlineGradient faceRamp;
        private boolean faceAxisRamp;
        private int faceHAxis;
        private int faceVAxis;
        private float faceHSign;
        private float faceVSign;

        /* Index (0..5) of the face being emitted. The layer ramp uses it to notice that a face lies
         * across the ramp axis and therefore has no extent to measure along it. */
        private int faceIndex;
        private final float[] rampScratch = new float[4];
        private float[] rampBounds;

        /* Screen-aligned ramp frame captured when this emitter was created, plus the bounds
         * projected onto its two axes (screen up / screen right). */
        private final float[] rampFrame = OutlineScreenFrame.screenFrame();
        private float rampUpLo;
        private float rampUpHi;
        private float rampRightLo;
        private float rampRightHi;

        /* Form opacity, needed by vertex() to scale a sampled ramp alpha. */
        private float formAlpha = 1F;

        /* When not null only the faces that resolve to this paint are emitted, so one glow batch
         * does not redraw every face of the shell. */
        private final OutlineGeometry.FacePaint faceFilter;

        OutlineEmitter(OutlineGeometry.VertexSink sink, boolean needsNormals, OutlineGeometry.FacePaint faceFilter)
        {
            this.sink = sink;
            this.needsNormals = needsNormals;
            this.faceFilter = faceFilter;
        }

        private boolean wantsFace()
        {
            if (this.faceFilter == null)
            {
                return true;
            }

            if (this.faceFilter.isRamp())
            {
                /* A whole ramp is one batch: every face painted with that same ramp belongs to it. */
                return this.faceRamp != null && this.faceRamp.hash() == this.faceFilter.gradient().hash();
            }

            /* A solid batch never paints a face that has its own ramp - the ramp wins over the
             * layer colour, and drawing both would double-blend the rim. */
            return this.faceRamp == null && this.faceFilter.argb() == this.faceEffective;
        }

        /**
         * Resolves the colour of one shell face. {@code faceArgb} is either {@code null} (the whole
         * shell uses the layer colour) or a table of six ARGB entries, where an entry equal to
         * {@link BoneOutlineHolder#DEFAULT_FACE_COLOR} keeps following the layer colour. The alpha
         * is multiplied by the form opacity here because each face may carry its own.
         *
         * <p>Precedence, highest first: this face's ramp, this face's solid colour, the layer's ramp,
         * the layer's solid colour. A face ramp therefore keeps painting over a face colour, and a
         * face colour stops the layer ramp on that face.</p>
         */
        private void pickFace(int[] faceArgb, OutlineGradient[] faceGradients, int layerArgb,
                              OutlineGradient layerGradient, int face, float r, float g, float b, float a,
                              float formAlpha)
        {
            this.faceR = r;
            this.faceG = g;
            this.faceB = b;
            this.faceA = a;
            this.faceEffective = layerArgb;
            this.faceRamp = null;
            this.faceAxisRamp = false;
            this.faceIndex = face;

            OutlineGradient ownRamp = faceGradients == null ? null : faceGradients[face];
            int argb = faceArgb == null ? BoneOutlineHolder.DEFAULT_FACE_COLOR : faceArgb[face];
            boolean hasOwnSolid = argb != BoneOutlineHolder.DEFAULT_FACE_COLOR;

            if (hasOwnSolid)
            {
                this.faceEffective = argb;
                this.faceR = OutlineGeometry.channel(argb, 16);
                this.faceG = OutlineGeometry.channel(argb, 8);
                this.faceB = OutlineGeometry.channel(argb, 0);
                this.faceA = OutlineGeometry.channel(argb, 24) * formAlpha;
            }

            if (ownRamp != null)
            {
                this.faceRamp = ownRamp;
                this.faceAxisRamp = true;

                int[] h = OutlineGeometry.FACE_H_AXIS[face];
                int[] v = OutlineGeometry.FACE_V_AXIS[face];

                this.faceHAxis = h[0];
                this.faceHSign = h[1];
                this.faceVAxis = v[0];
                this.faceVSign = v[1];
            }
            else if (!hasOwnSolid && layerGradient != null)
            {
                this.faceRamp = layerGradient;
            }

            if (this.faceRamp != null)
            {
                /* quad() early-outs on a transparent face; a ramp only counts as transparent when
                 * every stop is, so one opaque stop keeps the face alive. */
                this.faceA = maxAlpha(this.faceRamp) * formAlpha;
            }
        }

        private static float maxAlpha(OutlineGradient gradient)
        {
            float alpha = 0F;

            for (OutlineGradient.Stop stop : gradient.getStops())
            {
                alpha = Math.max(alpha, OutlineGeometry.channel(stop.argb, 24));
            }

            return alpha;
        }

        /** Projects the model bounds onto the screen axes so {@link #rampT} only has to dot. */
        private void projectBounds(float[] bounds)
        {
            float[] frame = this.rampFrame;
            float upLo = Float.MAX_VALUE;
            float upHi = -Float.MAX_VALUE;
            float rightLo = Float.MAX_VALUE;
            float rightHi = -Float.MAX_VALUE;

            for (int corner = 0; corner < 8; corner++)
            {
                float x = (corner & 1) == 0 ? bounds[0] : bounds[3];
                float y = (corner & 2) == 0 ? bounds[1] : bounds[4];
                float z = (corner & 4) == 0 ? bounds[2] : bounds[5];
                float up = x * frame[0] + y * frame[1] + z * frame[2];
                float right = x * frame[3] + y * frame[4] + z * frame[5];

                upLo = Math.min(upLo, up);
                upHi = Math.max(upHi, up);
                rightLo = Math.min(rightLo, right);
                rightHi = Math.max(rightHi, right);
            }

            this.rampUpLo = upLo;
            this.rampUpHi = upHi;
            this.rampRightLo = rightLo;
            this.rampRightHi = rightHi;
        }

        /** Position of {@code t} along the ramp, from the model-root AABB the shell covers. */
        private float rampT(float x, float y, float z)
        {
            float[] bounds = this.rampBounds;

            if (this.faceRamp == null || bounds == null)
            {
                return 0.5F;
            }

            boolean vertical = this.faceRamp.getAxis() == OutlineGradient.Axis.VERTICAL;

            int axis;
            float sign;

            if (this.faceAxisRamp)
            {
                /* Six-face ramps run along the face's own two in-plane axes, because a face's
                 * "up" and "right" on screen depend on where that face sits on the cube. */
                axis = vertical ? this.faceVAxis : this.faceHAxis;
                sign = vertical ? this.faceVSign : this.faceHSign;
            }
            else if (this.rampFrame != null)
            {
                /* Screen-space layer ramp: measure along the camera's up/right axis, so the ramp
                 * keeps running on screen no matter where the model is turned. Projecting onto the
                 * screen axes also means no face is ever edge-on to the ramp - which is what made
                 * the rim collapse to a single colour when looking along a model axis. */
                float[] frame = this.rampFrame;
                int offset = vertical ? 0 : 3;
                float dirX = frame[offset];
                float dirY = frame[offset + 1];
                float dirZ = frame[offset + 2];
                float lo = vertical ? this.rampUpLo : this.rampRightLo;
                float hi = vertical ? this.rampUpHi : this.rampRightHi;
                float coord = x * dirX + y * dirY + z * dirZ;

                return hi - lo <= 1.0E-6F ? 0.5F : Math.max(0F, Math.min(1F, (coord - lo) / (hi - lo)));
            }
            else
            {
                /* The layer ramp is model-wide: vertical means model Y, horizontal means model X,
                 * so one ramp runs continuously from the lowest bone to the highest. */
                axis = vertical ? 1 : 0;
                sign = 1F;

                /* A face lying across the ramp axis - the top and bottom of a vertical ramp, the
                 * left and right of a horizontal one - has no extent to measure, so all of its
                 * vertices land on the same end colour and the face reads as a flat patch as soon
                 * as the camera looks along the ramp. Such a face samples the other model axis
                 * instead, which keeps the rim showing the gradient from every angle. */
                boolean across = vertical ? (this.faceIndex == OutlineGeometry.FACE_TOP || this.faceIndex == OutlineGeometry.FACE_BOTTOM)
                                          : (this.faceIndex == OutlineGeometry.FACE_LEFT || this.faceIndex == OutlineGeometry.FACE_RIGHT);

                if (across)
                {
                    axis = vertical ? 0 : 1;
                }
            }

            float lo = bounds[axis];
            float hi = bounds[axis + 3];
            float coord = axis == 0 ? x : (axis == 1 ? y : z);
            float t = hi - lo <= 1.0E-6F ? 0.5F : (coord - lo) / (hi - lo);

            return sign < 0F ? 1F - t : t;
        }

        void cube(Matrix4f parent, ModelCube cube, float width, float r, float g, float b, float a,
                          float formAlpha, Set<String> welded, float[][] faceRects, boolean faceCull,
                          int[] faceArgb, int layerArgb, OutlineGradient layerGradient,
                          OutlineGradient[] faceGradients, float[] bounds)
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
            boolean flatX = Math.abs(realMaxX - realMinX) < OutlineGeometry.MIN_EXTENT;
            boolean flatY = Math.abs(realMaxY - realMinY) < OutlineGeometry.MIN_EXTENT;
            boolean flatZ = Math.abs(realMaxZ - realMinZ) < OutlineGeometry.MIN_EXTENT;
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

            /* The ramp runs across the whole model, so every face reads the same box. */
            this.rampBounds = bounds;

            if (this.rampFrame != null && bounds != null)
            {
                this.projectBounds(bounds);
            }

            this.formAlpha = formAlpha;

            if (!welded.contains("front") && cube.back != null && hasXY)
            {
                this.pickFace(faceArgb, faceGradients, layerArgb, layerGradient, OutlineGeometry.FACE_FRONT, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, OutlineGeometry.FACE_FRONT, 0F, 0F, -1F, this.faceR, this.faceG, this.faceB, this.faceA,
                        maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ);
                }
            }

            if (!welded.contains("right") && cube.left != null && hasYZ)
            {
                this.pickFace(faceArgb, faceGradients, layerArgb, layerGradient, OutlineGeometry.FACE_RIGHT, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, OutlineGeometry.FACE_RIGHT, 1F, 0F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
                        maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ);
                }
            }

            if (!welded.contains("back") && cube.front != null && hasXY)
            {
                this.pickFace(faceArgb, faceGradients, layerArgb, layerGradient, OutlineGeometry.FACE_BACK, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, OutlineGeometry.FACE_BACK, 0F, 0F, 1F, this.faceR, this.faceG, this.faceB, this.faceA,
                        minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
                }
            }

            if (!welded.contains("left") && cube.right != null && hasYZ)
            {
                this.pickFace(faceArgb, faceGradients, layerArgb, layerGradient, OutlineGeometry.FACE_LEFT, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, OutlineGeometry.FACE_LEFT, -1F, 0F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
                        minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
                }
            }

            if (!welded.contains("top") && cube.bottom != null && hasXZ)
            {
                this.pickFace(faceArgb, faceGradients, layerArgb, layerGradient, OutlineGeometry.FACE_TOP, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, OutlineGeometry.FACE_TOP, 0F, 1F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
                        maxX, maxY, minZ, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ);
                }
            }

            if (!welded.contains("bottom") && cube.top != null && hasXZ)
            {
                this.pickFace(faceArgb, faceGradients, layerArgb, layerGradient, OutlineGeometry.FACE_BOTTOM, r, g, b, a, formAlpha);

                if (this.wantsFace())
                {
                    this.quad(faceRects, OutlineGeometry.FACE_BOTTOM, 0F, -1F, 0F, this.faceR, this.faceG, this.faceB, this.faceA,
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

            /* A ramp replaces the flat face colour: its position along the ramp is measured in
             * model space, which is exactly the space the vertex now sits in. The sampled alpha is
             * multiplied by the form opacity, since a ramp can fade on its own too. */
            float u = 0F;

            if (this.faceRamp != null)
            {
                u = this.rampT(this.position.x, this.position.y, this.position.z);
                this.faceRamp.sample(u, this.rampScratch);
                r = this.rampScratch[0];
                g = this.rampScratch[1];
                b = this.rampScratch[2];
                a = this.rampScratch[3] * this.formAlpha;
            }

            if (this.needsNormals)
            {
                this.normal.set(nx, ny, nz);
                this.normalMatrix.transform(this.normal);

                if (this.normal.lengthSquared() > 1.0E-12F)
                {
                    this.normal.normalize();
                }

                this.sink.vertex(this.position.x, this.position.y, this.position.z,
                    this.normal.x, this.normal.y, this.normal.z, u, 0F, r, g, b, a);
            }
            else
            {
                this.sink.vertex(this.position.x, this.position.y, this.position.z, 0F, 0F, 0F, u, 0F, r, g, b, a);
            }
        }
    }
