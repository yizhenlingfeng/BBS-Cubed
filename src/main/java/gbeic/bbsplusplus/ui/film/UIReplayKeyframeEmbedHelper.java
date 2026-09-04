package gbeic.bbsplusplus.ui.film;

import gbeic.bbsplusplus.ui.miniwindow.IMiniWindowDockHostRef;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 效果面板 inspector 在选中变化后重新 embed 的辅助工具。
 * 主编辑器或效果面板任一浮动时，inspector 都必须脱离编辑器小窗，挂到 editArea。
 */
public final class UIReplayKeyframeEmbedHelper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UIReplayKeyframeEmbedHelper.class);

    private UIReplayKeyframeEmbedHelper()
    {}

    public static void reembedClipInspector(UIClipsPanel self)
    {
        try
        {
            UIFilmPanel film = self.filmPanel;

            if (!(film instanceof IMiniWindowDockHostRef host))
            {
                return;
            }

            host.bbspp_cml$selectInspectorOwner(self);
        }
        catch (Throwable t)
        {
            LOGGER.warn("[FSloveCML] inspector reembed 失败", t);
        }
    }

    public static void reembedKeyframeInspector(UIKeyframeEditor editor)
    {
        try
        {
            UIReplaysEditor replays = editor instanceof UIElement
                ? ((UIElement) editor).getParent(UIReplaysEditor.class)
                : null;

            if (replays == null)
            {
                return;
            }

            selectReplayInspector(replays);
        }
        catch (Throwable t)
        {
            LOGGER.warn("[FSloveCML] inspector reembed 失败", t);
        }
    }

    public static void reembedFromReplay(UIReplaysEditor replays)
    {
        try
        {
            UIFilmPanel film = replays instanceof UIElement
                ? ((UIElement) replays).getParent(UIFilmPanel.class)
                : null;

            if (film == null || !(film instanceof IMiniWindowDockHostRef host))
            {
                return;
            }

            host.bbspp_cml$refreshInspectorOwner();
        }
        catch (Throwable t)
        {
            LOGGER.warn("[FSloveCML] inspector reembed 失败", t);
        }
    }

    public static void prepareReplayInspectorRebuild(UIReplaysEditor replays)
    {
        try
        {
            UIFilmPanel film = replays instanceof UIElement
                ? ((UIElement) replays).getParent(UIFilmPanel.class)
                : null;

            if (film instanceof IMiniWindowDockHostRef host)
            {
                host.bbspp_cml$prepareInspectorRebuild();
            }
        }
        catch (Throwable t)
        {
            LOGGER.warn("[FSloveCML] inspector reembed 失败", t);
        }
    }

    private static void selectReplayInspector(UIReplaysEditor replays)
    {
        try
        {
            UIFilmPanel film = replays instanceof UIElement
                ? ((UIElement) replays).getParent(UIFilmPanel.class)
                : null;

            if (film instanceof IMiniWindowDockHostRef host)
            {
                host.bbspp_cml$selectInspectorOwner(replays);
            }
        }
        catch (Throwable t)
        {
            LOGGER.warn("[FSloveCML] inspector reembed 失败", t);
        }
    }
}
