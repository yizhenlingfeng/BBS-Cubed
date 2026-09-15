package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.PickTextureButtonHolder;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把"骨骼纹理"按钮带进 pose 关键帧属性面板（对齐 CML 的 UIPoseKeyframeFactory 布局）。
 *
 * <p>控件本体由 {@code UIPoseEditorMixin} 创建。FS 版 {@code resize()} 会先
 * {@code poseEditor.removeAll()}，并分别重建宽、窄布局。宽布局的右侧骨骼列表
 * 很高，因此按钮必须放进左侧参数列；若追加到整行之后，按钮前面就会
 * 留出与骨骼列表等高的空白。窄布局仍按顺序追加。</p>
 */
@Mixin(value = UIPoseKeyframeFactory.class, remap = false)
public abstract class UIPoseKeyframeFactoryMixin
{
    @Shadow
    private UIPoseKeyframeFactory.UIPoseFactoryEditor poseEditor;

    @Redirect(
        method = "resize()V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/utils/UI;column([Lmchorse/bbs_mod/ui/framework/elements/UIElement;)Lmchorse/bbs_mod/ui/framework/elements/UIElement;",
            ordinal = 0,
            remap = false
        ),
        require = 0,
        remap = false
    )
    private UIElement bbspp_cml$addWideLayout(UIElement[] layout)
    {
        UIElement[] merged = new UIElement[layout.length + 1];

        System.arraycopy(layout, 0, merged, 0, layout.length);
        merged[layout.length] = ((PickTextureButtonHolder) this.poseEditor).bbspp_cml$getPickTextureButton();

        return UI.column(merged);
    }

    @Redirect(
        method = "resize()V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory$UIPoseFactoryEditor;add([Lmchorse/bbs_mod/ui/framework/elements/IUIElement;)V",
            remap = false
        ),
        require = 0,
        remap = false
    )
    private void bbspp_cml$addNarrowLayout(
        UIPoseKeyframeFactory.UIPoseFactoryEditor editor,
        IUIElement[] layout
    )
    {
        IUIElement[] merged = new IUIElement[layout.length + 1];

        System.arraycopy(layout, 0, merged, 0, layout.length);
        merged[layout.length] = ((PickTextureButtonHolder) editor).bbspp_cml$getPickTextureButton();
        editor.add(merged);
    }
}
