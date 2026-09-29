# snow_actions × bbs-physics-engine 兼容性问题修复报告

> 调查日期：2026-09-30 ｜ 方式：静态源码分析（本项目 src ↔ BBS FS 2.7 源码 ↔ bbs-physics-engine-master 源码）｜ 暂不做修改

## 一、用户反馈现象

1. 物理化模型（挂布娃娃/刚体驱动的模型）出现**骨骼不再移动、但仍在旋转**；
2. **BBSFS 原生飞行动画**（`flying` / `flying_idle` 状态动作）被破坏。

## 二、结论摘要

snow_actions 对 BBSFS 动作管线的接管共有 **4 个注入点**（`ActionPlayback.getTick`、
`ActionsConfigKeyframeFactory.interpolate`、`KeyframeSegment.createInterpolated`、
`ActionConfig.isDefault/equals/copy`）。其中 `getTick` 劫持使动画帧完全脱离
`ActionPlayback` 自身的时间累计，**只随 actions 轨道插值结果（`timelineFrame`）变化**；
而轨道求值器 `ActionTimelineEvaluator` 在「clip 窗口外 / 配对失败 / 被后续关键帧截断」时
会输出**陈旧的固定帧或空名配置**。物理模组恰好以「逐整 tick 重放 actions 轨道 →
`collectMatrices` 采样纯动画姿态」作为布娃娃驱动目标，于是：

- **动画平移通道冻结**（帧恒定）→ 物理驱动的目标位置不再前进 →「骨骼不移动」；
- **物理模拟/布娃娃替换仍在运行**（`RagdollPoseApplier` 持续写 `orient`）→「但仍然旋转」；
- `flying` 等状态动作被空名配置顶掉或帧被钉死 →「飞行动画被破坏」。

## 三、问题清单（按严重度排序）

### P0-1　`getTick` 劫持忽略 transition 与时间累计，帧只在特定条件下刷新
- **位置**：`mixin/ActionPlaybackMixin.java:28-49`
- 劫持直接返回 `computeSeekFrame(length, timelineFrame, speed, config.tick, ...)`，
  完全忽略入参 `transition` 和 playback 自身的 `ticks` 累计。
- `timelineFrame` 只在「actions 属性轨道被重新应用 **且** `ensureAnimator` 检测到配置变化」
  时刷新。一旦下述 P0-2/P0-3 使求值器输出陈旧值，动画帧即**恒定不动**。
- 对照物理模组：`SceneCast.apply(tick)` → `SceneActor.sample` → `collectMatrices`
  （bbs-physics `SceneActor.java:172-189`、`FilmScene.evaluatePose`）整 tick 采样，
  正好踩中"帧被钉死"的窗口。

### P0-2　clip 窗口外写入空名 `ActionConfig("")`，状态动作被静默丢弃
- **位置**：`timeline/ActionTimelineEvaluator.java:207-216`（`evaluateAction`，`primary == null` 分支）、
  `:136-139`（`interpolateTimelineFrames`，overlay 且 `x > 0`）
- 写入 `new ActionConfig("")` 后，BBSFS `Animator.createAction`
  （`Animator.java:140-157`）`animations.get("") == null` → 返回 null →
  `this.flying = null` 等。`pickState`（`Animator.java:228-244`）取不到 flying →
  回落到 idle/running，**飞行动画被走路动画顶替**。
- **放大器**：`ActionsTimelineEditorSupport.setTimelineContext:127-146` 会把 overlay 通道内
  **所有** timeline 驱动动作批量标记 `overlay=true`，扩大该分支的触发面。

### P0-3　`findStopTick` 把轨道上任意后续关键帧当作循环边界 + clipId 继承污染配对
- **位置**：`ActionTimelineEvaluator.java:436-451`（`findStopTick`）、`:381-434`（`createClip`）
- actions 是**单轨多动作**属性（idle/running/… 同居一条轨道）。clip 结束点之后的
  **第一个任意关键帧**（哪怕属于别的动作）即被当作 stopTick，把该 clip 截断。
- 关键帧复制/插入会**继承插值出的 clipId**（`ActionConfig.copy` 经 mixin 复制元数据），
  `createClip` 以"帧距最大"猜原始端点对，继承 marker 越多越易误配。
- 窗口被截断后落入 P0-1/P0-2 的"窗口外"分支：非 overlay 保留**陈旧 timelineFrame**
  （`interpolateTimelineFrames` 不匹配端点且非 overlay 时直接沿用 a 端配置）→ 帧冻结。

### P0-4　`AdditiveAnimator` 槽位列表缺少 flying/swimming/riding 全系
- **位置**：`cubic/animation/AdditiveAnimator.java:72-76`（applyActions 槽位数组）；
  `mixin/ModelFormCMLMixin.java:34-37`（`bbspp_ACTION_SLOTS`）
