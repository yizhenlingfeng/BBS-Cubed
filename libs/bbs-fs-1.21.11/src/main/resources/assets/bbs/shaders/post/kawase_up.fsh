#version 330

// Dual Kawase upsample (Bjorge, ARM 2015): four taps a whole source texel out along the axes (weight 1)
// and four diagonal taps half a texel out (weight 2), written into a target twice the size.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    float Offset;
};

in vec2 texCoord;


out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec2 h = oneTexel * 0.5 * Offset;

    vec3 sum = texture(InSampler, texCoord + vec2(-h.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(0.0, -h.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(0.0, h.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(-h.x, -h.y)).rgb * 2.0;

    fragColor = vec4(sum / 12.0, 1.0);
}
