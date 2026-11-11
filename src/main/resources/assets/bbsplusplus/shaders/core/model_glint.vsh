#version 330

/*
 * BBS++ 附魔光效顶点着色器，1.21.11 自定义 RenderPipeline 版。
 *
 * 顶点格式仍是 POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL，但光效这条 pass 只用到 Position/Color/UV0：
 * 片元着色器只采 Sampler0，所以这里不再声明并采样 Sampler1/2 —— 声明了却不参与最终程序，
 * 原版会在管线创建时打「shader program does not use sampler Sampler1」的 WARN。
 */

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    texCoord0 = UV0;
}
