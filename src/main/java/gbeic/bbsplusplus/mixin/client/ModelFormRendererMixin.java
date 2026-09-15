package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.ActionsOverlayProvider;
import gbeic.bbsplusplus.api.PivotHolder;
import gbeic.bbsplusplus.api.TextureGradeHolder;
import gbeic.bbsplusplus.cubic.animation.AdditiveAnimator;
import gbeic.bbsplusplus.cubic.animation.AdditiveLayerContext;
import gbeic.bbsplusplus.cubic.animation.AdditiveProceduralAnimator;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.utils.MolangVariableScopes;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.values.ValueActionsConfig;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 在 {@link ModelFormRenderer} 私有方法 {@code applyPose(Pose target, Pose overlay)}
 * 末尾传播 CML 的两个扩展属性（FS 版该方法只合并平移/缩放/旋转）：
 * <ul>
 *   <li>骨骼纹理 —— overlay 带纹理则覆盖（对齐 CML 的
 *       {@code poseTransform.texture = LinkUtils.copy(value.texture)}）；</li>
 *   <li>中心点 —— 对齐 CML：fix 非零时 {@code pivot.lerp(value.pivot, fix)}，
 *       否则 {@code pivot.add(value.pivot)}。</li>
 * </ul>
 */
@Mixin(value = ModelFormRenderer.class, remap = false)
public class ModelFormRendererMixin
{
    @Unique
    private final List<IAnimator> bbspp_cml$actionsOverlayAnimators = new ArrayList<>();

    @Unique
    private final List<ActionsConfig> bbspp_cml$lastActionsOverlayConfigs = new ArrayList<>();

    @Unique
    private ModelInstance bbspp_cml$actionsOverlayModel;

    @Unique
    private boolean bbspp_cml$actionsOverlayProcedural;

    @Inject(
        method = "applyPose(Lmchorse/bbs_mod/utils/pose/Pose;Lmchorse/bbs_mod/utils/pose/Pose;)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$applyOverlayExtras(Pose targetPose, Pose pose, CallbackInfo ci)
    {
        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform value = entry.getValue();
            PoseTransform target = targetPose.get(entry.getKey());
            Link texture = ((BoneTextureHolder) value).bbspp_cml$getTexture();

            if (texture != null)
            {
                ((BoneTextureHolder) target).bbspp_cml$setTexture(LinkUtils.copy(texture));
            }

            TextureGradeHolder valueGrade = (TextureGradeHolder) value;
            TextureGradeHolder targetGrade = (TextureGradeHolder) target;

            if (valueGrade.bbspp_cml$getTextureTint().a > 0F)
            {
                targetGrade.bbspp_cml$setTextureTint(valueGrade.bbspp_cml$getTextureTint());
            }

            targetGrade.bbspp_cml$setTextureWhiten(MathUtils.clamp(
                targetGrade.bbspp_cml$getTextureWhiten() + valueGrade.bbspp_cml$getTextureWhiten(), 0F, 1F));

            Vector3f valuePivot = ((PivotHolder) value).bbspp_cml$getPivot();
            Vector3f targetPivot = ((PivotHolder) target).bbspp_cml$getPivot();

            if (!Operation.equals(value.fix, 0))
            {
                targetPivot.lerp(valuePivot, value.fix);
            }
            else
            {
                targetPivot.add(valuePivot);
            }
        }
    }

