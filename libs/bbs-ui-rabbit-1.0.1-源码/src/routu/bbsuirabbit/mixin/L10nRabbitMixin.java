package routu.bbsuirabbit.mixin;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

/** BBS UI Rabbit's strings ({@code assets/bbs/assets/bbs_ui_rabbit/lang/<lang>.json}). */
@Mixin(value = L10n.class, remap = false)
public class L10nRabbitMixin
{
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void rabbit$registerStrings(CallbackInfo ci)
    {
        ((L10n) (Object) this).register(L10nRabbitMixin::rabbit$links);
    }

    @Unique
    private static List<Link> rabbit$links(String lang)
    {
        String path = "bbs_ui_rabbit/lang/" + lang + ".json";

        return L10nRabbitMixin.class.getClassLoader().getResource("assets/bbs/assets/" + path) == null
            ? Collections.emptyList()
            : Collections.singletonList(Link.assets(path));
    }
}
