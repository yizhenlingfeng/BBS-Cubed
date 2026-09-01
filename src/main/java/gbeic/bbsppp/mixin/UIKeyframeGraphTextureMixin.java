package gbeic.bbsppp.mixin;

import gbeic.bbsppp.client.texture.TextureTweenManager;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 为模型纹理轨道的首个关键帧补齐当前实际显示的贴图。
 *
 * <p>模型未设置纹理覆盖时，{@link ModelForm#texture} 为 {@code null}，但渲染器会继续显示模型资源自带的
 * 默认贴图。原版创建首个关键帧只复制属性值，因此会留下一个空贴图关键帧。本类只在空轨道首次插入且
 * 待插入值为空时解析渲染器使用的真实贴图，后续关键帧仍完全沿用原版的邻近关键帧继承行为。</p>
 */
@Mixin(value = IUIKeyframeGraph.class, remap = false)
public interface UIKeyframeGraphTextureMixin
{
    /**
     * 注入目标：{@link IUIKeyframeGraph#addKeyframe(UIKeyframeSheet, float, Object)} 的待插入值。
     * 注入原因：模型默认贴图不保存在 {@code ModelForm.texture} 中，首个纹理关键帧会因此得到空值。
     * 修改行为：仅为空纹理轨道的第一个关键帧填入当前实际贴图，不干预已有轨道和显式传入的贴图。
     */
    @ModifyVariable(
        method = "addKeyframe(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeSheet;FLjava/lang/Object;)Lmchorse/bbs_mod/utils/keyframes/Keyframe;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Object bbsppp$seedFirstModelTexture(Object value, UIKeyframeSheet sheet, float tick)
    {
        if (value != null || sheet == null || !sheet.channel.isEmpty() || !bbsppp$isTextureChannel(sheet.channel.getId()))
        {
            return value;
        }

        Form form = sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);

        if (!(form instanceof ModelForm modelForm))
        {
            return value;
        }

        Link texture = modelForm.texture.getOriginalValue();

        if (texture == null || TextureTweenManager.isRuntimeLink(texture))
        {
            ModelInstance instance = ModelFormRenderer.getModel(modelForm);

            texture = instance == null ? null : instance.getTexture();
        }

        return texture == null || TextureTweenManager.isRuntimeLink(texture) ? value : texture;
    }

    private static boolean bbsppp$isTextureChannel(String id)
    {
        return "texture".equals(id) || id.endsWith("/texture");
    }
}
