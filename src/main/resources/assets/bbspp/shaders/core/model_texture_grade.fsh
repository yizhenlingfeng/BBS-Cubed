#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D GlintSampler;
uniform vec4 ColorModulator;
uniform vec4 TextureTint;
uniform float TextureWhiten;
uniform float GlintStrength;
uniform vec4 GlintColor;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec2 glintCoord;

out vec4 fragColor;

vec3 rgb2hsl(vec3 c)
{
    float maxC = max(c.r, max(c.g, c.b));
    float minC = min(c.r, min(c.g, c.b));
    float delta = maxC - minC;
    float lightness = (maxC + minC) * 0.5;
    float denominator = 1.0 - abs(2.0 * lightness - 1.0);
    float saturation = delta < 0.00001 || denominator < 0.00001 ? 0.0 : delta / denominator;
    float hue = 0.0;

    if (delta > 0.00001)
    {
        if (maxC == c.r)
        {
            hue = mod((c.g - c.b) / delta, 6.0) / 6.0;
        }
        else if (maxC == c.g)
        {
            hue = ((c.b - c.r) / delta + 2.0) / 6.0;
        }
        else
        {
            hue = ((c.r - c.g) / delta + 4.0) / 6.0;
        }
    }

    return vec3(fract(hue), saturation, lightness);
}

vec3 hsl2rgb(vec3 hsl)
{
    float h = fract(hsl.x);
    float s = clamp(hsl.y, 0.0, 1.0);
    float l = clamp(hsl.z, 0.0, 1.0);
    float chroma = (1.0 - abs(2.0 * l - 1.0)) * s;
    float x = chroma * (1.0 - abs(mod(h * 6.0, 2.0) - 1.0));
    float m = l - chroma * 0.5;
    vec3 rgb;

    if (h < 1.0 / 6.0) rgb = vec3(chroma, x, 0.0);
    else if (h < 2.0 / 6.0) rgb = vec3(x, chroma, 0.0);
    else if (h < 3.0 / 6.0) rgb = vec3(0.0, chroma, x);
    else if (h < 4.0 / 6.0) rgb = vec3(0.0, x, chroma);
    else if (h < 5.0 / 6.0) rgb = vec3(x, 0.0, chroma);
    else rgb = vec3(chroma, 0.0, x);

    return rgb + vec3(m);
}

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    if (color.a < 0.1)
    {
        discard;
    }

    /* Replace hue/saturation while preserving every source pixel's lightness. */
    vec3 sourceHsl = rgb2hsl(clamp(color.rgb, 0.0, 1.0));
    vec3 tintHsl = rgb2hsl(clamp(TextureTint.rgb, 0.0, 1.0));
    vec3 recolored = hsl2rgb(vec3(tintHsl.x, tintHsl.y, sourceHsl.z));
    color.rgb = mix(color.rgb, recolored, clamp(TextureTint.a, 0.0, 1.0));

    color *= vertexColor * ColorModulator;

    /* White is a material blend, so the original form color remains compatible. */
    color.rgb = mix(color.rgb, vec3(1.0), clamp(TextureWhiten, 0.0, 1.0));
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    /* 附魔光效：在基础色上叠加一层光效纹理。
     * 原型是原版的 rendertype_glint —— 本体画完后再叠一层近加色的光效。
     * 这里改在同一个 pass 里做加法，视觉等价且省掉一次 draw call。
     *
     * 必须严格对齐原版公式，否则会看不见：
     *   1) 原版混合是 SRC_COLOR（dst = src*src + dst），所以 rgb 要<b>自乘</b>；
     *   2) 原版片元只拿 alpha 做门槛（a < 0.1 丢弃），<b>不</b>拿 alpha 去乘 rgb
     *      —— 光效纹理的图案信息在 rgb 里，alpha 只是遮罩。
     *      误乘 alpha 会把光效压到几乎全黑，表现为"开了光效但什么都看不到"。
     * GlintColor 是乘法染色（rgb 定色、a 当强度微调），默认白色 = 原版观感。
     * 放在 fog 之前，使光效与原版一样随雾气衰减。 */
    float glint = clamp(GlintStrength, 0.0, 1.0);

    if (glint > 0.001)
    {
        vec4 glintSample = texture(GlintSampler, glintCoord);

        /* 兼容光效贴图的两种图案存法：
         *   1) 图案在 rgb（原版行为，alpha 是遮罩）
         *   2) 图案在 alpha（rgb 近零）
         * 取两者中较强的作为强度；颜色一律用用户设置的 GlintColor。 */
        float lum = max(glintSample.r, max(glintSample.g, glintSample.b));
        float intensity = lum > 0.01 ? lum * lum : glintSample.a * glintSample.a;

        color.rgb += GlintColor.rgb * intensity * GlintColor.a * glint;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
