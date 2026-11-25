package gbeic.bbsplusplus.client;

import gbeic.bbsplusplus.BBSPlusPlusClientAddon;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.clips.HotbarClip;
import gbeic.bbsplusplus.clips.ReplayClip;
import gbeic.bbsplusplus.clips.screen.CinematicClip;
import gbeic.bbsplusplus.forms.FluidForm;
import gbeic.bbsplusplus.forms.renderers.FluidFormRenderer;
import gbeic.bbsplusplus.forms.renderers.IconPlaceholderFormRenderer;
import gbeic.bbsplusplus.forms.renderers.UnknownFormRenderer;
import gbeic.bbsplusplus.particles.ParticlePlusClient;
import gbeic.bbsplusplus.premiere.PremiereExportHandler;
import gbeic.bbsplusplus.settings.ValueSectionHeader;
import gbeic.bbsplusplus.ui.film.clips.UICinematicClip;
import gbeic.bbsplusplus.ui.film.clips.UIHotbarClip;
import gbeic.bbsplusplus.ui.film.clips.UIReplayClip;
import gbeic.bbsplusplus.ui.forms.UIFluidForm;
import gbeic.bbsplusplus.util.AnimationInterpInheritance;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.UnknownForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;

/**
 * BBS Cubed 客户端功能注册器（非 entrypoint）。
 *
 * <p>由唯一客户端入口 {@link BBSPlusPlusModClient#onInitializeClient()} 调用，负责
 * 自有 assets 包、addon 翻译、剪辑 UI、流体表单、Value 工厂与 Premiere tick。
 * 翻译注册同时服务 {@link BBSPlusPlusClientAddon} 的 {@code RegisterL10nEvent}：事件可能
 * 早于资源包注册，此时延迟，待 {@link #initialize()} 后再次加载。</p>
 */
public final class ClientFeatures
{
    private ClientFeatures()
    {}

    public static final Logger LOGGER = LoggerFactory.getLogger(BBSPlusPlusMod.MOD_ID + "_client");
    private static L10n registeredL10n;

    public static synchronized void registerTranslations(L10n l10n)
    {
        if (l10n == null)
        {
            return;
        }

        /* BBS 的 RegisterL10nEvent 可能早于本模组的客户端初始化；此时自有 assets 包尚未注册，
         * 直接 reload 只会打印一堆 FileNotFoundException。等 initialize() 注册资源包后会再次调用。 */
        if (!hasLanguageFile(Link.assets("bbspp/strings/en_us.json")))
        {
            LOGGER.debug("[BBS Cubed] 自有资源包尚未注册，延迟加载 addon 翻译");
            return;
        }

        if (registeredL10n != l10n)
        {
            /* BBS++ 自有 namespace 的 UI 文案（bbspp/strings/<lang>.json） */
            l10n.registerOne((lang) -> Link.assets("bbspp/strings/" + lang + ".json"));

            /* BBS 主 namespace 的补充文案（assets/bbs/assets/lang/<lang>.json，原 L10nMixin 注册的那份）。
             * 文件缺失时返回空列表，安静回落到 BBS 已加载的默认英文。 */
            l10n.register((lang) -> {
                Link link = Link.assets("lang/" + lang + ".json");

                return hasLanguageFile(link)
                    ? Collections.singletonList(link)
                    : Collections.emptyList();
            });

            registeredL10n = l10n;
        }

        /* 资源包就绪后重新加载翻译，覆盖 BBS 提前派发语言事件的情况。 */
        try
        {
            l10n.reload();
            LOGGER.info("[BBS Cubed] addon translations registered and loaded");
        }
        catch (RuntimeException e)
        {
            LOGGER.error("[BBS Cubed] addon translation reload failed", e);
        }
    }

