package gbeic.bbsplusplus.settings;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * CML（FSloveCML）设置值持有者。
 *
 * <p>所有设置项由 {@code gbeic.bbsplusplus.BBSPlusPlusSettings#register}
 * 统一注册到 BBS Cubed 独立设置模块中，分属不同分类：</p>
 * <ul>
 *   <li>BBS 增强 — pivotTransform / poseKeyframeCollapse / snowActions / lockedLayoutPreventsResizing（前四项核心开关）</li>
 *   <li>CML 增强 — replaySprintParticles / pickLimbTexture / enchantGlint / fluidRealisticModelInteraction</li>
 *   <li>导出增强 — premiereExport* 系列</li>
 *   <li>隐藏设置 — bonePriority* / animationState* / keyframeEditorTimelineRatio</li>
 * </ul>
 *
 * <p>不再向主 BBS 设置注入 bbs_snow 分类，分栏符号（====Cml transplar==== / ====premiere-export====）已移除。</p>
 */
public class CMLSettings {
    /* 回放设置 */
    public static ValueBoolean replaySprintParticles;      // 回放疾跑粒子

    /* 外观设置 */
    public static ValueBoolean pickLimbTexture;            // 骨骼纹理按钮
    public static ValueBoolean enchantGlint;               // 附魔光效按钮

    /* 流体模拟 */
    public static ValueBoolean fluidRealisticModelInteraction; // 流体精确模型交互

    /* BBS_snow 设置 */
    public static ValueBoolean pivotTransform;             // XYZ 变换中心点(pivot)功能
    public static ValueBoolean poseKeyframeCollapse;       // Pose 关键帧骨骼折叠功能
    public static ValueBoolean bonePriorityExpandedLimb;   // 骨骼优先性:优先选择已展开 pose 的肢体轨道
    public static ValueString bonePriorityTrack;           // 骨骼优先性:点击骨骼跳转的优先轨道("" = 跟随原版)
    public static ValueBoolean snowActions;                // snow_actions 时间线功能
    public static ValueBoolean lockedLayoutPreventsResizing; // 锁定布局时彻底禁用边缘拖动
    public static ValueString animationStateLayout;        // 动画状态编辑器停靠布局(JSON,隐藏设置)
    public static ValueString animationStateHiddenPanels;  // 动画状态编辑器被用户隐藏的面板 id(JSON,隐藏设置)
    public static ValueFloat keyframeEditorTimelineRatio;  // 关键帧编辑器左侧轨道占比(隐藏设置)

    /* Premiere Export */
    public static ValueBoolean premiereExportEnabled;      // Premiere export功能(关=隐藏按钮)
    public static ValueBoolean premiereExportIndividualAudio; // 导出独立音频片段
    public static ValueBoolean premiereExportAudioOnly;   // 仅音频导出(不录视频)
    public static ValueBoolean premiereExportNtscFlag;     // NTSC 帧率标记
    public static ValueBoolean premiereExportSrt;          // 导出 SRT 代替 XML

    /**
     * 设置注册前保持默认开启，避免模型在 BBS 设置初始化期间丢失 actions 扩展。
     */
    public static boolean isSnowActionsEnabled() {
        return snowActions == null || snowActions.get();
    }
}
