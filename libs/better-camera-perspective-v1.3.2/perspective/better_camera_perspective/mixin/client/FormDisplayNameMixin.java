package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.StableIds;
import mchorse.bbs_mod.settings.values.core.ValueString;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.FilmDisplayNames;

/**
 * 修复「锚点绑定其他演员时只显示稳定 ID / form 类型」的显示 bug。
 *
 * 当表单未设置自定义名称（name 为空）时，getFormIdOrName() / getDisplayName()
 * 会返回 form 类型 Link（如 bbs:model）或稳定 ID，而不是演员的显示名。
 * 此处改为：优先在当前打开的影片中按表单反查 Replay 的显示名。
 *
 * 2026-10-02 加固：部分数据路径会把表单 name 字段写成 8 位稳定 ID
 * （如 071cb8c1），此时 name 非空但仍然是「数据码」——同样反查。
 */
@Mixin(Form.class)
public abstract class FormDisplayNameMixin {
    @Shadow
    @Final
    public ValueString name;

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void bcp$resolveActorDisplayName(CallbackInfoReturnable<String> cir) {
        String name = this.name.get();
        if (name.isEmpty() || StableIds.isStableId(name)) {
            String actor = FilmDisplayNames.lookupActorName((Form) (Object) this);
            if (actor != null) {
                cir.setReturnValue(actor);
            }
        }
    }

    @Inject(method = "getFormIdOrName", at = @At("HEAD"), cancellable = true)
    private void bcp$resolveActorFormIdOrName(CallbackInfoReturnable<String> cir) {
        String name = this.name.get();
        if (name.isEmpty() || StableIds.isStableId(name)) {
            String actor = FilmDisplayNames.lookupActorName((Form) (Object) this);
            if (actor != null) {
                cir.setReturnValue(actor);
            }
        }
    }
}

