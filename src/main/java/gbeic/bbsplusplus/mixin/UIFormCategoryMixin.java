package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.ui.morphing.BlockbenchUIKeys;
import gbeic.bbsplusplus.ui.morphing.DisabledContextAction;
import gbeic.bbsplusplus.utils.BlockbenchLauncher;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 伪装页模型格子的右键菜单追加「用 Blockbench 编辑」。
 *
 * <p>判定规则（自上而下）：</p>
 * <ul>
 *   <li>选中的不是 ModelForm → 不加该项</li>
 *   <li>模型目录在磁盘上不存在（内置模型，来自 jar/资源包）→ 不加该项</li>
 *   <li>Blockbench.exe 路径无效，或模型目录下没有 .geo.json/.bbmodel → 加灰色不可点项</li>
 *   <li>其余 → 加可点项，按设置优先 .bbmodel 或 .geo.json 打开</li>
 * </ul>
 *
 * <p>context() 为追加而非覆盖（同 UIFilmPreviewMixin 的做法），不影响原有菜单项。</p>
 */
@Mixin(value = UIFormCategory.class, remap = false)
public abstract class UIFormCategoryMixin
{
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp$addEditInBlockbench(FormCategory category, UIFormList list, CallbackInfo ci)
    {
        UIFormCategory self = (UIFormCategory) (Object) this;

        /* 注意：模型是右键时才确定的，必须在 lambda 内部（每次开菜单时）读取，
         * 不能在构造时捕获。
         *
         * 必须用 {@code getContextForm()} 而不是 {@code this.selected}：
         * 2.6 下右键某个格子时原版 UIFormCategory.subMouseClicked 只把被点中的
         * 模型记进私有的 contextForm，并不会选中它；getContextForm() 返回
         * "右键点到的模型，没有则回退到已选中项"。此前直接读 selected，
         * 导致分类里没选中任何模型时右键菜单找不到该项。 */
        self.context((menu) ->
        {
            Form selected = self.getContextForm();

            if (!(selected instanceof ModelForm modelForm))
            {
                return;
            }

            String modelId = modelForm.model.get();

            /* 内置模型：磁盘上没有目录，直接不显示该按钮 */
            if (!BlockbenchLauncher.isUserModel(modelId))
            {
                return;
            }

            boolean exeOk = BlockbenchLauncher.isExeValid();
            boolean hasTarget = BlockbenchLauncher.resolveTargetFile(modelId) != null;
            boolean enabled = exeOk && hasTarget;

            if (enabled)
            {
                menu.action(Icons.EDIT, BlockbenchUIKeys.EDIT_IN_BLOCKBENCH, () ->
                {
                    if (!BlockbenchLauncher.openModel(modelForm))
                    {
                        self.getContext().notifyError(BlockbenchUIKeys.LAUNCH_FAILED);
                    }
                });
            }
            else
            {
                /* 路径未配置/无效，或模型不是 .geo.json → 置灰可见但不可点 */
                menu.action(new DisabledContextAction(Icons.EDIT, BlockbenchUIKeys.EDIT_IN_BLOCKBENCH));
            }
        });
    }
}
