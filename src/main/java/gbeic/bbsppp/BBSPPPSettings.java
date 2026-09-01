package gbeic.bbsppp;

import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * 保存 BBSPPP 注入到 BBS++ 设置中的私有设置项。
 * <p>
 * 自 BBS++ 设置提升为独立模块后，BBSPPP 私有选项由
 * {@code gbeic.bbsplusplus.BBSPlusPlusSettings#register} 直接注册，
 * 紧跟在「BBS 增强功能」分组之后。本类仅持有设置值引用，
 * 供字幕导出等业务逻辑读取。
 * </p>
 */
public class BBSPPPSettings
{
    /** 开启后，在视频导出结束时额外生成音频 SRT 字幕文件 */
    public static ValueBoolean exportAudioSubtitle;

    /**
     * 兼容旧调用路径的空操作方法。
     * <p>
     * 历史上 BBSPPP 通过 {@code BBSModMixin} 在 BBS 主设置创建完成后
     * 把私有开关追加到 bbspp 分类。现在 BBS++ 已独立为设置模块，
     * 所有设置项（含 BBSPPP）在模块创建时统一注册，此方法不再需要
     * 执行任何操作。保留方法签名是为了不破坏旧 Mixin 的调用点。
     * </p>
     */
    public static void registerIntoBBSPlusPlus(Settings settings, boolean reloadExistingValue)
    {
        // 设置项已由 BBSPlusPlusSettings.register 统一注册，此处无需重复操作。
    }

    public static boolean shouldExportAudioSubtitle()
    {
        return exportAudioSubtitle != null && exportAudioSubtitle.get();
    }
}
