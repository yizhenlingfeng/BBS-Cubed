package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitMod;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The favourites file ({@code bbsplusplus/morph_favorites.json}) — a plain list of form
 * data the {@link FavoriteFormCategory} mirrors. Loading and saving is done here so the
 * category itself stays a dumb list of forms.
 */
public final class FavoriteForms
{
    private static final File FILE = BBSMod.getSettingsPath("bbs_ui_rabbit/morph_favorites.json");

    /** Set by {@link FavoriteFormCategory} so {@link #isFavorite(Form)} can cache by modCount. */
    private static FavoriteFormCategory category;

    private static int checkMod = -1;
    private static final List<MapType> checkData = new ArrayList<>();

    private FavoriteForms()
    {}

    static void bind(FavoriteFormCategory favoriteCategory)
    {
        category = favoriteCategory;
        checkMod = -1;
    }

    public static List<Form> load()
    {
        List<Form> forms = new ArrayList<>();

        if (!FILE.isFile())
        {
            return forms;
        }

        try
        {
            BaseType data = DataToString.read(FILE);

            if (data.isMap())
            {
                for (BaseType formData : data.asMap().getList("forms"))
                {
                    if (formData.isMap())
                    {
                        Form form = FormUtils.fromData(formData.asMap());

                        if (form != null)
                        {
                            forms.add(form);
                        }
                    }
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            RabbitMod.LOGGER.warn("读取收藏形态失败：{}", FILE.getAbsolutePath(), e);
        }

        return forms;
    }

    public static void save()
    {
        if (category == null)
        {
            return;
        }

        File parent = FILE.getParentFile();

        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
        {
            RabbitMod.LOGGER.warn("无法创建收藏形态目录：{}", parent.getAbsolutePath());

            return;
        }

        MapType data = new MapType();
        ListType forms = new ListType();

        for (Form form : category.getForms())
        {
            MapType formData = FormUtils.toData(form);

            if (formData != null)
            {
                forms.add(formData);
            }
        }

        data.put("forms", forms);

        if (!DataToString.writeSilently(FILE, data, true))
        {
            RabbitMod.LOGGER.warn("保存收藏形态失败：{}", FILE.getAbsolutePath());
        }
    }

    /** Whether a form equal to this one (by serialized data) is favourited. */
    public static boolean isFavorite(Form form)
    {
        if (category == null || form == null)
        {
            return false;
        }

        refreshCheck();

        MapType data = FormUtils.toData(form);

        return data != null && indexOf(data) >= 0;
    }

    public static void toggle(Form form)
    {
        if (category == null || form == null)
        {
            return;
        }

        MapType data = FormUtils.toData(form);

        if (data == null)
        {
            return;
        }

        refreshCheck();

        int index = indexOf(data);

        if (index >= 0)
        {
            category.removeForm(category.getForms().get(index));
        }
        else
        {
            category.addForm(FormUtils.copy(form));
        }
    }

    public static void toggleAll(List<Form> forms)
    {
        for (Form form : new ArrayList<>(forms))
        {
            toggle(form);
        }
    }

    /** Lenient structural match — favorites reloaded from disk hold doubles where live forms hold floats. */
    private static int indexOf(MapType data)
    {
        for (int i = 0; i < checkData.size(); i++)
        {
            if (BaseType.equals(checkData.get(i), data))
            {
                return i;
            }
        }

        return -1;
    }

    /** Rebuilds the serialized copies the membership check works on when the category changed. */
    private static void refreshCheck()
    {
        if (category != null && checkMod == category.getModCount())
        {
            return;
        }

        checkData.clear();

        for (Form form : category.getForms())
        {
            MapType formData = FormUtils.toData(form);

            if (formData != null)
            {
                checkData.add(formData);
            }
        }

        checkMod = category == null ? -1 : category.getModCount();
    }
}
