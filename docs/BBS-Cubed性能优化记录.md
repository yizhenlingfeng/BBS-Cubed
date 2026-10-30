# BBS-Cubed 自身性能占用整理与优化记录

- 项目：BBS-Cubed（mod id `bbsplusplus`，包名 `gbeic.bbsplusplus`）
- 源码版本：3.6.3（客户端实际运行 3.6.1，两版在本页涉及的文件上逻辑一致）
- 整理日期：2026-10-30
- 范围：只统计**本项目新增或注入**的每帧开销；BBS 本体、VFX-LIGHTS、BBS-POV 等第三方附属不在本页重复（见 `docs/影片编辑器性能诊断报告.md`）

## 一、结论速览

1. 本项目绝大多数 mixin 是**事件型或条件型**（只在特定操作、特定开关下介入），默认不产生每帧成本。
2. 真正会随"打开影片编辑器"持续付费的，集中在四处：**UI 树每帧遍历**、**Iris 焦点曲线取值**、**纹理补间取值**、**静默贴图请求**。
3. 这四处的共同特征不是 GPU 负载，而是**每帧的分配与同步**：小对象分配（GC 压力）+ 一次 `glFinish`（管线同步）。它们不体现在 F3 的帧时间曲线上，却会在 UI 密集界面里稳定拉低帧率。
4. 本轮改了 4 处，均为低风险等价改写；编译已通过（`gradlew compileJava` / `BUILD SUCCESSFUL`）。剩余 2 处（运动路径签名、模型调色快路径）风险略高，列在第四节。

## 二、本项目每帧性能占用总表

| 编号 | 位置 | 触发条件 | 每帧成本（改前） | 处理 | 状态 |
| --- | --- | --- | --- | --- | --- |
| P1 | `client/compat/iris/ShaderCurveState.java:82` `getCenterDepthTexture()` | 开 Iris + 影片/相机曲线里有 `center_depth` | 每次取值：`glGetInteger` + 绑定 FBO + 清屏 + **`glFinish()`**（整管线同步） | 缓存上次写入值，值未变直接返回；删除 `glFinish` | 已改 |
| P2 | `mixin/UIElementMixin.java:39` `bbspp$renderChildrenSnapshot` | 任意 UI 元素每次 `render` | `new ArrayList<>(children)` + 拷贝 + 迭代器 | 每个元素复用一个快照 `List`（`@Unique` 字段） | 已改 |
| P3 | `client/texture/TextureTweenManager.java:113` 保护集合构建 | 存在纹理补间（每帧每个补间模型一次） | `new HashSet` + 遍历整张 `CACHE` + `TextureTweenSourceCache.trim()` | 新建条目时扫一次，其余每 32 次取用扫一次；集合复用 | 已改 |
| P4 | `mixin/TextureManagerMixin.java:124` `bbspp$isPBRSidecar` | 开光影后每次静默 `getTexture`（Iris 会请求大量 `_n/_s` 伴随贴图） | 每个请求 `path.toLowerCase(Locale.ROOT)`，生成新字符串 | 先做零分配后缀判断，仅当路径含大写字母才小写化 | 已改 |
| P5 | `mixin/MotionPathMixin.java:169` `bbsplusplus$appendSignature` | 开启运动路径且正在显示骨骼轨迹 | 每帧对每个通道 `channel.toData().toString().hashCode()`（整条通道 NBT 序列化） | 未改：需要可靠的"通道已修改"信号，否则会错误复用轨迹缓存 | 待改 |
| P6 | `mixin/client/ModelInstanceTextureGradeMixin.java:63` | 每个 `ModelInstance.render`（每模型每帧） | `new Color(1,1,1,0)` + 遍历模型全部骨骼组查光效 | 可复用 `Color` 实例，并在 tint/whiten 都为空时减少判断 | 待改 |
| P7 | `mixin/client/CubicVAORendererMixin.java:158` | Iris 开启 + 骨骼级 PBR 任一值 > 0 | 每骨骼拼 6 段字符串做 variant key | 条件路径，默认成本为 0，暂不处理 | 保留 |
| P8 | `client/screen/ColorGradeRenderer.java:401` `apply()` | 影片里存在色调/暗角/畸变/电影感剪辑 | 全屏 `glCopyTexSubImage2D` + 全屏调色 pass | GPU 侧必要工作；已有 `need*` 短路 | 保留 |
| P9 | `client/texture/TextureTweenManager.java` `RuntimeEntry.update` | 补间进度跨过一个步进 | 整张纹理 CPU 重算 + 全量 `glTexSubImage2D` | 与补间步数绑定，属设计成本；可降 `STEPS` 但会牺牲观感 | 保留 |
| P10 | `mixin/UIElementMixin.java:49` `bbspp$syncFilmLibraryLayoutOnRender` | 实现了 `IFilmLibraryLayoutToggle` 的元素 | 一次 `instanceof` 判断（命中时才同步布局） | 已经很轻，保留 | 保留 |
| P11 | `client/screen/TextureGradeRenderContext.java:40` `select()` | 每次绘制图层 | 先判空栈，只有开启 form 调色/光效时才进入 | 已是最快路径，保留 | 保留 |
| P12 | `mixin/TextureManagerMixin.java:62` PBR 伴随贴图重试 | 开光影 + 伴随贴图缺失 | 已按 3 秒节流，单次最多一次 `getFile`/`exists` | 无需改动 | 保留 |

