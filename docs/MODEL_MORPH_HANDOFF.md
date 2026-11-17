# Model Morph 交接说明（给接任者）

> 本文档用于把 BBS-Cubed 的 Model Morph 功能完整交接给下一位开发者（含 AI 助手）。
> 请先读完本文，再动手改代码。

---

## 一、项目定位与工作目录

| 项 | 值 |
| --- | --- |
| 项目根目录 | `/home/mcaxolotl/开发/BBS-Cubed` |
| 当前分支 | `feat/model-morph-1.20.x`（从 `main` 的 `de0d383` 切出） |
| 目标 MC 版本 | **仅 1.20.x**（1.20.1 为主，1.20.4 兼容编译）；**1.21.x 明确不在范围内** |
| 许可证 | LGPL-3.0-or-later（项目本体）+ MIT（参考 bbs-bone-anchor 的数学工具） |
| 远程推送 | **未推送**，全部为本地提交。**不要自动 push** |

BBS 上游源码（2.7）可参考：`/home/mcaxolotl/开发/bbs-fs`
BBS 2.7 编译期依赖：`libs/archive/bbs-2.7/bbs-2.7-1.20.1-zh_CN.jar`

> ⚠️ **关键**：项目编译期依赖的是 **2.7 的 jar**，而 `bbs-fs` 目录是 2.8 开发版源码。
> 两者 API 有偏差。**任何要调用的 BBS API，都必须先用 `javap` 在 2.7 jar 上确认存在**，
> 不能只看 2.8 源码就动手。

---

## 二、这个功能是什么

在 BBS 的 **Model 关键帧轨道**上增加一个「形变映射」开关。开启后，从当前关键帧到**下一个** Model 关键帧之间，
模型会从源模型**几何形变**成目标模型（不是切换模型，不是 alpha 淡入淡出，是真实的顶点形变）。

三种映射类型，全部**由选择数量自动推断**，用户不需要选：

- **1:1** — 一个源 Cube 对一个目标 Cube
- **1:N** — 一个源 Cube 克隆分化成多个目标 Cube（是**克隆**，不是几何切分）
- **N:1** — 多个源 Cube 汇聚成一个目标 Cube（Full Convergence）

不支持 N:N（校验层会明确报错）。

### 核心设计：目标驱动（逆向绑定）

这是整个功能最重要的设计决策，**改动前必须理解**：

**以目标 Cube 为基准**，先把它缩放/平移成源 Cube 的形状（起点），再随进度恢复它自己的形状（终点）。

```
bind      = translate(sourceMin - targetMin*scale) * scale(sourceSize/targetSize)
startPose = sourceFrame * bind      // 目标块被缩放成源块形状
endPose   = targetFrame             // 目标块自己的姿态
```

**为什么这样做**：顶点始终使用**目标 Cube 自己的 UV**，采样目标图集原生区域，
所以**贴图全程保持原生清晰度**——就像缩放骨骼不会让纹理错乱。

**反面教材（已被否决，不要重走）**：
- ❌ 以源 Cube 为基准，把目标内容塞进源 UV 矩形 → 两个矩形宽高比不同（实测 bed 面 16×16 vs boat 面内容 16×6），必须拉伸 2.67 倍，全程发糊
- ❌ 逐像素交叉淡化两张不同布局的图集 → 同样需要重映射，引入模糊与错位

---

## 三、代码结构

