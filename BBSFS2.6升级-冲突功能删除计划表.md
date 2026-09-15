# BBSFS 2.6 升级 · 冲突 / 重复功能删除计划表

> 对比对象：
> - 旧版：`libs/bbs-fs-2.5-master`（Java 1265 个文件，本项目当前编译依赖 `bbs-2.5.2`）
> - 新版：`libs/bbs-fs-2.6-1.20.1`（Java 1565 个文件；新增 365 个文件、删除 55 个、改动 531 个公共文件）
> - 本项目：`src/main/java/gbeic/bbsplusplus`（518 个 Java 文件，其中 Mixin 241 个）
>
> 本表**只做计划，不改任何代码**。结论按处置方式分三类：
> - **A 类＝建议整块删除**：2.6 已原生实现，且功能基本一一对应，留着只会重复或直接撞车。
> - **B 类＝部分重叠，需合并裁剪**：上游吸收了一部分，删掉被覆盖的，自研差异化部分要基于新代码重做。
> - **C 类＝编译/启动级硬冲突**：不是删功能，但不迁移就编译不过 / 起不来，绕不开。
>
> 文末另附「确认保留清单」（2.6 没有、别误删）与「2.6 纯新增能力」（升级后白送）。

---

## 一、结论速览

| 编号 | 本项目功能 | 2.6 原生对应 | 类型 | 处置 |
| --- | --- | --- | --- | --- |
| A1 | 结构伪装 + 结构棒整套 | `bbs:structure` 形态 + 结构魔杖 StructureWand 整套 | **注册 ID 直接撞车** | 整块删除，用原生 |
| A2 | 视频伪装 VideoBillboard（依赖外部 MediaPlayer） | `bbs:video` VideoForm + 内置 ffmpeg 解码 + 视频剪辑 | 功能重复 | 整块删除，用原生 |
| A3 | 逐材质 PBR 纹理分级 + pbr 关键帧 | FormMaterial 五通道 + MaterialPropTrack + IrisPbrConstLoader | 功能重复 | 删「逐材质」部分；**逐骨骼 PBR 保留** |
| A4 | 姿势骨骼树形列表 | UIBoneTreeList（层级缩进/树枝线/搜索平铺） | 功能重复 | 只删树形分支 |
| A5 | 性能：VAO 分帧烘焙、Dashboard 空闲预热 | ModelSetupQueue、DashboardWarmup | 功能重复 | 删被覆盖的两项 |
| B1 | 光影曲线 shadercurves（含日月偏角） | 重写 ShaderCurves + ShaderSunRotation + 同款 Iris Mixin | **同目标双 Mixin 冲突** | 以原生为准合并 |
| B2 | 全新伪装界面（列表/网格/分类文件夹） | FormGridLayout + CategoryTree + CategoryPreferences | 部分吸收 | 删网格/分类，外壳按需重做 |
| B3 | 纹理管理器网格 + 缩略图 | 全新 UITextureBrowser / Grid / FolderTree | 部分吸收 | 删被覆盖部分 |
| B4 | 关键帧轨道样式硬编码 | RegisterTrackStylesEvent + TrackStyle 官方 API | 实现路径被取代 | 改用事件注册 |
| B5 | 逐组/逐骨骼指定纹理 | MaterialTextureTrack（逐材质硬切换） | 部分重叠 | 逐组评估替换，逐骨骼保留 |
| B6 | 全新影片库界面 | 旧面板类被删，改 Landing 落地页 | 底层类已删除 | 旧实现删除后重做 |
| C1 | 旧 Addon/事件 API（`bbs_mod.events.*`） | 包迁移到 `bbs_mod.api.*` | 编译不过 | 5 个文件改包/改入口 |
| C2 | 目标类已被删的 Mixin / 支撑类 | 新轨道系统、新关键帧 UI 等 | 起不来 | 6 处失效点重做 |
| C3 | IK/约束/物理 面板与预设 Mixin | 数据模型从 client 搬到 main 并并入 FormBone | 目标大改 | 重定向，功能保留 |
| C4 | 关键帧编辑器相关 Mixin（约 20+ 个） | UIKeyframeElement/Group 删除，改 Sheet/DopeSheet | 目标大改 | 逐个重定向 |
| C5 | 仪表盘 / 迷你窗相关 Mixin | 仪表盘重构（Landing/Tabs/TopBar） | 目标大改 | 适配 |
| C6 | Gizmo 改版相关 | Gizmo 框架重写 + 新增 Gizmo* 工具类 | 目标大改 | 重接，功能保留 |

