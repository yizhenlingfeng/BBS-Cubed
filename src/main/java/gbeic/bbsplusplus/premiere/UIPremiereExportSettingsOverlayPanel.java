package gbeic.bbsplusplus.premiere;

import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.l10n.L10n;

/**
 * 右键 Premiere 按钮时的快捷设置(与 BBS_snow 主设置项同步)。
 */
public class UIPremiereExportSettingsOverlayPanel extends UIOverlayPanel
{
    private final UIToggle enabled;
    private final UIToggle individualAudio;
    private final UIToggle audioOnly;
    private final UIToggle ntscFlag;
    private final UIToggle exportSrt;

    public UIPremiereExportSettingsOverlayPanel()
    {
        super(PremiereUIKeys.EXPORT_SETTINGS);

        this.enabled = new UIToggle(L10n.lang("bbs.config.bbs_snow.premiere_export_enabled"), (b) ->
        {
            if (CMLSettings.premiereExportEnabled != null)
            {
                CMLSettings.premiereExportEnabled.set(b.getValue());
            }
        });
        this.enabled.tooltip(L10n.lang("bbs.config.bbs_snow.premiere_export_enabled-comment"));

        this.individualAudio = new UIToggle(L10n.lang("bbs.config.bbs_snow.premiere_export_individual_audio"), (b) ->
        {
            if (CMLSettings.premiereExportIndividualAudio != null)
            {
                CMLSettings.premiereExportIndividualAudio.set(b.getValue());
            }
        });
        this.individualAudio.tooltip(L10n.lang("bbs.config.bbs_snow.premiere_export_individual_audio-comment"));

        this.audioOnly = new UIToggle(L10n.lang("bbs.config.bbs_snow.premiere_export_audio_only"), (b) ->
        {
            if (CMLSettings.premiereExportAudioOnly != null)
            {
                CMLSettings.premiereExportAudioOnly.set(b.getValue());
            }
        });
        this.audioOnly.tooltip(L10n.lang("bbs.config.bbs_snow.premiere_export_audio_only-comment"));

        this.ntscFlag = new UIToggle(L10n.lang("bbs.config.bbs_snow.premiere_export_ntsc_flag"), (b) ->
        {
            if (CMLSettings.premiereExportNtscFlag != null)
            {
                CMLSettings.premiereExportNtscFlag.set(b.getValue());
            }
        });
        this.ntscFlag.tooltip(L10n.lang("bbs.config.bbs_snow.premiere_export_ntsc_flag-comment"));

        this.exportSrt = new UIToggle(L10n.lang("bbs.config.bbs_snow.premiere_export_srt"), (b) ->
        {
            if (CMLSettings.premiereExportSrt != null)
            {
                CMLSettings.premiereExportSrt.set(b.getValue());
            }
        });
        this.exportSrt.tooltip(L10n.lang("bbs.config.bbs_snow.premiere_export_srt-comment"));

        UIScrollView editor = UI.scrollView(5, 6,
            this.enabled,
            this.individualAudio,
            this.audioOnly,
            this.ntscFlag,
            this.exportSrt
        );

        this.content.add(editor.full(this.content));
        this.fill();
    }

    private void fill()
    {
        if (CMLSettings.premiereExportEnabled != null)
        {
            this.enabled.setValue(CMLSettings.premiereExportEnabled.get());
        }
        if (CMLSettings.premiereExportIndividualAudio != null)
        {
            this.individualAudio.setValue(CMLSettings.premiereExportIndividualAudio.get());
        }
        if (CMLSettings.premiereExportAudioOnly != null)
        {
            this.audioOnly.setValue(CMLSettings.premiereExportAudioOnly.get());
        }
        if (CMLSettings.premiereExportNtscFlag != null)
        {
            this.ntscFlag.setValue(CMLSettings.premiereExportNtscFlag.get());
        }
        if (CMLSettings.premiereExportSrt != null)
        {
            this.exportSrt.setValue(CMLSettings.premiereExportSrt.get());
        }
    }
}
