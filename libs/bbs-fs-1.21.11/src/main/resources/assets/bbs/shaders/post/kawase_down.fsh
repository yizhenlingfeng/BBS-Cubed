#version 330

// Dual Kawase downsample (Bjorge, ARM 2015): the centre plus four diagonal bilinear taps half a
// source texel out, written into a target half the size. Offset spreads the taps for in-between strengths.

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

    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord - h).rgb;
    sum += texture(InSampler, texCoord + h).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb;

    fragColor = vec4(sum / 8.0, 1.0);
}