---

## 二、A 类：建议整块删除（2.6 已完整实现）

### A1 结构伪装 + 结构棒 —— 注册 ID 直接撞车（优先级最高）

**冲突本质**：本项目 `mixin/BBSModMixin` 注册 `Link.bbs("structure")`；2.6 `BBSMod` 也注册 `Link.bbs("structure")`（原生 StructureForm）。**同一个形态 ID 注册两次**，升级后必然互相覆盖/报错。且 2.6 自带一整套结构工具，功能是本项目的超集。

| 本项目文件（删除） | 作用 | 2.6 原生替代 |
| --- | --- | --- |
| `forms/StructureForm.java` | `bbs:structure` 形态（structure_file/color/biome_id/emit_light/light_intensity） | `forms/forms/StructureForm.java`（structure/biome/color/origin 锚点） |
| `client/renderer/StructureFormRenderer.java` | 结构渲染 | `forms/renderers/StructureFormRenderer.java`（+ `forms/structure/*` 烘焙/光照/选区共 14 个类） |
| `client/ui/forms/editors/forms/UIStructureForm.java` | 形态编辑 UI | `ui/forms/editors/forms/UIStructureForm.java` + `panels/UIStructureFormPanel.java` |
| `structure/StructureSaver.java` | 选区导出 nbt | `utils/StructureSaver.java`（另含 `clear()` 影片剪切，功能更全） |
| `structure/StructureStickItem.java`、`StructureStickRegistry.java` | 结构棒物品/注册 | 原生物品 `bbs.structure_wand` + `forms/structure/StructureWand.java`（712 行，两角点/滚轮推拉/Alt 保存） |
| `client/structure/StructureStickSelection.java`、`StructureStickSaveNameScreen.java`、`StructureStickTooltip.java` | 客户端框选/命名/提示 | StructureWand + `ui/structures/UIStructureSaveMenu(Panel).java` |
| `network/StructureStickNetworking.java` | 结构棒网络包 | 原生网络 + `/bbs structures save` 命令 |
| `src/versions/1.20.1/.../util/NbtCompat.java`、`src/versions/1.20.4/.../util/NbtCompat.java` | 仅结构功能在用（已核实） | 原生 NbtIo |
| 注册代码 | `BBSModMixin` 第 58–68 行、`BBSPlusPlusMod`、`BBSPlusPlusModClient` 中结构相关注册行 | — |
| 资源 | `assets/bbs/models/item/structure_stick.json`、`textures/item/structure_stick.png`、`assets/bbspp/sounds/structure_stick_*.ogg` 与 `sounds.json` 条目、相关 lang 键 | 原生资源 |

**注意**：
- `client/structure/VFXDestructionWandSelection/Tooltip.java` 是 **VFX「破坏魔杖」**联动，**不是**结构棒，删除前需单独甄别，大概率保留。
- 老工程数据要迁移：`structure_file → structure`、`biome_id → biome`，`emit_light/light_intensity` 原生无对应（2.6 有 `data/migration` 迁移框架可借用）。

---

### A2 视频伪装 VideoBillboard —— 原生已内置解码，不再需要外部 MediaPlayer

