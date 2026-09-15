package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import gbeic.bbsplusplus.KeyframeLocalizer;
import gbeic.bbsplusplus.client.KeyframeTrackStyle;
import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 {@link UIKeyframeSheet} 的轨道名称添加中文本地化支持，并应用 BBS++ / VFX 的上下文样式。
 *
 * <p><b>为什么挂在 {@code applyStyle()} 而不是构造器上：</b>
 * BBSFS 2.6 重写了 {@code UIKeyframeSheet} 的构造器体系（新增 {@code TrackDescriptor} 入口、
 * 7 参构造器末位追加 {@code TrackDescriptor}、并内建 {@code applyStyle()} 用于应用
 * {@code BBSSettings.trackStyles} 里的用户全局改名/改色）。于是 2.5 时代的两处注入点全部失效：</p>
 * <ul>
 *   <li>{@code @ModifyArg} 指向的 {@code <init>(IZL…KeyframeChannel;L…BaseValueBasic;)V} 已不存在；</li>
 *   <li>{@code @Inject} 指向的 {@code <init>(…;Z)V} 也已不存在。</li>
 * </ul>
 * <p>由于本 mixin 所在配置为 {@code injectors.defaultRequire=1}，任一目标缺失都会让
 * {@code UIKeyframeSheet} 类加载直接失败（打开回放编辑器即崩）。</p>
 *
 * <p>{@link UIKeyframeSheet#applyStyle()} 对<b>所有</b>构造路径都会在构造末尾执行
 * （所有构造器最终都汇聚到 7 参构造器），且其签名不随构造器变化；用户在设置里修改轨道样式时
 * 也会再次调用它。因此挂在这里既更稳也更全 —— 连 2.6 新增的 {@code TrackCatalog} 建出的
 * 轨道（其标题在 catalog 内生成，旧写法根本覆盖不到）也能一并汉化。</p>
 */
@Mixin(UIKeyframeSheet.class)
public class UIKeyframeSheetMixin
{
    @Shadow
    private Icon icon;

    /**
     * 注入目标：{@link UIKeyframeSheet#applyStyle()} 返回处。
     *
     * <p>此时 {@code id / title / color / property} 均已就绪，且原生 {@code applyStyle()}
     * 已把用户在设置里的全局覆盖写进 title/color。我们在其后写入中文名与上下文样式，
     * 顺序与 2.5 版一致：原生在前，BBS++ 覆盖在后。</p>
     */
    @Inject(method = "applyStyle", at = @At("RETURN"), remap = true)
    private void bbspp$applyContextualTrackStyle(CallbackInfo ci)
    {
        UIKeyframeSheet sheet = (UIKeyframeSheet) (Object) this;

        /* 通用中文轨道名。以 channel id 为键：旧版传的是 IKey.constant 的实参
           （getTrackName(channel.getId())，即末段名），这里传完整 id，
           KeyframeLocalizer 自己会拆出 path/ 前缀，覆盖面更广。
           受「中文关键帧轨道名称」开关与游戏语言共同控制，非中文环境返回 null。 */
        String localized = KeyframeLocalizer.localize(sheet.id);

        if (localized == null)
        {
            /* 兜底：已通过 KeyframeTrackExtensionRegistry 注册的扩展轨道，用其默认英文名 */
            localized = KeyframeTrackExtensionRegistry.replaceTailWithDefaultName(sheet.id);
        }

        if (localized != null && !localized.equals(sheet.id))
        {
            sheet.title = IKey.constant(localized);
        }

        /* BBS++ / VFX 形态专属的轨道名、颜色与图标，放在后面以便覆盖上面的通用结果 */
        KeyframeTrackStyle.apply(sheet);
    }

    /**
     * 注入目标：{@link UIKeyframeSheet#icon(Icon)}。
     * 注入原因：调用方会在构造后再次按全局 key 设置图标，可能覆盖物品喷射专属图标。
     * 修改行为：当轨道属于 BBS++ 专属形态时，使用上下文图标并跳过全局图标。
     */
    @Inject(method = "icon", at = @At("HEAD"), cancellable = true, remap = true)
    private void bbspp$useContextualIcon(Icon icon, CallbackInfoReturnable<UIKeyframeSheet> cir)
    {
        Icon contextual = KeyframeTrackStyle.getIconOverride((UIKeyframeSheet) (Object) this);

        if (contextual != null)
        {
            this.icon = contextual;
            cir.setReturnValue((UIKeyframeSheet) (Object) this);
        }
    }
}
