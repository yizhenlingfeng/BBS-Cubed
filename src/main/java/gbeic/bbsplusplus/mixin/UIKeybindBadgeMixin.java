package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.util.keybind.ConflictIndex;
import gbeic.bbsplusplus.util.keybind.KeybindConflictSettings;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 按键绑定设置界面的快捷键冲突提示。
 *
 * <p>仅在「BBS 增强」设置中开启本功能、且 UIKeybind 位于 UISettingsOverlayPanel 内时生效：
 * 重复的按键字体标黄，按键框左侧显示黄色叹号；悬停列出共用此键的全部功能；
 * 点击叹号跳转到冲突项（同分类滚动，跨分类自动切换并滚动到位）。</p>
 */
@Mixin(UIKeybind.class)
public abstract class UIKeybindBadgeMixin
{
    @Shadow public KeyCombo combo;
    @Shadow public boolean reading;

    @Unique private static final int bbspp$YELLOW = 0xffffd400;
    @Unique private static final int bbspp$TIP_BG = 0xee1c1f26;
    @Unique private static final IKey bbspp$TIP_HEADER =
        L10n.lang("bbspp.ui.keybind_conflict.tooltip_header");

    /** 跨分类跳转暂存的目标指纹，由新分类的目标行首次渲染时消费。 */
    @Unique private static String bbspp$pendingFingerprint;

    /* ── 按键文字染黄 ─────────────────────────────────────────────── */

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/utils/Batcher2D;textShadow(Ljava/lang/String;FF)V"
        )
    )
    private void bbspp$yellow(Batcher2D batcher, String label, float x, float y)
    {
        if (bbspp$active() && !ConflictIndex.conflictsOf(this.combo).isEmpty())
            batcher.textShadow(label, x, y, bbspp$YELLOW);
        else
            batcher.textShadow(label, x, y);
    }

    /* ── TAIL：叹号 + tooltip + 跨分类跳转落地 ────────────────────── */

    @Inject(method = "render", at = @At("TAIL"))
    private void bbspp$tail(UIContext context, CallbackInfo ci)
    {
        if (!bbspp$active()) return;

        UIKeybind self = (UIKeybind) (Object) this;

        /* 跨分类跳转：新分类的目标行首次渲染时自动滚到自身 */
        if (bbspp$pendingFingerprint != null
            && bbspp$pendingFingerprint.equals(ConflictIndex.fingerprint(this.combo)))
        {
            bbspp$pendingFingerprint = null;
            bbspp$scrollInto(self);
        }

        List<ConflictIndex.Entry> conflicts = ConflictIndex.conflictsOf(this.combo);
        if (conflicts.isEmpty() || this.reading || this.combo.keys.isEmpty()) return;

        FontRenderer font = context.batcher.getFont();
        int fh = font.getHeight();
        int exX = self.area.x - 9;
        int exY = self.area.my(fh) - fh;
        context.batcher.text("!", exX, exY, bbspp$YELLOW);

        boolean hover = context.mouseX >= exX - 2 && context.mouseX <= exX + 8
            && context.mouseY >= self.area.y && context.mouseY <= self.area.ey();
        if (!hover) return;

        List<String> lines = new ArrayList<>();
        lines.add(bbspp$TIP_HEADER.get());
        for (ConflictIndex.Entry e : conflicts)
        {
            /* 排除自身：UIKeybind.combo 就是 ValueKeyCombo 内部存储的本体引用 */
            if (e.combo == self.combo) continue;
            lines.add("· " + e.name() + " (" + e.keys() + ")");
        }
        if (lines.size() <= 1) return;

        int pad = 6;
        int rowH = fh + 3;
        int w = 0;
        for (String l : lines) w = Math.max(w, font.getWidth(l));
        int h = lines.size() * rowH;
        int tx = Math.min(context.mouseX + 10, context.getViewport().ex() - w - pad * 2);
        int ty = Math.max(context.mouseY - h - pad * 2, context.getViewport().y + 2);

        context.batcher.box(tx, ty, tx + w + pad * 2, ty + h + pad * 2, bbspp$TIP_BG);
        for (int i = 0; i < lines.size(); i++)
        {
            context.batcher.text(lines.get(i), tx + pad, ty + pad + i * rowH, 0xffffffff);
        }
    }

    /* ── 点击叹号 → 跳转 ─────────────────────────────────────────── */

    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbspp$click(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!bbspp$active() || context.mouseButton != 0 || this.reading) return;

        UIKeybind self = (UIKeybind) (Object) this;
        int exX = self.area.x - 9;
        boolean hover = context.mouseX >= exX - 2 && context.mouseX <= exX + 8
            && context.mouseY >= self.area.y && context.mouseY <= self.area.ey();
        if (!hover) return;

        UISettingsOverlayPanel panel = bbspp$panel(self);

        /* 同分类：直接遍历当前 options 树，找同键且非自己的行 */
        if (panel != null)
        {
            List<UIKeybind> widgets = new ArrayList<>();
            bbspp$collect(panel.options, widgets);
            String myFingerprint = ConflictIndex.fingerprint(this.combo);
            for (UIKeybind other : widgets)
            {
                if (other != self
                    && ConflictIndex.fingerprint(other.combo).equals(myFingerprint))
                {
                    bbspp$scrollInto(other);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        /* 跨分类：切分类后由新分类的目标行自动滚到位 */
        List<ConflictIndex.Entry> conflicts = ConflictIndex.conflictsOf(this.combo);
        if (conflicts.isEmpty() || panel == null)
        {
            cir.setReturnValue(true);
            return;
        }

        ConflictIndex.Entry target = conflicts.get(0);
        bbspp$pendingFingerprint = ConflictIndex.fingerprint(target.combo);

        Settings keybinds = BBSMod.getSettings().modules.get(ConflictIndex.MODULE);
        if (keybinds == null)
        {
            cir.setReturnValue(true);
            return;
        }
        ValueGroup group = keybinds.categories.get(target.groupId);
        if (group != null) panel.selectCategory(group);

        cir.setReturnValue(true);
    }

    /* ── 改绑完成 → 索引失效 ─────────────────────────────────────── */

    @Inject(method = "finish", at = @At("TAIL"))
    private void bbspp$dirty(CallbackInfo ci)
    {
        ConflictIndex.markDirty();
    }

    /* ── 工具方法 ────────────────────────────────────────────────── */

    @Unique
    private boolean bbspp$active()
    {
        return KeybindConflictSettings.isEnabled()
            && bbspp$panel((UIKeybind) (Object) this) != null;
    }

    @Unique
    private static UISettingsOverlayPanel bbspp$panel(UIElement e)
    {
        while (e != null)
        {
            if (e instanceof UISettingsOverlayPanel) return (UISettingsOverlayPanel) e;
            e = e.getParent();
        }
        return null;
    }

    @Unique
    private static void bbspp$collect(UIElement root, List<UIKeybind> out)
    {
        if (root instanceof UIKeybind) out.add((UIKeybind) root);
        for (IUIElement child : root.getChildren())
        {
            if (child instanceof UIElement c) bbspp$collect(c, out);
        }
    }

    @Unique
    private static void bbspp$scrollInto(UIKeybind target)
    {
        UISettingsOverlayPanel panel = bbspp$panel(target);
        if (panel == null) return;
        int y = target.area.y - panel.options.area.y;
        panel.options.scroll.scrollIntoView(y, target.area.h, 5);
    }
}
