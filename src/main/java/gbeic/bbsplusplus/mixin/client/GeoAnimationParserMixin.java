package gbeic.bbsplusplus.mixin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

import gbeic.bbsplusplus.BBSPlusPlusMod;

/**
 * BBS Bedrock 动画解析兼容层。
 *
 * <p>两个职责：
 * <ol>
 *   <li><b>Step 关键帧识别</b>：Blockbench 把步阶帧导出为
 *       {@code {"pre": 前帧, "post": 本帧}} 瞬变对，BBS 原生只读 post、丢弃 pre，
 *       导致 step 退化为线性滑动。本 Mixin 在 {@code parseChannel} 尾部把带 pre 且
 *       无 {@code lerp_mode} 的帧标记为 CONST 插值。注意 BBS 的 {@code KeyframeSegment}
 *       从后一帧 b 读取插值类型，因此必须标记 step 帧本身。</li>
 *   <li><b>标量通道规范化</b>：BBS 原生 {@code parseChannel} 只接受
 *       {@code [x,y,z]} 数组或关键帧对象，遇到 Bedrock 允许的裸数字简写（如
 *       {@code "scale": 2}）会静默丢弃。本 Mixin 在 {@code parse} 入口把
 *       {@code bones.<bone>.{position,rotation,scale}} 上的数字原值改写为
 *       {@code [n,n,n]}，让后续走原有数组解析路径。</li>
 * </ol>
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
            BBSPlusPlusMod.LOGGER.warn("Blockbench step 帧识别失败：{}", e.getMessage());
        }
    }

    @Inject(method = "parse", at = @At("HEAD"), remap = false)
    private static void bbspp_cml$normalizeScalarChannels(MolangParser parser, String name,
                                                          JsonObject object, CallbackInfoReturnable<Animation> cir)
    {
        try
        {
            JsonElement bonesEl = object.get("bones");
            if (bonesEl == null || !bonesEl.isJsonObject())
            {
                return;
            }

            for (Map.Entry<String, JsonElement> boneEntry : bonesEl.getAsJsonObject().entrySet())
            {
                if (!boneEntry.getValue().isJsonObject())
                {
                    continue;
                }

                JsonObject bone = boneEntry.getValue().getAsJsonObject();

                for (String channel : new String[]{"position", "rotation", "scale"})
                {
                    JsonElement value = bone.get(channel);

                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
                    {
                        double n = value.getAsDouble();
                        JsonArray arr = new JsonArray();
                        arr.add(n);
                        arr.add(n);
                        arr.add(n);
                        bone.add(channel, arr);
                    }
                }
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("标量动画通道规范化失败：{}", e.getMessage());
        }
    }

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
