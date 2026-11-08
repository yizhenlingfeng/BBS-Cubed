package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitSettings;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.base.BaseValue;

public final class MorphingDefaultCategory
{
    public static final String HOME = "home";

    private MorphingDefaultCategory()
    {}

    public static String get()
    {
        if (RabbitSettings.morphingDefaultCategory == null)
        {
            return HOME;
        }

        String value = RabbitSettings.morphingDefaultCategory.get();

        return value == null || value.isEmpty() ? HOME : value;
    }

    public static boolean isDefault(String id)
    {
        return get().equals(id == null || id.isEmpty() ? HOME : id);
    }

    public static void set(String id)
    {
        if (RabbitSettings.morphingDefaultCategory != null)
        {
            String value = id == null || id.isEmpty() ? HOME : id;

            if (value.equals(get()))
            {
                return;
            }

            RabbitSettings.morphingDefaultCategory.set(value);
            saveNow();
        }
    }

    public static void saveNow()
    {
        save(RabbitSettings.morphingDefaultCategory);
    }

    /** Persists any of the bbspp morphing settings (they share the same root file). */
    public static void save(BaseValue value)
    {
        if (value == null)
        {
            return;
        }

        BaseValue root = value.getRoot();

        if (root instanceof Settings settings)
        {
            settings.save();
        }
    }
}
