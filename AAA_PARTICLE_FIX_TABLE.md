# AAA 粒子问题修复表

> 基于用户反馈（坐标偏移 + 循环不正确）、用户修复 jar `BBS-Cubed-3.1.1+1.20.1_AAA_Coordinates_V7_FIXED2.jar` 反编译分析、以及当前源码（3.2.1）逐行审查生成。
> 本表仅做问题记录与修复方案规划，暂不修改代码。

---

## 一、坐标偏移问题

### C-01：世界模式矩阵乘法顺序与坐标系不一致

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 第 983-1000 行 |
| **现象** | 粒子在世界中跟随模型方块/实体时，位置有固定偏移，旋转角度也可能不对 |
| **根因** | 当前计算 `tempMatrix.set(inverseViewRotation).mul(pose)`，将视图逆旋转左乘到模型矩阵上。Effekseer 的 `setTransformMatrix` 期望的是**世界空间行主序 3×4 矩阵**，而 `pose`（MatrixStack 顶层）已经包含了模型→世界变换，再乘 inverseViewRotation 会引入额外的旋转。正确做法应直接从 `pose` 提取世界坐标，或确认乘法顺序为 `pose * inverseViewRotation` 而非 `inverseViewRotation * pose` |
| **影响场景** | 所有世界模式（模型方块、实体、影片演员）的 AAA 粒子 |
| **用户修复尝试** | `aaa.v7.StableCoordinates` 通过 ThreadLocal 栈在 `FilmBasisMixin`/`ModelBlockBasisMixin`/`WorldBasisMixin` 中捕获基变换矩阵，用 `CoordinateMath.basis()` 和 `nativeMatrix()` 重新计算，绕过当前的矩阵乘法逻辑 |
| **修复方案** | 1. 验证 `inverseViewRotation * pose` vs `pose * inverseViewRotation` 哪个正确；2. 直接从 `context.stack.peek()` 提取世界位置（`pose.m30/m31/m32` + 摄像机位置），旋转部分从 `pose` 的 3×3 子矩阵提取；3. 与用户 v7 方案对比，确认哪种坐标系与 Effekseer native 层一致 |
| **优先级** | 高 |

### C-02：模型方块渲染原点偏移

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 模型方块路径（727-734行获取 `modelBlockSource`，983-1000行坐标计算） |
| **现象** | 粒子附着在模型方块上时，Y 轴偏低/偏高约 0.5 格，或与模型视觉中心不重合 |
| **根因** | 模型方块的 `BlockEntityRenderDispatcher` 渲染时，MatrixStack 会先 `translate(0.5, 0, 0.5)` 将原点移到方块中心，但模型本身可能有自己的偏移（如 BBS 模型方块的 `ModelBlockEntity` 渲染器会额外做平移/旋转）。当前代码直接消费 MatrixStack 顶层，没有区分"方块中心"和"模型实际锚点" |
| **影响场景** | 模型方块上的 AAA 粒子 |
| **用户修复尝试** | `ModelBlockBasisMixin` 注入到 `ModelBlockEntity.render()`，在 enter/exit 时捕获 `ModelBlockEntity` 的完整状态（含位置、旋转、模型偏移），通过 `StableCoordinates.beginBlock()` 建立独立的坐标基 |
| **修复方案** | 在 `ModelBlockBasisMixin`（或直接在 renderer 中）获取 `ModelBlockEntity` 的实际渲染变换，而非仅依赖 MatrixStack；确认模型方块的粒子锚点应与模型原点对齐还是方块中心对齐 |
| **优先级** | 高 |

