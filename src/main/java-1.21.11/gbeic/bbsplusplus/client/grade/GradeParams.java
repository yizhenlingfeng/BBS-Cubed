package gbeic.bbsplusplus.client.grade;

/**
 * 一次模型绘制的完整「纹理分级」参数：调色（tint）+ 白化（whiten）+ 附魔光效（glint）。
 *
 * <p>对应 1.20.x 的 {@code ModelTextureGradeShader} 里的四组 {@code GlUniform}。1.21.5+
 * 取消了逐 draw 的 uniform 派发，所以这些值一次性写进 64 字节的 std140 块，由
 * {@link GradePass} 在开 pass 之前上传。字段顺序必须与
 * {@code assets/bbsplusplus/shaders/core/model_grade.fsh} 的 {@code GradeParams} 一致：
 * <pre>
 *   Tint       = (tintR, tintG, tintB, tintA)
 *   Options    = (whiten, glint ? 1 : 0, glintPhase, glintBrightness)
 *   GlintColor = (glintR, glintG, glintB, glintA)
 *   Extra      = (0, 0, 0, 0)
 * </pre>
 *
 * @param active  这一帧是否任意一条通道开启（false 时完全不接管绘制）
 * @param tintR   调色色相红
 * @param tintG   调色色相绿
 * @param tintB   调色色相蓝
 * @param tintA   调色强度（0 = 不调色）
 * @param whiten  白化强度（0 = 不白化）
 * @param glint   附魔光效开关
 * @param glintR  光效颜色红
 * @param glintG  光效颜色绿
 * @param glintB  光效颜色蓝
 * @param glintA  光效颜色强度
 * @param glintPhase     光效相位 [0, 1)
 * @param glintBrightness 光效亮度倍率
 */
public record GradeParams(
    boolean active,
    float tintR, float tintG, float tintB, float tintA,
    float whiten,
    boolean glint,
    float glintR, float glintG, float glintB, float glintA,
    float glintPhase, float glintBrightness
)
{
    public static final GradeParams NONE = new GradeParams(false, 1F, 1F, 1F, 0F, 0F, false, 0F, 0F, 0F, 0F, 0F, 1F);

    /** 调色/白化是否需要替换基础绘制（光效不需要，它是一层叠加）。 */
    public boolean needBase()
    {
        return this.active && (this.tintA > 0.0001F || this.whiten > 0.0001F);
    }

    public boolean needGlint()
    {
        return this.active && this.glint;
    }
}
