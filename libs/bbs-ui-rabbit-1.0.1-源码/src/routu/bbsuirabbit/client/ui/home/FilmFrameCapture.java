package routu.bbsuirabbit.client.ui.home;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.library.LibraryEntry;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.UIUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Takes the film library's pictures from the film editor's own preview — what the film camera
 * sees, after shaders, BBS's overlays and BBS++'s cinematic effects, without the editor's interface.
 *
 * <p>Automatic: while a film is open with the film camera on (not flying, not a replay's point of
 * view, no motion path drawn, not recording), whenever the playhead is closer to one of the eight
 * frame spots, or to the automatic cover's spot at 30%, than what the host already has. In
 * practice: one play-through fills the strip, and later edits refresh it as they get played or
 * scrubbed over. A capture is one 512x288 blit + read-back (~1 ms) at most every 300 ms, and
 * nothing at all once the pictures are as close as they get.</p>
 *
 * <p>Manual ("set as cover" in the preview's icon row): the very frame on screen, whatever the
 * camera, with the motion path and orbit marker left out of that one frame.</p>
 */
public final class FilmFrameCapture
{
    private static final int W = ThumbImages.COVER_W;
    private static final int H = ThumbImages.COVER_H;
    private static final long MIN_GAP_MS = 300;

    private static int readFbo = -1;
    private static int dstFbo = -1;
    private static int dstTex = -1;
    private static int midFbo = -1;
    private static int midTex = -1;

    private static String filmId;
    private static long filmSince;
    private static int lastTick = -1;
    private static long stableFrom;
    private static long lastCapture;

    private static boolean manualPending;
    private static int manualFrames;
    private static long flashAt;
    private static ExecutorService worker;

    private FilmFrameCapture()
    {}

    /** While a manual capture waits for its frame: the world leaves out the editor's in-world helpers. */
    public static boolean hidingOverlays()
    {
        return manualPending;
    }

    /** When the last manual capture happened, for the preview's flash. */
    public static long getFlashAt()
    {
        return flashAt;
    }

    public static void requestManual(UIFilmPanel panel)
    {
        if (panel.getData() == null)
        {
            return;
        }

        UIContext context = panel.getContext();

        if (!FilmThumbnails.enabled() || !FilmThumbnails.INSTANCE.canWrite())
        {
            if (context != null)
            {
                context.notifyError(L10n.lang("rabbit.film.set_cover.unavailable"));
            }

            return;
        }

        manualPending = true;
        manualFrames = 0;
    }

    private static ExecutorService worker()
    {
        if (worker == null)
        {
            worker = Executors.newSingleThreadExecutor((runnable) ->
            {
                Thread thread = new Thread(runnable, "BBS UI Rabbit film capture");

                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY + 1);

                return thread;
            });
        }

        return worker;
    }

    /** Film preview, before it draws: the preview texture holds this frame's picture by now. */
    public static void onPreviewRender(UIFilmPanel panel)
    {
        Film film = panel.getData();

        if (film == null || !FilmThumbnails.enabled())
        {
            manualPending = false;

            return;
        }

        FilmThumbnails thumbnails = FilmThumbnails.INSTANCE;
        long now = System.currentTimeMillis();
        String id = film.getId();

        thumbnails.tick();

        if (!id.equals(filmId))
        {
            filmId = id;
            filmSince = now;
            lastTick = -1;
        }

        Texture texture = BBSRendering.getTexture();

        if (texture == null || texture.width < 32 || texture.height < 18 || panel.recorder == null || panel.recorder.isRecording() || panel.recorder.isExporting())
        {
            return;
        }

        int span = film.calculateDuration();
        int tick = panel.getCursor();

        if (manualPending)
        {
            /* One frame for the world to be drawn without the editor's helpers */
            if (++manualFrames < 2)
            {
                return;
            }

            manualPending = false;
            captureManual(panel, id, texture, span, tick);

            return;
        }

        if (!RabbitSettings.isOn(RabbitSettings.filmThumbnailAuto) || !thumbnails.canWrite())
        {
            return;
        }

        /* Settle in first: chunks and models arrive after a film opens or the playhead jumps */
        if (lastTick >= 0 && Math.abs(tick - lastTick) > 40)
        {
            stableFrom = now + 400;
        }

        lastTick = tick;

        if (now - filmSince < 1500 || now < stableFrom || now - lastCapture < MIN_GAP_MS)
        {
            return;
        }

        UIFilmController controller = panel.getController();

        if (controller == null || controller.getPovMode() != UIFilmController.CAMERA_MODE_CAMERA || panel.isFlying() || controller.getMotionPath().enabled.get())
        {
            return;
        }

        boolean wantFrame = false;
        int slot = 0;
        float frameDist = 0;

        /* A spot gets one quick picture as soon as the playhead is anywhere near it, then only
         * one taken right at it: not a stream of slightly better ones on the way there */
        if (span >= LibraryEntry.SLOTS * 2)
        {
            slot = LibraryEntry.slotAt(tick, span);
            frameDist = Math.abs(tick - LibraryEntry.slotCenter(slot, span));
            wantFrame = (!thumbnails.hasFrame(id, slot, span) || frameDist <= Math.max(2F, span * 0.01F)) && thumbnails.wantsFrame(id, slot, frameDist, span);
        }

        float coverDist = Math.abs(tick - LibraryEntry.COVER_AT * span);
        boolean wantCover = (!thumbnails.hasCover(id) || coverDist <= Math.max(2F, span * 0.01F)) && thumbnails.wantsAutoCover(id, coverDist, span);

        if (!wantFrame && !wantCover)
        {
            return;
        }

        lastCapture = now;

        ByteBuffer pixels = grab(texture);

        if (pixels == null)
        {
            return;
        }

        boolean frame = wantFrame;
        boolean cover = wantCover;
        int frameSlot = slot;
        float dist = frameDist;

        /* Claimed now, so the next frames don't grab the same spots while this one is encoded */
        if (frame)
        {
            thumbnails.claimFrame(id, slot, frameDist, span);
        }

        if (cover)
        {
            thumbnails.skipAutoCover(id, coverDist, span);
        }

        worker().submit(() ->
        {
            BufferedImage image;

            try
            {
                image = ThumbImages.fromGl(pixels, W, H);
            }
            finally
            {
                MemoryUtil.memFree(pixels);
            }

            float brightness = ThumbImages.brightness(image);

            net.minecraft.class_310.method_1551().execute(() ->
            {
                if (frame)
                {
                    thumbnails.putFrame(id, frameSlot, dist, span, image);
                }

                if (cover)
                {
                    /* A fade from black makes a poor cover: wait for a lit frame */
                    if (brightness < 0.05F)
                    {
                        thumbnails.skipAutoCover(id, coverDist, span);
                    }
                    else
                    {
                        thumbnails.putCover(id, LibraryEntry.COVER_AUTO, coverDist, span, image, null);
                    }
                }
            });
        });
    }

    private static void captureManual(UIFilmPanel panel, String id, Texture texture, int span, int tick)
    {
        ByteBuffer pixels = grab(texture);

        if (pixels == null)
        {
            return;
        }

        flashAt = System.currentTimeMillis();
        UIUtils.playClick(1.4F);

        worker().submit(() ->
        {
            BufferedImage image;

            try
            {
                image = ThumbImages.fromGl(pixels, W, H);
            }
            finally
            {
                MemoryUtil.memFree(pixels);
            }

            net.minecraft.class_310.method_1551().execute(() -> FilmThumbnails.INSTANCE.putCover(id, LibraryEntry.COVER_MANUAL, 0, span, image, () ->
            {
                UIContext context = panel.getContext();

                if (context != null)
                {
                    context.notifySuccess(L10n.lang("rabbit.film.set_cover.done"));
                }
            }));
        });
    }

    /* GL */

    /**
     * The preview texture, centre-cropped to 16:9 and scaled to 512x288 on the GPU, read back as
     * RGBA (bottom row first). Every binding it touches is put back.
     */
    private static ByteBuffer grab(Texture source)
    {
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        ByteBuffer pixels = null;

        try
        {
            ensureTargets();

            /* Raw GL both ways: the real state goes back exactly as it was, whatever the caches think */
            if (scissor)
            {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            int sw = source.width, sh = source.height;
            int cw = sw, ch = sh;

            if ((long) sw * 9 > (long) sh * 16)
            {
                cw = sh * 16 / 9;
            }
            else
            {
                ch = sw * 9 / 16;
            }

            int cx = (sw - cw) / 2, cy = (sh - ch) / 2;

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, source.id, 0);

            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            {
                return null;
            }

            if (cw > W * 2)
            {
                /* Big frame (fullscreen preview): halve once first so the linear blit doesn't alias */
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, midFbo);
                GL30.glBlitFramebuffer(cx, cy, cx + cw, cy + ch, 0, 0, W * 2, H * 2, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, midFbo);
                cx = 0;
                cy = 0;
                cw = W * 2;
                ch = H * 2;
            }

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
            GL30.glBlitFramebuffer(cx, cy, cx + cw, cy + ch, 0, 0, W, H, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, dstFbo);

            pixels = MemoryUtil.memAlloc(W * H * 4);
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);

            return pixels;
        }
        catch (Throwable e)
        {
            if (pixels != null)
            {
                MemoryUtil.memFree(pixels);
            }

            System.err.println("[BBS UI Rabbit] Film frame capture failed: " + e);

            return null;
        }
        finally
        {
            if (readFbo >= 0)
            {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            }

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);

            if (scissor)
            {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private static void ensureTargets()
    {
        if (readFbo >= 0)
        {
            return;
        }

        readFbo = GL30.glGenFramebuffers();
        dstTex = texture(W, H);
        dstFbo = framebuffer(dstTex);
        midTex = texture(W * 2, H * 2);
        midFbo = framebuffer(midTex);
    }

    private static int texture(int w, int h)
    {
        int id = TextureUtil.generateTextureId();

        GlStateManager._bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlStateManager._bindTexture(0);

        return id;
    }

    private static int framebuffer(int texture)
    {
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int fbo = GL30.glGenFramebuffers();

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);

        return fbo;
    }
}
