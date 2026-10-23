package gbeic.bbsplusplus.client.renderer;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Vector4f;

/**
 * 保存当前模型渲染期间的 UV 变换上下文。
 * <p>
 * 模型渲染会在 CPU 顶点路径、VAO 路径和 BOBJ 路径之间切换。这个运行时对象用
 * {@link ThreadLocal} 把当前 {@link ModelForm} 的 UV 参数传递给具体渲染器或 shader，
 * 避免把 BBS++ 的字段硬塞进原版渲染类的公开 API。
 * </p>
 */
public class ModelUVTransformRuntime
{
    private static final ThreadLocal<Transform> CURRENT = new ThreadLocal<>();
    private static final Transform DEFAULT = new Transform(0F, 0F, 1F, 1F, 1F, 1F, false);

    public static void push(ModelForm form, ModelInstance model)
    {
        CURRENT.set(from(form, model));
    }

    public static void pop()
    {
        CURRENT.remove();
    }

    public static Transform current()
    {
        Transform transform = CURRENT.get();

        return transform == null ? DEFAULT : transform;
    }

    public static boolean hasActive(ModelForm form)
    {
        return from(form, null).active();
    }

    public static void applyToShader(ShaderProgram shader)
    {
        applyToShader(shader, current());
    }

    public static void clearShader(ShaderProgram shader)
    {
        applyToShader(shader, DEFAULT);
    }

    private static void applyToShader(ShaderProgram shader, Transform transform)
    {
        /* 1.21.11 的 GlUniform 变成了接口、没有任何 setter，uniform 值统一由 RenderPass 在
         * 提交时写入，无法再像 1.20.x 那样直接在 shader 上改自定义 UVTransform。
         * 保留方法签名让 Mixin 在两侧走同一套注入点；1.21.11 上退化为 no-op
         * （仅影响“通过 shader 做的 UV 变换”，CPU 顶点路径不受影响）。 */
    }

    private static Transform from(ModelForm form, ModelInstance model)
    {
        mchorse.bbs_mod.settings.values.misc.ValueVector4f uv = gbeic.bbsplusplus.api.BBSPPFormValues.getUvTransform(form);

        if (uv == null)
        {
            return DEFAULT;
        }

        Vector4f value = uv.get();
        float offsetU = value.x;
        float offsetV = value.y;
        float scaleU = value.z;
        float scaleV = value.w;
        boolean active = Math.abs(offsetU) > 1.0e-6F
            || Math.abs(offsetV) > 1.0e-6F
            || Math.abs(scaleU - 1F) > 1.0e-6F
            || Math.abs(scaleV - 1F) > 1.0e-6F;

        if (!active)
        {
            return DEFAULT;
        }

        float textureWidth = 1F;
        float textureHeight = 1F;
        IModel rawModel = model == null ? null : model.model;

        if (rawModel instanceof Model cubicModel)
        {
            textureWidth = Math.max(1, cubicModel.textureWidth);
            textureHeight = Math.max(1, cubicModel.textureHeight);
        }

        return new Transform(offsetU, offsetV, scaleU, scaleV, textureWidth, textureHeight, true);
    }

    public record Transform(float offsetU, float offsetV, float scaleU, float scaleV, float textureWidth, float textureHeight, boolean active)
    {
        public float normalizedOffsetU()
        {
            return this.offsetU / this.textureWidth;
        }

        public float normalizedOffsetV()
        {
            return this.offsetV / this.textureHeight;
        }
    }
}