**冲突本质**：目标完全相同（把视频当广告牌形态渲染、能被时间轴驱动）。本项目靠反射桥接外部 `MediaPlayer-BBS`；2.6 直接内置 ffmpeg 进程解码，还多出「视频/图片时间线剪辑」。本项目注册 ID 是 `bbs:video_billboard`（与原生 `bbs:video` 不同名，不会注册崩溃，但**形态列表里会出现两个重复的视频形态**）。

| 本项目文件（删除） | 2.6 原生替代 |
| --- | --- |
| `forms/VideoBillboardForm.java` | `forms/forms/VideoForm.java`（继承 BillboardForm，自带裁剪/染色/广告牌/着色） |
| `client/renderer/VideoBillboardFormRenderer.java` | `forms/renderers/VideoFormRenderer.java` |
| `client/renderer/VideoBackendBridge.java`（外部 MediaPlayer 反射桥） | `video/VideoManager.java` + `VideoPlayer.java`（内置 ffmpeg，按播放者分配解码器） |
| `client/renderer/VideoTimelineState.java`、`VideoForeignRenderPassState.java` | 原生以载体年龄为播放时钟，天然逐帧精确 |
| `client/ui/forms/editors/forms/UIVideoBillboardForm.java` | `ui/forms/editors/forms/UIVideoForm.java` + `panels/UIVideoFormPanel.java` |
| `client/ui/utils/UIVideoPicker.java` | 原生 `UIVideoFormPanel` / 统一选择器 |
| `client/debug/VideoDebug.java` | — |
| `BBSModMixin` 第 70–86 行 `bbs:video_billboard` 注册、客户端渲染器/编辑器注册 | 原生 `bbs:video` 注册 |
| 待评估连带：`mixin/UIKeyframesVideoScrubbingMixin.java`、`mixin/client/BBSRenderingGetVideoFolderMixin.java` | 确认是否仅服务视频形态后一并清理 |

**不要误删（这些是「视频导出/录制」，与视频形态无关，保留但 C 类里要重定向）**：
`client/settings/VideoSettingsPresets.java`、`UIVideoSettingsPresetsOverlayPanel.java`、`mixin/UISettingsVideoPresetMixin.java`、`mixin/VideoRecorderMixin.java`。

**注意**：本项目独有字段 width/height/speed/paused/restart/loopStart/loopEnd/outOfRange/keepAspectRatio，需对照原生（BillboardForm 几何 + `loop`/`videoOffset`）做老存档迁移。

---

### A3 逐材质 PBR 纹理分级 —— 原生材质系统同参覆盖（逐骨骼部分保留）

**冲突本质**：本项目「PBR 纹理分级」逐材质写 LabPBR 五通道并自研 pbr 关键帧工厂；2.6 原生 `FormMaterial` 提供**完全相同的五个 LabPBR 滑条**，并有时间线材质轨道与 Iris 常量烘焙，链路完整。

| 本项目文件（删除/替换） | 2.6 原生替代 |
| --- | --- |
| `pbr/PBRData.java`、`PBRChannel.java`（sR/sG/sB/sA/n + emission） | `forms/forms/utils/FormMaterial.java`：smoothness / metallic / sss / pixelEmission / relief（一一对应） |
| `pbr/PBRKeyframeFactory.java`、`pbr/ui/UIPBRKeyframeFactory.java`、`BBSFSloveCML` 中 `"pbr"` 工厂注册 | `film/replays/tracks/behaviours/MaterialPropTrack.java`（材质属性关键帧） |
| `pbr/render/PBRTextureContext.java`、`PBRTextureModifier.java`（材质部分） | `forms/renderers/utils/FormPbr.java` + `utils/iris/IrisPbrConstLoader/Wrapper.java` |
| `api/PBRModelFormAccess.java`、`mixin/FormPropertiesPBRMixin.java`、`mixin/client/IrisTextureWrapperPBRMixin.java`、`mixin/client/PBRUIReplaysEditorMixin.java` | 原生材质面板 `ui/.../panels/UIMaterialFormPanel.java`、ValueMaterials |

