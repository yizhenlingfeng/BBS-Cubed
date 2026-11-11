#version 330

/*
 * BBS++ 屏幕调色 / 电影效果的后处理片元着色器。
 *
 * 移植自 1.20.1 的 ColorGradeRenderer 内嵌 GLSL（逐字保留算法与顺序），只把三处
 * 立即模式时代的写法换成 1.21.11 的约定：
 *
 *   1. 顶点属性从 a_uv 换成 UV0（texCoord0），由 ScreenQuadPass 的
 *      POSITION_TEXTURE_COLOR 顶点格式提供；
 *   2. u_sampler 换成 Sampler0；
 *   3. 二十多个散装 uniform 收进一个 std140 块 ScreenGrade。
 *
 * std140 的对齐规则在这里被「全部用 vec4 打包」绕开了 —— 每个字段都落在
 * 16 字节边界上，Java 侧只要按同样的顺序 putVec4 就永远对得齐。
 */
layout(std140) uniform ScreenGrade {
    /* x = vignette 强度, y = vignette 平滑度 */
    vec4 Vig;
    /* rgb = vignette 颜色 */
    vec4 VigColor;
    /* x = brightness, y = contrast, z = saturation, w = hue */
    vec4 Grade1;
    vec4 Lift;
    vec4 Gamma;
    vec4 Gain;
    /* x = grain 强度, y = grain 尺寸, z = grain 种子 */
    vec4 Grain;
    /* xy = UV 位移 */
    vec4 Distort;
    /* x = aberration, y = vhs, z = lensDistortion, w = vintage */
    vec4 Cine1;
    /* x = radialBlur, y = rain, z = dust, w = lightLeak */
    vec4 Cine2;
    /* x = 时间（秒） */
    vec4 Clock;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

#define u_vigStr         Vig.x
#define u_vigSmooth      Vig.y
#define u_vigColor       VigColor.rgb
#define u_brightness     Grade1.x
#define u_contrast       Grade1.y
#define u_saturation     Grade1.z
#define u_hue            Grade1.w
#define u_lift           Lift.xyz
#define u_gamma          Gamma.xyz
#define u_gain           Gain.xyz
#define u_grainStr       Grain.x
#define u_grainSize      Grain.y
#define u_grainSeed      Grain.z
#define u_distort        Distort.xy
#define u_aberration     Cine1.x
#define u_vhs            Cine1.y
#define u_lensDistortion Cine1.z
#define u_vintage        Cine1.w
#define u_radialBlur     Cine2.x
#define u_rain           Cine2.y
#define u_dust           Cine2.z
#define u_lightLeak      Cine2.w
#define u_time           Clock.x

/* --- HSL helpers --- */

vec3 rgb2hsl(vec3 c)
{
    float maxC = max(c.r, max(c.g, c.b));
    float minC = min(c.r, min(c.g, c.b));
    float delta = maxC - minC;
    float l = (maxC + minC) * 0.5;
    float s = delta < 1e-5 ? 0.0 : delta / (1.0 - abs(2.0 * l - 1.0));
    float h = 0.0;

    if (delta > 1e-5)
    {
        if      (maxC == c.r) h = mod((c.g - c.b) / delta, 6.0) / 6.0;
        else if (maxC == c.g) h = ((c.b - c.r) / delta + 2.0) / 6.0;
        else                  h = ((c.r - c.g) / delta + 4.0) / 6.0;
    }

    return vec3(h, s, l);
}

vec3 hsl2rgb(vec3 c)
{
    float h = c.x, s = c.y, l = c.z;
    float C = (1.0 - abs(2.0 * l - 1.0)) * s;
    float X = C * (1.0 - abs(mod(h * 6.0, 2.0) - 1.0));
    float m = l - C * 0.5;
    vec3 rgb;

    if      (h < 1.0 / 6.0) rgb = vec3(C, X, 0.0);
    else if (h < 2.0 / 6.0) rgb = vec3(X, C, 0.0);
    else if (h < 3.0 / 6.0) rgb = vec3(0.0, C, X);
    else if (h < 4.0 / 6.0) rgb = vec3(0.0, X, C);
    else if (h < 5.0 / 6.0) rgb = vec3(X, 0.0, C);
    else                    rgb = vec3(C, 0.0, X);

    return rgb + m;
}

float hash(vec2 p)
{
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main()
{
    vec2 sampleUV = texCoord0 + u_distort;

    /* VHS Horizontal Glitch displacement before sampling */
    if (u_vhs > 0.001)
    {
        float glitchNoise = hash(vec2(floor(sampleUV.y * 80.0), floor(u_time * 12.0)));

        if (glitchNoise > 0.95 - (u_vhs * 0.05))
        {
            sampleUV.x += sin(sampleUV.y * 30.0 + u_time * 10.0) * 0.02 * u_vhs;
        }
    }

    /* Lens Distortion (Fisheye) warping */
    vec2 distortedUV = sampleUV;

    if (abs(u_lensDistortion) > 0.001)
    {
        vec2 uvOffset = sampleUV - vec2(0.5);
        float r2 = dot(uvOffset, uvOffset);

        distortedUV = uvOffset * (1.0 + u_lensDistortion * r2) + vec2(0.5);
        distortedUV = clamp(distortedUV, 0.0, 1.0);
    }

    /* Lens Dirt & Rain Overlay (procedural raindrops and static spots refraction) */
    if (u_rain > 0.001)
    {
        /* Falling rain droplets grid */
        vec2 rainUV = distortedUV * vec2(8.0, 4.5);

        rainUV.y += u_time * 1.2;

        vec2 cell = fract(rainUV) - vec2(0.5);
        vec2 id = floor(rainUV);
        float dropSeed = hash(id);

        if (dropSeed > 0.45)
        {
            float size = 0.22 * (0.4 + 0.6 * sin(u_time * 1.5 + dropSeed * 6.28));
            float d = length(cell);

            if (d < size)
            {
                vec2 refractOffset = cell * (size - d) * 1.5;

                distortedUV += refractOffset * u_rain;
            }
        }

        /* Static lens dirt / condensation drops */
        vec2 dirtUV = distortedUV * vec2(12.0, 9.0);
        vec2 dirtCell = fract(dirtUV) - vec2(0.5);
        vec2 dirtId = floor(dirtUV);
        float dirtSeed = hash(dirtId);

        if (dirtSeed > 0.72)
        {
            float d = length(dirtCell);
            float size = 0.12 * dirtSeed;

            if (d < size)
            {
                distortedUV += dirtCell * (size - d) * 0.4 * u_rain;
            }
        }
    }

    /* Chromatic Aberration splitting */
    vec2 uvRed = distortedUV;
    vec2 uvBlue = distortedUV;

    if (u_aberration > 0.001)
    {
        vec2 dir = distortedUV - vec2(0.5);
        float dist = length(dir);
        vec2 offset = dir * dist * u_aberration;

        uvRed += offset;
        uvBlue -= offset;
        uvRed = clamp(uvRed, 0.0, 1.0);
        uvBlue = clamp(uvBlue, 0.0, 1.0);
    }

    float r = texture(Sampler0, uvRed).r;
    float g = texture(Sampler0, distortedUV).g;
    float b = texture(Sampler0, uvBlue).b;
    vec3 rgb = vec3(r, g, b);

    /* Radial Action Blur */
    if (u_radialBlur > 0.001)
    {
        vec2 blurDir = (distortedUV - vec2(0.5)) * u_radialBlur * 0.12;
        vec3 blurRGB = vec3(0.0);

        blurRGB += texture(Sampler0, clamp(distortedUV - blurDir * 2.0, 0.0, 1.0)).rgb;
        blurRGB += texture(Sampler0, clamp(distortedUV - blurDir, 0.0, 1.0)).rgb;
        blurRGB += rgb;
        blurRGB += texture(Sampler0, clamp(distortedUV + blurDir, 0.0, 1.0)).rgb;
        blurRGB += texture(Sampler0, clamp(distortedUV + blurDir * 2.0, 0.0, 1.0)).rgb;
        rgb = blurRGB / 5.0;
    }

    /* 1 — Lift / Gamma / Gain */
    rgb = rgb * (vec3(1.0) + u_gain);
    rgb = sign(rgb) * pow(max(abs(rgb), vec3(1e-4)), max(vec3(1e-4), vec3(1.0) / (vec3(1.0) + u_gamma)));
    rgb = rgb + u_lift;

    /* 2 — Brightness and contrast */
    rgb = rgb + u_brightness;
    rgb = vec3(0.5) + (1.0 + u_contrast) * (rgb - vec3(0.5));

    /* 3 — Saturation */
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

    rgb = mix(vec3(luma), rgb, 1.0 + u_saturation);

    /* 4 — Hue rotation */
    if (abs(u_hue) > 0.01)
    {
        vec3 hsl = rgb2hsl(rgb);

        hsl.x = fract(hsl.x + u_hue / 360.0);
        rgb = hsl2rgb(hsl);
    }

    /* 5 — Vignette (radial, smooth) */
    if (u_vigStr > 0.001)
    {
        vec2 uv = texCoord0 - vec2(0.5);
        float dist = length(uv) * 2.0 / sqrt(2.0);
        float inner = max(0.0, 1.0 - u_vigSmooth);
        float alpha = smoothstep(inner, 1.0, dist) * u_vigStr;

        rgb = mix(rgb, u_vigColor, clamp(alpha, 0.0, 1.0));
    }

    /* 6 — Film grain */
    if (u_grainStr > 0.001)
    {
        vec2 texSize = vec2(textureSize(Sampler0, 0));
        vec2 grainUV = floor(texCoord0 * texSize / max(1.0, u_grainSize));
        float noise = hash(grainUV + vec2(u_grainSeed));

        rgb += (noise - 0.5) * u_grainStr * 2.0;
    }

    /* Vintage film flicker & scratches */
    if (u_vintage > 0.001)
    {
        float flicker = sin(u_time * 73.0) * cos(u_time * 59.0) * 0.07 * u_vintage;

        rgb += vec3(flicker);

        float scratchX = hash(vec2(floor(distortedUV.x * 250.0), floor(u_time * 16.0)));

        if (scratchX > 0.993)
        {
            rgb *= mix(1.0, 0.45, u_vintage);
        }
    }

    /* 7 — VHS scanlines and static noise */
    if (u_vhs > 0.001)
    {
        float scanline = sin(distortedUV.y * 300.0 - u_time * 15.0) * 0.08 * u_vhs;

        rgb -= vec3(scanline);

        float vhsNoise = hash(distortedUV + vec2(u_time * 0.01));

        rgb = mix(rgb, vec3(vhsNoise), 0.03 * u_vhs);
    }

    /* 8 — Cinematic light leak flare */
    if (u_lightLeak > 0.001)
    {
        float leakGrad = smoothstep(1.2, 0.0, length(distortedUV - vec2(0.0, 0.4)));
        float leakPulse = 0.65 + 0.35 * sin(u_time * 1.8 + cos(u_time * 1.2));
        vec3 leakColor = vec3(0.95, 0.48, 0.12) * leakGrad * leakPulse * u_lightLeak;

        float blueGrad = smoothstep(1.5, 0.0, length(distortedUV - vec2(1.0, 0.7)));
        vec3 blueColor = vec3(0.12, 0.35, 0.95) * blueGrad * (0.8 + 0.2 * cos(u_time * 0.9)) * u_lightLeak * 0.45;

        rgb += leakColor + blueColor;
    }

    /* 9 — Projector dust & specks (60s tape / projector) */
    if (u_dust > 0.001)
    {
        float dustTime = floor(u_time * 12.0);

        for (int i = 0; i < 3; i++)
        {
            vec2 randPos = vec2(
                hash(vec2(dustTime, float(i) * 15.3)),
                hash(vec2(dustTime, float(i) * 31.7))
            );

            float spawnProb = hash(vec2(dustTime, float(i) * 7.9));

            if (spawnProb < u_dust)
            {
                vec2 diff = distortedUV - randPos;

                diff.x *= 1.77;

                /* Randomly rotate the coordinates for each speck */
                float rotAngle = hash(vec2(dustTime, float(i) * 19.3)) * 6.28318;
                float cosA = cos(rotAngle);
                float sinA = sin(rotAngle);
                vec2 rotatedDiff = vec2(
                    diff.x * cosA - diff.y * sinA,
                    diff.x * sinA + diff.y * cosA
                );

                float typeDecider = hash(vec2(dustTime, float(i) * 88.1));

                if (typeDecider < 0.33)
                {
                    /* Type A: rounded / irregular speck (soot flake) */
                    float angle = atan(rotatedDiff.y, rotatedDiff.x);
                    float deform = 1.0 + 0.4 * sin(angle * 4.0) + 0.3 * cos(angle * 7.0 + 0.8);
                    float rLimit = 0.008 * u_dust * deform;

                    if (length(rotatedDiff) < rLimit)
                    {
                        rgb = mix(rgb, vec3(1.0), 0.95);
                    }
                }
                else if (typeDecider < 0.66)
                {
                    /* Type B: thread / curved lint hair */
                    float hairLength = 0.022 * u_dust;
                    float hairThickness = 0.0010 * u_dust;
                    float bend = sin(rotatedDiff.x * 180.0) * 0.005;

                    if (abs(rotatedDiff.x) < hairLength && abs(rotatedDiff.y - bend) < hairThickness)
                    {
                        rgb = mix(rgb, vec3(1.0), 0.95);
                    }
                }
                else
                {
                    /* Type C: deformed elongated ellipse speck (dust fiber clump) */
                    vec2 stretched = vec2(rotatedDiff.x * 2.8, rotatedDiff.y);
                    float angle = atan(stretched.y, stretched.x);
                    float deform = 1.0 + 0.35 * sin(angle * 3.0);
                    float rLimit = 0.012 * u_dust * deform;

                    if (length(stretched) < rLimit)
                    {
                        rgb = mix(rgb, vec3(1.0), 0.95);
                    }
                }
            }
        }
    }

    fragColor = vec4(clamp(rgb, 0.0, 1.0), texture(Sampler0, distortedUV).a);
}
