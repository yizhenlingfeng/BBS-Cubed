package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;

/**
 * 由 {@code UIPoseEditorMixin} 实现的 duck 接口，把外挂的"骨骼纹理"按钮
 * 暴露给其它 mixin —— {@code UIPoseKeyframeFactoryMixin} 需要在
 * {@code resize()} 重排布局（removeAll 后重加）时把按钮重新塞回
 * pose 关键帧属性面板。
 */
public interface PickTextureButtonHolder
{
    UIButton bbspp_cml$getPickTextureButton();

    /** 标记骨骼纹理按钮已由外部宿主接管，姿态面板重建布局时不再收回。 */
    void bbspp_cml$setPickTextureButtonDetached(boolean detached);
}