**保留（2.6 没有，原生 FormBone 不含 PBR 字段，已核实）**：
`pbr/BonePBRData.java`、`BonePBRKeyframeFactory.java`、`pbr/ui/UIBonePBRKeyframeFactory.java`、`pbr/render/BonePBRContext.java`（**逐骨骼 PBR**）。

**注意**：老数据键 `pbr_s_r/g/b/a`、`pbr_n`、`emission_multiplier` → 原生材质通道需要迁移。

---

### A4 姿势骨骼树形列表 —— 原生 UIBoneTreeList 一模一样

- 本项目：`mixin/UIPoseEditorMixin.java`（树形分支）、`api/BoneCollapseHandler.java`、`mixin/client/UIModelPoseEditorAccessor.java`（相关部分）、设置项「姿势骨骼树形列表」。
- 2.6 原生：`ui/utils/bones/UIBoneTreeList.java`——按父子层级缩进、绘制 outliner 树枝连接线、搜索时平铺，与本项目功能描述逐字对应（本项目原本就移植自 FS2.5）。
- **处置：只删树形渲染分支**。同一个 `UIPoseEditorMixin` 里还有「骨骼参数刷 / 跳过当前帧关键值 / 改动骨骼橙标 / 多选粘贴」等**原生没有**的自研逻辑，切勿整文件删。

---

### A5 两项性能优化 —— 上游用同样思路做了官方版

| 本项目 | 2.6 原生 | 说明 |
| --- | --- | --- |
| `performance/ProgressiveVAOBaker.java`（把 `ModelInstance.setup()` 的 VAO 烘焙拆到多帧，未烘焙走 CPU 兜底） | `cubic/model/ModelSetupQueue.java`（按每帧 4ms 预算分批烘焙、CPU 兜底，机理一致） | 删除自研，用原生 |
| 「Dashboard 空闲预热」（在 `performance/PerformanceOptimizationManager.java` 内） | `ui/dashboard/DashboardWarmup.java`（世界加载时分步预建仪表盘） | 删除该子项 |

`PerformanceOptimizationManager` 里还有「调色板缓存 / Gizmo 静止跳过重绘 / 渲染距离 / 全局开关」，原生未全做，**逐项核对后只删被覆盖的两项**。

---

## 三、B 类：部分重叠，需合并裁剪

### B1 光影曲线 shadercurves —— 存在同目标双 Mixin 冲突（建议优先处理）

- 本项目（11 个文件 + 设置项）：`mixin/shadercurves/`（CelestialUniforms、CenterDepthSampler、CustomUniformsSetupAccessor、IrisRenderingPipeline、ShaderCurves、ShadowRenderer 共 6 个）、`client/compat/iris/ShaderCurveState.java`、`SafeShaderLanguageMap.java`、`client/compat/shadercurves/ShaderCurveDebug.java`、`client/WorldFilmShaderCurveState.java`、`client/ui/curves/UIShaderCurvePickerOverlayPanel.java`、`compat/ShaderCurvesMixinPlugin.java`，以及「新光影曲线选择界面 / 世界播放应用光影曲线 / 光影控制按钮 / 日月偏角」。
- 2.6：`utils/iris/ShaderCurves.java` 重写（brightness、sun_rotation、sun_horizontal_rotation、weather、任意自定义 uniform）；新增 `ShaderSunRotation.java`（吸收「日月偏角」）；**新增同款 Iris Mixin** `mixin/client/iris/CelestialUniformsMixin/HandRendererMixin/ShadowMatricesMixin`。
- **硬冲突点**：本项目 `shadercurves/CelestialUniformsMixin` 与 2.6 自带 `CelestialUniformsMixin` **同时注入 Iris 的同一个类**，必须删掉一个。
- **处置**：以原生 ShaderCurves/ShaderSunRotation 为准，删除重复注入与日月偏角自研；仅保留原生没有的「按光影分类的选择 UI、世界播放开关、控制按钮」外壳，内部改为调用原生。

