package gbeic.bbsplusplus.client.glint;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.api.ModelVAOGlintAccess;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Map;

/**
 * Iris 光影下的附魔光效第二遍绘制。
 *
 * <p>自建核心 shader 不在 Iris 的 program 管线内，因此改为用原版 glint 程序
 * （{@code GameRenderer.getRenderTypeGlintProgram()}）重画同一组 VAO。Iris 会把该程序
 * 映射到光影包的 {@code gbuffers_armor_glint}，缺失时回退 {@code gbuffers_textured}。</p>
 *
 * <p>几何使用 {@code ModelVAO.vao2}：原版 glint 的顶点格式是 {@code POSITION_TEX}，
 * attribute 0=Position、1=UV0；BBS 主 VAO 在 Iris 下是扩展格式，UV0 位于 attribute 2。</p>
 *
 * <p>状态与原版 {@code GLINT_TRANSPARENCY} 一致：RGB 用 {@code SRC_COLOR/ONE} 加色，
 * 深度 {@code EQUAL}、不写深度、关闭剔除；绘制完毕后恢复调用方状态。</p>
 */
public final class GlintRenderer
{
    private static final Identifier GLINT_TEXTURE = new Identifier("textures/misc/enchanted_glint_item.png");
    private static final Color DEFAULT_COLOR = new Color(1F, 1F, 1F, 1F);
    private static final float GLINT_SCALE = 8.0F;
    private static final float GLINT_ANGLE = 0.17453292F;

    private GlintRenderer()
    {}

    /**
     * 是否应该为这个组追加光效。只处理「光影真的接管了本次绘制」的情况；
     * 无光影时仍由现有的 {@code model_texture_grade} 单 pass 负责。
     */
    public static boolean shouldRender(ShaderProgram baseProgram, ModelGroup group)
    {
        if (group == null || baseProgram == null)
        {
            return false;
        }

        if (!BBSRendering.isIrisWorldShadersEnabled() || BBSRendering.isIrisShadowPass())
        {
            return false;
        }

        if (IrisUtils.isRenderingOffscreen())
        {
            return false;
        }

        if (baseProgram == BBSShaders.getPickerModelsProgram())
        {
            return false;
        }

        return hasGlint(group);
    }

    public static void render(ModelInstance model, ModelGroup group, MatrixStack stack)
    {
        if (model == null || group == null || stack == null || !hasGlint(group) || !group.isVisible())
        {
            return;
        }

        Map<ModelGroup, Map<String, ModelVAO>> allVaos = model.getVaos();
        Map<String, ModelVAO> groupVaos = allVaos != null ? allVaos.get(group) : null;

        if (groupVaos == null || groupVaos.isEmpty())
        {
            return;
        }

        ShaderProgram shader = GameRenderer.getRenderTypeGlintProgram();
        AbstractTexture texture = resolveGlintTexture();

        if (shader == null || texture == null)
        {
            return;
        }

        int previousVao = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousTexture = RenderSystem.getShaderTexture(0);
        Matrix4f previousTextureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        try
        {
            float speed = speedOf(group);
            float brightness = brightnessOf(group);

            RenderSystem.setShaderTexture(0, texture.getGlId());
            RenderSystem.setTextureMatrix(createGlintMatrix(speed));

            ModelVAORenderer.setupUniforms(shader, ModelVAORenderer.captureModelView(stack), stack.peek().getNormalMatrix());
            applyColor(shader, colorOf(group), brightness);

            shader.bind();

            try
            {
                /* Iris 的 ExtendedShader.apply() 会用全局 model-view 覆盖 NormalMat；
                 * 这里在 bind 之后补回该骨骼自己的法线矩阵，避免旋转骨骼上的光照错位。 */
                applyNormal(shader, stack.peek().getNormalMatrix());

                /* bind() 会应用 shader JSON 里的 blend 状态，所以加色混合必须在 bind 之后设置。 */
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SrcFactor.SRC_COLOR,
                    GlStateManager.DstFactor.ONE,
                    GlStateManager.SrcFactor.ZERO,
                    GlStateManager.DstFactor.ONE
                );
                RenderSystem.depthMask(false);
                RenderSystem.depthFunc(GL11.GL_EQUAL);
                RenderSystem.disableCull();

                for (ModelVAO vao : groupVaos.values())
                {
                    if (vao instanceof ModelVAOGlintAccess access)
                    {
                        access.bbspp_cml$drawGlintVao();
                    }
                }
            }
            finally
            {
                shader.unbind();
            }
        }
        finally
        {
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.setTextureMatrix(previousTextureMatrix);
            RenderSystem.depthFunc(previousDepthFunc);
            RenderSystem.depthMask(previousDepthMask);

            if (previousCull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            RenderSystem.defaultBlendFunc();

            if (previousBlend)
            {
                RenderSystem.enableBlend();
            }
            else
            {
                RenderSystem.disableBlend();
            }

            GL30.glBindVertexArray(previousVao);
        }
    }

