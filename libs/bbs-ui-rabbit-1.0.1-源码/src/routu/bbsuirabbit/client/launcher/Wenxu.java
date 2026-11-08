package routu.bbsuirabbit.client.launcher;

import com.mojang.blaze3d.systems.RenderSystem;
import routu.bbsuirabbit.client.ui.home.Gfx;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.class_286;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_293;
import net.minecraft.class_4587;
import net.minecraft.class_757;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 小文绪 on the launcher: the live-stream avatar's layered art, skeleton and action curves
 * (avatar v12), cut down for an interface mascot.
 *
 * <p>Everything is in the art's own pixels (a 1254 px canvas); {@link #render} maps that onto
 * the screen with her feet at a point. A layer is a textured quad under an affine matrix built
 * from the skeleton: feet squash and stretch, the hips carry the weight, the neck turns the
 * head, hair, ahoge and scarf hang on springs. The sticker look comes from white, padded
 * silhouettes of the outer layers drawn first (a shifted, shaded copy, then a white one).</p>
 */
public class Wenxu
{
    private static final String DIR = "textures/bbs_ui_rabbit/wenxu/";
    private static final float TAU = (float) (Math.PI * 2);

    private static final float[] F = {562, 1216};
    private static final float[] HIP = {578, 950};
    private static final float[] NECK = {625, 630};
    private static final float[] LEG_R = {663, 955}, ARM_L = {482, 697}, ARM_R = {698, 690};
    private static final float[] HAIR_L = {514, 193}, HAIR_R = {757, 197}, HAIR_C = {664, 172}, HAIR_B = {625, 230}, AHOGE = {708, 152};
    private static final float[] STAR_S = {467, 350}, STAR_L = {417, 311}, HAND_L = {330, 725}, MOUTH = {625, 563};
    private static final float[] BLUSH_L = {515, 556}, BLUSH_R = {733, 580}, SCARF_ROOT = {761, 674}, SCARF_HANG = {652, 698}, NOSE = {640, 545};
    private static final float[] EYE_L = {523, 503}, EYE_R = {735, 527};

    /** Layers that make the silhouette: their padded masks are the sticker's white edge and shade. */
    private static final String[] OUTER = {"scarf_tail", "leg_right", "leg_left", "arm_left_end_v3", "arm_left_v3", "torso_v3", "scarf_hanging_v2", "arm_right_v3",
        "scarf_collar", "hair_back", "face_base", "hair_front_left", "hair_front_right", "hair_front_center", "ahoge", "star_small", "star_large"};

    private static final class Layer
    {
        String name;
        float x, y, w, h, mx, my, mw, mh;
        Texture tex, mask;
    }

    private final Map<String, Layer> layers = new HashMap<>();
    private boolean loaded;
    private final Random random = new Random();

    /* Springs: value and velocity */
    private static final class S
    {
        float p, v;

        S(float p)
        {
            this.p = p;
        }

        void spr(float target, float k, float c, float h)
        {
            this.v += ((target - this.p) * k - this.v * c) * h;
            this.p += this.v * h;
        }
    }

    /** What an action asks of the body at one moment; springs then chase it. */
    private static final class Pose
    {
        float weightX, tilt, lean, armL, armR, flailL, flailR, legR, legKick, shLx, shLy, ahoge, freeze, bean, jit, lid, sq, hsq, nod, shake, eyeWide, blush, mSx = 1, mSy = 1;
        boolean armFront;
        String eyes, mouth;
    }

    private static final class Act
    {
        String id;
        float t0, dur, dir, amp;
        final java.util.Set<String> marks = new java.util.HashSet<>();

        boolean once(String key, boolean cond)
        {
            return cond && this.marks.add(key);
        }
    }

    private static final class Part
    {
        String kind, txt = "";
        float x, y, vx, vy, life, max = 1, size = 80, rot;
        int color;
    }

    private float t;
    private Act act;
    private Pose pose = new Pose();
    private String eyeShown = "open";
    private float blinkT = 2F, blinkAt = -9F, blinkDur = 0.12F, blink2;
    private final S e = new S(1), eyeK = new S(1), closedPop = new S(0), happyPop = new S(0), squintPop = new S(0);
    private final S tilt = new S(0), lean = new S(0), weight = new S(0), armL = new S(0), armR = new S(0), legR = new S(0), nod = new S(0), sq = new S(0), hsq = new S(0);
    private final S headX = new S(0), headY = new S(0), eyeX = new S(0), eyeY = new S(0);
    private final S hairB = new S(0), hairL = new S(0), hairR = new S(0), hairC = new S(0), ahoge = new S(0), starY = new S(0), starR = new S(0);
    private final S blush = new S(0.35F), eyeWide = new S(0), drowsy = new S(0), bubble = new S(0), scarfT = new S(0), scarfH = new S(0);
    private String jumpPhase = "ground";
    private float jumpY, jumpVy, jumpTimer, jumpDur, jumpPower, jumpQueued;
    private boolean jumpBig;
    private float breathPh;
    private float nextIdle = 6F;
    private boolean sleeping;
    private float zT;
    private float activeAt;
    private float glanceX, glanceY, glanceUntil;
    private float lookX, lookY, gazeX, gazeY;
    private boolean wideOnTarget;
    private int selN, pokes;
    private float selT = -9F, pokeT = -9F;
    private final List<Part> parts = new ArrayList<>();
    private float[][] rig;
    private float rigHx, rigHy;
    /** Seconds without the mouse moving before she dozes off. */
    public float sleepAfter = 8F;
    private boolean reduce;

    /* Loading */

    private void load()
    {
        if (this.loaded)
        {
            return;
        }

        this.loaded = true;

        for (Object[] row : WenxuSprites.LAYERS)
        {
            Layer layer = new Layer();

            layer.name = (String) row[0];
            layer.x = (Integer) row[1];
            layer.y = (Integer) row[2];
            layer.w = (Integer) row[3];
            layer.h = (Integer) row[4];
            layer.mx = (Integer) row[5];
            layer.my = (Integer) row[6];
            layer.mw = (Integer) row[7];
            layer.mh = (Integer) row[8];
            layer.tex = texture(layer.name);

            if (layer.mw > 0)
            {
                layer.mask = texture(layer.name + "_m");
            }

            this.layers.put(layer.name, layer);
        }
    }

