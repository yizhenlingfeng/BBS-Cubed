# BBS Cubed 适配 BBS FS 2.7 计划表

> 对比基线：`libs/bbs-fs-2.6-1.20.1`（旧）→ `libs/bbs-fs-2.7-1.20.1`（新）
> 目标 jar：
> - 1.20.1：`run/1.20.1/mods/bbs-2.7-1.20.1-zh_CN.jar`
> - 1.20.4：`run/1.20.4/mods/bbs-2.7-1.20.4.jar`
> 本文档仅为计划，未对任何源码 / 构建配置做修改。

---

## 0. 总体结论

| 项 | 数量 / 状态 |
|---|---|
| FS 2.7 新增类 | 50（含 4 个 FS 自身 mixin、若干新 API 事件、KeyframeLoop 循环系统） |
| FS 2.7 删除类 | 3（`cubic.weld.CubeFace`、`ui.model_editor.UIModelGroupList`、`ui.utils.GizmoLens`） |
| FS 2.7 删除资源 | 2（`textures/banners/bg4.png`、`shaders/post/interface_blur.json`） |
| FS 2.7 修改类 | 163（其中 80 个被 BBS-Cubed import、47 个是 BBS-Cubed Mixin 目标） |
| BBS-Cubed 直接引用被删类 | **0** ✅ |
| BBS-Cubed 直接引用被删资源 | **0** ✅ |
| 已确认被删的公开方法 | 1（`UIKeyframeSheet.getRowColor()`）——BBS-Cubed 未调用 ✅ |
| API 破坏性质 | 抽样 4 个核心类：**以增量新增为主**（Keyframe 加 `motionShift`、KeyframeChannel 加 loops、Form 加 overlay 等），未见方法签名删除 |
| 构建文件需改 | 2 处（`gradle.properties` 的两个 `bbs_jar_*`）+ 可选 1 处（`fabric.mod.json` 依赖下限） |

**判断**：本次升级属于"小版本增量 + 内部重构"，没有硬删除 BBS-Cubed 正在使用的 API；主要风险集中在 **47 个 Mixin 目标类的内部方法 / 字段 / 私有结构是否发生位移**，需要通过编译 + 运行时 Mixin 装载验证来暴露。

---

## 1. 构建文件同步（必做，改动极小）

| # | 文件 | 当前值 | 改为 | 说明 |
|---|---|---|---|---|
| 1 | `gradle.properties` | `bbs_jar_1.20.1=libs/archive/bbs-2.6.1/bbs-2.6.1-1.20.1-zh_CN.jar` | `bbs_jar_1.20.1=run/1.20.1/mods/bbs-2.7-1.20.1-zh_CN.jar` | 编译期 `modCompileOnly` 依赖，路径相对项目根 |
| 2 | `gradle.properties` | `bbs_jar_1.20.4=libs/archive/bbs-2.6.1/bbs-2.6.1-1.20.4.jar` | `bbs_jar_1.20.4=run/1.20.4/mods/bbs-2.7-1.20.4.jar` | 同上 |
| 3 | `src/main/resources/fabric.mod.json` | `"bbs": ">=2.6-0"` | `"bbs": ">=2.7-0"`（或保留 `>=2.6-0`，见决策点 D1） | 运行时依赖下限 |

### 决策点

- **D1**：是否把依赖下限从 `>=2.6-0` 抬到 `>=2.7-0`？
  - 抬到 2.7：明确要求用户装 2.7，避免 2.6 下因 Mixin 装载失败而崩溃。
  - 保留 2.6：兼容老用户，但 2.7 新增的内部结构若被 Mixin 引用，2.6 下会炸。
  - **建议**：抬到 `>=2.7-0`。
- **D2**：是否把新 jar 从 `run/<ver>/mods/` 复制到 `libs/archive/bbs-2.7/` 再引用？
  - 当前 `modCompileOnly` 指向 `run/...` 技术上可行，但 `run/` 按惯例是运行时目录，且可能被 `runClient` 清理。
  - **建议**：复制到 `libs/archive/bbs-2.7/bbs-2.7-1.20.1-zh_CN.jar` 与 `libs/archive/bbs-2.7/bbs-2.7-1.20.4.jar`，gradle.properties 指向 libs；`run/` 下保留运行副本。

---

## 2. 源码适配工作（按风险分层）

### 2.1 已确认安全、无需改动

