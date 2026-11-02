package gbeic.bbsplusplus.api;

/**
 * 附魔光效的连续参数：流动速度与亮度。
 * 速度 0 表示静止；亮度 1 为原始强度，大于 1 会增强光效图层的自发光。
 */
public interface GlintSettingsHolder
{
    float DEFAULT_SPEED = 1F;
    float DEFAULT_BRIGHTNESS = 1F;
    float MAX_SPEED = 4F;
    float MAX_BRIGHTNESS = 4F;

    float bbspp_cml$getGlintSpeed();

    void bbspp_cml$setGlintSpeed(float speed);

    float bbspp_cml$getGlintBrightness();

    void bbspp_cml$setGlintBrightness(float brightness);
}
