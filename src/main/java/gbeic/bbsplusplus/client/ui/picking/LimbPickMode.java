package gbeic.bbsplusplus.client.ui.picking;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.Pair;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * 肢体选择模式（按住「肢体选择」键，默认 E）的全局状态与拾取辅助。
 *
 * <p>只在 BBS 界面内生效，文本框获得焦点时让路（E 是字母键）。按住时：左键忽略骨骼覆盖，
 * 选中点击到的实际肢体；右键按骨骼覆盖映射。模板缓冲区在渲染时生成、拾取结果在点击时读取，
 * 所以按住时模板一律记录实际肢体，读取结果时再决定是否映射，见 {@link #respectOverrides()}
 * 与 {@link #withPickingOverrides(Pair)}。</p>
 */
public final class LimbPickMode
{
    private LimbPickMode()
    {}

    /**
     * 按键是否按住（仅判断 BBS 界面）。用于拿不到 {@link UIContext} 的调用点，
     * 例如影片编辑器的世界渲染与模板缓冲区的骨骼拾取。
     */
    public static boolean held()
    {
        if (!SnowUIKeys.IGNORE_PICKING_OVERRIDE_KEY.isHeld())
        {
            return false;
        }

        return MinecraftClient.getInstance().currentScreen instanceof UIScreen;
    }

    /**
     * 按键是否按住，文本框输入时让路（E 是字母键）。
     */
    public static boolean held(UIContext context)
    {
        return context != null && !context.isFocused() && held();
    }

    /**
     * 影片编辑器里当前选中的演员实体（没有打开影片面板时为 null），用来把这个模型压在其他模型之前。
     */
    public static IEntity selectedEntity()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu instanceof UIDashboard dashboard && dashboard.getPanels().panel instanceof UIFilmPanel filmPanel)
        {
            UIFilmController controller = filmPanel.getController();

            return controller == null ? null : controller.getCurrentEntity();
        }

        return null;
    }

    /**
     * 这次拾取是否要「按骨骼覆盖来选」：按住按键且按着右键时为真，否则拾取结果就是实际肢体。
     */
    public static boolean respectOverrides()
    {
        return held() && Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    /**
     * 把拾取到的实际肢体按模型的骨骼覆盖映射一遍，得到与平常拾取一致的骨骼。
     * 不是模型表单、或该模型没有配置骨骼覆盖时原样返回。
     */
    public static Pair<Form, String> withPickingOverrides(Pair<Form, String> pair)
    {
        if (pair == null || pair.a == null || pair.b == null || pair.b.isEmpty())
        {
            return pair;
        }

        if (!(pair.a instanceof ModelForm form))
        {
            return pair;
        }

        ModelInstance model = ModelFormRenderer.getModel(form);

        if (model == null || model.config == null)
        {
            return pair;
        }

        String bone = model.config.getPickingOverrides().getOrDefault(pair.b, pair.b);

        return bone.equals(pair.b) ? pair : new Pair<>(pair.a, bone);
    }
}
