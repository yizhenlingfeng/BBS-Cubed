package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.ReplayContextAction;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 限制锚点演员选择菜单的高度，同时沿用 BBS 2.7 的回放顺序和显示名。
 *
 * <p>BBS 2.7 的 {@code displayActors} 已按影片的回放列表生成条目，并用
 * {@link Replay#getName()} 显示；本 mixin 按同样的顺序和命名重建菜单，
 * 只额外限制高度，避免演员较多时菜单超出屏幕。</p>
 */
@Mixin(value = UIAnchorKeyframeFactory.class, remap = false)
public abstract class UIAnchorKeyframeFactoryMixin
{
    private static final int ACTOR_PICKER_VISIBLE_ROWS = 10;
    private static final int ACTOR_PICKER_ROW_HEIGHT = 20;

    @Inject(method = "displayActors", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void bbspp_cml$displayScrollableActors(
        UIContext context,
        Map<String, IEntity> entities,
        String value,
        Consumer<String> callback,
        CallbackInfo ci)
    {
        List<UIFilmPanel> children = context.menu.main.getChildren(UIFilmPanel.class);
        UIFilmPanel panel = children.isEmpty() ? null : children.get(0);
        List<Replay> replays = panel != null ? panel.getData().replays.getList() : List.of();

        UISimpleContextMenu menu = new UISimpleContextMenu()
        {
            @Override
            public void setMouse(UIContext menuContext)
            {
                super.setMouse(menuContext);

                /* Keep the initial window compact; UISimpleContextMenu's list still retains every action and scrolls. */
                this.maxH(Math.min(
                    menuContext.menu.height - 10,
                    ACTOR_PICKER_VISIBLE_ROWS * ACTOR_PICKER_ROW_HEIGHT));
            }
        };
        menu.actions.scroll.scrollItemSize = ACTOR_PICKER_ROW_HEIGHT;

        context.replaceContextMenu((manager) ->
        {
            manager.custom(menu);
            manager.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(""));

            for (Replay replay : replays)
            {
                String actor = replay.getId();
                IEntity entity = entities.get(actor);

                if (entity == null)
                {
                    continue;
                }

                int color = actor.equals(value) ? BBSSettings.primaryColor(0) : 0;

                manager.action(new ReplayContextAction(
                    replay,
                    IKey.raw(replay.getName()),
                    () -> callback.accept(actor),
                    color));
            }
        });

        ci.cancel();
    }
}
