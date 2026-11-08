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
 * <p>只在 BBS 界面打开时生效，避免在游戏里走动时误触发；文本框获得焦点时让路，因为 E 是字母键。
 * 按住时：左键拾取忽略骨骼覆盖，选中点击到的实际肢体；右键拾取按骨骼覆盖映射；操控器、坐标轴、
 * 摄像机参考线与 IK 调试层隐藏；模型改用原版实体着色器；影片 / 场景预览里当前选中的演员压在其他
 * 模型之前。</p>
 *
 * <p>模板缓冲区在渲染时生成，而拾取结果在点击时才读取，两者不在同一时刻。为此按住按键时模板一律
 * 记录实际肢体（原始骨骼），到读取拾取结果时再按鼠标键决定是否映射，见 {@link #respectOverrides()}
 * 与 {@link #withPickingOverrides(Pair)}。</p>
 */
public final class LimbPickMode
{
    private LimbPickMode()
    {}

    /**
     * 按键是否处于按住状态（仅判断 BBS 界面）。用于拿不到 {@link UIContext} 的调用点，
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
     * 按键是否处于按住状态，且在文本框输入时自动让路（E 是字母键）。
     */
    public static boolean held(UIContext context)
    {
        return context != null && !context.isFocused() && held();
    }

    /**
     * 影片编辑器里当前选中的演员实体（没有打开影片面板时为 null）。
     *
     * <p>用来把「选中的那个模型」压在其他模型之前，而不是整场景一起改绘制顺序。</p>
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
     * 按住按键时，这次拾取是否要「按骨骼覆盖来选」（即按住右键）。
     *
     * <p>左键（或没按键）时返回 false，拾取结果就是点击到的实际肢体。</p>
     */
    public static boolean respectOverrides()
    {
        return held() && Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    /**
     * 把拾取到的实际肢体按模型的骨骼覆盖映射一遍，得到与未按住按键时相同的拾取结果。
     *
     * <p>{@code pair.a} 不是模型表单、或该模型没有配置骨骼覆盖时，原样返回。</p>
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
