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

/** 动画悬停预览：在独立 form 副本上循环播放指定动画，并按绑定姿态包围盒自动填满视口。 */
public class UIAnimationPreview extends UIModelRenderer
{
    private ModelForm source;
    private ModelForm previewForm;
    private ModelFormRenderer renderer;
    private ModelBindingGeometry geometry;
    private String currentAnimation;

    public UIAnimationPreview()
    {
        this.grid = false;
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
            .set(FormRenderType.PREVIEW, this.entity, context.batcher.getContext().getMatrices(),
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
