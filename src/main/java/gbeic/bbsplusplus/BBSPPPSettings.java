package gbeic.bbsplusplus;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * 保存纹理补间模块注入到 BBS++ 设置中的私有设置项。
 * <p>
 * 自 BBS++ 设置提升为独立模块后，私有选项由
 * {@code gbeic.bbsplusplus.BBSPlusPlusSettings#register} 直接注册，
 * 紧跟在「BBS 增强功能」分组之后。本类仅持有设置值引用，
 * 供字幕导出等业务逻辑读取。
 * </p>
 */
public class BBSPPPSettings
{
    /** 开启后，在视频导出结束时额外生成音频 SRT 字幕文件 */
    public static ValueBoolean exportAudioSubtitle;

    public static boolean shouldExportAudioSubtitle()
    {
        return exportAudioSubtitle != null && exportAudioSubtitle.get();
    }
}
