package gbeic.bbsplusplus.client.ui.animations;

import gbeic.bbsplusplus.client.texture.ModelBindingGeometry;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * 动画悬停预览：在独立 form 副本上循环播放指定动画，并按绑定姿态包围盒自动填满视口。
 *
 * <p>1.21.11 的两阶段 GUI 决定了这个元素不能只靠 {@code render(context)} 显示：那个回调只会把
 * 上一帧在世界阶段烘好的离屏贴图贴到界面上（{@code UIModelRenderer#renderModel}）。真正画模型的一步
 * （{@code renderModelToTexture}）必须发生在世界阶段、两阶段录制窗口之外，而 {@code UIScreen#renderInWorld}
 * 只会遍历 {@code menu.getRoot().getChildren(UIModelRenderer.class)} —— tooltip 里的这个实例不是根元素的
 * 子元素，永远轮不到，于是小窗一直是空的。
 *
 * <p>所以这里自带一个「正在悬停」的注册表，由 {@code UIScreenPreviewMixin121} 在世界阶段末尾统一驱动。
 * 注册表只保存当前确实在显示的那一个（tooltip 每帧 {@code clear()} + {@code prepare()}）。
 */
public class UIAnimationPreview extends UIModelRenderer
{
    /** 当前正在悬停显示、需要在世界阶段烘贴图的预览实例。 */
    private static final java.util.List<UIAnimationPreview> ACTIVE = new java.util.ArrayList<>();

    private ModelForm source;
    private ModelForm previewForm;
    private ModelFormRenderer renderer;
    private ModelBindingGeometry geometry;
    private String currentAnimation;

    public UIAnimationPreview()
    {
        this.grid = false;
    }

    /** 世界阶段驱动：给所有正在悬停的预览各烘一帧离屏贴图。 */
    public static void renderActivePreviews(UIContext context)
    {
        if (ACTIVE.isEmpty() || context == null)
        {
            return;
        }

        for (UIAnimationPreview preview : new java.util.ArrayList<>(ACTIVE))
        {
            if (preview.previewForm == null || preview.renderer == null)
            {
                continue;
            }

            if (preview.area.w <= 0 || preview.area.h <= 0)
            {
                continue;
            }

            try
            {
                preview.renderModelToTexture(context);
            }
            catch (Exception ignored)
            {
                /* 单个预览失败不能连累世界渲染循环或其它预览。 */
            }
        }
    }

    /**
     * tooltip 收起时调用：停止在世界阶段烘贴图。
     *
     * <p>注意不能清 {@code currentAnimation}：tooltip 每帧都会先 {@code clear()} 再 {@code prepare()}，
     * 清了会让 {@code setAnimation} 每帧都判定「动画变了」而 {@code resetAnimator()}，预览就永远停在第 0 帧。
     */
    public void deactivate()
    {
        ACTIVE.remove(this);
    }

    public void setAnimation(ModelForm source, String animation)
    {
        if (this.previewForm == null || this.source != source)
        {
            this.source = source;
            this.rebuildForm();
        }

        if (!Objects.equals(this.currentAnimation, animation))
        {
            this.currentAnimation = animation;
            this.configureIdle(animation);
        }

        if (this.previewForm != null && this.renderer != null && !ACTIVE.contains(this))
        {
            ACTIVE.add(this);
        }
    }

    private void rebuildForm()
    {
        this.previewForm = new ModelForm();
        this.previewForm.model.set(this.source.model.get());
        this.previewForm.texture.set(this.source.texture.get());
        this.previewForm.color.set(this.source.color.get());
        this.previewForm.actions.set(new ActionsConfig());

        this.renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(this.previewForm);
        this.geometry = ModelBindingGeometry.create(this.previewForm);
        this.fitCamera();
    }

    private void configureIdle(String animation)
    {
        ActionConfig idle = new ActionConfig("idle");
        idle.name = animation;
        idle.loop = true;
        idle.fade = 0;

        ActionsConfig actions = new ActionsConfig();
        actions.actions.put("idle", idle);
        this.previewForm.actions.set(actions);

        this.renderer.resetAnimator();
        this.renderer.ensureAnimator(0F);
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.renderer != null && this.renderer.getAnimator() != null)
        {
            this.renderer.getAnimator().update(this.entity);
        }
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.previewForm == null)
        {
            return;
        }

        this.renderer.ensureAnimator(context.getTransition());

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, this.createCameraStack(),
                LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        FormUtilsClient.render(this.previewForm, formContext);
    }

    private void fitCamera()
    {
        if (this.geometry == null)
        {
            return;
        }

        Vector3f min = this.geometry.min();
        Vector3f max = this.geometry.max();
        Vector3f center = new Vector3f(min).add(max).mul(0.5F);
        float span = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));

        this.setPosition(-center.x, center.y, -center.z);
        this.setDistance(Math.max(1.0F, span * 1.3F));
        this.setRotation(25F, -10F);
    }
}
