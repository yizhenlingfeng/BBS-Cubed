package gbeic.bbsplusplus.client.screen;

import gbeic.bbsplusplus.BBSFSloveCML;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Model shader variant used only while one of the texture-grade properties is active.
 * The shader keeps FS's regular vertex format and lighting path, adding two fragment
 * uniforms for hue-preserving tinting and white mixing.
 */
public final class ModelTextureGradeShader
{
    private static final Color BASE_TINT = new Color(1F, 1F, 1F, 0F);
    /* 使用 HashMap 而非 IdentityHashMap：Iris 光影下 GameRenderer 的实体 shader 可能携带
     * 内容相同但实例不同的 VertexFormat，按引用匹配会导致找不到纹理调色变体。 */
    private static final Map<VertexFormat, ShaderProgram> SHADERS = new HashMap<>();
    private static final Variant[] VARIANTS = {
        new Variant("model_texture_grade", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL),
        new Variant("block_texture_grade", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL),
        new Variant("unshaded_texture_grade", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR),
        new Variant("text_texture_grade", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT),
        new Variant("trail_texture_grade", VertexFormats.POSITION_TEXTURE)
    };
    private static boolean attempted;
    private static ResourceManager attemptedManager;
    private static float baseWhiten;

    /*
     * 注意：本类位于<b>每帧、每组模型</b>都会走到的渲染热路径上（select 每次切换 shader、
     * apply 每次绘制 group）。这里曾有一对 "[FSloveCML-DBG]" 调试日志，因为只判断了
     * debugCounter < 5 却漏了自增，导致 apply() 每帧都往日志写一行 —— 实测一轮游戏
     * 就把 latest.log 撑到 1.7 GB（其中 99.8% 是这一行），并因同步写盘造成持续卡顿。
     * 该日志已删除；今后不要在此类热路径上增加任何未做严格限流的日志。
     */

    /* ---------- 附魔光效 ----------
     *
     * TODO(未完成 / WIP)：Iris 光影兼容尚未完成。
     * 当前实现为自建核心 shader（model_texture_grade）单 pass 加色，无 Iris 时正常；
     * Iris 世界渲染时自建 program 不在 Iris 管线内，光效不显示。
     * 计划方向：Iris 下改用原版 glint RenderType 材质路径（同组 VAO 二次绘制），
     * 详见计划文档。在完成前，README 已知限制保留此说明。 */
    /**
     * 原版物品附魔光效纹理。1.20.1 / 1.20.4 的真实路径是 {@code enchanted_glint_item.png}
     * （从 ItemRenderer.ITEM_ENCHANTMENT_GLINT 的字节码确认，勿用旧名 enchanted_item_glint.png）。
     */
    private static final Identifier GLINT_TEXTURE = new Identifier("textures/misc/enchanted_glint_item.png");

    private static final String GLINT_SAMPLER = "GlintSampler";
    private static final String GLINT_STRENGTH = "GlintStrength";
    private static final String GLINT_PHASE = "GlintPhase";
    private static final String GLINT_COLOR = "GlintColor";

    /** 开启时的光效强度。第一版不做 UI 参数，改这里即可整体调亮/调暗。 */
    private static final float GLINT_ON = 1.0F;

    /** 光效默认颜色（白色 = 原版观感）。 */
    private static final Color DEFAULT_GLINT_COLOR = new Color(1F, 1F, 1F, 1F);

    /** 光效扫过一轮的周期（毫秒）。改这里即可调快/调慢。 */
    private static final long GLINT_PERIOD_MS = 3000L;

    /** 光效纹理是否已成功注册到 shader 上。 */
    private static boolean glintAvailable;

    /**
     * 当前模型是否支持逐组写 uniform（VAO 渲染）。
     * CPU 路径（动态 Geo / 非 VAO）所有骨骼的顶点进同一个缓冲区、一次绘制，
     * 逐组写入毫无意义 —— 最后一次写入生效，会把 select() 里设的"整模型 fallback"覆盖掉。
     * 此时必须跳过逐组写入，让 fallback 的 1 保持住。
     */
    private static boolean glintPerGroup;