```
src/main/java-1.20.x/gbeic/bbsplusplus/
├── client/morph/                    # 核心逻辑（19 个文件）
│   ├── MorphMath.java               # 安全矩阵求逆/分解、TRS 插值、四元数 slerp
│   ├── ModelMorphData.java          # 每关键帧的映射配置（序列化到 keyframe）
│   ├── MappingGroup.java            # 映射组 + 1:1/1:N/N:1 类型推断
│   ├── ModelMorphSkeleton.java      # 只读骨骼与 Cube 快照
│   ├── ModelMorphAdapter.java       # Model → Skeleton，稳定 key 与几何指纹
│   ├── MutationCube.java            # ★ 目标驱动形变的核心：单对 Cube 的逆向绑定
│   ├── MorphGeometry.java           # 按进度采样（带缓存）
│   ├── MorphPlan.java               # 把映射编译成过渡体（Slot）
│   ├── MorphPlanCache.java          # LRU 缓存 + 失败负缓存
│   ├── ModelMorphValidation.java    # 校验引用/冲突/指纹漂移
│   ├── MorphAutoMatch.java          # 归一化几何特征 + 全局分配；数量差异归并 1:N / N:1
│   ├── ModelMorphState.java         # 每表单的运行时状态
│   ├── ModelMorphRuntime.java       # 关键帧区间求值与渲染接管入口
│   ├── MorphRenderContext.java      # 线程内渲染上下文（避免共享实例字段污染）
│   ├── MorphTextures.java           # 纹理混合 + translucent 标志同步
│   ├── MorphScreenPicks.java        # 渲染期投影 Cube 面与深度（供 3D 拾取）
│   ├── MorphMappingPresets.java     # 映射预设（继承 BBS DataManager）
│   └── LegacyMorphMapping.java      # 旧数据一次性导入
├── client/ui/morph/
│   └── UICubeMappingEditorPanel.java  # ★ Dashboard 全屏映射页（约 2700 行）
├── keyframes/IModelMorphKeyframe.java   # 关键帧配置读写接口
└── mixin/morph/                     # 10 个 Mixin / Accessor 文件；22 个 @Inject 均 require = 1
    ├── ModelMorphKeyframeMixin      # Keyframe 的 toData/fromData/copy/copyOverExtra/equals
    ├── ModelFormMorphMixin          # 每表单的 ModelMorphState 字段
    ├── ModelFormRendererMorphMixin  # 为 VAO 着色器判定提供当前表单作用域
    ├── PropertyTrackMorphMixin      # PropertyTrack#apply HEAD → update
    ├── FormPropertiesMorphMixin     # FormProperties#apply HEAD → clearAll
    ├── ModelInstanceMorphMixin      # render 接管 + isVAORendered 判定
    ├── CubicCubeRendererMorphMixin  # renderCube HEAD 接管，逐面提交顶点
    ├── ModelMorphReloadMixin        # 资源重载时失效缓存
    ├── TextureTranslucentAccessor   # 暴露 Texture.translucent 写入口
    └── UIModelKeyframeFactoryMorphMixin  # 关键帧面板上的入口
```

测试：`src/test/java/gbeic/bbsplusplus/client/morph/`、`client/ui/morph/` 与 `mixin/morph/`，共 **128 例，全通过**。

---

## 四、关键实现细节（改代码前必读）

### 4.1 不新增轨道

形变开关是**挂在既有 Model 关键帧上**的，通过 `Keyframe` 的 mixin 附加数据。
`ModelMorphData.DATA_KEY = "bbsppp_model_morph"`。
**不要**新建 KeyframeChannel 或新关键帧类型。

### 4.2 纹理与 UV

- UV 全程固定为目标 Cube 的 UV，**绝不移动**
- 混合画布按目标图集尺寸，并按 `SUPERSAMPLE = 4` 超采样，避免首帧细节丢失
- **alpha 平滑插值**（不是二值化、不是抖动）——BBS 有 `FormTranslucentQueue` 支持半透明
- **必须同步 `Texture.translucent` 标志**：BBS 只在 `uploadTexture(Pixels)` 里计算它，
  而形变纹理用 `glTexSubImage2D` 上传（因为 `Pixels` 会被 delete），所以要走
  `TextureTranslucentAccessor` 手动同步。**不同步会导致淡入淡出完全不生效。**

### 4.3 渲染接管

- `isVAORendered()` 在 BBS 里**同时决定着色器选择**，且着色器选择发生在几何提交前
- `ModelFormRendererMorphMixin` 在 UI、世界与第一人称模型渲染入口建立当前表单作用域；
  不要改为读取共享资源上的 `ModelInstance.form`，也不要把“同 ID 任一表单形变”当作当前表单
- `ModelMorphRuntime.isMorphing()` 必须与 `ModelMorphState.active()` **完全一致**
  （曾经让 isMorphing 比 active 宽，导致「VAO 被禁用但形变几何不渲染」的空档，反而引入跳变）
- `active()` 严格在 `0 < progress < 1` 之间接管：端点交给 BBS 自己的模型渲染，
  否则会同时绘制两套重合几何，产生 z-fighting 和双倍开销

### 4.4 3D 拾取（踩过很多坑）

