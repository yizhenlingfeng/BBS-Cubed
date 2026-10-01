package bbslezy.mixin.client;

import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two buttons on the replay list's toolbar that jump to the top and the bottom of the list, so
 * reaching the end of a thousand replays does not mean a scroll that takes one.
 *
 * <p>Both set the scroll directly rather than animating it, because "instant" was the point: the
 * target is a position, not a motion. The bottom is computed from the row height, so it stays
 * right when the list grows between two presses. The two icons cost the search box 44px of its
 * width, which the toolbar's own layout already sizes by remaining space.</p>
 */
@Mixin(value = UIReplaysListPanel.class, remap = false)
public class ReplaysListPanelMixin
{
    private static final int SCROLL_ICON_SIZE = 20;
    private static final int SCROLL_ICON_MARGIN = 2;

    @Final
    @Shadow
    public UIReplayList replays;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbslezy$addScrollButtons(CallbackInfo ci)
    {
        UIReplaysListPanel self = (UIReplaysListPanel) (Object) this;

        UIIcon toTop = new UIIcon(Icons.ARROW_UP, (b) ->
        {
            this.replays.scroll.setScroll(0);
        });

        UIIcon toBottom = new UIIcon(Icons.ARROW_DOWN, (b) ->
        {
            Scroll scroll = this.replays.scroll;
            int last = this.replays.getList().size() - 1;

            scroll.setScroll(last * scroll.scrollItemSize);
        });

        toTop.relative(self.bar).x(1F, -2 * SCROLL_ICON_SIZE - SCROLL_ICON_MARGIN).y(0).w(SCROLL_ICON_SIZE).h(20);
        toBottom.relative(self.bar).x(1F, -SCROLL_ICON_SIZE).y(0).w(SCROLL_ICON_SIZE).h(20);

        self.bar.add(toTop, toBottom);
        self.search.relative(self.bar).w(1F, -SCROLL_ICON_SIZE - SCROLL_ICON_MARGIN - 2 * SCROLL_ICON_SIZE - SCROLL_ICON_MARGIN);
    }
}