- 两处槽位列表均缺 `flying / flying_idle / swimming / swimming_idle / riding / riding_idle`
  （BBSFS 2.6+ 新增的状态动作）。叠加层中 timeline 驱动的这些动作**永远不会被应用**
  （active 路径对 timeline-driven 跳过，槽位循环又不含它们）。
- 另：`isTimelineActive`（`AdditiveAnimator.java:101-109`）**无条件返回 true**，
  死代码 + 语义错误（应判断 clip 是否处于活动窗口/权重>0）。

### P1-5　`refineEquals` 比较 `timelineFrame` 引发 animator 每 tick 重建
- **位置**：`timeline/ActionTimelineMeta.java:133-148`；对照
  `ModelFormRenderer.ensureAnimator`（FS 源 `ModelFormRenderer.java:307-332`）与
  本项目 `mixin/client/ModelFormRendererMixin.java:222-236`
- `timelineFrame` 逐 tick 前进 → equals 恒为 false → 基础 animator 每 tick `setup`、
  叠加层 animator **整体替换重建**。副作用：播放对象 churn 时 `this.active` 指向旧
  playback（陈旧帧）、切换伴随 `rewind()+fadeIn()`（闪烁）；物理 catch-up
  每帧多次采样时开销成倍放大。

### P1-6　非 loop 动作 fadeOut 后同对象早退，永不恢复
- **位置**：BBSFS `ActionPlayback.update`（`ActionPlayback.java:157-160`，`ticks` 累计
  未被劫持影响）+ `Animator.setActiveAction`（`Animator.java:336`，`active == action` 早退）
- timeline 驱动的非循环动作（dying/一次性 clip）在 `ticks >= duration` 后 fadeOut，
  blend 归零；因状态槽 playback 是同一对象，`setActiveAction` 早退使其**不再淡入**，
  姿态彻底冻结。

### P2-7　两层插值接管语义不一致
- `ActionsConfigKeyframeFactoryMixin`（factory 层，仅 `x > 0` 才清空 overlay）与
  `KeyframeSegmentCMLMixin`（segment 层，无 x 条件）先后改写同一结果，
  `evaluate` 再整体覆盖 factory 层输出。两层对"窗口外/失配"的处理不一致，
  是 P0-2/P0-3 行为不稳定的直接来源；且 `evaluate` 在**每次** `createInterpolated`
  都全通道扫描（O(关键帧×动作)），物理 catch-up 逐 tick 采样时被放大。

### P2-8　`isDefault()` 劫持对 equals 的间接干扰
- `ActionConfigMixin.java:136-143` 使 timeline 动作永非 default →
  `ActionsConfig.equals/removeDefaultActions`（`ActionsConfig.java:18-59`）的
  "默认项剔除"对 timeline 项失效，与 vanilla 配置对比时不对称，加剧 P1-5 的误判。

## 四、修复建议（方向，暂不实施）

1. **P0-1**：`getTick` 劫持加上 `+ transition * speed`；或只在求值器本轮确实产出了
   新帧（如给 config 打版本戳）时才接管，避免陈旧帧被钉死。
2. **P0-2**：窗口外不要写空名配置——保留原动作（尤其 `flying` 等状态槽）；
   或在 `Animator.createAction` 一侧对空名回退到 old playback（需 FS 侧 mixin）。
3. **P0-3**：`findStopTick` 只认**同 actionKey 且非继承**的关键帧；给 marker 记录
   来源（authored vs inherited clipId），`createClip` 排除继承 marker。
4. **P0-4**：补齐两处槽位列表（flying/flying_idle/swimming/swimming_idle/riding/riding_idle）；
   `isTimelineActive` 改为依据 clip 活动窗口与 `timelineWeight > 0` 判定。
5. **P1-5**：`refineEquals` 移除 `timelineFrame`/`timelineWeight` 的逐帧比较
   （只比较静态元数据 clipId/overlay/loop 配置）。
6. **P1-6**：timeline 驱动的 playback 跳过 `update()` 的 fadeOut 判定
   （帧由轨道接管时 `ticks` 累计无意义），或在 fadeOut 后允许重新 fadeIn。
7. **P2-7**：合并两层接管为单一入口（保留 segment 层即可），并缓存每 tick 的
   clip 配对结果。
8. **兼容性护栏**：物理模组采样期间（可检测 `RagdollPoseApplier.isEvaluating()`）
   窗口外一律输出"上一个有效帧"而非空名/陈旧帧，保证布娃娃驱动目标连续。

## 五、临时规避

- 配置项已有运行时开关：`BBSPlusPlusSettings.java:57`（`snow_actions`，默认开启）。
  **关闭后所有注入点均直接走 vanilla 路径**（各 mixin 均检查
  `CMLSettings.isSnowActionsEnabled()`），影片中已写入的 `bbspp_*` 元数据会被
  BBSFS 忽略，可立即恢复物理模组与飞行动画。关闭后需重开影片面板或重载模型。