| 项 | 证据 |
|---|---|
| `cubic.weld.CubeFace` 被删 | BBS-Cubed 无 import；新位置为 `main/java/mchorse/bbs_mod/cubic/data/model/CubeFace.java`（包路径迁移） |
| `ui.model_editor.UIModelGroupList` 被删 | BBS-Cubed 无 import |
| `ui.utils.GizmoLens` 被删 | BBS-Cubed 无 import；新拆分到 `GizmoSize` 等 |
| `bg4.png`、`interface_blur.json` 资源被删 | BBS-Cubed 资源 / 代码均无引用 |
| `UIKeyframeSheet.getRowColor()` 被删 | 全仓 grep 无调用 |

### 2.2 高风险：Mixin 目标类发生修改（47 个，运行时 Mixin 装载可能失败）

> 这些是 BBS-Cubed `@Mixin` 直接注入的目标类，2.7 内部方法 / 字段若重命名或挪位，会导致 `@Inject` / `@Shadow` / `@Overwrite` 找不到目标。
> 验证方式：启动 `runClient1201` / `runClient1204`，看日志 `Mixin apply failed` / `could not find method`。

按功能子系统分组：

#### A. Keyframe / 关键帧系统（2.7 新增 Loop、MotionShift，内部布局变化最大）

涉及 BBS-Cubed Mixin：
- `KeyframeMixin`、`KeyframeChannelMixin`、`KeyframeSegmentMixin`、`KeyframeSegmentCMLMixin`、`KeyframeSegmentPoseBoneSkipMixin`、`TextureTweenKeyframeMixin`、`KeyframeChannelTextureTweenMixin`、`FormPropertiesTextureTweenMixin`
- `UIKeyframesMixin`、`UIKeyframeSheetMixin`、`UIKeyframesBoxSelectionMixin`、`UIKeyframesLayoutLockMixin`、`UIKeyframesVideoScrubbingMixin`、`UIKeyframeGraphSelectedScrollMixin`、`UIKeyframeGraphTextureMixin`、`UIKeyframeDopeSheetTrackHeightScrollMixin`、`UIKeyframeFactoryMixin`、`UIKeyframeInspectorEmbedMixin`、`UIKeyframeEditorSplitMixin`、`UIKeyframesExportMenuMixin`、`UIKeyframeSheetActionsMixin`、`UIKeyframeSheetTextureGradeMixin`、`UIKeyframesPoseParameterBrushMixin`
- `UIAnchorKeyframeFactoryMixin`、`UIPoseKeyframeFactoryMixin`、`UIActionsConfigKeyframeFactoryMixin`、`UIItemStackKeyframeFactoryMixin`、`UILinkKeyframeFactoryMixin`
- `IUIKeyframeGraphMixin`
- `DiamondKeyframeShapeRendererMixin`

重点核对：2.7 给 `Keyframe` 加了 `motionShift` 字段与方法、给 `KeyframeChannel` 加了 `loops` 列表；`UIKeyframeSheet` 把行色逻辑挪进了 `Section` record。**BBS-Cubed 若通过 `@Shadow` 访问这些内部字段，需要核对字段名 / 类型。**

#### B. Film / Replay / 剪辑

涉及 Mixin：
- `FilmsMixin`、`ReplayKeyframesMixin`、`WorldFilmControllerReplayTimeMixin`、`BaseFilmControllerMixin`、`BaseFilmControllerReplayTimeMixin`、`BaseFilmControllerVisibilityMixin`
- `UIReplaysEditorMixin`、`UIReplaysEditorUtilsMixin`、`UIReplaysEditorClickMixin`、`UIReplaysEditorHeaderBlockerMixin`、`UIReplaysEditorEmbedMixin`、`UIReplaysEditorActionsOverlayMixin`、`UIReplayListCategoryMixin`
- `UIClipsMixin`、`UIClipsDoubleClickMixin`、`UIClipsInvoker`、`UIClipsPanelAccess`
- `UIFilmPanelMixin` 系列（GameMode / LoopIcon / Scroll / Visibility / MiniWindow / Layout）、`UIFilmPreviewMixin`、`UIFilmRecorderMixin`、`UIFilmKeyframesMixin`、`UIFilmControllerGameModeMixin`、`UIFilmControllerPrevCameraModeMixin`
- `MotionPathMixin`、`UICurveClipMixin`、`CameraClipMixin`
- `ReplayActionSafetyMixin`、`ReplayTimeMixin`、`UIScreenMixin`（film 全屏相关）

重点核对：2.7 新增 `FilmPlayerPose`、`FilmEditEvents` 等事件体系；`TrackCatalog` / `TrackStyle` / `TrackId` / `TrackKind` 均有改动。

#### C. Form / Model / 渲染

