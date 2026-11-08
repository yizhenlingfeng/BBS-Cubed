package routu.bbsuirabbit.client.ui.home;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * The film library's "体检": checks every film of this world in the background ({@link FilmHealth}) and
 * lists what can't be shown. Clicking a film's name opens it. Read only.
 */
public class UIFilmHealthOverlay extends UIOverlayPanel
{
    private static final String L = "rabbit.film_health.";
    private static final int LINE = 12;
    private static final int PAD = 6;
    private static final int MAX_BACKUPS_LISTED = 40;

    private final UIScrollView scroll = new UIScrollView();
    private final ReportView view = new ReportView();
    private final Consumer<String> opener;
    private final List<Line> lines = new ArrayList<>();

    private volatile FilmHealth.Report report;
    private volatile String failure;
    private volatile boolean cancelled;
    private boolean built;
    private long started = System.currentTimeMillis();

    private record Line(String text, int color, String film, int indent)
    {}

    /**
     * @param folder the world's films folder, null when the films aren't on this computer (a guest)
     * @param opener opens a film by id
     */
    public UIFilmHealthOverlay(File folder, Consumer<String> opener)
    {
        super(L10n.lang(L + "title"));

        this.opener = opener;
        this.scroll.scroll.scrollSpeed = LINE * 3;
        this.scroll.full(this.content);
        this.scroll.add(this.view);
        this.content.add(this.scroll);
        this.onClose((event) -> this.cancelled = true);

        if (folder == null || !folder.isDirectory())
        {
            this.failure = tr("guest");

            return;
        }

        FilmHealth.Known known = known();
        Thread thread = new Thread(() ->
        {
            try
            {
                this.report = FilmHealth.scan(folder, known, () -> this.cancelled);
            }
            catch (Throwable e)
            {
                this.failure = tr("failed", e.toString());
            }
        }, "BBS UI Rabbit film check");

        thread.setDaemon(true);
        thread.start();
    }

    private static String tr(String key, Object... args)
    {
        return args.length == 0 ? L10n.lang(L + key).get() : L10n.lang(L + key).format(args).get();
    }

    /** What this game has, read here on the render thread. */
    private static FilmHealth.Known known()
    {
        FilmHealth.Known known = new FilmHealth.Known();
        AssetProvider provider = BBSMod.getProvider();

        known.form = (map) -> BBSMod.getForms().has(map);
        known.keyframeTypes = new HashSet<>(KeyframeFactories.FACTORIES.keySet());

        try
        {
            known.models = new HashSet<>(BBSModClient.getModels().getAvailableKeys());
        }
        catch (Throwable e)
        {
            known.models = null;
        }

        try
        {
            known.particles = new HashSet<>(BBSModClient.getParticles().getKeys());
        }
        catch (Throwable e)
        {
            known.particles = null;
        }

        known.asset = (link) ->
        {
            try
            {
                return provider.hasAsset(Link.create(link));
            }
            catch (Throwable e)
            {
                /* Can't tell: don't report it */
                return true;
            }
        };

        return known;
    }

