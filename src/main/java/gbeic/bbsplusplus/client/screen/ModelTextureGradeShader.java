package gbeic.bbsplusplus.client.screen;

import gbeic.bbsplusplus.BBSFSloveCML;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

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
    private static int debugCounter;

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

        if (debugCounter < 5)
        {
            debugCounter++;
            BBSFSloveCML.LOGGER.info("[FSloveCML-DBG] select: fallback=" + (fallback == null ? "null" : fallback.getClass().getSimpleName())
                + " format=" + (fallback == null || fallback.getFormat() == null ? "null" : fallback.getFormat().getVertexSizeByte() + "B/" + fallback.getFormat().getElements().size())
                + " found=" + (current != null)
                + " tint=" + (tint == null ? "null" : tint.r + "," + tint.g + "," + tint.b + "," + tint.a)
                + " whiten=" + whiten);
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

        if (debugCounter < 5)
        {
            BBSFSloveCML.LOGGER.info("[FSloveCML-DBG] apply: program=" + program.getClass().getSimpleName()
                + " tintUniform=" + (tintUniform != null)
                + " whitenUniform=" + (whitenUniform != null)
                + " tint=" + tint.r + "," + tint.g + "," + tint.b + "," + tint.a
                + " whiten=" + whiten);
        }

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

    public static void applyGroup(ShaderProgram program, Color tint, float whiten)
    {
        Color appliedTint = tint.a > 0.0001F ? tint : BASE_TINT;
        float appliedWhiten = whiten > 0.0001F ? whiten : baseWhiten;

        if (appliedTint.a > 0.0001F || appliedWhiten > 0.0001F)
        {
            apply(program, appliedTint, appliedWhiten);
        }
        else
        {
            apply(program, BASE_TINT, 0F);
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