- BBS 的 `StencilMap` 只到**骨骼**粒度，做不到 Cube 级 → 必须自己实现
- **正确做法**：在 `renderCube` 里用渲染栈的骨骼矩阵，并补上 Cube 自身 Pivot/旋转，投影实际面三角形和角点；
  拾取按鼠标下的表面深度选前景对象、按完整骨骼路径回查，避免重名路径和外接矩形误选
- **不要**在 UI 侧自己重拼 `projection × view × 平移` —— 曾经因为：
  - 漏了 `-camera.position` 一层
  - 把 `view` 应用了两次（渲染栈里已有 view）
  - Y 轴翻转做了两次
  导致反复「点不中」
- 高亮绘制必须在 `super.render()` **之后**（那时才 `restoreMatrices()` 恢复成 2D），
  在 `renderUserModelOverlay` 里画会被 3D 矩阵再变换一次，框跑到屏幕外

### 4.5 性能

- `ModelMorphData.hashCode()` 在渲染热路径每帧调用，**已缓存**，所有结构写入口必须
  调 `invalidateHash()`
- `MorphPlanCache` 有失败负缓存，避免不可用映射每帧重建 + 刷日志
- 形变几何走 CPU 顶点路径（VAO 是预烘焙的，无法承载逐帧变化的几何），这是设计代价

---

## 五、构建与验证

```bash
cd /home/mcaxolotl/开发/BBS-Cubed

# 注意：./gradlew 不可执行，必须用 bash gradlew
bash gradlew compileJava --offline                          # 1.20.1
bash gradlew compileJava -Pmc_version=1.20.4 --offline
bash gradlew test --offline                                 # 128 例
bash gradlew build1201 --offline                            # 产出 jar
bash gradlew runClient1201 --offline                        # 启动客户端
```

**当前验证状态**：

| 项 | 状态 |
| --- | --- |
| 1.20.1 / 1.20.4 编译 | ✅ 0 错误 |
| 单元测试 | ✅ 128/128 |
| `build1201` | ✅ 产出正常 |
| 新增渲染器/关键帧 Mixin 启动注入 | ✅ 本轮 `runClient1201 --offline` 已越过 Mixin 应用并完成资源加载；之前的非 private static helper 问题已移出 Mixin 到独立工具类。启动后映射日志确认同模型 `bed -> bed` 成功构建 1/6 与 6/6 transitions |
| 游戏内几何正确性 | ✅ 离线探针验证端点误差 3e-7 |
| 游戏内贴图/多选 | ✅ 用户此前已确认；源拾取视口本轮发现缓存冲突并修复，仍需用户在新构建中确认 |
| **最后一刻是否有轻微跳变** | ⚠️ **未确认**（见第六节） |

### 重要：不要并发跑不同目标的构建

`build/<mc>/` 是隔离的，但**不要**在 `runClient1201` 运行时跑其它目标的 `compileJava`，
曾因此产生过 stale 输出导致 `ClassNotFoundException`。

### 重要：EGL 冲突

如果用户自己开着游戏客户端，`bash gradlew runClient1201` 会失败：
`Failed to create window: GLFW error 65544: EGL ...`
此时**无法做游戏内验证**，必须请用户关掉自己的客户端，或让用户自己启动验证。
不要反复重试。

---

## 六、当前未解决的问题（最重要）

### 6.1 最后一刻的轻微跳变（未确认是否仍存在）

用户报告过「实际影片播放/录制时，区间终点有轻微缩放跳变」。
已经排查并确认的事实：

- ✅ 端点几何**精确**：t=1 的过渡几何与真实目标模型顶点误差仅 **3e-7**（离线探针实测）
- ✅ 插值曲线正常：`exp_out` 在 raw=0.5 时已达 0.9697，属正常缓动
- ✅ 已移除几何收缩（曾经用顶点朝中心收缩做淡出，导致「莫名缩小」，已删）
- ✅ 已移除顶点 alpha 淡出（会让整个模型变半透明）
- ✅ 已回退「让 isMorphing 覆盖端点」的改动（引入了更严重的跳变）

**尚未确认的假设**：跳变可能来自 **VAO 路径与 CPU 路径的着色器差异**，或者
**用户实际观察到的是别的现象**。