    private static boolean hasLanguageFile(Link link)
    {
        try (InputStream ignored = BBSMod.getProvider().getAsset(link))
        {
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static void initialize()
    {
        LOGGER.info("[BBS Cubed] 客户端初始化开始...");

        try
        {
            /* 注册 addon 自有的内部资源源包到主 AssetProvider（source="assets"）。
             * 用本类 classloader 读 jar内 assets/bbspp/strings/<lang>.json，
             * 避免主 InternalAssetsSourcePack 用主 mod classloader 读 assets/bbs/assets/... 时
             * 与主 jar 同路径 zh_cn.json 先命中、盖掉本 addon 新增翻译键。 */
            BBSMod.getProvider().register(new InternalAssetsSourcePack(Link.ASSETS, "assets", ClientFeatures.class));

            /* 注册 addon 自有 namespace 的翻译提供者（path 与主 bbs strings 不重叠）。
             * 注册后立即 reload；若此时 BBS 尚未创建 L10n，RegisterL10nEvent 会补注册。 */
            registerTranslations(BBSModClient.getL10n());

            initializeFeature("Particle+ 组件", ParticlePlusClient::initialize);

            /* BBS 通过 bbs-client-addon 入口订阅 addon 事件。 */
            UIClip.register(HotbarClip.class, UIHotbarClip::new);
            LOGGER.info("[BBS Cubed] UIHotbarClip 注册成功!");

            if (BBSMod.getFactoryCameraClips() != null
                && BBSMod.getFactoryCameraClips().getTypeClass(Link.bbs("cinematic")) == CinematicClip.class)
            {
                UIClip.register(CinematicClip.class, UICinematicClip::new);
                LOGGER.info("[BBS Cubed] UICinematicClip 注册成功!");
            }
            else
            {
                LOGGER.info("[BBS Cubed] bbs:cinematic 由其它模组提供，跳过 UICinematicClip，避免覆盖");
            }

            UIClip.register(ReplayClip.class, UIReplayClip::new);
            LOGGER.info("[BBS Cubed] UIReplayClip 注册成功!");

            initializeFeature("流体伪装编辑器", () -> UIFormEditor.register(FluidForm.class, UIFluidForm::new));
            if (!initializeFeature("流体伪装渲染器", () -> FormUtilsClient.register(FluidForm.class, FluidFormRenderer::new)))
            {
                initializeFeature("流体伪装占位渲染器", () ->
                    FormUtilsClient.register(FluidForm.class, form -> new IconPlaceholderFormRenderer<>(form, null, Icons.DROP)));
            }

            /* 为运行时缺失的 form 类型（UnknownForm，例如回放里残留的 irlite:spotlight
             * 等未安装前置的 form）注册空 renderer，避免轨道相机收集骨骼矩阵时
             * getRenderer() 返回 null 导致 NPE 崩溃。 */
            FormUtilsClient.register(UnknownForm.class, UnknownFormRenderer::new);
            LOGGER.info("[BBS Cubed] UnknownForm 占位渲染器注册成功（防止缺失 form 类型崩溃）");

            /* 注册 easing:"bezier" 标记,让动画导入/导出能携带贝塞尔插值(联动 PoseCurve) */
            AnimationInterpInheritance.registerBezierEasing();

            /* 设置分区标题：仅居中文字，无输入框 / tooltip */
            UIValueMap.register(ValueSectionHeader.class, (value, ui) ->
            {
                UILabel label = UI.label(L10n.lang(UIValueFactory.getValueLabelKey(value)), 20);
                label.labelAnchor(0.5F, 0.5F);
                return Collections.singletonList(label);
            });
            LOGGER.info("[BBS Cubed] ValueSectionHeader UI 工厂已注册");

            /* Premiere 导出:录制结束后生成 XML/SRT */
            ClientTickEvents.END_CLIENT_TICK.register(client ->
                PremiereExportHandler.getInstance().onClientTick());
            LOGGER.info("[BBS Cubed] Premiere 导出 tick 监听已注册");
            LOGGER.info("[BBS Cubed] 客户端核心功能注册完成");
        }
        catch (RuntimeException | LinkageError e)
        {
            LOGGER.error("[BBS Cubed] 客户端核心功能初始化失败", e);
        }
    }

    static boolean initializeFeature(String name, Runnable initialize)
    {
        try
        {
            initialize.run();
            return true;
        }
        catch (RuntimeException | LinkageError e)
        {
            LOGGER.warn("[BBS Cubed] {}初始化失败", name, e);
            return false;
        }
    }
}
