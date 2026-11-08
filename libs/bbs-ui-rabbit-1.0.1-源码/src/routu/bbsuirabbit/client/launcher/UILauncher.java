package routu.bbsuirabbit.client.launcher;

import com.mojang.blaze3d.systems.RenderSystem;
import routu.bbsuirabbit.client.ui.home.FilmThumbnails;
import routu.bbsuirabbit.client.ui.home.Gfx;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1043;
import net.minecraft.class_1109;
import net.minecraft.class_1131;
import net.minecraft.class_1934;
import net.minecraft.class_2561;
import net.minecraft.class_286;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_293;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_3417;
import net.minecraft.class_4189;
import net.minecraft.class_4325;
import net.minecraft.class_426;
import net.minecraft.class_429;
import net.minecraft.class_422;
import net.minecraft.class_437;
import net.minecraft.class_445;
import net.minecraft.class_4587;
import net.minecraft.class_4749;
import net.minecraft.class_5481;
import net.minecraft.class_642;
import net.minecraft.class_757;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The BBS-Rabbit launcher: the first screen of the game. A comic-sticker workbench in the
 * studio colours — a sidebar (worlds, tags, multiplayer, and vanilla's title buttons at the
 * bottom, 小文绪 standing above them), a grid of worlds, and the selected world's details.
 *
 * <p>Drawn by hand like BBS++'s film library: hit regions are collected while drawing and
 * answered on the next click. Text fields are real BBS text boxes laid over the drawing.</p>
 */
public class UILauncher extends UIElement
{
    public static final String VIEW_SAVES = "saves";
    public static final String VIEW_SERVERS = "servers";

    private static final String L = "rabbit.launcher.";
    private static final Link LOGO = Link.assets("textures/bbs_ui_rabbit/routu_logo.png");
    private static final String[] SPLASHES = {"AAA兔肉批发！", "关注文绪谢谢喵！", "这里不是小僵！", "爱花TV！", "BBS 2.7！", "肉兔重工出品！", "今天拍哪一场？"};
    private static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";
    private static final String[] SORTS = {"recent", "name", "films", "size"};

    /* Palette (set every frame from BBS's light/dark surfaces) */
    private boolean dark;
    private int paper, side, card, stroke, text, muted, rasp, raspSoft, mint, mintBg, butter, plate, dot, shade, ground;

    /* Data */
    private final LauncherWorlds worlds = new LauncherWorlds();
    private final LauncherServers servers = new LauncherServers();
    private final LauncherPrefs prefs = new LauncherPrefs();
    private final Wenxu wenxu = new Wenxu();

    /* Text fields */
    private final UITextbox search;
    private final UITextbox tagInput;
    private final UITextbox address;
    private final UITextbox nameInput;

    /* State */
    private String view = VIEW_SAVES;
    private String filter = "all";
    private String selected;
    private class_642 selServer;
    private class_1131 selLan;
    private List<LauncherWorlds.World> visible = new ArrayList<>();
    private int seenWorlds = -1;
    private String listKey = "";
    private long listChangedAt;
    private long selectedAt;
    private boolean wasEmpty;
    private boolean lanSeen;
    private boolean opened;
    private final String splash;
    /** What the inline editor is changing: a save's name or alias in the inspector (a folder), or a tag in the sidebar. */
    private String renaming;
    private String renameKind = "name";

    /* Scrolling */
    private float scroll, scrollTarget, navScroll, navTarget, inspScroll, inspTarget;
    private float gridContentH, navContentH, inspContentH;
    private float navTop, navBottom;
    private int gridCols = 3;

    /* Time and mouse */
    private long lastFrame;
    private float dt, time;
    private float lastMx = -1, lastMy = -1;

    /* Interaction */
    private static final class Hit
    {
        final String key;
        final float x, y, w, h;
        Runnable click, right, dbl;
        String tooltip;
        /** Hovering shows this film's details instead of a text tooltip. */
        LauncherWorlds.Film film;

        Hit(String key, float x, float y, float w, float h)
        {
            this.key = key;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(float px, float py)
        {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private List<Hit> lastHits = new ArrayList<>();
    private String hot;
    private String hotSince;
    private long hotAt;
    private String lastClickKey;
    private long lastClickAt;
    private final Map<String, Float> anims = new HashMap<>();

    /* Overlays */
    private static final class MenuItem
    {
        final String label;
        final Runnable action;
        final boolean on, danger, separator;

        MenuItem(String label, Runnable action, boolean on, boolean danger)
        {
            this.label = label;
            this.action = action;
            this.on = on;
            this.danger = danger;
            this.separator = label == null;
        }
    }

    private List<MenuItem> menu;
    private float menuX, menuY;
    private long menuAt;
    private String dlgTitle, dlgText, dlgYes;
    private Runnable dlgAction;
    private long dlgAt;
    private String toast;
    private long toastAt;

    /* Layout */
    private float W, H, SW, IW, MX, MW, utilTop, charTop;
    private Batcher2D b;
    private UIContext ctx;

    /* Halftone tile */
    private static class_1043 dotTexture;

    public UILauncher()
    {
        this.search = new UITextbox(100, (t) -> this.scrollTarget = 0);
        this.search.background(false);
        this.search.placeholder(L10n.lang(L + "search"));
        this.search.relative(this).set(0, 0, 10, 16);

        this.tagInput = new UITextbox(16, (t) -> {});
        this.tagInput.background(false);
        this.tagInput.placeholder(L10n.lang(L + "tag.add"));
        this.tagInput.relative(this).set(0, 0, 10, 16);

        this.address = new UITextbox(255, (t) -> {});
        this.address.background(false);
        this.address.placeholder(L10n.lang(L + "server.address"));
        this.address.relative(this).set(0, 0, 10, 16);

        /* Enter keeps the new name, Escape drops it (the textbox would otherwise just lose focus) */
        this.nameInput = new UITextbox(32, (t) -> {})
        {
            @Override
            public boolean subKeyPressed(UIContext context)
            {
                if (this.isFocused() && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
                {
                    UILauncher.this.finishRename(false);

                    return true;
                }

                if (this.isFocused() && (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER)))
                {
                    UILauncher.this.finishRename(true);

                    return true;
                }

                return super.subKeyPressed(context);
            }
        };
        this.nameInput.background(false);
        this.nameInput.placeholder(L10n.lang(L + "rename.placeholder"));
        this.nameInput.relative(this).set(0, 0, 10, 16);
        this.nameInput.setVisible(false);

        this.add(this.search, this.tagInput, this.address, this.nameInput);

        this.splash = SPLASHES[(int) (Math.random() * SPLASHES.length)];
        this.prefs.load();
        this.selected = this.prefs.selected.isEmpty() ? null : this.prefs.selected;
    }

    /* Strings */

    private static String tr(String key)
    {
        return L10n.lang(L + key).get();
    }

    private static String tr(String key, Object... args)
    {
        return L10n.lang(L + key).format(args).get();
    }

    /* Lifecycle */

    public void showView(String view)
    {
        if (view.equals(VIEW_SERVERS))
        {
            this.setViewServers(this.filter.equals("lan") ? "lan" : "servers");
        }
        else if (this.view.equals(VIEW_SERVERS))
        {
            this.view = VIEW_SAVES;
            this.filter = "all";
        }
    }

    public void onOpen()
    {
        this.worlds.reload();
        this.seenWorlds = -1;
        this.wenxu.activity();

        if (this.view.equals(VIEW_SERVERS))
        {
            this.servers.open();
            this.servers.reloadList();
        }

        if (!this.opened)
        {
            this.opened = true;
            this.wenxu.react("hello");
        }

        this.listChangedAt = System.currentTimeMillis();
    }

    public void onClose()
    {
        this.servers.close();
        this.closeMenu();
    }

    private class_437 parent()
    {
        return class_310.method_1551().field_1755;
    }

    private static void sound()
    {
        class_310.method_1551().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1F));
    }

    private void showToast(String text)
    {
        this.toast = text;
        this.toastAt = System.currentTimeMillis();
    }

    /* Data views */

    private boolean isFavorite(LauncherWorlds.World w)
    {
        return this.prefs.favorites.contains(w.folder);
    }

    private List<String[]> tagsOf(LauncherWorlds.World w)
    {
        List<String[]> list = new ArrayList<>();

        if (w.flat)
        {
            list.add(new String[] {tr("tag.flat"), "auto"});
        }

        for (String tag : this.prefs.tagsOf(w.folder))
        {
            list.add(new String[] {tag, ""});
        }

        return list;
    }

    private boolean recent(LauncherWorlds.World w)
    {
        return System.currentTimeMillis() - w.lastPlayed < 7L * 24 * 3600 * 1000;
    }

    private Map<String, Integer> tagIndex(List<LauncherWorlds.World> all)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String flat = tr("tag.flat");

        for (LauncherWorlds.World w : all)
        {
            if (w.flat)
            {
                counts.merge(flat, 1, Integer::sum);
            }
        }

        Map<String, Integer> user = new HashMap<>();

        for (LauncherWorlds.World w : all)
        {
            for (String tag : this.prefs.tagsOf(w.folder))
            {
                user.merge(tag, 1, Integer::sum);
            }
        }

        user.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue() != 0 ? c.getValue() - a.getValue() : a.getKey().compareTo(c.getKey()))
            .forEach((e) -> counts.merge(e.getKey(), e.getValue(), Integer::sum));

