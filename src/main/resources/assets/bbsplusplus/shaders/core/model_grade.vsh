#version 330

/*
 * BBS++ 纹理分级（调色 / 白化）顶点着色器，1.21.11 自定义 RenderPipeline 版。
 *
 * 与上游 assets/bbs/shaders/core/model.vsh 完全同构：顶点格式
 * POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL 的 UV1 指向 overlay swatch（逐骨骼调色），
 * UV2 指向 lightmap，所以接管基础绘制之后光照与逐骨骼 tint 都还在。
 */

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
