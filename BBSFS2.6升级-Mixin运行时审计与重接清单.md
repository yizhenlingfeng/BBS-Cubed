# BBSFS 2.6 升级 · Mixin 运行时存活审计与后续重接清单（3.5 分支）

> 生成时间：2026-09-15。配合《BBSFS2.6升级-冲突功能删除计划表.md》使用。
> 背景：`compileJava`/`build` 通过 ≠ Mixin 运行时有效——javac 不校验 `@Shadow` 字段、`@Inject(method=…)`、`@Redirect(target=…)` 的目标是否存在；而 `bbsplusplus.mixins.json` 设了 `"injectors":{"defaultRequire":1}`，目标缺失会在类加载时直接崩溃。本文是逐类对照 `libs/bbs-fs-2.6-1.20.1/src` 的审计结果。

## 一、当前状态结论

- 分支 `3.5`，Gradle 已切到 BBSFS 2.6（1.20.1 / 1.20.4 两个 jar）。
- `gradlew build` **BUILD SUCCESSFUL**，产物 `build/1.20.1/libs/BBS-Cubed-3.5.0+1.20.1.jar`。
- 编译期 140 个错误已全部清零（A1/A2/A3/A5 删除、C1 事件迁移、全部 API 改名）。
- 运行时审计分两轮：
  1. **方法注入**：沿 `extends` 继承链向上查找；除下文停用项外，所有仍启用 Mixin 的注入方法都能在 2.6 解析到。
  2. **@Shadow 成员**：逐字段/方法核对；除下文停用项与已快速修复项外，均存在。
- 为保证产出 jar 可加载，已把「目标成员在 2.6 确认删除、需按 B/C 计划做功能级重接」的 **15 个 Mixin 条目临时从配置移除**（`.java` 源码全部保留，恢复方式见第四节）。

## 二、本轮已「快速重接」的 Mixin（已编译验证）

| Mixin | 2.5 旧目标 | 2.6 现状 / 处理 |
|---|---|---|
| OrbitFilmCameraControllerFollowMixin | `getReplayPivot(float)→Vector3f` | 改名 `getOrbitTarget(float)→Vector3d`，shadow 与调用同步 |
| UIClipsDoubleClickMixin | `@Shadow Scale scale` | 改 `@Shadow(remap=false) protected final Scale xAxis = null;`（final 影子须 =null） |
| UIReplaysEditorMixin | 注入 `collectFormPropertySheets` 做轨道排序 | 该方法 2.6 已删，**删除此注入及专用 helper**；保留 `getColor/getIcon`（轨道上色/图标汉化，2.6:165/170 仍在） |
| UIReplayListCategoryMixin | `@Shadow collapsedCategories(Set)`、`contextFolderCategoryName` | 折叠态改由 `film.replayCategories.isExpanded/setExpanded` 管理；右键路径改 shadow 现有字段 `contextFolderPath` |

## 三、临时停用、待功能级重接的 15 个条目（重点工作队列）

> 共同原因：2.6 对对应子系统做了重写，简单改名无法平移，需按计划表 B/C 项重新对接。源码均在，停用仅为「可加载」。

### 配置 `bbsplusplus.mixins.json`（移除 9）

| # | Mixin | 失效点（2.6 已删/改） | 对应计划 | 影响的保留功能 |
|---|---|---|---|---|
| 1 | MotionPathMixin | `computeBoneTrajectory` 删除；2.6 改 `computeSampledTrajectory(Map<String,IEntity>,Replay,FilmTarget)`（:421），语义变 | B/运动轨迹 | 单骨骼运动轨迹 |
| 2 | UITexturePickerMixin | shadow `right/picker/updateFolderButton` 全无；2.6 用 `public UIFilteredLinkList multiList` | B3 | 纹理选择网格 |
| 3 | UIDataContextMenuMixin | shadow `row` 无；2.6 改 `public UIContextMenuBar bar`，按钮须 `MenuIcon(Icon,IKey,MenuVerb.Slot,Runnable)` + `bar.register` + `sync` | C/预设 | 预设面板「自动保存」按钮 |
| 4 | UIModelIKFormPanelMixin | shadow `presetGroup/syncingUI` 无、`commitChanges` 无（注：IK 面板 `startEdit` 仍在） | C3 | IK 预设面板增强 |
| 5 | UIModelPhysicsFormPanelMixin | `presetGroup/syncingUI` 无，2.6 用 `save` | C3 | 物理预设面板增强 |
| 6 | UIModelConstraintsFormPanelMixin | `presetGroup/syncingUI/commitChanges/startEdit` 全无 | C3 | 约束预设面板增强 |
| 7 | UIModelBlockPanelGlobalMixin | 目标 `lambda$new$9` 合成方法，2.6 重新编号 | C/方块面板 | 方块面板全局增强 |
| 8 | UIModelBlockPanelPaletteCacheMixin | 目标 `lambda$new$13` 重新编号 | C/方块面板 | 调色板缓存 |
| 9 | FormPropertiesTextureTweenMixin | 注入 `FormProperties.applyProperty`，该方法 2.6 整删；应用改走 `TrackBehaviours` 按 `TrackKind` 分发 | B/纹理补间 | 纹理补间「应用」环节（配套 5 个 tween Mixin 仍启用、当前休眠不崩） |