### B2 全新伪装界面（列表/网格/分类）—— 网格与文件夹分类被原生吸收

- 本项目：`ui/morphing/`（11 个文件）+ `mixin/UIFormCategoryMixin/UIFormListMixin/UIFormPaletteMixin`；设置「全新伪装界面布局」、列表/网格切换、Ctrl+滚轮缩放、拖入分类文件夹。
- 2.6 原生：`ui/forms/FormGridLayout.java`（3:4 网格、40–140 缩放）、`utils/categories/*`（Category/CategoryTree/CategoryPath 文件夹分类）、`forms/CategoryPreferences.java`（偏好记忆）、`FormCellRenderer.java`。
- **处置**：网格模式、图标缩放、文件夹分类删除改用原生；自研的侧边栏 / 首页 / 双栏布局 / Blockbench 路径行 / 模型预览若要保留，需基于重构后的 `UIFormList/UIFormPalette` 重写（属 C4/C5 工作量）。

### B3 纹理管理器「网格模式 + 缩略图」—— 原生新增独立纹理浏览器

- 本项目：`mixin/UITextureManagerPanelMixin.java`、`mixin/UITexturePickerMixin.java`、`util/TextureThumbnailManager.java`。
- 2.6 原生：新增 `ui/textures/` 整包——`UITextureBrowser`（浏览器）、`UITextureGrid`（网格）、`UIFolderTree`（文件夹树）、`TextureSort`（排序）、`TexturePins`（收藏）、`UIBreadcrumbs`、`TextureCellRenderer`。
- **处置**：网格/缩略图被原生吸收，可删；注意旧 `UITextureManagerPanel`（像素绘制器）2.6 仍在，针对它的其余混入（`TextureManagerMixin/TextureManagerReloadMixin` 属重载修复）要重新判断是否还需要。

### B4 关键帧轨道样式 —— 官方给了正规注册入口

- 本项目：`client/KeyframeTrackStyle.java`、`api/KeyframeTrackExtensionRegistry.java`、`BBSPlusPlusMod#registerKeyframeTrackExtensions` 等硬编码方式。
- 2.6：`api/client/events/RegisterTrackStylesEvent.java`、`film/replays/tracks/TrackStyle.java`、`ValueKeyframeStyle`、`UIKeyframeStyleOverlayPanel/UITrackStyleOverlayPanel`。
- **处置**：不是功能重复，而是实现路径被官方 API 取代；建议改为事件注册，规避关键帧 UI 重构带来的 Mixin 冲突。**轨道中文名汉化 `KeyframeLocalizer` 不受影响，保留。**

### B5 逐组 / 逐骨骼指定纹理 —— 与原生材质纹理轨道部分重叠

- 本项目：`api/GroupTextureHolder.java`、`api/BoneTextureHolder.java`、`GroupTextureGradeHolder.java`、`PoseTransformMixin` 及 Model/Group 消费链。
- 2.6：`MaterialTextureTrack.java` + `ModelForm.materialTextureOverrides`（**逐材质**纹理切换，且明确「硬切、不做混合」）。
- **处置**：逐组（材质）静态指定可评估改用原生；**逐骨骼（Pose 级）纹理原生没有，保留**；**纹理补间（颜色插值/像素溶解/消散，需要混合）原生明确不混合，全部保留**。

### B6 全新影片库界面 —— 所依赖的底层类已被删除

- 本项目（9 个文件）：`client/ui/film/` 下 `FilmLibraryDefaultLocation`、`IFilmLibraryLayoutToggle`、`IFilmLibraryPathList`、`IFilmLibrarySearchBox`、`UIAllFilmsNavItem`、`UIFilmLibraryFolderTree`，以及 `mixin/UIDataPathListFilmLibraryMixin`、`UIFilmSelectionPanelLibraryLayoutMixin`、`UITextboxFilmLibrarySearchMixin`；设置「全新影片库界面」。
- 2.6：`ui/film/UIFilmSelectionPanel.java`、`ui/dashboard/panels/UISelectionScreen.java` **已删除**，换成 `ui/dashboard/panels/landing/*`（UILandingScreen、UIRecentDataList、UITabList、UIPanelTopBar）。
- **处置**：旧实现无法平移（目标类没了，`UIFilmSelectionPanelLibraryLayoutMixin` 已属失效 Mixin，见 C2），只能删除后基于新落地页重做。

