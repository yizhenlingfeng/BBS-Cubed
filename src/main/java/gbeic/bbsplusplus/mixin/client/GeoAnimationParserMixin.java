package gbeic.bbsplusplus.mixin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.api.IBbsppAnimation;
import gbeic.bbsplusplus.api.IBbsppStepChannel;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Bedrock 动画解析兼容层，补齐 BBS 原生 {@link GeoAnimationParser} 对 Blockbench
 * 导出格式的三处支持缺口。
 *
 * <ul>
 *   <li><b>步阶帧</b>：识别步阶到达帧并写入对应标记，见 {@link #bbspp$applyStepFrames}；</li>
 *   <li><b>标量通道简写</b>：{@code "scale": 2} 规范化为 {@code [n, n, n]}；</li>
 *   <li><b>loop 字段</b>：读取 {@code loop} 供动作编辑器使用。</li>
 * </ul>
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
            bbspp$applyStepFrames(x, y, z, element);
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("Blockbench 步阶帧识别失败：{}", e.getMessage());
        }
    }

    /**
     * 步阶帧在 BBS 里有三个读取方，语义各不相同，必须分别标记：
     *
     * <ul>
     *   <li>{@code CubicModelAnimator} 取段内<b>后一帧</b>的插值——把到达帧标为
     *       {@link Interpolations#CONST}，播放时才会"保持前值 → 瞬跳"。这也是关键帧
     *       本身的插值类型，界面与后续读取方看到的都是 BBS 内置的步阶；</li>
     *   <li>pose / 影片轨道取段内<b>首帧</b>的插值——把到达帧的前一帧同样标为 CONST，
     *       在出发帧上表达同一段保持；</li>
 *   <li>动画转姿势需要知道"下一帧是不是步阶"，只靠插值无法区分 CONST 表示的是
 *       进入还是离开，因此把 tick 登记到通道（{@link IBbsppStepChannel}）。</li>
     * </ul>
     */
    @Unique
    private static void bbspp$applyStepFrames(KeyframeChannel<MolangExpression> x,
        KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, JsonElement element)
    {
        for (float seconds : bbspp$collectStepTimes(element))
        {
            float tick = seconds * 20F;

            bbspp$markConstAtTick(x, tick);
            bbspp$markConstAtTick(y, tick);
            bbspp$markConstAtTick(z, tick);

            bbspp$markPreviousConst(x, tick);
            bbspp$markPreviousConst(y, tick);
            bbspp$markPreviousConst(z, tick);

            bbspp$recordStepTick(x, tick);
            bbspp$recordStepTick(y, tick);
            bbspp$recordStepTick(z, tick);
        }
    }

    /**
     * 收集步阶"到达帧"的时间（秒）。判据来自动画文件已有的插值声明，不靠 {@code pre}
     * 的存在与否推断——Blockbench 对<b>有前值的平滑帧</b>同样会写 {@code pre}、{@code post}：
     *
     * <ul>
     *   <li>本帧 {@code pre != post}：到达本帧时确有瞬变；</li>
     *   <li>上一帧 {@code easing == "step"}：Bedrock 的 easing 描述"从该帧出发的段"，
     *       因此步阶到达帧是它的下一帧。</li>
     * </ul>
     *
     * <p>{@code pre == post} 的帧不加任何标记，交回原生 {@code lerp_mode} /
     * {@code easing} 解析，保留它原本的插值类型。</p>
     */
    @Unique
    private static Set<Float> bbspp$collectStepTimes(JsonElement element)
    {
        Set<Float> stepTimes = new TreeSet<>();

        if (!element.isJsonObject())
        {
            return stepTimes;
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
                /* 非时间键（如 lerp_mode）跳过 */
            }
        }

        List<Map.Entry<Float, JsonElement>> ordered = new ArrayList<>(frames.entrySet());

        for (int i = 0; i < ordered.size(); i++)
        {
            JsonElement value = ordered.get(i).getValue();

            if (!value.isJsonObject())
            {
                continue;
            }

            JsonObject frame = value.getAsJsonObject();
            JsonArray pre = bbspp$asVector(frame.get("pre"));
            JsonArray post = bbspp$asVector(frame.get("post"));

            if (pre != null && post != null && pre.size() >= 3 && post.size() >= 3
                && !pre.equals(post))
            {
                stepTimes.add(ordered.get(i).getKey());
            }

            if (i + 1 < ordered.size() && bbspp$isStepEasing(frame))
            {
                stepTimes.add(ordered.get(i + 1).getKey());
            }
        }

        return stepTimes;
    }

    /** {@code easing} 是否为 {@code "step"}（原生映射到 {@link Interpolations#CONST}）。 */
    @Unique
    private static boolean bbspp$isStepEasing(JsonObject frame)
    {
        JsonElement easing = frame.get("easing");

        return easing != null && easing.isJsonPrimitive() && "step".equals(easing.getAsString());
    }

    /** 取三轴数组，兼容原生支持的 {@code [x, y, z]} 与 {@code {"vector": [x, y, z]}}。 */
    @Unique
    private static JsonArray bbspp$asVector(JsonElement element)
    {
        if (element == null)
        {
            return null;
        }

        if (element.isJsonArray())
        {
            return element.getAsJsonArray();
        }

        JsonElement vector = element.isJsonObject() ? element.getAsJsonObject().get("vector") : null;

        return vector != null && vector.isJsonArray() ? vector.getAsJsonArray() : null;
    }

    /** 把时间恰好为 {@code tick} 的关键帧标为步阶。 */
    @Unique
    private static void bbspp$markConstAtTick(KeyframeChannel<MolangExpression> channel, float tick)
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

    /** 把该 tick 之前最近的一帧标为步阶，供取"段内首帧"插值的通道使用。 */
    @Unique
    private static void bbspp$markPreviousConst(KeyframeChannel<MolangExpression> channel, float tick)
    {
        Keyframe<MolangExpression> previous = null;

        for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
        {
            if (keyframe.getTick() < tick - 0.001F
                && (previous == null || keyframe.getTick() > previous.getTick()))
            {
                previous = keyframe;
            }
        }

        if (previous != null)
        {
            previous.getInterpolation().setInterp(Interpolations.CONST);
        }
    }

    @Unique
    private static void bbspp$recordStepTick(KeyframeChannel<MolangExpression> channel, float tick)
    {
        if (channel instanceof IBbsppStepChannel steps)
        {
            steps.bbspp$addStepTick(tick);
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

                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
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
}
