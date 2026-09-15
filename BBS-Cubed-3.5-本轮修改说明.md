# BBS-Cubed 3.5（适配 BBSFS 2.6）本轮修改说明

> 分支：`3.5` ｜ 构建：JDK21 + Gradle，`gradlew clean build` BUILD SUCCESSFUL
> 产物：`build/1.20.1/libs/BBS-Cubed-3.5.0+1.20.1.jar`（约 1.87 MB）
> 提交：`5f39f3c feat(3.5): 移除逐骨骼PBR与bone_pbr轨道全部代码`（上一笔 `4053f46`）

---

## 一、目标
在已升级到 BBSFS 2.6 的基础上：
1. 删除与 2.6 原生冲突的 CelestialUniforms 重定向；
2. 修复 2.6 中签名/结构变更导致失效的 Mixin；
3. 彻底移除"逐骨骼/肢体 PBR"与 `bone_pbr` 轨道（含源码、注册、Mixin、lang）。

## 二、已完成改动

### A. 删除冲突（B1）
- 删除 `CelestialUniformsMixin`：2.6 已原生自带同名 Mixin（`@ModifyConstant(-90F)` + `BBSRendering.getSunHorizontalRotation()`），且我方 `BBSRenderingMixin` 已挂钩喂入曲线值，原 field-redirect 版冗余冲突。

### B. 重接并重新启用（2.6 适配）
| Mixin | 适配点 |
|---|---|
| MotionPath | `Pair<String,Boolean>`→`FilmTarget`；`computeBoneTrajectory`→`computeSampledTrajectory`；字段 `boneTrajectory`→`sampledTrajectory`；`signature(Replay,String)`→`signature(Replay,FilmTarget)` |
| UIDataContextMenu | shadow `row`→`bar:UIContextMenuBar`；自动保存按钮改 `MenuIcon` + `bar.register` |
| UIModel IK/Physics/Constraints 预设面板 | funnel 从 commitChanges/save → 子类 `updateFields()`；`presetGroup` 改 shadow 父类 protected 字段 |
| UIFilmPanelVisibility | 顶栏三件套删除 → 构造末尾 `actions().action(button)` |
| UIActionsConfigEditor | 删除 5 个脆弱 `lambda$new$N` 同步注入，保留循环控件与 pickAction |
| ParticleManagerVanillaForm | 重新启用（误报，目标为原版 `ParticleManager`） |
| FormPropertiesTextureTween | 应用改挂 2.6 静态 `apply(TrackContext,TrackId,KeyframeChannel,F,F)`，按 `track.toKey()` 识别 texture 通道 |

### C. 移除逐骨骼 PBR / bone_pbr 轨道（本轮）
**整文件删除（10）**
- `pbr/BonePBRData`、`pbr/BonePBRKeyframeFactory`、`pbr/PBRChannel`
- `pbr/render/BonePBRContext`、`pbr/render/PBRTextureModifier`
- `pbr/ui/UIBonePBRKeyframeFactory`
- `api/PBRModelFormAccess`、`mixin/FormPropertiesPBRMixin`
- `mixin/client/PBRUIReplaysEditorMixin`、`mixin/client/IrisTextureWrapperPBRMixin`

**切除引用（保留无关逻辑）**
- 主入口/客户端：移除 `KeyframeFactories.put("bone_pbr",…)` 与 UI 工厂注册
- `ModelFormCMLMixin`：移除 access 接口、覆盖字段与取值方法
- `CubicVAORendererMixin` / client `ModelFormRendererMixin`：移除 BonePBR set/clear 钩子（保留调色/白化/光效/动作叠加）
- `UIReplaysEditorUtilsMixin`：移除 UIBonePBR 骨骼拾取分支
- `BonePriority`：移除 bone_pbr 白名单判断
- `SnowUIKeys`：删 6 个 `BONE_PBR_*` 常量

**注册与资源**
- `bbspp-fslovecml.mixins.json` 注销 `FormPropertiesPBRMixin`
- `bbspp-fslovecml.client.mixins.json` 注销 `IrisTextureWrapperPBRMixin`
- zh/en lang 删除 6 条 `bbspp.ui.bone_pbr.*` 键

**保留未动**：`texture_tween_pbr_glow*`（闪白补间经 LabPBR 发光，属保留的纹理补间功能，非骨骼 PBR 轨道）。

## 三、进入游戏前的验证（已做）
- `gradlew clean build` BUILD SUCCESSFUL，无编译错误。
- jar 内 `bbspp-fslovecml.mixins.json`（12 项）与 `…client.mixins.json`（78 项 client）**全部引用类均已打包，missing=0**。
- jar 内已无任何 `PBR` / `pbr` / `BonePBR` 条目（clean 后旧 `PBRTextureContext.class` 残留已清除）。
- 两个 mixins.json 为合法 JSON；`fabric.mod.json` 配置列表与磁盘一致。

## 四、仍停用（不影响进游戏，纯增强项）
| Mixin | 原因 |
|---|---|
| UITexturePickerMixin | 2.6 picker 重构为 `multiList:UIFilteredLinkList`，网格列表需改挂新体系 |
| UIModelBlockPanel Global / PaletteCache | 指向旧合成 `lambda$new$N`，需 Loom dev jar 反查新序号（刷新/缓存微优化） |

## 五、进游戏冒烟清单
1. 把 `build/1.20.1/libs/BBS-Cubed-3.5.0+1.20.1.jar` 放入 mods，配合 BBSFS 2.6 加载。
2. 启动至主菜单（确认无 Mixin apply / target 报错）。
3. 进入存档打开回放编辑器，确认：动作叠加、纹理调色/白化、影片可见性按钮、IK/Physics/Constraints 预设面板、MotionPath 轨迹正常。
4. 时间线不应再出现 `bone_pbr` 轨道类型。
