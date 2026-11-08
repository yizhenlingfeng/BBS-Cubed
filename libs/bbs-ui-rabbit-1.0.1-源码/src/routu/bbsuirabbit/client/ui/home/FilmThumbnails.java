package routu.bbsuirabbit.client.ui.home;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.library.LibraryEntry;
import routu.bbsuirabbit.library.LibraryNetwork;
import routu.bbsuirabbit.library.LibraryStore;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.utils.repos.IRepository;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2540;
import net.minecraft.class_310;
import net.minecraft.class_642;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The client half of the film library pictures: a mirror of the host's index (covers, hover frames,
 * colour labels), the pictures themselves as GL textures fetched on demand, and the way back up
 * for new captures, custom images and label changes.
 *
 * <p>When the server runs BBS++ (any LAN or single player world — the integrated server is BBS++'s
 * own) everything goes through {@link LibraryNetwork}, so every player sees the same pictures and
 * gets each change pushed as it happens. Anywhere else (a dedicated server without BBS++) the
 * pictures are kept on this computer only.</p>
 *
 * <p>Textures: fetched the first time a card asks, decoded on a worker thread, uploaded with
 * mipmaps, dropped least-recently-used past {@link #MAX_TEXTURES} (a cover is ~0.6 MB of VRAM, a
 * frame ~0.2 MB).</p>
 */
public final class FilmThumbnails
{
    public static final FilmThumbnails INSTANCE = new FilmThumbnails();

    /** Colour labels 1..8: red, orange, yellow, green, cyan, blue, purple, pink. */
    public static final int[] LABEL_COLORS = {0, 0xff6b6b, 0xffa94d, 0xffd43b, 0x51cf66, 0x3bc9db, 0x4c8dff, 0x9775fa, 0xf783ac};

    private static final int MAX_TEXTURES = 80;
    private static final int MAX_IN_FLIGHT = 4;

    /** A picture on the GPU. */
    public static final class Thumb
    {
        public int texture;
        public int width;
        public int height;
        public long ver;
        /** Mood colour for glows (RGB). */
        public int mood;
        /** When it arrived, for the fade-in. */
        public long arrived;
        long used;
    }

    private final Map<String, LibraryEntry> entries = new HashMap<>();
    private final Map<String, Thumb> thumbs = new HashMap<>();
    private final Map<String, Long> wanted = new LinkedHashMap<>();
    private final Map<String, Long> inFlight = new HashMap<>();
    private final Map<String, Long> inFlightVer = new HashMap<>();
    private final Map<String, Long> missing = new HashMap<>();
    /** What this client already sent and the host hasn't answered yet: "id#kind" → {dist, time, span}. */
    private final Map<String, float[]> pendingDist = new HashMap<>();
    private final Map<String, Long> pendingTime = new HashMap<>();
    private final Consumer<String> localListener = this::onLocalChange;

    private boolean synced;
    private long syncAt;
    private long joinedAt;
    private boolean remote;
    private LibraryStore local;
    private int version;
    private int transfers;
    private ExecutorService worker;

    private FilmThumbnails()
    {}

    public static boolean enabled()
    {
        return RabbitSettings.isOn(RabbitSettings.filmThumbnails);
    }

    public static int labelColor(int label)
    {
        return label > 0 && label < LABEL_COLORS.length ? LABEL_COLORS[label] : 0;
    }

    /* Setup */

    public void register()
    {
        ClientPlayNetworking.registerGlobalReceiver(LibraryNetwork.INDEX, (client, handler, buf, sender) ->
        {
            byte[] bytes = LibraryNetwork.readBytes(buf);

            client.execute(() -> this.receiveIndex(bytes));
        });
        ClientPlayNetworking.registerGlobalReceiver(LibraryNetwork.ENTRY, (client, handler, buf, sender) ->
        {
            String id = LibraryNetwork.readString(buf);
            String json = LibraryNetwork.readString(buf);

            client.execute(() -> this.receiveEntry(id, json));
        });
        ClientPlayNetworking.registerGlobalReceiver(LibraryNetwork.IMAGE, (client, handler, buf, sender) ->
        {
            String id = LibraryNetwork.readString(buf);
            int kind = buf.readByte();
            long ver = buf.readLong();
            byte[] bytes = LibraryNetwork.readBytes(buf);

            client.execute(() -> this.receiveImage(id, kind, ver, bytes));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() ->
        {
            this.reset();
            this.joinedAt = System.currentTimeMillis();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::reset));
    }

    /** Forget everything (left the world): the next library frame asks again. */
    public void reset()
    {
        for (Thumb thumb : this.thumbs.values())
        {
            release(thumb);
        }

        if (this.local != null)
        {
            this.local.removeListener(this.localListener);
            this.local = null;
        }

        this.thumbs.clear();
        this.entries.clear();
        this.wanted.clear();
        this.inFlight.clear();
        this.inFlightVer.clear();
        this.missing.clear();
        this.pendingDist.clear();
        this.pendingTime.clear();
        this.synced = false;
        this.syncAt = 0;
        this.version++;
    }

    private ExecutorService worker()
    {
        if (this.worker == null)
        {
            this.worker = Executors.newSingleThreadExecutor((runnable) ->
            {
                Thread thread = new Thread(runnable, "BBS UI Rabbit film thumbnails");

                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY + 1);

                return thread;
            });
        }

        return this.worker;
    }

