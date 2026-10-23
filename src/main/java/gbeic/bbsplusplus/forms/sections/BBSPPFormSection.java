package gbeic.bbsplusplus.forms.sections;


import gbeic.bbsplusplus.client.ClientFeatures;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.forms.FluidForm;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.sections.FormSection;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Arrays;
import java.util.List;

/**
 * BBS++ 专属的表单拾取器分区。
 *
 * <p>原先由 {@code ExtraFormSectionMixin} 把流体 / AAA 粒子 / 物品喷射塞进
 * BBS 自带"杂项(extra)"分类的私有列表里；BBS 2.7 提供了官方入口
 * {@code RegisterFormSectionsEvent}（每次资源重载重建调色板时都会重新询问
 * factory），因此改由 {@code BBSPlusPlusClientAddon} 订阅事件注册本分区，
 * 消掉该 Mixin。</p>
 *
 * <p>与旧方案的行为差异：表单不再出现在"杂项"里，而是集中在新的"BBS++"
 * 顶层分区。分类可见性 ID 为 {@code bbspp}，可在分类可见性设置里单独开关。</p>
 */
public class BBSPPFormSection extends FormSection
{
    private FormCategory bbspp;
    private List<FormCategory> categories;

    public BBSPPFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        this.bbspp = new FormCategory(IKey.constant("BBS++"), this.parent.preferences.visible("bbspp")).icon(Icons.MORE);

        this.bbspp.addForm(new FluidForm());

        /* AAA 粒子表单依赖 aaa_particles 前置的 Effekseer 运行时，未装前置时跳过 */
        if (FabricLoader.getInstance().isModLoaded("aaa_particles"))
        {
            this.bbspp.addForm(new AAAParticleForm());
        }

        this.bbspp.addForm(new ItemSprayForm());

        this.categories = Arrays.asList(this.bbspp);
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return this.categories;
    }
}
