package gbeic.bbsplusplus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.util.XRayManager;
import mod.chloeprime.aaaparticles.api.client.EffectDefinition;
import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerManager;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.internal.RenderStateCapture;
import mod.chloeprime.aaaparticles.client.render.RenderUtil;
import net.minecraft.client.gl.Framebuffer;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 混入 {@link EffectDefinition}，在世界粒子绘制完成后追加 X-Ray 穿透粒子的渲染。
 *
 * <p>通过独立的 {@link EffekseerManager}（{@link XRayManager}）渲染标记为 ignoreDepth
 * 的粒子，配合 {@code glDepthRange(0,0)} 与深度缓冲备份/恢复，实现穿透方块渲染
 * 且不污染深度缓冲。</p>
 *
 * <p><b>前置判断 {@link XRayManager#isActive()} 的必要性：</b>本方法挂在
 * {@code EffectDefinition.draw} 的 TAIL 上，只要场景里画了任意一个 WORLD 类型粒子就会触发，
 * 但下面这整套（创建独立管理器、备份/恢复当前 Framebuffer 的深度缓冲、
 * 改写 glDepthRange / glDepthMask / GL_DEPTH_TEST、解绑 0~7 号纹理）只为穿透粒子服务。
 * 若玩家从未启用穿透粒子，这些操作没有任何收益，却会改动全局 GL 深度状态与
 * 「当前绑定」Framebuffer 的深度缓冲；而 BBS 的编辑器预览（模型方块页、影片页）正是把世界
 * 渲染进自己的离屏 Framebuffer（{@code BBSRendering} 的 custom-size 目标），
 * 两者冲突会把预览画面整个抹黑，且不抛任何异常。故无穿透粒子时直接返回，不碰 GL 状态。</p>
 *
 * <p><b>GL 状态按原值恢复：</b>深度写入与深度测试必须在进入前记录、退出时还原。
 * 若写死恢复成 {@code glDepthMask(true)} + {@code enableDepthTest()}，当进入前
 * 深度测试本就关闭时（BBS 的 UI/预览渲染大量使用该状态）会把它强行打开，属状态泄漏，
 * 同样会吃掉后续画面。</p>
 */
@Mixin(value = EffectDefinition.class, remap = true)
public class EffectDefinitionMixin
{
    @Inject(method = "draw", at = @At("TAIL"), remap = true)
    private static void bbspp_afterDraw(
            ParticleEmitter.Type type,
            Vector3f front,
            Vector3f pos,
            int w, int h,
            float[] camera,
            float[] projection,
            float deltaFrames,
            float partialTicks,
            Framebuffer background,
            CallbackInfo ci)
    {
        if (type != ParticleEmitter.Type.WORLD || !XRayManager.isActive())
        {
            return;
        }

        EffekseerManager xrayManager = XRayManager.get();

        // 同步所有必需的渲染状态
        xrayManager.setViewport(w, h);
        xrayManager.setCameraMatrix(camera);
        xrayManager.setProjectionMatrix(projection);
        xrayManager.setCameraParameter(front.x, front.y, front.z, pos.x, pos.y, pos.z);

        xrayManager.update(deltaFrames);

        /* 记录进入前的深度状态，退出时按原值还原。 */
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        // 深度测试覆盖：由于 Effekseer C++ 底层会强制重设 GL_DEPTH_TEST 和 GL_DEPTH_FUNC，
        // 我们使用 glDepthRange(0.0, 0.0) 将深度强制压缩到 0.0（最近处），使得 GL_LEQUAL 测试必然通过！
        // 同时，因为 C++ 底层可能会强行开启 glDepthMask(true)，导致这个 0.0 被写死在深度缓冲中，
        // 进而导致随后的第一人称手臂在做深度测试时被这个 0.0 错误地遮挡（即发生粒子穿透右手的 Bug）。
        // 为了彻底解决这个问题，我们在粒子渲染前先备份当前的深度缓冲，然后在渲染后原样恢复！
        RenderUtil.copyCurrentDepthTo(RenderStateCapture.CAPTURED_WORLD_DEPTH_BUFFER);

        RenderSystem.disableDepthTest();
        GL11.glDepthRange(0.0, 0.0);
        GL11.glDepthMask(false);

        xrayManager.drawBack();
        xrayManager.drawFront();

        // 强行恢复深度缓冲（消除 C++ 底层的任何污染）
        RenderUtil.pasteToCurrentDepthFrom(RenderStateCapture.CAPTURED_WORLD_DEPTH_BUFFER);

        // 【BBS++ 核心修复】
        // Effekseer 的 C++ 底层不仅更改了深度测试，还会直接调用 OpenGL 原生方法绑定纹理（glActiveTexture 和 glBindTexture）。
        // 这种绕过 Minecraft RenderSystem 的行为，会导致 RenderSystem 的内部缓存（如当前激活的纹理单元、绑定的贴图 ID）与 OpenGL 实际状态严重脱节！
        // 因为我们在 RenderContextMixin 中把粒子渲染强行提前到了“画手”和“画BBS伪装”之前，如果在这里不把状态拨乱反正，
        // 接下来画手的时候，RenderSystem 以为自己早就绑定了玩家皮肤贴图而跳过绑定，最终导致手臂和 BBS 伪装使用错误的（或空的）贴图！
        for (int i = 0; i < 8; i++)
        {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            RenderSystem.bindTexture(0);
        }

        RenderSystem.activeTexture(GL13.GL_TEXTURE0);

        // 深度范围与深度函数由本方法主动改动过，这里恢复成 Minecraft 的常规值。
        GL11.glDepthRange(0.0, 1.0);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        // 深度写入与深度测试按进入前的原值还原，避免把本该关闭的状态强行打开。
        GL11.glDepthMask(prevDepthMask);

        if (prevDepthTest)
        {
            RenderSystem.enableDepthTest();
        }
        else
        {
            RenderSystem.disableDepthTest();
        }
    }
}
