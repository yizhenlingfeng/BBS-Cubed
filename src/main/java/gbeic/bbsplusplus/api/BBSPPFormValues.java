package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.forms.values.ValueActionsConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * BBS++ 注入到 BBS 原生表单上的扩展属性的统一读取入口。
 *
 * <p>这些属性（UV 变换、纹理调色、molang 互通、动作覆盖层等）原先由一组
 * Form 构造器 Mixin 注册、并经 duck 接口（{@code TextureGradeProvider} 等）
 * 读取；现在改由 {@code BBSPlusPlusAddon} 订阅 {@code RegisterFormModifiersEvent}
 * 统一注册，本类按属性 ID 从 form 值树中直接取回，duck 接口与 Mixin 一并移除。</p>
 *
 * <p>取不到时返回 {@code null}：值不存在即"该 form 不支持此特性"（BBS 调色板
 * 模板不经 modifier，因此没有这些值；一切进入场景/存档的 form 都经过
 * {@code FormArchitect.create()}，必然带有）。调用方须对 null 走默认外观分支。</p>
 */
public class BBSPPFormValues
{
    public static final String UV_TRANSFORM = "bbspp_uv_transform";
    public static final String UV_ROTATION = "bbspp_uv_rotation";
    public static final String UV_SCALE = "bbspp_uv_scale";
    public static final String TEXTURE_TINT = "texture_tint";
    public static final String TEXTURE_WHITEN = "texture_whiten";
    public static final String MOLANG_SHARED = "molangShared";
    public static final String COLOR = "color";
    public static final String ACTIONS_OVERLAY = "actions_overlay";

    private BBSPPFormValues()
    {}

    /** ModelForm / ExtrudedForm 的 U/V 偏移与缩放（U 偏移、V 偏移、U 缩放、V 缩放）。 */
    public static ValueVector4f getUvTransform(Form form)
    {
        return form == null ? null : get(form, UV_TRANSFORM, ValueVector4f.class);
    }

    /** ExtrudedForm 的表面 UV 旋转角度。 */
    public static ValueFloat getUvRotation(Form form)
    {
        return form == null ? null : get(form, UV_ROTATION, ValueFloat.class);
    }

    /** BillboardForm 的 U/V 缩放（两个缩放分量共用一条向量轨道）。 */
    public static ValueVector4f getUvScale(Form form)
    {
        return form == null ? null : get(form, UV_SCALE, ValueVector4f.class);
    }

    /** 纹理调色（透明白为中性，alpha 为效果强度）。FluidForm 自带同名值，同样适用。 */
    public static ValueColor getTextureTint(Form form)
    {
        return form == null ? null : get(form, TEXTURE_TINT, ValueColor.class);
    }

    /** 纹理白化强度（0~1）。 */
    public static ValueFloat getTextureWhiten(Form form)
    {
        return form == null ? null : get(form, TEXTURE_WHITEN, ValueFloat.class);
    }

    /** TrailForm / VanillaParticleForm 补充的颜色属性。 */
    public static ValueColor getColor(Form form)
    {
        return form == null ? null : get(form, COLOR, ValueColor.class);
    }

    /** ModelForm 的"variable 动作模型互通"开关；null 时按隔离（false）处理。 */
    public static ValueBoolean getMolangShared(Form form)
    {
        return form == null ? null : get(form, MOLANG_SHARED, ValueBoolean.class);
    }

    /** ModelForm 的主动作覆盖层（snow_actions 时间线）；null 表示未启用。 */
    public static ValueActionsConfig getActionsOverlay(Form form)
    {
        return form == null ? null : get(form, ACTIONS_OVERLAY, ValueActionsConfig.class);
    }

    /**
     * ModelForm 的并行动作覆盖层集合（{@code actions_overlay0..N}，
     * 数量与 BBS 录制的 pose/transform overlays 之和一致）。
     */
    public static List<ValueActionsConfig> getAdditionalActionsOverlays(Form form)
    {
        List<ValueActionsConfig> result = new ArrayList<>();

        if (form != null)
        {
            for (BaseValue value : form.getAll())
            {
                if (value instanceof ValueActionsConfig
                    && value.getId().startsWith(ACTIONS_OVERLAY)
                    && !value.getId().equals(ACTIONS_OVERLAY))
                {
                    result.add((ValueActionsConfig) value);
                }
            }
        }

        return result;
    }

    private static <T extends BaseValue> T get(Form form, String id, Class<T> type)
    {
        BaseValue value = form.get(id);

        return type.isInstance(value) ? type.cast(value) : null;
    }
}