涉及 Mixin：
- `ModelFormMixin`、`ModelFormCMLMixin`、`ModelFormRendererMixin`、`ModelFormTrackNameMixin`、`ModelInstanceMixin`、`ModelInstanceTextureGradeMixin`、`ModelMixin`、`ModelGroupMixin`
- `FormRendererTextureGradeContextMixin`、`BillboardFormMixin`、`BillboardFormRendererMixin`、`ExtrudedFormMixin`、`ExtrudedFormRendererMixin`、`TrailFormColorMixin`、`TrailFormRendererMixin`
- `UIBillboardFormPanelMixin`、`UIExtrudedFormPanelMixin`、`UIModelFormPanelMixin`、`UIModelIKFormPanelMixin`、`UIModelPhysicsFormPanelMixin`、`UIModelConstraintsFormPanelMixin`、`UIModelBlockPanelGizmoThrottleMixin`
- `CustomVertexConsumerTextureGradeMixin`、`BBSShadersTextureGradeMixin`、`MiscFormTextureGradeMixin`
- `BBSRenderingMixin`、`BBSRenderingGetVideoFolderMixin`、`CubicCubeRendererMixin`、`CubicModelAnimatorMixin`、`CubicMatrixRendererMixin`、`CubicVAORendererMixin`、`BOBJModelAnimatorMixin`、`BOBJModelVAOMixin`、`ModelVAOMixin`、`ModelVAORendererMixin`、`AnimatorMixin`、`ProceduralAnimatorMixin`、`GeoAnimationParserMixin`

重点核对：2.7 给 `Form` 加了 `additionalOverlays`、`syncOverlayTracks()`、`getAll()`；`ModelInstance`、`ModelPivotFrames`、`WeldBinding`、`ModelManager` 有改动；新增 `ProceduralBone`。

#### D. 设置 / 键位 / 通用 UI

涉及 Mixin：
- `BBSSettingsMixin`、`BBSSettingsCMLMixin`、`SettingsManagerMixin`、`KeybindMixin`、`KeybindSettingsMixin`、`UIKeybindMixin`、`UISettingsLayoutMixin`、`UISettingsVideoPresetMixin`
- `UIFormEditorMixin`、`UIFormEditorAnimationStateLayoutMixin`、`UIAnimationStateEditorMixin`、`UIAnimationStateKeyframesMixin`、`UIAnimationStatePoseInsertMixin`
- `UIPoseEditorMixin`、`UIPoseFactoryEditorMixin`、`UIPoseKeyframeFactoryMixin`、`UIPoseTransformsBoneSkipMixin`、`PoseBoneSkipMixin`、`PoseTransformMixin`
- `UIPropTransformMixin`、`UITransformMixin`、`UIElementMixin`、`UISectionMixin`、`UIOverlayPanelResizeMixin`、`UIOverlayUndoHistoryMixin`、`UIUndoListMixin`、`UISoundOverlayPanelMixin`、`UIStringOverlayPanelMixin`、`UIBaseTextboxMixin`、`UITextareaMixin`、`UITextboxFilmLibrarySearchMixin`、`UIChalkboardMixin`
- `GizmoPivotMixin`、`Gizmo` 相关
- `ShaderCurvesMixin`、`IrisUtilsMixin`、`MatrixStackUtilsMixin`、`MathBuilderMixin`、`UIValueMapMixin`、`ValueColorsMixin`、`L10nMixin`、`LangKeyMixin`、`StringKeyMixin`、`SoundManagerMixin`、`WaveReaderMixin`、`VideoRecorderMixin`、`TextureManagerMixin`、`TextureManagerReloadMixin`、`TextureMixin`、`EffekAssetLoaderMixin`、`EffectDefinitionMixin`、`WindowClipboardMixin`、`KeyboardNarratorShortcutMixin`、`ClientPlayNetworkHandlerMixin`、`RenderContextMixin`、`GameRendererMixin`、`GameRendererAccessor`、`MinecraftClientMixin`、`ActionPlayerMixin`、`ActionPlaybackMixin`、`ActionConfigMixin`、`ClipsMixin`、`ICursor` 相关、`RunnerCameraControllerMixin`

### 2.3 中风险：被 import 但不是 Mixin 目标的类（80 - 47 ≈ 33 个）

这些类 BBS-Cubed 只调用公开 API，不直接注入。2.7 以增量为主，预期编译通过；若编译报错，按报错信息修复即可。代表类：

- `BBSModClient`、`BBSRendering`、`TimeUtils`、`ModelPivotFrames`、`FilmMatrices`、`FilmEntityRenderer`、`TrackId`、`TrackKind`、`FormUtils`、`VanillaParticleFormRenderer`、`UserFormSection`、`Draw`、`ValueGroup`、`ICursor`、`UIForm`、`UIIcon`、`UIKeyframePropTransform`、`UIListOverlayPanel`、`Batcher2D`、`UIModelRenderer`、`UITimelineCanvas`、`Keys`、`UIKeys`、`Icons`、`Scale`、`Scroll`、`UI`、`UIConstants`、`Matrices` 等。

