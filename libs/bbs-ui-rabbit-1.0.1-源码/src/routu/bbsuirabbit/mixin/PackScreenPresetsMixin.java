package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.packs.PackPresetScreen;
import routu.bbsuirabbit.client.packs.PackPresets;
import mchorse.bbs_mod.l10n.L10n;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_5369;
import net.minecraft.class_5375;
import net.minecraft.class_7919;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource pack presets on the resource pack screen: a row under Open Folder / Done with up to four
 * presets (one click switches to that combination and reloads, like picking the packs and pressing
 * Done; the one already selected is greyed out) and "预设…" to save, update or delete them
 * ({@link PackPresetScreen}). The data pack screen uses the same class and gets nothing.
 */
@Mixin(value = class_5375.class, remap = false)
public abstract class PackScreenPresetsMixin extends class_437
{
    @Unique
    private static final int BBSPP$QUICK = 4;

    @Shadow(remap = false)
    @Final
    private class_5369 field_25468;

    /** The quick buttons and their presets, greyed out while the right column already matches. */
    @Unique
    private final List<class_4185> rabbit$quickButtons = new ArrayList<>();
    @Unique
    private final List<PackPresets.Preset> rabbit$quickPresets = new ArrayList<>();

    private PackScreenPresetsMixin(class_2561 title)
    {
        super(title);
    }

    @Inject(method = "method_25426", at = @At("TAIL"), remap = false)
    private void rabbit$addPresetRow(CallbackInfo ci)
    {
        PackOrganizerAccessor organizer = (PackOrganizerAccessor) this.field_25468;
        class_310 client = class_310.method_1551();

        this.rabbit$quickButtons.clear();
        this.rabbit$quickPresets.clear();

        if (!routu.bbsuirabbit.RabbitSettings.isOn(routu.bbsuirabbit.RabbitSettings.packPresets))
        {
            return;
        }

        if (organizer.rabbit$getManager() != client.method_1520())
        {
            return;
        }

        class_5375 screen = (class_5375) (Object) this;
        List<PackPresets.Preset> presets = PackPresets.all();
        int left = this.field_22789 / 2 - 154;
        int y = this.field_22790 - 24;
        int manageWidth = 64;
        int shown = Math.min(BBSPP$QUICK, presets.size());
        int room = 308 - manageWidth - 4;

        for (int i = 0; i < shown; i++)
        {
            PackPresets.Preset preset = presets.get(i);
            int x = left + room * i / shown;
            int width = room * (i + 1) / shown - room * i / shown - 4;
            String label = this.field_22793.method_27523(preset.name, width - 8);
            class_4185 button = class_4185.method_46430(class_2561.method_43470(label), (b) -> PackPresetScreen.applyAndReload(client, screen, organizer, preset))
                .method_46434(x, y, width, 20)
                .method_46436(class_7919.method_47407(class_2561.method_43470(L10n.lang("rabbit.pack_presets.quick_tooltip").format(preset.name, preset.packs.size()).get())))
                .method_46431();

            button.field_22763 = !PackPresets.isCurrent(organizer, preset);
            this.rabbit$quickButtons.add(button);
            this.rabbit$quickPresets.add(preset);
            this.method_37063(button);
        }

        String manage = L10n.lang(presets.isEmpty() ? "rabbit.pack_presets.button_empty" : "rabbit.pack_presets.button").get();
        int manageX = shown == 0 ? left : left + room;
        int width = shown == 0 ? 308 : manageWidth;

        this.method_37063(class_4185.method_46430(class_2561.method_43470(manage), (b) -> client.method_1507(new PackPresetScreen(screen, organizer)))
            .method_46434(manageX, y, width, 20)
            .method_46436(class_7919.method_47407(class_2561.method_43470(L10n.lang("rabbit.pack_presets.button_tooltip").get())))
            .method_46431());
    }

    /** Packs moved by hand: which preset matches changes with them. */
    @Inject(method = "method_25393", at = @At("TAIL"), remap = false)
    private void rabbit$refreshPresetRow(CallbackInfo ci)
    {
        if (this.rabbit$quickButtons.isEmpty())
        {
            return;
        }

        PackOrganizerAccessor organizer = (PackOrganizerAccessor) this.field_25468;

        for (int i = 0; i < this.rabbit$quickButtons.size(); i++)
        {
            this.rabbit$quickButtons.get(i).field_22763 = !PackPresets.isCurrent(organizer, this.rabbit$quickPresets.get(i));
        }
    }
}
