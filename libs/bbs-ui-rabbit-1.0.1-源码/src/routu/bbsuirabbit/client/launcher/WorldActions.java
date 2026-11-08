package routu.bbsuirabbit.client.launcher;

import com.mojang.datafixers.util.Pair;
import net.minecraft.class_1109;
import net.minecraft.class_1928;
import net.minecraft.class_124;
import net.minecraft.class_155;
import net.minecraft.class_156;
import net.minecraft.class_1940;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_32;
import net.minecraft.class_3417;
import net.minecraft.class_34;
import net.minecraft.class_370;
import net.minecraft.class_403;
import net.minecraft.class_405;
import net.minecraft.class_410;
import net.minecraft.class_424;
import net.minecraft.class_437;
import net.minecraft.class_5218;
import net.minecraft.class_524;
import net.minecraft.class_525;
import net.minecraft.class_5244;
import net.minecraft.class_5250;
import net.minecraft.class_5317;
import net.minecraft.class_7193;
import net.minecraft.class_8100;
import net.minecraft.class_8586;

import java.io.File;
import java.nio.file.Path;

/**
 * What can be done to a save, written after vanilla's world list entry (play with its backup
 * and version prompts, edit, delete, recreate) so nothing vanilla offers goes missing; the
 * screens it opens come back to {@code parent}, the launcher.
 */
public final class WorldActions
{
    private WorldActions()
    {}

    private static class_310 mc()
    {
        return class_310.method_1551();
    }

