package gbeic.bbsplusplus.mixin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.api.IBbsppAnimation;
import mchorse.bbs_mod.cubic.data.animation.Animation;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.TreeMap;

/**
 * Bedrock 动画解析兼容层。
 *
 * <p>BBS 原生 {@link GeoAnimationParser} 按自身格式解析，对 Blockbench 导出的 Bedrock
 * 动画有三处兼容缺口，统一在本 Mixin 补齐：
 * <ol>
 *   <li><b>步阶帧（step）</b>：Blockbench 把步阶帧导出为 {@code {"pre": 前值, "post": 本值}}
 *       瞬变对，原生只读 post、丢弃 pre，步阶退化为线性滑动。在 {@code parseChannel} 尾部
 *       识别带 {@code pre} 数组的帧，把前一段插值标为 {@link Interpolations#CONST}。
 *       注意 BBS 的 {@code KeyframeSegment} 用段内<b>首帧 a</b> 的插值，而步阶作用于
 *       "进入该帧之前"的段，因此 CONST 标在<b>步阶帧的前一帧</b>上；步阶帧自身的
 *       {@code lerp_mode}（如 "catmullrom"）描述它到下一帧的段，必须原样保留。</li>
 *   <li><b>标量通道简写</b>：Bedrock 允许 {@code "scale": 2}，原生只接受数组或对象，
 *       遇裸数字会静默丢弃。在 {@code parse} 入口把 bone 上的数字值改写为 {@code [n,n,n]}。</li>
 *   <li><b>loop 字段</b>：Bedrock 的 {@code loop: true} / {@code "hold_on_last_frame"}
 *       原生不读，挂到解析后的 {@link Animation} 上供动作编辑器使用。</li>
 * </ol>
 */
@Mixin(value = GeoAnimationParser.class, remap = false)
public abstract class GeoAnimationParserMixin
{
    @Inject(method = "parseChannel", at = @At("TAIL"), remap = false)
    private static void bbspp$detectStepKeyframes(MolangParser parser,
        KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y,
        KeyframeChannel<MolangExpression> z, JsonElement element, CallbackInfo ci)
    {
        try
        {
            if (!element.isJsonObject())
            {
                return;
            }

            TreeMap<Float, JsonElement> frames = new TreeMap<>();

            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
            {
                try
                {
                    frames.put(Float.parseFloat(entry.getKey()), entry.getValue());
                }
                catch (NumberFormatException ignored)
                {
                    /* 非时间键（如 vector/lerp_mode）跳过 */
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

                if (!frame.has("pre") || !frame.get("pre").isJsonArray()
                    || frame.getAsJsonArray("pre").size() < 3)
                {
                    continue;
                }

                float tick = entry.getKey() * 20F;

                bbspp$markPreviousConst(x, tick);
                bbspp$markPreviousConst(y, tick);
                bbspp$markPreviousConst(z, tick);
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("Blockbench 步阶帧识别失败：{}", e.getMessage());
        }
    }

    @Inject(method = "parse", at = @At("HEAD"), remap = false)
    private static void bbspp$normalizeScalarChannels(MolangParser parser, String name,
        JsonObject object, CallbackInfoReturnable<Animation> cir)
    {
        try
        {
            JsonElement bones = object.get("bones");

            if (bones == null || !bones.isJsonObject())
            {
                return;
            }

            for (Map.Entry<String, JsonElement> bone : bones.getAsJsonObject().entrySet())
            {
                if (!bone.getValue().isJsonObject())
                {
                    continue;
                }

                JsonObject boneObject = bone.getValue().getAsJsonObject();

                for (String channel : new String[]{"position", "rotation", "scale"})
                {
                    JsonElement value = boneObject.get(channel);

                    if (value != null && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isNumber())
                    {
                        double n = value.getAsDouble();
                        JsonArray array = new JsonArray();

                        array.add(n);
                        array.add(n);
                        array.add(n);
                        boneObject.add(channel, array);
                    }
                }
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("标量动画通道规范化失败：{}", e.getMessage());
        }
    }

    @Inject(method = "parse", at = @At("TAIL"), remap = false)
    private static void bbspp$readLoop(MolangParser parser, String name,
        JsonObject object, CallbackInfoReturnable<Animation> cir)
    {
        try
        {
            Animation animation = cir.getReturnValue();

            if (animation instanceof IBbsppAnimation ext && object.has("loop"))
            {
                JsonElement loop = object.get("loop");
                boolean looping = loop.isJsonPrimitive() && loop.getAsJsonPrimitive().isBoolean()
                    ? loop.getAsBoolean()
                    : "hold_on_last_frame".equals(loop.getAsString());

                ext.bbspp$setLoop(looping);
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("动画 loop 字段读取失败：{}", e.getMessage());
        }
    }

    /**
     * 把步阶帧之前那一帧的插值设为 {@link Interpolations#CONST}，使前一段保持前值、
     * 在步阶帧时刻瞬跳到 post。步阶帧自身的插值不动。
     */
    @Unique
    private static void bbspp$markPreviousConst(KeyframeChannel<MolangExpression> channel, float stepTick)
    {
        Keyframe<MolangExpression> prev = null;

        for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
        {
            float t = keyframe.getTick();

            if (t < stepTick - 0.001F && (prev == null || t > prev.getTick()))
            {
                prev = keyframe;
            }
        }

        if (prev != null)
        {
            prev.getInterpolation().setInterp(Interpolations.CONST);
        }
    }
}