---

## 四、C 类：编译 / 启动级硬冲突（必须迁移，不是删功能）

### C1 旧 Addon / 事件 API 包被整体移除（5 个文件）

- 旧包 `mchorse.bbs_mod.events.*`（含 `BBSAddonMod/Subscribe/Subscription/EventBus/ModelBlockEntityUpdateCallback` 与 `events.register.*`）在 2.6 **全部删除**，迁移到 `mchorse.bbs_mod.api.*`（`api/BBSAddonMod`、`api/Subscribe`、`api/EventBus`、`api/events/*`、`api/compat/VanillaAccess`）。
- 受影响文件：`BBSFSloveCMLAddon.java`、`BBSFSloveCMLClientAddon.java`、`BBSPlusPlusMod.java`、`client/renderer/AAAParticleFormRenderer.java`、`client/renderer/ItemSprayGlobalSystem.java`。
- 同时按 2.6 `ADDONS.md` 调整 `fabric.mod.json` 入口为 `bbs-addon` / `bbs-client-addon`，并改走 `RegisterFormsEvent/RegisterCameraClipsEvent/…` 等新事件（本项目现在靠「RegisterSettingsEvent 里顺手注册」的取巧写法在新 API 下应拆分到对应事件）。

### C2 目标类已被删除的 Mixin / 支撑类（不改直接起不来）

| 本项目文件 | 已消失的 2.5 类 | 2.6 去向 |
| --- | --- | --- |
| `mixin/client/UIAnimationStatePoseInsertMixin.java`、`UIKeyframesExportMenuMixin.java`、`UIReplaysEditorUtilsMixin.java`，以及 `ui/film/replays/overlays/UIExportAnimationOverlayPanel.java`、`ui/forms/AnimationStateEditorSupport.java` | `film/replays/PerLimbService.java`（另 `FormControlKeys` 也删） | 新轨道体系 `film/replays/tracks/*`（TrackBehaviour / behaviours 各 Track） |
| `mixin/UIFilmSelectionPanelLibraryLayoutMixin.java` | `UISelectionScreen`、`UIFilmSelectionPanel` | 见 B6 Landing |
| `mixin/client/UIKeyframeDopeSheetMixin.java` | `UIKeyframeElement.java`（UIKeyframeGroup 也删） | `UIKeyframeSheet` / `UIKeyframeDopeSheet` |
| `mixin/client/CubicAxisRendererMixin.java` | `cubic/render/CubicAxisRenderer.java` | 新 Gizmo/渲染体系评估是否还需要 |

### C3 IK / 约束 / 物理 / 链条 数据模型大搬迁（功能保留，目标重定向）

- 2.5 的 client 类 `cubic/ik/ModelIKConfig(IO)`、`cubic/constraints/ModelConstraintsConfig(IO)` 删除；2.6 在 **main** 端重建为 `cubic/ik/(IKControl/IKControls/BoneIKIO/JointDoF)`、`cubic/constraints/(BoneConstraint/BoneConstraintsIO)`、`cubic/physics/(PhysicsControl(s)/BonePhysicsIO)`、`cubic/chains/(ChainControl(s))`，并统一并入 `forms/forms/utils/FormBone.java`、`settings/values/core/ValueBoneIK/BoneConstraint/BonePhysics/JointDoF`、`tracks/behaviours/*Track`，另新增 IK 烘焙 `film/IKBake`。
- 本项目 `mixin/UIModelIKFormPanelMixin`、`UIModelConstraintsFormPanelMixin`、`UIModelPhysicsFormPanelMixin`、预设自动保存（`client/ui/presets/AutoSavePresetState`）等需按新面板/新值类型重接，**功能本身保留**。