## 三、四处改动的依据与安全性

### P1 焦点深度纹理（`ShaderCurveState`）

原实现每次被 Iris 询问"屏幕中心深度"时，都会重新绑定 FBO、清一次 1×1、再 `glFinish()`。
`glFinish` 要求 GPU 把队列里**所有**已提交命令执行完才返回，等于每帧在渲染线程上插一次硬同步；Iris 开启时这条路径每帧至少走一次。

改动：
- 记录上一次写入的 `lastCenterDepth`，值相同直接返回已有纹理（相机静止、对焦距离不变时省掉全部 GL 调用）。
- 去掉 `glFinish`：对同一 FBO 的清屏与该纹理之后的采样本来就按提交顺序执行，OpenGL 保证同上下文内的可见性，不需要额外的管线同步。

风险：低。唯一语义变化是"值未变时不再重写纹理"，而写入的值本来就相同。

### P2 UI 子控件快照（`UIElementMixin`）

原实现为了规避"渲染期间增删子控件导致 `ConcurrentModificationException`"，每次都 `new ArrayList<>(children)`。
影片编辑器的 UI 树有几百个节点（时间轴、轨道行、右侧属性面板、曲线编辑器……），每个节点每帧都要渲染一次，即**每帧数百次 ArrayList 分配**（按 60 FPS 约 2–4 万次/秒），全部是朝生夕死的小对象。

改动：在 `UIElement` 上挂一个 `@Unique` 的复用列表，`clear()` + `addAll()` 后返回其迭代器。
语义完全等价（仍是快照），只是不再分配；容量在元素生命周期内复用。

风险：低。前提是同一元素不会在同一次 `render` 里递归渲染自己（BBS 的 UI 树是树形，不存在该情况）。

### P3 纹理补间保护集合（`TextureTweenManager`）

补间在每帧每模型取用时，都会新建 `HashSet`、遍历整张运行时 `CACHE` 收集"仍被引用"的源贴图，再调用 `TextureTweenSourceCache.trim()`。缓存条目多时这是 O(条目数) 的每帧工作，外加一次集合分配。

改动：把"收集 + 裁剪"节流为**新建条目时执行一次、其余每 32 次取用执行一次**，并复用一个静态集合。
裁剪只决定不再使用的源贴图何时被释放，延迟回收不影响补间结果；真正被本帧使用的 `a`/`b` 本来就通过 `protectedSources` 保护。

风险：低。最坏情况是废弃源贴图多驻留最多 32 次取用的时间（通常 < 1 秒）。

