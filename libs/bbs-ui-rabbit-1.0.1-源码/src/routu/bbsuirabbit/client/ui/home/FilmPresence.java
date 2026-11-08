package routu.bbsuirabbit.client.ui.home;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_634;
import net.minecraft.class_640;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who else has which film open, from BBS Together (bbs_together) when it is installed: its client
 * keeps the film every player has open in the film editor and the player's colour. Read by
 * reflection, so BBS++ does not depend on it. Collected once per frame.
 */
final class FilmPresence
{
    static final class Viewer
    {
        final UUID uuid;
        final String name;
        final int color;
        final int skin;

        Viewer(UUID uuid, String name, int color, int skin)
        {
            this.uuid = uuid;
            this.name = name;
            this.color = color;
            this.skin = skin;
        }
    }

    private static boolean resolved;
    private static Method isConnected;
    private static Field filmSelections;
    private static Field playerColors;

    private final Map<String, List<Viewer>> byFilm = new HashMap<>();
    private long collectedAt = -1;

    private static void resolve()
    {
        if (resolved)
        {
            return;
        }

        resolved = true;

        if (!FabricLoader.getInstance().isModLoaded("bbs_together"))
        {
            return;
        }

        try
        {
            Class<?> network = Class.forName("mchorse.bbs_together.network.TogetherClientNetwork");

            isConnected = network.getMethod("isConnected");
            filmSelections = network.getField("filmSelections");
            playerColors = network.getField("playerColors");
        }
        catch (Throwable e)
        {
            isConnected = null;
            filmSelections = null;
            playerColors = null;
        }
    }

    /** Other players with this film open (sorted by name; empty without BBS Together). */
    List<Viewer> get(String filmId)
    {
        long now = System.nanoTime() / 16_000_000L;

        if (now != this.collectedAt)
        {
            this.collectedAt = now;
            this.collect();
        }

        List<Viewer> viewers = this.byFilm.get(filmId);

        return viewers == null ? Collections.emptyList() : viewers;
    }

    private void collect()
    {
        this.byFilm.clear();
        resolve();

        if (filmSelections == null)
        {
            return;
        }

        class_310 mc = class_310.method_1551();
        class_634 network = mc.method_1562();

        if (network == null || mc.field_1724 == null)
        {
            return;
        }

        Map<?, ?> films;
        Map<?, ?> colors;

        try
        {
            if (!(Boolean) isConnected.invoke(null))
            {
                return;
            }

            films = (Map<?, ?>) filmSelections.get(null);
            colors = (Map<?, ?>) playerColors.get(null);
        }
        catch (Throwable e)
        {
            filmSelections = null;

            return;
        }

        UUID self = mc.field_1724.method_5667();

        for (Map.Entry<?, ?> entry : films.entrySet())
        {
            if (!(entry.getKey() instanceof UUID uuid) || !(entry.getValue() instanceof String filmId) || filmId.isEmpty() || uuid.equals(self))
            {
                continue;
            }

            class_640 player = network.method_2871(uuid);

            if (player == null)
            {
                continue;
            }

            class_2960 skinId = player.method_2968();
            int skin = mc.method_1531().method_4619(skinId).method_4624();
            Object color = colors == null ? null : colors.get(uuid);
            int rgb = color instanceof Integer c ? c & 0xffffff : 0xffffff;

            this.byFilm.computeIfAbsent(filmId, k -> new ArrayList<>()).add(new Viewer(uuid, player.method_2966().getName(), rgb, skin));
        }

        for (List<Viewer> viewers : this.byFilm.values())
        {
            viewers.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
    }

    static String names(List<Viewer> viewers)
    {
        StringBuilder builder = new StringBuilder();

        for (Viewer viewer : viewers)
        {
            if (builder.length() > 0)
            {
                builder.append(", ");
            }

            builder.append(viewer.name);
        }

        return builder.toString();
    }
}
