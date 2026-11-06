package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.settings.ModSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.utils.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 1.21.11 版「Shift 直接选择父级」（设置项 {@code direct_parent_picking}）。
 *
 * <p>为什么单独再写一份：主线的 {@code mixin/UIFormEditorMixin.java} 里同时有
 * {@code openFormList} 的新伪装界面注入（依赖 {@code UIBBSPPFormEditorList}），而后者在
 * 1.21.11 属于未移植的 morphing UI 系统、被 {@code build.gradle} 排除。整个
 * {@code UIFormEditorMixin} 因此被连带排除，连与它毫无关系的「直接选骨骼」注入一起消失。
 * 本类只搬那一段注入，不引用任何被排除的类，放在 {@code java-1.21.11} 专属源码目录并登记到
 * {@code bbsplusplus-12111.mixins.json}，因此 1.20.x 构建完全不受影响（那边仍用原 mixin）。
 *
 * <p>BBS 原生行为：模型方块/表单编辑器预览里点击模型部位，普通左键直接选中的骨骼，
 * 按住 Shift 则弹出层级菜单（{@code UIReplaysEditorUtils.offerHierarchy}）需要再点一次。
 * 开启本设置后，Shift 点击改为直接选中第一个未被禁用的父级骨骼。
 *
 * <p>1.21.11 的 {@code ModelInstance.model} 类型是 {@code IModel}（继承
 * {@code IBoneHierarchy#getHierarchyGroups}），{@code UIFormEditor#pickFormFromRenderer}
 * 与 {@code pickFormBone} 的签名都与 1.20.x 一致，所以不需要改写逻辑。
 */
@Mixin(value = UIFormEditor.class, remap = false)
public abstract class UIFormEditorPickingMixin121
{
    private static boolean logged;

    @Shadow
    protected abstract void pickFormBone(Form form, String bone);

    @Inject(method = "pickFormFromRenderer", at = @At("HEAD"), cancellable = true, remap = true, require = 0)
    private void bbspp$directParentPick(Pair<Form, String> pair, CallbackInfo ci)
    {
        if (!Window.isShiftPressed() || Window.isCtrlPressed()
            || ModSettings.directParentPicking == null || !ModSettings.directParentPicking.get())
        {
            return;
        }

        if (pair == null || !(pair.a instanceof ModelForm modelForm) || pair.b == null)
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null || model.model == null)
        {
            return;
        }

        Collection<String> hierarchyCol = model.model.getHierarchyGroups(pair.b);
        List<String> hierarchy = hierarchyCol instanceof List<?> list
            ? castList(list)
            : new ArrayList<>(hierarchyCol);

        /* 从索引 1 开始跳过自身，选第一个未被禁用的祖先骨骼。 */
        for (int i = 1; i < hierarchy.size(); i++)
        {
            String parentBone = hierarchy.get(i);

            if (!model.getDisabledBones().contains(parentBone))
            {
                this.pickFormBone(pair.a, parentBone);
                ci.cancel();

                if (!logged)
                {
                    logged = true;
                    BBSPlusPlusMod.LOGGER.info("[BBS++] 1.21.11：Shift 直接选择父级已生效（{} → {}）", pair.b, parentBone);
                }

                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(List<?> list)
    {
        return (List<String>) list;
    }
}
