package gbeic.bbsplusplus.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import gbeic.bbsplusplus.client.screen.ScreenEffectRenderer;
import gbeic.bbsplusplus.clips.HotbarClip;
import gbeic.bbsplusplus.clips.HotbarState;
import gbeic.bbsplusplus.clips.screen.ColorClip;
import gbeic.bbsplusplus.clips.screen.ColorEffect;
import gbeic.bbsplusplus.ui.film.UIHotbarRenderer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 注入 {@link BBSRendering#onWorldRenderEnd()}，追加 CML 的 hotbar HUD 与 cinematic/color 屏幕效果。
 *
 * <p>第一性原理：{@code HotbarClip.applyClip} 把 HotbarState 塞进 {@code context.clipData}，
 * {@code CinematicClip.applyClip} 把 ColorEffect 塞进 {@code ColorClip.getEffects(context)}。
 * clipData 在 {@code CameraWorkCameraController.apply} 每帧 clear 后由 clips 重新填充，
 * 故消费点必须在"apply 之后、下一帧 clear 之前"同一渲染帧内读取对应 controller 的 context。</p>
 *
 * <p>对齐 CML 的 BBSRendering.onWorldRenderEnd 三分支结构：
 * <ul>
 *   <li><b>播放</b>：{@code PlayCameraController.getContext()}（/play 或触发器播放）；</li>
 *   <li><b>录制</b>：{@code VideoRecorder.isRecording()} 且当前为 {@code CameraWorkCameraController}
 *       （编辑器渲染导出走 runner，属于其子类）；</li>
 *   <li><b>编辑器预览</b>：film 面板 appear() 时恒开 customSize 且把 runner 加入相机控制器，
 *       每帧 {@code RunnerCameraController.setup -> apply} 填充 clipData —— 编辑关键帧后
 *       本分支用 {@code panel.getRunner().getContext()} 即时渲染，实现"编辑立即可见"。</li>
 * </ul></p>
 *
 * <p>onWorldRenderEnd 时期投影矩阵仍是 3D 透视，绘制 2D HUD 必须临时切正交投影
 * （CML 同款 ortho(0..w, h..0)），渲染完恢复缓存矩阵，防止污染后续渲染。</p>
 *
 * <p>@At("RETURN") 会在 {@code !customSize} 的提前 return 与末尾 return 两个出口各注入一次，
 * 但同一帧只会经过其中一个出口，且编辑器分支自带 isCustomSize() 条件，故不会重复渲染。</p>
 *
 * <p>remap=false：bbs 自身方法不被 yarn obf 映射，编译期 named 描述符即运行时描述符，
 * 本 addon 打包后 refmap 为空，显式 remap=false + 字面描述符是最稳路线。
 * 旧版曾注入 {@code renderHud}（HudRenderCallback 路径），但播放时 InGameHud.render 被
 * bbs 的 InGameHudMixin 在 HEAD cancel，该回调根本不触发，属死代码，已移除。</p>
 */
@Mixin(BBSRendering.class)
public abstract class BBSRenderingMixin
{
    @Inject(method = "onWorldRenderEnd()V", at = @At("RETURN"), remap = false)
    private static void bbspp_cml$renderOverlays(CallbackInfo ci)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (current instanceof PlayCameraController controller)
        {
            bbspp_cml$renderContextOverlays(mc, controller.getContext());
        }
        else if (BBSModClient.getVideoRecorder().isRecording() && current instanceof CameraWorkCameraController controller)
        {
            bbspp_cml$renderContextOverlays(mc, controller.getContext());
        }

        /* 编辑器预览分支：film 面板打开（appear 恒置 customSize=true）。
         * 该分支仅在末尾 return 注入点条件成立（提前 return 仅发生于 customSize=false）。 */
        if (BBSRendering.isCustomSize()
            && UIScreen.getCurrentMenu() instanceof UIDashboard dashboard
            && dashboard.getPanels().panel instanceof UIFilmPanel panel
            && panel.getData() != null)
        {
            bbspp_cml$renderContextOverlays(mc, panel.getRunner().getContext());
        }
    }

    @Unique
    private static void bbspp_cml$renderContextOverlays(MinecraftClient mc, ClipContext context)
    {
        if (context == null)
        {
            return;
        }

        List<HotbarState> hotbars = HotbarClip.getHotbars(context);
        List<ColorEffect> effects = ColorClip.getEffects(context);

        if (hotbars.isEmpty() && effects.isEmpty())
        {
            return;
        }

        DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());
        Batcher2D batcher = new Batcher2D(drawContext);
        Window window = mc.getWindow();
        int w = window.getScaledWidth();
        int h = window.getScaledHeight();
        Matrix4f cache = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f ortho = new Matrix4f().ortho(0, w, h, 0, -1000, 3000);

        RenderSystem.setProjectionMatrix(ortho, VertexSorter.BY_Z);
        RenderSystem.disableDepthTest();

        UIHotbarRenderer.renderHotbars(batcher.getContext().getMatrices(), batcher, hotbars, 0, 0, w, h);
        ScreenEffectRenderer.render(batcher, context, w, h);

        /* 恢复投影前必须把缓冲中的 2D 图元落盘，否则会以透视矩阵提交。 */
        batcher.flush();

        RenderSystem.enableDepthTest();
        RenderSystem.setProjectionMatrix(cache, VertexSorter.BY_Z);
    }
}