    private static void click()
    {
        mc().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1F));
    }

    public static void play(class_437 parent, class_34 level)
    {
        class_310 mc = mc();

        if (level.method_33784())
        {
            return;
        }

        if (level instanceof class_34.class_8578)
        {
            mc.method_1507(new class_8586(parent));

            return;
        }

        class_34.class_5781 warning = level.method_33405();

        if (warning.method_33406())
        {
            class_5250 question = class_2561.method_43471("selectWorld.backupQuestion." + warning.method_33408());

            if (warning.method_33407())
            {
                question.method_27695(new class_124[] {class_124.field_1067, class_124.field_1061});
            }

            class_5250 text = class_2561.method_43469("selectWorld.backupWarning." + warning.method_33408(), new Object[] {level.method_258(), class_155.method_16673().method_48019()});

            mc.method_1507(new class_405(parent, (backup, eraseCache) ->
            {
                if (backup)
                {
                    backup(level.method_248());
                }

                start(parent, level);
            }, question, text, false));
        }
        else if (level.method_260())
        {
            mc.method_1507(new class_410((yes) ->
            {
                if (yes)
                {
                    try
                    {
                        start(parent, level);
                    }
                    catch (Exception e)
                    {
                        mc.method_1507(new class_403(() -> mc.method_1507(parent), class_2561.method_43471("selectWorld.futureworld.error.title"), class_2561.method_43471("selectWorld.futureworld.error.text")));
                    }
                }
                else
                {
                    mc.method_1507(parent);
                }
            }, class_2561.method_43471("selectWorld.versionQuestion"), class_2561.method_43469("selectWorld.versionWarning", new Object[] {level.method_258()}), class_2561.method_43471("selectWorld.versionJoinButton"), class_5244.field_24335));
        }
        else
        {
            start(parent, level);
        }
    }

    private static void start(class_437 parent, class_34 level)
    {
        class_310 mc = mc();

        click();

        if (mc.method_1586().method_230(level.method_248()))
        {
            mc.method_29970(new class_424(class_2561.method_43471("selectWorld.data_read")));
            mc.method_41735().method_41894(parent, level.method_248());
        }
    }

    /** Vanilla's backup (a zip in the backups folder); true when it worked. */
    public static boolean backup(String folder)
    {
        try (class_32.class_5143 session = mc().method_1586().method_52236(folder))
        {
            class_524.method_2701(session);

            return true;
        }
        catch (Exception e)
        {
            class_370.method_27023(mc(), folder);
            System.err.println("[BBS UI Rabbit] Failed to back up " + folder + ": " + e);

            return false;
        }
    }

    /** Vanilla's rename (EditWorldScreen's save): only level.dat's name changes, not the folder; true when it worked. */
    public static boolean rename(String folder, String name)
    {
        try (class_32.class_5143 session = mc().method_1586().method_52236(folder))
        {
            session.method_27008(name.trim());

            return true;
        }
        catch (Exception e)
        {
            class_370.method_27023(mc(), folder);
            System.err.println("[BBS UI Rabbit] Failed to rename " + folder + ": " + e);

            return false;
        }
    }

    public static void edit(class_437 parent, class_34 level, Runnable changed)
    {
        class_310 mc = mc();

        if (level instanceof class_34.class_8578)
        {
            mc.method_1507(new class_8586(parent));

            return;
        }

        mc.method_29970(new class_424(class_2561.method_43471("selectWorld.data_read")));

        String folder = level.method_248();

        try
        {
            class_32.class_5143 session = mc.method_1586().method_52236(folder);

            mc.method_1507(new class_524((saved) ->
            {
                try
                {
                    session.close();
                }
                catch (Exception e)
                {
                    System.err.println("[BBS UI Rabbit] Failed to unlock level " + folder + ": " + e);
                }

                if (saved)
                {
                    changed.run();
                }

                mc.method_1507(parent);
            }, session));
        }
        catch (Exception e)
        {
            class_370.method_27023(mc, folder);
            mc.method_1507(parent);
            changed.run();
        }
    }

    public static boolean delete(String folder)
    {
        try (class_32.class_5143 session = mc().method_1586().method_27002(folder))
        {
            session.method_27015();

            return true;
        }
        catch (Exception e)
        {
            class_370.method_27025(mc(), folder);
            System.err.println("[BBS UI Rabbit] Failed to delete world " + folder + ": " + e);

            return false;
        }
    }

    public static void recreate(class_437 parent, class_34 level)
    {
        class_310 mc = mc();

        if (level instanceof class_34.class_8578)
        {
            mc.method_1507(new class_8586(parent));

            return;
        }

        mc.method_29970(new class_424(class_2561.method_43471("selectWorld.data_read")));

        try (class_32.class_5143 session = mc.method_1586().method_52236(level.method_248()))
        {
            Pair<class_1940, class_7193> pair = mc.method_41735().method_45696(session);
            class_1940 info = pair.getFirst();
            class_7193 options = pair.getSecond();
            Path packs = class_525.method_29685(session.method_27010(class_5218.field_24186), mc);

            if (options.comp_616().method_28035())
            {
                mc.method_1507(new class_410((yes) -> mc.method_1507(yes ? class_525.method_40212(mc, parent, info, options, packs) : parent),
                    class_2561.method_43471("selectWorld.recreate.customized.title"), class_2561.method_43471("selectWorld.recreate.customized.text"), class_5244.field_24338, class_5244.field_24335));
            }
            else
            {
                mc.method_1507(class_525.method_40212(mc, parent, info, options, packs));
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Unable to recreate world: " + e);
            mc.method_1507(new class_403(() -> mc.method_1507(parent), class_2561.method_43471("selectWorld.recreate.error.title"), class_2561.method_43471("selectWorld.recreate.error.text")));
        }
    }

    public static void openFolder(File dir)
    {
        class_156.method_668().method_672(dir);
    }

    /* New worlds */

    private static String template;

    public static void create(class_437 parent, String preset)
    {
        template = preset;
        class_525.method_31130(mc(), parent);
    }

    /** Vanilla's create screen just opened: fill it from the template asked for, once. */
    public static void onCreateScreen(class_525 screen)
    {
        String preset = template;

        template = null;

        if (preset == null)
        {
            return;
        }

        try
        {
            class_8100 creator = screen.method_48657();

            if (preset.equals("flat"))
            {
                creator.method_48710(mchorse.bbs_mod.l10n.L10n.lang("rabbit.launcher.template.flat_name").get());
                creator.method_48704(class_8100.class_4539.field_20626);
                creator.method_48713(true);

                java.util.List<class_8100.class_8101> types = new java.util.ArrayList<>(creator.method_48732());

                types.addAll(creator.method_48733());

                for (class_8100.class_8101 type : types)
                {
                    if (type.comp_1238() != null && type.comp_1238().method_40225(class_5317.field_25054))
                    {
                        creator.method_48705(type);

                        break;
                    }
                }

                class_1928 rules = creator.method_48734().method_27325();

                rules.method_20746(class_1928.field_19396).method_20758(false, null);
                rules.method_20746(class_1928.field_19406).method_20758(false, null);
                rules.method_20746(class_1928.field_19390).method_20758(false, null);
                creator.method_48697(rules);
            }
        }
        catch (Throwable e)
        {
            System.err.println("[BBS UI Rabbit] Couldn't apply the world template " + preset + ": " + e);
        }
    }
}