用户表示目前无法区分跳变类型，因此本轮先做了全分支代码审查。审查确认了同一模型 ID 的其他表单
可能被过宽的 VAO 判定带到 CPU 着色器路径；现在 `ModelFormRendererMorphMixin` 在着色器选择前
提供本次实际渲染的表单作用域。几何/明暗是否因此改善仍需游戏画面确认。本轮启动开发客户端并完成
初始资源加载，但未进入形变影片确认跳变，也未触发旧日志中的文件监视回调异常。

### 6.2 映射页交互、本地化与源模型拾取（代码已修复，游戏内待确认）

- **窄窗元素溢出**：编辑器已移出可缩放浮窗，迁到 BBS Dashboard 全屏面板；列宽由 BBS 分隔线调整并保留设置。
- **底部警告未本地化**：原代码尝试从英文校验字符串解析数量。现在直接依据校验结果里的未映射 Cube 列表生成本地化提示，不再把英文警告文本传入界面。
- **源模型拾取偏差**：屏幕投影缓存只按模型 ID 区分；源拾取窗和右下形变预览都渲染源模型时，后渲染的预览会覆盖源视口投影。现在缓存按“视口实例 + 模型 ID”隔离，并新增了同模型多视口回归测试。
- **操作发现性**：主要命令使用 BBS 原生图标 action bar 与本地化 tooltip；源、目标和映射列使用 BBS `UISplitter`，可拖动且双击复位。
- **进度与视角**：预览使用 BBS `UISliderTrackpad`；进度到 100% 切换目标模型时保留相机，只在源/目标模型对改变时重新适配视角。
- **允许不完整映射**：tooltip 已说明，未映射源 Cube 保持原形、未映射目标 Cube 随目标模型在终点出现；关闭该选项则不完整映射回退硬切。
- **同模型重组**：源模型和目标模型 ID 相同时不再被运行时短路；只要映射组改变 Cube 关系，仍会生成并渲染几何过渡。
- **关键帧目标推导**：目标现在取同轨道上下一个不同 tick 的 Model 关键帧，即使模型 ID 与源相同也保留；只跳过同 tick 并列键和空值。
- 当前 UI/拾取、自动匹配、预览滑块和端点相机回归共通过 128/128 测试。页面返回、图标提示、分栏调整、100% 视角连续性、本地化和源模型拾取仍待游戏内人工复验。

### 6.3 用户已确认不卡顿

用户确认「录制起来似乎没有卡顿」。所以性能问题已排除，不必再优化 `hashCode` 之类。

### 6.4 映射失效提示

换模型后旧映射会失效，全屏映射编辑页会显示具体问题。
日志会打印具体原因（已加诊断日志）。处理方式是**手动在编辑器里「全部清空」后重建**。
用户暂未要求自动清理（有误删风险）。

---

## 七、已知限制（详见 docs/MODEL_MORPH_1.20.x.md 第 11 节）

1. 仅 1.20.x
2. 不做 LabPBR `_n` / `_s` 混合（强 PBR 材质下中间帧光照会偏）
3. 焊接模型（weld bindings）不做形变
4. 含 OBJ 网格的模型不支持（`isCubeOnly()` 为假直接不建方案）
5. 不支持 N:N
6. 未映射的目标 Cube 在过渡期不出现
7. 重叠面做到确定性但不保证无缝（极窄进度带内可能 z-fighting）
8. 镜像/零缩放/剪切/退化 → 退化为逐顶点线性插值
9. 「自动匹配」是全覆盖的，会替换现有全部映射组

---

## 八、尚未实施的优化（用户提过但未做）

按优先级：

1. **双击映射行直接删除**（减少一次点击）
2. **未映射 Cube 用不同底色标记**（现有 tint 只标了「已映射」和「无效」，未映射是默认色）
3. **映射表按类型/源模型筛选**

