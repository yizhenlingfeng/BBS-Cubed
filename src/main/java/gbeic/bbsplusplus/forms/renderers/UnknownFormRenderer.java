package gbeic.bbsplusplus.forms.renderers;

import mchorse.bbs_mod.forms.forms.UnknownForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * 为运行时缺失的 form 类型提供一个空 renderer。
 *
 * <p>BBS 在反序列化回放/模型数据时，遇到当前环境里没有注册的 form 类型
 * （例如以前装过 IRLite 又卸载，回放里残留 {@code irlite:spotlight}；或
 * bbspp 自定义 form 的 renderer 注册尚未跑），会走
 * {@code FormArchitect.createUnknown(...)}，把它反序列化成
 * {@link UnknownForm}。但 BBS 自身的 {@code FormUtilsClient.setup()} 没有为
 * {@code UnknownForm} 注册任何 renderer，于是 {@code FormUtilsClient.getRenderer()}
 * 直接返回 {@code null}。</p>
 *
 * <p>一旦轨道相机在切换回放时通过 {@code FormFrameCache.collect(...)} 去收集骨骼
 * 矩阵，就会在 {@code RenderFrame.collect(...)} 里对 null 调
 * {@code collectMatrices(...)} 抛出 NullPointerException，整个电影 UI 崩溃。
 * （{@code OrbitFilmCameraController.writeAnchor} 已经对 null renderer 做了判空，
 * 但 {@code getOrbitTarget -> FormFrameCache.collect} 这条路径没有。）</p>
 *
 * <p>这里注册一个 no-op renderer：{@code collectMatrices} 沿用基类默认实现，只会
 * 产出一个空前缀（""）的变换矩阵条目，而 {@code getPoseCenter} 本就会跳过空前缀，
 * 所以轨道中心退化为实体自身坐标；其余渲染方法全部空实现。这样未知 form 在场景里
 * 静默不显示，但 UI 与相机轨道不再崩溃。所有「未知 form 类型」都共用这一个类，
 * 一处修复覆盖全部缺失类型。</p>
 */
public class UnknownFormRenderer extends FormRenderer<UnknownForm>
{
    public UnknownFormRenderer(UnknownForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* 未知 form 在编辑器面板里不画任何预览图。 */
    }
}
