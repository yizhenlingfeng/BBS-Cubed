package gbeic.bbsplusplus.client.grade;

import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.GroupPbrHolder;
import gbeic.bbsplusplus.api.GroupTextureHolder;
import gbeic.bbsplusplus.api.LimbCullingConfigHolder;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormPbr;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;

/**
 * 逐骨骼提交的「一组该用什么参数」解析器（施工表 B2-02/03/04/06 的公共决策点）。
 *
 * <p>1.20.x 里这三件事分散在 {@code CubicVAORendererMixin} 的三个 {@code @Redirect} 上，
 * 因为当时一次模型 = 一次 raw-GL 绘制，可以在绘制中途换 shader uniform、换绑定贴图、
 * 开关 GL 剔除。1.21.11 把这些状态固化在「一次提交所用的 {@code RenderLayer}」上，
 * 所以同一套判断被搬到这里：由逐个组的提交方在 {@code submit} 之前问一次，
 * 拿到该组的贴图、剔除开关与分级参数。
 * </p>
 *
 * <p>所有方法都是纯查询，失败时返回调用方传进来的默认值 —— 逐骨骼特性永远不能
 * 让一次模型绘制整个掉帧。</p>
 */
public final class BoneGroupResolve121
{
    private BoneGroupResolve121()
    {
    }

    /**
     * 该组最终要绑定的贴图（B2-02 逐骨骼 PBR + B2-03 骨骼贴图覆盖）。
     *
     * <p>顺序与 1.20.x 一致：先用「骨骼贴图覆盖」替换材质链接，再让 {@link FormPbr}
     * 按材质级滑条产出 albedo 变体，最后在骨骼自己带 PBR 值时用骨骼五值再产一个变体
     * 覆盖上去。</p>
     *
     * @param form     当前模型表单（可为 null）
     * @param material 当前材质通道的材质名（可为空串）
     * @param link     当前材质通道的原始链接（可为 null）
     * @param base     已经绑定好的基线贴图，所有失败的兜底值
     */
    public static Texture resolveTexture(ModelForm form, String material, Link link, Texture base, ModelGroup group)
    {
        if (group == null)
        {
            return base;
        }

        Link effective = link;

        if (group instanceof GroupTextureHolder holder)
        {
            Link override = holder.bbspp_cml$getTextureOverride();

            if (override != null)
            {
                effective = override;
            }
        }

        Texture texture = base;

        if (effective != null && BBSModClient.getTextures() != null)
        {
            Texture resolved = BBSModClient.getTextures().getTexture(effective);

            /* 材质级 PBR 会读写 link/贴图对；只有解析出真实贴图时才走它。 */
            if (resolved != null && resolved != BBSModClient.getTextures().getError())
            {
                texture = FormPbr.resolveAlbedo(form, material, effective, resolved);
            }
        }

        return resolveBonePbr(form, material, effective, texture, group);
    }

    /**
     * 骨骼级 PBR：任一值 &gt; 0 时，用五值构造独立的 albedo variant key 并 track，
     * 让 Iris 按骨骼值生成 specular/normal 贴图。
     *
     * <p>优先读 {@link GroupPbrHolder}（{@code Model.applyPose} 每帧传播），
     * 未激活时回退到 {@link BonePbrHolder}（动画器直接写 {@code group.current} 的情形，
     * 与 1.20.x 的 glint 回退同构）。</p>
     */
    private static Texture resolveBonePbr(ModelForm form, String material, Link link, Texture texture, ModelGroup group)
    {
        if (form == null || link == null || texture == null || BBSModClient.getTextures() == null)
        {
            return texture;
        }

        if (!BBSRendering.isIrisShadersEnabled())
        {
            return texture;
        }

        float[] values = pbrValues(group);

        if (values == null)
        {
            return texture;
        }

        String materialKey = material == null ? "" : material;
        String boneKey = "pbr_bone:" + materialKey + ":" + System.identityHashCode(form)
            + ":" + Math.round(values[0] * 255F)
            + ":" + Math.round(values[1] * 255F)
            + ":" + Math.round(values[2] * 255F)
            + ":" + Math.round(values[3] * 255F)
            + ":" + Math.round(values[4] * 255F);

        Texture variant = BBSModClient.getTextures().getVariant(link, boneKey);

        if (variant == null || variant == BBSModClient.getTextures().getError())
        {
            return texture;
        }

        BBSRendering.trackPbrVariant(variant, link, values[0], values[1], values[2], values[3], values[4]);

        return variant;
    }

    /**
     * 该组的五个 PBR 值，全 0 或读不到时返回 {@code null}。
     */
    private static float[] pbrValues(ModelGroup group)
    {
        float smooth = 0F;
        float metal = 0F;
        float sss = 0F;
        float emission = 0F;
        float relief = 0F;

        if (group instanceof GroupPbrHolder pbr)
        {
            smooth = pbr.bbspp_cml$getSmoothness();
            metal = pbr.bbspp_cml$getMetallic();
            sss = pbr.bbspp_cml$getSss();
            emission = pbr.bbspp_cml$getEmission();
            relief = pbr.bbspp_cml$getRelief();
        }

        if (smooth <= 0F && metal <= 0F && sss <= 0F && emission <= 0F && relief <= 0F
            && group.current instanceof BonePbrHolder render)
        {
            smooth = render.bbspp_cml$getSmoothness();
            metal = render.bbspp_cml$getMetallic();
            sss = render.bbspp_cml$getSss();
            emission = render.bbspp_cml$getEmission();
            relief = render.bbspp_cml$getRelief();
        }

        if (smooth <= 0F && metal <= 0F && sss <= 0F && emission <= 0F && relief <= 0F)
        {
            return null;
        }

        return new float[]{smooth, metal, sss, emission, relief};
    }

    /**
     * 该组的剔除开关（B2-04）：1.20.x 用 {@code RenderSystem.enableCull()} 在肢体剔除
     * 名单内的组上强制背面剔除，1.21.11 改为给这次提交选 culled 变体。
     */
    public static boolean culled(ModelInstance instance, boolean baseCull, ModelGroup group)
    {
        if (baseCull || instance == null || group == null || group.id == null)
        {
            return baseCull;
        }

        ModelConfig config = instance.config;

        if (config instanceof LimbCullingConfigHolder holder)
        {
            try
            {
                return holder.bbspp$getLimbCullingBones().contains(group.id);
            }
            catch (Throwable ignored)
            {
                /* 配置来源不可用时按基线处理，不影响绘制。 */
            }
        }

        return baseCull;
    }
}
