#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

/* BBS++ 纹理分级参数（std140，与 GradePass 写入的 64 字节一致）：
 *   Tint       rgb = 目标色相，a = 调色强度（0 = 不调色）
 *   Options    x = 白化强度，y = 光效开关，z = 光效相位，w = 光效亮度
 *   GlintColor rgb = 光效颜色，a = 光效强度
 *   Extra      保留 */
layout(std140) uniform GradeParams
{
    vec4 Tint;
    vec4 Options;
    vec4 GlintColor;
    vec4 Extra;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

/* 与 1.20.x 的 model_texture_grade.fsh 同一套 HSL 实现：
 * 只替换色相与饱和度、保留原图明度，所以「调色」不会把皮肤压成一片死色。 */
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

    if (Tint.a > 0.0001)
    {
        vec3 sourceHsl = rgb2hsl(clamp(color.rgb, 0.0, 1.0));
        vec3 tintHsl = rgb2hsl(clamp(Tint.rgb, 0.0, 1.0));
        vec3 recolored = hsl2rgb(vec3(tintHsl.x, tintHsl.y, sourceHsl.z));

        color.rgb = mix(color.rgb, recolored, clamp(Tint.a, 0.0, 1.0));
    }

    color *= vertexColor * ColorModulator;

    if (Options.x > 0.0001)
    {
        color.rgb = mix(color.rgb, vec3(1.0), clamp(Options.x, 0.0, 1.0));
    }

    /* overlay：逐骨骼调色板（swatch 里 alpha = 1 - 强度，中性 texel 是白色不透明）。
     * 与上游 model.fsh 同一条式子，所以逐骨骼 tint 的行为不变。 */
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    color *= lightMapColor;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