用户之前还提过一些已完成的优化，不要重复做：
- ✅ 映射行两行显示（避免 1:N 被截断）
- ✅ 点击映射行联动两侧树定位 + 3D 高亮
- ✅ 布局按比例自适应（视口 45%，列表保底 5 行；编辑器迁到 BBS Dashboard 全屏面板）
- ✅ 拾取投影实际面并按深度选前景，缓存按视口和模型隔离；完整骨骼路径与 Cube 序号避免重名误选
- ✅ 全屏映射页底部的未映射数量与错误/警告详情使用 L10n 文案
- ✅ 顶部操作接入 BBS 原生 action bar；分栏迁移到 BBS-FS `UIDockLayout` / `EditorLayoutNode`
- ✅ 参考 BBS-FS 映射工作流：左列源预览/源结构树，中列目标预览/目标结构树，右侧建立映射卡片与关系表
- ✅ 顶部 action bar 提供“映射编辑 / 形变展示”模式切换，形变展示模式独占预览区
- ✅ 映射编辑模式隐藏形变预览与进度条，关系表和两侧骨骼树铺满可用高度
- ✅ 形变预览独立为第三个顶部 Dock 面板，与源/目标静态预览并列，并按固定速度循环当前形变
- ✅ 左右列与上下行均由 BBS DockLayout 调整；中央交点支持上下左右改变四块区域大小，顶部不再保留额外空白
- ✅ 3D 视口画真实立方体棱边（不是外接矩形）
- ✅ 悬浮高亮用 BBS 原版半透明白
- ✅ Ctrl 多选 / Shift 范围选择
- ✅ 撤销重做（Ctrl+Z / Ctrl+Y）
- ✅ 映射预设（右键映射列表，BBS 原生 `UIDataContextMenu`）

---

## 九、与用户协作的经验（很重要）

这些是踩坑总结，能显著提高效率：

1. **用户能看游戏画面，你看不到。** 涉及视觉的问题，必须请用户描述或截图。
2. **不要靠猜。** 每次「改一版试试」都可能浪费一轮。优先做：
   - 读 BBS 2.7 jar 的 `javap` 确认 API
   - 写离线探针（编译小 Java 类，用真实模型数据跑）验证数学
   - 必要时临时加诊断日志做一次本地验证；合并前删除拾取和渲染热路径上的临时日志
3. **日志只保留运行所需信息。** 映射方案失败和贴图失败会记录警告；拾取诊断日志已在 PR 清理中移除，
   不要再把逐帧采集或每次点击的调试输出留在正式代码里。
4. **用户对「突兀」「不自然」很敏感**，但对技术细节不感兴趣。
   回答要直接说结论和下一步，不要长篇解释。
5. **改动要小而可验证。** 曾经一次性改了布局和着色器路径，结果引入新问题。
6. **每次改动后必须跑 `bash gradlew test --offline`**（当前 128 例，几秒完成）。
7. **提交要原子化。** 现有 8 个提交按逻辑层拆分，请保持这个习惯。

---

## 十、接手后的建议动作顺序

1. 读本文档 + `docs/MODEL_MORPH_1.20.x.md`（约 1325 行，含逐轮联调记录）
2. 跑 `bash gradlew test --offline` 确认基线 128/128
3. 向用户确认当前最想解决的问题（大概率是 6.1 的跳变）
4. 映射编辑器现挂载在 BBS `UIDashboard` 全屏宿主；布局看 `UICubeMappingEditorPanel.java` 的 `buildLayout` / `resize`
5. 如果需要改几何，**先写离线探针验证**，再动 `MutationCube` / `MorphPlan`

---

## 十一、快速索引：常见任务去哪改

| 想改什么 | 改哪个文件 |
| --- | --- |
| 形变几何形状/时序 | `client/morph/MutationCube.java`、`MorphPlan.java` |
| 贴图混合/透明度 | `client/morph/MorphTextures.java` |
| 映射校验规则 | `client/morph/ModelMorphValidation.java` |
| 自动匹配算法 | `client/morph/MorphAutoMatch.java` |
| 全屏编辑器布局 | `client/ui/morph/UICubeMappingEditorPanel.java` 的 `buildLayout` / `resize` |
| 3D 拾取 | `client/morph/MorphScreenPicks.java` + `CubicCubeRendererMorphMixin` |
| 关键帧面板入口 | `mixin/morph/UIModelKeyframeFactoryMorphMixin.java` |
| 渲染接管时机 | `mixin/morph/ModelInstanceMorphMixin.java` |
| 顶点提交 | `mixin/morph/CubicCubeRendererMorphMixin.java` |
| 预设 | `client/morph/MorphMappingPresets.java` |
| 本地化文案 | `src/main/resources/assets/bbspp/strings/{en_us,zh_cn}.json` |

---

**最后**：这个功能已经经过大量迭代，很多看起来「奇怪」的写法背后都有踩坑记录
（见 `docs/MODEL_MORPH_1.20.x.md` 的逐轮联调章节）。**改之前先搜一下文档里有没有相关记录**，
能避免重走弯路。
