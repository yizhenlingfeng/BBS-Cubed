package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

/**
 * 向 BBS 的语言系统注册 BBSPPP 专属语言文件。
 * <p>
 * BBS 的自定义语言资源使用 {@code assets:bbs...} 这套内部资源路径。
 * 如果 BBSPPP 直接提供 {@code assets:lang/zh_cn.json}，会和 BBS++ 的扩展语言文件同路径冲突，
 * 在开发环境里可能导致 BBS++ 甚至 BBSFS 的大量文本退回本地化键。
 * 因此这里注册唯一的 {@code assets:BBSPPP/lang/*.json} 路径，只加载 BBSPPP 自己的少量文本。
 * </p>
 */
@Mixin(L10n.class)
public class L10nTextureTweenMixin
{
    /**
     * 注入目标：BBS 的 {@link L10n} 构造完成后。
     * 注入原因：需要在 BBS 首次 {@code reload()} 前，把 BBSPPP 的语言文件加入加载列表。
     * 修改行为：中文环境加载 BBSPPP 中文语言文件，其余语言加载英文兜底。
     */
    @Inject(method = "<init>()V", at = @At("RETURN"), remap = false)
    private void bbsppp$registerL10nFiles(CallbackInfo ci)
    {
        L10n self = (L10n) (Object) this;

        self.register((lang) ->
        {
            if ("zh_cn".equals(lang))
            {
                return Collections.singletonList(Link.assets("bbsppp/lang/zh_cn.json"));
            }

            return Collections.singletonList(Link.assets("bbsppp/lang/en_us.json"));
        });
    }
}
