package gbeic.bbsplusplus.mixin.client;

import com.google.gson.JsonArray;
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

/**
 * 识别 Blockbench 原生导出的步阶(step)关键帧:bedrock 格式没有 step 标记,
 * Blockbench 把它导出成"下一帧带 {pre: 前帧值, post: 本帧值}"的瞬变对,而
 * BBS 的 GeoAnimationParser 只读 post、丢弃 pre —— step 就退化成了线性滑动。
 *
 * <p>parseChannel TAIL 补一遍扫描:时间键值带 pre 数组、无 catmullrom 标记
 * (catmullrom 的 include_pre 分支也会写 pre)、且 pre 与前一关键帧的常量值
 * 相等(证明是"保持前值"而非真瞬变)时,把前一关键帧的插值改为 CONST ——
 * BBS 的 CONST 语义(保持该帧值到下一帧)与 Blockbench step 一致。
 * 同时修复了此类动画在 BBS 里的播放效果。</p>
 */
@Mixin(value = GeoAnimationParser.class, remap = false)
public abstract class GeoAnimationParserMixin
{
    @Inject(method = "parseChannel", at = @At("TAIL"), remap = false)
    private static void bbspp_cml$detectStepKeyframes(MolangParser parser, KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, JsonElement element, CallbackInfo ci)
    {
        if (!element.isJsonObject())
        {
            return;
        }

        JsonObject object = element.getAsJsonObject();

        if (object.has("vector"))
        {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            float time;

            try
            {
                time = Float.parseFloat(entry.getKey());
            }
            catch (Exception e)
            {
                continue;
            }

            JsonElement value = entry.getValue();

            if (!value.isJsonObject())
            {
                continue;
            }

            JsonObject frame = value.getAsJsonObject();

            if (!frame.has("pre") || !frame.get("pre").isJsonArray() || frame.has("lerp_mode"))
            {
                continue;
            }

            JsonArray pre = frame.getAsJsonArray("pre");

            if (pre.size() < 3)
            {
                continue;
            }

            float tick = time * 20F;

            bbspp_cml$markPreviousConst(x, tick, pre.get(0));
            bbspp_cml$markPreviousConst(y, tick, pre.get(1));
            bbspp_cml$markPreviousConst(z, tick, pre.get(2));
        }
    }

    @Unique
    private static void bbspp_cml$markPreviousConst(KeyframeChannel<MolangExpression> channel, float tick, JsonElement preValue)
    {
        if (!preValue.isJsonPrimitive() || !preValue.getAsJsonPrimitive().isNumber())
        {
            return;
        }

        Keyframe<MolangExpression> previous = null;

        for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
        {
            if (keyframe.getTick() < tick - 0.001F && (previous == null || keyframe.getTick() > previous.getTick()))
            {
                previous = keyframe;
            }
        }

        if (previous == null)
        {
            return;
        }

        double expected = preValue.getAsDouble();
        double actual;

        try
        {
            actual = previous.getValue().get();
        }
        catch (Exception e)
        {
            return;
        }

        if (Math.abs(actual - expected) < 0.001D)
        {
            previous.getInterpolation().setInterp(Interpolations.CONST);
        }
    }
}