    /* State */

    public int getVersion()
    {
        return this.version;
    }

    public boolean isSynced()
    {
        return this.synced;
    }

    public LibraryEntry entry(String id)
    {
        return this.entries.get(id);
    }

    public int label(String id)
    {
        LibraryEntry entry = this.entries.get(id);

        return entry == null ? 0 : entry.color;
    }

    /** Keep the mirror and the downloads going; called every frame by whoever shows pictures. */
    public void tick()
    {
        if (!enabled() || class_310.method_1551().field_1724 == null)
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (!this.synced && now - this.syncAt > 3000)
        {
            this.syncAt = now;
            this.sync();
        }

        /* Requests the host never answered */
        this.inFlight.entrySet().removeIf((e) -> now - e.getValue() > 10_000);
        this.inFlightVer.keySet().retainAll(this.inFlight.keySet());

        while (this.inFlight.size() < MAX_IN_FLIGHT && !this.wanted.isEmpty())
        {
            Iterator<Map.Entry<String, Long>> it = this.wanted.entrySet().iterator();
            Map.Entry<String, Long> next = it.next();

            it.remove();
            this.fetch(next.getKey(), next.getValue());
        }
    }

    private void sync()
    {
        this.remote = ClientPlayNetworking.canSend(LibraryNetwork.SYNC);

        /* The server lists its channels a moment after joining: don't settle for "local only" too early */
        if (!this.remote && System.currentTimeMillis() - this.joinedAt < 6000)
        {
            this.syncAt = 0;

            return;
        }

        if (this.remote)
        {
            ClientPlayNetworking.send(LibraryNetwork.SYNC, PacketByteBufs.create());

            return;
        }

        LibraryStore store = this.localStore();

        if (store == null)
        {
            return;
        }

        if (this.local != store)
        {
            if (this.local != null)
            {
                this.local.removeListener(this.localListener);
            }

            this.local = store;
            store.addListener(this.localListener);
        }

        this.worker().submit(() ->
        {
            Map<String, LibraryEntry> snapshot = store.snapshot();

            class_310.method_1551().execute(() ->
            {
                this.entries.clear();
                this.entries.putAll(snapshot);
                this.synced = true;
                this.version++;
            });
        });
    }

    /** Where pictures go when the server can't keep them: beside the local films, or per server in the config. */
    private LibraryStore localStore()
    {
        try
        {
            IRepository<?> repository = ContentType.FILMS.getRepository();
            File folder = repository == null ? null : repository.getFolder();

            if (folder != null)
            {
                return LibraryStore.forFilms(folder);
            }
        }
        catch (Throwable e)
        {
            /* fall through */
        }

        String server = "remote";
        class_642 entry = class_310.method_1551().method_1558();

        if (entry != null && entry.field_3761 != null)
        {
            server = entry.field_3761.replaceAll("[^A-Za-z0-9._-]", "_");
        }

        return LibraryStore.at(FabricLoader.getInstance().getConfigDir().resolve("bbs_ui_rabbit").resolve("library").resolve(server).toFile());
    }

    private void onLocalChange(String id)
    {
        LibraryStore store = this.local;

        if (store == null)
        {
            return;
        }

        if (id == null)
        {
            Map<String, LibraryEntry> snapshot = store.snapshot();

            class_310.method_1551().execute(() ->
            {
                this.entries.clear();
                this.entries.putAll(snapshot);
                this.version++;
            });

            return;
        }

        LibraryEntry entry = store.get(id);

        class_310.method_1551().execute(() -> this.putEntry(id, entry));
    }