    private static Texture texture(String name)
    {
        try
        {
            Texture texture = BBSModClient.getTextures().getTexture(Link.assets(DIR + name + ".png"));

            if (texture != null && texture.width > 0 && texture.id > 0)
            {
                com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture.id);
                texture.setFilter(GL11.GL_LINEAR);
                texture.setWrap(org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            }

            return texture;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * BBS's texture manager can drop and remake its textures (clearing it deletes every GL texture,
     * e.g. around leaving a world), so a kept Texture may point at a deleted id and draw solid
     * black: ask the manager each time (a map lookup) and set up whatever it hands back.
     */
    private static Texture live(Texture kept, String name)
    {
        Texture now;

        try
        {
            now = BBSModClient.getTextures().getTexture(Link.assets(DIR + name + ".png"));
        }
        catch (Exception e)
        {
            return null;
        }

        if (now != kept && now != null && now.width > 0 && now.id > 0)
        {
            now = texture(name);
        }

        return now != null && now.id > 0 ? now : null;
    }

    /* Public controls */

    public void setReduceMotion(boolean reduce)
    {
        this.reduce = reduce;
    }

    /** The mouse moved: stay awake, and wake up startled if she was asleep. */
    public void activity()
    {
        this.activeAt = this.t;

        if (this.sleeping)
        {
            this.wake(true);
        }
    }

    /** Where she looks: (x, y) relative to her head, roughly -1..1; {@code wide} = something worth looking at. */
    public void look(float headX, float headY, float eyeX, float eyeY, boolean wide)
    {
        this.lookX = headX;
        this.lookY = headY;
        this.gazeX = eyeX;
        this.gazeY = eyeY;
        this.wideOnTarget = wide;
    }

    public boolean isSleeping()
    {
        return this.sleeping;
    }

    public void react(String event)
    {
        switch (event)
        {
            case "hello" -> this.act("wave");
            case "select" ->
            {
                this.selN = this.t - this.selT < 2.5F ? this.selN + 1 : 0;
                this.selT = this.t;
                this.act(new String[] {"happy", "giggle", "tilt", "hop"}[this.selN % 4]);
            }
            case "fav" -> this.act("happy");
            case "tag" -> this.act("hop");
            case "empty" -> this.act("doudou");
            case "dialog" -> this.act("dai");
            case "deleted" -> this.act("wunai");
            case "cancel" -> this.act("giggle");
            case "mp" -> this.act("tilt");
            case "lan" -> this.act("wave");
            case "theme" ->
            {
                this.blinkNow(0);
                this.blink2 = this.t + 0.24F;
                this.starHop();
            }
            case "poke" ->
            {
                this.pokes = this.t - this.pokeT < 1.2F ? this.pokes + 1 : 1;
                this.pokeT = this.t;

                if (this.pokes >= 3)
                {
                    this.pokes = 0;
                    this.act("wobble");
                }
                else
                {
                    this.act(new String[] {"hop", "giggle", "happy", "wave", "tilt"}[this.random.nextInt(5)]);
                }
            }
            default -> {}
        }
    }

    public void act(String id)
    {
        if (id.equals("star"))
        {
            this.starHop();

            return;
        }

        float dur = switch (id)
        {
            case "tilt" -> rnd(1.4F, 2.2F);
            case "happy" -> 1.9F;
            case "giggle" -> rnd(0.8F, 1.1F);
            case "wunai" -> 3.1F;
            case "doudou" -> 3.3F;
            case "dai" -> 2.95F;
            case "wave" -> 2.6F;
            case "hum" -> 3.9F;
            case "squint" -> 1.35F;
            case "sneeze" -> 2.1F;
            case "wobble" -> 1.85F;
            case "hop" -> 0.95F;
            default -> -1F;
        };

        if (dur < 0)
        {
            return;
        }

        if (this.sleeping)
        {
            this.wake(false);
        }

        Act a = new Act();

        a.id = id;
        a.t0 = this.t;
        a.dur = dur;
        a.dir = this.random.nextBoolean() ? -1 : 1;
        a.amp = rnd(0.06F, 0.1F);
        this.act = a;
        this.nextIdle = rnd(8F, 13F);
    }

    /* Maths */

    private float rnd(float a, float b)
    {
        return a + this.random.nextFloat() * (b - a);
    }

    private static float clamp(float v, float a, float b)
    {
        return v < a ? a : Math.min(v, b);
    }

    private static float smooth(float u)
    {
        u = clamp(u, 0, 1);

        return u * u * (3 - 2 * u);
    }

    private static float env(float u, float a, float r, float dur)
    {
        return smooth(u / a) * (1 - smooth((u - dur + r) / r));
    }

    private static float bump(float u, float c, float w)
    {
        float k = (u - c) / w;

        return (float) Math.exp(-k * k);
    }

    private static float sin(float x)
    {
        return (float) Math.sin(x);
    }

    private static float cos(float x)
    {
        return (float) Math.cos(x);
    }

    /** Affine [a, b, c, d, e, f] as in canvas setTransform. */
    private static float[] mul(float[] A, float[] B)
    {
        return new float[] {A[0] * B[0] + A[2] * B[1], A[1] * B[0] + A[3] * B[1], A[0] * B[2] + A[2] * B[3], A[1] * B[2] + A[3] * B[3],
            A[0] * B[4] + A[2] * B[5] + A[4], A[1] * B[4] + A[3] * B[5] + A[5]};
    }

    private static float[] T(float x, float y)
    {
        return new float[] {1, 0, 0, 1, x, y};
    }

    /** Around point p: translate t, rotate a, scale (sx, sy). */
    private static float[] X(float[] p, float a, float sx, float sy, float tx, float ty)
    {
        float c = cos(a), s = sin(a);
        float A = c * sx, B = s * sx, C = -s * sy, D = c * sy;

        return new float[] {A, B, C, D, p[0] + tx - (A * p[0] + C * p[1]), p[1] + ty - (B * p[0] + D * p[1])};
    }

    private static float[] X(float[] p, float a)
    {
        return X(p, a, 1, 1, 0, 0);
    }

    private static float[] pt(float[] M, float x, float y)
    {
        return new float[] {M[0] * x + M[2] * y + M[4], M[1] * x + M[3] * y + M[5]};
    }

    /* Actions */

    private Pose actionPose()
    {
        Pose z = new Pose();
        Act a = this.act;

        if (a == null)
        {
            return z;
        }

        float u = this.t - a.t0, D = a.dur, k = this.reduce ? 0.3F : 1F;

        switch (a.id)
        {
            case "tilt" ->
            {
                float e = env(u, 0.22F, 0.5F, D);

                z.tilt = a.dir * a.amp * e;
                z.lean = -a.dir * 0.012F * e;
                z.nod = -2 * e;
            }
            case "happy" ->
            {
                float e = env(u, 0.15F, 0.35F, D), w = TAU * 1.15F * u;

                z.tilt = 0.06F * sin(w) * e * k;
                z.lean = 0.016F * sin(w + 0.6F) * e * k;
                z.armL = 0.12F * e;
                z.armR = -0.1F * e;
                z.blush = 0.5F * e;
                z.eyes = u < D - 0.12F ? "happy" : null;
                z.mouth = "open";
                z.mSy = 0.86F;
            }
            case "giggle" ->
            {
                if (u > 0.02F && u < D - 0.1F) z.eyes = "squint";
                z.blush = 0.3F * env(u, 0.1F, 0.3F, D);
                z.mouth = "closed";
                z.mSx = 1.15F;
            }
            case "wave" ->
            {
                float e = env(u, 0.28F, 0.45F, D), w = TAU * 2.5F * Math.max(0, u - 0.3F), e2 = smooth((u - 0.25F) / 0.15F) * (1 - smooth((u - D + 0.55F) / 0.3F));

                z.armL = (0.74F * e + 0.24F * sin(w) * e2) * k;
                z.shLx = -26 * e * k;
                z.shLy = -14 * e * k;
                z.armFront = true;
                z.armR = -0.1F * e * k;
                z.tilt = -0.075F * e * k + 0.02F * sin(w + 0.8F) * e2;
                z.weightX = -0.018F * e * k;
                z.lean = -0.01F * e;
                z.legR = -0.13F * e * k;
                z.sq = -0.012F * e2 * (0.5F + 0.5F * sin(2 * w));
                z.blush = 0.35F * e;
                if (u > 0.32F && u < D - 0.38F) z.eyes = "happy";
                z.mouth = "open";
                z.mSy = 0.92F;
            }
            case "hum" ->
            {
                float e = env(u, 0.45F, 0.6F, D), w = TAU * 0.95F * u;

                z.weightX = 0.034F * sin(w) * e * k;
                z.lean = 0.018F * sin(w) * e * k;
                z.tilt = 0.085F * sin(w + 0.45F) * e * k;
                z.armL = (0.05F + 0.1F * sin(w + 0.3F)) * e * k;
                z.armR = (-0.05F + 0.1F * sin(w + 0.3F)) * e * k;
                z.legR = -0.13F * e * (0.5F + 0.5F * sin(2 * w)) * k;
                z.sq = -0.012F * e * (0.5F + 0.5F * cos(2 * w));
                z.blush = 0.3F * e;
                if (u > 0.3F && u < D - 0.3F) z.eyes = "happy";
                z.mouth = "closed";
                z.mSx = 1 + 0.08F * sin(2 * w);
                z.mSy = 1 + 0.1F * sin(2 * w + 1);
            }
            case "squint" ->
            {
                float e = env(u, 0.07F, 0.22F, D + 0.2F), tr = sin(TAU * 13 * u);

                z.sq = -0.05F * e * k;
                z.hsq = -0.035F * e;
                z.nod = 5 * e;
                z.armL = 0.34F * e * k;
                z.armR = -0.3F * e * k;
                z.flailL = 0.05F * tr * e;
                z.flailR = 0.05F * sin(TAU * 12 * u + 1) * e;
                z.legR = -0.24F * e * k;
                z.shake = 1.6F * tr * e * smooth(u / 0.3F) * k;
                z.blush = 0.75F * e;
                if (u > 0.04F) z.eyes = "squint";
                z.mouth = "closed";
                z.mSx = 1.18F;
                z.mSy = 1.1F;
            }
            case "sneeze" ->
            {
                if (u < 0.95F)
                {
                    float e1 = smooth(u / 0.3F), pulse = bump(u, 0.32F, 0.07F) + 1.3F * bump(u, 0.66F, 0.07F);

                    z.nod = -6 * e1 - 3.5F * pulse;
                    z.tilt = a.dir * 0.04F * e1;
                    z.sq = 0.018F * e1 + 0.008F * pulse;
                    z.hsq = 0.012F * e1;
                    z.armL = 0.06F * e1;
                    z.armR = -0.06F * e1;
                    if (u > 0.42F) z.eyes = "closed";
                    z.mouth = "small";
                    z.mSx = 0.92F;
                    z.mSy = 0.8F + 0.5F * smooth((u - 0.2F) / 0.65F);
                }
                else
                {
                    float v = u - 0.95F, j = (float) Math.exp(-v * 7);

                    z.nod = 6 * j;
                    z.tilt = -a.dir * 0.05F * j;
                    z.sq = -0.02F * j;
                    z.armL = 0.28F * j * k;
                    z.armR = -0.26F * j * k;
                    z.legR = -0.2F * j * k;
                    z.shake = 2.2F * sin(TAU * 11 * v) * j * k;
                    z.blush = 0.45F * (1 - smooth((v - 0.8F) / 0.35F));

                    if (v < 0.42F)
                    {
                        z.eyes = "squint";
                        z.mouth = "closed";
                        z.mSx = 1.25F;
                        z.mSy = 1.2F;
                    }
                    else
                    {
                        z.mouth = "small";
                        z.mSx = 0.8F;
                        z.mSy = 0.6F + 0.15F * bump(v, 0.78F, 0.06F);
                        z.nod += 2 * bump(v, 0.78F, 0.06F);
                    }
                }
            }
            case "wunai" ->
            {
                float inh = bump(u, 0.55F, 0.2F), ex = smooth((u - 0.72F) / 0.28F) * (1 - smooth((u - D + 0.65F) / 0.5F)), sh = smooth((u - 1.3F) / 0.25F) * (1 - smooth((u - D + 0.75F) / 0.35F));

                z.sq = (0.02F * inh - 0.026F * ex) * k;
                z.hsq = 0.012F * inh - 0.01F * ex;
                z.nod = -3.5F * inh + 8 * ex;
                z.armL = (0.07F * inh - 0.12F * ex) * k;
                z.armR = (-0.07F * inh + 0.12F * ex) * k;
                z.tilt = a.dir * 0.035F * ex + 0.05F * sin(TAU * 1.05F * (u - 1.3F)) * sh * k;
                z.lean = -0.01F * ex;
                z.legR = 0.07F * ex;
                z.ahoge = 0.32F * ex;
                z.lid = ex;
                if (u > 0.04F && u < D - 0.28F) z.eyes = "wunai";
                z.mouth = "small";
                z.mSx = 1.25F - 0.2F * inh;
                z.mSy = 0.42F + 0.35F * inh + 0.12F * bump(u, 0.85F, 0.12F);
            }
            case "doudou" ->
            {
                float t1 = smooth((u - 0.12F) / 0.35F), t2 = smooth((u - 1.7F) / 0.28F), back = 1 - smooth((u - D + 0.5F) / 0.4F), side = a.dir * (t1 - 2 * t2) * back;

                z.tilt = 0.11F * side * k;
                z.lean = -0.016F * side;
                z.weightX = 0.012F * side * k;
                z.sq = -0.022F * bump(u, 1.72F, 0.07F) + 0.012F * bump(u, 1.84F, 0.07F);
                z.armL = 0.06F * env(u, 0.2F, 0.4F, D) * k;
                z.armR = -0.06F * env(u, 0.2F, 0.4F, D) * k;
                z.legR = -0.08F * bump(u, 1.75F, 0.12F) * k;
                z.bean = bump(u, 1.12F, 0.045F) + bump(u, 1.33F, 0.045F);
                z.blush = 0.28F * env(u, 0.2F, 0.4F, D);
                if (u > 0.04F && u < D - 0.22F) z.eyes = "doudou";
                z.mouth = "small";
                z.mSx = 0.7F;
                z.mSy = 0.78F + 0.06F * sin(TAU * 1.5F * u);
            }
            case "dai" ->
            {
                float fr = env(u, 0.05F, 0.25F, 2.15F), shU = u - 2.05F, she = shU > 0 ? env(shU, 0.04F, 0.25F, 0.6F) : 0;

                z.freeze = fr;
                z.jit = fr;
                z.sq = 0.016F * fr;
                z.hsq = 0.012F * fr;
                z.nod = -2.5F * fr;
                z.armL = -0.05F * fr;
                z.armR = 0.05F * fr;
                z.ahoge = 0.38F * fr;
                z.legR = 0.05F * fr;
                z.tilt = 0.08F * sin(TAU * 7 * shU) * she * k;
                z.shake = 3 * sin(TAU * 9.5F * shU) * she * k;
                if (u > 0.03F && u < 2.3F) z.eyes = "dai";
                z.mouth = "small";
                float soul = bump(u, 1.15F, 0.45F);
                z.mSx = 0.62F + 0.2F * soul;
                z.mSy = 0.55F + 0.35F * soul;
            }
            case "wobble" ->
            {
                float vv = Math.max(0, u - 0.16F), fade = 1 - smooth((u - 1.4F) / 0.4F);
                float l = a.dir * 0.2F * smooth(u / 0.14F) * cos(TAU * 1.7F * vv) * (float) Math.exp(-1.25F * vv) * fade * k, fe = env(u, 0.1F, 0.4F, 1.4F);

                z.weightX = l;
                z.lean = l * 0.5F;
                z.tilt = -l * 1.1F;
                z.armL = 0.42F * fe * k;
                z.armR = -0.38F * fe * k;
                z.flailL = 0.42F * sin(TAU * 4.2F * u) * fe * k;
                z.flailR = 0.42F * sin(TAU * 4.2F * u + 2.4F) * fe * k;
                z.armFront = true;
                z.legR = -0.18F * fe * k;
                z.legKick = 0.14F * sin(TAU * 3.2F * u) * fe * k;
                z.sq = -0.02F * fe;
                z.blush = 0.2F * fe;
                if (u > 0.08F && u < 1.38F) z.eyes = "squint";

                if (u < 1.35F)
                {
                    z.mouth = "small";
                    z.mSy = 0.9F + 0.22F * sin(TAU * 5 * u);
                }
            }
            case "hop" ->
            {
                float e = env(u, 0.1F, 0.3F, D);

                z.blush = 0.3F * e;
                z.armL = 0.1F * e;
                z.armR = -0.1F * e;
                if (u > 0.05F && u < D - 0.1F) z.eyes = "happy";
                z.mouth = "small";
            }
            default -> {}
        }

        return z;
    }

    private float[] headPt(float x, float y)
    {
        return this.rig == null ? new float[] {x, y} : pt(this.rig[5], x, y);
    }

    private void actEvents(Act a, float u)
    {
        switch (a.id)
        {
            case "tilt" ->
            {
                if (a.once("s", true))
                {
                    if (this.random.nextFloat() < 0.5F) this.blinkNow(0);
                    if (this.random.nextFloat() < 0.6F) this.emit("txt", this.headPt(a.dir > 0 ? 880 : 370, 250), "?", 92, a.dir * 30, -60);
                }
            }
            case "happy" ->
            {
                if (a.once("s", true))
                {
                    this.hop(0.42F, false);
                    this.blush.v += 2;
                    this.emit("heart", this.headPt(860, 300), "", 80, 60, -rnd(90, 140));
                    this.emit("heart", this.headPt(400, 330), "", 80, -50, -rnd(90, 140)).life = -0.18F;
                }
            }
            case "giggle" ->
            {
                if (a.once("s", true))
                {
                    this.squintPop.v += 4;
                    this.emit("spark", this.headPt(870, 380), "", 70, rnd(-60, 60), -rnd(60, 120));
                }
            }
            case "wave" ->
            {
                float[] at = {0.55F, 1F, 1.45F};

                for (int i = 0; i < at.length; i++)
                {
                    if (a.once("w" + i, u > at[i]))
                    {
                        float[] h = this.handPt();

                        this.emit("spark", new float[] {h[0] - 50, h[1] - 60}, "", 60, rnd(-60, 60), -rnd(60, 120));
                    }
                }
            }
            case "hum" ->
            {
                for (int i = 0; i < 5; i++)
                {
                    if (a.once("n" + i, u > 0.45F + i * 0.65F))
                    {
                        this.emit("note", this.headPt(i % 2 == 1 ? rnd(330, 400) : rnd(840, 900), rnd(280, 360)), this.random.nextBoolean() ? "♪" : "♫", 86, (i % 2 == 1 ? -1 : 1) * rnd(25, 50), -rnd(70, 110));
                    }
                }
            }
            case "squint" ->
            {
                if (a.once("s", u > 0.04F))
                {
                    this.ahoge.v -= 3;
                    this.closedPop.v += 4;

                    for (int s = -1; s <= 1; s += 2)
                    {
                        this.emit("puff", this.headPt(s < 0 ? 400 : 850, 470), "", 36, s * rnd(50, 80), -rnd(10, 40));
                        this.emit("lines", this.headPt(s < 0 ? 360 : 890, 420), "", 80, 0, 0).rot = s < 0 ? -2.6F : -0.55F;
                    }
                }

                if (a.once("j", u > a.dur - 0.16F))
                {
                    this.hop(2F, true);
                }
            }
            case "sneeze" ->
            {
                if (a.once("p1", u > 0.3F))
                {
                    this.closedPop.v += 3;
                    this.emit("txt", this.headPt(a.dir > 0 ? 930 : 330, 420), "哈…", 64, 0, -20).max = 0.7F;
                }

                if (a.once("p2", u > 0.64F))
                {
                    this.closedPop.v += 4;
                    this.emit("txt", this.headPt(a.dir > 0 ? 950 : 310, 350), "哈…", 76, 0, -20).max = 0.6F;
                }

                if (a.once("p3", u > 0.95F))
                {
                    this.squintPop.v += 5;
                    this.ahoge.v += 5;
                    this.emit("txt", this.headPt(a.dir > 0 ? 960 : 290, 300), "啾！", 104, 0, -40).max = 0.9F;
                    this.emit("puff", this.headPt(640, 590), "", 36, a.dir * 120, -20);
                }
            }
            case "wunai" ->
            {
                if (a.once("s", u > 0.85F))
                {
                    Part p = this.emit("puff", this.headPt(600, 600), "", 50, -40, 40);

                    p.max = 1.1F;
                    p.color = 0xffdcdceb;
                }
            }
            case "doudou" ->
            {
                if (a.once("s", u > 0.35F)) this.emit("txt", this.headPt(a.dir > 0 ? 880 : 370, 240), "?", 96, 0, -60);
                if (a.once("t", u > 1.8F)) this.emit("txt", this.headPt(a.dir > 0 ? 370 : 880, 260), "?", 80, 0, -60);
            }
            case "dai" ->
            {
                if (a.once("s", u > 0.35F)) this.emit("soul", this.headPt(640, 300), "", 80, 0, -70).max = 1.7F;
                if (a.once("w", u > 2.1F)) this.ahoge.v += 6;
            }
            case "wobble" ->
            {
                if (a.once("s", u > 0.1F))
                {
                    this.emit("txt", this.headPt(880, 230), "!", 100, 0, -60);
                    this.emit("drop", this.headPt(830, 330), "", 60, 20, 30).max = 0.9F;
                }
            }
            case "hop" ->
            {
                if (a.once("a", true)) this.hop(0.75F, false);

                if (a.once("b", u > 0.45F))
                {
                    this.hop(0.55F, false);
                    this.emit("spark", this.headPt(860, 320), "", 70, rnd(-60, 60), -rnd(60, 120));
                }
            }
            default -> {}
        }
    }

    private float[] handPt()
    {
        if (this.rig == null)
        {
            return new float[] {330, 700};
        }

        Pose A = this.pose;

        return pt(mul(this.rig[4], X(ARM_L, this.armL.p + A.flailL, 1, 1, A.shLx, A.shLy)), HAND_L[0], HAND_L[1]);
    }

    private void hop(float power, boolean big)
    {
        if (this.reduce)
        {
            return;
        }

        power = clamp(power, 0.1F, big ? 2.4F : 1.2F);

        if (this.jumpPhase.equals("ground"))
        {
            this.jumpPhase = "crouch";
            this.jumpTimer = 0;
            this.jumpDur = 0.045F + 0.055F * power;
            this.jumpPower = power;
            this.jumpBig = big;

            if (power > 0.7F && !big)
            {
                this.blinkNow(this.jumpDur);
            }
        }
        else
        {
            this.jumpQueued = Math.max(this.jumpQueued, Math.min(1.2F, power) * 0.8F);
        }
    }

    private void blinkNow(float hold)
    {
        this.blinkAt = this.t;
        this.blinkDur = 0.1F + hold;
    }

    private void starHop()
    {
        this.starY.v -= 420;
        this.starR.v += 9;
        this.emit("spark", this.headPt(410, 270), "", 70, rnd(-60, 60), -rnd(60, 120));
    }

    private void sleep()
    {
        this.sleeping = true;
        this.act = null;
        this.zT = 0.6F;
    }

    private void wake(boolean startle)
    {
        if (!this.sleeping)
        {
            return;
        }

        this.sleeping = false;
        this.bubble.p = 0;

        if (startle)
        {
            this.eyeWide.v += 6;
            this.hop(0.8F, false);
            this.emit("txt", this.headPt(880, 220), "!", 104, 0, -60);
        }
    }

    private Part emit(String kind, float[] p, String txt, float size, float vx, float vy)
    {
        Part q = new Part();

        q.kind = kind;
        q.x = p[0];
        q.y = p[1];
        q.txt = txt;
        q.size = size;
        q.vx = vx;
        q.vy = vy;
        q.max = switch (kind)
        {
            case "heart" -> 1.2F;
            case "note" -> 1.4F;
            case "spark" -> 0.8F;
            case "puff" -> 0.7F;
            case "lines" -> 0.4F;
            case "z" -> 1.9F;
            default -> 1F;
        };

        if (this.parts.size() < 40)
        {
            this.parts.add(q);
        }

        return q;
    }

    /* Simulation */

    /** Advance by {@code dt} seconds in fixed steps. */
    public void update(float dt)
    {
        dt = Math.min(dt, 0.05F);

        int steps = Math.max(1, Math.round(dt * 120F));
        float h = dt / steps;

        for (int i = 0; i < steps; i++)
        {
            this.step(h);
        }
    }

    private void step(float h)
    {
        this.t += h;

        float t = this.t;
        Pose A = this.actionPose();

        if (this.act != null)
        {
            this.actEvents(this.act, t - this.act.t0);

            if (this.act != null && t - this.act.t0 > this.act.dur)
            {
                this.act = null;
            }
        }

        this.pose = A;
        this.drowsy.spr(this.sleeping ? 1 : 0, 6, 5, h);

        float d = clamp(this.drowsy.p, 0, 1);

        /* Jump: crouch, fly, land */
        float crouch = 0;

        if (this.jumpPhase.equals("crouch"))
        {
            crouch = -(0.05F + 0.03F * this.jumpPower);
            this.jumpTimer += h;

            if (this.jumpTimer >= this.jumpDur)
            {
                this.jumpPhase = "air";
                this.jumpVy = -(520 + 480 * this.jumpPower);
                this.sq.v += 3;
                this.ahoge.v -= 4;
                this.hairC.v -= 1.5F;
            }
        }
        else if (this.jumpPhase.equals("air"))
        {
            this.jumpVy += 5200 * h;
            this.jumpY += this.jumpVy * h;

            if (this.jumpY >= 0)
            {
                float land = this.jumpVy;

                this.jumpY = 0;
                this.jumpVy = 0;
                this.jumpPhase = "ground";
                this.sq.v -= 1.6F + land / 700F;
                this.ahoge.v += 4;
                this.hairL.v += 0.8F;
                this.hairR.v -= 0.8F;
                this.hairC.v += 1.2F;
                this.scarfT.v += 1.2F;

                if (this.jumpBig)
                {
                    this.emit("puff", new float[] {440, 1210}, "", 40, -90, -20);
                    this.emit("puff", new float[] {700, 1210}, "", 40, 90, -20);
                    this.jumpBig = false;
                }

                if (this.jumpQueued > 0)
                {
                    float q = this.jumpQueued;

                    this.jumpQueued = 0;
                    this.hop(q, false);
                }
            }
        }

        float air = this.jumpPhase.equals("air") ? 1 : 0;
        float stretch = air > 0 ? clamp(-this.jumpVy / 22000F, -0.03F, 0.06F) : 0;

        /* Eyes */
        String eyes = A.eyes != null ? A.eyes : d > 0.55F ? "closed" : null;
        String shown = eyes == null ? "open" : eyes;

        if (!shown.equals(this.eyeShown))
        {
            this.eyeShown = shown;
            this.eyeK.p = shown.equals("open") ? 0.55F : 0.7F;

            if (shown.equals("happy")) this.happyPop.v += 4;
            if (shown.equals("squint")) this.squintPop.v += 4;
            if (shown.equals("closed")) this.closedPop.v += 3;
        }

        this.eyeK.spr(1, 260, 14, h);
        this.closedPop.spr(0, 300, 14, h);
        this.happyPop.spr(0, 300, 14, h);
        this.squintPop.spr(0, 300, 14, h);

        boolean blinking = t - this.blinkAt < this.blinkDur;

        this.e.spr(blinking || d > 0.55F ? 0 : 1, 900, 42, h);

        if (A.eyes == null && d < 0.3F)
        {
            this.blinkT -= h;

            if (this.blinkT <= 0)
            {
                this.blinkNow(0);

                if (this.random.nextFloat() < 0.22F)
                {
                    this.blink2 = t + 0.24F;
                }

                this.blinkT = rnd(2.2F, 5.5F);
            }
        }

        if (this.blink2 > 0 && t >= this.blink2)
        {
            this.blink2 = 0;
            this.blinkNow(0);
        }

        /* Body */
        float idle = this.reduce ? 0 : 1;

        this.tilt.spr(A.tilt + d * 0.05F, 120, 16, h);
        this.lean.spr(A.lean + d * 0.01F, 120, 16, h);
        this.weight.spr(A.weightX, 90, 14, h);
        this.armL.spr(A.armL - d * 0.05F + idle * 0.02F * sin(t * 1.3F), 140, 15, h);
        this.armR.spr(A.armR + d * 0.05F - idle * 0.02F * sin(t * 1.3F + 0.6F), 140, 15, h);
        this.legR.spr(A.legR - air * 0.14F, 140, 15, h);
        this.nod.spr(A.nod + d * 8, 110, 15, h);
        this.hsq.spr(A.hsq, 160, 14, h);
        this.sq.spr(A.sq + crouch + stretch + d * 0.012F, 260, 15, h);
        this.blush.spr(0.35F + A.blush, 40, 10, h);
        this.eyeWide.spr(A.eyeWide + (this.wideOnTarget ? 0.1F : 0), 120, 13, h);

        float fz = 1 - A.freeze, aw = 1 - d;
        float lx = this.lookX, ly = this.lookY, gx = this.gazeX, gy = this.gazeY;

        if (t < this.glanceUntil)
        {
            lx = this.glanceX;
            ly = this.glanceY;
            gx = lx * 1.4F;
            gy = ly * 1.4F;
        }

        this.headX.spr(lx * aw * fz, 70, 11, h);
        this.headY.spr(ly * aw * fz, 70, 11, h);
        this.eyeX.spr(gx * aw * fz, 220, 22, h);
        this.eyeY.spr(gy * aw * fz, 220, 22, h);

        float hairT = -this.tilt.v * 0.09F - this.headX.v * 0.015F;

        this.hairB.spr(-hairT * 0.5F + this.jumpVy * 2e-5F, 60, 5, h);
        this.hairL.spr(hairT + this.jumpVy * 3e-5F, 70, 4.5F, h);
        this.hairR.spr(hairT - this.jumpVy * 3e-5F, 70, 4.5F, h);
        this.hairC.spr(hairT * 0.8F, 75, 4.5F, h);
        this.ahoge.spr(A.ahoge + d * 0.45F + idle * 0.05F * sin(t * 2.1F) - this.tilt.v * 0.12F, 45, 3.5F, h);
        this.scarfT.spr(idle * 0.06F * sin(t * 1.15F) - this.lean.v * 0.5F - this.weight.v * 0.4F + this.jumpVy * 6e-5F, 26, 3.2F, h);
        this.scarfH.spr(idle * 0.04F * sin(t * 1.3F + 0.8F) - this.lean.v * 0.4F + this.jumpVy * 4e-5F, 32, 3.6F, h);
        this.starY.spr(0, 180, 9, h);
        this.starR.spr(0, 150, 7, h);
        this.breathPh += h * (2.1F - d * 0.9F);
        this.bubble.spr(this.sleeping && d > 0.9F ? 0.55F + 0.45F * sin(this.breathPh) : 0, 30, 8, h);

        /* A little life when nothing happens */
        if (!this.reduce && this.act == null && !this.sleeping && this.jumpPhase.equals("ground"))
        {
            this.nextIdle -= h;

            if (this.nextIdle <= 0)
            {
                String[] ids = {"tilt", "glance", "star", "giggle", "hum", "happy", "sneeze", "doudou", "hop", "wave", "wunai"};
                float[] w = {4, 5, 3, 1.2F, 0.8F, 0.7F, 0.35F, 0.5F, 0.6F, 0.4F, 0.3F};
                float sum = 0;

                for (float v : w) sum += v;

                float r = this.random.nextFloat() * sum;
                String id = "tilt";

                for (int i = 0; i < ids.length; i++)
                {
                    r -= w[i];

                    if (r <= 0)
                    {
                        id = ids[i];

                        break;
                    }
                }

                if (id.equals("glance"))
                {
                    this.glanceX = rnd(-1, 1);
                    this.glanceY = rnd(-0.6F, 0.5F);
                    this.glanceUntil = t + rnd(0.8F, 1.6F);
                }
                else
                {
                    this.act(id);
                }

                this.nextIdle = rnd(5, 10);
            }
        }

        if (this.sleeping)
        {
            this.zT -= h;

            if (this.zT <= 0)
            {
                this.zT = 1.7F;

                Part z = this.emit("z", this.headPt(860, 280), "Z", rnd(60, 90), 26, -50);

                z.color = 0xffb9c6ff;
            }
        }
        else if (this.act == null && t - this.activeAt > this.sleepAfter && this.jumpPhase.equals("ground"))
        {
            this.sleep();
        }

        for (int i = this.parts.size() - 1; i >= 0; i--)
        {
            Part q = this.parts.get(i);

            q.life += h;

            if (q.life < 0)
            {
                continue;
            }

            q.x += q.vx * h;
            q.y += q.vy * h;

            if (q.kind.equals("puff"))
            {
                q.vx *= 0.96F;
                q.vy *= 0.96F;
            }

            if (q.kind.equals("soul")) q.vx = 40 * sin(q.life * 5);
            if (q.kind.equals("z")) q.vx = 30 * sin(q.life * 3);

            if (q.life > q.max)
            {
                this.parts.remove(i);
            }
        }
    }

    /* Skeleton */

    /** [root, legL, up, torso, shoulder, head] matrices. */
    private float[][] rig()
    {
        Pose A = this.pose;
        float d = clamp(this.drowsy.p, 0, 1), t = this.t, idle = this.reduce ? 0 : 1;
        float sq = this.sq.p, sy = 1 + sq, sx = 1 - sq * 0.75F;
        float[] root = mul(T(A.shake, this.jumpY), X(F, 0, sx, sy, 0, 0));
        float[] rw = X(F, this.weight.p);
        float[] hip = pt(rw, HIP[0], HIP[1]);
        float[] legL = mul(root, rw);
        float breath = sin(this.breathPh) * (1 + d * 0.8F), bShift = breath * 3.2F;
        float lean = idle * 0.012F * sin(t * 0.63F) + this.lean.p;
        float[] up = mul(root, mul(T(hip[0] - HIP[0], hip[1] - HIP[1]), X(HIP, lean)));
        float[] torso = mul(up, X(HIP, 0, 1 - breath * 0.004F, 1 + breath * 0.011F, 0, 0));
        float hx = this.headX.p, hy = this.headY.p;
        float tilt = hx * 0.065F + this.tilt.p + idle * 0.014F * sin(t * 0.9F);
        float hsq = this.hsq.p - sq * 0.45F, hdx = hx * 6, hdy = hy * (hy < 0 ? 3 : 5) + this.nod.p;
        float[] neck = mul(up, T(hdx, hdy - bShift));
        float[] shoulder = mul(up, T(hdx * 0.5F, Math.max(hdy, 0) * 0.3F - bShift * 0.85F));
        float[] head = mul(neck, X(NECK, tilt, 1 - hsq * 0.7F, 1 + hsq, 0, 0));

        this.rigHx = hx;
        this.rigHy = hy;

        return new float[][] {root, legL, up, torso, shoulder, head};
    }

    private float[] par(float dx, float dy)
    {
        return mul(this.rig[5], T(this.rigHx * dx, this.rigHy * dy));
    }

    /* Drawing */

    /** The screen point of an art pixel, for a figure drawn with its feet at (ox, oy) and scale k. */
    public static float[] toScreen(float ox, float oy, float k, float x, float y)
    {
        return new float[] {ox + (x - 672) * k, oy + (y - 1216) * k};
    }

    /** Head centre on screen (for aiming her gaze). */
    public float[] head(float ox, float oy, float k)
    {
        return toScreen(ox, oy, k, 625, 430);
    }

    public boolean contains(float ox, float oy, float k, float mx, float my)
    {
        float[] a = toScreen(ox, oy, k, 330, 60), b = toScreen(ox, oy, k, 980, 1225);

        return mx >= a[0] && mx <= b[0] && my >= a[1] + this.jumpY * k && my <= b[1];
    }

    /**
     * Draw with her feet at (ox, oy), {@code k} screen pixels per art pixel; {@code shade} is the
     * sticker shadow's colour (opaque, so overlapping silhouettes don't stack up).
     */
    public void render(Batcher2D b, float ox, float oy, float k, int shade, int ground)
    {
        this.load();

        if (this.layers.isEmpty())
        {
            return;
        }

        this.rig = this.rig();

        Pose A = this.pose;
        float[][] R = this.rig;
        float[] collar = this.par(2, 1.5F);
        float aL = this.armL.p + A.flailL, aR = this.armR.p + A.flailR;
        float[] armLM = mul(R[4], X(ARM_L, aL, 1, 1, A.shLx, A.shLy)), armRM = mul(R[4], X(ARM_R, aR));
        boolean lateL = aL > 0.35F && (A.armFront || aL > 0.62F);
        float[] front = this.par(4.5F, 2.8F);
        float[] stars = mul(front, T(0, this.starY.p * 0.08F));
        Map<String, float[]> m = new HashMap<>();

        m.put("scarf_tail", mul(collar, X(SCARF_ROOT, this.scarfT.p)));
        m.put("leg_right", mul(R[2], X(LEG_R, this.legR.p + A.legKick)));
        m.put("leg_left", R[1]);
        m.put("arm_left_end_v3", armLM);
        m.put("arm_left_v3", armLM);
        m.put("torso_v3", R[3]);
        m.put("scarf_hanging_v2", mul(collar, X(SCARF_HANG, this.scarfH.p)));
        m.put("arm_right_v3", armRM);
        m.put("scarf_collar", collar);
        m.put("hair_back", mul(this.par(-3.5F, -1.5F), X(HAIR_B, this.hairB.p)));
        m.put("face_base", this.par(2, 1.5F));
        m.put("hair_front_left", mul(front, X(HAIR_L, this.hairL.p)));
        m.put("hair_front_right", mul(front, X(HAIR_R, this.hairR.p)));
        m.put("hair_front_center", mul(front, X(HAIR_C, this.hairC.p)));
        m.put("ahoge", mul(front, X(AHOGE, this.ahoge.p * 0.9F)));
        m.put("star_small", mul(stars, X(STAR_S, this.starR.p * 0.05F)));
        m.put("star_large", mul(stars, X(STAR_L, -this.starR.p * 0.04F)));

        class_4587 stack = b.getContext().method_51448();

        stack.method_22903();
        stack.method_46416(ox - 672 * k, oy - 1216 * k, 0F);
        stack.method_22905(k, k, 1F);

        /* Ground shadow */
        float lift = clamp(-this.jumpY / 300F, 0, 1);

        stack.method_22903();
        stack.method_46416(F[0] + 20, 1218, 0);
        stack.method_22905(1F, 0.13F, 1F);
        Gfx.circle(b, 0, 0, 170 * (1 - 0.3F * lift), Gfx.mulA(ground, 1 - 0.5F * lift));
        stack.method_22909();

        /* Sticker: shade, then white edge */
        float off = 2.6F / k;

        for (String name : OUTER)
        {
            this.mask(b, name, mul(T(off * 0.8F, off), m.get(name)), shade);
        }

        for (String name : OUTER)
        {
            this.mask(b, name, m.get(name), 0xffffffff);
        }

        /* The figure */
        this.draw(b, "scarf_tail", m);
        this.draw(b, "leg_right", m);
        this.draw(b, "leg_left", m);

        if (!lateL)
        {
            this.draw(b, "arm_left_end_v3", m);
            this.draw(b, "arm_left_v3", m);
        }

        if (lateL)
        {
            /* The torso and collar keep depth, so the raised arm drawn later tucks behind them */
            b.flush();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            this.sprite(b, "torso_v3", m.get("torso_v3"), 0xffffffff, 60F);
        }
        else
        {
            this.draw(b, "torso_v3", m);
        }

        if (lateL)
        {
            RenderSystem.depthMask(false);
        }

        this.draw(b, "scarf_hanging_v2", m);
        this.draw(b, "arm_right_v3", m);

        if (lateL)
        {
            RenderSystem.depthMask(true);
            this.sprite(b, "scarf_collar", m.get("scarf_collar"), 0xffffffff, 60F);
            RenderSystem.depthMask(false);
        }
        else
        {
            this.draw(b, "scarf_collar", m);
        }

        this.draw(b, "hair_back", m);
        this.draw(b, "face_base", m);
        this.drawBlush(b);
        this.drawEye(b, "left", mul(R[5], T(this.rigHx * 8, this.rigHy * 5)), EYE_L);
        this.drawEye(b, "right", mul(R[5], T(this.rigHx * 7, this.rigHy * 5)), EYE_R);

        String mouth = A.mouth != null ? A.mouth : this.eyeShown.equals("happy") || this.sleeping ? "small" : "closed";
        float msx = A.mouth != null ? A.mSx : 1, msy = A.mouth != null ? A.mSy : 1;

        if (this.sleeping)
        {
            msx = msy = 0.7F + 0.08F * sin(this.breathPh);
        }

        this.sprite(b, "mouth_" + mouth, mul(this.par(6, 3.5F), X(MOUTH, 0, msx, msy, 0, 0)), 0xffffffff, 0);

        for (String name : new String[] {"hair_front_left", "hair_front_right", "hair_front_center", "ahoge", "star_small", "star_large"})
        {
            this.draw(b, name, m);
        }

        if (lateL)
        {
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            this.sprite(b, "arm_left_end_v3", armLM, 0xffffffff, 30F);
            this.sprite(b, "arm_left_v3", armLM, 0xffffffff, 30F);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(true);
        }

        this.drawBubble(b);
        this.drawParts(b);

        stack.method_22909();
    }

    private void draw(Batcher2D b, String name, Map<String, float[]> m)
    {
        this.sprite(b, name, m.get(name), 0xffffffff, 0F);
    }

    private void mask(Batcher2D b, String name, float[] M, int color)
    {
        Layer layer = this.layers.get(name);

        if (layer == null || layer.mw <= 0 || M == null)
        {
            return;
        }

        Texture texture = live(layer.mask, name + "_m");

        if (texture == null)
        {
            return;
        }

        layer.mask = texture;

        quad(b, layer.mask, M, layer.mx, layer.my, layer.mw, layer.mh, color, 0F);
    }

    private void sprite(Batcher2D b, String name, float[] M, int color, float z)
    {
        Layer layer = this.layers.get(name);

        if (layer == null || M == null)
        {
            return;
        }

        Texture texture = live(layer.tex, name);

        if (texture == null)
        {
            return;
        }

        layer.tex = texture;

        quad(b, layer.tex, M, layer.x, layer.y, layer.w, layer.h, color, z);
    }

    /** One texture over (x, y, w, h) art pixels, under the affine M and the current pose. */
    private static void quad(Batcher2D b, Texture texture, float[] M, float x, float y, float w, float h, int color, float z)
    {
        if (texture == null || texture.width <= 0)
        {
            return;
        }

        b.flush();

        Matrix4f pose = new Matrix4f(b.getContext().method_51448().method_23760().method_23761());
        Matrix4f affine = new Matrix4f(M[0], M[1], 0, 0, M[2], M[3], 0, 0, 0, 0, 1, 0, M[4], M[5], z, 1);

        pose.mul(affine);

        class_287 builder = class_289.method_1348().method_1349();

        builder.method_1328(class_293.class_5596.field_27379, class_290.field_1575);
        builder.method_22918(pose, x, y, 0).method_22913(0, 0).method_39415(color).method_1344();
        builder.method_22918(pose, x, y + h, 0).method_22913(0, 1).method_39415(color).method_1344();
        builder.method_22918(pose, x + w, y + h, 0).method_22913(1, 1).method_39415(color).method_1344();
        builder.method_22918(pose, x, y, 0).method_22913(0, 0).method_39415(color).method_1344();
        builder.method_22918(pose, x + w, y + h, 0).method_22913(1, 1).method_39415(color).method_1344();
        builder.method_22918(pose, x + w, y, 0).method_22913(1, 0).method_39415(color).method_1344();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(class_757::method_34543);
        RenderSystem.setShaderTexture(0, texture.id);
        class_286.method_43433(builder.method_1326());
        RenderSystem.enableCull();
    }

    private void drawEye(Batcher2D b, String side, float[] M, float[] pivot)
    {
        Pose A = this.pose;
        String pre = "eye_" + side + "_";
        float kq = this.eyeK.p, ky = Math.max(0.05F, kq), kx = kq < 1 ? 1 + (1 - kq) * 0.42F : 1 - (kq - 1) * 0.5F;
        String s = this.eyeShown;

        if (s.equals("squint"))
        {
            float p = this.squintPop.p;

            this.sprite(b, pre + "squint", mul(M, X(pivot, 0, (1 + p * 0.12F) * kx, (1 - p * 0.1F) * ky, 0, 0)), 0xffffffff, 0);

            return;
        }

        if (s.equals("happy"))
        {
            float p = this.happyPop.p;

            this.sprite(b, pre + "happy", mul(M, X(pivot, 0, (1 + p * 0.12F) * kx, (1 - p * 0.18F) * ky, 0, -p * 3)), 0xffffffff, 0);

            return;
        }

        if (s.equals("closed") || this.e.p < 0.22F)
        {
            float p = this.closedPop.p;

            this.sprite(b, pre + "closed", mul(M, X(pivot, 0, (1 + p * 0.1F) * kx, (1 - p * 0.25F) * ky, 0, 0)), 0xffffffff, 0);

            return;
        }

        if (s.equals("wunai") || s.equals("doudou") || s.equals("dai"))
        {
            String name = s + "_" + side + "_v4";
            Layer layer = this.layers.get(name);

            if (layer == null)
            {
                return;
            }

            float sx = 1, sy = 1, tx = 0, ty = 0;

            if (s.equals("wunai"))
            {
                sy = 1 - 0.16F * A.lid;
                ty = 2 * A.lid;
            }
            else if (s.equals("doudou"))
            {
                sy = 1 - 0.9F * A.bean;
                sx = 1 + 0.3F * A.bean;
            }
            else
            {
                float o = side.equals("left") ? 0 : 1.7F;

                tx = A.jit * 0.9F * sin(this.t * 57 + o);
                ty = A.jit * 0.7F * sin(this.t * 43 + o * 2);
            }

            float[] piv = {layer.x + layer.w / 2F, layer.y + layer.h / 2F};

            this.sprite(b, name, mul(M, X(piv, 0, sx * kx, sy * ky, tx, ty)), 0xffffffff, 0);

            return;
        }

        /* Open: white, iris (follows the gaze, kept small so it stays inside), lashes */
        float e = this.e.p, wide = 1 + this.eyeWide.p;
        float esy = Math.min(e, 1.1F) * wide * ky, esx = clamp(1 - (Math.min(e, 1.1F) - 1) * 0.6F, 0.9F, 1.18F) * wide * kx;
        float[] eye = mul(M, X(pivot, 0, esx, esy, 0, 0));
        float dx = clamp(this.eyeX.p * 6, -6, 6), dy = clamp(this.eyeY.p * 4 - this.hsq.p * 30, -4, 4);

        this.sprite(b, pre + "frame", eye, 0xffffffff, 0);
        this.sprite(b, pre + "iris", mul(eye, T(dx, dy)), 0xffffffff, 0);
        this.sprite(b, pre + "lash", eye, 0xffffffff, 0);
    }

    private void drawBlush(Batcher2D b)
    {
        float bl = clamp(this.blush.p, 0, 0.95F);

        if (bl < 0.01F)
        {
            return;
        }

        float[] M = this.par(6, 3.5F);
        int center = Gfx.alpha(0xff80a0, Math.min(0.8F, 0.55F * bl));

        for (float[] at : new float[][] {BLUSH_L, BLUSH_R})
        {
            this.push(b, M);
            b.getContext().method_51448().method_46416(at[0], at[1], 0);
            b.getContext().method_51448().method_22905(1, 0.55F, 1);
            Gfx.radial(b, 0, 0, 34, center, Gfx.alpha(0xff80a0, 0F));
            b.getContext().method_51448().method_22909();
        }
    }

    private void drawBubble(Batcher2D b)
    {
        float s = this.bubble.p;

        if (s < 0.03F)
        {
            return;
        }

        float r = 8 + 34 * s, x = NOSE[0] + 16 + r * 0.7F, y = NOSE[1] + 4 + r * 0.2F;

        this.push(b, this.par(6, 3.5F));
        Gfx.circle(b, x, y, r, 0x8cbee4ff);
        Gfx.circleRing(b, x, y, r, 4, 0xff3a1d33);
        Gfx.circle(b, x - r * 0.25F, y - r * 0.35F, r * 0.22F, 0xd9ffffff);
        b.getContext().method_51448().method_22909();
    }

    private void push(Batcher2D b, float[] M)
    {
        class_4587 stack = b.getContext().method_51448();

        stack.method_22903();
        stack.method_23760().method_23761().mul(new Matrix4f(M[0], M[1], 0, 0, M[2], M[3], 0, 0, 0, 0, 1, 0, M[4], M[5], 0, 1));
    }

    private void drawParts(Batcher2D b)
    {
        class_4587 stack = b.getContext().method_51448();

        for (Part q : this.parts)
        {
            if (q.life < 0)
            {
                continue;
            }

            float u = q.life / q.max;
            float a = clamp(u < 0.15F ? u / 0.15F : 1 - smooth((u - 0.65F) / 0.35F), 0, 1);
            float s = q.size * 1.35F * (u < 0.15F ? 0.6F + 0.4F * u / 0.15F : 1);
            int ink = Gfx.alpha(0x3a1d33, a);

            stack.method_22903();
            stack.method_46416(q.x, q.y, 0);

            switch (q.kind)
            {
                case "heart", "note", "txt", "spark", "z" ->
                {
                    String glyph = q.kind.equals("heart") ? "♥" : q.kind.equals("spark") ? "✦" : q.txt;
                    int fill = q.kind.equals("heart") ? 0xffff1b75 : q.kind.equals("spark") ? 0xffffd84a : q.kind.equals("note") ? 0xff6fb7ff : q.color != 0 ? q.color : 0xfffdfded;
                    float scale = s / 9F;

                    stack.method_22907(new org.joml.Quaternionf().rotateZ(q.kind.equals("spark") ? u * 2 : sin(q.life * 4) * 0.15F));
                    stack.method_22905(scale, scale, 1);

                    float w = Gfx.width(b, glyph);

                    for (int i = 0; i < 8; i++)
                    {
                        double ang = i * Math.PI / 4;

                        Gfx.text(b, glyph, -w / 2F + (float) Math.cos(ang) * 1.1F, -4.5F + (float) Math.sin(ang) * 1.1F, ink);
                    }

                    Gfx.text(b, glyph, -w / 2F, -4.5F, Gfx.mulA(fill, a));
                }
                case "puff" ->
                {
                    int fill = Gfx.mulA(q.color != 0 ? q.color : 0xffffffff, a);

                    for (int j = 0; j < 3; j++)
                    {
                        float r = s * (0.6F + 0.4F * u) * (j == 1 ? 1.15F : 0.85F), cx = (j - 1) * s * 0.7F, cy = (j % 2) * -s * 0.4F;

                        Gfx.circleRing(b, cx, cy, r + 3, 7, ink);
                        Gfx.circle(b, cx, cy, r, fill);
                    }
                }
                case "lines" ->
                {
                    stack.method_22907(new org.joml.Quaternionf().rotateZ(q.rot));

                    for (int j = -1; j <= 1; j++)
                    {
                        float len = 70 + 40 * u;

                        b.box(0, j * 34 - 5, len, j * 34 + 5, ink);
                    }
                }
                case "soul" ->
                {
                    Gfx.circleRing(b, 0, 0, 49, 6, ink);
                    Gfx.circle(b, 0, 0, 46, Gfx.alpha(0xffffff, 0.92F * a));
                    Gfx.triangle(b, 10, 30, 46, 0, 20, 85, Gfx.alpha(0xffffff, 0.92F * a));
                    Gfx.circle(b, -14, -6, 6, ink);
                    Gfx.circle(b, 14, -6, 6, ink);
                }
                case "drop" ->
                {
                    Gfx.circleRing(b, 0, 6, 22, 6, ink);
                    Gfx.circle(b, 0, 6, 19, Gfx.alpha(0x9fd8ff, a));
                    Gfx.triangle(b, -15, -6, 15, -6, 0, -32, Gfx.alpha(0x9fd8ff, a));
                }
                default -> {}
            }

            stack.method_22909();
        }
    }
}
