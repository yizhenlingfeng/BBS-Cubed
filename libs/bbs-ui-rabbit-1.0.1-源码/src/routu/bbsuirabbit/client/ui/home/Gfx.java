package routu.bbsuirabbit.client.ui.home;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import net.minecraft.class_286;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_293;
import net.minecraft.class_4587;
import net.minecraft.class_757;
import org.joml.Matrix4f;

/**
 * Drawing for the film library home: anti-aliased looking rounded shapes, soft shadows and
 * glows, built as triangles on float coordinates so they stay smooth at any GUI scale, plus
 * a palette that follows BBS's surface ladder and accent colour (dark and light themes).
 */
public final class Gfx
{
    private Gfx()
    {}

    /* Palette */

    public static boolean light()
    {
        return BBSSettings.lightSurfaces();
    }

    public static int accent()
    {
        return 0xff000000 | BBSSettings.primaryColor.get();
    }

    public static int accent(float alpha)
    {
        return alpha(BBSSettings.primaryColor.get(), alpha);
    }

    /** Main text: never pure white, so Batcher2D leaves it alone in the light theme. */
    public static int ink(float alpha)
    {
        return alpha(light() ? 0x1b1f27 : 0xf2f4f8, alpha);
    }

    public static int inkSoft(float alpha)
    {
        return alpha(light() ? 0x4a5160 : 0xb3bac6, alpha);
    }

    public static int inkMuted(float alpha)
    {
        return alpha(light() ? 0x7a8292 : 0x7d8594, alpha);
    }

    public static int base(float alpha)
    {
        return alpha(BBSSettings.baseSurface(), alpha);
    }

    public static int raised(float alpha)
    {
        return alpha(BBSSettings.raisedSurface(), alpha);
    }

    public static int deep(float alpha)
    {
        return alpha(BBSSettings.deepSurface(), alpha);
    }

    public static int sunken(float alpha)
    {
        return alpha(BBSSettings.sunkenSurface(), alpha);
    }

    public static int divider(float alpha)
    {
        return alpha(BBSSettings.dividerColor(), alpha);
    }

    /** White in the dark theme, black in the light one: hover washes and hairlines. */
    public static int wash(float alpha)
    {
        return alpha(light() ? 0x000000 : 0xffffff, alpha);
    }

    public static int onAccent()
    {
        int c = BBSSettings.primaryColor.get();
        float l = ((c >> 16 & 0xff) * 0.299F + (c >> 8 & 0xff) * 0.587F + (c & 0xff) * 0.114F) / 255F;

        return l > 0.62F ? 0xff10131a : 0xfff8fafc;
    }

    public static int alpha(int rgb, float alpha)
    {
        int a = Math.round(Math.max(0F, Math.min(1F, alpha)) * 255F);

        return (a << 24) | (rgb & 0xffffff);
    }

    public static int mulA(int argb, float factor)
    {
        int a = Math.round(((argb >>> 24) & 0xff) * Math.max(0F, Math.min(1F, factor)));

        return (a << 24) | (argb & 0xffffff);
    }