    private void build()
    {
        this.built = true;
        this.lines.clear();

        if (this.failure != null)
        {
            this.lines.add(new Line(this.failure, Colors.LIGHTER_GRAY, null, 0));
        }
        else
        {
            FilmHealth.Report report = this.report;

            if (report.problems.isEmpty())
            {
                this.lines.add(new Line(tr("all_good", report.films), 0xff7ddb8a, null, 0));
            }
            else
            {
                this.lines.add(new Line(tr("summary", report.films, report.problems.size()), Colors.WHITE, null, 0));

                /* How many films have each kind of problem */
                StringBuilder counts = new StringBuilder();

                for (String kind : FilmHealth.KINDS)
                {
                    int films = 0;

                    for (FilmHealth.FilmReport film : report.problems)
                    {
                        for (FilmHealth.Issue issue : film.issues.values())
                        {
                            if (issue.kind.equals(kind))
                            {
                                films++;
                                break;
                            }
                        }
                    }

                    if (films > 0)
                    {
                        counts.append(counts.length() == 0 ? "" : "   ").append(tr("short." + kind, films));
                    }
                }

                this.lines.add(new Line(counts.toString(), Colors.LIGHTEST_GRAY, null, 0));
                this.lines.add(new Line(tr("click_hint"), Colors.LIGHTER_GRAY, null, 0));
            }

            for (FilmHealth.FilmReport film : report.problems)
            {
                this.lines.add(new Line("", 0, null, 0));
                this.lines.add(new Line(film.id, 0xff7fc4ff, film.id, 0));

                for (FilmHealth.Issue issue : film.issues.values())
                {
                    String text = tr("kind." + issue.kind) + (FilmHealth.UNREADABLE.equals(issue.kind) ? "" : "  " + issue.detail);

                    if (issue.count > 1)
                    {
                        text += "  ×" + issue.count;
                    }

                    this.lines.add(new Line("· " + text, FilmHealth.UNREADABLE.equals(issue.kind) ? 0xffff7070 : Colors.LIGHTEST_GRAY, null, 1));
                }
            }

            this.lines.add(new Line("", 0, null, 0));
            this.lines.add(new Line(report.brokenBackups.isEmpty() ? tr("backups_ok", report.backups) : tr("backups_broken", report.backups, report.brokenBackups.size()), Colors.LIGHTER_GRAY, null, 0));

            for (int i = 0; i < Math.min(MAX_BACKUPS_LISTED, report.brokenBackups.size()); i++)
            {
                this.lines.add(new Line(report.brokenBackups.get(i), Colors.GRAY, null, 1));
            }

            if (report.brokenBackups.size() > MAX_BACKUPS_LISTED)
            {
                this.lines.add(new Line(tr("more", report.brokenBackups.size() - MAX_BACKUPS_LISTED), Colors.GRAY, null, 1));
            }
        }

        this.resize();
    }

    @Override
    public void resize()
    {
        super.resize();

        int height = PAD * 2 + Math.max(1, this.lines.size()) * LINE;

        this.view.area.set(this.scroll.area.x, this.scroll.area.y, this.scroll.area.w - this.scroll.scroll.getScrollbarWidth(), height);
        this.scroll.scroll.scrollSize = height;
        this.scroll.scroll.clamp();
    }

    @Override
    public void render(UIContext context)
    {
        if (!this.built && (this.report != null || this.failure != null))
        {
            this.build();
        }

        super.render(context);
    }

    private class ReportView extends UIElement
    {
        private int lineAt(UIContext context)
        {
            int index = (context.mouseY - this.area.y - PAD) / LINE;

            return context.mouseY >= this.area.y + PAD && index < UIFilmHealthOverlay.this.lines.size() ? index : -1;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton != 0 || !this.area.isInside(context))
            {
                return false;
            }

            int index = this.lineAt(context);

            if (index < 0)
            {
                return false;
            }

            Line line = UIFilmHealthOverlay.this.lines.get(index);

            if (line.film() == null)
            {
                return false;
            }

            UIFilmHealthOverlay.this.close();
            UIFilmHealthOverlay.this.opener.accept(line.film());

            return true;
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            int x = this.area.x + PAD;
            int y = this.area.y + PAD;

            if (!UIFilmHealthOverlay.this.built)
            {
                int dots = (int) ((System.currentTimeMillis() - UIFilmHealthOverlay.this.started) / 400 % 4);

                context.batcher.textShadow(tr("scanning") + ".".repeat(dots), x, y, Colors.LIGHTER_GRAY);
                super.render(context);

                return;
            }

            int hovered = this.area.isInside(context) ? this.lineAt(context) : -1;

            for (int i = 0; i < UIFilmHealthOverlay.this.lines.size(); i++)
            {
                Line line = UIFilmHealthOverlay.this.lines.get(i);
                int ly = y + i * LINE;

                if (line.text().isEmpty())
                {
                    continue;
                }

                if (i == hovered && line.film() != null)
                {
                    context.batcher.box(this.area.x + 2, ly - 2, this.area.ex() - 2, ly + LINE - 2, Colors.A25 | 0xffffff);
                }

                String text = font.limitToWidth(line.text(), this.area.w - PAD * 2 - line.indent() * 10);

                context.batcher.textShadow(text, x + line.indent() * 10, ly, line.color());
            }

            super.render(context);
        }
    }
}