    private ModelTextureGradeShader()
    {}

    public static synchronized void setup()
    {
        attempted = true;

        for (ShaderProgram shader : SHADERS.values())
        {
            shader.close();
        }
        SHADERS.clear();
        glintAvailable = false;

        ResourceManager manager = MinecraftClient.getInstance().getResourceManager();

        attemptedManager = manager;

        if (manager == null)
        {
            return;
        }

        AddonResourceFactory factory = new AddonResourceFactory(manager);

        for (Variant variant : VARIANTS)
        {
            try
            {
                ShaderProgram loaded = new ShaderProgram(factory, variant.name, variant.format);

                if (loaded.getUniform("TextureTint") == null || loaded.getUniform("TextureWhiten") == null)
                {
                    loaded.close();
                    throw new IOException("texture-grade shader is missing required uniforms");
                }

                registerGlintSampler(loaded);
                SHADERS.put(variant.format, loaded);
            }
            catch (IOException | RuntimeException e)
            {
                BBSFSloveCML.LOGGER.error("[FSloveCML] 无法创建纹理调色 shader: " + variant.name, e);
            }
        }

        BBSFSloveCML.LOGGER.info("[FSloveCML] 纹理材质 shader 变体加载完成: " + SHADERS.size() + "/" + VARIANTS.length);
    }

    public static ShaderProgram select(ShaderProgram fallback, Color tint, float whiten)
    {
        ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
        ShaderProgram current = fallback == null ? null : findByFormat(fallback.getFormat());

        if (current == null && (!attempted || manager != attemptedManager))
        {
            setup();
            current = fallback == null ? null : findByFormat(fallback.getFormat());
        }

        if (current == null)
        {
            return fallback;
        }

        BASE_TINT.copy(tint);
        baseWhiten = Math.max(0F, Math.min(1F, whiten));
        apply(current, BASE_TINT, baseWhiten);

        return current;
    }

    /**
     * 按 VertexFormat 查找已加载的纹理调色 shader。
     * 先尝试 Map 直接查找；若未命中（Iris 下 format 实例可能不同），
     * 回退到按顶点步长与元素列表逐一遍历比较。
     */
    private static ShaderProgram findByFormat(VertexFormat format)
    {
        if (format == null)
        {
            return null;
        }

        ShaderProgram direct = SHADERS.get(format);
        if (direct != null)
        {
            return direct;
        }

        for (Map.Entry<VertexFormat, ShaderProgram> entry : SHADERS.entrySet())
        {
            VertexFormat key = entry.getKey();
            if (key.getVertexSizeByte() == format.getVertexSizeByte()
                && key.getElements().equals(format.getElements()))
            {
                return entry.getValue();
            }
        }

        return null;
    }

    /** Updates one model-group draw; no-op when the program does not expose grade uniforms. */
    public static void apply(ShaderProgram program, Color tint, float whiten)
    {
        if (program == null)
        {
            return;
        }

        GlUniform tintUniform = program.getUniform("TextureTint");
        GlUniform whitenUniform = program.getUniform("TextureWhiten");

        /* 只有声明了纹理调色 uniform 的 shader 才写入，避免对原版/Iris shader 误操作。 */
        if (tintUniform == null && whitenUniform == null)
        {
            return;
        }

        if (tintUniform != null)
        {
            tintUniform.set(tint.r, tint.g, tint.b, tint.a);
        }

        if (whitenUniform != null)
        {
            whitenUniform.set(Math.max(0F, Math.min(1F, whiten)));
        }
    }