### C4 关键帧编辑器 UI 重构（工作量大头，约 20+ 个 Mixin）

- 删除：`UIKeyframeElement`、`UIKeyframeGroup`、`UIVector3fKeyframeFactory`、`UIVector3KeyframeGraph`、`TrackpadRecorder` 等；新增 `UIKeyframeSheet`、`UIKeyframeDopeSheet`、`UITimelineCanvas/Panel`、`UIKeyframeEditor`、`UIUndoKeys` 等。
- 本项目大量关键帧 Mixin（UIClips*、UIKeyframeGraph*、UIKeyframes*、UIKeyframeSheet*、DopeSheet*、KeyframeSegment/Channel/Keyframe* 等）的目标方法签名都会变，需要在 C1 之后逐个编译、重定向。属适配工作，不删功能。

### C5 仪表盘 / 面板体系重构

- 删除 `UISidebarDashboardPanel`、`UISelectionScreen`、`UIDebugPanel`、`utils/UIGraphCanvas/Panel`；新增 `dashboard/panels/landing|tabs|bar/*`。
- 本项目 `mixin/UIDashboardMixin`、迷你窗体系（`client/UIDashboardPanelMiniWindowMixin`、`UIDockLayoutMiniWindowMixin`、`UIFilmPanelMiniWindowMixin`、`ui/miniwindow/*`）需按新面板结构适配。

### C6 Gizmo 交互框架重写

- 2.6 `ui/utils/Gizmo.java`、`GizmoDrag/GizmoInteraction` 大改，新增 `GizmoLens/GizmoPie/GizmoRings/GizmoJacobian`、`ui/film/UIPlacementGizmo.java`。
- 本项目「Blockbench 风味 Gizmo」：`util/GizmoModeController.java`、`mixin/GizmoBlockbenchMixin.java`、`mixin/client/GizmoPivotMixin.java`、`mixin/UIModelBlockPanelGizmoThrottleMixin.java`、`api/GizmoPivotTarget/TransformPivotEditor` 需重新挂接，**功能保留**。

### 适配体量参考

本项目 Mixin 对 BBS 类的引用中：指向「2.6 未改动类」的 306 处、指向「有改动类」的 818 处。C2 是已确定的失效点，其余随 C3–C6 在编译期逐个过。

---

## 五、确认保留清单（2.6 无对应，切勿误删）

- **流体模拟**：`forms/FluidForm`、`simulation/*`、渲染器/UI（2.6 无流体）。
- **AAA 粒子**：`forms/AAAParticleForm`、`client/renderer/AAAParticleFormRenderer`、指令模块等（依赖外部 aaa_particles）。
- **粒子 Plus**：变形 Morph、碰撞外观、碰撞染色、收藏浏览器、附加渲染纹理、Additive 材质（`particles/*`、相关 UI/Mixin）。
- **物品喷射**：`forms/ItemSprayForm` 与 `client/renderer/ItemSpray*` 整套。
- **附魔光效**：逐骨骼 glint（`api/GlintHolder/GroupGlintHolder` 等，2.6 仅有原版盔甲 glint）。
- **纹理补间**：颜色插值 / 像素溶解 / 消散 / 3D 扩散起点拾取 / 旧数据迁移（2.6 纹理轨道「只硬切不混合」，不构成替代）。
- **导出**：Premiere XMEML、SRT、音频分轨/字幕导出（`premiere/*`、`export/AudioSubtitleExporter`、`premiere/audio/IndividualAudioExporter`）。
- **三种剪辑**：快捷栏 / 电影 / 重播剪辑（2.6 新增的 image/video 剪辑是新类型，不冲突）；回放时间控制（变速/反向/传播）、剪辑可见性、Hotbar 渲染。
- **动作叠加 actions_overlay、Additive 动画、Molang 变量共享**（2.6 的 poseOverlay/additiveColor 不是一回事）。
- **动画态编辑器增强、迷你窗口停靠体系**（重接后保留）。
- **Pose 增强**：骨骼参数刷、跳过当前帧关键值、改动骨骼标识、多选粘贴（A4 只删其中的树形列表）。
- **逐骨骼 PBR、逐骨骼纹理**（见 A3/B5）。
- **UV 变换/缩放**：BillboardFormUVScale、ModelFormUVTransform、ExtrudedFormUVTransform 及编辑器/关键帧工厂（2.6 BillboardForm 字段与 2.5 相同，仍无 UV 缩放）。
- **其余**：自定义快捷键、时间线滚轮方向/负数帧限制/BBS 专用剪贴板、输入法修复、IRLights/VFX 汉化与增强、音频输出设备监听、XRay/裁剪配置、`/bbsplusplus` 命令、各类原版 BUG 修复与中文汉化。

