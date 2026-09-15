package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * 回放编辑器里追加逐骨骼 PBR（bone_pbr）轨道页。
 * 逐材质 PBR 轨道页已随 2.6 原生 FormMaterial 移除。
 */
@Mixin(value = UIReplaysEditor.class, remap = false)
public class PBRUIReplaysEditorMixin
{
    @Shadow
    private Replay replay;

    @Inject(method = "flushForm", at = @At("RETURN"))
    private void bbspp_snow$addPbrSheets(List<UIKeyframeSheet> sheets, List<UIKeyframeSheet> formSheets, Form form, Map<UIKeyframeSheet, List<UIKeyframeSheet>> poseTabs, Map<UIKeyframeSheet, Integer> poseTabDepths, CallbackInfo ci)
    {
        if (!(form instanceof ModelForm modelForm) || this.replay == null)
        {
            return;
        }

        String path = FormUtils.getPath(modelForm);

        IKeyframeFactory factory = KeyframeFactories.FACTORIES.get("bone_pbr");

        if (factory != null)
        {
            String channelKey = path.isEmpty() ? "bone_pbr" : path + FormUtils.PATH_SEPARATOR + "bone_pbr";
            String title = path.isEmpty() ? "BonePBR" : path + "/BonePBR";
            KeyframeChannel channel = this.replay.properties.register(TrackId.parse(channelKey), factory);

            sheets.add(new UIKeyframeSheet(channelKey, () -> title, Colors.CYAN, channel, null).icon(Icons.POSE).form(modelForm));
        }
    }
}
