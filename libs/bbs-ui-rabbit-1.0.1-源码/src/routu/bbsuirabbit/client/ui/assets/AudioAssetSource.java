package routu.bbsuirabbit.client.ui.assets;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Audio editor's home source: list rows (no preview — the editor is barely used). Resolves
 * an event id to a local file for size/date columns; unresolved ids show nothing there.
 */
public class AudioAssetSource implements AssetHomeSource
{
    private final ILandingHost host;

    /** Resolved file per id (null = known missing); cleared when the name list refreshes. */
    private static final Map<String, File> FILE_CACHE = new HashMap<>();

    /** SoundBuffer per id; a null value means "failed or skipped", absence means "not yet". */
    private static final Map<String, SoundBuffer> BUFFERS = new HashMap<>();
    private static final java.util.LinkedList<String> PENDING = new java.util.LinkedList<>();
    private static final long MAX_WAVEFORM_BYTES = 25L << 20;

    public static void clearCache()
    {
        FILE_CACHE.clear();
        BUFFERS.clear();
        PENDING.clear();
    }

    /** The waveform of an event id, or null — see {@link #isFailed} for "never coming". */
    public static mchorse.bbs_mod.audio.Waveform waveformOf(String id)
    {
        SoundBuffer buffer = BUFFERS.get(id);

        return buffer == null ? null : buffer.getWaveform();
    }

    public static float durationOf(String id)
    {
        SoundBuffer buffer = BUFFERS.get(id);

        return buffer == null ? 0 : buffer.getDuration();
    }

    /** True when a waveform was decided unreachable (missing file, oversize, decode error). */
    public static boolean isFailed(String id)
    {
        return BUFFERS.containsKey(id) && BUFFERS.get(id) == null;
    }

    public AudioAssetSource(ILandingHost host)
    {
        this.host = host;
    }

    @Override
    public ILandingHost host()
    {
        return this.host;
    }

    @Override
    public Icon icon()
    {
        return Icons.SOUND;
    }

    @Override
    public boolean listStyle()
    {
        return true;
    }

    @Override
    public void renderThumb(UIContext ctx, String id, int x, int y, int w, int h, boolean hovered)
    {
        /* Recent strip cards show the waveform centered on the dark thumb */
        mchorse.bbs_mod.audio.Waveform waveform = waveformOf(id);

        if (waveform != null)
        {
            waveform.render(ctx.batcher, routu.bbsuirabbit.client.ui.home.Gfx.accent(0.9F), x + 6, y + 6, w - 12, h - 12, 0, durationOf(id));
        }
        else if (!isFailed(id))
        {
            ctx.batcher.box(x + 8, y + h / 2, x + w - 8, y + h / 2 + 1, routu.bbsuirabbit.client.ui.home.Gfx.wash(0.15F));
        }
    }

    /** Tree/breadcrumb path without the {@code <ns>:audio/} event prefix. */
    @Override
    public String displayPath(String id)
    {
        int cut = id.indexOf(":audio/");

        return cut >= 0 ? id.substring(cut + 7) : id;
    }

    /** Queue a waveform for lazy loading — one request per visible row/card. */
    @Override
    public void requestThumb(String id)
    {
        if (BUFFERS.containsKey(id) || PENDING.contains(id))
        {
            return;
        }

        File file = fileOf(id);

        if (file == null || file.length() > MAX_WAVEFORM_BYTES)
        {
            BUFFERS.put(id, null);

            return;
        }

        PENDING.add(id);
    }

    /** Decode at most one queued waveform per frame; SoundManager owns the buffers. */
    @Override
    public void tick(UIContext context, String hoveredId, int x, int y, int w, int h)
    {
        if (PENDING.isEmpty())
        {
            return;
        }

        String id = PENDING.removeFirst();
        SoundBuffer buffer = null;

        try
        {
            buffer = BBSModClient.getSounds().get(Link.create(id), true);
        }
        catch (Throwable t)
        {}

        BUFFERS.put(id, buffer);
    }

    /** Best-effort local file for a sound event id (null when it can't be resolved). */
    public static File fileOf(String id)
    {
        if (FILE_CACHE.containsKey(id))
        {
            return FILE_CACHE.get(id);
        }

        File file = resolve(id);

        FILE_CACHE.put(id, file);

        return file;
    }

    private static File resolve(String id)
    {
        try
        {
            Link link = Link.create(id);
            File assets = BBSMod.getAssetsFolder();
            File audio = BBSMod.getAudioFolder();
            String path = link.path;

            File[] candidates = {
                new File(assets, path),
                new File(audio, path.startsWith("audio/") ? path.substring(6) : path),
                new File(assets, "audio/" + path)
            };

            for (File file : candidates)
            {
                if (file.isFile())
                {
                    return file;
                }
            }
        }
        catch (Exception e)
        {}

        return null;
    }
}
