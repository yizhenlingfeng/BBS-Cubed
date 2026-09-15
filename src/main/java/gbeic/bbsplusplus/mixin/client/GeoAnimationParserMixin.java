package gbeic.bbsplusplus.mixin.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.geo.GeoAnimationParser;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.TreeMap;
import gbeic.bbsplusplus.BBSPlusPlusMod;

/**
 * 识别 Blockbench 原生导出的步阶(step)关键帧。
 *
 * <p>Bedrock 格式没有 step 标记，Blockbench 把 step 帧导出为
 * {@code {"pre": 前帧值, "post": 本帧值}} 的瞬变对。BBS 原生
 * {@code GeoAnimationParser} 只读 post、丢弃 pre，导致 step 退化为线性滑动。
 *
 * <p>本 Mixin 在 {@code parseChannel} 尾部扫描所有时间帧，将带 pre 且无
 * {@code lerp_mode} 的帧标记为 CONST 插值。
 *
 * <p><b>关键方向：</b>BBS 的 {@code KeyframeSegment} 插值从后一帧 b 读取
 * 插值类型（{@code CubicModelAnimator.interpolateSegment} 中
 * {@code segment.b.getInterpolation()}），因此必须标记 step 帧本身为 CONST，
 * 使前一帧→step 帧的段保持前一帧值、到 step 帧跳变到 post，与 Blockbench
 * step 语义一致。
 *
 * <p>{@code lerp_mode=catmullrom} 的帧也可能带 pre（include_pre 分支），
 * 那是真平滑曲线，必须跳过。
 */
@Mixin(value = GeoAnimationParser.class, remap = false)
public abstract class GeoAnimationParserMixin
{
    @Inject(method = "parseChannel", at = @At("TAIL"), remap = false)
    private static void bbspp_cml$detectStepKeyframes(MolangParser parser, KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, JsonElement element, CallbackInfo ci)
    {
        try
        {
            if (!element.isJsonObject())
            {
                return;
            }

            JsonObject object = element.getAsJsonObject();

            /* vector 格式是单帧全向量，不含时间键，不是 step */
            if (object.has("vector"))
            {
                return;
            }

            /* 按时间排序，确保遍历顺序稳定 */
            TreeMap<Float, JsonElement> frames = new TreeMap<>();

            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                try
                {
                    frames.put(Float.parseFloat(entry.getKey()), entry.getValue());
                }
                catch (NumberFormatException ignored)
                {
                    /* 非时间键跳过 */
                }
            }

            for (Map.Entry<Float, JsonElement> entry : frames.entrySet())
            {
                JsonElement value = entry.getValue();

                if (!value.isJsonObject())
                {
                    continue;
                }

                JsonObject frame = value.getAsJsonObject();

                /* 只处理带 pre 数组、无 lerp_mode 的帧（Blockbench step 格式）。
                 * catmullrom 的 include_pre 分支也写 pre，需排除。 */
                if (!frame.has("pre")
                    || !frame.get("pre").isJsonArray()
                    || frame.has("lerp_mode"))
                {
                    continue;
                }

                if (frame.getAsJsonArray("pre").size() < 3)
                {
                    continue;
                }

                float tick = entry.getKey() * 20F;

                bbspp_cml$markTickConst(x, tick);
                bbspp_cml$markTickConst(y, tick);
                bbspp_cml$markTickConst(z, tick);
            }
        }
        catch (Exception e)
        {
            /* step 检测失败不应影响动画解析 */
            BBSPlusPlusMod.LOGGER.warn("Blockbench step 帧识别失败：{}", e.getMessage());
        }
    }

    /** 将指定 tick 的关键帧插值设为 CONST。 */
    @Unique
    private static void bbspp_cml$markTickConst(KeyframeChannel<MolangExpression> channel, float tick)
    {
        for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
        {
            if (Math.abs(keyframe.getTick() - tick) < 0.001F)
            {
                keyframe.getInterpolation().setInterp(Interpolations.CONST);
                return;
            }
        }
    }
}
