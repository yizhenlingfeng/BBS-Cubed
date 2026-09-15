package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Makes the Anchor actor picker use BBS's own scrollable context-menu list.
 *
 * <p>The stock picker adds all actors to a menu but assumes actor ids are
 * contiguous.  The editor can contain more actors, or have holes after
 * removing one, so iterating the map entries keeps every real actor usable.</p>
 *
 * <p>2.6 起 actor 标识由 int 编号改为 String 名称，entities 为 {@code Map<String, IEntity>}，
 * 回调为 {@code Consumer<String>}。</p>
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

        List<Map.Entry<String, IEntity>> entries = new ArrayList<>(entities.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));

        context.replaceContextMenu((manager) ->
        {
            manager.custom(menu);
            manager.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(""));

            for (Map.Entry<String, IEntity> entry : entries)
            {
                String actor = entry.getKey();
                IEntity entity = entry.getValue();

                if (entity == null)
                {
                    continue;
                }

                Form form = entity.getForm();
                String label = actor + (form == null ? "" : " - " + form.getFormIdOrName());

                IKey key = IKey.constant(label);
                manager.action(Icons.CLOSE, key, value != null && value.equals(actor), () -> callback.accept(actor));
            }
        });

        ci.cancel();
    }
}