    private void receiveIndex(byte[] bytes)
    {
        try
        {
            JsonObject films = JsonParser.parseString(new String(LibraryNetwork.gunzip(bytes), StandardCharsets.UTF_8)).getAsJsonObject();

            this.entries.clear();

            for (Map.Entry<String, JsonElement> film : films.entrySet())
            {
                LibraryEntry entry = new LibraryEntry();

                entry.fromJson(film.getValue().getAsJsonObject());
                this.entries.put(film.getKey(), entry);
            }

            this.synced = true;
            this.version++;
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Bad film library index: " + e);
        }
    }

    private void receiveEntry(String id, String json)
    {
        LibraryEntry entry = null;

        if (!json.isEmpty())
        {
            entry = new LibraryEntry();
            entry.fromJson(JsonParser.parseString(json).getAsJsonObject());
        }

        this.putEntry(id, entry);
    }

    private void putEntry(String id, LibraryEntry entry)
    {
        if (entry == null)
        {
            this.entries.remove(id);
        }
        else
        {
            this.entries.put(id, entry);
        }

        /* The host answered: what we sent is either in or was beaten */
        this.pendingDist.keySet().removeIf((key) -> key.startsWith(id + "#"));
        this.version++;
    }

    /* Pictures */

    /** The film's cover: the set one, else the frame nearest the automatic cover's spot. */
    public Thumb cover(String id)
    {
        LibraryEntry entry = this.entries.get(id);

        if (entry == null || !enabled())
        {
            return null;
        }

        if (entry.hasCover())
        {
            return this.get(id, 0, entry.coverVer);
        }

        int best = -1;
        int target = Math.round(LibraryEntry.COVER_AT * LibraryEntry.SLOTS - 0.5F);

        for (int i = 0; i < LibraryEntry.SLOTS; i++)
        {
            if (entry.frameVer[i] != 0 && (best < 0 || Math.abs(i - target) < Math.abs(best - target)))
            {
                best = i;
            }
        }

        return best < 0 ? null : this.get(id, best + 1, entry.frameVer[best]);
    }

    public Thumb frame(String id, int slot)
    {
        LibraryEntry entry = this.entries.get(id);

        if (entry == null || !enabled() || entry.frameVer[slot] == 0)
        {
            return null;
        }

        return this.get(id, slot + 1, entry.frameVer[slot]);
    }

