package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.forms.editors.panels.UIBoneListFormPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link UIBoneListFormPanel} 的预设分组键 presetGroup。
 * <p>
 * 2.6 中 presetGroup 上移到了父类（protected String）。子类 Mixin 直接
 * {@code @Shadow} 该继承字段在 dev 无 refmap 环境下无法定位，故改为在字段声明类
 * 上用 Accessor 暴露，再由各子类面板 Mixin 强转读取。
 * </p>
 */
@Mixin(value = UIBoneListFormPanel.class, remap = false)
public interface UIBoneListFormPanelAccessor
{
    @Accessor(value = "presetGroup", remap = false)
    String bbspp$getPresetGroup();
}