    private static boolean hasGlint(ModelGroup group)
    {
        if (((GroupGlintHolder) group).bbspp_cml$getGlint())
        {
            return true;
        }

        return group.current instanceof GlintHolder glint && glint.bbspp_cml$getGlint();
    }

    private static Color colorOf(ModelGroup group)
    {
        GroupGlintHolder holder = (GroupGlintHolder) group;
        Color color = holder.bbspp_cml$getGlint() ? holder.bbspp_cml$getGlintColor() : null;

        if (color == null && group.current instanceof GlintHolder glint)
        {
            color = glint.bbspp_cml$getGlintColor();
        }

        return color != null ? color : DEFAULT_COLOR;
    }

    private static GlintSettingsHolder settingsOf(ModelGroup group)
    {
        if (group == null)
        {
            return null;
        }

        /* 组上的值是 applyPose 传播过来的渲染期快照；没有传播时（例如只开了
         * Transform 上的开关）回退到 Transform，保证和 colorOf() 的取值顺序一致。 */
        if (!((GroupGlintHolder) group).bbspp_cml$getGlint()
            && group.current instanceof GlintSettingsHolder current)
        {
            return current;
        }

        return group instanceof GlintSettingsHolder holder ? holder : null;
    }

    private static float speedOf(ModelGroup group)
    {
        GlintSettingsHolder holder = settingsOf(group);

        if (holder == null)
        {
            return GlintSettingsHolder.DEFAULT_SPEED;
        }

        return Math.max(0F, Math.min(GlintSettingsHolder.MAX_SPEED, holder.bbspp_cml$getGlintSpeed()));
    }

    private static float brightnessOf(ModelGroup group)
    {
        GlintSettingsHolder holder = settingsOf(group);

        if (holder == null)
        {
            return GlintSettingsHolder.DEFAULT_BRIGHTNESS;
        }

        return Math.max(0F, Math.min(GlintSettingsHolder.MAX_BRIGHTNESS, holder.bbspp_cml$getGlintBrightness()));
    }

    private static void applyNormal(ShaderProgram shader, org.joml.Matrix3f normalMatrix)
    {
        GlUniform normalUniform = shader.getUniform("NormalMat");

        if (normalUniform != null)
        {
            normalUniform.set(normalMatrix);
            normalUniform.upload();
        }
    }

    private static void applyColor(ShaderProgram shader, Color color, float brightness)
    {
        float r = clamp(color.r);
        float g = clamp(color.g);
        float b = clamp(color.b);
        float a = clamp(color.a);
        float scale = Math.max(0F, brightness);

        /* glint 的混合是 SRC_COLOR/ONE：源色会被平方。用 sqrt 抵消 RGB 的平方，
         * 这样 ColorModulator 里的颜色在最终画面上大致等于 UI 里选的颜色；
         * 亮度乘在平方前，等价于把光效图层的自发光强度按 brightness 放大。 */
        GlUniform colorUniform = shader.getUniform("ColorModulator");

        if (colorUniform != null)
        {
            colorUniform.set(sqrt(r * scale), sqrt(g * scale), sqrt(b * scale), 1F);
        }

        GlUniform alphaUniform = shader.getUniform("GlintAlpha");

        if (alphaUniform != null)
        {
            alphaUniform.set(a);
        }
    }

    private static Matrix4f createGlintMatrix(float speed)
    {
        /* 与 model_texture_grade.vsh 的 glint_uv 完全同构：
         * uv*8 → 旋转 10° → 两轴各平移 phase*4（整数格），保证两条路径流速一致。 */
        float phase = GlintTiming.phase(speed);

        return new Matrix4f()
            .translation(phase * GlintTiming.SHIFT, phase * GlintTiming.SHIFT, 0.0F)
            .rotateZ(GLINT_ANGLE)
            .scale(GLINT_SCALE);
    }

    private static AbstractTexture resolveGlintTexture()
    {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(GLINT_TEXTURE);

        if (texture == null)
        {
            return null;
        }

        /* 纹理还没真正加载完时 glId 是 -1；bind 一次让它有机会分配，再判断。 */
        texture.bindTexture();

        if (texture.getGlId() == -1)
        {
            return null;
        }

        /* 光效 UV 会大幅超出 [0,1]：线性过滤 + REPEAT 让超出部分平铺回贴图内。 */
        texture.setFilter(true, false);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        return texture;
    }

    private static float clamp(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }

    private static float sqrt(float value)
    {
        return (float) Math.sqrt(value);
    }
}