    public static int lerp(int a, int b, float t)
    {
        t = Math.max(0F, Math.min(1F, t));

        int aa = a >>> 24, ar = a >> 16 & 0xff, ag = a >> 8 & 0xff, ab = a & 0xff;
        int ba = b >>> 24, br = b >> 16 & 0xff, bg = b >> 8 & 0xff, bb = b & 0xff;

        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** An opaque colour of the given hue (0..1), saturation and value. */
    public static int hsv(float h, float s, float v)
    {
        h = (h % 1F + 1F) % 1F * 6F;

        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;

        switch (i)
        {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }

        return 0xff000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /* Easing */

    public static float clamp01(float x)
    {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    public static float easeOut(float x)
    {
        x = clamp01(x);

        float inv = 1 - x;

        return 1 - inv * inv * inv;
    }

    public static float easeInOut(float x)
    {
        x = clamp01(x);

        return x < 0.5F ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    public static float easeOutBack(float x)
    {
        x = clamp01(x);

        float c1 = 1.70158F, c3 = c1 + 1;

        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    /** Frame-rate independent approach of {@code value} to {@code target}. */
    public static float approach(float value, float target, float speed, float dt)
    {
        float k = 1F - (float) Math.exp(-speed * dt);
        float result = value + (target - value) * k;

        return Math.abs(result - target) < 0.001F ? target : result;
    }

    /* Geometry */

    private static class_287 begin(Batcher2D batcher)
    {
        batcher.flush();

        class_287 builder = class_289.method_1348().method_1349();

        builder.method_1328(class_293.class_5596.field_27379, class_290.field_1576);

        return builder;
    }

    private static void end(Batcher2D batcher, class_287 builder)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(class_757::method_34540);
        class_286.method_43433(builder.method_1326());
        RenderSystem.enableCull();
        batcher.getContext().method_51452();
    }

    private static Matrix4f matrix(Batcher2D batcher)
    {
        return batcher.getContext().method_51448().method_23760().method_23761();
    }

    private static int segments(float r)
    {
        return Math.max(2, Math.min(10, (int) Math.ceil(r * 1.2F)));
    }

    /** Outline of a rounded rectangle, clockwise from the top-left arc: [x0, y0, x1, y1, ...]. */
    private static float[] outlinePoints(float x, float y, float w, float h, float r)
    {
        r = Math.max(0F, Math.min(r, Math.min(w, h) / 2F));

        int n = segments(r);
        float[] points = new float[(n + 1) * 4 * 2];
        float[][] centers = {{x + r, y + r}, {x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}};
        int k = 0;

        for (int c = 0; c < 4; c++)
        {
            double start = Math.PI + c * Math.PI / 2;

            for (int i = 0; i <= n; i++)
            {
                double a = start + i * (Math.PI / 2) / n;

                points[k++] = centers[c][0] + (float) Math.cos(a) * r;
                points[k++] = centers[c][1] + (float) Math.sin(a) * r;
            }
        }

        return points;
    }

    private static int bilinear(float u, float v, int tl, int tr, int bl, int br)
    {
        return lerp(lerp(tl, tr, u), lerp(bl, br, u), v);
    }

    public static void roundRect(Batcher2D batcher, float x, float y, float w, float h, float r, int color)
    {
        roundRect(batcher, x, y, w, h, r, color, color, color, color);
    }

    public static void roundRectV(Batcher2D batcher, float x, float y, float w, float h, float r, int top, int bottom)
    {
        roundRect(batcher, x, y, w, h, r, top, top, bottom, bottom);
    }

    public static void roundRectH(Batcher2D batcher, float x, float y, float w, float h, float r, int left, int right)
    {
        roundRect(batcher, x, y, w, h, r, left, right, left, right);
    }

    /** Rounded rectangle with a colour per corner, blended across the shape. */
    public static void roundRect(Batcher2D batcher, float x, float y, float w, float h, float r, int tl, int tr, int bl, int br)
    {
        if (w <= 0 || h <= 0 || ((tl | tr | bl | br) >>> 24) == 0)
        {
            return;
        }

        float[] p = outlinePoints(x, y, w, h, r);
        float cx = x + w / 2F, cy = y + h / 2F;
        int center = bilinear(0.5F, 0.5F, tl, tr, bl, br);
        Matrix4f m = matrix(batcher);
        class_287 builder = begin(batcher);
        int count = p.length / 2;

        for (int i = 0; i < count; i++)
        {
            int j = (i + 1) % count;
            float x1 = p[i * 2], y1 = p[i * 2 + 1], x2 = p[j * 2], y2 = p[j * 2 + 1];

            builder.method_22918(m, cx, cy, 0F).method_39415(center).method_1344();
            builder.method_22918(m, x2, y2, 0F).method_39415(bilinear((x2 - x) / w, (y2 - y) / h, tl, tr, bl, br)).method_1344();
            builder.method_22918(m, x1, y1, 0F).method_39415(bilinear((x1 - x) / w, (y1 - y) / h, tl, tr, bl, br)).method_1344();
        }

        end(batcher, builder);
    }

    /** A band between two rounded rectangles: inner edge {@code inner}, outer edge {@code outer}. */
    private static void ring(Batcher2D batcher, float x, float y, float w, float h, float r, float spread, int inner, int outer)
    {
        if (w <= 0 || h <= 0)
        {
            return;
        }

        r = Math.max(0F, Math.min(r, Math.min(w, h) / 2F));

        float[] in = outlinePoints(x, y, w, h, r);
        float[] out = outlinePointsMatching(x - spread, y - spread, w + spread * 2, h + spread * 2, r + spread, segments(r));
        Matrix4f m = matrix(batcher);
        class_287 builder = begin(batcher);
        int count = in.length / 2;

        for (int i = 0; i < count; i++)
        {
            int j = (i + 1) % count;

            builder.method_22918(m, in[i * 2], in[i * 2 + 1], 0F).method_39415(inner).method_1344();
            builder.method_22918(m, out[j * 2], out[j * 2 + 1], 0F).method_39415(outer).method_1344();
            builder.method_22918(m, out[i * 2], out[i * 2 + 1], 0F).method_39415(outer).method_1344();

            builder.method_22918(m, in[i * 2], in[i * 2 + 1], 0F).method_39415(inner).method_1344();
            builder.method_22918(m, in[j * 2], in[j * 2 + 1], 0F).method_39415(inner).method_1344();
            builder.method_22918(m, out[j * 2], out[j * 2 + 1], 0F).method_39415(outer).method_1344();
        }

        end(batcher, builder);
    }

    private static float[] outlinePointsMatching(float x, float y, float w, float h, float r, int n)
    {
        float[] points = new float[(n + 1) * 4 * 2];
        float[][] centers = {{x + r, y + r}, {x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}};
        int k = 0;

        for (int c = 0; c < 4; c++)
        {
            double start = Math.PI + c * Math.PI / 2;

            for (int i = 0; i <= n; i++)
            {
                double a = start + i * (Math.PI / 2) / n;

                points[k++] = centers[c][0] + (float) Math.cos(a) * r;
                points[k++] = centers[c][1] + (float) Math.sin(a) * r;
            }
        }

        return points;
    }

    /** A hairline (or thicker) border drawn inside the rounded rectangle. */
    public static void roundOutline(Batcher2D batcher, float x, float y, float w, float h, float r, float thickness, int color)
    {
        if ((color >>> 24) == 0)
        {
            return;
        }

        ring(batcher, x + thickness, y + thickness, w - thickness * 2, h - thickness * 2, Math.max(0, r - thickness), thickness, color, color);
    }

    /** Soft shadow (or glow) fading out from the edge of a rounded rectangle. */
    public static void shadow(Batcher2D batcher, float x, float y, float w, float h, float r, float spread, int color)
    {
        if ((color >>> 24) == 0 || !BBSSettings.hasInterfaceGlow())
        {
            return;
        }

        ring(batcher, x, y, w, h, r, spread, color, color & 0xffffff);
    }

    public static void circle(Batcher2D batcher, float cx, float cy, float radius, int color)
    {
        radial(batcher, cx, cy, radius, color, color);
    }

    /** A disc whose colour runs from {@code center} to {@code edge}: glows and aurora blobs. */
    public static void radial(Batcher2D batcher, float cx, float cy, float radius, int center, int edge)
    {
        if (radius <= 0 || ((center | edge) >>> 24) == 0)
        {
            return;
        }

        int n = Math.max(16, Math.min(64, (int) (radius / 2)));
        Matrix4f m = matrix(batcher);
        class_287 builder = begin(batcher);

        for (int i = 0; i < n; i++)
        {
            double a1 = i * Math.PI * 2 / n, a2 = (i + 1) * Math.PI * 2 / n;

            builder.method_22918(m, cx, cy, 0F).method_39415(center).method_1344();
            builder.method_22918(m, cx + (float) Math.cos(a2) * radius, cy + (float) Math.sin(a2) * radius, 0F).method_39415(edge).method_1344();
            builder.method_22918(m, cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius, 0F).method_39415(edge).method_1344();
        }

        end(batcher, builder);
    }

    /** A circle outline of the given thickness, drawn inside the radius. */
    public static void circleRing(Batcher2D batcher, float cx, float cy, float radius, float thickness, int color)
    {
        if (radius <= 0 || (color >>> 24) == 0)
        {
            return;
        }

        int n = Math.max(20, Math.min(64, (int) radius * 2));
        float inner = Math.max(0, radius - thickness);
        Matrix4f m = matrix(batcher);
        class_287 builder = begin(batcher);

        for (int i = 0; i < n; i++)
        {
            double a1 = i * Math.PI * 2 / n, a2 = (i + 1) * Math.PI * 2 / n;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            builder.method_22918(m, cx + c1 * inner, cy + s1 * inner, 0F).method_39415(color).method_1344();
            builder.method_22918(m, cx + c2 * radius, cy + s2 * radius, 0F).method_39415(color).method_1344();
            builder.method_22918(m, cx + c1 * radius, cy + s1 * radius, 0F).method_39415(color).method_1344();

            builder.method_22918(m, cx + c1 * inner, cy + s1 * inner, 0F).method_39415(color).method_1344();
            builder.method_22918(m, cx + c2 * inner, cy + s2 * inner, 0F).method_39415(color).method_1344();
            builder.method_22918(m, cx + c2 * radius, cy + s2 * radius, 0F).method_39415(color).method_1344();
        }

        end(batcher, builder);
    }

    public static void triangle(Batcher2D batcher, float x1, float y1, float x2, float y2, float x3, float y3, int color)
    {
        if ((color >>> 24) == 0)
        {
            return;
        }

        Matrix4f m = matrix(batcher);
        class_287 builder = begin(batcher);

        builder.method_22918(m, x1, y1, 0F).method_39415(color).method_1344();
        builder.method_22918(m, x2, y2, 0F).method_39415(color).method_1344();
        builder.method_22918(m, x3, y3, 0F).method_39415(color).method_1344();

        end(batcher, builder);
    }

    /** A slanted band (a light sweep): parallelogram with a colour across its width. */
    public static void sweep(Batcher2D batcher, float x, float y1, float y2, float width, float slant, int edge, int middle)
    {
        Matrix4f m = matrix(batcher);
        class_287 builder = begin(batcher);
        float half = width / 2F;
        float[][] quads = {{x - half, x}, {x, x + half}};
        int[][] colors = {{edge, middle}, {middle, edge}};

        for (int q = 0; q < 2; q++)
        {
            float a = quads[q][0], b = quads[q][1];
            int ca = colors[q][0], cb = colors[q][1];

            builder.method_22918(m, a + slant, y1, 0F).method_39415(ca).method_1344();
            builder.method_22918(m, a, y2, 0F).method_39415(ca).method_1344();
            builder.method_22918(m, b, y2, 0F).method_39415(cb).method_1344();

            builder.method_22918(m, a + slant, y1, 0F).method_39415(ca).method_1344();
            builder.method_22918(m, b, y2, 0F).method_39415(cb).method_1344();
            builder.method_22918(m, b + slant, y1, 0F).method_39415(cb).method_1344();
        }

        end(batcher, builder);
    }

    /* Images */

    private static final java.util.Set<Integer> LINEAR = new java.util.HashSet<>();

    /**
     * A picture from BBS's assets, smoothly filtered (logos are drawn smaller than their pixels;
     * the pixel-art shader BBS uses for icons would make them crunchy).
     */
    public static void image(Batcher2D batcher, mchorse.bbs_mod.resources.Link link, float x, float y, float w, float h, int color)
    {
        if ((color >>> 24) < 6)
        {
            return;
        }

        mchorse.bbs_mod.graphics.texture.Texture texture = mchorse.bbs_mod.BBSModClient.getTextures().getTexture(link);

        if (texture == null || texture.width <= 0)
        {
            return;
        }

        if (!texture.isLinear() || LINEAR.add(texture.id))
        {
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture.id);
            texture.setFilter(org.lwjgl.opengl.GL11.GL_LINEAR);
            texture.setWrap(org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        }

        batcher.texturedBox(texture, color, x, y, w, h, 0, 0, texture.width, texture.height, texture.width, texture.height);
    }

    /**
     * A GL texture inside a rounded rectangle, showing the {@code u0..u1 x v0..v1} part of it
     * (thumbnails: covers keep the card's corners, and a smaller window zooms in).
     */
    public static void imageRound(Batcher2D batcher, int texture, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int color)
    {
        imageRound(batcher, texture, x, y, w, h, r, u0, v0, u1, v1, color, color, 0F);
    }

    /**
     * Same, tinted from {@code top} to {@code bottom} (fades), and {@code blur} > 0 reads that many
     * mip levels further down: a soft, out-of-focus copy for ambient backdrops, for free.
     */
    public static void imageRound(Batcher2D batcher, int texture, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int top, int bottom, float blur)
    {
        if (texture <= 0 || w <= 0 || h <= 0 || ((top >>> 24) < 2 && (bottom >>> 24) < 2))
        {
            return;
        }

        float[] p = outlinePoints(x, y, w, h, r);
        float cx = x + w / 2F, cy = y + h / 2F;
        Matrix4f m = matrix(batcher);

        batcher.flush();

        class_287 builder = class_289.method_1348().method_1349();
        int count = p.length / 2;
        int middle = lerp(top, bottom, 0.5F);

        builder.method_1328(class_293.class_5596.field_27379, class_290.field_1575);

        for (int i = 0; i < count; i++)
        {
            int j = (i + 1) % count;
            float x1 = p[i * 2], y1 = p[i * 2 + 1], x2 = p[j * 2], y2 = p[j * 2 + 1];

            builder.method_22918(m, cx, cy, 0F).method_22913((u0 + u1) / 2F, (v0 + v1) / 2F).method_39415(middle).method_1344();
            builder.method_22918(m, x2, y2, 0F).method_22913(u0 + (u1 - u0) * (x2 - x) / w, v0 + (v1 - v0) * (y2 - y) / h).method_39415(lerp(top, bottom, (y2 - y) / h)).method_1344();
            builder.method_22918(m, x1, y1, 0F).method_22913(u0 + (u1 - u0) * (x1 - x) / w, v0 + (v1 - v0) * (y1 - y) / h).method_39415(lerp(top, bottom, (y1 - y) / h)).method_1344();
        }

        if (blur > 0F)
        {
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture);
            org.lwjgl.opengl.GL11.glTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL14.GL_TEXTURE_LOD_BIAS, blur);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(class_757::method_34543);
        RenderSystem.setShaderTexture(0, texture);
        class_286.method_43433(builder.method_1326());
        RenderSystem.enableCull();

        if (blur > 0F)
        {
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture);
            org.lwjgl.opengl.GL11.glTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL14.GL_TEXTURE_LOD_BIAS, 0F);
        }

        batcher.getContext().method_51452();
    }

    /* Text and icons */

    public static void text(Batcher2D batcher, String text, float x, float y, int color)
    {
        if ((color >>> 24) < 6 || text == null || text.isEmpty())
        {
            return;
        }

        batcher.text(text, x, y, color, false);
    }

    /** Text drawn at a scale, top-left at (x, y); sub-pixel placement keeps animations smooth. */
    public static void text(Batcher2D batcher, String text, float x, float y, float scale, int color)
    {
        if ((color >>> 24) < 6 || text == null || text.isEmpty())
        {
            return;
        }

        class_4587 stack = batcher.getContext().method_51448();

        stack.method_22903();
        stack.method_46416(x, y, 0F);
        stack.method_22905(scale, scale, 1F);
        batcher.text(text, 0, 0, color, false);
        stack.method_22909();
    }

    public static int width(Batcher2D batcher, String text)
    {
        return batcher.getFont().getWidth(text);
    }

    public static String fit(Batcher2D batcher, String text, int width)
    {
        FontRenderer font = batcher.getFont();

        if (font.getWidth(text) <= width)
        {
            return text;
        }

        return font.limitToWidth(text, "…", width);
    }

    public static void icon(Batcher2D batcher, Icon icon, float x, float y, int color)
    {
        if ((color >>> 24) < 6 || icon == null)
        {
            return;
        }

        batcher.icon(icon, color, x, y);
    }

    /** Icon centred on (cx, cy), scaled. */
    public static void icon(Batcher2D batcher, Icon icon, float cx, float cy, float scale, int color)
    {
        if ((color >>> 24) < 6 || icon == null)
        {
            return;
        }

        float size = icon.w * scale;

        batcher.scaledIcon(icon, color, cx - size / 2F, cy - size / 2F, size);
    }
}
