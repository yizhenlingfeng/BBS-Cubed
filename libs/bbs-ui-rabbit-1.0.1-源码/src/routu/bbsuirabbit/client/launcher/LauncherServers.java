package routu.bbsuirabbit.client.launcher;

import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1131;
import net.minecraft.class_1134;
import net.minecraft.class_155;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_412;
import net.minecraft.class_437;
import net.minecraft.class_639;
import net.minecraft.class_641;
import net.minecraft.class_642;
import net.minecraft.class_644;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multiplayer for the launcher, done the way vanilla's multiplayer screen does it: the same
 * {@code servers.dat} (so both lists stay one list), the same pinger on a small thread pool,
 * the same LAN broadcast listener.
 */
public class LauncherServers
{
    private static final ExecutorService PING = Executors.newFixedThreadPool(5, (r) ->
    {
        Thread thread = new Thread(r, "BBS UI Rabbit launcher pinger");

        thread.setDaemon(true);

        return thread;
    });

    private class_641 list;
    private final class_644 pinger = new class_644();
    private class_1134.class_1136 lanEntries;
    private class_1134.class_1135 lanDetector;
    private List<class_1131> lan = new ArrayList<>();
    private final Map<class_642, Icon> icons = new IdentityHashMap<>();
    private boolean open;
    private long lastTick;

    private static class Icon
    {
        byte[] bytes;
        class_1043 texture;
    }

    public void open()
    {
        if (this.open)
        {
            return;
        }

        this.open = true;
        this.list = new class_641(class_310.method_1551());
        this.list.method_2981();
        this.lanEntries = new class_1134.class_1136();

        try
        {
            this.lanDetector = new class_1134.class_1135(this.lanEntries);
            this.lanDetector.start();
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Unable to start LAN server detection: " + e.getMessage());
        }
    }

    public void close()
    {
        if (!this.open)
        {
            return;
        }

        this.open = false;

        if (this.lanDetector != null)
        {
            this.lanDetector.interrupt();
            this.lanDetector = null;
        }

        this.pinger.method_3004();

        for (Icon icon : this.icons.values())
        {
            if (icon.texture != null)
            {
                icon.texture.close();
            }
        }

        this.icons.clear();
    }

    public boolean isOpen()
    {
        return this.open;
    }

    /** Call every frame; network polling runs at tick rate. */
    public void tick()
    {
        if (!this.open)
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - this.lastTick < 50)
        {
            return;
        }

        this.lastTick = now;

        List<class_1131> found = this.lanEntries.method_45912();

        if (found != null)
        {
            this.lan = new ArrayList<>(found);
        }

        this.pinger.method_3000();
    }

    public List<class_642> servers()
    {
        List<class_642> servers = new ArrayList<>();

        if (this.list != null)
        {
            for (int i = 0; i < this.list.method_2984(); i++)
            {
                servers.add(this.list.method_2982(i));
            }
        }

        return servers;
    }

    public List<class_1131> lan()
    {
        return this.lan;
    }

    /** Start pinging a server the first time it is shown (vanilla does the same). */
    public void ensurePinged(class_642 server)
    {
        if (server.field_3754)
        {
            return;
        }

        server.field_3754 = true;
        server.field_3758 = -2L;
        server.field_3757 = class_2561.method_43473();
        server.field_3753 = class_2561.method_43473();

        PING.submit(() ->
        {
            try
            {
                this.pinger.method_3003(server, () -> class_310.method_1551().execute(this::save));
            }
            catch (UnknownHostException e)
            {
                server.field_3758 = -1L;
                server.field_3757 = class_2561.method_43471("multiplayer.status.cannot_resolve");
            }
            catch (Exception e)
            {
                server.field_3758 = -1L;
                server.field_3757 = class_2561.method_43471("multiplayer.status.cannot_connect");
            }
        });
    }

    public static boolean pinged(class_642 server)
    {
        return server.field_3754 && server.field_3758 != -2L;
    }

    public static boolean compatible(class_642 server)
    {
        return !pinged(server) || server.field_3758 < 0 || server.field_3756 == class_155.method_16673().method_48020();
    }

    public void refresh()
    {
        this.pinger.method_3004();

        for (class_642 server : this.servers())
        {
            server.field_3754 = false;
        }
    }

    public void save()
    {
        if (this.list != null)
        {
            this.list.method_2987();
        }
    }

    public void reloadList()
    {
        if (this.list != null)
        {
            this.list.method_2981();
        }
    }

    public void add(class_642 server)
    {
        class_642 hidden = this.list.method_44296(server.field_3761);

        if (hidden != null)
        {
            hidden.method_44292(server);
        }
        else
        {
            this.list.method_2988(server, false);
        }

        this.save();
    }

    public void remove(class_642 server)
    {
        this.list.method_2983(server);
        this.save();
    }

    public void move(class_642 server, int delta)
    {
        List<class_642> all = this.servers();
        int i = all.indexOf(server), j = i + delta;

        if (i >= 0 && j >= 0 && j < all.size())
        {
            this.list.method_2985(i, j);
            this.save();
        }
    }

    /** Direct connect: vanilla keeps such addresses as hidden entries. */
    public void connectAddress(class_437 parent, String address)
    {
        class_642 known = this.list.method_44295(address);

        if (known == null)
        {
            known = new class_642(class_2561.method_43471("selectServer.defaultName").getString(), address, false);
            this.list.method_2988(known, true);
            this.save();
        }

        connect(parent, known);
    }

    public static void connect(class_437 parent, class_642 server)
    {
        class_412.method_36877(parent, class_310.method_1551(), class_639.method_2950(server.field_3761), server, false);
    }

    public static void connectLan(class_437 parent, class_1131 entry)
    {
        connect(parent, new class_642(entry.method_4813(), entry.method_4812(), true));
    }

    /** The server's favicon as a texture id (0 = none). */
    public int icon(class_642 server)
    {
        byte[] bytes = server.method_49306();
        Icon icon = this.icons.computeIfAbsent(server, (s) -> new Icon());

        if (!Arrays.equals(bytes, icon.bytes))
        {
            icon.bytes = bytes;

            if (icon.texture != null)
            {
                icon.texture.close();
                icon.texture = null;
            }

            if (bytes != null)
            {
                try
                {
                    icon.texture = new class_1043(class_1011.method_49277(bytes));
                }
                catch (Exception e)
                {
                    server.method_49305(null);
                    this.save();
                }
            }
        }

        return icon.texture == null ? 0 : icon.texture.method_4624();
    }
}