    /** Slots that have a frame, in order. */
    public int[] frameSlots(String id)
    {
        LibraryEntry entry = this.entries.get(id);

        if (entry == null || !enabled())
        {
            return new int[0];
        }

        List<Integer> slots = new ArrayList<>();

        for (int i = 0; i < LibraryEntry.SLOTS; i++)
        {
            if (entry.frameVer[i] != 0)
            {
                slots.add(i);
            }
        }

        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private Thumb get(String id, int kind, long ver)
    {
        String key = id + "#" + kind;
        Thumb thumb = this.thumbs.get(key);

        if (thumb != null)
        {
            thumb.used = System.currentTimeMillis();
        }

        if ((thumb == null || thumb.ver != ver) && !this.inFlight.containsKey(key))
        {
            Long gone = this.missing.get(key);

            if (gone == null || gone != ver)
            {
                this.wanted.put(key, ver);
            }
        }

        return thumb;
    }

    private void fetch(String key, long ver)
    {
        int hash = key.lastIndexOf('#');
        String id = key.substring(0, hash);
        int kind = Integer.parseInt(key.substring(hash + 1));

        this.inFlight.put(key, System.currentTimeMillis());
        this.inFlightVer.put(key, ver);

        if (this.remote)
        {
            class_2540 buf = PacketByteBufs.create();

            LibraryNetwork.writeString(buf, id);
            buf.writeByte(kind);
            ClientPlayNetworking.send(LibraryNetwork.GET, buf);

            return;
        }

        LibraryStore store = this.local;

        if (store == null)
        {
            this.inFlight.remove(key);

            return;
        }

        this.worker().submit(() ->
        {
            byte[] bytes = store.readImage(id, kind);

            class_310.method_1551().execute(() -> this.receiveImage(id, kind, ver, bytes == null ? new byte[0] : bytes));
        });
    }

    private void receiveImage(String id, int kind, long ver, byte[] bytes)
    {
        String key = id + "#" + kind;

        Long asked = this.inFlightVer.remove(key);

        if (bytes.length == 0)
        {
            /* Not there (any more): don't ask again until the index names a newer one */
            this.inFlight.remove(key);
            this.missing.put(key, Math.max(ver, asked == null ? 0 : asked));

            return;
        }

        this.worker().submit(() ->
        {
            try
            {
                BufferedImage image = ThumbImages.fromJpeg(bytes);
                int mood = ThumbImages.moodColor(image);
                ByteBuffer pixels = ThumbImages.toRgb(image);
                int w = image.getWidth(), h = image.getHeight();

                class_310.method_1551().execute(() ->
                {
                    try
                    {
                        this.install(key, ver, pixels, w, h, mood);
                    }
                    finally
                    {
                        MemoryUtil.memFree(pixels);
                        this.inFlight.remove(key);
                    }
                });
            }
            catch (Exception e)
            {
                class_310.method_1551().execute(() ->
                {
                    this.inFlight.remove(key);
                    this.missing.put(key, ver);
                });
            }
        });
    }

    private void install(String key, long ver, ByteBuffer pixels, int w, int h, int mood)
    {
        Thumb old = this.thumbs.get(key);
        Thumb thumb = new Thumb();

        thumb.texture = TextureUtil.generateTextureId();
        thumb.width = w;
        thumb.height = h;
        thumb.ver = ver;
        thumb.mood = mood;
        thumb.arrived = old == null ? System.currentTimeMillis() : old.arrived;
        thumb.used = System.currentTimeMillis();

        GlStateManager._bindTexture(thumb.texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 6);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._bindTexture(0);

        this.thumbs.put(key, thumb);

        if (old != null)
        {
            release(old);
        }

        this.evict();
    }

    /** Free the GL texture; anyone still holding the thumb sees texture -1 and skips it. */
    private static void release(Thumb thumb)
    {
        if (thumb.texture > 0)
        {
            TextureUtil.releaseTextureId(thumb.texture);
            thumb.texture = -1;
        }
    }

    private void evict()
    {
        if (this.thumbs.size() <= MAX_TEXTURES)
        {
            return;
        }

        long now = System.currentTimeMillis();
        List<Map.Entry<String, Thumb>> list = new ArrayList<>(this.thumbs.entrySet());

        list.sort((a, b) -> Long.compare(a.getValue().used, b.getValue().used));

        for (Map.Entry<String, Thumb> entry : list)
        {
            if (this.thumbs.size() <= MAX_TEXTURES || now - entry.getValue().used < 1000)
            {
                break;
            }

            release(entry.getValue());
            this.thumbs.remove(entry.getKey());
        }
    }

    /* Captures */

    private boolean pendingBlocks(String key, float dist, int span, long now)
    {
        float[] pending = this.pendingDist.get(key);

        if (pending == null)
        {
            return false;
        }

        return !LibraryEntry.improves(1, pending[0], this.pendingTime.getOrDefault(key, 0L), (int) pending[1], dist, span, now);
    }

    private void notePending(String key, float dist, int span)
    {
        this.pendingDist.put(key, new float[] {dist, span});
        this.pendingTime.put(key, System.currentTimeMillis());
    }

    public boolean wantsFrame(String id, int slot, float dist, int span)
    {
        long now = System.currentTimeMillis();
        LibraryEntry entry = this.entries.get(id);

        if (entry != null && !entry.wantsFrame(slot, dist, span, now))
        {
            return false;
        }

        return !this.pendingBlocks(id + "#" + (slot + 1), dist, span, now);
    }

    public boolean wantsAutoCover(String id, float dist, int span)
    {
        long now = System.currentTimeMillis();
        LibraryEntry entry = this.entries.get(id);

        if (entry != null && !entry.wantsAutoCover(dist, span, now))
        {
            return false;
        }

        return !this.pendingBlocks(id + "#0", dist, span, now);
    }

    public boolean hasFrame(String id, int slot, int span)
    {
        LibraryEntry entry = this.entries.get(id);

        return (entry != null && entry.frameVer[slot] != 0 && entry.frameSpan == span) || this.pendingDist.containsKey(id + "#" + (slot + 1));
    }

    public boolean hasCover(String id)
    {
        LibraryEntry entry = this.entries.get(id);

        return (entry != null && entry.hasCover()) || this.pendingDist.containsKey(id + "#0");
    }

    /** Mark a frame spot as being uploaded, so it isn't captured again meanwhile. */
    public void claimFrame(String id, int slot, float dist, int span)
    {
        this.notePending(id + "#" + (slot + 1), dist, span);
    }

    /** Remember an automatic cover was skipped here (too dark), so the same spot isn't grabbed every frame. */
    public void skipAutoCover(String id, float dist, int span)
    {
        this.notePending(id + "#0", dist, span);
    }

    public void putFrame(String id, int slot, float dist, int span, BufferedImage image)
    {
        this.notePending(id + "#" + (slot + 1), dist, span);
        this.worker().submit(() -> this.upload(id, slot + 1, LibraryEntry.COVER_NONE, dist, span, ThumbImages.scale(image, ThumbImages.FRAME_W, ThumbImages.FRAME_H), 0.8F, null));
    }

    public void putCover(String id, int coverKind, float dist, int span, BufferedImage image, Runnable done)
    {
        if (coverKind == LibraryEntry.COVER_AUTO)
        {
            this.notePending(id + "#0", dist, span);
        }

        this.worker().submit(() -> this.upload(id, 0, coverKind, dist, span, image, 0.86F, done));
    }

    /** A picture from disk as the film's cover (file picker or drag and drop). */
    public void putCustomCover(String id, File file, Runnable done, Runnable failed)
    {
        this.worker().submit(() ->
        {
            BufferedImage image;

            try
            {
                image = ThumbImages.readCustom(file);
            }
            catch (Throwable e)
            {
                class_310.method_1551().execute(failed);

                return;
            }

            this.upload(id, 0, LibraryEntry.COVER_CUSTOM, 0, 0, image, 0.9F, done);
        });
    }

    /** Worker thread: encode and hand to the host. */
    private void upload(String id, int kind, int coverKind, float dist, int span, BufferedImage image, float quality, Runnable done)
    {
        byte[] jpeg;

        try
        {
            jpeg = ThumbImages.toJpeg(image, quality);
        }
        catch (Throwable e)
        {
            System.err.println("[BBS UI Rabbit] Couldn't encode a film thumbnail: " + e);

            return;
        }

        class_310.method_1551().execute(() ->
        {
            if (this.remote && ClientPlayNetworking.canSend(LibraryNetwork.PUT))
            {
                int transfer = this.transfers++;
                int total = Math.max(1, (jpeg.length + LibraryNetwork.CHUNK - 1) / LibraryNetwork.CHUNK);

                for (int i = 0; i < total; i++)
                {
                    class_2540 buf = PacketByteBufs.create();
                    int from = i * LibraryNetwork.CHUNK;
                    int to = Math.min(jpeg.length, from + LibraryNetwork.CHUNK);
                    byte[] chunk = new byte[to - from];

                    System.arraycopy(jpeg, from, chunk, 0, chunk.length);
                    buf.writeInt(transfer);
                    buf.writeInt(i);
                    buf.writeInt(total);

                    if (i == 0)
                    {
                        LibraryNetwork.writeString(buf, id);
                        buf.writeByte(kind);
                        buf.writeByte(coverKind);
                        buf.writeFloat(dist);
                        buf.writeInt(span);
                    }

                    LibraryNetwork.writeBytes(buf, chunk);
                    ClientPlayNetworking.send(LibraryNetwork.PUT, buf);
                }
            }
            else if (this.local != null)
            {
                LibraryStore store = this.local;

                this.worker().submit(() ->
                {
                    if (kind == 0)
                    {
                        store.putCover(id, coverKind, dist, span, jpeg);
                    }
                    else
                    {
                        store.putFrame(id, kind - 1, dist, span, jpeg);
                    }
                });
            }

            if (done != null)
            {
                done.run();
            }
        });
    }

    /* Labels and cover reset */

    public void setLabel(String id, int color)
    {
        LibraryEntry entry = this.entries.get(id);

        /* Show it right away; the host's answer confirms */
        if (entry == null)
        {
            entry = new LibraryEntry();
            this.entries.put(id, entry);
        }

        entry.color = color;
        this.version++;

        if (this.remote)
        {
            class_2540 buf = PacketByteBufs.create();

            LibraryNetwork.writeString(buf, id);
            buf.writeByte(color);
            ClientPlayNetworking.send(LibraryNetwork.COLOR, buf);
        }
        else if (this.local != null)
        {
            LibraryStore store = this.local;

            this.worker().submit(() -> store.setColor(id, color));
        }
    }

    public void resetCover(String id)
    {
        if (this.remote)
        {
            class_2540 buf = PacketByteBufs.create();

            LibraryNetwork.writeString(buf, id);
            ClientPlayNetworking.send(LibraryNetwork.RESET, buf);
        }
        else if (this.local != null)
        {
            LibraryStore store = this.local;

            this.worker().submit(() -> store.resetCover(id));
        }
    }

    /** Whether pictures can be stored at all right now (in a world, index known). */
    public boolean canWrite()
    {
        return this.synced && (this.remote || this.local != null);
    }
}