### 配置 `bbspp-fslovecml.mixins.json`（移除 1）

| # | Mixin | 失效点 | 计划 | 影响功能 |
|---|---|---|---|---|
| 10 | FormPropertiesPBRMixin | shadow `registerChannel(String,f)` 无→`register(TrackId,IKeyframeFactory)`；注入 `applyProperty` 无 | B5 | 逐骨骼 PBR 通道注册/应用 |

### 配置 `bbspp-fslovecml.client.mixins.json`（移除 5）

| # | Mixin | 失效点 | 计划 | 影响功能 |
|---|---|---|---|---|
| 11 | PBRUIReplaysEditorMixin | 注入 `UIReplaysEditor.flushForm` 无；2.6 建轨走 `TrackCatalog` + `UIReplaysEditorUtils.buildSheets` | B5 | 逐骨骼 PBR 轨道在编辑器的建表 |
| 12 | UIActionsConfigEditorMixin | shadow `remap` 无 + `lambda$new$1..5` 重编号 | C/动作配置 | 动作配置编辑器增强 |
| 13 | UIFilmPanelVisibilityMixin | `renderTopBarButton/topBarActions/getTabsRightInsetPx/renderTopBarActions` 全无（顶栏随 tabBar→actions 重做） | C/顶栏 | 影片面板顶栏可见性 |
| 14 | ParticleManagerVanillaFormMixin | `addParticle/renderParticles` 无（粒子管线重写） | C/粒子 | 原版粒子形态接入 |
| 15 | UIDataContextMenuMixin | 同 #3（该类在两个配置都注册，两处都移除） | C/预设 | 同上 |

## 四、恢复方式

把对应类名加回原配置 JSON 的数组即可（源码未删）。重接时的 2.6 关键 API 见下表与计划表。

- 轨道体系：`TrackId`（record：kind/formPath/subject/property，静态 `bone/ikTarget/materialTexture/property…`，`parse`，实例 `subject()/is(TrackKind)`）；`FormProperties.tracks:Map<TrackId,KeyframeChannel>`、`register(TrackId,factory)`；应用走 `applyProperties→apply(TrackContext,…)→TrackBehaviours`。
- 建表：`TrackCatalog.of/ordered` + `UIReplaysEditorUtils.buildSheets/pruneTree`。
- 面板操作栏：`panel.actions()` 返回 `UIPanelActionBar`，槽 `editor/action/layout/common/menu`；无 `tabBar/iconBar`。
- 上下文菜单条：`UIDataContextMenu.bar:UIContextMenuBar`，`register(MenuIcon)`、`sync(boolean)`。
- 合成 lambda 注入：先在 2.6 源码重新确认 `lambda$new$N` 序号，或改注入稳定的具名方法。

## 五、需进一步「签名级」复核的注入（不崩，但功能可能未生效）

- `BaseFilmControllerVisibilityMixin`：`@Redirect` 目标 `renderEntity(FilmControllerContext)` 在 2.6 已并入三参 `renderEntity(WorldRenderContext,Replay,IEntity)`（:623，内部改调 `FilmEntityRenderer.renderEntity`）。该注入带 **`require=0`，不会崩**，但「隐藏伪装形态」在此入口暂不生效，需把重定向移到新渲染路径。
- 其余描述符固定（`method="name(L…;)V"`）的注入已做参数级自动比对，除上条外均能匹配；2.6 渲染层存在 MatrixStack→JOML(Matrix4f/Matrix3f) 迁移，**建议进游戏做一次冒烟加载**最终确认（类加载即会暴露任何残留漂移）。

## 六、仍未开始的计划大项（本次未动）

