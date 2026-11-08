package routu.bbsuirabbit.client.packs;

import routu.bbsuirabbit.mixin.PackOrganizerAccessor;
import mchorse.bbs_mod.l10n.L10n;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_370;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_5375;
import net.minecraft.class_7919;

import java.util.List;

/**
 * Managing resource pack presets, opened from the resource pack screen's "预设…" button: apply one,
 * save the screen's current selection as a new preset or over an existing one, delete one (two clicks).
 * Applying works like picking the packs by hand and pressing Done: the game reloads its resources.
 */
public class PackPresetScreen extends class_437
{
    private static final int ROW = 24;
    private static final int NAME_WIDTH = 200;
    private static final int SMALL_WIDTH = 56;

    private final class_5375 packScreen;
    private final PackOrganizerAccessor organizer;

    private class_342 nameField;
    private int page;
    /** The preset whose delete button was clicked once, waiting for the second click. */
    private PackPresets.Preset armedDelete;

    public PackPresetScreen(class_5375 packScreen, PackOrganizerAccessor organizer)
    {
        super(text("rabbit.pack_presets.title"));

        this.packScreen = packScreen;
        this.organizer = organizer;
    }

    static class_2561 text(String key)
    {
        return class_2561.method_43470(L10n.lang(key).get());
    }

    static class_2561 text(String key, Object... args)
    {
        return class_2561.method_43470(L10n.lang(key).format(args).get());
    }

    @Override
    protected void method_25426()
    {
        List<PackPresets.Preset> presets = PackPresets.all();
        int perPage = this.perPage();
        int pages = Math.max(1, (presets.size() + perPage - 1) / perPage);

        this.page = Math.max(0, Math.min(this.page, pages - 1));

        int rowWidth = NAME_WIDTH + 4 + SMALL_WIDTH + 4 + SMALL_WIDTH;
        int x = this.field_22789 / 2 - rowWidth / 2;
        int y = 32;

        for (int i = this.page * perPage; i < Math.min(presets.size(), (this.page + 1) * perPage); i++)
        {
            PackPresets.Preset preset = presets.get(i);
            boolean current = PackPresets.isCurrent(this.organizer, preset);
            class_4185 apply = class_4185.method_46430(class_2561.method_43470((current ? "✔ " : "") + preset.name), (b) -> this.apply(preset))
                .method_46434(x, y, NAME_WIDTH, 20)
                .method_46436(class_7919.method_47407(text("rabbit.pack_presets.apply_tooltip", preset.packs.size())))
                .method_46431();
            class_4185 update = class_4185.method_46430(text("rabbit.pack_presets.update"), (b) -> this.update(preset))
                .method_46434(x + NAME_WIDTH + 4, y, SMALL_WIDTH, 20)
                .method_46436(class_7919.method_47407(text("rabbit.pack_presets.update_tooltip")))
                .method_46431();
            class_4185 delete = class_4185.method_46430(text(this.armedDelete == preset ? "rabbit.pack_presets.delete_confirm" : "rabbit.pack_presets.delete"), (b) -> this.delete(preset))
                .method_46434(x + NAME_WIDTH + 4 + SMALL_WIDTH + 4, y, SMALL_WIDTH, 20)
                .method_46431();

            this.method_37063(apply);
            this.method_37063(update);
            this.method_37063(delete);

            y += ROW;
        }

        if (pages > 1)
        {
            class_4185 previous = class_4185.method_46430(class_2561.method_43470("<"), (b) -> this.turn(-1))
                .method_46434(this.field_22789 / 2 - 60, this.field_22790 - 80, 20, 20)
                .method_46431();
            class_4185 next = class_4185.method_46430(class_2561.method_43470(">"), (b) -> this.turn(1))
                .method_46434(this.field_22789 / 2 + 40, this.field_22790 - 80, 20, 20)
                .method_46431();

            previous.field_22763 = this.page > 0;
            next.field_22763 = this.page < pages - 1;
            this.method_37063(previous);
            this.method_37063(next);
        }

        String typed = this.nameField == null ? "" : this.nameField.method_1882();

        this.nameField = new class_342(this.field_22793, this.field_22789 / 2 - 154, this.field_22790 - 52, 200, 20, text("rabbit.pack_presets.name"));
        this.nameField.method_1880(48);
        this.nameField.method_1852(typed);
        this.nameField.method_47404(text("rabbit.pack_presets.name_hint"));
        this.method_37063(this.nameField);

        this.method_37063(class_4185.method_46430(text("rabbit.pack_presets.save"), (b) -> this.saveNew())
            .method_46434(this.field_22789 / 2 + 50, this.field_22790 - 52, 104, 20)
            .method_46436(class_7919.method_47407(text("rabbit.pack_presets.save_tooltip")))
            .method_46431());
        this.method_37063(class_4185.method_46430(text("rabbit.pack_presets.back"), (b) -> this.method_25419())
            .method_46434(this.field_22789 / 2 - 154, this.field_22790 - 28, 308, 20)
            .method_46431());
    }

