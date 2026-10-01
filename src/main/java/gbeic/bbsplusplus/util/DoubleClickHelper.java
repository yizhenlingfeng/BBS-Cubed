package gbeic.bbsplusplus.util;

import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.UIKeyframeClip;
import mchorse.bbs_mod.ui.film.clips.UIRemapperClip;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;

/**
 * 双击剪辑辅助工具 — 直接调用剪辑编辑面板的 "编辑" 按钮。
 * <p>
 * 代替 {@link DblClickHandler} 接口链，避免 mixin {@code @Implements}
 * 的兼容问题。
 * </p>
 */
public class DoubleClickHelper
{
    /**
     * 尝试触发剪辑编辑面板的编辑按钮。
     *
     * @param clipPanel 当前打开的剪辑编辑面板
     * @return 如果成功触发了编辑则返回 {@code true}
     */
    public static boolean triggerEdit(UIClip<?> clipPanel)
    {
        if (clipPanel instanceof UIKeyframeClip)
        {
            ((UIKeyframeClip) clipPanel).edit.clickItself();
            return true;
        }

        if (clipPanel instanceof UIRemapperClip)
        {
            ((UIRemapperClip) clipPanel).edit.clickItself();
            return true;
        }

        if (clipPanel instanceof UICurveClip)
        {
            ((UICurveClip) clipPanel).edit.clickItself();
            return true;
        }

        /* 通用分支：对 addon 提供的自定义 UI clip（如 BBS Lezy 的
           UIColorClip / UICinematicClip / UILetterboxClip / UIVignetteClip），
           通过反射查找 public edit 字段并触发点击，避免主模组硬编码依赖 addon 类。 */
        try
        {
            java.lang.reflect.Field field = findEditField(clipPanel.getClass());

            if (field != null)
            {
                UIButton edit = (UIButton) field.get(clipPanel);

                if (edit != null)
                {
                    edit.clickItself();
                    return true;
                }
            }
        }
        catch (Exception ignored)
        {}

        return false;
    }

    /** 沿类层级向上查找名为 edit 或 editAll 的 UIButton 字段 */
    private static java.lang.reflect.Field findEditField(Class<?> clazz) throws IllegalAccessException
    {
        String[] names = {"edit", "editAll"};

        while (clazz != null && clazz != Object.class)
        {
            for (String name : names)
            {
                try
                {
                    java.lang.reflect.Field field = clazz.getDeclaredField(name);

                    if (UIButton.class.isAssignableFrom(field.getType()))
                    {
                        field.setAccessible(true);
                        return field;
                    }
                }
                catch (NoSuchFieldException ignored)
                {}
            }

            clazz = clazz.getSuperclass();
        }

        return null;
    }
}