### 2.4 低风险 / 可选：2.7 新 API（不升级也不影响运行）

- **关键帧循环**：`KeyframeLoop`、`KeyframeLoops`、`UIKeyframeLoops`、`UIKeyframeLoopOverlay`、`UIKeyframeMotionShift`
- **新编辑器事件**：`RegisterFilmToolsEvent`、`RegisterFormPanelsEvent`、`RegisterReplayActionsEvent`、`RegisterTrackCategoriesEvent`、`FilmEditEvents`、`FilmGizmoEvents`、`FormPoseEvents`、`FormPreviewEvents`、`StructureRenderEvents`、`TimelineEvents`
- **新工具 / 渲染**：`FilmEditorTool`、`FormEditorTool`、`TrackCategory`、`RenderAttachment`、`StructureRenderPart`、`UniformScaleDrag`、`GizmoSize`、`ShaderMenu` / `IrisShaderMenu`、`UIShaderOptionPicker`
- **模型编辑器重构**：`ModelNode`、`ModelCubeEdit`、`ModelFaces`、`UIModelTree`、`UIModelUVEditor` 等（替代旧 `UIModelGroupList`）
- **BBS 官方 addon API 校验器**：`addonApiChecks/` 模块，`AddonApiCheck` 接口

> 这些是**增量**，BBS-Cubed 不接入也能编译运行；是否采用是产品决策，不在本次"适配"必做范围内。

---

## 3. 执行步骤（按顺序）

| 步骤 | 命令 / 动作 | 预期结果 | 失败处理 |
|---|---|---|---|
| S1 | 改 `gradle.properties` 两个 `bbs_jar_*`（按 D1/D2 决策） | 文件保存 | — |
| S2 | （可选）改 `fabric.mod.json` 的 `bbs` 依赖下限 | 文件保存 | — |
| S3 | `./gradlew compileJava -Pmc_version=1.20.1` | 编译通过；若有错，按 2.3 清单逐个修 | 修 import / 方法签名 |
| S4 | `./gradlew compileJava -Pmc_version=1.20.4` | 编译通过 | 同 S3 |
| S5 | `./gradlew build1201` | 构建出 jar；Mixin 注解处理无报错 | 看 Mixin AP 警告 |
| S6 | `./gradlew build1204` | 构建出 jar | 同 S5 |
| S7 | `./gradlew runClient1201` | 进游戏主菜单无 Mixin 装载报错 | 按 2.2 分组，定位失败的 Mixin |
| S8 | 进游戏打开 BBS 编辑器，依次冒烟：关键帧面板、Film/Replay 面板、ModelForm 编辑、设置面板、Gizmo | 各面板能打开、操作不崩 | 对照 2.2 A/B/C/D 分组排查 |
| S9 | `./gradlew runClient1204` 重复 S7/S8 | 同上 | 同 S7 |
| S10 | 更新 `docs/BBS-Cubed-1.20.4-兼容性报告.md` 中残留的旧 jar 路径引用 | 文档与构建一致 | — |

---

## 4. 验收清单

- [ ] `gradle.properties` 中两个 `bbs_jar_*` 指向 2.7 jar
- [ ] `fabric.mod.json` 的 `bbs` 依赖下限按 D1 决策更新
- [ ] 1.20.1 / 1.20.4 双版本 `build` 成功
- [ ] 1.20.1 / 1.20.4 双版本 `runClient` 启动无 Mixin 装载失败
- [ ] 关键帧 / Film / Model / 设置 / Gizmo 五大子系统手动冒烟通过
- [ ] 无对已删除类（`CubeFace` 旧包、`GizmoLens`、`UIModelGroupList`）的残留引用
- [ ] 文档中旧 `bbs-2.6.1` 路径已更新

---

## 5. 附：差异统计速查

| 维度 | 数量 |
|---|---|
| 2.7 新增文件 | 57（Java 类 50 + 资源 / 测试 7） |
| 2.7 删除文件 | 5（Java 类 3 + 资源 2） |
| 2.7 修改文件 | 163（Java 类 160 + 资源 3） |
| BBS-Cubed import 的 BBS 类总数 | 394 |
| 其中在 2.7 被修改 | 80 |
| BBS-Cubed Mixin 目标类（BBS 侧） | 约 130 |
| 其中在 2.7 被修改 | 47 |
| FS 2.7 自身新增 client Mixin | 4（`ClientPlayerEntityFilmSneakMixin`、`DownloadingTerrainScreenMixin`、`PlayerEntityFilmPoseMixin`、`RenderPhaseMixin`） |
| FS 2.7 自身删除 client Mixin | 0 |
