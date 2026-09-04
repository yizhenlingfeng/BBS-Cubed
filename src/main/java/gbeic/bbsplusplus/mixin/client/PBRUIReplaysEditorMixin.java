package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
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

        ModelInstance model = ModelFormRenderer.getModel(modelForm);
        String path = FormUtils.getPath(modelForm);

        if (model != null && model.materials != null)
        {
            String[] keys = {"pbr_s_r", "pbr_s_g", "pbr_s_b", "pbr_s_a", "pbr_n"};
            String[] titles = {"Smoothness", "F0/Metal", "Porosity/SSS", "Emissive", "Normal"};
            int[] colors = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.YELLOW, Colors.CYAN};

            for (String material : model.materials)
            {
                if (material == null || material.isEmpty())
                {
                    continue;
                }

                for (int i = 0; i < keys.length; i++)
                {
                    String leaf = material + "." + keys[i];
                    String channelKey = path.isEmpty() ? leaf : path + FormUtils.PATH_SEPARATOR + leaf;
                    String title = path.isEmpty() ? material + "/" + titles[i] : path + "/" + material + "/" + titles[i];
                    KeyframeChannel<Float> channel = this.replay.properties.registerChannel(channelKey, KeyframeFactories.FLOAT);

                    sheets.add(new UIKeyframeSheet(channelKey, () -> title, colors[i], false, channel, null).icon(Icons.MATERIAL));
                }
            }
        }

        IKeyframeFactory factory = KeyframeFactories.FACTORIES.get("bone_pbr");

        if (factory != null)
        {
            String channelKey = path.isEmpty() ? "bone_pbr" : path + FormUtils.PATH_SEPARATOR + "bone_pbr";
            String title = path.isEmpty() ? "BonePBR" : path + "/BonePBR";
            KeyframeChannel channel = this.replay.properties.registerChannel(channelKey, factory);

            sheets.add(new UIKeyframeSheet(channelKey, () -> title, Colors.CYAN, false, channel, null).icon(Icons.POSE).form(modelForm));
        }
    }
}
