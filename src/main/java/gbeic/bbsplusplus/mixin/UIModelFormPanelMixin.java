package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.ModelFormUvSectionHolder;
import gbeic.bbsplusplus.api.PickTextureButtonHolder;
import gbeic.bbsplusplus.client.ui.UVTransformEditor;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.UI;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在模型形态编辑面板中加入贴图 UV 变换控件，并调整顶部纹理按钮布局。
 * <p>
 * 原版面板只有模型、纹理和颜色选择。这里在同一个选项栏追加“纹理变换”小节，
 * 让用户可以直接编辑 BBS++ 注入到 {@link ModelForm} 上的 UV 偏移与缩放属性。
 * </p>
 * <p>
 * 同时把“选择纹理…”与姿态面板的“骨骼纹理”并排放在顶部同一行（各占一半），
 * 使后者不再占据姿态面板底部的位置。
 * </p>
 */
@Mixin(value = UIModelFormPanel.class, remap = true)
public abstract class UIModelFormPanelMixin extends UIFormPanel<ModelForm> implements ModelFormUvSectionHolder
{
    @Shadow public UIButton pickModel;
    @Shadow public UIButton pick;
    @Shadow public UIModelPoseEditor poseEditor;

    @Unique private UISection bbspp$uvSection;
    @Unique private UVTransformEditor bbspp$uvEditor;

    private UIModelFormPanelMixin(UIForm<ModelForm> editor)
    {
        super(editor);
    }

    /**
     * 注入目标：{@code UIModelFormPanel} 构造结束。
     * 注入原因：原版模型面板没有 UV 变换输入项。
     * 修改行为：创建偏移、缩放和统一缩放输入控件，并追加到右侧模型选项栏。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addUvControls(UIForm<ModelForm> editor, CallbackInfo ci)
    {
        this.bbspp$uvEditor = new UVTransformEditor((value) ->
        {
            ValueVector4f uvTransform = gbeic.bbsplusplus.api.BBSPPFormValues.getUvTransform(this.form);

            if (uvTransform != null)
            {
                uvTransform.set(new Vector4f(value));
            }
        });

        this.bbspp$uvSection = new UISection(L10n.lang("bbs.ui.forms.editor.model.uv_transform"));
        this.bbspp$uvSection.fields.add(this.bbspp$uvEditor);
        this.bbspp$uvSection.setExpanded(false);

        this.options.add(this.bbspp$uvSection);

        this.bbspp$splitTextureButton();
    }

    /**
     * 把顶部“选择纹理…”按钮与姿态面板的“骨骼纹理”按钮并排放进同一行，各占一半宽。
     * 骨骼纹理按钮完成后由本面板持有，姿态面板不再在底部显示它。
     */
    @Unique
    private void bbspp$splitTextureButton()
    {
        if (!(this.poseEditor instanceof PickTextureButtonHolder holder))
        {
            return;
        }

        UIButton boneTexture = holder.bbspp_cml$getPickTextureButton();

        if (boneTexture == null)
        {
            return;
        }

        /* 先标记为外部接管，避免姿态面板之后重建布局时把按钮从这一行里收回。 */
        holder.bbspp_cml$setPickTextureButtonDetached(true);

        this.pick.removeFromParent();
        this.options.addAfter(this.pickModel, UI.row(this.pick, boneTexture));
    }

    /**
     * 注入目标：{@code UIModelFormPanel#startEdit} 结束。
     * 注入原因：切换表单后，输入框需要同步当前表单已保存或关键帧运行时采样出的值。
     * 修改行为：把当前模型形态的 UV 参数刷新到四个输入控件。
     */
    @Inject(method = "startEdit", at = @At("TAIL"))
    private void bbspp$syncUvControls(ModelForm form, CallbackInfo ci)
    {
        ValueVector4f uvTransform = gbeic.bbsplusplus.api.BBSPPFormValues.getUvTransform(form);
        Vector4f value = uvTransform == null ? null : uvTransform.get();

        if (value == null)
        {
            this.bbspp$setUvExpanded(null);

            return;
        }

        this.bbspp$uvEditor.setValue(value);
        this.bbspp$setUvExpanded(value);
    }

    /**
     * 选中肢体时由 {@code UIPoseEditorMixin} 沿 UI 父链调用，保证模型级 UV 栏也能
     * 随“五栏自动展开”刷新。这里只改折叠状态，不重复写入编辑器控件。
     */
    @Override
    public void bbspp$refreshUvSectionExpansion()
    {
        ValueVector4f uvTransform = gbeic.bbsplusplus.api.BBSPPFormValues.getUvTransform(this.form);
        Vector4f value = uvTransform == null ? null : uvTransform.get();

        this.bbspp$setUvExpanded(value);
    }

    @Unique
    private void bbspp$setUvExpanded(Vector4f value)
    {
        if (this.bbspp$uvSection != null)
        {
            this.bbspp$uvSection.setExpanded(value != null && !this.bbspp$isDefaultUv(value));
        }
    }

    /**
     * UV 是模型级属性而非逐骨骼属性，所以它只能按“当前模型是否配置过 UV”来决定默认展开，
     * 不能像材质/PBR/描边/附魔光效那样随选中的骨骼变化。默认值是 (0, 0, 1, 1)。
     */
    @Unique
    private boolean bbspp$isDefaultUv(Vector4f value)
    {
        return Math.abs(value.x) < 1.0E-6F
            && Math.abs(value.y) < 1.0E-6F
            && Math.abs(value.z - 1.0F) < 1.0E-6F
            && Math.abs(value.w - 1.0F) < 1.0E-6F;
    }
}
