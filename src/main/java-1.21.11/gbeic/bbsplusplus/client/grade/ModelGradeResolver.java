package gbeic.bbsplusplus.client.grade;

import gbeic.bbsplusplus.api.BBSPPFormValues;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.client.glint.GlintTiming;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * 从正在渲染的 {@link ModelInstance} 算出这一帧的 {@link GradeParams}。
 *
 * <p>数据来源：
 * <ul>
 *   <li>调色 / 白化：{@link BBSPPFormValues#getTextureTint(Form)} / {@link BBSPPFormValues#getTextureWhiten(Form)}
 *       —— 和 1.20.x 的 {@code TextureGradeRenderContext} 用同一套表单值（键
 *       {@code texture_tint} / {@code texture_whiten}），面板里的值、老存档里的值都不用迁移。</li>
 *   <li>附魔光效：{@code PoseTransform} → {@code ModelGroup} 的每帧瞬态值（{@link GroupGlintHolder}），
 *       速度/亮度取 {@link GlintSettingsHolder}。</li>
 * </ul>
 *
 * <p>已知取舍（v1）：光效是「整模型级」——取第一个开启光效的 group。精确到逐骨骼需要把光效
 * 改成逐骨拆 draw（见计划文档 WP2 的第二条通道），属 v2。
 */
public final class ModelGradeResolver
{
    /** 首帧解析到光效时打一条，实机可据此区分「数据链没读到」与「绘制没执行」。 */
    private static boolean loggedGlint;

    private ModelGradeResolver()
    {
    }

    public static GradeParams resolve(ModelInstance instance)
    {
        Form form = instance == null ? null : instance.form;

        if (!(form instanceof ModelForm))
        {
            return GradeParams.NONE;
        }

        Color tint = null;
        float whiten = 0F;

        ValueColor tintProperty = BBSPPFormValues.getTextureTint(form);
        ValueFloat whitenProperty = BBSPPFormValues.getTextureWhiten(form);

        if (tintProperty != null)
        {
            tint = tintProperty.get();
        }

        if (whitenProperty != null)
        {
            whiten = whitenProperty.get();
        }

        float tintA = tint == null ? 0F : tint.a;
        boolean tinted = tintA > 0.0001F || whiten > 0.0001F;

        GlintView glint = resolveGlint(instance.model);

        if (glint.active && !loggedGlint)
        {
            loggedGlint = true;

            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.info(
                "[BBS++] 附魔光效参数已解析：color=({}, {}, {}, {}), phase={}, brightness={}",
                glint.r, glint.g, glint.b, glint.a, glint.phase, glint.brightness);
        }

        if (!tinted && !glint.active)
        {
            return GradeParams.NONE;
        }

        return new GradeParams(
            true,
            tint == null ? 1F : tint.r,
            tint == null ? 1F : tint.g,
            tint == null ? 1F : tint.b,
            tintA,
            whiten,
            glint.active,
            glint.r, glint.g, glint.b, glint.a,
            glint.phase,
            glint.brightness
        );
    }

    private static GlintView resolveGlint(Object modelObject)
    {
        if (!(modelObject instanceof Model model))
        {
            return GlintView.NONE;
        }

        for (ModelGroup group : model.getAllGroups())
        {
            GlintView view = resolveGroup(group);

            if (view.active)
            {
                return view;
            }
        }

        return GlintView.NONE;
    }

    private static GlintView resolveGroup(ModelGroup group)
    {
        if (group == null)
        {
            return GlintView.NONE;
        }

        Color color = null;

        if (group instanceof GroupGlintHolder holder && holder.bbspp_cml$getGlint())
        {
            color = holder.bbspp_cml$getGlintColor();
        }
        else if (group.current instanceof GlintHolder poseHolder && poseHolder.bbspp_cml$getGlint())
        {
            /* 兜底：pose 变换自己也实现 GlintHolder（1.20 的回退就是这么写的）。 */
            color = poseHolder.bbspp_cml$getGlintColor();
        }

        if (color == null)
        {
            return GlintView.NONE;
        }

        float speed = GlintSettingsHolder.DEFAULT_SPEED;
        float brightness = GlintSettingsHolder.DEFAULT_BRIGHTNESS;

        if (group instanceof GlintSettingsHolder settings)
        {
            speed = settings.bbspp_cml$getGlintSpeed();
            brightness = settings.bbspp_cml$getGlintBrightness();
        }

        return new GlintView(true, color.r, color.g, color.b, color.a, GlintTiming.phase(speed), brightness);
    }

    /** 内部轻量视图，避免和 {@link GradeParams#active()} 的「任意通道开启」语义混在一起。 */
    private record GlintView(boolean active, float r, float g, float b, float a, float phase, float brightness)
    {
        static final GlintView NONE = new GlintView(false, 0F, 0F, 0F, 0F, 0F, 1F);
    }
}