### P4 伴随贴图判定（`TextureManagerMixin`）

开光影后，Iris 会为每个用到的纹理请求 `_n`/`_s`/`_e` 等伴随贴图；模型皮肤大多没有这些文件，于是这个判断会高频执行。
原实现对每个请求都 `toLowerCase(Locale.ROOT)`，即每次分配一个新字符串。

改动：先用原样后缀做零分配匹配（绝大多数资源路径本身就是小写），只有路径确实含大写字母时才回退到小写化。
行为与原实现一致，只是把常见路径变成纯字符比较。

风险：低。

## 四、未处理项与原因

- **P5 运动路径签名**：签名的用途是给轨迹缓存做 key，必须"动画一改就变"。目前拿不到 BBS 通道的修改计数，若改成粗粒度缓存会错误复用旧轨迹（表现为拖关键帧时轨迹不跟随）。可行方案是给 `KeyframeChannel` 增加 dirty 计数（需要动 BBS 本体或再加一层 mixin），属于下一步工作。
- **P6 模型调色判定**：`new Color` 每个模型每次渲染一个，量级远小于 P2；且 `Color` 的复用要确认 BBS `Color` 是否会被下游持有引用（`ModelTextureGradeShader.select` 内部可能保留），贸然复用有别名风险，暂缓。
- **P8/P9**：属于"用户明确要的画面效果"，优化只能靠降采样或降步数，会改变观感，不默认做。

## 五、如何验证收益

1. **对照场景**（同一存档、同一影片、同一镜头）：
   - S-A：仅打开影片编辑器，播放头静止（UI 重绘为主）；
   - S-B：播放含 3 个纹理补间模型的片段；
   - S-C：开 Iris + 相机曲线里带 `center_depth` 的片段。
2. **读数**：F3 帧时间 + BBS 自带 profiler overlay（`config/bbs/settings/bbs.json` 的 `performance.profiler_overlay`，或设置界面"性能"分类）。
3. **预期**：
   - S-A 提升取决于 UI 节点数，编辑器满载时最明显（分配消除，减少 GC 抖动）；
   - S-C 在 Iris 下最明显（去掉每帧 `glFinish`，典型 0.3–3 ms/帧，取决于驱动与 GPU 队列深度）；
   - S-B 在补间模型多时有小幅提升（每帧集合分配与 O(n) 扫描消失）。

## 六、构建与替换

```powershell
cd D:\Desktop\Mod\源码\BBS-Cubed
.\gradlew.bat build
```

产物在 `build/1.20.1/libs/BBS-Cubed-3.6.3+1.20.1-1.20.4.jar`（1 975 229 B），替换客户端 `mods` 目录里的 `BBS-Cubed-3.6.1+1.20.1-1.20.4.jar` 即可（建议先备份原 jar）。
客户端为 BBS 2.7，本项目源码面向 2.7，无需改依赖；替换后 `fabric.mod.json` 里的版本号会从 3.6.1 变成 3.6.3。

## 七、改动清单（本轮）

- `src/main/java/gbeic/bbsplusplus/client/compat/iris/ShaderCurveState.java`
- `src/main/java/gbeic/bbsplusplus/mixin/UIElementMixin.java`
- `src/main/java/gbeic/bbsplusplus/client/texture/TextureTweenManager.java`
- `src/main/java/gbeic/bbsplusplus/mixin/TextureManagerMixin.java`

编译状态：`gradlew compileJava --offline`、`gradlew build --offline` 均 **BUILD SUCCESSFUL**（Gradle 9.7.1 / Loom 1.15.5，2026-10-30 19:59）。

产物校验（对 `BBS-Cubed-3.6.3+1.20.1-1.20.4.jar` 反汇编）：
- `UIElementMixin` 已包含新字段 `private java.util.List<IUIElement> bbspp$childSnapshot`；
- `ShaderCurveState` 已包含 `lastCenterDepth`，方法字节码中 `glFinish` 出现次数为 **0**。
