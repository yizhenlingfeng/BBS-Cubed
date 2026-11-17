package gbeic.bbsplusplus.ui.particles;

import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 粒子编辑器的「另存为」。
 *
 * <p>1.20.1 那版（{@code src/main/java/...}）在面板外面自建了一套快照历史：
 * 每帧序列化整份 scheme，按 300ms 防抖推一条 undo，并挂上 Ctrl+Z / Ctrl+Y 和历史面板。
 * 1.21.11 的上游已经把这件事做进 {@link UIParticleSchemePanel} 本身了
 * （{@code undoManager} + {@code undoSnapshot} + 400ms 的 {@code undoCheckTimer}，
 * 面板构造里也自带 {@code UIUndoKeys}），所以这里<b>只补另存为</b>——
 * 再注册一遍同样的键位会和上游抢 Ctrl+Z，两套历史还会互相覆盖。</p>
 */
@Environment(EnvType.CLIENT)
public final class ParticleEditorFileControls
{
    public static void attach(UIParticleSchemePanel panel)
    {
        UIIcon saveAs = new UIIcon(Icons.COPY, (button) -> openSaveAs(panel));

        saveAs.tooltip(ParticlePlusUIKeys.SAVE_AS, Direction.LEFT);
        panel.actions().common(saveAs);
    }

    private static void openSaveAs(UIParticleSchemePanel panel)
    {
        ParticleScheme data = panel.getData();

        if (data == null)
        {
            return;
        }

        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
            ParticlePlusUIKeys.SAVE_AS,
            ParticlePlusUIKeys.SAVE_AS_DESCRIPTION,
            (filename) -> saveAs(panel, filename)
        );
        String id = data.getId();
        int slash = id == null ? -1 : id.lastIndexOf('/');

        prompt.text.setText(id == null ? "" : id.substring(slash + 1));
        prompt.text.filename();
        UIOverlay.addOverlay(panel.getContext(), prompt);
    }

    private static void saveAs(UIParticleSchemePanel panel, String filename)
    {
        ParticleScheme source = panel.getData();

        if (source == null || filename == null || filename.trim().isEmpty())
        {
            return;
        }

        String id = panel.overlay.namesList.getPath(filename.trim()).toString();

        if (panel.overlay.namesList.hasInHierarchy(id))
        {
            panel.getContext().notifyError(ParticlePlusUIKeys.SAVE_AS_EXISTS.format(id));

            return;
        }

        /* 先把当前方案落盘，再复制一份挂到新 id 上——顺序不能反，
         * 否则 fill() 之后 data 已换成副本，原文件就丢了未保存的改动。 */
        panel.save();

        ParticleScheme copy = ParticleScheme.dupe(source);

        if (copy == null)
        {
            return;
        }

        copy.setId(id);
        panel.overlay.namesList.addFile(id);
        panel.fill(copy);
        panel.forceSave();
        panel.requestNames();
        panel.getContext().notifySuccess(ParticlePlusUIKeys.SAVE_AS_SUCCESS.format(id));
    }

    private ParticleEditorFileControls()
    {}
}
