package gbeic.bbsplusplus;

import gbeic.bbsplusplus.pbr.BonePBRKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BBS FSloveCML - 主入口类
 *
 * <p>剪辑/流体伪装注册不在本类进行：Fabric 入口顺序下本类先于 BBSMod 初始化，
 * 此时 BBSMod 的 forms/剪辑工厂尚未创建（getForms() 为 null）。
 * 注册改由 {@link BBSFSloveCMLAddon#onRegisterSettings} 在 BBS 的
 * RegisterSettingsEvent 回调中执行——那时 BBSMod.onInitialize 已完成。</p>
 */
public class BBSFSloveCML implements ModInitializer {
    public static final String MOD_ID = "bbspp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        /* 关键帧工厂是静态注册表，不依赖 BBS 初始化，可在主入口注册 */
        KeyframeFactories.FACTORIES.put("bone_pbr", new BonePBRKeyframeFactory());

        LOGGER.info("========================================");
        LOGGER.info("[FSloveCML] 模组初始化开始...");
        LOGGER.info("[FSloveCML] 模组 ID: {}", MOD_ID);
        LOGGER.info("========================================");
        LOGGER.info("[FSloveCML] 模组初始化完成!");
        LOGGER.info("[FSloveCML] 已添加功能:");
        LOGGER.info("[FSloveCML] - 快捷栏剪辑 (HotbarClip)");
        LOGGER.info("[FSloveCML] - 电影效果剪辑 (CinematicClip)");
        LOGGER.info("[FSloveCML] - 骨骼纹理按钮 (通过 Mixin)");
        LOGGER.info("[FSloveCML] - 回放设置面板 (通过 addon)");
        LOGGER.info("========================================");
    }
}