        return counts;
    }

    private void rebuild(List<LauncherWorlds.World> all)
    {
        String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
        List<LauncherWorlds.World> list = new ArrayList<>();

        for (LauncherWorlds.World w : all)
        {
            if (this.filter.equals("recent") && !this.recent(w)) continue;
            if (this.filter.equals("fav") && !this.isFavorite(w)) continue;
            if (this.filter.equals("films") && w.films.isEmpty()) continue;

            if (this.filter.startsWith("tag:"))
            {
                String tag = this.filter.substring(4);

                if (this.tagsOf(w).stream().noneMatch((t) -> t[0].equals(tag))) continue;
            }

            if (!q.isEmpty())
            {
                StringBuilder hay = new StringBuilder(w.name).append(' ').append(this.display(w)).append(' ').append(w.folder);

                this.tagsOf(w).forEach((t) -> hay.append(' ').append(t[0]));
                w.films.forEach((f) -> hay.append(' ').append(f.id));

                if (!hay.toString().toLowerCase(Locale.ROOT).contains(q)) continue;
            }

            list.add(w);
        }

        Comparator<LauncherWorlds.World> order = switch (this.prefs.sort)
        {
            case "name" -> Comparator.comparing((LauncherWorlds.World w) -> this.display(w).toLowerCase(Locale.ROOT));
            case "films" -> Comparator.comparingInt((LauncherWorlds.World w) -> -w.films.size());
            case "size" -> Comparator.comparingLong((LauncherWorlds.World w) -> -w.size);
            default -> Comparator.comparingLong((LauncherWorlds.World w) -> -w.lastPlayed);
        };

        list.sort(order);

        String key = this.filter + "|" + q + "|" + this.prefs.sort + "|" + list.size() + "|" + this.worlds.version();

        if (!key.equals(this.listKey))
        {
            boolean onlyQuery = this.listKey.startsWith(this.filter + "|") && !q.isEmpty();

            this.listKey = key;

            if (!onlyQuery)
            {
                this.listChangedAt = System.currentTimeMillis();
            }
        }

        boolean empty = list.isEmpty() && !all.isEmpty();

        if (empty && !this.wasEmpty)
        {
            this.wenxu.react("empty");
        }

        this.wasEmpty = empty;
        this.visible = list;

        if (this.selected == null || all.stream().noneMatch((w) -> w.folder.equals(this.selected)))
        {
            this.selected = list.isEmpty() ? (all.isEmpty() ? null : all.get(0).folder) : list.get(0).folder;
        }
    }

    private LauncherWorlds.World selectedWorld()
    {
        return this.worlds.get(this.selected);
    }

    private void select(String folder, boolean react)
    {
        if (folder == null || folder.equals(this.selected))
        {
            return;
        }

        this.selected = folder;
        this.selectedAt = System.currentTimeMillis();
        this.inspTarget = 0;
        this.tagInput.setText("");
        this.prefs.selected = folder;
        this.prefs.save();

        if (react)
        {
            this.wenxu.react("select");
        }
    }

    private void setFilter(String filter)
    {
        if (filter.equals("servers") || filter.equals("lan"))
        {
            this.setViewServers(filter);

            return;
        }

        this.view = VIEW_SAVES;
        this.filter = filter;
        this.scrollTarget = 0;
        this.servers.close();
    }

    private void setViewServers(String filter)
    {
        class_310 mc = class_310.method_1551();

        if (!mc.method_29043())
        {
            this.showToast(tr("multiplayer.disabled"));

            return;
        }

        if (!mc.field_1690.field_21840)
        {
            /* Vanilla's first-time warning; it opens the server list (= here) when accepted */
            mc.method_1507(new class_4749(this.parent()));

            return;
        }

        boolean changed = !this.view.equals(VIEW_SERVERS);

        this.view = VIEW_SERVERS;
        this.filter = filter;
        this.scrollTarget = 0;
        this.servers.open();

        if (changed)
        {
            this.wenxu.react("mp");
        }
    }

    /* Palette */

    private void palette()
    {
        this.dark = this.prefs.theme.equals("dark") || (!this.prefs.theme.equals("light") && !Gfx.light());

        if (this.dark)
        {
            this.paper = 0xff231a26;
            this.side = 0xff1b141e;
            this.card = 0xff30263a;
            this.stroke = 0xff0d080e;
            this.text = 0xfff6edf2;
            this.muted = 0xffb49dac;
            this.rasp = 0xffff2b7f;
            this.raspSoft = 0xff4b2038;
            this.mint = 0xff2b5447;
            this.mintBg = 0xff1b2723;
            this.butter = 0xffe8c13a;
            this.plate = 0xff0d080e;
            this.dot = 0x1aff2b7f;
            this.shade = 0xff7a2252;
            this.ground = 0x66000000;
        }
        else
        {
            this.paper = 0xfffdfded;
            this.side = 0xfff7f5e3;
            this.card = 0xffffffff;
            this.stroke = 0xff3a1d33;
            this.text = 0xff3a1d33;
            this.muted = 0xff8b6f80;
            this.rasp = 0xffff1b75;
            this.raspSoft = 0xffffe0ec;
            this.mint = 0xffc9f0e1;
            this.mintBg = 0xffe9f7ef;
            this.butter = 0xffffd84a;
            this.plate = 0xff3a1d33;
            this.dot = 0x22ff1b75;
            this.shade = 0xffeaa6c0;
            this.ground = 0x333a1d33;
        }
    }

    /* Animation helpers */

    private float anim(String key, float target, float speed)
    {
        float value = this.anims.getOrDefault(key, target);

        value = Gfx.approach(value, target, speed, this.dt);
        this.anims.put(key, value);

        return value;
    }

    private float hover(String key)
    {
        boolean on = key.equals(this.hot) && (this.dlgAction == null || key.startsWith("dlg-"));

        return this.anim("h:" + key, on ? 1F : 0F, 18F);
    }

    /* Hits */

    private Hit hit(String key, float x, float y, float w, float h)
    {
        Hit hit = new Hit(key, x, y, w, h);

        this.hits.add(hit);

        return hit;
    }

    private Hit hitAt(float px, float py)
    {
        for (int i = this.lastHits.size() - 1; i >= 0; i--)
        {
            Hit hit = this.lastHits.get(i);

            if (hit.contains(px, py))
            {
                return hit;
            }
        }

        return null;
    }

    /* Drawing helpers */

    private void comic(float x, float y, float w, float h, float r, int fill, int border, int shadowColor, float shadow)
    {
        if (shadow > 0)
        {
            Gfx.roundRect(this.b, x + shadow, y + shadow, w, h, r, shadowColor);
        }

        Gfx.roundRect(this.b, x, y, w, h, r, border);
        Gfx.roundRect(this.b, x + 1.5F, y + 1.5F, w - 3, h - 3, Math.max(0, r - 1.5F), fill);
    }

    private void txt(String s, float x, float y, int color)
    {
        Gfx.text(this.b, s, x, y, color);
    }

    private void txt(String s, float x, float y, float scale, int color)
    {
        Gfx.text(this.b, s, x, y, scale, color);
    }

    private void bold(String s, float x, float y, float scale, int color)
    {
        Gfx.text(this.b, s, x, y, scale, color);
        Gfx.text(this.b, s, x + 0.45F, y, scale, color);
    }

    private int width(String s)
    {
        return Gfx.width(this.b, s);
    }

    private String fit(String s, float w)
    {
        return Gfx.fit(this.b, s, (int) Math.max(4, w));
    }

    private void push()
    {
        this.b.getContext().method_51448().method_22903();
    }

    private void pop()
    {
        this.b.getContext().method_51448().method_22909();
    }

    private void rotateAround(float cx, float cy, float degrees, float scale)
    {
        class_4587 stack = this.b.getContext().method_51448();

        stack.method_46416(cx, cy, 0);
        stack.method_22907(new Quaternionf().rotateZ((float) Math.toRadians(degrees)));
        stack.method_22905(scale, scale, 1);
        stack.method_46416(-cx, -cy, 0);
    }

    /** A sticker button; kind: 0 plain, 1 primary, 2 danger, 3 quiet (no shadow). */
    private Hit button(String key, float x, float y, float w, float h, String label, Icon icon, int kind, Runnable action)
    {
        float hv = this.hover(key);
        boolean disabled = action == null;
        float lift = disabled ? 0 : hv * 1.2F;
        int fill = kind == 1 ? this.rasp : kind == 2 ? 0xffff5d5d : this.card;
        int fg = kind == 1 || kind == 2 ? 0xffffffff : this.text;

        if (disabled)
        {
            fill = Gfx.lerp(fill, this.side, 0.55F);
            fg = Gfx.mulA(fg, 0.45F);
        }

        this.comic(x - lift, y - lift, w, h, 6, fill, this.stroke, this.stroke, kind == 3 ? 0 : 2 + lift);

        float lw = this.width(label) + (icon != null ? 13 : 0);
        float lx = x - lift + (w - lw) / 2F;

        if (icon != null)
        {
            Gfx.icon(this.b, icon, lx + 5, y - lift + h / 2F, 1F, fg);
            lx += 13;
        }

        this.txt(label, lx, y - lift + (h - 8) / 2F, fg);

        Hit hit = this.hit(key, x, y, w, h);

        if (!disabled)
        {
            hit.click = () ->
            {
                sound();
                action.run();
            };
        }

        return hit;
    }

    private float chip(String label, float x, float y, int fill, int fg, boolean dashed)
    {
        float w = this.width(label) + 8;

        Gfx.roundRect(this.b, x, y, w, 11, 4, dashed ? this.muted : this.stroke);
        Gfx.roundRect(this.b, x + 1, y + 1, w - 2, 9, 3, fill);
        this.txt(label, x + 4, y + 1.5F, fg);

        return w;
    }

    /** A GL texture in a rounded rect, cropped to the box's aspect (cover, centred). */
    private void picture(int texture, float tw, float th, float x, float y, float w, float h, float r, int tint)
    {
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;

        if (tw > 0 && th > 0)
        {
            float boxA = w / h, texA = tw / th;

            if (texA > boxA)
            {
                float k = boxA / texA;

                u0 = (1 - k) / 2;
                u1 = 1 - u0;
            }
            else
            {
                float k = texA / boxA;

                v0 = (1 - k) / 2;
                v1 = 1 - v0;
            }
        }

        Gfx.imageRound(this.b, texture, x, y, w, h, r, u0, v0, u1, v1, tint);
    }

    private void placeholder(float x, float y, float w, float h, float r, String name)
    {
        int seed = name.hashCode();
        float hue = ((seed >>> 8) & 0xff) / 255F;

        Gfx.roundRectV(this.b, x, y, w, h, r, Gfx.hsv(hue, 0.35F, this.dark ? 0.45F : 0.9F), Gfx.hsv((hue + 0.08F) % 1, 0.45F, this.dark ? 0.3F : 0.75F));

        String letter = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1));

        this.bold(letter, x + w / 2F - this.width(letter), y + h / 2F - 8, 2F, 0xccffffff);
    }

    /** The halftone dots behind everything: one quad over a small repeating texture. */
    private void halftone(float x, float y, float w, float h, float drift)
    {
        if (dotTexture == null)
        {
            net.minecraft.class_1011 image = new net.minecraft.class_1011(16, 16, true);

            for (int py = 0; py < 16; py++)
            {
                for (int px = 0; px < 16; px++)
                {
                    float d = (float) Math.hypot(px + 0.5F - 8, py + 0.5F - 8);
                    int a = (int) (255 * Math.max(0, Math.min(1, 2.6F - d)));

                    image.method_4305(px, py, a << 24 | 0xffffff);
                }
            }

            dotTexture = new class_1043(image);
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(dotTexture.method_4624());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }

        float tile = 6F;
        float u0 = (x - drift) / tile, v0 = (y - drift) / tile, u1 = u0 + w / tile, v1 = v0 + h / tile;

        this.b.flush();

        Matrix4f m = this.b.getContext().method_51448().method_23760().method_23761();
        class_287 builder = class_289.method_1348().method_1349();

        builder.method_1328(class_293.class_5596.field_27379, class_290.field_1575);
        builder.method_22918(m, x, y, 0).method_22913(u0, v0).method_39415(this.dot).method_1344();
        builder.method_22918(m, x, y + h, 0).method_22913(u0, v1).method_39415(this.dot).method_1344();
        builder.method_22918(m, x + w, y + h, 0).method_22913(u1, v1).method_39415(this.dot).method_1344();
        builder.method_22918(m, x, y, 0).method_22913(u0, v0).method_39415(this.dot).method_1344();
        builder.method_22918(m, x + w, y + h, 0).method_22913(u1, v1).method_39415(this.dot).method_1344();
        builder.method_22918(m, x + w, y, 0).method_22913(u1, v0).method_39415(this.dot).method_1344();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(class_757::method_34543);
        RenderSystem.setShaderTexture(0, dotTexture.method_4624());
        class_286.method_43433(builder.method_1326());
    }

    private String ago(long time)
    {
        long ms = System.currentTimeMillis() - time;

        if (time <= 0) return tr("time.never");
        if (ms < 60_000L) return tr("time.now");
        if (ms < 3_600_000L) return tr("time.minutes", ms / 60_000L);
        if (ms < 86_400_000L) return tr("time.hours", ms / 3_600_000L);

        LocalDate day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();

        if (day.equals(today.minusDays(1))) return tr("time.yesterday");
        if (ms < 7 * 86_400_000L) return tr("time.days", ms / 86_400_000L);
        if (day.getYear() == today.getYear()) return tr("time.date", day.getMonthValue(), day.getDayOfMonth());

        return tr("time.year", day.getYear(), day.getMonthValue());
    }

    private String full(long time)
    {
        if (time <= 0)
        {
            return tr("time.never");
        }

        LocalDateTime at = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime();

        return tr("time.full", at.getYear(), at.getMonthValue(), at.getDayOfMonth(), String.format("%02d:%02d", at.getHour(), at.getMinute()));
    }

    private static String size(long bytes)
    {
        if (bytes < 0) return "…";
        if (bytes < 1024L * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.0f MB", bytes / 1048576.0);

        return String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0);
    }

    private String modeName(LauncherWorlds.World w)
    {
        if (w.hardcore) return tr("mode.hardcore");
        if (w.mode == class_1934.field_9215) return tr("mode.survival");
        if (w.mode == class_1934.field_9216) return tr("mode.adventure");
        if (w.mode == class_1934.field_9219) return tr("mode.spectator");

        return tr("mode.creative");
    }

    /* Render */

    @Override
    public void render(UIContext context)
    {
        long now = System.nanoTime();

        this.dt = this.lastFrame == 0 ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1_000_000_000F);
        this.lastFrame = now;
        this.time += this.dt;
        this.b = context.batcher;
        this.ctx = context;

        this.palette();

        if (this.view.equals(VIEW_SERVERS))
        {
            this.servers.tick();
        }

        if (context.mouseX != this.lastMx || context.mouseY != this.lastMy)
        {
            if (this.lastMx >= 0)
            {
                this.wenxu.activity();
            }

            this.lastMx = context.mouseX;
            this.lastMy = context.mouseY;
        }

        List<LauncherWorlds.World> all = this.worlds.worlds();

        if (this.worlds.version() != this.seenWorlds)
        {
            this.seenWorlds = this.worlds.version();

            if (this.worlds.isEmpty() && !this.worlds.isLoading() && this.view.equals(VIEW_SAVES))
            {
                /* No saves at all: vanilla goes straight to creating one; offer it */
                this.showToast(tr("worlds.none"));
            }
        }

        this.rebuild(all);

        /* Layout */
        this.W = this.area.w;
        this.H = this.area.h;
        this.SW = Math.max(132, Math.min(176, this.W * 0.17F));
        this.IW = Math.max(176, Math.min(236, this.W * 0.23F));

        if (this.W - this.SW - this.IW < 230)
        {
            this.IW = Math.max(150, this.W - this.SW - 230);
        }

        this.MX = this.SW;
        this.MW = this.W - this.SW - this.IW;
        this.utilTop = this.H - 26;
        this.charTop = this.utilTop - Math.max(104, Math.min(150, this.H * 0.27F));

        boolean blocked = this.dlgAction != null;

        /* Clicking elsewhere keeps the new name, like renaming a file */
        if (this.renaming != null && !this.nameInput.isFocused())
        {
            this.finishRename(true);
        }

        Hit under = this.area.isInside(context) ? this.hitAt(context.mouseX, context.mouseY) : null;
        String hotNow = under == null ? null : under.key;

        if (hotNow == null ? this.hotSince != null : !hotNow.equals(this.hotSince))
        {
            this.hotSince = hotNow;
            this.hotAt = System.currentTimeMillis();
        }

        this.hot = hotNow;
        this.hits.clear();

        /* Ground */
        this.b.box(0, 0, this.W, this.H, this.paper);
        this.halftone(0, 0, this.W, this.H, (this.time * 0.7F) % 6F);

        this.nameBox = null;
        this.renderSidebar(context);

        if (this.view.equals(VIEW_SERVERS))
        {
            this.renderServers(context);
            this.renderServerInspector(context);
        }
        else
        {
            this.renderWorlds(context);
            this.renderInspector(context);
        }

        this.renderWenxu(context);

        /* Text fields sit on the pills drawn above */
        this.search.setVisible(!blocked && this.view.equals(VIEW_SAVES));
        this.address.setVisible(!blocked && this.view.equals(VIEW_SERVERS));
        this.tagInput.setVisible(!blocked && this.view.equals(VIEW_SAVES) && this.selectedWorld() != null && this.tagBox != null);
        this.nameInput.setVisible(!blocked && this.view.equals(VIEW_SAVES) && this.renaming != null && this.nameBox != null);

        super.render(context);

        this.renderMenu(context);
        this.renderDialog(context);
        this.renderToast();
        this.renderTooltip(context, under);

        List<Hit> swap = this.lastHits;

        this.lastHits = new ArrayList<>(this.hits);
        swap.clear();
    }

    private void place(UITextbox box, float x, float y, float w, float h)
    {
        int ix = Math.round(x), iy = Math.round(y), iw = Math.round(w), ih = Math.round(h);

        if (box.area.x != ix || box.area.y != iy || box.area.w != iw || box.area.h != ih)
        {
            box.relative(this).set(ix, iy, iw, ih);
            box.resize();
        }
    }

    /* Sidebar */

    private void renderSidebar(UIContext context)
    {
        float sw = this.SW;

        this.b.box(0, 0, sw, this.H, this.side);
        this.b.box(sw - 2, 0, sw, this.H, this.stroke);

        /* Brand plate, tilted, with the splash under its corner */
        float px = 9, py = 10, pw = sw - 22, ph = 38;

        this.push();
        this.rotateAround(px + pw / 2, py + ph / 2, -1.5F, 1F);
        this.comic(px, py, pw, ph, 8, this.plate, this.stroke, this.rasp, 2.5F);

        float lh = 28, lw = lh * 168F / 236F;

        Gfx.image(this.b, LOGO, px + 7, py + 5, lw, lh, 0xffffffff);
        this.bold("BBS-Rabbit", px + 12 + lw, py + 8, 1.15F, 0xfffdfded);
        this.txt(tr("brand.sub"), px + 12 + lw, py + 22, this.rasp);
        this.pop();

        float pulse = 1F + 0.05F * (float) Math.sin(this.time * 7F);
        float spW = this.width(this.splash);
        float spX = sw - 14 - spW * 0.9F, spY = py + ph + 4;

        this.push();
        this.rotateAround(spX + spW * 0.45F, spY + 4, -12F, pulse * 0.9F);

        for (int i = 0; i < 4; i++)
        {
            this.txt(this.splash, spX + (i % 2 == 0 ? -0.7F : 0.7F), spY + (i < 2 ? -0.7F : 0.7F), 0xff3f3a12);
        }

        this.txt(this.splash, spX, spY, 0xffffe14d);
        this.pop();

        /* Navigation */
        float top = py + ph + 16, bottom = this.charTop + 26;
        float x = 8, w = sw - 18;

        this.navTop = top;
        this.navBottom = bottom;

        this.navScroll += (this.navTarget - this.navScroll) * Math.min(1, this.dt * 14);
        this.b.clip((int) 0, (int) top, (int) sw - 2, (int) (bottom - top), context);

        float y = top - this.navScroll;
        List<LauncherWorlds.World> all = this.worlds.worlds();

        y = this.section(tr("nav.singleplayer"), x, y, w);
        y = this.navItem("all", Icons.LIST, tr("nav.all"), all.size(), x, y, w, false);
        y = this.navItem("recent", Icons.TIME, tr("nav.recent"), (int) all.stream().filter(this::recent).count(), x, y, w, false);
        y = this.navItem("fav", Icons.HEART, tr("nav.fav"), (int) all.stream().filter(this::isFavorite).count(), x, y, w, false);
        y = this.navItem("films", Icons.FILM, tr("nav.films"), (int) all.stream().filter((wd) -> !wd.films.isEmpty()).count(), x, y, w, false);

        Map<String, Integer> tags = this.tagIndex(all);

        if (!tags.isEmpty())
        {
            y = this.section(tr("nav.tags"), x, y, w);

            for (Map.Entry<String, Integer> tag : tags.entrySet())
            {
                y = this.navItem("tag:" + tag.getKey(), null, "# " + tag.getKey(), tag.getValue(), x, y, w, false);
            }
        }

        y = this.section(tr("nav.multiplayer"), x, y, w);
        y = this.navItem("servers", Icons.SERVER, tr("nav.servers"), this.servers.isOpen() ? this.servers.servers().size() : -1, x, y, w, false);
        y = this.navItem("lan", Icons.GLOBE, tr("nav.lan"), this.servers.isOpen() ? this.servers.lan().size() : -1, x, y, w, this.servers.isOpen() && !this.servers.lan().isEmpty());
        y = this.navItem("realms", Icons.BOOKMARK, "Realms", -1, x, y, w, false);

        this.b.unclip(context);
        this.navContentH = y + this.navScroll - top + 6;
        this.navTarget = Math.max(0, Math.min(this.navTarget, this.navContentH - (bottom - top)));
        this.hit("nav-area", 0, top, sw, bottom - top);
        this.moveToBack("nav-area");

        /* Vanilla title buttons */
        this.b.box(0, this.utilTop, sw - 2, this.H, this.card);
        this.b.box(0, this.utilTop, sw - 2, this.utilTop + 2, this.stroke);

        List<Object[]> util = new ArrayList<>();
        class_310 mc = class_310.method_1551();

        if (FabricLoader.getInstance().isModLoaded("modmenu"))
        {
            util.add(new Object[] {Icons.BLOCK, tr("util.mods"), (Runnable) this::openMods});
        }

        util.add(new Object[] {Icons.GEAR, tr("util.options"), (Runnable) () -> mc.method_1507(new class_429(this.parent(), mc.field_1690))});
        util.add(new Object[] {Icons.GLOBE, tr("util.language"), (Runnable) () -> mc.method_1507(new class_426(this.parent(), mc.field_1690, mc.method_1526()))});
        util.add(new Object[] {Icons.USER, tr("util.accessibility"), (Runnable) () -> mc.method_1507(new class_4189(this.parent(), mc.field_1690))});
        util.add(new Object[] {Icons.EXTERNAL, tr("util.quit"), (Runnable) mc::method_1592});

        float uw = (sw - 2) / util.size();

        for (int i = 0; i < util.size(); i++)
        {
            Object[] u = util.get(i);
            float ux = i * uw;
            String key = "util" + i;
            float hv = this.hover(key);

            if (hv > 0.01F)
            {
                this.b.box(ux, this.utilTop + 2, ux + uw, this.H, Gfx.mulA(this.raspSoft, hv));
            }

            int fg = Gfx.lerp(this.muted, this.rasp, hv);

            Gfx.icon(this.b, (Icon) u[0], ux + uw / 2F, this.utilTop + 9, 1F, fg);

            String label = this.fit((String) u[1], uw - 2);

            this.txt(label, ux + (uw - this.width(label)) / 2F, this.utilTop + 16, fg);

            Hit hit = this.hit(key, ux, this.utilTop, uw, this.H - this.utilTop);
            Runnable action = (Runnable) u[2];

            hit.click = () ->
            {
                sound();
                action.run();
            };
            hit.tooltip = (String) u[1];
        }
    }

    private void openMods()
    {
        try
        {
            Class<?> screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");

            class_310.method_1551().method_1507((class_437) screen.getConstructor(class_437.class).newInstance(this.parent()));
        }
        catch (Throwable e)
        {
            this.showToast(tr("util.mods_missing"));
        }
    }

    private float section(String label, float x, float y, float w)
    {
        y += 6;
        this.txt(label, x + 2, y, this.muted);

        float lx = x + 6 + this.width(label);

        for (float dx = lx; dx < x + w - 2; dx += 4)
        {
            this.b.box(dx, y + 4, dx + 2, y + 5, Gfx.mulA(this.muted, 0.45F));
        }

        return y + 12;
    }

    private float navItem(String key, Icon icon, String label, int count, float x, float y, float w, boolean live)
    {
        String tag = key.startsWith("tag:") ? key.substring(4) : null;

        if (tag != null && this.editing("tag", tag))
        {
            float h = 15;

            Gfx.roundRect(this.b, x, y, w, h, 5, this.card);
            Gfx.roundOutline(this.b, x, y, w, h, 5, 1.5F, this.rasp);
            this.txt("#", x + 6, y + 4, this.rasp);
            this.nameInput.setColor(this.text, false);
            this.place(this.nameInput, x + 6 + this.width("# "), y, w - 10 - this.width("# "), h);
            this.nameBox = this.nameInput;

            /* Keep the tag being renamed in view (it may have been picked from the inspector) */
            if (y < this.navTop) this.navTarget -= this.navTop - y;
            if (y + h > this.navBottom) this.navTarget += y + h - this.navBottom;

            return y + h + 1;
        }

        boolean active = this.filter.equals(key) || (key.equals("servers") && this.view.equals(VIEW_SERVERS) && this.filter.equals("servers"));
        float hv = this.hover("nav:" + key);
        float sel = this.anim("navsel:" + key, active ? 1F : 0F, 16F);
        float h = 15;
        float shift = hv * 2;

        if (sel > 0.01F)
        {
            this.comic(x, y, w, h, 5, Gfx.lerp(this.side, this.rasp, sel), Gfx.mulA(this.stroke, sel), Gfx.mulA(this.stroke, sel), 2 * sel);
        }
        else if (hv > 0.01F)
        {
            Gfx.roundRect(this.b, x, y, w, h, 5, Gfx.mulA(this.raspSoft, hv));
        }

        int fg = Gfx.lerp(this.text, 0xffffffff, sel);
        int accent = Gfx.lerp(this.rasp, 0xffffffff, sel);

        if (icon != null)
        {
            Gfx.icon(this.b, icon, x + 9 + shift, y + h / 2F, 0.85F, accent);
        }

        float tx = x + (icon != null ? 18 : 6) + shift;
        String shown = label;

        if (icon == null && label.startsWith("# "))
        {
            this.txt("#", tx, y + 4, accent);
            tx += this.width("# ");
            shown = label.substring(2);
        }

        String countText = count >= 0 ? String.valueOf(count) : "";
        float cw = this.width(countText);

        this.txt(this.fit(shown, w - (tx - x) - cw - 8 - (live ? 8 : 0)), tx, y + 4, fg);

        if (live)
        {
            float pulse = 0.5F + 0.5F * (float) Math.sin(this.time * 4);

            Gfx.circle(this.b, x + w - cw - 10, y + h / 2F, 2.5F + pulse, Gfx.alpha(0x2fcf8a, 0.3F * (1 - pulse)));
            Gfx.circle(this.b, x + w - cw - 10, y + h / 2F, 2.2F, 0xff2fcf8a);
        }

        this.txt(countText, x + w - cw - 4, y + 4, Gfx.lerp(this.muted, 0xffffffff, sel));

        Hit hit = this.hit("nav:" + key, x, y, w, h);

        hit.click = () ->
        {
            sound();

            if (key.equals("realms"))
            {
                class_310 mc = class_310.method_1551();

                if (mc.method_29043())
                {
                    mc.method_1507(new class_4325(this.parent()));
                }
                else
                {
                    this.showToast(tr("multiplayer.disabled"));
                }

                return;
            }

            this.setFilter(key);
        };

        if (tag != null)
        {
            hit.right = () -> this.tagMenu(tag, this.ctx.mouseX, this.ctx.mouseY);
        }

        return y + h + 1;
    }

    /* Worlds */

    private void renderToolbar(UIContext context, boolean servers)
    {
        float x = this.MX + 12, y = 10, h = 18;
        float right = this.MX + this.MW - 10;
        float bw1 = servers ? this.width(tr("server.add")) + 16 : this.width(tr("sort." + this.prefs.sort) + " ▾") + 14;
        float bw2 = servers ? this.width(tr("server.refresh")) + 26 : this.width(tr("world.new")) + 28;
        float lamp = 20;
        float sw = right - x - bw1 - bw2 - lamp - 18;
        UITextbox box = servers ? this.address : this.search;
        boolean focus = box.isFocused();
        float f = this.anim(servers ? "addr-focus" : "search-focus", focus ? 1F : 0F, 14F);

        this.comic(x, y, sw, h, 8, this.card, Gfx.lerp(this.stroke, this.rasp, f), this.stroke, 2);
        Gfx.icon(this.b, servers ? Icons.LINK : Icons.SEARCH, x + 10, y + h / 2F, 1F, Gfx.lerp(this.muted, this.rasp, f));
        this.place(box, x + 18, y + 1, sw - (servers ? 22 : 40), h - 2);
        this.hit(servers ? "address" : "search", x, y, sw, h).click = () -> context.focus(box);

        if (!servers)
        {
            String kbd = "Ctrl F";

            if (this.search.getText().isEmpty())
            {
                float kw = this.width(kbd) + 6;

                Gfx.roundOutline(this.b, x + sw - kw - 6, y + 4, kw, 10, 3, 1, Gfx.mulA(this.muted, 0.6F));
                this.txt(kbd, x + sw - kw - 3, y + 5, this.muted);
            }
            else
            {
                this.hit("search-clear", x + sw - 16, y + 2, 14, 14).click = () -> this.search.setText("");
                Gfx.icon(this.b, Icons.CLOSE, x + sw - 9, y + h / 2F, 0.8F, this.muted);
            }
        }

        float bx = x + sw + 6;
        float lx = right - lamp;
        Hit lampHit = this.button("lamp", lx, y, lamp, h, "", this.dark ? Icons.SUN : Icons.LIGHT, 0, () -> {});

        lampHit.click = () ->
        {
            sound();
            this.prefs.theme = this.dark ? "light" : "dark";
            this.prefs.save();
            this.wenxu.react("theme");
        };
        lampHit.right = () ->
        {
            this.prefs.theme = "auto";
            this.prefs.save();
            this.showToast(tr("theme.auto"));
        };
        lampHit.tooltip = tr("theme.tooltip");

        if (servers)
        {
            this.button("addsv", bx, y, bw1, h, tr("server.add"), null, 0, this::addServer);
            this.button("refresh", bx + bw1 + 6, y, bw2, h, tr("server.refresh"), Icons.REFRESH, 0, () -> this.servers.refresh());
        }
        else
        {
            Hit sort = this.button("sort", bx, y, bw1, h, tr("sort." + this.prefs.sort) + " ▾", null, 0, () -> {});

            sort.click = () ->
            {
                sound();

                List<MenuItem> items = new ArrayList<>();

                for (String s : SORTS)
                {
                    items.add(new MenuItem(tr("sort." + s), () ->
                    {
                        this.prefs.sort = s;
                        this.prefs.save();
                    }, s.equals(this.prefs.sort), false));
                }

                this.openMenu(items, bx, y + h + 4);
            };

            Hit create = this.button("new", bx + bw1 + 6, y, bw2, h, tr("world.new"), Icons.ADD, 1, () -> {});
            float cx = bx + bw1 + 6;

            create.click = () ->
            {
                sound();

                List<MenuItem> items = new ArrayList<>();

                items.add(new MenuItem(tr("world.new.vanilla"), () -> WorldActions.create(this.parent(), null), false, false));
                items.add(new MenuItem(null, null, false, false));
                items.add(new MenuItem(tr("world.new.flat"), () -> WorldActions.create(this.parent(), "flat"), false, false));
                this.openMenu(items, cx + bw2 - 170, y + h + 4);
            };
        }
    }

    private void heading(String title, int count, String hint, float y)
    {
        float x = this.MX + 14;

        this.bold(title, x, y, 1.45F, this.text);

        float tw = this.width(title) * 1.45F + 8;
        String c = String.valueOf(count);
        float cw = this.width(c) + 10;

        Gfx.roundRect(this.b, x + tw, y + 1, cw, 12, 4, this.stroke);
        Gfx.roundRect(this.b, x + tw + 1.5F, y + 2.5F, cw - 3, 9, 3, this.butter);
        this.txt(c, x + tw + 5, y + 3, 0xff3a1d33);
        this.txt(hint, x + tw + cw + 8, y + 4, this.muted);
    }

    private void renderWorlds(UIContext context)
    {
        this.renderToolbar(context, false);

        String title = switch (this.filter)
        {
            case "recent" -> tr("nav.recent");
            case "fav" -> tr("nav.fav");
            case "films" -> tr("nav.films");
            default -> this.filter.startsWith("tag:") ? "#" + this.filter.substring(4) : tr("nav.all");
        };

        this.heading(title, this.visible.size(), tr("worlds.hint"), 38);

        float top = 58, bottom = this.H - 18;
        float x = this.MX + 12, w = this.MW - 20;
        float gap = 10;
        int cols = Math.max(1, (int) ((w + gap) / (118 + gap)));
        float cw = (w - gap * (cols - 1)) / cols;
        float thumbH = cw * 9F / 16F;
        float ch = thumbH + 54;

        this.gridCols = cols;
        this.scroll += (this.scrollTarget - this.scroll) * Math.min(1, this.dt * 14);
        this.b.clip((int) this.MX, (int) top - 6, (int) this.MW - 2, (int) (bottom - top + 6), context);
        this.hit("grid", this.MX, top, this.MW, bottom - top);

        if (this.worlds.isLoading() && this.visible.isEmpty())
        {
            String msg = tr("worlds.loading");

            this.txt(msg, x + (w - this.width(msg)) / 2F, top + 40, this.muted);
        }
        else if (this.visible.isEmpty())
        {
            String a = this.worlds.worlds().isEmpty() ? tr("worlds.none") : tr("worlds.empty");
            String c = this.worlds.worlds().isEmpty() ? tr("worlds.none_hint") : tr("worlds.empty_hint");

            this.bold(a, x + (w - this.width(a) * 1.2F) / 2F, top + 40, 1.2F, this.text);
            this.txt(c, x + (w - this.width(c)) / 2F, top + 56, this.muted);
        }

        long since = System.currentTimeMillis() - this.listChangedAt;

        for (int i = 0; i < this.visible.size(); i++)
        {
            LauncherWorlds.World world = this.visible.get(i);
            int col = i % cols, row = i / cols;
            float cx = x + col * (cw + gap), cy = top + 4 + row * (ch + gap + 2) - this.scroll;

            if (cy > bottom + 10 || cy + ch < top - 10)
            {
                continue;
            }

            float pop = Gfx.clamp01((since - i * 35) / 380F);

            this.card(world, cx, cy, cw, ch, thumbH, pop < 1 ? Gfx.easeOutBack(pop) : 1F, top, bottom);
        }

        this.b.unclip(context);

        int rows = (this.visible.size() + cols - 1) / cols;

        this.gridContentH = rows * (ch + gap + 2) + 10;
        this.scrollTarget = Math.max(0, Math.min(this.scrollTarget, this.gridContentH - (bottom - top)));

        if (this.scrollToSelected)
        {
            this.scrollToSelected = false;

            int index = this.indexOfSelected();

            if (index >= 0)
            {
                float rowY = (index / cols) * (ch + gap + 2);

                if (rowY < this.scrollTarget) this.scrollTarget = rowY;
                if (rowY + ch > this.scrollTarget + (bottom - top)) this.scrollTarget = rowY + ch - (bottom - top) + 8;
            }
        }

        /* Status line: totals, version, vanilla's copyright line */
        long total = 0;

        for (LauncherWorlds.World w2 : this.worlds.worlds())
        {
            total += Math.max(0, w2.size);
        }

        this.b.box(this.MX + 10, this.H - 16, this.MX + this.MW - 10, this.H - 15, Gfx.mulA(this.muted, 0.3F));
        this.txt(tr("status.worlds", this.worlds.worlds().size(), size(total)), this.MX + 14, this.H - 11, this.muted);
        this.renderVersionLine();
    }

    private boolean scrollToSelected;

    private int indexOfSelected()
    {
        for (int i = 0; i < this.visible.size(); i++)
        {
            if (this.visible.get(i).folder.equals(this.selected))
            {
                return i;
            }
        }

        return -1;
    }

    private void renderVersionLine()
    {
        String version = "Minecraft " + net.minecraft.class_155.method_16673().method_48019() + " · BBS-Rabbit " + FabricLoader.getInstance().getModContainer("bbs_ui_rabbit").map((m) -> m.getMetadata().getVersion().getFriendlyString().replace("+bbsfs2.7", "")).orElse("");
        float rw = this.width(COPYRIGHT);
        float rx = this.MX + this.MW - 14 - rw;
        float hv = this.hover("copyright");

        this.txt(COPYRIGHT, rx, this.H - 11, Gfx.lerp(this.muted, this.rasp, hv));
        this.txt(version, rx - 10 - this.width(version), this.H - 11, this.muted);
        this.hit("copyright", rx, this.H - 14, rw, 12).click = () ->
        {
            class_437 parent = this.parent();

            class_310.method_1551().method_1507(new class_445(false, () -> class_310.method_1551().method_1507(parent)));
        };
    }

    private void card(LauncherWorlds.World w, float x, float y, float cw, float ch, float thumbH, float pop, float clipTop, float clipBottom)
    {
        String key = "w:" + w.folder;
        boolean sel = w.folder.equals(this.selected);
        float hv = this.hover(key);
        float s = this.anim("sel:" + w.folder, sel ? 1F : 0F, 14F);
        float lift = Math.max(hv * 2, s * 2);
        float tiltDeg = -(hv * 0.8F + s * 0.9F);

        this.push();
        this.rotateAround(x + cw / 2, y + ch / 2, tiltDeg, 0.88F + 0.12F * pop);

        int border = Gfx.lerp(this.stroke, this.rasp, s);

        this.comic(x - lift, y - lift, cw, ch, 9, this.card, border, Gfx.lerp(this.stroke, this.rasp, s), 2.5F + lift);

        float px = x - lift + 3.5F, py = y - lift + 3.5F, pw = cw - 7, phh = thumbH - 4;
        float[] pic = this.coverOf(w);

        if (pic != null)
        {
            this.picture((int) pic[0], pic[1], pic[2], px, py, pw, phh, 6, 0xffffffff);
        }
        else
        {
            this.placeholder(px, py, pw, phh, 6, this.display(w));
        }

        /* Badges on the picture */
        boolean fav = this.isFavorite(w);
        float fh = this.hover("fav:" + w.folder);

        if (fav || hv > 0.05F || fh > 0.05F)
        {
            float a = fav ? 1F : Math.max(hv, fh) * 0.9F;
            float hx = px + pw - 9, hy = py + 7;

            Gfx.circle(this.b, hx, hy, 6.5F + fh, Gfx.mulA(this.stroke, a));
            Gfx.circle(this.b, hx, hy, 5F + fh, Gfx.mulA(fav ? this.rasp : this.card, a));
            Gfx.icon(this.b, Icons.HEART, hx, hy, 0.65F, Gfx.mulA(fav ? 0xffffffff : this.muted, a));
        }

        if (!w.films.isEmpty())
        {
            String label = tr("world.films", w.films.size());
            float lw = this.width(label) + 8;

            Gfx.roundRect(this.b, px + pw - lw - 3, py + phh - 14, lw, 11, 4, this.stroke);
            Gfx.roundRect(this.b, px + pw - lw - 2, py + phh - 13, lw - 2, 9, 3, this.rasp);
            this.txt(label, px + pw - lw + 1, py + phh - 12, 0xffffffff);
        }

        String warn = w.locked ? tr("world.locked") : w.needsBackup ? tr("world.old") : w.future ? tr("world.future") : w.experimental ? tr("world.experimental") : null;

        if (warn != null)
        {
            float lw = this.width(warn) + 8;

            Gfx.roundRect(this.b, px + 3, py + phh - 14, lw, 11, 4, this.stroke);
            Gfx.roundRect(this.b, px + 4, py + phh - 13, lw - 2, 9, 3, this.butter);
            this.txt(warn, px + 7, py + phh - 12, 0xff3a1d33);
        }

        /* Text */
        float tx = x - lift + 7, ty = y - lift + thumbH + 3;

        this.bold(this.fit(this.display(w), cw - 14), tx, ty, 1F, this.text);
        /* The save folder under the name, as vanilla's list shows both */
        this.txt(this.fit(w.folder, cw - 14), tx, ty + 10, Gfx.mulA(this.muted, 0.75F));

        float mx = tx;
        String when = this.ago(w.lastPlayed);

        this.txt(when, mx, ty + 21, this.muted);
        mx += this.width(when) + 5;

        if (!w.isCreative())
        {
            mx += this.chip(this.modeName(w), mx, ty + 20, w.hardcore ? 0xffff8f8f : 0xffffe9a8, 0xff3a1d33, false) + 3;
        }

        float gx = tx;

        for (String[] tag : this.tagsOf(w))
        {
            float tw = this.width(tag[0]) + 8;

            if (gx + tw > x - lift + cw - 6)
            {
                break;
            }

            gx += this.chip(tag[0], gx, ty + 33, tag[1].equals("auto") ? this.card : this.mint, this.text, tag[1].equals("auto")) + 3;
        }

        this.pop();

        /* The pick sticker */
        if (s > 0.01F)
        {
            float pt = Gfx.clamp01((System.currentTimeMillis() - this.selectedAt) / 420F);
            float sc = sel ? Gfx.easeOutBack(pt) : s;
            String label = tr("world.picked");
            float lw = this.width(label) * 1.1F + 12, lh = 15;
            float sx = x + cw - lw + 8, sy = y - 9;

            this.push();
            this.rotateAround(sx + lw / 2, sy + lh / 2, 9F + 25F * (1 - sc), Math.max(0.05F, sc));
            this.comic(sx, sy, lw, lh, 5, this.butter, this.stroke, this.stroke, 1.5F);
            this.bold(label, sx + 6, sy + 4, 1.1F, 0xff3a1d33);
            this.pop();
        }

        /* Interaction (only where the card is inside the list) */
        float hy0 = Math.max(y, clipTop), hy1 = Math.min(y + ch, clipBottom);

        if (hy1 > hy0)
        {
            Hit card = this.hit(key, x, hy0, cw, hy1 - hy0);

            card.click = () -> this.select(w.folder, true);
            card.dbl = () -> this.play(w);
            card.right = () ->
            {
                this.select(w.folder, true);
                this.worldMenu(w, this.ctx.mouseX, this.ctx.mouseY);
            };

            float hx = px + pw - 16, hyy = py;

            if (hyy > clipTop && hyy + 14 < clipBottom)
            {
                Hit heart = this.hit("fav:" + w.folder, hx, hyy, 16, 14);

                heart.click = () -> this.toggleFavorite(w);
                heart.tooltip = tr(fav ? "world.unfav" : "world.fav");
            }
        }
    }

    private void toggleFavorite(LauncherWorlds.World w)
    {
        sound();

        boolean now = this.prefs.toggleFavorite(w.folder);

        if (now)
        {
            this.wenxu.react("fav");
        }

        this.showToast(now ? tr("toast.fav", this.display(w)) : tr("toast.unfav"));
    }

    private void play(LauncherWorlds.World w)
    {
        if (w.unavailable)
        {
            return;
        }

        if (w.locked)
        {
            this.showToast(tr("world.locked_hint"));

            return;
        }

        this.prefs.selected = w.folder;
        this.prefs.save();
        WorldActions.play(this.parent(), w.summary);
    }

    private void worldMenu(LauncherWorlds.World w, float mx, float my)
    {
        List<MenuItem> items = new ArrayList<>();

        items.add(new MenuItem(tr("world.play"), () -> this.play(w), false, false));
        items.add(new MenuItem(tr("world.rename"), () -> this.startRename(w), false, false));
        items.add(new MenuItem(tr(this.prefs.aliases.containsKey(w.folder) ? "world.alias_edit" : "world.alias"), () -> this.startAlias(w), false, false));
        items.add(new MenuItem(tr("world.edit"), () -> WorldActions.edit(this.parent(), w.summary, this.worlds::reload), false, false));
        items.add(new MenuItem(tr("world.backup"), () -> this.backup(w), false, false));
        items.add(new MenuItem(tr("world.folder"), () -> WorldActions.openFolder(w.dir()), false, false));
        items.add(new MenuItem(tr("cover.change") + "…", () -> this.coverMenu(w, mx, my), false, false));
        items.add(new MenuItem(null, null, false, false));
        items.add(new MenuItem(tr("world.recreate"), () -> WorldActions.recreate(this.parent(), w.summary), false, false));
        items.add(new MenuItem(tr("world.delete"), () -> this.confirmDelete(w), false, true));
        this.openMenu(items, mx, my);
    }

    private void backup(LauncherWorlds.World w)
    {
        if (WorldActions.backup(w.folder))
        {
            this.showToast(tr("toast.backup", this.display(w)));
        }
    }

    private void confirmDelete(LauncherWorlds.World w)
    {
        this.wenxu.react("dialog");
        this.openDialog(tr("delete.title"), tr("delete.text", this.display(w)), tr("delete.yes"), () ->
        {
            if (WorldActions.delete(w.folder))
            {
                this.prefs.forget(w.folder);
                this.selected = null;
                this.worlds.reload();
                this.showToast(tr("toast.deleted", this.display(w)));
                this.wenxu.react("deleted");
            }
        });
    }

    /** The name cards and lists show: the launcher's alias, or the world's own name. */
    private String display(LauncherWorlds.World w)
    {
        String alias = this.prefs.aliases.get(w.folder);

        return alias == null ? w.name : alias;
    }

    private boolean editing(String kind, String key)
    {
        return key.equals(this.renaming) && kind.equals(this.renameKind);
    }

    private void nameMenu(LauncherWorlds.World w, float mx, float my)
    {
        List<MenuItem> items = new ArrayList<>();
        boolean aliased = this.prefs.aliases.containsKey(w.folder);

        items.add(new MenuItem(tr("world.rename"), () -> this.startRename(w), false, false));
        items.add(new MenuItem(tr(aliased ? "world.alias_edit" : "world.alias"), () -> this.startAlias(w), false, false));

        if (aliased)
        {
            items.add(new MenuItem(tr("world.alias_clear"), () ->
            {
                this.prefs.setAlias(w.folder, null);
                this.showToast(tr("toast.alias_cleared", w.name));
            }, false, false));
        }

        this.openMenu(items, mx, my);
    }

    private void startRename(LauncherWorlds.World w)
    {
        if (w.locked || w.unavailable)
        {
            this.showToast(tr("world.locked_hint"));

            return;
        }

        this.select(w.folder, false);
        this.inspTarget = 0;
        this.startEdit("name", w.folder, w.name, "rename.placeholder");
    }

    private void startAlias(LauncherWorlds.World w)
    {
        this.select(w.folder, false);
        this.inspTarget = 0;
        this.startEdit("alias", w.folder, this.prefs.aliases.getOrDefault(w.folder, ""), "alias.placeholder");
    }

    private void startTagRename(String tag)
    {
        this.startEdit("tag", tag, tag, "tag.rename_placeholder");
    }

    private void startEdit(String kind, String key, String text, String placeholder)
    {
        this.finishRename(true);
        this.renameKind = kind;
        this.renaming = key;
        this.nameInput.placeholder(L10n.lang(L + placeholder));
        this.nameInput.setText(text);
        this.nameInput.setVisible(true);
        this.ctx.focus(this.nameInput);
    }

    /** Ends the inline edit; {@code keep} applies it (vanilla's rename for a name: level.dat changes, the folder stays). */
    private void finishRename(boolean keep)
    {
        String key = this.renaming;

        if (key == null)
        {
            return;
        }

        this.renaming = null;

        String text = this.nameInput.getText().trim();

        if (this.nameInput.isFocused())
        {
            this.ctx.unfocus();
        }

        this.nameInput.setVisible(false);

        if (!keep)
        {
            return;
        }

        if (this.renameKind.equals("tag"))
        {
            String to = text.replaceFirst("^#", "").trim();

            if (to.isEmpty() || to.equals(key))
            {
                return;
            }

            this.prefs.renameTag(key, to);

            if (this.filter.equals("tag:" + key))
            {
                this.filter = "tag:" + to;
            }

            this.showToast(tr("toast.tag_renamed", key, to));
            this.wenxu.react("tag");

            return;
        }

        LauncherWorlds.World w = this.worlds.get(key);

        if (w == null)
        {
            return;
        }

        if (this.renameKind.equals("alias"))
        {
            String old = this.prefs.aliases.getOrDefault(w.folder, "");

            if (text.equals(old) || text.equals(w.name) && old.isEmpty())
            {
                return;
            }

            /* An alias equal to the real name is no alias */
            this.prefs.setAlias(w.folder, text.equals(w.name) ? null : text);
            this.showToast(text.isEmpty() || text.equals(w.name) ? tr("toast.alias_cleared", w.name) : tr("toast.alias", text));
            this.wenxu.react("tag");

            return;
        }

        if (text.isEmpty() || text.equals(w.name))
        {
            return;
        }

        if (WorldActions.rename(w.folder, text))
        {
            this.showToast(tr("toast.renamed", text));
            this.wenxu.react("tag");
            this.worlds.reload();
        }
    }

    private void tagMenu(String tag, float mx, float my)
    {
        if (!this.prefs.hasTag(tag))
        {
            return;
        }

        List<MenuItem> items = new ArrayList<>();

        items.add(new MenuItem(tr("tag.rename"), () -> this.startTagRename(tag), false, false));
        items.add(new MenuItem(tr("tag.delete"), () ->
        {
            this.wenxu.react("dialog");
            this.openDialog(tr("tag.delete_title"), tr("tag.delete_text", tag), tr("tag.delete_yes"), () ->
            {
                this.prefs.renameTag(tag, null);

                if (this.filter.equals("tag:" + tag))
                {
                    this.filter = "all";
                }

                this.showToast(tr("toast.tag_deleted", tag));
            });
        }, false, true));
        this.openMenu(items, mx, my);
    }

    /* Covers */

    /** [texture, width, height] of a world's picture: the picked cover, the newest film cover, then the world icon; null while none is ready. */
    private float[] coverOf(LauncherWorlds.World w)
    {
        String pick = this.prefs.covers.get(w.folder);

        if (pick != null && pick.startsWith("file:"))
        {
            java.io.File file = new java.io.File(pick.substring(5));

            if (file.isFile())
            {
                LauncherWorlds.Pic custom = LauncherWorlds.custom(file);
                int id = custom.id();

                if (id > 0)
                {
                    return new float[] {id, custom.width, custom.height};
                }
            }
        }

        LauncherWorlds.Film film = w.coverFilm;

        if (pick != null && pick.startsWith("film:"))
        {
            String id = filmOf(pick);
            java.io.File snapshot = snapshotOf(pick);

            /* A locked film cover shows its own copy, so re-capturing the film's cover later doesn't change it */
            if (snapshot != null && snapshot.isFile())
            {
                LauncherWorlds.Pic custom = LauncherWorlds.custom(snapshot);
                int tex = custom.id();

                if (tex > 0)
                {
                    return new float[] {tex, custom.width, custom.height};
                }
            }

            for (LauncherWorlds.Film f : w.films)
            {
                if (f.id.equals(id) && f.cover != null)
                {
                    film = f;
                }
            }

            /* Locks from before 2.7.48 point at the film only: freeze them on today's picture */
            if (snapshot == null && film != null && film.id.equals(id) && !this.snapshotFailed.contains(w.folder))
            {
                this.setCoverQuietly(w, this.lockFilm(w, film));
            }
        }

        int cover = LauncherWorlds.cover(film);

        if (cover > 0)
        {
            return new float[] {cover, film.pic.width, film.pic.height};
        }

        int icon = LauncherWorlds.icon(w);

        return icon > 0 ? new float[] {icon, w.icon.width, w.icon.height} : null;
    }

    private void coverMenu(LauncherWorlds.World w, float mx, float my)
    {
        List<MenuItem> items = new ArrayList<>();
        String pick = this.prefs.covers.getOrDefault(w.folder, "");
        int n = 0;

        if (pick.isEmpty() && w.coverFilm != null)
        {
            items.add(new MenuItem(tr("cover.pin"), () -> this.togglePin(w), false, false));
            items.add(new MenuItem(null, null, false, false));
        }

        for (LauncherWorlds.Film film : w.films)
        {
            if (film.cover == null || n++ >= 8)
            {
                continue;
            }

            String id = film.id;

            items.add(new MenuItem(tr("cover.film", id), () -> this.setCover(w, this.lockFilm(w, film)), pick.startsWith("film:") && filmOf(pick).equals(id), false));
        }

        if (!items.isEmpty())
        {
            items.add(new MenuItem(null, null, false, false));
        }

        items.add(new MenuItem(tr("cover.pick"), () -> this.pickCoverImage(w), pick.startsWith("file:"), false));
        items.add(new MenuItem(tr("cover.reset"), () -> this.setCover(w, null), pick.isEmpty(), false));
        this.openMenu(items, mx, my);
    }

    /** Pinning keeps the picture shown now (the newest film cover) when newer films get covers; unpinning goes back to following them. */
    private void togglePin(LauncherWorlds.World w)
    {
        if (this.prefs.covers.containsKey(w.folder))
        {
            this.setCover(w, null);
            this.showToast(tr("toast.unpinned"));
        }
        else if (w.coverFilm != null)
        {
            this.setCover(w, this.lockFilm(w, w.coverFilm));
            this.showToast(tr("toast.pinned", w.coverFilm.name()));
        }
    }

    private void setCover(LauncherWorlds.World w, String value)
    {
        this.setCoverQuietly(w, value);
        this.selectedAt = System.currentTimeMillis();
        this.wenxu.react("fav");
    }

    private void setCoverQuietly(LauncherWorlds.World w, String value)
    {
        String old = value == null ? this.prefs.covers.remove(w.folder) : this.prefs.covers.put(w.folder, value);
        java.io.File snapshot = old == null || old.equals(value) ? null : snapshotOf(old);

        if (snapshot != null && snapshot.getParentFile() != null && snapshot.getParentFile().equals(coversDir()))
        {
            snapshot.delete();
        }

        this.prefs.save();
    }

    /** Worlds whose old film lock couldn't be copied (don't retry every frame). */
    private final java.util.Set<String> snapshotFailed = new java.util.HashSet<>();

    /**
     * A film cover lock, "film:<id>|<copy>": the film's cover as it is now, copied beside the launcher's
     * settings so the lock keeps this picture when the film's cover is re-captured; without the copy it
     * falls back to following the film.
     */
    private String lockFilm(LauncherWorlds.World w, LauncherWorlds.Film film)
    {
        String value = "film:" + film.id;

        if (film.cover == null || !film.cover.isFile())
        {
            return value;
        }

        try
        {
            java.io.File copy = new java.io.File(coversDir(), Integer.toHexString(w.folder.hashCode()) + "_film_" + System.currentTimeMillis() + ".jpg");

            coversDir().mkdirs();
            java.nio.file.Files.copy(film.cover.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return value + "|" + copy.getAbsolutePath();
        }
        catch (Exception e)
        {
            this.snapshotFailed.add(w.folder);
            System.err.println("[BBS UI Rabbit] Couldn't keep a copy of " + film.cover + " for the locked cover: " + e);

            return value;
        }
    }

    private static java.io.File coversDir()
    {
        return new java.io.File(class_310.method_1551().field_1697, "config/bbs_ui_rabbit/launcher_covers").getAbsoluteFile();
    }

    /** The film id of a "film:<id>[|<copy>]" cover. */
    private static String filmOf(String pick)
    {
        int bar = pick.indexOf('|');

        return bar < 0 ? pick.substring(5) : pick.substring(5, bar);
    }

    /** The picture a cover keeps a copy of ("film:<id>|<copy>"), null for covers that have none. */
    private static java.io.File snapshotOf(String pick)
    {
        int bar = pick.indexOf('|');

        return pick.startsWith("film:") && bar >= 0 ? new java.io.File(pick.substring(bar + 1)) : null;
    }

    /** Pick a picture (a screenshot, by default) and keep a copy beside the launcher's settings. */
    private void pickCoverImage(LauncherWorlds.World w)
    {
        java.io.File shots = new java.io.File(class_310.method_1551().field_1697, "screenshots");

        mchorse.bbs_mod.ui.utils.UIFileDialogs.pickFile(L10n.lang(L + "cover.pick_title"), shots.isDirectory() ? shots : null,
            new String[] {"*.png", "*.jpg", "*.jpeg", "*.bmp"}, L10n.lang(L + "cover.images"), (file) ->
        {
            if (file == null || !file.isFile())
            {
                return;
            }

            try
            {
                java.io.File dir = coversDir();
                String name = file.getName();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".png";
                java.io.File copy = new java.io.File(dir, Integer.toHexString(w.folder.hashCode()) + "_" + System.currentTimeMillis() + ext);

                dir.mkdirs();
                java.nio.file.Files.copy(file.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                class_310.method_1551().execute(() -> this.setCover(w, "file:" + copy.getAbsolutePath()));
            }
            catch (Exception e)
            {
                System.err.println("[BBS UI Rabbit] Couldn't use " + file + " as a cover: " + e);
            }
        });
    }

    /* Inspector */

    private Object tagBox;
    private Object nameBox;

    private void renderInspector(UIContext context)
    {
        float x0 = this.W - this.IW;

        this.b.box(x0, 0, this.W, this.H, this.mintBg);
        this.b.box(x0, 0, x0 + 2, this.H, this.stroke);

        LauncherWorlds.World w = this.selectedWorld();

        this.tagBox = null;

        if (w == null)
        {
            String msg = this.worlds.isLoading() ? tr("worlds.loading") : tr("inspector.none");

            this.txt(msg, x0 + (this.IW - this.width(msg)) / 2F, this.H / 2F - 4, this.muted);

            return;
        }

        float pad = 12, x = x0 + pad + 2, iw = this.IW - pad * 2 - 2;
        float appear = Gfx.easeOutBack(Gfx.clamp01((System.currentTimeMillis() - this.selectedAt) / 380F));
        float top = 0, bottom = this.H;

        this.inspScroll += (this.inspTarget - this.inspScroll) * Math.min(1, this.dt * 14);
        this.b.clip((int) x0 + 2, (int) top, (int) this.IW - 2, (int) (bottom - top), context);
        this.push();
        this.b.getContext().method_51448().method_46416(0, (1 - appear) * 10 - this.inspScroll, 0);

        float y = 12;

        /* Hero: speed lines and a taped-on picture */
        float heroH = iw * 0.56F;

        this.comic(x, y, iw, heroH, 9, this.card, this.stroke, this.stroke, 0);
        this.b.clip((int) x + 2, (int) (y + 2 + (1 - appear) * 10 - this.inspScroll), (int) iw - 4, (int) heroH - 4, context);
        this.burst(x + iw / 2, y + heroH / 2, iw);
        this.b.unclip(context);

        float picW = iw * 0.78F, picH = picW * 9F / 16F, picX = x + (iw - picW) / 2, picY = y + (heroH - picH) / 2 + 2;

        this.push();
        this.rotateAround(picX + picW / 2, picY + picH / 2, -2.5F, 1F);
        Gfx.roundRect(this.b, picX + 3, picY + 3, picW, picH, 5, this.rasp);
        Gfx.roundRect(this.b, picX - 1.5F, picY - 1.5F, picW + 3, picH + 3, 6, this.stroke);

        float[] pic = this.coverOf(w);

        if (pic != null)
        {
            this.picture((int) pic[0], pic[1], pic[2], picX, picY, picW, picH, 4, 0xffffffff);
        }
        else
        {
            this.placeholder(picX, picY, picW, picH, 4, this.display(w));
        }

        this.pop();

        float tapeW = 30, tapeX = x + iw / 2 - tapeW / 2, tapeY = picY - 6;

        this.push();
        this.rotateAround(tapeX + tapeW / 2, tapeY + 4, 3F, 1F);
        Gfx.roundRect(this.b, tapeX, tapeY, tapeW, 9, 1, Gfx.mulA(this.stroke, 0.9F));
        Gfx.roundRect(this.b, tapeX + 1, tapeY + 1, tapeW - 2, 7, 1, 0xe0ffd84a);
        this.pop();

        this.sparkle(x + iw - 12, y + heroH - 12, 0.6F);
        this.sparkle(x + iw - 22, y + 14, 1.1F);

        float cbw = this.width(tr("cover.change")) + 22, cbx = x + iw - cbw - 5, cby = y + heroH - 21;
        Hit coverHit = this.button("cover", cbx, cby, cbw, 15, tr("cover.change"), Icons.IMAGE, 0, () -> {});

        coverHit.click = () ->
        {
            sound();
            this.coverMenu(w, cbx + cbw - 190, cby + 19 - this.inspScroll);
        };

        /* Pin: a lock in the corner keeps today's picture even when newer films get covers */
        boolean pinned = this.prefs.covers.containsKey(w.folder);

        if (pinned || w.coverFilm != null)
        {
            float lx = x + 11, ly = y + 11;
            float hv = this.hover("pin");
            float a = pinned ? 1F : 0.55F + 0.45F * hv;
            float r = 7.5F + hv;

            Gfx.circle(this.b, lx + 1, ly + 1.5F, r, Gfx.mulA(this.stroke, a));
            Gfx.circle(this.b, lx, ly, r, Gfx.mulA(this.stroke, a));
            Gfx.circle(this.b, lx, ly, r - 1.5F, Gfx.mulA(pinned ? this.rasp : this.card, a));
            Gfx.icon(this.b, pinned ? Icons.LOCKED : Icons.UNLOCKED, lx, ly, 0.8F, Gfx.mulA(pinned ? 0xffffffff : this.text, a));

            Hit pin = this.hit("pin", lx - 9, ly - 9 - this.inspScroll, 18, 18);

            pin.tooltip = tr(pinned ? "cover.unpin_hint" : "cover.pin_hint");
            pin.click = () ->
            {
                sound();
                this.togglePin(w);
            };
        }

        this.hit("hero", x, y - this.inspScroll, iw, heroH).right = () -> this.coverMenu(w, this.ctx.mouseX, this.ctx.mouseY);
        this.moveToBack("hero");

        y += heroH + 10;

        /* Name (click to rename it, as vanilla's edit screen does) with the save folder under it */
        class_327 font = this.b.getFont().getRenderer();

        boolean aliased = this.prefs.aliases.containsKey(w.folder);

        if (!this.renameKind.equals("tag") && w.folder.equals(this.renaming))
        {
            float nh = 17;

            Gfx.roundRect(this.b, x - 2, y - 4, iw + 4, nh, 4, this.card);
            Gfx.roundOutline(this.b, x - 2, y - 4, iw + 4, nh, 4, 1.5F, this.rasp);
            this.nameInput.setColor(this.text, false);
            this.place(this.nameInput, x + 2, y - 4 - this.inspScroll + (1 - appear) * 10, iw - 4, nh);
            this.nameBox = this.nameInput;
            y += nh - 2;
        }
        else
        {
            List<String> lines = FontRenderer.wrap(font, this.display(w), (int) ((iw - 14) / 1.25F));
            float ny = y, lastW = 0;
            float hv = this.hover("rename");
            int count = Math.min(2, lines.size());

            for (int i = 0; i < count; i++)
            {
                this.bold(lines.get(i), x, y, 1.25F, Gfx.lerp(this.text, this.rasp, hv * 0.6F));
                lastW = this.width(lines.get(i)) * 1.25F;
                y += 13;
            }

            if (hv > 0.01F)
            {
                Gfx.icon(this.b, Icons.EDIT, x + lastW + 8, y - 13 + 5.5F, 0.8F, Gfx.mulA(this.rasp, hv));
            }

            Hit rename = this.hit("rename", x, ny - 2 - this.inspScroll, Math.min(iw, lastW + 16), y - ny + 1);

            rename.tooltip = tr("rename.hint");
            float menuY = y - this.inspScroll + 2;

            rename.click = () -> this.nameMenu(w, x, menuY);
        }

        /* With an alias, the world's own name only shows here */
        if (aliased)
        {
            String own = tr("alias.own");

            this.txt(own, x, y, this.muted);
            this.txt(this.fit(w.name, iw - this.width(own) - 5), x + this.width(own) + 5, y, this.text);
            y += 11;
        }

        this.txt(this.fit(w.folder, iw), x, y, this.muted);
        y += 15;

        /* Details */
        List<String[]> kv = new ArrayList<>();

        kv.add(new String[] {tr("kv.last"), this.full(w.lastPlayed)});

        if (!w.isCreative())
        {
            kv.add(new String[] {tr("kv.mode"), this.modeName(w)});
        }

        if (w.cheats)
        {
            kv.add(new String[] {tr("kv.cheats"), tr("kv.on")});
        }

        kv.add(new String[] {tr("kv.version"), w.version});
        kv.add(new String[] {tr("kv.size"), size(w.size)});

        float kw = 0;

        for (String[] row : kv)
        {
            kw = Math.max(kw, this.width(row[0]));
        }

        for (String[] row : kv)
        {
            this.txt(row[0], x, y, this.muted);
            this.bold(this.fit(row[1], iw - kw - 12), x + kw + 10, y, 1F, this.text);
            y += 11;
        }

        y += 3;

        String note = w.locked ? tr("note.locked") : w.needsBackup ? tr("note.old", w.version) : w.future ? tr("note.future", w.version) : w.experimental ? tr("note.experimental") : null;

        if (note != null)
        {
            List<String> nl = FontRenderer.wrap(font, note, (int) iw - 14);
            float nh = nl.size() * 10 + 8;

            this.comic(x, y, iw, nh, 6, this.butter, this.stroke, this.stroke, 0);

            for (int i = 0; i < nl.size(); i++)
            {
                this.txt(nl.get(i), x + 7, y + 5 + i * 10, 0xff3a1d33);
            }

            y += nh + 8;
        }

        /* Tags */
        this.txt(tr("tags"), x, y, this.muted);
        y += 11;

        float tx = x;

        for (String[] tag : this.tagsOf(w))
        {
            boolean auto = tag[1].equals("auto");
            float tw = this.width(tag[0]) + (auto ? 8 : 16);

            if (tx + tw > x + iw)
            {
                tx = x;
                y += 14;
            }

            this.chip(tag[0] + (auto ? "" : "  "), tx, y, auto ? this.card : this.mint, this.text, auto);

            if (!auto)
            {
                String tagName = tag[0];
                float hv = this.hover("untag:" + tagName);

                Gfx.icon(this.b, Icons.CLOSE, tx + tw - 6, y + 5.5F, 0.6F, Gfx.lerp(this.muted, this.rasp, hv));

                this.hit("tagchip:" + tagName, tx, y - this.inspScroll, tw, 11).right = () -> this.tagMenu(tagName, this.ctx.mouseX, this.ctx.mouseY);

                Hit un = this.hit("untag:" + tagName, tx + tw - 11, y + 1 - this.inspScroll, 10, 10);

                un.click = () ->
                {
                    this.prefs.removeTag(w.folder, tagName);

                    if (this.filter.equals("tag:" + tagName) && this.tagIndex(this.worlds.worlds()).get(tagName) == null)
                    {
                        this.filter = "all";
                    }
                };
            }
            else
            {
                this.hit("autotag", tx, y - this.inspScroll, tw, 11).tooltip = tr("tag.auto_hint");
            }

            tx += tw + 3;
        }

        float inW = 74;

        if (tx + inW > x + iw)
        {
            tx = x;
            y += 14;
        }

        boolean tf = this.tagInput.isFocused();

        Gfx.roundOutline(this.b, tx, y - 1, inW, 13, 4, 1F, tf ? this.rasp : this.muted);
        this.place(this.tagInput, tx + 2, y - 2 - this.inspScroll + (1 - appear) * 10, inW - 4, 14);
        this.tagBox = this.tagInput;
        this.hit("taginput", tx, y - 1 - this.inspScroll, inW, 13).click = () -> this.ctx.focus(this.tagInput);
        y += 18;

        /* Films */
        this.txt(tr("films"), x, y, this.muted);
        this.bold(String.valueOf(w.films.size()), x + this.width(tr("films")) + 4, y, 1F, this.rasp);
        y += 12;

        if (w.films.isEmpty())
        {
            String none = w.scanned ? tr("films.none") : tr("worlds.loading");
            float nh = 20;

            Gfx.roundOutline(this.b, x, y, iw, nh, 6, 1F, Gfx.mulA(this.muted, 0.6F));
            this.txt(none, x + (iw - this.width(none)) / 2F, y + 6, this.muted);
            y += nh + 8;
        }
        else
        {
            /* As many rows as fit above the buttons; the last tile unfolds the rest */
            int cols = 3;
            float gx = 6, gy = 7;
            float fw = (iw - gx * (cols - 1)) / cols, fh = fw * 9F / 16F;
            float room = this.H - 12 - (26 + 6 + 18) - 8 - y;
            int rows = Math.max(1, (int) ((room + gy) / (fh + gy)));
            int total = w.films.size();
            boolean more = total > rows * cols;
            int shown = more ? rows * cols - 1 : total;
            int tiles = shown + (more ? 1 : 0);

            for (int i = 0; i < tiles; i++)
            {
                float fx = x + (i % cols) * (fw + gx), fy = y + (i / cols) * (fh + gy);
                boolean toggle = i >= shown;
                String fkey = toggle ? "film-more" : "film:" + i;
                float hv = toggle ? 0F : this.hover(fkey);

                this.push();
                this.rotateAround(fx + fw / 2, fy + fh / 2, ((i * 7) % 5 - 2) * 0.8F * (1 - hv), 1F + 0.06F * hv);
                Gfx.roundRect(this.b, fx - 1, fy - 1, fw + 2, fh + 2, 4, hv > 0.01F ? Gfx.lerp(this.stroke, this.rasp, hv) : this.stroke);

                if (toggle)
                {
                    Gfx.roundRect(this.b, fx, fy, fw, fh, 3, this.card);

                    String label = "+" + (total - shown);

                    this.bold(label, fx + (fw - this.width(label)) / 2F, fy + fh / 2 - 4, 1F, this.text);
                }
                else
                {
                    LauncherWorlds.Film film = w.films.get(i);
                    int tex = LauncherWorlds.cover(film);

                    if (tex > 0)
                    {
                        this.picture(tex, film.pic.width, film.pic.height, fx, fy, fw, fh, 3, 0xffffffff);
                    }
                    else
                    {
                        this.placeholder(fx, fy, fw, fh, 3, film.name());
                    }

                    int label = FilmThumbnails.labelColor(film.color);

                    if (label != 0)
                    {
                        Gfx.circle(this.b, fx + 6, fy + 6, 3.5F, this.stroke);
                        Gfx.circle(this.b, fx + 6, fy + 6, 2.5F, 0xff000000 | label);
                    }
                }

                this.pop();

                Hit tile = this.hit(fkey, fx, fy - this.inspScroll, fw, fh);

                if (toggle)
                {
                    tile.tooltip = tr("films.more", total - shown);
                }
                else
                {
                    tile.film = w.films.get(i);
                }
            }

            y += ((tiles + cols - 1) / cols) * (fh + gy) + 3;
        }

        /* Actions, kept at the bottom when there is room */
        float actionsH = 26 + 6 + 18;
        float ay = Math.max(y + 4, this.H - 12 - actionsH);
        boolean canPlay = !w.unavailable && !w.locked;

        this.button("play", x, ay, iw, 24, tr("world.play"), Icons.PLAY, 1, canPlay ? () -> this.play(w) : null);

        float bw = (iw - 3 * 5) / 4F;

        this.button("edit", x, ay + 31, bw, 17, tr("world.edit"), null, 0, () -> WorldActions.edit(this.parent(), w.summary, this.worlds::reload));
        this.button("backup", x + (bw + 5), ay + 31, bw, 17, tr("world.backup"), null, 0, () -> this.backup(w));
        this.button("folder", x + (bw + 5) * 2, ay + 31, bw, 17, tr("world.folder_short"), null, 0, () -> WorldActions.openFolder(w.dir()));

        float mx = x + (bw + 5) * 3;
        Hit more = this.button("more", mx, ay + 31, bw, 17, "⋯", null, 0, () -> {});

        more.click = () ->
        {
            sound();

            List<MenuItem> items = new ArrayList<>();

            items.add(new MenuItem(tr("world.rename"), () -> this.startRename(w), false, false));
            items.add(new MenuItem(tr(this.prefs.aliases.containsKey(w.folder) ? "world.alias_edit" : "world.alias"), () -> this.startAlias(w), false, false));
            items.add(new MenuItem(tr("world.recreate"), () -> WorldActions.recreate(this.parent(), w.summary), false, false));
            items.add(new MenuItem(null, null, false, false));
            items.add(new MenuItem(tr("world.delete"), () -> this.confirmDelete(w), false, true));
            this.openMenu(items, mx + bw - 150, ay + 31 + 21 - this.inspScroll - 155);
        };

        this.pop();
        this.b.unclip(context);

        /* Hits were placed in scrolled space for the parts above; shift the action buttons back */
        this.fixHits(this.inspScroll, x0);
        this.inspContentH = ay + actionsH + 12;
        this.inspTarget = Math.max(0, Math.min(this.inspTarget, this.inspContentH - this.H));
        this.hit("insp-area", x0, 0, this.IW, this.H);
        this.moveToBack("insp-area");
    }

    /** Hits drawn inside the scrolled inspector (button/sticker helpers don't know about the scroll). */
    private void fixHits(float scroll, float x0)
    {
        if (scroll == 0)
        {
            return;
        }

        for (int i = 0; i < this.hits.size(); i++)
        {
            Hit h = this.hits.get(i);

            if (h.x >= x0 && (h.key.equals("play") || h.key.equals("cover") || h.key.equals("edit") || h.key.equals("backup") || h.key.equals("folder") || h.key.equals("more")
                || h.key.equals("join") || h.key.equals("editsv") || h.key.equals("delsv") || h.key.equals("svmore")))
            {
                Hit moved = new Hit(h.key, h.x, h.y - scroll, h.w, h.h);

                moved.click = h.click;
                moved.right = h.right;
                moved.dbl = h.dbl;
                moved.tooltip = h.tooltip;
                this.hits.set(i, moved);
            }
        }
    }

    private void moveToBack(String key)
    {
        for (int i = this.hits.size() - 1; i >= 0; i--)
        {
            if (this.hits.get(i).key.equals(key))
            {
                this.hits.add(0, this.hits.remove(i));

                return;
            }
        }
    }

    private void burst(float cx, float cy, float size)
    {
        float r = size;
        int rays = 26;
        float spin = this.time * 0.12F;
        int color = Gfx.mulA(this.text, 0.11F);

        for (int i = 0; i < rays; i++)
        {
            float a = spin + i * (float) (Math.PI * 2 / rays);
            float a2 = a + 0.07F;

            Gfx.triangle(this.b, cx, cy, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, cx + (float) Math.cos(a2) * r, cy + (float) Math.sin(a2) * r, color);
        }
    }

    private void sparkle(float x, float y, float phase)
    {
        float t = 0.5F + 0.5F * (float) Math.sin(this.time * 3.6F + phase * 4);
        float s = 3 + 3 * t;
        int c = Gfx.mulA(this.rasp, 0.35F + 0.65F * t);

        Gfx.triangle(this.b, x - s, y, x, y - s * 0.3F, x, y + s * 0.3F, c);
        Gfx.triangle(this.b, x + s, y, x, y - s * 0.3F, x, y + s * 0.3F, c);
        Gfx.triangle(this.b, x, y - s, x - s * 0.3F, y, x + s * 0.3F, y, c);
        Gfx.triangle(this.b, x, y + s, x - s * 0.3F, y, x + s * 0.3F, y, c);
    }

    /* Servers */

    private void addServer()
    {
        class_642 info = new class_642(class_2561.method_43471("selectServer.defaultName").getString(), "", false);
        class_437 parent = this.parent();

        class_310.method_1551().method_1507(new class_422(parent, (ok) ->
        {
            if (ok)
            {
                this.servers.add(info);
            }

            class_310.method_1551().method_1507(parent);
        }, info));
    }

    private void editServer(class_642 server)
    {
        class_642 edit = new class_642(server.field_3752, server.field_3761, false);
        class_437 parent = this.parent();

        edit.method_2996(server);
        class_310.method_1551().method_1507(new class_422(parent, (ok) ->
        {
            if (ok)
            {
                server.field_3752 = edit.field_3752;
                server.field_3761 = edit.field_3761;
                server.method_2996(edit);
                this.servers.save();
            }

            class_310.method_1551().method_1507(parent);
        }, edit));
    }

    private void deleteServer(class_642 server)
    {
        this.wenxu.react("dialog");
        this.openDialog(tr("server.delete.title"), tr("server.delete.text", server.field_3752), tr("delete.yes"), () ->
        {
            this.servers.remove(server);
            this.selServer = null;
            this.wenxu.react("deleted");
        });
    }

    private void bars(float x, float y, class_642 server, int index)
    {
        int n;
        boolean pinging = !LauncherServers.pinged(server);

        if (pinging)
        {
            int s = (int) ((System.currentTimeMillis() / 100L + index * 2) & 7);

            n = s > 4 ? 8 - s : s;
        }
        else
        {
            long p = server.field_3758;

            n = p < 0 ? 0 : p < 150 ? 4 : p < 300 ? 3 : p < 600 ? 2 : 1;
        }

        for (int i = 0; i < 4; i++)
        {
            float bh = 3 + i * 2.2F;
            int c = i < n ? (pinging ? Gfx.mulA(this.rasp, 0.6F) : this.rasp) : Gfx.mulA(this.muted, 0.35F);

            this.b.box(x + i * 3.5F, y + 10 - bh, x + i * 3.5F + 2.5F, y + 10, c);
        }
    }

    private void drawText(class_2561 text, float x, float y, int color)
    {
        this.b.flush();
        this.b.getContext().method_51439(this.b.getFont().getRenderer(), text, Math.round(x), Math.round(y), color, false);
    }

    private void drawOrdered(class_5481 text, float x, float y, int color)
    {
        this.b.flush();
        this.b.getContext().method_51430(this.b.getFont().getRenderer(), text, Math.round(x), Math.round(y), color, false);
    }

    private void renderServers(UIContext context)
    {
        this.renderToolbar(context, true);

        List<class_642> list = this.servers.servers();
        List<class_1131> lan = this.servers.lan();
        boolean lanOnly = this.filter.equals("lan");

        this.heading(lanOnly ? tr("nav.lan") : tr("nav.servers"), lanOnly ? lan.size() : list.size(), tr("servers.hint"), 38);

        if (!lan.isEmpty() && !this.lanSeen)
        {
            this.lanSeen = true;
            this.wenxu.react("lan");
            this.showToast(tr("toast.lan", lan.get(0).method_4813()));
        }

        float top = 58, bottom = this.H - 18;
        float x = this.MX + 14, w = this.MW - 26, rh = 30;
        long since = System.currentTimeMillis() - this.listChangedAt;

        this.scroll += (this.scrollTarget - this.scroll) * Math.min(1, this.dt * 14);
        this.b.clip((int) this.MX, (int) top - 4, (int) this.MW - 2, (int) (bottom - top + 4), context);
        this.hit("grid", this.MX, top, this.MW, bottom - top);

        float y = top + 2 - this.scroll;
        class_327 font = this.b.getFont().getRenderer();

        if (!lanOnly)
        {
            if (list.isEmpty())
            {
                String a = tr("servers.none");

                this.txt(a, x + (w - this.width(a)) / 2F, y + 16, this.muted);
                y += 40;
            }

            for (int i = 0; i < list.size(); i++)
            {
                class_642 server = list.get(i);

                this.servers.ensurePinged(server);

                float pop = Gfx.clamp01((since - i * 45) / 360F);
                String key = "sv:" + i;
                boolean sel = server == this.selServer;
                float hv = this.hover(key);
                float s = this.anim("svsel:" + System.identityHashCode(server), sel ? 1F : 0F, 14F);
                float lift = hv * 1.5F;

                this.push();
                this.rotateAround(x + w / 2, y + rh / 2, 0, 0.9F + 0.1F * Gfx.easeOutBack(pop));
                this.comic(x - lift, y - lift, w, rh, 8, this.card, Gfx.lerp(this.stroke, this.rasp, s), Gfx.lerp(this.stroke, this.rasp, s), 2 + lift);

                int icon = this.servers.icon(server);
                float ix = x - lift + 5, iy = y - lift + 4;

                if (icon > 0)
                {
                    Gfx.roundRect(this.b, ix - 1, iy - 1, 24, 24, 5, this.stroke);
                    Gfx.imageRound(this.b, icon, ix, iy, 22, 22, 4, 0, 0, 1, 1, 0xffffffff);
                }
                else
                {
                    this.placeholder(ix, iy, 22, 22, 4, server.field_3752);
                }

                float tx = ix + 28;
                boolean compatible = LauncherServers.compatible(server);
                String count = !compatible ? server.field_3760.getString() : server.field_3753.getString();
                float cw = this.width(count);

                this.bold(this.fit(server.field_3752, w - 60 - cw), tx, iy + 1, 1F, this.text);

                List<class_5481> motd = font.method_1728(server.field_3757, (int) (w - 60));

                if (!motd.isEmpty())
                {
                    this.drawOrdered(motd.get(0), tx, iy + 12, this.muted);
                }

                this.txt(count, x - lift + w - cw - 24, iy + 1, compatible ? this.muted : 0xffd23b3b);
                this.bars(x - lift + w - 19, iy, server, i);
                this.pop();

                if (y + rh > top && y < bottom)
                {
                    Hit hit = this.hit(key, x, Math.max(y, top), w, Math.min(y + rh, bottom) - Math.max(y, top));

                    hit.click = () ->
                    {
                        this.selServer = server;
                        this.selLan = null;
                        this.wenxu.react("select");
                    };
                    hit.dbl = () -> LauncherServers.connect(this.parent(), server);

                    if (!server.field_3762.isEmpty())
                    {
                        StringBuilder players = new StringBuilder();

                        for (class_2561 line : server.field_3762)
                        {
                            players.append(line.getString()).append('\n');
                        }

                        hit.tooltip = players.toString().trim();
                    }
                }

                y += rh + 8;
            }
        }

        /* LAN */
        y += 4;
        this.txt(tr("servers.lan"), x + 2, y, this.muted);

        if (lan.isEmpty())
        {
            String scanning = tr("servers.scanning");
            float sx = x + 6 + this.width(tr("servers.lan"));

            this.txt(scanning, sx, y, this.muted);

            for (int i = 0; i < 3; i++)
            {
                float bounce = (float) Math.max(0, Math.sin(this.time * 6 - i * 0.8F)) * 2.5F;

                Gfx.circle(this.b, sx + this.width(scanning) + 5 + i * 5, y + 4 - bounce, 1.6F, this.rasp);
            }
        }

        y += 13;

        for (int i = 0; i < lan.size(); i++)
        {
            class_1131 entry = lan.get(i);
            String key = "lan:" + i;
            boolean sel = this.selLan != null && this.selLan.method_4812().equals(entry.method_4812());
            float hv = this.hover(key);
            float lift = hv * 1.5F;

            this.comic(x - lift, y - lift, w, rh, 8, sel ? this.card : Gfx.lerp(this.card, this.butter, 0.35F), sel ? this.rasp : this.stroke, sel ? this.rasp : this.stroke, 2 + lift);

            float pulse = 0.5F + 0.5F * (float) Math.sin(this.time * 4);

            Gfx.circle(this.b, x - lift + 14, y - lift + rh / 2, 4 + pulse * 2, Gfx.alpha(0x2fcf8a, 0.3F * (1 - pulse)));
            Gfx.circle(this.b, x - lift + 14, y - lift + rh / 2, 4, 0xff2fcf8a);
            this.bold(this.fit(entry.method_4813(), w - 70), x - lift + 26, y - lift + 6, 1F, this.text);
            this.txt(entry.method_4812(), x - lift + 26, y - lift + 17, this.muted);

            Hit hit = this.hit(key, x, y, w, rh);

            hit.click = () ->
            {
                this.selLan = entry;
                this.selServer = null;
                this.wenxu.react("select");
            };
            hit.dbl = () -> LauncherServers.connectLan(this.parent(), entry);
            y += rh + 8;
        }

        this.b.unclip(context);
        this.gridContentH = y + this.scroll - top + 10;
        this.scrollTarget = Math.max(0, Math.min(this.scrollTarget, this.gridContentH - (bottom - top)));

        this.b.box(this.MX + 10, this.H - 16, this.MX + this.MW - 10, this.H - 15, Gfx.mulA(this.muted, 0.3F));
        this.txt(tr("status.servers", list.size(), lan.size()), this.MX + 14, this.H - 11, this.muted);
        this.renderVersionLine();
    }

    private void renderServerInspector(UIContext context)
    {
        float x0 = this.W - this.IW;

        this.b.box(x0, 0, this.W, this.H, this.mintBg);
        this.b.box(x0, 0, x0 + 2, this.H, this.stroke);
        this.tagBox = null;

        class_642 server = this.selServer;
        class_1131 lan = this.selLan;

        if (server == null && lan == null)
        {
            String msg = tr("servers.pick");

            this.txt(msg, x0 + (this.IW - this.width(msg)) / 2F, this.H / 2F - 4, this.muted);

            return;
        }

        float pad = 12, x = x0 + pad + 2, iw = this.IW - pad * 2 - 2, y = 12;
        float heroH = iw * 0.5F;

        this.comic(x, y, iw, heroH, 9, this.card, this.stroke, this.stroke, 0);
        this.b.clip((int) x + 2, (int) y + 2, (int) iw - 4, (int) heroH - 4, context);
        this.burst(x + iw / 2, y + heroH / 2, iw);
        this.b.unclip(context);

        float is = Math.min(heroH - 18, 52), ix = x + (iw - is) / 2, iy = y + (heroH - is) / 2;
        int icon = server != null ? this.servers.icon(server) : 0;

        Gfx.roundRect(this.b, ix + 3, iy + 3, is, is, 8, this.rasp);
        Gfx.roundRect(this.b, ix - 1.5F, iy - 1.5F, is + 3, is + 3, 9, this.stroke);

        if (icon > 0)
        {
            Gfx.imageRound(this.b, icon, ix, iy, is, is, 7, 0, 0, 1, 1, 0xffffffff);
        }
        else
        {
            this.placeholder(ix, iy, is, is, 7, server != null ? server.field_3752 : lan.method_4813());
        }

        this.sparkle(x + 10, y + 10, 0F);
        this.sparkle(x + iw - 12, y + heroH - 12, 0.6F);
        y += heroH + 10;

        String name = server != null ? server.field_3752 : lan.method_4813();
        String addr = server != null ? server.field_3761 : lan.method_4812();

        this.bold(this.fit(name, iw / 1.25F), x, y, 1.25F, this.text);
        y += 14;
        this.txt(this.fit(addr, iw), x, y, this.muted);
        y += 14;

        if (server != null)
        {
            boolean compatible = LauncherServers.compatible(server);
            boolean pinged = LauncherServers.pinged(server);

            this.txt(tr("kv.version"), x, y, this.muted);
            this.bold(this.fit(server.field_3760.getString(), iw - 50), x + 40, y, 1F, compatible ? this.text : 0xffd23b3b);
            y += 11;
            this.txt(tr("kv.ping"), x, y, this.muted);
            this.bold(!pinged ? tr("server.pinging") : server.field_3758 < 0 ? tr("server.offline") : server.field_3758 + " ms", x + 40, y, 1F, this.text);
            y += 11;
            this.txt(tr("kv.online"), x, y, this.muted);
            this.bold(server.field_3753.getString(), x + 40, y, 1F, this.text);
            y += 15;

            class_327 font = this.b.getFont().getRenderer();
            List<class_5481> motd = font.method_1728(server.field_3757, (int) iw);

            for (int i = 0; i < Math.min(3, motd.size()); i++)
            {
                this.drawOrdered(motd.get(i), x, y, this.text);
                y += 10;
            }

            y += 4;

            if (!compatible)
            {
                y = this.note(tr("server.incompatible", server.field_3760.getString()), x, y, iw);
            }

            if (!server.field_3762.isEmpty())
            {
                this.txt(tr("server.players"), x, y, this.muted);
                y += 12;

                float px = x;

                for (class_2561 line : server.field_3762)
                {
                    String p = line.getString();
                    float pw = this.width(p) + 18;

                    if (px + pw > x + iw)
                    {
                        px = x;
                        y += 14;
                    }

                    if (y > this.H - 80)
                    {
                        break;
                    }

                    Gfx.roundRect(this.b, px, y, pw, 12, 5, this.stroke);
                    Gfx.roundRect(this.b, px + 1, y + 1, pw - 2, 10, 4, this.card);
                    Gfx.roundRect(this.b, px + 2.5F, y + 2.5F, 7, 7, 2, Gfx.hsv((p.hashCode() & 0xff) / 255F, 0.45F, 0.95F));
                    this.txt(p, px + 12, y + 2, this.text);
                    px += pw + 3;
                }
            }
        }

        float ay = this.H - 12 - 50;

        if (server != null)
        {
            this.button("join", x, ay, iw, 24, tr("server.join"), Icons.PLAY, 1, () -> LauncherServers.connect(this.parent(), server));

            float bw = (iw - 2 * 5) / 3F;

            this.button("editsv", x, ay + 31, bw, 17, tr("world.edit"), null, 0, () -> this.editServer(server));
            this.button("delsv", x + bw + 5, ay + 31, bw, 17, tr("server.delete"), null, 0, () -> this.deleteServer(server));

            float mx = x + (bw + 5) * 2;
            Hit more = this.button("svmore", mx, ay + 31, bw, 17, "⋯", null, 0, () -> {});

            more.click = () ->
            {
                sound();

                List<MenuItem> items = new ArrayList<>();

                items.add(new MenuItem(tr("server.up"), () -> this.servers.move(server, -1), false, false));
                items.add(new MenuItem(tr("server.down"), () -> this.servers.move(server, 1), false, false));
                items.add(new MenuItem(null, null, false, false));
                items.add(new MenuItem(tr("server.copy"), () -> class_310.method_1551().field_1774.method_1455(server.field_3761), false, false));
                this.openMenu(items, mx + bw - 130, ay - 60);
            };
        }
        else
        {
            this.button("join", x, ay + 20, iw, 24, tr("server.join"), Icons.PLAY, 1, () -> LauncherServers.connectLan(this.parent(), lan));
        }
    }

    private float note(String text, float x, float y, float iw)
    {
        List<String> nl = FontRenderer.wrap(this.b.getFont().getRenderer(), text, (int) iw - 14);
        float nh = nl.size() * 10 + 8;

        this.comic(x, y, iw, nh, 6, this.butter, this.stroke, this.stroke, 0);

        for (int i = 0; i < nl.size(); i++)
        {
            this.txt(nl.get(i), x + 7, y + 5 + i * 10, 0xff3a1d33);
        }

        return y + nh + 8;
    }

    /* 小文绪 */

    private void renderWenxu(UIContext context)
    {
        float k = (this.utilTop - this.charTop - 6) / 1181F;
        float ox = this.SW / 2F + 2, oy = this.utilTop - 3;
        float[] head = this.wenxu.head(ox, oy, k);
        Hit target = null;

        if (this.hot != null && (this.hot.equals("play") || this.hot.equals("join")))
        {
            for (Hit h : this.lastHits)
            {
                if (h.key.equals(this.hot))
                {
                    target = h;
                }
            }
        }

        float tx, ty;
        boolean wide = target != null;

        if (target != null)
        {
            tx = target.x + target.w / 2;
            ty = target.y + target.h / 2;
        }
        else
        {
            tx = context.mouseX;
            ty = context.mouseY;
        }

        float dx = tx - head[0], dy = ty - head[1];

        this.wenxu.look(clamp(dx / 300F, -1, 1), clamp(dy / 210F, -1, 1), clamp(dx / 180F, -1.1F, 1.1F), clamp(dy / 140F, -1.1F, 1.1F), wide);
        this.wenxu.update(this.dt);

        this.wenxu.render(this.b, ox, oy, k, this.shade, this.ground);

        if (this.wenxu.contains(ox, oy, k, context.mouseX, context.mouseY))
        {
            this.hit("wenxu", ox - 50, this.charTop, 100, this.utilTop - this.charTop).click = () -> this.wenxu.react("poke");
        }
    }

    private static float clamp(float v, float a, float c)
    {
        return Math.max(a, Math.min(c, v));
    }

    /* Overlays */

    private void openMenu(List<MenuItem> items, float x, float y)
    {
        this.menu = items;
        this.menuX = x;
        this.menuY = y;
        this.menuAt = System.currentTimeMillis();
    }

    private void closeMenu()
    {
        this.menu = null;
    }

    private void renderMenu(UIContext context)
    {
        if (this.menu == null)
        {
            return;
        }

        float w = 0, h = 6;

        for (MenuItem item : this.menu)
        {
            w = Math.max(w, item.separator ? 0 : this.width(item.label) + 26);
            h += item.separator ? 7 : 15;
        }

        w = Math.max(w, 110);

        float x = clamp(this.menuX, 6, this.W - w - 8), y = this.menuY;

        if (y + h > this.H - 8)
        {
            y = Math.max(6, this.H - 8 - h);
        }

        float t = Gfx.easeOutBack(Gfx.clamp01((System.currentTimeMillis() - this.menuAt) / 220F));

        /* A full-screen catcher closes the menu when clicked outside */
        this.hit("menu-outside", 0, 0, this.W, this.H).click = this::closeMenu;

        this.push();
        this.rotateAround(x + w / 2, y, 0, 0.9F + 0.1F * t);
        this.comic(x, y, w, h, 8, this.card, this.stroke, this.stroke, 3);

        float iy = y + 3;

        for (int i = 0; i < this.menu.size(); i++)
        {
            MenuItem item = this.menu.get(i);

            if (item.separator)
            {
                for (float dx = x + 8; dx < x + w - 8; dx += 4)
                {
                    this.b.box(dx, iy + 3, dx + 2, iy + 4, Gfx.mulA(this.muted, 0.5F));
                }

                iy += 7;

                continue;
            }

            String key = "menu:" + i;
            float hv = this.hover(key);

            if (hv > 0.01F)
            {
                Gfx.roundRect(this.b, x + 3, iy, w - 6, 14, 5, Gfx.mulA(this.raspSoft, hv));
            }

            int fg = item.danger ? 0xffe23d3d : item.on ? this.rasp : this.text;

            if (item.on)
            {
                Gfx.icon(this.b, Icons.CHECKMARK, x + 10, iy + 7, 0.8F, this.rasp);
            }

            this.txt(item.label, x + 18, iy + 3, fg);

            Hit hit = this.hit(key, x + 3, iy, w - 6, 14);

            hit.click = () ->
            {
                sound();
                this.closeMenu();
                item.action.run();
            };
            iy += 15;
        }

        this.pop();
    }

    private void openDialog(String title, String text, String yes, Runnable action)
    {
        this.closeMenu();
        this.dlgTitle = title;
        this.dlgText = text;
        this.dlgYes = yes;
        this.dlgAction = action;
        this.dlgAt = System.currentTimeMillis();
    }

    private void closeDialog(boolean cancelled)
    {
        this.dlgAction = null;

        if (cancelled)
        {
            this.wenxu.react("cancel");
        }
    }

    private void renderDialog(UIContext context)
    {
        if (this.dlgAction == null)
        {
            return;
        }

        float t = Gfx.easeOutBack(Gfx.clamp01((System.currentTimeMillis() - this.dlgAt) / 300F));

        this.b.box(0, 0, this.W, this.H, Gfx.alpha(0x281423, 0.35F * Math.min(1, t)));
        this.hit("dlg-outside", 0, 0, this.W, this.H).click = () -> this.closeDialog(true);

        float w = Math.min(280, this.W - 40);
        List<String> lines = FontRenderer.wrap(this.b.getFont().getRenderer(), this.dlgText, (int) w - 28);
        float h = 46 + lines.size() * 10 + 26;
        float x = (this.W - w) / 2, y = (this.H - h) / 2;

        this.push();
        this.rotateAround(x + w / 2, y + h / 2, -2F * (1 - t), 0.85F + 0.15F * t);
        this.comic(x, y, w, h, 10, this.card, this.stroke, this.stroke, 4);
        this.bold(this.dlgTitle, x + 14, y + 12, 1.3F, this.text);

        for (int i = 0; i < lines.size(); i++)
        {
            this.txt(lines.get(i), x + 14, y + 32 + i * 10, this.muted);
        }

        this.hit("dlg-box", x, y, w, h);

        float by = y + h - 26;
        float yw = this.width(this.dlgYes) + 22, nw = this.width(tr("cancel")) + 22;
        Runnable action = this.dlgAction;

        this.button("dlg-no", x + w - 14 - yw - 8 - nw, by, nw, 17, tr("cancel"), null, 0, () -> this.closeDialog(true));
        this.button("dlg-yes", x + w - 14 - yw, by, yw, 17, this.dlgYes, null, 2, () ->
        {
            this.closeDialog(false);
            action.run();
        });
        this.pop();
    }

    private void renderToast()
    {
        if (this.toast == null)
        {
            return;
        }

        long age = System.currentTimeMillis() - this.toastAt;

        if (age > 2600)
        {
            this.toast = null;

            return;
        }

        float in = Gfx.easeOutBack(Gfx.clamp01(age / 350F)), out = Gfx.clamp01((2600 - age) / 300F);
        float w = this.width(this.toast) + 24, h = 18;
        float x = this.MX + (this.MW - w) / 2, y = -h + (h + 10) * in * out;

        this.comic(x, y, w, h, 7, this.plate, this.stroke, this.rasp, 2);
        this.txt(this.toast, x + 12, y + 5, 0xfffdfded);
    }

    private void renderTooltip(UIContext context, Hit under)
    {
        if (under != null && under.film != null && this.menu == null && this.dlgAction == null)
        {
            this.renderFilmTip(context, under.film);

            return;
        }

        if (under == null || under.tooltip == null || this.menu != null || this.dlgAction != null || System.currentTimeMillis() - this.hotAt < 450)
        {
            return;
        }

        String[] lines = under.tooltip.split("\n");
        float w = 0;

        for (String line : lines)
        {
            w = Math.max(w, this.width(line));
        }

        w += 12;

        float h = lines.length * 10 + 6;
        float x = clamp(context.mouseX + 8, 4, this.W - w - 4), y = clamp(context.mouseY + 12, 4, this.H - h - 4);

        this.comic(x, y, w, h, 5, this.plate, this.stroke, this.stroke, 0);

        for (int i = 0; i < lines.length; i++)
        {
            this.txt(lines[i], x + 6, y + 4 + i * 10, 0xfffdfded);
        }
    }

    /** A film's card on hover: its library frames flipping by, name, place, length, last change, size. */
    private void renderFilmTip(UIContext context, LauncherWorlds.Film film)
    {
        long since = System.currentTimeMillis() - this.hotAt;

        if (since < 180)
        {
            return;
        }

        float t = Gfx.easeOutBack(Gfx.clamp01((since - 180) / 220F));
        int light = 0xfffdfded, dim = Gfx.mulA(light, 0.6F);
        float pw = 176, ph = pw * 9F / 16F;

        /* Picture: the hover frames in film order, else the cover */
        int frames = film.frames == null ? 0 : film.frames.length;
        int tex = 0;
        float tw = 0, th = 0;

        for (int i = 0; i < frames; i++)
        {
            LauncherWorlds.frame(film, i);
        }

        int at = frames > 0 ? (int) (since / 550L) % frames : -1;

        if (at >= 0)
        {
            tex = LauncherWorlds.frame(film, at);

            LauncherWorlds.Pic pic = LauncherWorlds.framePic(film, at);

            if (pic != null)
            {
                tw = pic.width;
                th = pic.height;
            }
        }

        if (tex <= 0)
        {
            tex = LauncherWorlds.cover(film);

            if (tex > 0)
            {
                tw = film.pic.width;
                th = film.pic.height;
            }
        }

        List<String[]> rows = new ArrayList<>();
        int slash = film.id.lastIndexOf('/');

        if (slash > 0)
        {
            rows.add(new String[] {tr("film.where"), film.id.substring(0, slash)});
        }

        if (film.span > 0)
        {
            float secs = film.span / 20F;

            rows.add(new String[] {tr("film.length"), secs < 60 ? tr("film.seconds", String.format(Locale.ROOT, "%.1f", secs)) : String.format(Locale.ROOT, "%d:%02d", (int) secs / 60, (int) secs % 60)});
        }

        rows.add(new String[] {tr("film.modified"), this.full(film.modified) + " · " + this.ago(film.modified)});
        rows.add(new String[] {tr("kv.size"), film.size < 1024 ? "1 KB" : size(film.size)});

        float kw = 0, vw = this.width(film.name()) * 1.1F + (film.color != 0 ? 12 : 0);

        for (String[] row : rows)
        {
            kw = Math.max(kw, this.width(row[0]));
        }

        for (String[] row : rows)
        {
            vw = Math.max(vw, kw + 8 + this.width(row[1]));
        }

        float w = Math.max(pw, Math.min(300, vw)) + 12;
        float h = 6 + ph + (frames > 1 ? 6 : 0) + 6 + 12 + rows.size() * 10 + 4;
        float x = clamp(context.mouseX - w - 12, 4, this.W - w - 4), y = clamp(context.mouseY - h / 2F, 4, this.H - h - 4);

        this.push();
        this.rotateAround(x + w, y + h / 2, 1.5F * (1 - t), 0.85F + 0.15F * t);
        this.comic(x, y, w, h, 7, this.plate, this.stroke, this.rasp, 2.5F);

        float px = x + 6 + (w - 12 - pw) / 2F, py = y + 6;

        Gfx.roundRect(this.b, px - 1, py - 1, pw + 2, ph + 2, 5, this.stroke);

        if (tex > 0)
        {
            this.picture(tex, tw, th, px, py, pw, ph, 4, 0xffffffff);
        }
        else
        {
            this.placeholder(px, py, pw, ph, 4, film.name());
        }

        float cy = py + ph + 6;

        /* Which eighth of the film is showing */
        if (frames > 1)
        {
            float sw = (pw - (frames - 1) * 2) / frames;

            for (int i = 0; i < frames; i++)
            {
                Gfx.roundRect(this.b, px + i * (sw + 2), cy - 3, sw, 3, 1, i == at ? this.rasp : Gfx.mulA(light, 0.25F));
            }

            cy += 6;
        }

        float nx = x + 6;
        int label = FilmThumbnails.labelColor(film.color);

        if (label != 0)
        {
            Gfx.circle(this.b, nx + 4, cy + 4.5F, 3.5F, 0xff000000 | label);
            nx += 12;
        }

        this.bold(this.fit(film.name(), w - 12 - (nx - x - 6)), nx, cy, 1.1F, light);
        cy += 13;

        for (String[] row : rows)
        {
            this.txt(row[0], x + 6, cy, dim);
            this.txt(this.fit(row[1], w - 12 - kw - 8), x + 6 + kw + 8, cy, light);
            cy += 10;
        }

        this.pop();
    }

    /* Input */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        Hit hit = this.hitAt(context.mouseX, context.mouseY);

        if (this.menu != null && (hit == null || !hit.key.startsWith("menu:")))
        {
            this.closeMenu();

            return true;
        }

        if (hit == null)
        {
            return true;
        }

        if (this.dlgAction != null && !hit.key.startsWith("dlg-"))
        {
            return true;
        }

        if (context.mouseButton == 1)
        {
            if (hit.right != null)
            {
                hit.right.run();
            }

            return true;
        }

        if (context.mouseButton != 0)
        {
            return true;
        }

        long now = System.currentTimeMillis();
        boolean isDouble = hit.key.equals(this.lastClickKey) && now - this.lastClickAt < 320;

        this.lastClickKey = hit.key;
        this.lastClickAt = isDouble ? 0 : now;

        if (isDouble && hit.dbl != null)
        {
            hit.dbl.run();
        }
        else if (hit.click != null)
        {
            hit.click.run();
        }

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context) || this.menu != null || this.dlgAction != null)
        {
            return false;
        }

        float amount = (float) context.mouseWheel * 32F;

        if (context.mouseX < this.SW)
        {
            this.navTarget -= amount;
        }
        else if (context.mouseX >= this.W - this.IW)
        {
            this.inspTarget -= amount;
        }
        else
        {
            this.scrollTarget -= amount;
        }

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            if (this.dlgAction != null)
            {
                this.closeDialog(true);

                return true;
            }

            if (this.menu != null)
            {
                this.closeMenu();

                return true;
            }
        }

        if (this.dlgAction != null)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ENTER))
            {
                Runnable action = this.dlgAction;

                this.closeDialog(false);
                action.run();
            }

            return true;
        }

        boolean ctrl = mchorse.bbs_mod.graphics.window.Window.isCtrlPressed();

        if (ctrl && (context.isPressed(GLFW.GLFW_KEY_F) || context.isPressed(GLFW.GLFW_KEY_K)))
        {
            context.focus(this.view.equals(VIEW_SERVERS) ? this.address : this.search);

            return true;
        }

        if (this.tagInput.isFocused())
        {
            if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
            {
                String tag = this.tagInput.getText().trim().replaceFirst("^#", "").trim();
                LauncherWorlds.World w = this.selectedWorld();

                if (!tag.isEmpty() && w != null && !this.prefs.tagsOf(w.folder).contains(tag) && !(w.flat && tag.equals(tr("tag.flat"))))
                {
                    this.prefs.addTag(w.folder, tag);
                    this.wenxu.react("tag");
                    this.showToast(tr("toast.tag", this.display(w), tag));
                }

                this.tagInput.setText("");

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.tagInput.unfocus(context);

                return true;
            }

            return false;
        }

        if (this.address.isFocused())
        {
            if ((context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER)) && !this.address.getText().trim().isEmpty())
            {
                this.servers.connectAddress(this.parent(), this.address.getText().trim());

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.address.unfocus(context);

                return true;
            }

            return false;
        }

        if (this.search.isFocused())
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                if (this.search.getText().isEmpty())
                {
                    this.search.unfocus(context);
                }
                else
                {
                    this.search.setText("");
                }

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_ENTER) && !this.visible.isEmpty())
            {
                this.search.unfocus(context);
                this.select(this.visible.get(0).folder, true);
                this.scrollToSelected = true;

                return true;
            }

            if (!context.isPressed(GLFW.GLFW_KEY_DOWN))
            {
                return false;
            }

            this.search.unfocus(context);
        }

        if (this.view.equals(VIEW_SERVERS))
        {
            if (context.isPressed(GLFW.GLFW_KEY_F5))
            {
                this.servers.refresh();

                return true;
            }

            if ((context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER)))
            {
                if (this.selServer != null) LauncherServers.connect(this.parent(), this.selServer);
                else if (this.selLan != null) LauncherServers.connectLan(this.parent(), this.selLan);

                return true;
            }

            return false;
        }

        int index = this.indexOfSelected();
        int step = 0;

        if (context.isPressed(GLFW.GLFW_KEY_RIGHT)) step = 1;
        if (context.isPressed(GLFW.GLFW_KEY_LEFT)) step = -1;
        if (context.isPressed(GLFW.GLFW_KEY_DOWN)) step = this.gridCols;
        if (context.isPressed(GLFW.GLFW_KEY_UP)) step = -this.gridCols;

        if (step != 0 && !this.visible.isEmpty())
        {
            int next = index < 0 ? 0 : Math.max(0, Math.min(this.visible.size() - 1, index + step));

            this.select(this.visible.get(next).folder, true);
            this.scrollToSelected = true;

            return true;
        }

        LauncherWorlds.World w = this.selectedWorld();

        if (w != null && (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER)))
        {
            this.play(w);

            return true;
        }

        if (w != null && context.isPressed(GLFW.GLFW_KEY_DELETE))
        {
            this.confirmDelete(w);

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_F5))
        {
            this.worlds.reload();

            return true;
        }

        return false;
    }
}