- **B1 光影曲线**：本项目双 Mixin 与 2.6 自带 `CelestialUniformsMixin` 同目标冲突（最优先；2.6 用 ShaderCurves+ShaderSunRotation；在 `bbsplusplus-shadercurves.mixins.json`）。
- **B4** 轨道样式改 `RegisterTrackStylesEvent`（KeyframeLocalizer 汉化保留）。
- **B5** 逐骨骼 PBR 按 TrackId/TrackBehaviour/TrackCatalog 重接（即 #10/#11）。
- **B6** 影片库按 Landing 重做。
- **C5** 仪表盘重构（`BBSFSloveCMLClientAddon` 里 `event.dashboard.overlay.keys()/getPanels().panel`、`UISelectionScreen` 已删）。
- **C6** Gizmo 模式循环三文件已删、设置字段 `gizmoBlockbenchMode/gizmoTCombined/gizmoKeepOriginal` 休眠，是否基于 `TransformGesture` 重做待定。
- **存档迁移**：A1/A2/A3 老字段改名（structure_file→structure、biome_id→biome、视频字段、pbr_s_*→材质通道），建议挂 2.6 data/migration 框架，当前仅保证编译。
- 1.20.4 变体需 `gradlew build -Pmc_version=1.20.4` 回归（本轮只验证默认 1.20.1）。

## 七、明确保留、当前仍启用（勿误删）

流体、AAA 粒子、粒子 Plus、物品喷射、附魔光效、纹理补间（应用环节待重接）、Premiere/SRT/音频导出、Hotbar/Cinematic/Replay 三剪辑、动作叠加/Additive/Molang、迷你窗、回放时间控制、剪辑可见性、Pose 参数刷/跳过/橙标/多选/列选择、逐骨骼 PBR 数据层与逐骨骼纹理、UV 变换、VFX 破坏魔杖增强、骨骼优先级/动画插值继承、Gizmo pivot、Alt 滚轮时间线、循环按钮、各类汉化与 bug 修复。

---

## 八、第二轮修复（2026-09-15，已 build 通过）

### 已删除
- **CelestialUniformsMixin（B1）**：2.6 原生自带同名 Mixin（`@ModifyConstant(-90F)` + `BBSRendering.getSunHorizontalRotation()`），本插件 `BBSRenderingMixin` 已挂钩喂入曲线值，我方 field-redirect 版冗余冲突，已 `git rm` 并从 shadercurves 配置移除。

### 已重接并重新启用（编译+build 验证）
| Mixin | 2.6 适配 |
|---|---|
| MotionPathMixin | `Pair<String,Boolean>`→`FilmTarget`；`computeBoneTrajectory`→`computeSampledTrajectory`；`boneTrajectory`→`sampledTrajectory`；`signature(Replay,String)`→`signature(Replay,FilmTarget)` |
| UIDataContextMenuMixin | shadow `row`→`bar:UIContextMenuBar`；自动保存按钮改 `new MenuIcon(Icons.SAVE,…,Slot.COMMON,runnable)` + `bar.register`（动态高亮改为静态，功能保留） |
| UIModelIK/Physics/ConstraintsFormPanelMixin | `presetGroup` 改 shadow 父类 `UIBoneListFormPanel` 的 protected 字段；删 `syncingUI`（已删）；funnel 从 commitChanges/save → 子类覆写的 `updateFields()` |
| UIFilmPanelVisibilityMixin | 顶栏三件套（topBarActions/renderTopBarButton/getTabsRightInsetPx）全删，改构造末尾 `panel.actions().action(visibilityButton)` |
| UIActionsConfigEditorMixin | 字段/构造器/pickAction 仍在；删除 5 个脆弱的 `lambda$new$1..5` 同步注入（loop/speed/fade/tick/selection），保留循环控件初始化与 pickAction |
| ParticleManagerVanillaFormMixin | **误报纠正**：目标是原版 `net.minecraft.client.particle.ParticleManager`（方法在原版仍在，且 require=0），非 BBS 类，已重新启用 |
| FormPropertiesPBRMixin | 注册 `registerChannel(String,…)`→`register(TrackId,…)`；应用 `applyProperty`→挂私有静态 `apply(TrackContext,TrackId,KeyframeChannel,F,F)` HEAD 拦截 bone_pbr |
| FormPropertiesTextureTweenMixin | `applyProperty` HEAD/RETURN→同一静态 `apply` 的 HEAD/RETURN，用 `track.toKey()` 判断 texture 通道；`fromData` 迁移保留 |

### 仍停用（本轮未做，需后续）
| Mixin | 原因 / 下一步 |
|---|---|
| UITexturePickerMixin（B3） | 2.6 picker 重构为 `multiList:UIFilteredLinkList`（旧 `right/picker/updateFolderButton` 删）；网格列表需改挂 UIFilteredLinkList 体系 |
| PBRUIReplaysEditorMixin（B5） | `flushForm` 已删，建表走 `TrackCatalog`+`UIReplaysEditorUtils.buildSheets`；bone_pbr 通道应用已通，编辑器加表需接入新目录体系 |
| UIModelBlockPanelGlobalMixin / PaletteCacheMixin | 仍指向旧 `lambda$new$13/$9`；2.6 重新编号（运行 jar 为混淆名，需 Loom dev jar 反查）；纯刷新/缓存微优化 |