    /**
     * 写入一个 model group 的附魔光效开关。
     *
     * <p>这是<b>逐组</b>调用，所以每次都必须写（包括关闭时写 0）—— 否则同一个模型里
     * 前一个开了光效的骨骼会把状态泄漏给后一个没开的骨骼。</p>
     */
    public static void applyGlint(ShaderProgram program, boolean enabled, Color color)
    {
        if (program == null)
        {
            return;
        }

        GlUniform strength = program.getUniform(GLINT_STRENGTH);

        if (strength == null)
        {
            return;
        }

        strength.set(glintAvailable && enabled ? GLINT_ON : 0F);

        GlUniform phase = program.getUniform(GLINT_PHASE);

        if (phase != null)
        {
            phase.set(glintPhase());
        }

        GlUniform tint = program.getUniform(GLINT_COLOR);

        if (tint != null)
        {
            /* null / 未就绪时回退到白色，保证默认观感与原版一致。 */
            Color use = color == null ? DEFAULT_GLINT_COLOR : color;

            tint.set(use.r, use.g, use.b, use.a);
        }
    }

    /** 光效滚动相位，取值 [0,1)。整数格位移 + sin/cos 连续滚动 → 无限循环无接缝。 */
    public static float glintPhase()
    {
        return (float) (System.currentTimeMillis() % GLINT_PERIOD_MS) / (float) GLINT_PERIOD_MS;
    }

    public static boolean isGlintPerGroup()
    {
        return glintPerGroup;
    }

    public static void setGlintPerGroup(boolean value)
    {
        glintPerGroup = value;
    }

    /**
     * 把原版附魔光效纹理注册到 shader 的第 4 个 sampler 上。
     *
     * <p>{@code ShaderProgram.bind()} 会遍历 JSON 里声明的 sampler 名：若该名字在
     * samplers 映射里有<b>非 null</b> 值，就把纹理绑到 GL_TEXTURE0+i 并把 sampler uniform
     * 设为 i；为 null 则直接跳过。JSON 只提供名字（{@code readSampler} 会 put(name, null)），
     * 真实纹理必须由这里 {@code addSampler} 提供 —— 这也正是原版给自己核心 shader
     * 挂纹理的机制。所以额外 sampler 绝不能只改 JSON。</p>
     */
    private static void registerGlintSampler(ShaderProgram program)
    {
        if (program.getUniform(GLINT_STRENGTH) == null)
        {
            /* 该变体没有声明光效 uniform（非 model_texture_grade），无需挂载。 */
            return;
        }

        AbstractTexture texture = resolveGlintTexture();

        if (texture == null)
        {
            return;
        }

        program.addSampler(GLINT_SAMPLER, texture);
        glintAvailable = true;
    }

    private static AbstractTexture resolveGlintTexture()
    {
        try
        {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(GLINT_TEXTURE);

            /* 纹理还没真正加载完时 glId 是 -1：addSampler 也会挂上，但 bind() 会因
             * glId == -1 跳过绑定，等效于没挂 —— 所以这里视为"还没好"，下次重试。 */
            if (texture == null || texture.getGlId() == -1)
            {
                return null;
            }

            /* 光效 UV 会大幅超出 [0,1]：线性过滤避免硬像素块，
             * REPEAT 环绕让超出部分的采样平铺回贴图内。 */
            texture.setFilter(true, false);
            texture.bindTexture();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

            return texture;
        }
        catch (Throwable t)
        {
            BBSFSloveCML.LOGGER.error("[FSloveCML] 附魔光效纹理加载失败，光效将被禁用", t);

            return null;
        }
    }

    private static final class AddonResourceFactory implements ResourceFactory
    {
        private final ResourceManager manager;

        private AddonResourceFactory(ResourceManager manager)
        {
            this.manager = manager;
        }

        @Override
        public Optional<Resource> getResource(Identifier id)
        {
            if (id.getPath().contains("/core/"))
            {
                return this.manager.getResource(new Identifier(BBSFSloveCML.MOD_ID, id.getPath()));
            }

            return this.manager.getResource(id);
        }
    }

    private record Variant(String name, VertexFormat format)
    {}
}
