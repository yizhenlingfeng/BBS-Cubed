package gbeic.bbsplusplus.mixin.morph;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code Texture.translucent} 的写入口。
 *
 * <p>BBS 只在 {@code uploadTexture(int, int, Pixels)} 里计算这个标志
 * （内部调用 {@code scanTranslucency(pixels)}），而该重载会
 * {@code pixels.delete()} 掉传入的缓冲区。</p>
 *
 * <p>形变纹理需要跨帧复用同一块 {@code Pixels}，因此改用
 * {@code glTexSubImage2D} 上传——但这绕过了那个重载，{@code translucent}
 * 会永远停在 {@code false}，于是 {@code ModelFormRenderer} 不把纹理送进
 * {@code FormTranslucentQueue}，半透明像素被当成不透明绘制，
 * 表现为「淡入淡出完全不生效」。</p>
 *
 * <p>这里只加一个 setter，让形变纹理能按自己的扫描结果更新该标志。</p>
 */
@Mixin(targets = "mchorse.bbs_mod.graphics.texture.Texture", remap = false)
public interface TextureTranslucentAccessor
{
    @Accessor("translucent")
    void bbsppp$setTranslucent(boolean translucent);
}
