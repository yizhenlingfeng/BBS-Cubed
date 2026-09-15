package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = UIReplaysEditor.class, remap = true)
public class UIReplaysEditorMixin
{

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getAAAParticleColor(String key, CallbackInfoReturnable<Integer> cir)
    {
        String topLevel = StringUtils.fileName(key);

        switch (topLevel)
        {
            case "effect":
                cir.setReturnValue(Colors.MAGENTA);
                break;
            case "bbspp_uv_transform":
                cir.setReturnValue(Colors.CYAN);
                break;
            case "bbspp_uv_scale":
                cir.setReturnValue(Colors.CYAN);
                break;
            case "bbspp_uv_rotation":
                cir.setReturnValue(Colors.CYAN);
                break;
            case "restart":
            case "loopStart":
            case "loopEnd":
                cir.setReturnValue(Colors.YELLOW);
                break;
            case "particleScale":
                cir.setReturnValue(Colors.CYAN);
                break;
            case "trigger0":
            case "trigger1":
            case "trigger2":
            case "trigger3":
                cir.setReturnValue(Colors.RED);
                break;
            case "ignoreDepth":
                cir.setReturnValue(Colors.WHITE);
                break;
        }
    }

    @Inject(method = "getIcon", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getAAAParticleIcon(String key, CallbackInfoReturnable<Icon> cir)
    {
        String topLevel = StringUtils.fileName(key);

        switch (topLevel)
        {
            case "effect":
                cir.setReturnValue(Icons.PARTICLE);
                break;
            case "bbspp_uv_transform":
                cir.setReturnValue(Icons.MATERIAL);
                break;
            case "bbspp_uv_scale":
                cir.setReturnValue(Icons.SCALE);
                break;
            case "bbspp_uv_rotation":
                cir.setReturnValue(Icons.REFRESH);
                break;
            case "restart":
                cir.setReturnValue(Icons.REDO);
                break;
            case "loopStart":
                cir.setReturnValue(Icons.LEFTLOAD);
                break;
            case "loopEnd":
                cir.setReturnValue(Icons.RIGHTLOAD);
                break;
            case "particleScale":
                cir.setReturnValue(Icons.SCALE);
                break;
            case "dynamicInput0":
            case "dynamicInput1":
            case "dynamicInput2":
            case "dynamicInput3":
                cir.setReturnValue(Icons.WRENCH);
                break;
            case "trigger0":
            case "trigger1":
            case "trigger2":
            case "trigger3":
                cir.setReturnValue(Icons.BULLET);
                break;
            case "ignoreDepth":
                cir.setReturnValue(Icons.VISIBLE);
                break;
        }
    }
}