---

## 六、2.6 纯新增能力（本项目没有，升级后直接白送，无需再自研）

1. **视频/图片**：VideoForm 形态、VideoClip/ImageClip 时间线剪辑、内置 ffmpeg、GIF 帧（GifFrames）。
2. **JEM/CEM 原版生物骨骼**：`cubic/jem/*` 解析/动画、`forms/renderers/mob/*` 生物骨骼 rig、玩家皮肤（PlayerSkins）、JemModelLoader。
3. **结构整套**：StructureForm/StructureWand/StructureManager/StructureWorld/烘焙光照（即 A1 的原生侧）。
4. **逐材质系统**：FormMaterial/ValueMaterials/FormBone、材质属性与纹理轨道、LabPBR 常量烘焙。
5. **IK/约束/物理/链条新模型 + IK 烘焙**（IKBake、UIBakeIKOverlayPanel）。
6. **影片标记 FilmMarkers、演员相机 ActorCamera、动作路径钉 MotionPathPin、录制控制器 FilmRecordingController、胶片锚点 AnchorRebase/Placement**。
7. **全新关键帧轨道体系** TrackBehaviour/TrackCatalog、**轨道样式 API**、关键帧样式。
8. **纹理浏览器**、**模型编辑器大改版**（骨骼/组/焊接列表、几何编辑、ModelEditUndo）。
9. **仪表盘落地页 / 标签页 / 顶栏 / 新手引导 Tour / 欢迎向导 / 字体管理器**。
10. **基础设施**：Addon API 与事件总线、数据迁移框架 data/migration、稳定 ID、分类树 CategoryTree、Oklab 颜色、BBSProfiler、FramebufferPool、WeldGeometryCache 等。

---

## 七、建议执行顺序（仅计划，待你确认后再动手）

1. **第 0 步**：编译依赖切到 2.6 jar；按 C1 改 Addon 入口与事件 API，让工程先能解析符号。
2. **第 1 步**：删 A 类整块功能（A1 结构 → A2 视频 → A3 逐材质 PBR → A4 树形骨骼 → A5 两项性能），并同步编写老存档字段迁移。
3. **第 2 步**：清掉 C2 失效 Mixin/支撑类，达到「能编译」。
4. **第 3 步**：B 类逐块合并裁剪，**B1 光影曲线优先**（存在双 Mixin 注入冲突）。
5. **第 4 步**：按 C3→C6 系统批量重定向 Mixin（IK/物理 → 关键帧 UI → 仪表盘 → Gizmo）。
6. **第 5 步**：对照「保留清单」逐项回归，确认自研功能未被误删；统计最终删除/保留文件数。

> 风险提示：A1/A2/A3 都涉及**老工程存档数据字段改名**，删除自研代码前应先确定迁移方案（可挂 2.6 的 `data/migration`），避免用户旧影片/形态打开后丢数据。
