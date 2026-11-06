#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

/* 与 model_grade.fsh 完全相同的 64 字节块（两条管线共用一个 UBO 布局）：
 *   Tint       rgb = 目标色相，a = 调色强度（本 pass 不用）
 *   Options    x = 白化强度（本 pass 不用），y = 光效开关，z = 光效相位，w = 光效亮度
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
in vec2 texCoord0;

out vec4 fragColor;

/* 光效 UV：放大 8 倍 + 绕原点旋转 10°，再沿对角平移。
 * 平移量恒为整数格（相位在 [0,1) → 0..4 整格），图案在 S/T 上重复采样，
 * 所以相位回绕那一帧完全无缝。常量与 1.20.x 的 model_texture_grade.vsh 一致。 */
vec2 glint_uv(vec2 uv, float phase)
{
    const float ANGLE = 0.17453293;   /* 10° */
    const float SCALE = 8.0;
    const float SHIFT = 4.0;

    float c = cos(ANGLE);
    float s = sin(ANGLE);
    vec2 p = uv * SCALE;

    p = vec2(p.x * c - p.y * s, p.x * s + p.y * c);

    return p + vec2(phase * SHIFT, phase * SHIFT);
}

void main()
{
    if (Options.y <= 0.001)
    {
        discard;
    }

    /* 只画模型本身不透明的像素：光效是一层叠加，不该跑到镂空里。 */
    vec4 base = texture(Sampler0, texCoord0);

    if (base.a < 0.1)
    {
        discard;
    }

    vec2 gc = glint_uv(texCoord0, Options.z);

    /* 程序化图案：两个方向的条纹相乘得到斜向菱形亮斑，平方让亮部更集中。
     * 不额外引贴图，让管线只依赖 Sampler0。 */
    float bands = sin(gc.x * 6.2831853) * sin(gc.y * 6.2831853);
    float intensity = bands * bands;

    if (intensity < 0.002)
    {
        discard;
    }

    /* 亮度倍率 0–4（GlintSettingsHolder.MAX_BRIGHTNESS），颜色 alpha 是 0–1 的颜色强度 —— 与
     * 1.20 的 `GlintColor.rgb * intensity * GlintColor.a * glint * GlintBrightness` 同量级。 */
    float strength = clamp(Options.w, 0.0, 4.0) * clamp(GlintColor.a, 0.0, 1.0);
    vec3 rgb = GlintColor.rgb * intensity * strength;

    /* 混合用 BlendFunction.ADDITIVE，而它是 (SRC_ALPHA, ONE)：颜色会被再乘一次 alpha。
     * 所以这里 alpha 固定写 1.0，把亮度全部放在 rgb 里 —— 否则实际结果是
     * rgb * intensity * strength，比预期暗一个数量级，调亮度滑块也会变成平方曲线。 */
    vec4 color = vec4(rgb, 1.0);

    /* 走 apply_fog 只是为了引用 Fog 块，否则 bindDefaultUniforms 的 "Fog" 没有落点。 */
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
