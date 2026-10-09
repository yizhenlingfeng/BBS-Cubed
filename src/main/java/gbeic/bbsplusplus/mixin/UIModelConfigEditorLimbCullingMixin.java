package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.LimbCullingConfigHolder;
import gbeic.bbsplusplus.api.LimbCullingTabAccessor;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.model_editor.UIModelConfigEditor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

@Mixin(value = UIModelConfigEditor.class, remap = true)
public abstract class UIModelConfigEditorLimbCullingMixin implements LimbCullingTabAccessor
{
    @Shadow public UITabStrip tabs;
    @Shadow private UIScrollView[] pages;
    @Shadow private ModelConfig data;

    @Shadow protected abstract UIElement body();
    @Shadow protected abstract ModelInstance instance();

    @Unique private UIScrollView bbspp$limbCullingPage;
    @Unique private UIBoneTreeList bbspp$limbCullingList;
    @Unique private UISearchList<String> bbspp$limbCullingSearch;
    @Unique private UIElement bbspp$limbCullingPanel;
    @Unique private String bbspp$limbCullingCurrent;
    @Unique private boolean bbspp$limbCullingActive;

    @Inject(method = "createTabs", at = @At("TAIL"))
    private void bbspp$addLimbCullingTab(CallbackInfo ci)
    {
        UIElement self = (UIElement) (Object) this;
        this.tabs.addTab(new UIIcon(Icons.BLOCK, null).wh(20, 20));

        this.bbspp$limbCullingPage = UI.scrollView(3, 6, new UIElement[0]);
        this.bbspp$limbCullingPage.relative(self);
        this.bbspp$limbCullingPage.x(0).y(20).w(1.0F).h(1.0F, -20);
        this.bbspp$limbCullingPage.setVisible(false);
        self.add(this.bbspp$limbCullingPage);
    }

    @Redirect(method = "createTabs", at = @At(value = "INVOKE",
        target = "Lmchorse/bbs_mod/ui/framework/elements/utils/UITabStrip;hoverLabels(Ljava/util/function/IntFunction;)Lmchorse/bbs_mod/ui/framework/elements/utils/UITabStrip;"))
    private UITabStrip bbspp$wrapHoverLabels(UITabStrip strip, IntFunction<IKey> original)
    {
        return strip.hoverLabels(i -> i == 8
            ? L10n.lang("bbs.ui.model_editor.limb_culling.tab")
            : original.apply(i));
    }

    @Redirect(method = "createTabs", at = @At(value = "INVOKE",
        target = "Lmchorse/bbs_mod/ui/framework/elements/utils/UITabStrip;active(Ljava/util/function/IntSupplier;)Lmchorse/bbs_mod/ui/framework/elements/utils/UITabStrip;"))
    private UITabStrip bbspp$wrapActive(UITabStrip strip, IntSupplier original)
    {
        return strip.active(() -> this.bbspp$limbCullingActive ? 8 : original.getAsInt());
    }

    @Inject(method = "createPages", at = @At("TAIL"))
    private void bbspp$fillLimbCullingPage(CallbackInfo ci)
    {
        this.bbspp$limbCullingList = new UIBoneTreeList(this::bbspp$onBoneSelected);
        this.bbspp$limbCullingSearch = new UISearchList<>(this.bbspp$limbCullingList);
        this.bbspp$limbCullingSearch.label(UIKeys.GENERAL_SEARCH).h(104).expand();
        this.bbspp$limbCullingPanel = this.body();
        this.bbspp$limbCullingPage.add(this.bbspp$limbCullingSearch, this.bbspp$limbCullingPanel);
    }

    @Inject(method = "showPage", at = @At("TAIL"))
    private void bbspp$hideLimbCullingOnOriginalTab(CallbackInfo ci)
    {
        this.bbspp$limbCullingActive = false;
        if (this.bbspp$limbCullingPage != null)
        {
            this.bbspp$limbCullingPage.setVisible(false);
        }
    }

    @Inject(method = "selectBone", at = @At("HEAD"), cancellable = true)
    private void bbspp$selectBoneForLimbCulling(String bone, CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.bbspp$limbCullingActive || bone == null) return;
        this.bbspp$limbCullingSearch.filter("", true);
        this.bbspp$limbCullingList.setCurrent(bone);
        this.bbspp$limbCullingCurrent = bone;
        this.bbspp$fillLimbCullingPanel();
        cir.setReturnValue(true);
    }

    @Override
    public void bbspp$showLimbCullingTab()
    {
        this.bbspp$limbCullingActive = true;
        for (UIScrollView page : this.pages)
        {
            if (page != null) page.setVisible(false);
        }
        this.bbspp$limbCullingPage.setVisible(true);
        ((UIElement) (Object) this).resize();

        ModelInstance instance = this.instance();
        if (instance != null && this.bbspp$limbCullingList != null)
        {
            IModel model = instance.getModel();
            if (model != null)
            {
                this.bbspp$limbCullingList.fillBones(model, Collections.emptyList());
            }
            if (this.bbspp$limbCullingCurrent != null)
            {
                this.bbspp$limbCullingList.setCurrent(this.bbspp$limbCullingCurrent);
            }
        }
        this.bbspp$fillLimbCullingPanel();
    }

    @Unique
    private void bbspp$onBoneSelected(List<String> selected)
    {
        this.bbspp$limbCullingCurrent = (selected == null || selected.isEmpty()) ? null : selected.get(0);
        this.bbspp$fillLimbCullingPanel();
    }

    @Unique
    private void bbspp$fillLimbCullingPanel()
    {
        this.bbspp$limbCullingPanel.removeAll();
        ModelConfig config = this.data;
        String bone = this.bbspp$limbCullingCurrent;
        if (!(config instanceof LimbCullingConfigHolder holder)) return;

        boolean culled = bone != null && holder.bbspp$getLimbCullingBones().contains(bone);
        ValueStringKeys value = holder.bbspp$getLimbCullingBonesValue();
        IKey label = L10n.lang("bbs.ui.model_editor.limb_culling.toggle");

        this.bbspp$limbCullingPanel.add(new UIToggle(label, culled, t ->
        {
            if (bone == null || value == null) return;
            boolean enabled = t.getValue();
            BaseValue.edit(value, (ValueStringKeys v) ->
            {
                Set<String> set = v.get();
                if (set != null)
                {
                    if (enabled) set.add(bone);
                    else set.remove(bone);
                }
            });
        }));
        UIUtils.setEnabledDeep(this.bbspp$limbCullingPanel, bone != null);
    }
}