    private int perPage()
    {
        return Math.max(1, (this.field_22790 - 32 - 88) / ROW);
    }

    private void turn(int by)
    {
        this.page += by;
        this.armedDelete = null;
        this.rebuild();
    }

    private void rebuild()
    {
        this.method_25423(this.field_22787, this.field_22789, this.field_22790);
    }

    private void apply(PackPresets.Preset preset)
    {
        applyAndReload(this.field_22787, this.packScreen, this.organizer, preset);
    }

    /**
     * Back to the pack screen (its columns refresh as it opens), the preset into its columns, then what
     * its Done button does: the selection goes to the game, which reloads resources.
     */
    public static void applyAndReload(class_310 client, class_5375 packScreen, PackOrganizerAccessor organizer, PackPresets.Preset preset)
    {
        if (client.field_1755 != packScreen)
        {
            client.method_1507(packScreen);
        }

        List<String> missing = PackPresets.apply(organizer, preset);

        if (!missing.isEmpty())
        {
            class_370.method_1990(client.method_1566(), class_370.class_371.field_23774,
                text("rabbit.pack_presets.missing_title", preset.name),
                class_2561.method_43470(String.join(", ", missing)));
        }

        packScreen.method_25419();
    }

    private void update(PackPresets.Preset preset)
    {
        preset.packs.clear();
        preset.packs.addAll(PackPresets.current(this.organizer));
        PackPresets.save();
        this.armedDelete = null;
        this.toast(text("rabbit.pack_presets.updated", preset.name));
        this.rebuild();
    }

    private void delete(PackPresets.Preset preset)
    {
        if (this.armedDelete != preset)
        {
            this.armedDelete = preset;
            this.rebuild();

            return;
        }

        PackPresets.all().remove(preset);
        PackPresets.save();
        this.armedDelete = null;
        this.rebuild();
    }

    private void saveNew()
    {
        String name = this.nameField.method_1882().trim();

        if (name.isEmpty())
        {
            name = L10n.lang("rabbit.pack_presets.default_name").format(PackPresets.all().size() + 1).get();
        }

        for (PackPresets.Preset preset : PackPresets.all())
        {
            if (preset.name.equals(name))
            {
                /* Same name: the existing one takes the new combination */
                this.nameField.method_1852("");
                this.update(preset);

                return;
            }
        }

        PackPresets.all().add(new PackPresets.Preset(name, PackPresets.current(this.organizer)));
        PackPresets.save();
        this.nameField.method_1852("");
        this.armedDelete = null;
        this.page = (PackPresets.all().size() - 1) / this.perPage();
        this.toast(text("rabbit.pack_presets.saved", name));
        this.rebuild();
    }

    private void toast(class_2561 title)
    {
        class_370.method_1990(this.field_22787.method_1566(), class_370.class_371.field_2219, title, null);
    }

    @Override
    public void method_25394(class_332 context, int mouseX, int mouseY, float delta)
    {
        this.method_25420(context);
        context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, 8, 0xFFFFFF);
        context.method_27534(this.field_22793, text("rabbit.pack_presets.hint"), this.field_22789 / 2, 20, 0xA0A0A0);

        if (PackPresets.all().isEmpty())
        {
            context.method_27534(this.field_22793, text("rabbit.pack_presets.empty"), this.field_22789 / 2, 44, 0x808080);
        }

        super.method_25394(context, mouseX, mouseY, delta);
    }

    /** Escape and Back return to the pack screen, nothing applied. */
    @Override
    public void method_25419()
    {
        this.field_22787.method_1507(this.packScreen);
    }
}
