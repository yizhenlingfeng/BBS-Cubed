package gbeic.bbsplusplus;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.BBSApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import gbeic.bbsplusplus.command.BBSPlusPlusCommand;

/**
 * BBS++ 主入口点。
 * <p>
 * 实际的 BBS 注册（表单 / 关键帧工厂）由 {@link BBSPlusPlusAddon} 订阅
 * BBS 官方事件完成。
 * </p>
 */
public class BBSPlusPlusMod implements ModInitializer, BBSAddonMod
{
    public static final String MOD_ID = "bbsplusplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        /* BBS 2.7 的 addon API 版本为 2。 */
        BBSApi.requireVersion(MOD_ID, 2);

        // 发光方块由各 Minecraft 版本源码集提供同名实现。
        try
        {
            BBSPlusPlusBlocks.register();
        }
        catch (RuntimeException | LinkageError e)
        {
            LOGGER.warn("BBSPlusPlusBlocks.register() 调用失败", e);
        }

        this.registerKeyframeTrackExtensions();
        CommandRegistrationCallback.EVENT.register(BBSPlusPlusCommand::register);
        LOGGER.info("[BBS Cubed] 主入口初始化完成");

        // aaa_effect 关键帧工厂改由 BBSPlusPlusAddon 订阅 RegisterKeyframeFactoriesEvent 注册
        // （原直接 KeyframeFactories.FACTORIES.put 存在"未注册就被读取"的时序隐患）。
        // 注意：AAA 粒子表单同样由 BBSPlusPlusAddon 订阅 RegisterFormsEvent 注册。
    }

    /**
     * 注册 BBS++ 注入到原版形态上的关键帧轨道显示信息。
     * <p>
     * 这些轨道的真实属性 ID 必须稳定且带命名空间，避免和原版或第三方形态冲突；
     * 因此这里额外提供面向时间轴显示的默认英文名、中文名和排序锚点。
     * </p>
     */
    private void registerKeyframeTrackExtensions()
    {
        KeyframeTrackExtensionRegistry.register("bbspp_uv_transform", "Texture Transform", "纹理变换", "texture", null, null, "model");
    }
}