### C-03：影片模式坐标未跟随影片摄像机

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` Source.FILM 分支（需确认具体行号，当前 render3D 中通过 `context.type` 区分） |
| **现象** | 在影片编辑器中播放时，粒子位置不跟随影片演员移动，或在影片摄像机移动时粒子出现漂移 |
| **根因** | 影片模式下，`FormRenderingContext` 的 `camera` 是 BBS 影片摄像机而非 Minecraft 世界摄像机。当前世界模式坐标计算使用 `context.camera.position`（Minecraft 摄像机），在影片上下文中这个位置可能不正确。影片演员的位置需要从 `FilmControllerContext` 中获取，并应用影片的时间线变换 |
| **影响场景** | 影片编辑器中预览/录制 AAA 粒子 |
| **用户修复尝试** | `FilmBasisMixin` 注入到 `FilmControllerContext`，在 enter/exit 时通过反射获取影片的 `pose`、`camera`、`progress` 等状态，建立影片坐标基 |
| **修复方案** | 1. 在影片模式下，从 `FilmControllerContext` 获取演员的世界位置和影片摄像机变换；2. 区分"影片预览"（PREVIEW 类型）和"影片录制/播放"（FILM 类型）的坐标计算；3. 确保粒子的 `setTransformMatrix` 使用影片空间而非世界空间 |
| **优先级** | 高 |

### C-04：预览模式与世界模式坐标系不统一

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 第 926-980 行（预览）vs 981-1002 行（世界） |
| **现象** | 表单编辑器中预览的粒子位置/旋转，与放到世界中后的实际效果不一致 |
| **根因** | 预览模式用 `emitter.setPosition/Rotation/Scale`（欧拉角 + 平移），世界模式用 `emitter.setTransformMatrix`（3×4 矩阵）。两种 API 在 Effekseer native 层的坐标系处理可能不同（如欧拉角旋转顺序、Y-up 约定），导致同一组 transform 参数在两种模式下效果不同 |
| **影响场景** | 表单编辑器预览 vs 实际放置 |
| **修复方案** | 统一两种模式都使用 `setTransformMatrix`，从 `form.transform` 构建矩阵后传入；或统一使用 `setPosition/Rotation/Scale`，确认欧拉角顺序与 Effekseer 一致 |
| **优先级** | 中 |

### C-05：实体位置未使用渲染插值

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 世界模式坐标计算 |
| **现象** | 移动中的实体上的粒子有轻微卡顿/滞后，或在高帧率下位置抖动 |
| **根因** | 当前坐标完全依赖 `context.stack.peek()` 的 MatrixStack，而 BBS 的实体渲染器在构建 MatrixStack 时可能使用了实体的当前位置而非插值位置（`Lerps.lerp(prevX, X, tickDelta)`）。Minecraft 渲染规范要求使用插值位置避免抖动 |
| **影响场景** | 移动中的实体（玩家、生物、载具）上的 AAA 粒子 |
| **修复方案** | 确认 BBS `FormUtilsClient.render()` 或实体渲染器是否已做插值；若未做，在 renderer 中从 `entity.lastRenderX/Y/Z` 和 `entity.getX/Y/Z` 手动插值 |
| **优先级** | 中 |

---

## 二、循环不正确问题

### L-01：默认循环依赖 emitter 自然结束后重建，存在间隙

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 第 804-848 行（主动倒带）+ 850-903 行（后备重建） |
| **现象** | 开启循环后，粒子播放完一轮会短暂消失/闪烁一下再重新开始，不是无缝循环 |
| **根因** | 代码注释明确说明："默认循环不使用 maxTerm() 主动倒带。部分复杂特效的 GetTermMax 只覆盖发射阶段，子节点、拖尾或残留视觉仍会继续存在；按该值倒带会在约 0.3～0.5 秒错误重开。默认循环改为等待 emitter.exists() 真正结束后走下方后备重建。" 但后备重建（`emitter = null; ensureEmitter()`）会导致至少一帧的间隙，且新 emitter 从第 0 帧开始，与旧 emitter 的尾部无法衔接 |
| **影响场景** | 所有未设置自定义循环范围（loopStart/loopEnd 为 0）的循环粒子 |
| **修复方案** | 1. 改进主动倒带逻辑：不依赖 `effectMaxTerm`，而是通过 `emitter.getProgress()` 或 `emitter.exists()` 的状态变化检测循环点；2. 在 emitter 即将结束前（如 exists() 返回 false 的前一帧）主动 `setProgress(loopStart)`，避免重建；3. 对 `effectMaxTerm` 不准确的特效，提供"循环帧"用户可手动指定的兜底选项 |
| **优先级** | 高 |

### L-02：effectMaxTerm 获取不准确导致自定义循环范围错误

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.ensureEmitter()` 中 `this.effectMaxTerm = effect.getTerm()` |
| **现象** | 设置了自定义循环结束帧（loopEnd）后，粒子在错误的时间点循环，或 loopEnd 被忽略 |
| **根因** | `effect.getTerm()` 返回的是 Effekseer 特效根节点的持续时间，但复杂特效可能有多个子发射器，各子发射器的持续时间不同。根节点的 term 可能小于实际视觉持续时间（如拖尾、粒子生命周期），导致 `maxTerm` 计算偏小 |
| **影响场景** | 设置了 loopStart/loopEnd 的复杂特效 |
| **修复方案** | 1. 遍历特效的所有子节点，取最大的 term 作为 `effectMaxTerm`；2. 或在用户设置了 loopEnd 时，完全以用户的 loopEnd 为准，不与 `effectMaxTerm` 取交集；3. 添加调试日志输出实际的 effectMaxTerm 值 |
| **优先级** | 高 |

