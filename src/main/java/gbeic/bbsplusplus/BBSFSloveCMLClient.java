package gbeic.bbsplusplus;

import gbeic.bbsplusplus.clips.HotbarClip;
import gbeic.bbsplusplus.clips.ReplayClip;
import gbeic.bbsplusplus.clips.screen.CinematicClip;
import gbeic.bbsplusplus.forms.FluidForm;
import gbeic.bbsplusplus.forms.renderers.FluidFormRenderer;
import gbeic.bbsplusplus.forms.renderers.GradedVanillaParticleFormRenderer;
import gbeic.bbsplusplus.particles.ParticlePlusClient;
import gbeic.bbsplusplus.premiere.PremiereExportHandler;
import gbeic.bbsplusplus.settings.ValueSectionHeader;
import gbeic.bbsplusplus.ui.film.clips.UICinematicClip;
import gbeic.bbsplusplus.ui.film.clips.UIHotbarClip;
import gbeic.bbsplusplus.ui.film.clips.UIReplayClip;
import gbeic.bbsplusplus.ui.forms.UIFluidForm;
import gbeic.bbsplusplus.utils.AnimationInterpInheritance;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * BBS FSloveCML Client - 注册客户端 UI 组件与 addon 翻译/源包。
 *
 * <p>BBS(2.5) 的 BBSModClient 通过 Fabric "bbs-client-addon" 入口加载 client addon
 * （即 BBSFSloveCMLClientAddon，见 fabric.mod.json），无需再手动 events.register。
 * 本类保留翻译/源包的双保险注册：早于 MC 的 LanguageManager.reload 直接注册，
 * 无论 addon 何时加载都不会错过 l10n 重载。</p>
 */
public class BBSFSloveCMLClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(BBSFSloveCML.MOD_ID + "_client");
    private static L10n registeredL10n;

    public static synchronized void registerTranslations(L10n l10n) {
        if (l10n == null || registeredL10n == l10n) {
            return;
        }

        l10n.registerOne((lang) -> Link.assets("bbspp/strings/" + lang + ".json"));
        registeredL10n = l10n;

        try {
            l10n.reload();
            LOGGER.info("[FSloveCML] addon translations registered and loaded");
        } catch (RuntimeException e) {
            LOGGER.error("[FSloveCML] addon translation reload failed", e);
        }
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[FSloveCML] 客户端初始化开始...");

        try {
            /* 注册 addon 自有的内部资源源包到主 AssetProvider（source="assets"）。
             * 用本类 classloader 读 jar内 assets/bbspp/strings/<lang>.json，
             * 避免主 InternalAssetsSourcePack 用主 mod classloader 读 assets/bbs/assets/... 时
             * 与主 jar 同路径 zh_cn.json 先命中、盖掉本 addon 新增翻译键。 */
            BBSMod.getProvider().register(new InternalAssetsSourcePack(Link.ASSETS, "assets", BBSFSloveCMLClient.class));

            /* 注册 addon 自有 namespace 的翻译提供者（path 与主 bbs strings 不重叠）。
             * 注册后立即 reload；若此时 BBS 尚未创建 L10n，RegisterL10nEvent 会补注册。 */
            registerTranslations(BBSModClient.getL10n());

            ParticlePlusClient.initialize();
            LOGGER.info("[FSloveCML] Particle+ components registered");

            /* Client addon 由 BBS 2.5 经 "bbs-client-addon" 入口加载,此处不再手动注册,
             * 避免 F5 面板键等 addon 事件被重复订阅。 */
            UIClip.register(HotbarClip.class, UIHotbarClip::new);
            LOGGER.info("[FSloveCML] UIHotbarClip 注册成功!");

            UIClip.register(CinematicClip.class, UICinematicClip::new);
            LOGGER.info("[FSloveCML] UICinematicClip 注册成功!");

            UIClip.register(ReplayClip.class, UIReplayClip::new);
            LOGGER.info("[FSloveCML] UIReplayClip 注册成功!");

            /* 流体伪装：编辑器面板 + 渲染器 */
            UIFormEditor.register(FluidForm.class, UIFluidForm::new);
            FormUtilsClient.register(FluidForm.class, FluidFormRenderer::new);
            LOGGER.info("[FSloveCML] 流体伪装 UI 与渲染器注册成功!");

            FormUtilsClient.register(VanillaParticleForm.class, GradedVanillaParticleFormRenderer::new);
            LOGGER.info("[FSloveCML] 原版粒子伪装颜色渲染器注册成功!");

            /* 注册 easing:"bezier" 标记,让动画导入/导出能携带贝塞尔插值(联动 PoseCurve) */
            AnimationInterpInheritance.registerBezierEasing();

            /* 设置分区标题：仅居中文字，无输入框 / tooltip */
            UIValueMap.register(ValueSectionHeader.class, (value, ui) ->
            {
                UILabel label = UI.label(L10n.lang(UIValueFactory.getValueLabelKey(value)), 20);
                label.labelAnchor(0.5F, 0.5F);
                return Collections.singletonList(label);
            });
            LOGGER.info("[FSloveCML] ValueSectionHeader UI 工厂已注册");

            /* Premiere 导出:录制结束后生成 XML/SRT */
            ClientTickEvents.END_CLIENT_TICK.register(client ->
                PremiereExportHandler.getInstance().onClientTick());
            LOGGER.info("[FSloveCML] Premiere 导出 tick 监听已注册");
        } catch (Exception e) {
            LOGGER.error("[FSloveCML] 客户端初始化失败", e);
        }

        LOGGER.info("[FSloveCML] 客户端初始化完成!");
    }
}
