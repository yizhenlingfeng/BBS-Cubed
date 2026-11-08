package routu.bbsuirabbit.client.ui.home;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * Picture plumbing for the film library thumbnails, all off the render thread: JPEG in and out,
 * 16:9 crops, good downscaling, and the numbers the library draws with (average colour, brightness).
 */
public final class ThumbImages
{
    public static final int COVER_W = 512;
    public static final int COVER_H = 288;
    public static final int FRAME_W = 320;
    public static final int FRAME_H = 180;

    private ThumbImages()
    {}

    /** Pixels read back from GL (RGBA, bottom row first) as an upright image. */
    public static BufferedImage fromGl(ByteBuffer rgba, int w, int h)
    {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];

        for (int y = 0; y < h; y++)
        {
            int base = (h - 1 - y) * w * 4;

            for (int x = 0; x < w; x++)
            {
                int i = base + x * 4;

                row[x] = (rgba.get(i) & 0xff) << 16 | (rgba.get(i + 1) & 0xff) << 8 | (rgba.get(i + 2) & 0xff);
            }

            image.setRGB(0, y, w, 1, row, 0, w);
        }

        return image;
    }

    /** A user's picture: centre-cropped to 16:9, flattened on a dark ground, scaled to cover size. */
    public static BufferedImage readCustom(File file) throws IOException
    {
        BufferedImage source = ImageIO.read(file);

        if (source == null)
        {
            throw new IOException("Not an image: " + file);
        }

        int sw = source.getWidth(), sh = source.getHeight();
        int cw = sw, ch = sh;

        if (sw * 9 > sh * 16)
        {
            cw = Math.max(1, sh * 16 / 9);
        }
        else
        {
            ch = Math.max(1, sw * 9 / 16);
        }

        BufferedImage crop = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = crop.createGraphics();

        g.setColor(new java.awt.Color(0x14161b));
        g.fillRect(0, 0, cw, ch);
        g.drawImage(source, -(sw - cw) / 2, -(sh - ch) / 2, null);
        g.dispose();

        return scale(crop, COVER_W, COVER_H);
    }

    /** Downscale in halving steps (each one bilinear), so big pictures don't alias. */
    public static BufferedImage scale(BufferedImage image, int w, int h)
    {
        BufferedImage current = image;

        while (current.getWidth() / 2 >= w && current.getHeight() / 2 >= h)
        {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }

        if (current.getWidth() == w && current.getHeight() == h)
        {
            return current;
        }

        return draw(current, w, h);
    }

    private static BufferedImage draw(BufferedImage image, int w, int h)
    {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();

        return out;
    }

    public static byte[] toJpeg(BufferedImage image, float quality) throws IOException
    {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

        if (!writers.hasNext())
        {
            throw new IOException("No JPEG writer");
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
        {
            ImageWriteParam param = writer.getDefaultWriteParam();

            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        }
        finally
        {
            writer.dispose();
        }

        return out.toByteArray();
    }

    public static BufferedImage fromJpeg(byte[] bytes) throws IOException
    {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));

        if (image == null)
        {
            throw new IOException("Unreadable picture");
        }

        return image;
    }

    /** Mean brightness, 0..1 (Rec. 601 luma over a sparse grid). */
    public static float brightness(BufferedImage image)
    {
        long sum = 0;
        int count = 0;

        for (int y = 2; y < image.getHeight(); y += 6)
        {
            for (int x = 2; x < image.getWidth(); x += 6)
            {
                int c = image.getRGB(x, y);

                sum += ((c >> 16 & 0xff) * 299 + (c >> 8 & 0xff) * 587 + (c & 0xff) * 114) / 1000;
                count++;
            }
        }

        return count == 0 ? 0 : sum / (count * 255F);
    }

    /**
     * The picture's mood colour for glows: the average, pushed toward its own saturation and kept
     * bright enough to read as light.
     */
    public static int moodColor(BufferedImage image)
    {
        long r = 0, g = 0, b = 0;
        int count = 0;

        for (int y = 3; y < image.getHeight(); y += 8)
        {
            for (int x = 3; x < image.getWidth(); x += 8)
            {
                int c = image.getRGB(x, y);

                r += c >> 16 & 0xff;
                g += c >> 8 & 0xff;
                b += c & 0xff;
                count++;
            }
        }

        if (count == 0)
        {
            return 0x808080;
        }

        float[] hsb = java.awt.Color.RGBtoHSB((int) (r / count), (int) (g / count), (int) (b / count), null);

        /* Glows read as light only when saturated and bright: dark, grey frames still get a colour */
        return java.awt.Color.HSBtoRGB(hsb[0], Math.min(0.85F, hsb[1] * 1.8F + 0.2F), Math.max(0.8F, Math.min(1F, hsb[2] * 1.6F))) & 0xffffff;
    }

    /** Pixels for a GL_RGB upload, top row first. Caller frees the buffer. */
    public static ByteBuffer toRgb(BufferedImage image)
    {
        int w = image.getWidth(), h = image.getHeight();
        ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 3);
        int[] row = new int[w];

        for (int y = 0; y < h; y++)
        {
            image.getRGB(0, y, w, 1, row, 0, w);

            for (int x = 0; x < w; x++)
            {
                int c = row[x];

                buffer.put((byte) (c >> 16));
                buffer.put((byte) (c >> 8));
                buffer.put((byte) c);
            }
        }

        buffer.flip();

        return buffer;
    }
}