### L-03：循环倒带后触发器状态未正确恢复

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 第 845 行 `this.resetTriggers()` + 第 873 行 `this.resetTriggers()` |
| **现象** | 依赖触发器（Trigger 0-3）启动子发射器的特效，循环一次后子发射器不再发射，或触发器信号丢失 |
| **根因** | 循环倒带时调用 `resetTriggers()` 清除了所有待发送触发器信号，但如果特效的子发射器需要在第 0 帧接收触发器才能启动，倒带后没有重新发送初始触发器。`manualTriggerPulse` 和 `pendingTriggers` 机制在循环后状态不一致 |
| **影响场景** | 使用了 Effekseer 触发器的特效（如爆炸后触发碎片、循环触发音效粒子） |
| **修复方案** | 1. 倒带后根据 `form.trigger0-3` 的初始值重新发送触发器；2. 区分"循环倒带"和"首次播放"的触发器初始化逻辑；3. 检查 `consumePendingTriggers()` 在倒带后是否被正确调用 |
| **优先级** | 中 |

### L-04：editorProgress 与 emitter 内部进度漂移

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 第 1011-1058 行（editorProgress 更新） |
| **现象** | 长时间循环后，粒子动画速度逐渐变快/变慢，或与时间线不同步 |
| **根因** | `editorProgress` 基于墙钟时间（`System.currentTimeMillis()`）计算帧增量，而 emitter 内部有自己的时间步进（基于 `setProgress`）。两者在卡顿、掉帧、暂停后会产生累积误差。`framesToAdd` 被限制为最大 10 帧，但没有与 emitter 的实际进度做同步校验 |
| **影响场景** | 长时间播放/循环，或游戏卡顿后 |
| **修复方案** | 1. 定期（如每 60 帧）用 `emitter.getProgress()` 校准 `editorProgress`；2. 暂停/恢复后重置 `lastEditorTime` 避免时间跳跃；3. 考虑直接使用 emitter 内部进度而非独立维护 editorProgress |
| **优先级** | 中 |

### L-05：智能定格（forceFreeze）模式循环逻辑复杂且易出错

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 第 1038-1058 行 + `tick()` 中相关逻辑 |
| **现象** | 开启 forceFreeze 且 loopStart == loopEnd 时，粒子有时不能正确停在目标帧，或需要播放多轮才定格 |
| **根因** | 智能定格逻辑分散在 `render3D()` 和 `tick()` 两处：render3D 中 `smartFreezeLoops < 1` 时拨回 `target - 1`，tick 中又有 `smartFreezeLoops >= 1 && editorProgress >= target` 时设置 `isSmartPaused`。两处的 `smartFreezeLoops` 递增和判断条件不完全一致，且 `tick()` 可能在 `render3D()` 之前或之后执行，导致状态竞争 |
| **影响场景** | 使用 forceFreeze 定格单帧的特效 |
| **修复方案** | 1. 将智能定格逻辑统一到一处（建议在 render3D 中，因为它能访问渲染上下文）；2. 简化状态机：未播放→播放中→已定格，三个状态明确转换；3. 移除 tick() 中的重复逻辑 |
| **优先级** | 中 |

### L-06：setProgress 倒带不会清除已发射粒子

