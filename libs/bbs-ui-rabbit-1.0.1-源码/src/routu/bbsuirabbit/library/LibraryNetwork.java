package routu.bbsuirabbit.library;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.utils.PermissionUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Server side of the film library pictures and labels: whoever hosts the world (the integrated
 * server of a LAN/single player world, where BBS++ runs) keeps them in the world folder and tells
 * every connected player with BBS++ about each change right away, so covers and labels set by one
 * player show up for everyone without reopening anything. It does not go through BBS Together:
 * the channels are BBS++'s own.
 *
 * <p>Client → server: {@link #SYNC} (send me the index), {@link #GET} (one picture),
 * {@link #PUT} (a picture, in chunks: client packets max out at 32 KB), {@link #COLOR},
 * {@link #RESET}. Server → client: {@link #INDEX}, {@link #ENTRY} (one film changed),
 * {@link #IMAGE}.</p>
 */
public final class LibraryNetwork
{
    public static final class_2960 SYNC = new class_2960("bbsplusplus", "lib_sync");
    public static final class_2960 GET = new class_2960("bbsplusplus", "lib_get");
    public static final class_2960 PUT = new class_2960("bbsplusplus", "lib_put");
    public static final class_2960 COLOR = new class_2960("bbsplusplus", "lib_color");
    public static final class_2960 RESET = new class_2960("bbsplusplus", "lib_reset");

    public static final class_2960 INDEX = new class_2960("bbsplusplus", "lib_index");
    public static final class_2960 ENTRY = new class_2960("bbsplusplus", "lib_entry");
    public static final class_2960 IMAGE = new class_2960("bbsplusplus", "lib_image");

    /** Payload bytes per {@link #PUT} chunk, under the 32767 byte limit of client packets. */
    public static final int CHUNK = 30_000;

    private static final Map<String, Transfer> TRANSFERS = new ConcurrentHashMap<>();
    private static ExecutorService io;
    private static MinecraftServer server;
    private static LibraryStore served;
    private static final Consumer<String> BROADCAST = LibraryNetwork::broadcast;

    private LibraryNetwork()
    {}

    /** Picture upload being put together from its chunks. */
    private static final class Transfer
    {
        String id;
        int kind;
        int coverKind;
        float dist;
        int span;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long touched = System.currentTimeMillis();
    }

    public static void register()
    {
        ServerPlayNetworking.registerGlobalReceiver(SYNC, (server, player, handler, buf, sender) -> handleSync(server, player));
        ServerPlayNetworking.registerGlobalReceiver(GET, (server, player, handler, buf, sender) -> handleGet(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(PUT, (server, player, handler, buf, sender) -> handlePut(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(COLOR, (server, player, handler, buf, sender) -> handleColor(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(RESET, (server, player, handler, buf, sender) -> handleReset(server, player, buf));

        ServerLifecycleEvents.SERVER_STARTED.register(LibraryNetwork::serve);
        ServerLifecycleEvents.SERVER_STOPPED.register((s) -> stop());
    }

    /* Lifecycle */

    private static synchronized void serve(MinecraftServer started)
    {
        server = started;

        try
        {
            store(started);
        }
        catch (Throwable e)
        {
            System.err.println("[BBS UI Rabbit] Film library store unavailable: " + e);
        }
    }

    private static synchronized void stop()
    {
        if (served != null)
        {
            served.removeListener(BROADCAST);
        }

        served = null;
        server = null;
        TRANSFERS.clear();
        LibraryStore.forgetAll();
    }

    /** The store of the world being served, listened to so every change reaches the players. */
    private static synchronized LibraryStore store(MinecraftServer from)
    {
        server = from;

        File films = BBSMod.getFilms().getFolder();
        LibraryStore store = LibraryStore.forFilms(films);

        if (store != served)
        {
            if (served != null)
            {
                served.removeListener(BROADCAST);
            }

            served = store;
            store.addListener(BROADCAST);
        }

        return store;
    }

    private static synchronized ExecutorService io()
    {
        if (io == null)
        {
            io = Executors.newSingleThreadExecutor((runnable) ->
            {
                Thread thread = new Thread(runnable, "BBS UI Rabbit film library IO");

                thread.setDaemon(true);

                return thread;
            });
        }

        return io;
    }

    /* Handlers (called on the network thread) */

    private static void handleSync(MinecraftServer server, class_3222 player)
    {
        LibraryStore store = store(server);

        io().submit(() ->
        {
            class_2540 out = PacketByteBufs.create();

            writeBytes(out, gzip(store.indexJson().getBytes(StandardCharsets.UTF_8)));
            server.execute(() -> send(player, INDEX, out));
        });
    }

    private static void handleGet(MinecraftServer server, class_3222 player, class_2540 buf)
    {
        String id = readString(buf);
        int kind = buf.readByte();
        LibraryStore store = store(server);

        io().submit(() ->
        {
            LibraryEntry entry = store.get(id);
            byte[] bytes = store.readImage(id, kind);
            long ver = entry == null ? 0 : (kind == 0 ? entry.coverVer : entry.frameVer[Math.max(0, Math.min(LibraryEntry.SLOTS - 1, kind - 1))]);
            class_2540 out = PacketByteBufs.create();

            writeString(out, id);
            out.writeByte(kind);
            out.writeLong(ver);
            writeBytes(out, bytes == null ? new byte[0] : bytes);
            server.execute(() -> send(player, IMAGE, out));
        });
    }

    private static void handlePut(MinecraftServer server, class_3222 player, class_2540 buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        int transferId = buf.readInt();
        int index = buf.readInt();
        int total = buf.readInt();
        String key = player.method_5667() + "/" + transferId;
        long now = System.currentTimeMillis();

        /* Forget uploads that never finished */
        for (Iterator<Transfer> it = TRANSFERS.values().iterator(); it.hasNext(); )
        {
            if (now - it.next().touched > 30_000)
            {
                it.remove();
            }
        }

        Transfer transfer;

        if (index == 0)
        {
            transfer = new Transfer();
            transfer.id = readString(buf);
            transfer.kind = buf.readByte();
            transfer.coverKind = buf.readByte();
            transfer.dist = buf.readFloat();
            transfer.span = buf.readInt();
            TRANSFERS.put(key, transfer);
        }
        else
        {
            transfer = TRANSFERS.get(key);
        }

        if (transfer == null)
        {
            return;
        }

        byte[] chunk = readBytes(buf);

        transfer.touched = now;
        transfer.bytes.writeBytes(chunk);

        if (transfer.bytes.size() > LibraryStore.MAX_IMAGE)
        {
            TRANSFERS.remove(key);

            return;
        }

        if (index < total - 1)
        {
            return;
        }

        TRANSFERS.remove(key);

        LibraryStore store = store(server);
        byte[] bytes = transfer.bytes.toByteArray();

        io().submit(() ->
        {
            if (transfer.kind == 0)
            {
                store.putCover(transfer.id, transfer.coverKind, transfer.dist, transfer.span, bytes);
            }
            else
            {
                store.putFrame(transfer.id, transfer.kind - 1, transfer.dist, transfer.span, bytes);
            }
        });
    }

    private static void handleColor(MinecraftServer server, class_3222 player, class_2540 buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        String id = readString(buf);
        int color = buf.readByte();
        LibraryStore store = store(server);

        io().submit(() -> store.setColor(id, color));
    }

    private static void handleReset(MinecraftServer server, class_3222 player, class_2540 buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        String id = readString(buf);
        LibraryStore store = store(server);

        io().submit(() -> store.resetCover(id));
    }

    /* Telling everyone */

    private static void broadcast(String id)
    {
        MinecraftServer target;
        LibraryStore store;

        synchronized (LibraryNetwork.class)
        {
            target = server;
            store = served;
        }

        if (target == null || store == null)
        {
            return;
        }

        if (id == null)
        {
            String json = store.indexJson();

            target.execute(() ->
            {
                for (class_3222 player : PlayerLookup.all(target))
                {
                    class_2540 out = PacketByteBufs.create();

                    writeBytes(out, gzip(json.getBytes(StandardCharsets.UTF_8)));
                    send(player, INDEX, out);
                }
            });

            return;
        }

        LibraryEntry entry = store.get(id);
        String json = entry == null ? "" : entry.toJson().toString();

        target.execute(() ->
        {
            for (class_3222 player : PlayerLookup.all(target))
            {
                class_2540 out = PacketByteBufs.create();

                writeString(out, id);
                writeString(out, json);
                send(player, ENTRY, out);
            }
        });
    }

    private static void send(class_3222 player, class_2960 channel, class_2540 buf)
    {
        if (ServerPlayNetworking.canSend(player, channel))
        {
            ServerPlayNetworking.send(player, channel, buf);
        }
    }

    /* Wire helpers (plain netty calls only) */

    public static void writeString(class_2540 buf, String string)
    {
        writeBytes(buf, string.getBytes(StandardCharsets.UTF_8));
    }

    public static String readString(class_2540 buf)
    {
        return new String(readBytes(buf), StandardCharsets.UTF_8);
    }

    public static void writeBytes(class_2540 buf, byte[] bytes)
    {
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static byte[] readBytes(class_2540 buf)
    {
        int length = buf.readInt();

        if (length < 0 || length > buf.readableBytes())
        {
            throw new IllegalStateException("Bad length " + length);
        }

        byte[] bytes = new byte[length];

        buf.readBytes(bytes);

        return bytes;
    }

    public static byte[] gzip(byte[] bytes)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (GZIPOutputStream zip = new GZIPOutputStream(out))
        {
            zip.write(bytes);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }

        return out.toByteArray();
    }

    public static byte[] gunzip(byte[] bytes) throws IOException
    {
        try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(bytes)))
        {
            return zip.readAllBytes();
        }
    }

    /* Films renamed or deleted through BBS's film manager (any player, any UI) */

    public static void filmRenamed(File filmsFolder, String from, String to)
    {
        LibraryStore store = LibraryStore.existingForFilms(filmsFolder);

        if (store != null)
        {
            store.renamed(from, to);
        }
    }

    public static void filmFolderRenamed(File filmsFolder, String from, String to)
    {
        LibraryStore store = LibraryStore.existingForFilms(filmsFolder);

        if (store != null)
        {
            store.folderRenamed(from, to);
        }
    }

    public static void filmDeleted(File filmsFolder, String id)
    {
        LibraryStore store = LibraryStore.existingForFilms(filmsFolder);

        if (store != null)
        {
            store.deleted(id);
        }
    }
}
