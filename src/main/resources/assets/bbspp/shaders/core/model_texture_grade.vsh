#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat3 NormalMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform float GlintPhase;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec2 glintCoord;

/* 附魔光效 UV：放大 8 倍 + 绕原点旋转 10° + 沿对角平移。
 * 平移量恒为整数格（GlintPhase 在 [0,1) 内 → 0..4 整格），
 * 因为光效纹理在 S/T 上都以 GL_REPEAT 采样，整数格位移在视觉上等于没有位移，
 * 所以相位回绕的那一帧完全无缝 —— 这正是"无限循环"的来源，不需要任何循环逻辑。 */
vec2 glint_uv(vec2 uv, float phase)
{
    const float ANGLE = 0.17453293;   /* 10° */
    const float SCALE = 8.0;
    const float SHIFT = 4.0;          /* 整数格，保证无缝 */

    float c = cos(ANGLE);
    float s = sin(ANGLE);
    vec2 p = uv * SCALE;

    p = vec2(p.x * c - p.y * s, p.x * s + p.y * c);

    return p + vec2(phase * SHIFT, phase * SHIFT);
}

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);

    vec3 fixNormal = normalize(NormalMat * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
    glintCoord = glint_uv(UV0, GlintPhase);
}