    /**
     * 在 {@code evaluateChannels} 中、基础动作 {@code animator.applyActions(...)}
     * 完成后、姿势提交给模型（{@code model.model.applyPose(...)}）之前，叠加附加动作层。
     *
     * <p>注入方式说明：原实现用 {@code @Redirect} 拦截 {@code applyActions} 调用，
     * 但 bbs-and-ysm (bbs-more-molang) 的 {@code ModelFormRendererApplyGuardMixin}
     * 已用 {@code @Redirect} 拦截同一调用点（try/catch 守卫）。{@code @Redirect}
     * 只能有一个且会先被应用、把该 INVOKE 节点替换掉，导致本模组再按同一调用点
     * 注入（无论 {@code @Redirect} 还是 {@code @Inject}）都找不到节点并触发
     * Critical injection failure（启动即崩溃）。故把注入点移到后续未被其它模组
     * 替换的 {@code applyPose} 调用点（{@code Shift.BEFORE}）：{@code evaluateChannels}
     * 为直线流程（resetPose → applyActions → applyPose），该位置恰好等价于
     * “基础动作已跑完、姿势尚未提交”，语义与原先一致且不再冲突。</p>
     */
    @Inject(
        method = "evaluateChannels",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/IModel;applyPose(Lmchorse/bbs_mod/utils/pose/Pose;)V",
            shift = At.Shift.BEFORE
        ),
        remap = false
    )
    private void bbspp_cml$applyActionsWithOverlays(IEntity entity, ModelInstance model, float transition, CallbackInfo ci)
    {
        ModelFormRenderer renderer = (ModelFormRenderer) (Object) this;
        IAnimator animator = renderer.getAnimator();

        this.bbspp_cml$ensureActionsOverlayAnimators(animator);

        if (this.bbspp_cml$actionsOverlayAnimators.isEmpty())
        {
            return;
        }

        MolangVariableScopes.beforeApply(model);

        try
        {
            /* 附加层用相加叠加应用(见 AdditiveLayerContext 与 CubicModelAnimatorMixin/
             * BOBJModelAnimatorMixin):每层的位移/旋转/缩放增量累加到基础动作之上并彼此
             * 叠加,而不是越靠下越覆盖。基础动作 applyActions 已由调用点之前的原调用跑完。 */
            AdditiveLayerContext.begin();

            for (IAnimator overlay : this.bbspp_cml$actionsOverlayAnimators)
            {
                overlay.applyActions(entity, model, transition);
            }
        }
        finally
        {
            AdditiveLayerContext.end();
            MolangVariableScopes.afterApply(model);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void bbspp_cml$updateActionsOverlayAnimators(IEntity entity, CallbackInfo ci)
    {
        ModelFormRenderer renderer = (ModelFormRenderer) (Object) this;

        this.bbspp_cml$ensureActionsOverlayAnimators(renderer.getAnimator());

        for (IAnimator animator : this.bbspp_cml$actionsOverlayAnimators)
        {
            animator.update(entity);
        }
    }

    @Inject(method = "resetAnimator", at = @At("RETURN"), remap = false)
    private void bbspp_cml$resetActionsOverlayAnimators(CallbackInfo ci)
    {
        this.bbspp_cml$clearActionsOverlayAnimators();
    }

    @Unique
    private void bbspp_cml$ensureActionsOverlayAnimators(IAnimator primaryAnimator)
    {
        if (!CMLSettings.isSnowActionsEnabled())
        {
            this.bbspp_cml$clearActionsOverlayAnimators();

            return;
        }

        ModelFormRenderer renderer = (ModelFormRenderer) (Object) this;
        ModelInstance model = renderer.getModel();
        ActionsOverlayProvider provider = (ActionsOverlayProvider) renderer.getForm();
        List<ValueActionsConfig> values = new ArrayList<>();

        if (provider.bbspp_cml$getActionsOverlay() != null)
        {
            values.add(provider.bbspp_cml$getActionsOverlay());
        }

        if (provider.bbspp_cml$getAdditionalActionsOverlays() != null)
        {
            values.addAll(provider.bbspp_cml$getAdditionalActionsOverlays());
        }

        if (model == null)
        {
            this.bbspp_cml$clearActionsOverlayAnimators();

            return;
        }

        boolean procedural = primaryAnimator instanceof ProceduralAnimator;

        if (this.bbspp_cml$actionsOverlayModel != model
            || this.bbspp_cml$actionsOverlayProcedural != procedural
            || this.bbspp_cml$actionsOverlayAnimators.size() != values.size())
        {
            this.bbspp_cml$clearActionsOverlayAnimators();
            this.bbspp_cml$actionsOverlayModel = model;
            this.bbspp_cml$actionsOverlayProcedural = procedural;

            for (ValueActionsConfig value : values)
            {
                ActionsConfig current = value.get();
                IAnimator animator = this.bbspp_cml$createActionsOverlayAnimator(procedural);

                animator.setup(model, current, false);
                this.bbspp_cml$actionsOverlayAnimators.add(animator);
                this.bbspp_cml$lastActionsOverlayConfigs.add(this.bbspp_cml$copyActions(current));
            }

            return;
        }

        for (int i = 0; i < values.size(); i++)
        {
            ActionsConfig current = values.get(i).get();

            if (!Objects.equals(current, this.bbspp_cml$lastActionsOverlayConfigs.get(i)))
            {
                /* Animator.setup(..., true) leaves Animator.active pointing at
                 * the removed playback. Replacing the animator clears it. */
                IAnimator replacement = this.bbspp_cml$createActionsOverlayAnimator(procedural);

                replacement.setup(model, current, false);
                this.bbspp_cml$actionsOverlayAnimators.set(i, replacement);
                this.bbspp_cml$lastActionsOverlayConfigs.set(i, this.bbspp_cml$copyActions(current));
            }
        }
    }

    @Unique
    private IAnimator bbspp_cml$createActionsOverlayAnimator(boolean procedural)
    {
        return procedural ? new AdditiveProceduralAnimator() : new AdditiveAnimator();
    }

    @Unique
    private ActionsConfig bbspp_cml$copyActions(ActionsConfig source)
    {
        ActionsConfig copy = new ActionsConfig();

        copy.copy(source);

        return copy;
    }

    @Unique
    private void bbspp_cml$clearActionsOverlayAnimators()
    {
        this.bbspp_cml$actionsOverlayAnimators.clear();
        this.bbspp_cml$lastActionsOverlayConfigs.clear();
        this.bbspp_cml$actionsOverlayModel = null;
        this.bbspp_cml$actionsOverlayProcedural = false;
    }

}
