package gbeic.bbsplusplus.settings;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * BBS Cubed 全部设置的持有者。
 *
 * <p>原来分散在多个设置类中的静态引用统一收敛到这里；字段由
 * {@link gbeic.bbsplusplus.BBSPlusPlusSettings#register} 在设置模块注册时赋值。
 * 字段声明顺序沿用历史分组，实际界面分类以注册顺序为准。</p>
 */
public final class ModSettings
{
    private ModSettings()
    {}

    /* 字段声明沿用历史顺序，设置界面中的分类与顺序见 BBSPlusPlusSettings。 */

    /** 启用后，关键帧轨道名称显示为中文 */
    public static ValueBoolean chineseKeyframeNames;

    /** 打开影片编辑器时自动切换旁观模式，关闭时恢复 */
    public static ValueBoolean filmAutoGameMode;

    /** 禁止在关键帧编辑器中把关键帧拖到 0 秒之前 */
    public static ValueBoolean preventNegativeKeyframes;

    /** 反转影片编辑器中滚轮驱动的时间轴方向 */
    public static ValueBoolean reverseTimelineScroll;

    /** Shift 点击模型部位时直接选择父骨骼 */
    public static ValueBoolean directParentPicking;

    /** IK 目标不能选择本 IK 链自身的骨骼（防循环） */
    public static ValueBoolean ikTargetCycleConstraint;

    /** 伪装界面使用新的双栏布局 */
    public static ValueBoolean newMorphingPanel;

    /** Blockbench.exe 完整路径 */
    public static ValueString blockbenchPath;

    /** 优先使用 .bbmodel 工程文件打开模型 */
    public static ValueBoolean blockbenchPreferBbmodel;

    /** 影片选择界面使用新版影片库布局 */
    public static ValueBoolean newFilmLibraryUi;

    /** 影片编辑器显示 Iris 光影控制按钮 */
    public static ValueBoolean enableIrisButton;

    /** 曲线剪辑使用按光影设置分类的新选择界面 */
    public static ValueBoolean shaderCurvePicker;

    /** 关键帧布局锁定时同时隐藏轨道宽度调节柄 */
    public static ValueBoolean enableUiKeyframesLayoutLock;

    /** 世界内播放影片时也应用曲线剪辑中的光影参数 */
    public static ValueBoolean worldFilmShaderCurves;

    /** 允许把整段剪辑拖到时间轴顶部之外以创建更多轨道 */
    public static ValueBoolean allowClipTrackExpansion;

    /** BBS 结构化复制数据保存在 BBS Cubed 私有剪贴板 */
    public static ValueBoolean privateBbsClipboard;

    /** 自动为没有 config.json 的模型生成关闭面剔除的配置 */
    public static ValueBoolean autoDisableFaceCulling;

    /** 影片编辑器关键帧视图中 Alt+滚轮的行为模式 */
    public static ValueInt filmAltWheelTimelineMode;

    /** 视野外跳过物品喷射粒子渲染 */
    public static ValueBoolean itemSprayFrustumCulling;

    /** 物品喷射最大渲染距离，0 = 跟随客户端视距 */
    public static ValueInt itemSprayMaxRenderDistance;

    /** 每帧最多渲染的物品喷射粒子数，0 = 不限 */
    public static ValueInt itemSprayMaxRenderedItems;

    /** IRLights 阴影中最多参与投影的物品喷射粒子数，0 = 禁用 */
    public static ValueInt itemSprayIRLiteShadowMaxItems;

    /** 操控器默认平移模式，G/S/R 切换、T 键循环 */
    public static ValueBoolean blockbenchGizmo;

    /** 第 2 次按 G/S/R 保持原版直接切换行为 */
    public static ValueBoolean gizmoKeepOriginal;

    /** 回放疾跑粒子 */
    public static ValueBoolean replaySprintParticles;

    /** 骨骼纹理按钮 */
    public static ValueBoolean pickLimbTexture;

    /** 流体精确模型交互 */
    public static ValueBoolean fluidRealisticModelInteraction;

    /** XYZ 变换锚点（pivot）功能 */
    public static ValueBoolean pivotTransform;

    /** XYZ 变换位移（T）行的单次点按/拖动步长 */
    public static ValueFloat transformTranslateStep;

    /** XYZ 变换缩放（S）行的单次点按/拖动步长 */
    public static ValueFloat transformScaleStep;

    /** XYZ 变换欧拉旋转（R）行的单次点按/拖动步长 */
    public static ValueFloat transformRotateStep;

    /** XYZ 变换锚点（P）行的单次点按/拖动步长 */
    public static ValueFloat transformPivotStep;

    /** 骨骼优先性：优先选择已展开 pose 的肢体轨道 */
    public static ValueBoolean bonePriorityExpandedLimb;

    /** 骨骼优先性：点击骨骼跳转的优先轨道（空 = 跟随原版） */
    public static ValueString bonePriorityTrack;

    /** snow_actions 时间轴功能 */
    public static ValueBoolean snowActions;

    /** 动画状态自动读取模型目录的 *.animation.json */
    public static ValueBoolean autoLoadAnimationFile;

    /** 锁定布局时彻底禁用边缘拖动 */
    public static ValueBoolean lockedLayoutPreventsResizing;

    /** 动画状态编辑器停靠布局（JSON，隐藏设置） */
    public static ValueString animationStateLayout;

    /** 动画状态编辑器被隐藏的面板 id（JSON，隐藏设置） */
    public static ValueString animationStateHiddenPanels;

    /** 关键帧编辑器左侧轨道占比（隐藏设置） */
    public static ValueFloat keyframeEditorTimelineRatio;

    /** 视频导出结束时额外生成音频 SRT 字幕 */
    public static ValueBoolean exportAudioSubtitle;

    /** Premiere 导出功能（关 = 隐藏按钮） */
    public static ValueBoolean premiereExportEnabled;

    /** 导出独立音频片段 */
    public static ValueBoolean premiereExportIndividualAudio;

    /** 仅音频导出（不录视频） */
    public static ValueBoolean premiereExportAudioOnly;

    /** NTSC 帧率标记 */
    public static ValueBoolean premiereExportNtscFlag;

    /** 导出 SRT 代替 XML */
    public static ValueBoolean premiereExportSrt;

    /** 纹理管理器排版：0=列表，1=小网格，2=中网格，3=大网格 */
    public static ValueInt textureManagerLayout;

    /** 模型方块渲染距离（米），0 = 原版 512 */
    public static ValueInt modelBlockRenderDistance;

    /** AAA 粒子特效选择窗口宽度，0 = 默认比例 */
    public static ValueInt aaaEffectPickerWidth;

    /** AAA 粒子特效选择窗口高度，0 = 默认比例 */
    public static ValueInt aaaEffectPickerHeight;

    /** 影片库排序：0=名称升序，1=名称降序 */
    public static ValueInt filmLibrarySortMode;

    /** 影片库默认打开位置：folder:路径（根目录为 folder:） */
    public static ValueString filmLibraryDefaultLocation;

    /** 影片库快捷栏固定文件夹，每行一个 */
    public static ValueString filmLibraryShortcuts;

    /** 伪装界面默认分类：home 或分类可见性 ID */
    public static ValueString morphingDefaultCategory;

    /** 伪装界面排版：0=列表，1=网格 */
    public static ValueInt morphingLayoutMode;

    /** 伪装界面图标缩放百分比：20~200 */
    public static ValueInt morphingIconScale;

    /** 回放编辑器自定义轨道顺序（逗号分隔） */
    public static ValueString replayTrackOrder;

    /** 预览工具栏收纳的工具 ID（逗号分隔） */
    public static ValueString filmToolbarOverflow;

    /*
     * 这四个默认值就是 BBS 三个轨道板预设给出的步长，默认下不改变原版手感：
     * 位移 block() = 1/16（面板按三位小数显示为 "0.062"）、
     * 缩放 factor() = 0.25、欧拉旋转 degrees() = 15。
     *
     * 位移这里刻意写 1F/16F 而不是 0.062：原版就是 0.0625，写成 0.062 的话
     * 每点一次箭头都会比原版少 0.0005，点满 16 次也凑不满一格。
     */
    public static final float DEFAULT_TRANSFORM_TRANSLATE_STEP = 1F / 16F;
    public static final float DEFAULT_TRANSFORM_SCALE_STEP = 0.25F;
    public static final float DEFAULT_TRANSFORM_ROTATE_STEP = 15F;
    public static final float DEFAULT_TRANSFORM_PIVOT_STEP = 1F / 16F;

    /**
     * XYZ 变换位移（T）行的步长，以 {@link #transformTranslateStep} 为准。
     * 设置模块尚未注册时回落到 BBS 原版的 1/16。
     */
    public static double getTransformTranslateStep()
    {
        return transformTranslateStep == null ? DEFAULT_TRANSFORM_TRANSLATE_STEP : transformTranslateStep.get();
    }

    /** XYZ 变换缩放（S）行的步长，设置未注册时回落到 BBS 原版的 0.25。 */
    public static double getTransformScaleStep()
    {
        return transformScaleStep == null ? DEFAULT_TRANSFORM_SCALE_STEP : transformScaleStep.get();
    }

    /** XYZ 变换欧拉旋转（R）行的步长（度），设置未注册时回落到 BBS 原版的 15。 */
    public static double getTransformRotateStep()
    {
        return transformRotateStep == null ? DEFAULT_TRANSFORM_ROTATE_STEP : transformRotateStep.get();
    }

    /** XYZ 变换锚点（P）行的步长，设置未注册时回落到 1/16（显示为 0.062）。 */
    public static double getTransformPivotStep()
    {
        return transformPivotStep == null ? DEFAULT_TRANSFORM_PIVOT_STEP : transformPivotStep.get();
    }

    public static AltWheelTimelineMode getFilmAltWheelTimelineMode()
    {
        if (filmAltWheelTimelineMode == null)
        {
            return AltWheelTimelineMode.DEFAULT;
        }

        return AltWheelTimelineMode.fromIndex(filmAltWheelTimelineMode.get());
    }

    /** 设置注册前保持默认开启，避免模型在 BBS 设置初始化期间丢失 actions 扩展。 */
    public static boolean isSnowActionsEnabled()
    {
        return snowActions == null || snowActions.get();
    }

    /** 设置注册前保持默认开启。 */
    public static boolean isAutoLoadAnimationFileEnabled()
    {
        return autoLoadAnimationFile == null || autoLoadAnimationFile.get();
    }

    public static boolean shouldExportAudioSubtitle()
    {
        return exportAudioSubtitle != null && exportAudioSubtitle.get();
    }

    /** 影片编辑器关键帧视图中 Alt+滚轮的行为模式。 */
    public enum AltWheelTimelineMode
    {
        DEFAULT,
        DISABLED,
        HORIZONTAL_SCROLL;

        public static AltWheelTimelineMode fromIndex(int index)
        {
            AltWheelTimelineMode[] values = values();

            return index >= 0 && index < values.length ? values[index] : DEFAULT;
        }
    }
}
