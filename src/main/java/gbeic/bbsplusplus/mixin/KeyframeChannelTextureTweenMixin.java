package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.texture.TextureTweenManager;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 阻止纹理补间的内存临时 Link 进入关键帧数据。
 *
 * <p>BBS 在补间区间内插入关键帧时会把当前插值结果作为新值。纹理补间的当前结果只存在于内存，
 * 如果被保存，重启后就无法读取。本类在插入、加载和保存三个入口统一用时间上最近的真实纹理替换它。</p>
 */
@Mixin(value = KeyframeChannel.class, remap = false)
public class KeyframeChannelTextureTweenMixin
{
    /**
     * 注入目标：{@link KeyframeChannel#insert(float, Object)} 的待插入值。
     * 注入原因：补间区间内新增关键帧时，原版会传入当前生成的临时纹理。
     * 修改行为：仅对模型原生纹理轨道及迁移期旧轨道，将临时 Link 替换为时间上最近的真实关键帧纹理。
     */
    @ModifyVariable(method = "insert", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Object bbsppp$replaceRuntimeLinkBeforeInsert(Object value, float tick)
    {
        KeyframeChannel<?> channel = (KeyframeChannel<?>) (Object) this;

        if (!bbsppp$isTextureTweenChannel(channel.getId()) || !(value instanceof Link link)
            || !TextureTweenManager.isRuntimeLink(link))
        {
            return value;
        }

        Link replacement = bbsppp$findClosestPersistentLink(channel, tick);

        return replacement == null ? value : replacement;
    }

    /**
     * 注入目标：{@link KeyframeChannel#fromData(BaseType)} 完成后。
     * 注入原因：旧影片可能已经保存了无法跨进程使用的临时 Link。
     * 修改行为：加载相关纹理轨道后立即用相邻真实纹理修复内存中的坏关键帧。
     */
    @Inject(method = "fromData", at = @At("TAIL"))
    private void bbsppp$sanitizeRuntimeLinksAfterLoad(BaseType data, CallbackInfo ci)
    {
        bbsppp$sanitizeRuntimeLinks((KeyframeChannel<?>) (Object) this);
    }

    /**
     * 注入目标：{@link KeyframeChannel#toData()} 序列化前。
     * 注入原因：复制、粘贴或其他扩展可能绕过常规插入入口。
     * 修改行为：保存前执行最后一次清理，确保临时 Link 不会再次写入影片文件。
     */
    @Inject(method = "toData", at = @At("HEAD"))
    private void bbsppp$sanitizeRuntimeLinksBeforeSave(CallbackInfoReturnable<BaseType> cir)
    {
        bbsppp$sanitizeRuntimeLinks((KeyframeChannel<?>) (Object) this);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bbsppp$sanitizeRuntimeLinks(KeyframeChannel<?> channel)
    {
        if (!bbsppp$isTextureTweenChannel(channel.getId()))
        {
            return;
        }

        List<? extends Keyframe<?>> keyframes = channel.getKeyframes();

        for (Keyframe<?> keyframe : keyframes)
        {
            if (keyframe.getValue() instanceof Link link && TextureTweenManager.isRuntimeLink(link))
            {
                Link replacement = bbsppp$findClosestPersistentLink(channel, keyframe.getTick());

                if (replacement != null)
                {
                    ((Keyframe) keyframe).setValue(replacement);
                }
            }
        }
    }

    private static Link bbsppp$findClosestPersistentLink(KeyframeChannel<?> channel, float tick)
    {
        Link closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            if (!(keyframe.getValue() instanceof Link candidate) || TextureTweenManager.isRuntimeLink(candidate))
            {
                continue;
            }

            float distance = Math.abs(keyframe.getTick() - tick);

            if (distance < closestDistance)
            {
                closest = candidate;
                closestDistance = distance;
            }
        }

        return closest;
    }

    private static boolean bbsppp$isTextureTweenChannel(String id)
    {
        return "texture".equals(id) || id.endsWith("/texture")
            || "bbsppp_texture_tween".equals(id) || id.endsWith("/bbsppp_texture_tween");
    }
}
