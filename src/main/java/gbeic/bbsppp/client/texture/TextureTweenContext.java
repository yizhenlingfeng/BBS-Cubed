package gbeic.bbsppp.client.texture;

import mchorse.bbs_mod.forms.forms.ModelForm;

/**
 * 模型原生纹理轨道的补间上下文桥梁。
 *
 * <p>BBS 的关键帧段只持有关键帧与进度，不保留所属形态。属性应用阶段则同时拥有轨道和
 * {@link ModelForm}，因此这里用线程局部上下文把模型形态安全地传递给补间生成过程，
 * 为后续读取模型顶点、骨骼和 UV 数据提供稳定入口。</p>
 */
public class TextureTweenContext
{
    private static final ThreadLocal<ModelForm> CURRENT_FORM = new ThreadLocal<>();

    public static void begin(ModelForm form)
    {
        CURRENT_FORM.set(form);
    }

    public static ModelForm getForm()
    {
        return CURRENT_FORM.get();
    }

    public static void end()
    {
        CURRENT_FORM.remove();
    }
}
