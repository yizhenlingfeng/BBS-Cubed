package gbeic.bbsplusplus;

import java.util.HashMap;
import java.util.Map;

/**
 * 双重映射表：
 * 1. 英文文本 → 中文（供 StringKeyMixin 使用，处理硬编码 IKey.constant）
 * 2. L10n key → 中文（供 LangKeyMixin 使用，处理 BBS 设置项 LangKey）
 *
 * 翻译对齐自 irlite-1.1.7+mc1.20.1_zh-CN_TEST.jar 的 L10nMixin 与 UI 类中文硬编码。
 */
public class IRLightsZHTranslator {

    /** 硬编码英文字符串 → 中文（拦截 StringKey.get()） */
    private static final Map<String, String> STRING_MAP = new HashMap<>();

    /** L10n key → 中文（拦截 LangKey.get()，仅限 irlights 相关的 key） */
    private static final Map<String, String> LANG_KEY_MAP = new HashMap<>();

    static {
        // ═══════════════════════════════════════
        // STRING_MAP：对应 IKey.constant("英文") 的硬编码文本
        // ═══════════════════════════════════════

        // —— 表单面板标签 ——
        STRING_MAP.put("Point light",               "点光源");
        STRING_MAP.put("Spotlight",                  "聚光灯");
        STRING_MAP.put("Entities only",              "仅实体");
        STRING_MAP.put("Blocks only",                "仅方块");
        STRING_MAP.put("Shadows",                    "阴影");
        STRING_MAP.put("Color",                      "颜色");
        STRING_MAP.put("Intensity",                  "强度");
        STRING_MAP.put("Range",                      "范围");
        STRING_MAP.put("Radius",                     "半径");
        STRING_MAP.put("Inner radius",               "内半径");
        STRING_MAP.put("Beam strength",              "光束强度");
        STRING_MAP.put("Anisotropy",                 "各向异性");
        STRING_MAP.put("VL density",                 "体积光密度");
        STRING_MAP.put("Bulb size (shadow softness)","灯珠尺寸（阴影柔和度）");
        STRING_MAP.put("Cookie texture (gobo)",      "遮光片纹理（gobo）");
        STRING_MAP.put("Invert gobo",                "反转遮光片");
        STRING_MAP.put("Cookie rotation",            "遮光片旋转");
        STRING_MAP.put("Cookie scale",               "遮光片缩放");
        STRING_MAP.put("Cookie / gobo (spot mask)",  "遮光片 / gobo（聚光遮罩）");

        // —— 表单 Tab 标签（光源自身设置面板） ——
        STRING_MAP.put("Volumetric (this light)",    "体积光（此灯光）");
        STRING_MAP.put("Outline (this light)",       "轮廓（此灯光）");

        // —— 光源自身体积光设置面板 ——
        STRING_MAP.put("Off: global settings. Keyframes activate own settings.",
            "关闭时：使用全局设置。关键帧可激活自定义设置。");
        STRING_MAP.put("Own volumetric settings",    "自定义体积光设置");
        STRING_MAP.put("Copy global settings",        "复制全局设置");
        STRING_MAP.put("Beam",                       "光束");
        STRING_MAP.put("Beam intensity",             "光束强度");
        STRING_MAP.put("Max distance",               "最大距离");
        STRING_MAP.put("Beam shadows",              "光束阴影");
        STRING_MAP.put("Tip glow",                   "端头辉光");
        STRING_MAP.put("Tip radius",                 "端头半径");
        STRING_MAP.put("Beam noise",                 "光束噪声");
        STRING_MAP.put("Noise amount",               "噪声强度");
        STRING_MAP.put("Noise scale",                "噪声缩放");
        STRING_MAP.put("Drift speed",               "飘移速度");
        STRING_MAP.put("Noise morph",                "噪声形变");

        // —— 光源自身轮廓设置面板 ——
        STRING_MAP.put("Own outline settings",        "自定义轮廓设置");
        STRING_MAP.put("Outline",                    "轮廓");
        STRING_MAP.put("Draw on: all",               "作用于：全部");
        STRING_MAP.put("Draw on: entities",          "作用于：实体");
        STRING_MAP.put("Draw on: blocks",            "作用于：方块");
        STRING_MAP.put("Strength",                   "强度");
        STRING_MAP.put("Thickness (px, 0 = global)", "粗细（像素，0 = 全局）");
        STRING_MAP.put("Fresnel falloff",            "菲涅尔衰减");
        STRING_MAP.put("Backlight rim",              "背光轮廓");
        STRING_MAP.put("Front catch-light",          "正面高光");
        STRING_MAP.put("Front strength",             "正面强度");
        STRING_MAP.put("Inner glow",                 "内部辉光");
        STRING_MAP.put("Glow strength",              "辉光强度");
        STRING_MAP.put("Front rim & glow",           "正面轮廓与辉光");
        STRING_MAP.put("Replays",                    "回放");
        STRING_MAP.put("Outline: selected replays only", "轮廓：仅选中的回放");
        STRING_MAP.put("Choose outlined replays...", "选择被勾勒的回放...");
        STRING_MAP.put("Light: selected replays only",   "灯光：仅选中的回放");
        STRING_MAP.put("Choose lit replays...",      "选择被照亮的回放...");
        STRING_MAP.put("Own outline settings are active.", "自定义轮廓设置已激活。");

        // —— 回放选择器 tooltip ——
        STRING_MAP.put("Click a replay to add or remove it. None clears the list. Changes apply immediately.",
            "点击回放可将其添加或移除。选择“无”可清空列表。更改立即生效。");

        // —— Patcher UI ——
        STRING_MAP.put("Shaderpacks",                "光影包");
        STRING_MAP.put("Patches",                    "补丁");
        STRING_MAP.put("Refresh lists",              "刷新列表");
        STRING_MAP.put("Open shaderpacks folder",    "打开光影包文件夹");
        STRING_MAP.put("Open patches folder",        "打开补丁文件夹");
        STRING_MAP.put("Create new pack each time",  "每次创建新光影包");
        STRING_MAP.put("Validate",                   "校验");
        STRING_MAP.put("Dry-run: check every op against the selected pack, write nothing",
            "试运行：针对所选光影包检查每一步操作，不写入任何内容");
        STRING_MAP.put("Patch",                      "应用补丁");
        STRING_MAP.put("Select a shaderpack and a patch for it.", "请选择一个光影包及对应的补丁。");
        STRING_MAP.put("Couldn't read this patch.", "无法读取此补丁。");
        STRING_MAP.put("Select a shaderpack above to continue.", "请在上方选择光影包以继续。");
        STRING_MAP.put("Select a shaderpack from the list.", "请从列表中选择一个光影包。");
        STRING_MAP.put("Select a patch for the shaderpack.", "请为该光影包选择一个补丁。");
        STRING_MAP.put("Couldn't read the selected patch.", "无法读取所选补丁。");
        STRING_MAP.put("It fits! Press Patch to create the light version of the pack.",
            "补丁匹配！点击「应用补丁」以生成该光影包的灯光版本。");
        STRING_MAP.put("This shaderpack already has the light. Pick the original (clean) pack.",
            "此光影包已包含灯光。请选择原始（未修改）的光影包。");
        STRING_MAP.put("Patch isn't compatible with this mod version. Update the mod or the patch.",
            "补丁与此模组版本不兼容。请更新模组或补丁。");
        STRING_MAP.put("Couldn't open the shaderpack. Make sure a valid pack is selected.",
            "无法打开光影包。请确保选择了有效的光影包。");
        STRING_MAP.put("File error. Close the pack in other programs and try again.",
            "文件错误。请在其他程序中关闭该光影包后重试。");
        STRING_MAP.put("This patch didn't fit the selected pack, maybe it's a different version.",
            "补丁与所选光影包不匹配，可能是版本不同。");

        // —— 阴影质量下拉选项 ——
        STRING_MAP.put("LOW",    "低");
        STRING_MAP.put("MEDIUM", "中");
        STRING_MAP.put("HIGH",   "高");
        STRING_MAP.put("ULTRA",  "极致");

        // —— 描边目标下拉选项（outline_target） ——
        STRING_MAP.put("ALL",      "全部");
        STRING_MAP.put("ENTITIES", "实体");
        STRING_MAP.put("BLOCKS",   "方块");

        // —— 表单面板折叠分组标题 ——
        STRING_MAP.put("Light",           "灯光");
        STRING_MAP.put("Volumetric beam", "体积光束");
        STRING_MAP.put("Affects",         "作用对象");

        // —— 设置面板预设区块（UIPresetSection） ——
        STRING_MAP.put("Presets",    "预设");
        STRING_MAP.put("Quality",    "画质");
        STRING_MAP.put("Beam style", "光束风格");
        STRING_MAP.put("Performance", "性能");
        STRING_MAP.put("Balanced",    "均衡");
        STRING_MAP.put("Custom",      "自定义");
        STRING_MAP.put("Clean",       "纯净");
        STRING_MAP.put("Dusty",       "浮尘");
        STRING_MAP.put("Smoky",       "烟雾");
        STRING_MAP.put("Cost of the lighting: march steps, ray distance, shadow and noise tap strides, "
            + "shadow map resolution and the shader light cap. Custom means the knobs below "
            + "no longer match any preset — pick one to overwrite them. "
            + "No preset selects ULTRA shadows; that one stays a deliberate choice.",
            "光照的性能开销：步进步数、光线距离、阴影与噪声的采样间隔、阴影贴图分辨率以及着色器灯光数量上限。"
            + "“自定义”表示下方的参数不再匹配任何预设——选择一个预设会覆盖这些参数。"
            + "任何预设都不会选择极致阴影，它始终需要手动开启。");
        STRING_MAP.put("Look of the volumetric beams: noise, drift and the glow around the lamp itself. "
            + "Clean is uniform beams, Dusty is drifting puffs, Smoky is heavy morphing haze "
            + "(the priciest of the three — it is the only one that turns morph on).",
            "体积光束的外观：噪声、飘移以及灯体本身的辉光。纯净为均匀光束，浮尘为飘动的尘团，"
            + "烟雾为浓重的形变雾霭（三者中最耗性能——只有它会开启噪声形变）。");

        // —— 调试区块（UIDebugSection，-Dirlite.debug=true 时显示） ——
        STRING_MAP.put("Debug",                  "调试");
        STRING_MAP.put("Hide performance overlay", "隐藏性能浮层");
        STRING_MAP.put("Show performance overlay", "显示性能浮层");
        STRING_MAP.put("Per-pass GPU milliseconds in the top-left corner: the shadow "
            + "bake segments, every Iris fullscreen pass and the VL march, plus CPU frame time and "
            + "VRAM residency. Costs a GL timer query per pass, so leave it off for recording. "
            + "Takes effect on the next frame.",
            "在左上角显示每个渲染通道的 GPU 耗时（毫秒）：阴影烘焙分段、Iris 的每个全屏通道与体积光步进，"
            + "以及 CPU 帧时间与显存占用。每个通道都要付出一次 GL 计时查询的代价，因此录制时请保持关闭。将在下一帧生效。");

        // —— 回放时间线分类 ——
        STRING_MAP.put("Light keyframes: colour, intensity, beam, shadows, the light's own outline and volumetric settings, and its replay lists",
            "灯光关键帧：颜色、强度、光束、阴影、灯光自身的轮廓与体积光设置，以及其回放列表");
        STRING_MAP.put("Choose replays...", "选择回放...");

        // -- 回放时间线轨道名（LightTrackLayout 的 IKey.constant） --
        // 这些与表单面板措辞不同，轨道编辑器用短名
        STRING_MAP.put("Cookie / gobo",              "遮光片 / gobo");
        STRING_MAP.put("Volumetric",                 "体积光");
        STRING_MAP.put("Cone angle",                 "锥角");
        STRING_MAP.put("Inner cone angle",           "内锥角");
        STRING_MAP.put("Density",                    "密度");
        STRING_MAP.put("Softness",                   "柔和度");
        STRING_MAP.put("Target (all / entities / blocks)", "作用对象（全部/实体/方块）");
        STRING_MAP.put("Thickness",                  "粗细");
        STRING_MAP.put("Back rim",                   "背光轮廓");
        STRING_MAP.put("Front rim",                  "正面轮廓");
        STRING_MAP.put("Front rim strength",         "正面轮廓强度");
        STRING_MAP.put("Noise",                      "噪声");
        STRING_MAP.put("Lit replays",                "被照亮的回放");
        STRING_MAP.put("Outlined replays",           "被勾勒的回放");

        // ═══════════════════════════════════════
        // LANG_KEY_MAP：对应 BBS L10n 系统的 LangKey
        // ═══════════════════════════════════════

        // —— 旧版设置面板侧边栏名称（兼容旧版 bbs.config.irlite.*） ——
        LANG_KEY_MAP.put("bbs.config.irlite.title",         "IRLite");
        LANG_KEY_MAP.put("bbs.config.irlite.tooltip",       "IRLite 光源插件设置");
        LANG_KEY_MAP.put("bbs.config.irlite_patcher.title", "光影补丁");
        LANG_KEY_MAP.put("bbs.config.irlite_patcher.tooltip","将 .irlights 补丁应用到光影包");

        // —— 旧版设置项标签 ——
        LANG_KEY_MAP.put("bbs.config.irlite.show_guides",       "在世界中绘制光源线框");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_quality",    "阴影质量");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_cache",      "缓存静态阴影");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_blocks",     "方块阴影");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_block_radius","阴影投射方块半径");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_bake_budget", "阴影烘焙预算");

        // —— 旧版设置项悬浮注释（-comment 键） ——
        LANG_KEY_MAP.put("bbs.config.irlite.show_guides-comment",
            "为世界中放置的点光源和聚光灯绘制线框。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_quality-comment",
            "阴影深度贴图分辨率。越高越清晰，但占用更多显存（低～40 MB……极高～2.5 GB）。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_cache-comment",
            "仅在灯光或遮挡物移动时重新烘焙阴影贴图。静态场景大幅提升帧率，如阴影有残影请关闭。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_blocks-comment",
            "从世界方块投射阴影。异形方块（台阶、楼梯、栅栏）按真实形状投影，镂空方块（树叶、栏杆、玻璃门）忽略透明像素。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_block_radius-comment",
            "灯光周围收集阴影投射方块的半径（方块数）。超出此范围的方块不投射阴影。较大值使每次重收集更耗性能，默认 24。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_bake_budget-comment",
            "每帧允许烘焙的最大阴影贴图数量（0 = 无限制）。较低的值可避免烘焙期间的帧率骤降。默认 4。");

        // ═══════════════════════════════════════
        // 新版 addon（irlights 独立设置模块）
        // 翻译对齐自 irlite-1.1.7 zh-CN_TEST.jar
        // ═══════════════════════════════════════

        // —— 模块与分类名称 ——
        LANG_KEY_MAP.put("irlights.config.title",                  "IRLights");
        LANG_KEY_MAP.put("irlights.config.presets.title",          "预设");
        LANG_KEY_MAP.put("irlights.config.presets.tooltip",        "画质与光束风格预设，以及值得单独使用的参数");
        LANG_KEY_MAP.put("irlights.config.lighting.title",         "光照");
        LANG_KEY_MAP.put("irlights.config.lighting.tooltip",       "IRLights 灯光照射在表面上产生的效果：亮度、高光与卡通色阶");
        LANG_KEY_MAP.put("irlights.config.volumetric.title",       "体积光");
        LANG_KEY_MAP.put("irlights.config.volumetric.tooltip",     "光束与雾霭：步进开销、阴影计算与动态噪声");
        LANG_KEY_MAP.put("irlights.config.shadows.title",         "阴影");
        LANG_KEY_MAP.put("irlights.config.shadows.tooltip",        "为 IRLights 灯光烘焙的阴影贴图");
        LANG_KEY_MAP.put("irlights.config.outline.title",         "轮廓");
        LANG_KEY_MAP.put("irlights.config.outline.tooltip",       "由灯光驱动的边缘轮廓：剪影、菲涅尔光晕与正面高光");
        LANG_KEY_MAP.put("irlights.config.patcher.title",         "光影补丁器");
        LANG_KEY_MAP.put("irlights.config.patcher.tooltip",       "将 IRLights 的 .irlights 补丁文件应用到光影包上");

        // —— 预设分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.presets.vl_intensity",       "体积光强度");
        LANG_KEY_MAP.put("irlights.config.presets.max_shader_lights",  "着色器灯光数量上限");
        LANG_KEY_MAP.put("irlights.config.presets.show_guides",        "在世界中显示灯光辅助线");

        // —— 预设分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.presets.vl_intensity-comment",
            "IRLite 灯光产生的体积光（雾状光束）的全局倍率。每帧即时生效，无需重载光影包。1.0 = 光影包的默认值，0 = 关闭 IRLite 体积光。在此选项出现之前打过补丁的光影包仍使用其编译时的体积光强度设置。");
        LANG_KEY_MAP.put("irlights.config.presets.max_shader_lights-comment",
            "每帧上传到着色器的光源数量上限。注入的着色器会对每个像素遍历所有已上传的光源，因此光源越少越省性能。当范围内的光源超过此上限时，最近的（优先级最高的）光源生效；其余光源会被跳过照明，但仍会投射和接收阴影并保持注册状态。0 = 无限制（默认值）。画质预设从不改动此项。");
        LANG_KEY_MAP.put("irlights.config.presets.show_guides-comment",
            "为世界中已放置的点光源与聚光灯形态绘制线框操控器。");

        // —— 光照分类设置项标签（新版新增） ——
        LANG_KEY_MAP.put("irlights.config.lighting.diffuse",        "漫反射光照");
        LANG_KEY_MAP.put("irlights.config.lighting.intensity",      "强度");
        LANG_KEY_MAP.put("irlights.config.lighting.specular",       "高光");
        LANG_KEY_MAP.put("irlights.config.lighting.specular_intensity", "高光强度");
        LANG_KEY_MAP.put("irlights.config.lighting.toon",           "卡通着色");
        LANG_KEY_MAP.put("irlights.config.lighting.toon_bands",     "色阶数");
        LANG_KEY_MAP.put("irlights.config.lighting.toon_smooth",    "色阶过渡平滑");

        // —— 光照分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.lighting.diffuse-comment",
            "IRLights 灯光在表面上的漫反射光。关闭后只剩高光（如开启）、轮廓与体积光束。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.lighting.intensity-comment",
            "IRLights 灯光在表面上一切效果的亮度倍率：漫反射光、高光与轮廓边缘。体积光束有独立的体积光强度。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.lighting.specular-comment",
            "IRLights 灯光产生的闪亮高光，使用光影包自身的镜面反射模型。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.lighting.specular_intensity-comment",
            "仅高光部分的倍率，叠加在强度之上。0 会隐藏高光但仍计算开销——请改用「高光」开关来跳过计算。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.lighting.toon-comment",
            "将 IRLights 灯光的漫反射量化为平直色阶（赛璐珞渲染）。默认关闭。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.lighting.toon_bands-comment",
            "卡通着色开启时的亮度色阶数量。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.lighting.toon_smooth-comment",
            "两个卡通色阶之间边缘的柔和程度。0 = 硬切。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");

        // —— 体积光分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_steps",            "步进步数");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_max_dist",         "最大距离");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadows_live",     "光束阴影");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadow_stride",    "阴影采样间隔");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_boost",        "端头辉光");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_radius",       "端头半径");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_live",       "光束噪声");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_amount",     "噪声强度");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_scale",      "噪声缩放");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_speed",      "飘移速度");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_morph",      "噪声形变");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_stride",     "噪声采样间隔");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_dither_temporal",  "抖动时间轮换");

        // —— 体积光分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_steps-comment",
            "体积光通道中每盏灯的光线步进步数。越高越平滑，但性能开销越大——被光束覆盖的每个像素都要承担全部步数。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效，旧补丁仍使用编译时的设置。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_max_dist-comment",
            "体积光光线的最大距离（格）。光线越长，天空像素的开销越大。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadows_live-comment",
            "IRLite 灯光体积光阴影的运行时开关。每帧即时生效，无需重载光影包。关闭后跳过所有体积光阴影采样（光束将穿过几何体）。仅对使用运行时体积光开关的光影包生效；旧补丁会忽略此项。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadow_stride-comment",
            "每隔 N 个步进步数采样一次 IRLights 阴影贴图，中间复用该结果。设为 2 大约能将体积光阴影开销减半，代价是阴影略柔和；1 = 每步都采样。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_boost-comment",
            "光源本体附近的额外体积光辉光。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_radius-comment",
            "光源周围额外辉光的半径（格）。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_live-comment",
            "IRLite 体积光中动态噪声的运行时开关。每帧即时生效，无需重载光影包。关闭后跳过噪声采样，渲染均匀光束。仅对使用运行时体积光开关的光影包生效；旧补丁会忽略此项。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_amount-comment",
            "动态噪声对光束的调制强度。较低时光束基本均匀，1 则完全碎化为团雾；平均亮度保持不变。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_scale-comment",
            "噪声团雾的大致尺寸（格）。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_speed-comment",
            "噪声团雾在光束中飘移的速度，如同空气中的尘埃。0 = 静止。按 0.25 的步进对齐——中间值会在着色器的风循环重置时产生跳变。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_morph-comment",
            "噪声团雾形变为新形状的速度（叠加在飘移之上）。0（默认）= 经典的仅飘移雾；开启后每次刷新需额外一次噪声采样——实测比噪声本身更耗性能，因此没有任何光束风格预设会开启它。按 0.25 的步进对齐——中间值会在着色器的形变循环重置时产生跳变。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_stride-comment",
            "每隔 N 个步进步数采样一次密度噪声，中间复用该值。步数较高时更省性能；但可能沿光束产生条纹。1 = 每步都采样。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_dither_temporal-comment",
            "每帧轮换蓝噪声抖动图案，使噪点随时间平均化。如果录制的画面中移动的灯出现闪烁或颗粒沸腾感，且未开启时间抗锯齿，请在拍摄该镜头时关闭此选项。每帧即时生效，无需重载光影包；仅对使用运行时体积光全局变量打过补丁的光影包生效。");

        // —— 阴影分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_quality",      "阴影质量");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_blocks",       "方块阴影");
        LANG_KEY_MAP.put("irlights.config.shadows.shadows_live",        "灯光阴影");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_partial_tile", "阴影部分更新");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_softness",     "柔和度");

        // —— 阴影分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_quality-comment",
            "阴影深度贴图的分辨率。越高越清晰，但占用更多显存（低约 40 MiB……极致约 2.5 GiB）。");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_blocks-comment",
            "让世界方块投射阴影：部分方块（台阶、楼梯、栅栏）按真实形状投影，镂空方块（树叶、栏杆、玻璃门）的透明像素不产生阴影。在逐灯缓存落地之前开销较大。");
        LANG_KEY_MAP.put("irlights.config.shadows.shadows_live-comment",
            "让 IRLights 灯光投射阴影。每帧即时生效，无需重载光影包。关闭此项（且光束阴影也关闭）后，模组会停止烘焙阴影贴图，因此能同时收回烘焙开销与显存占用，而不仅仅是屏幕上的阴影。这是日常使用的开关——光影包自身也有 IRLITE_SHADOWS 选项，但那是从编译后的着色器中移除阴影代码的兼容性逃生开关。仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_partial_tile-comment",
            "聚光灯附近移动主体的加速优化：每帧只重绘并重新过滤阴影贴图中该主体投影所在的矩形区域，而非整个分块。该矩形按碰撞箱计算，因此如果形态的视觉尺寸远大于碰撞箱，阴影边缘可能在矩形边界处被裁切，并随主体移动而偏移。拍摄超大形态时请关闭此项；代价是每个有移动主体的聚光灯每帧都要做一次整分块更新。");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_softness-comment",
            "光源的表观尺寸，决定阴影边缘随投影物距离增加而散开的速度——接触处保持锐利，远处阴影变柔和。0 = 所有边缘都锐利。设置了自身灯珠尺寸的灯光会忽略此项并使用灯珠尺寸。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");

        // —— 轮廓分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.outline.outline",               "轮廓");
        LANG_KEY_MAP.put("irlights.config.outline.outline_target",        "作用于");
        LANG_KEY_MAP.put("irlights.config.outline.outline_strength",      "强度");
        LANG_KEY_MAP.put("irlights.config.outline.outline_pixel_size",   "粗细");
        LANG_KEY_MAP.put("irlights.config.outline.outline_fresnel_power", "菲涅尔衰减");
        LANG_KEY_MAP.put("irlights.config.outline.outline_back",          "背光轮廓");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front",         "正面高光");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front_strength","正面强度");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow",          "内部辉光");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow_strength", "辉光强度");

        // —— 轮廓分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.outline.outline-comment",
            "在被 IRLights 灯光照亮的表面上绘制边缘轮廓：深度剪影加菲涅尔光晕，颜色取自灯光颜色并随其衰减淡出。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_target-comment",
            "哪些表面会获得轮廓，由 gbuffer 材质遮罩决定：全部、仅实体（生物、玩家、模型方块）或仅方块。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_strength-comment",
            "边缘轮廓的整体亮度。0 = 不可见。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_pixel_size-comment",
            "深度边缘检测器的采样偏移（像素）。越大读取的剪影越宽——更粗但也更糙，并且会开始捕捉到并非真正剪影的边缘。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_fresnel_power-comment",
            "轮廓贴紧掠射角的程度。越高 = 只在剪影处的窄带；越低 = 辉光散布到整个表面。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_back-comment",
            "背向灯光的表面上的轮廓强度——经典的逆光剪影。0 关闭；设计上没有独立开关。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front-comment",
            "在朝向灯光的表面上叠加轮廓，位于背光轮廓之上。默认关闭。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front_strength-comment",
            "正面高光轮廓的强度。仅在正面高光开启时使用——关闭该开关会保留此值，供下次使用。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow-comment",
            "在剪影内部添加柔和的菲涅尔光晕，供光影包的泛光拾取。默认关闭。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow_strength-comment",
            "内部辉光光晕的强度。仅在内部辉光开启时使用——关闭该开关会保留此值，供下次使用。每帧即时生效，无需重载光影包；仅对使用运行时全局变量打过补丁的光影包生效。");
    }

    /**
     * 根据硬编码英文文本查找中文翻译（供 StringKeyMixin 使用）。
     * @return 中文文字，或 null（表示不需要翻译）
     */
    public static String getChinese(String englishText) {
        if (englishText == null) return null;
        String exact = STRING_MAP.get(englishText);
        if (exact != null) {
            return exact;
        }

        // —— Patcher 动态拼接文本 ——
        if (englishText.startsWith("This patch is for the ") && englishText.endsWith(" shaderpack. Select it above.")) {
            String target = englishText.substring(22, englishText.length() - 29);
            return "此补丁适用于光影包 " + target + "，请在上方选择。";
        }
        if (englishText.startsWith("This patch is for a different shaderpack (") && englishText.endsWith(").")) {
            String target = englishText.substring(42, englishText.length() - 2);
            return "此补丁适用于其他光影包（" + target + "）。";
        }
        if (englishText.startsWith("This patch is made for the ") && englishText.endsWith(" shaderpack.")) {
            String target = englishText.substring(27, englishText.length() - 12);
            return "此补丁是为光影包 " + target + " 制作的。";
        }
        if (englishText.startsWith("Done! Pack \"") && englishText.endsWith("\" created. Select it in Iris settings.")) {
            String target = englishText.substring(12, englishText.length() - 38);
            return "完成！已创建光影包“" + target + "”，请在 Iris 设置中选择它。";
        }

        // —— LightReplayWidgets 动态状态文本 ——
        if (englishText.startsWith("Global outline: ") && englishText.endsWith(". Keyframes activate own settings.")) {
            String onOff = englishText.substring(16, englishText.length() - 35);
            String cn = "ON".equals(onOff) ? "开" : "关";
            return "全局轮廓：" + cn + "。关键帧可激活自定义设置。";
        }
        if (englishText.equals("Filter ON: empty list = nobody.")) {
            return "筛选已开启：列表为空 = 不作用于任何对象。";
        }
        if (englishText.equals("Filter OFF: list ignored.")) {
            return "筛选已关闭：列表被忽略。";
        }
        if (englishText.startsWith("Default list: ")) {
            return "默认列表：" + englishText.substring(14);
        }

        return null;
    }

    /**
     * 根据 BBS L10n key 查找中文翻译（供 LangKeyMixin 使用）。
     * 仅覆盖 irlights 相关的 key，不干涉其他模组。
     * @return 中文文字，或 null（表示不需要翻译）
     */
    public static String getChineseForKey(String langKey) {
        return LANG_KEY_MAP.get(langKey);
    }
}
