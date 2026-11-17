package gbeic.bbsplusplus.mixin12111;

import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.particles.UIParticleSchemeRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修好粒子预览里被放大成一片红的坐标轴。
 *
 * <p>现象：打开粒子编辑器的预览面板，视口里会出现一块铺满画面的纯红色矩形，粒子却画在上面对。
 * 那块红就是 {@code Draw.coolerAxes} 的 X 轴（{@code Colors.RED = 0xff4059}，与截图逐像素吻合），
 * 被撑到贴住摄像机、跨过近裁剪面，于是整块糊在屏幕上。</p>
 *
 * <p>根因在 1.21.11 上游移植：{@code UIParticleSchemeRenderer#renderGrid} 要画坐标轴时，用的是</p>
 * <pre>MatrixStack stack = new MatrixStack();
 * stack.multiplyPositionMatrix(new Matrix4f(InverseView.get()).invert());</pre>
 * <p>而 {@code InverseView} 只装了 {@code camera.view} 的 <b>3x3 部分</b>（{@code Camera#updateView}
 * 只做三次 rotate，平移放在独立的 {@code camera.position} 里），所以取反再取反拿回来的是
 * <b>纯旋转</b> {@code R}，少了 {@code T(-camera.position)}。坐标轴是唯一一批「用世界坐标直接喂给
 * 矩阵」的几何（粒子不是——{@code ParticleEmitter#render} 自己先减掉了 {@code cX/cY/cZ}，这也是为什么
 * 粒子看着是对的），于是这根 2 格长的轴被放到了摄像机鼻子底下，跨近裁剪面铺满视口。</p>
 *
 * <p>修法：把这里的栈换成 {@code UIModelRenderer#createCameraStack()} 同款的
 * {@code R * T(-camera.position)}，与同一面板里地面网格用的完全是同一个相机栈。{@code createCameraStack}
 * 还会再乘一个 {@code transform}（模型方块那种带自身变换的预览才有，普通预览是单位矩阵），
 * 粒子预览永远是单位矩阵，所以这里不重复乘。</p>
 *
 * <p>只重定向 {@code renderGrid} 里的那一次 {@code coolerAxes} 调用；粒子的 {@code renderUserModel}
 * 保持上游原样不动（它是对的，且换成完整相机栈反而会让粒子整体偏移一个摄像机位置）。</p>
 */
@Environment(EnvType.CLIENT)
@Mixin(value = UIParticleSchemeRenderer.class, remap = false)
public class UIParticleSchemeRendererMixin121
{
    @Redirect(
        method = "renderGrid",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/graphics/Draw;coolerAxes(Lnet/minecraft/client/util/math/MatrixStack;FF)V"),
        require = 0
    )
    private void bbspp$coolerAxes(MatrixStack stack, float axisSize, float axisOffset)
    {
        /* camera 是 UIModelRenderer 的 public 字段，直接取，省一次 @Shadow 解析 */
        UIModelRenderer renderer = (UIModelRenderer) (Object) this;

        MatrixStack axes = new MatrixStack();

        axes.multiplyPositionMatrix(new Matrix4f(renderer.camera.view).translate(
            (float) -renderer.camera.position.x,
            (float) -renderer.camera.position.y,
            (float) -renderer.camera.position.z
        ));

        Draw.coolerAxes(axes, axisSize, axisOffset);
    }
}
