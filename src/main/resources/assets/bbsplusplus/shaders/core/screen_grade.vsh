#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;

/*
 * 全屏后处理用的顶点着色器。
 *
 * 与上游 bbs:core/selection 同构：ScreenQuadPass 传来的四个顶点本来就已经是
 * 「目标纹理像素」坐标，Projection 由 ScreenQuadPass 换成 (0..w, h..0) 的正交矩阵，
 * 因此这里的变换与普通 2D 绘制完全一致。
 */
void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
}