| 项目 | 内容 |
|---|---|
| **位置** | `AAAParticleFormRenderer.render3D()` 第 843 行 `this.emitter.setProgress(this.editorProgress)` |
| **现象** | 循环倒带后，上一轮的残留粒子（如拖尾、烟雾）仍然存在，与新一轮粒子重叠，导致视觉混乱 |
| **根因** | Effekseer 的 `setProgress()` 只是跳转播放头，不会销毁已经发射的粒子实例。对于有长生命周期粒子的特效（如烟雾、火焰拖尾），倒带后旧粒子继续存在直到自然消亡 |
| **影响场景** | 有长生命周期子粒子/拖尾的特效循环 |
| **修复方案** | 1. 倒带前调用 `emitter.stop()` 然后立即 `play()` + `setProgress(loopStart)`，强制清除所有粒子；2. 或调查 Effekseer 是否有"清除所有活跃粒子"的 API；3. 对不需要清除的特效（如纯发射型）保留当前行为 |
| **优先级** | 中 |

---

## 三、其他相关问题

### O-01：用户修复 jar 基于 3.1.1，与当前 3.2.1 源码存在差异

| 项目 | 内容 |
|---|---|
| **位置** | 用户 jar 版本 3.1.1 vs 当前源码 3.2.1 |
| **说明** | 用户的 `aaa.v7` 修复包是在 3.1.1 基础上新增的，当前 3.2.1 源码中已经有一些坐标相关的改进（如 3×4 矩阵传递、X-Ray 迁移等）。直接移植 v7 代码需要先确认哪些问题在 3.2.1 中已部分修复 |
| **修复方案** | 逐项对比 v7 修复与当前源码，只移植仍存在的问题对应的修复 |
| **优先级** | 低（前置工作） |

### O-02：日志中无 AAA 相关崩溃，问题为视觉层面

| 项目 | 内容 |
|---|---|
| **位置** | `latest (3).log` |
| **说明** | 用户日志中没有 AAA 粒子的异常/崩溃，所有错误均来自其他模组（物品 JSON、认证等）。坐标偏移和循环问题是纯视觉/逻辑层面的，不会产生崩溃日志 |
| **修复方案** | 需要用户提供具体的复现步骤和特效文件，才能精确验证修复效果 |
| **优先级** | 低（信息收集） |

---

## 四、修复优先级汇总

| 优先级 | 编号 | 问题 | 预计工作量 |
|---|---|---|---|
| 高 | C-01 | 世界模式矩阵乘法顺序 | 中 |
| 高 | C-02 | 模型方块渲染原点偏移 | 中 |
| 高 | C-03 | 影片模式坐标未跟随影片摄像机 | 大 |
| 高 | L-01 | 默认循环依赖重建存在间隙 | 中 |
| 高 | L-02 | effectMaxTerm 不准确 | 小 |
| 中 | C-04 | 预览与世界模式坐标系不统一 | 中 |
| 中 | C-05 | 实体位置未使用渲染插值 | 小 |
| 中 | L-03 | 循环倒带后触发器状态未恢复 | 中 |
| 中 | L-04 | editorProgress 与 emitter 进度漂移 | 中 |
| 中 | L-05 | 智能定格逻辑分散易出错 | 中 |
| 中 | L-06 | setProgress 倒带不清除已发射粒子 | 小 |
| 低 | O-01 | 用户 jar 版本差异需对比 | 小 |
| 低 | O-02 | 需用户提供复现步骤 | — |

---

## 五、建议的修复实施顺序

1. **第一步**：C-01 + C-02 — 修复世界模式和模型方块的坐标基础，这是最明显的偏移问题
2. **第二步**：L-01 + L-02 — 修复默认循环和 effectMaxTerm，解决循环不无缝的核心问题
3. **第三步**：C-03 — 修复影片模式坐标，需要深入理解 BBS 影片渲染管线
4. **第四步**：L-03 + L-06 — 修复循环后的触发器和残留粒子问题
5. **第五步**：C-04 + C-05 + L-04 + L-05 — 统一坐标系、插值、进度同步、定格逻辑
6. **验证**：用用户提供的特效文件在三种场景（模型方块、实体、影片）中逐一验证
