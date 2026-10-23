# BBS Cubed 项目整理与重构计划

> **状态**：只读审计已完成，本文件只做计划，**未修改任何代码 / 资源 / 构建脚本**。
> **基线**：分支 `3.5`，提交 `6e146f4`（另有 2 处未提交修改），`mod_version=3.6.0`，审计日期 2026-10-23。
> **对照上游**：`libs/bbs-fs-2.7-1.20.1`（BBS FS 2.7 源码快照）与 `libs/archive/bbs-2.7/*.jar`（实际编译/运行 jar）。

---

## 0. 文档说明

### 0.1 目的

把当前项目"讲清楚"，并给出一份可执行、可验收、可回滚的整理计划。重点回答四个问题：

1. 项目里现在到底有什么（代码、资源、构建、文档、仓库资产）；
2. 哪些地方会与原版 BBS FS（以及 BBS-VFX / VFX-Lights / IRLights / bbs_physics / bbs_lezy / bbs_together 等生态）产生冲突；
3. 哪些是确定的代码缺陷，哪些只是结构不好；
4. 按什么顺序整理，风险最小、收益最大。

### 0.2 方法与口径

- **只读**：grep / 文件统计 / JSON 解析 / class 文件字节码解析 / 与上游逐文件 diff；未启动游戏。
- 行数按文件物理行统计（`(Get-Content).Count`，含空行与注释；与“非空行”口径不同）；`src/main/java` 488 个文件、68,498 行；`src/main/java-1.21.11` 1 个文件、844 行；合计 **489 文件 / 69,342 行**。
- Mixin 统计含 `mixin/` 下全部 232 个 `.java`：230 个被 7 个 `*.mixins.json` 声明、1 个未声明的 Mixin、1 个 Config Plugin（非 Mixin）。
- "上游"指 `libs/bbs-fs-2.7-1.20.1` 源码 + `libs/archive/bbs-2.7/bbs-2.7-1.20.1-zh_CN.jar`；两者**并非完全一致**（见 3.5.2），凡涉及运行期行为均以 jar 字节码为准。
- 标注 **需实机确认** 的条目，是静态证据强但未运行验证的结论，不应直接当事实写进发布说明。

### 0.3 重要限制

1. 审计未启动 1.20.1 / 1.20.4 / 1.21.11 客户端，因此"功能失效"均指**注入点/加载路径不成立**，不等于玩家一定看不到功能（可能有其他兜底路径）。
2. Mixin 目标方法存在性用 BBS jar 的 class 文件逐类核对；继承方法不算存在（Mixin 只能注入目标类自身声明的方法）。
3. 仓库内 `libs/`、`run/`、`build/`、`.workbuddy/` 等本地目录不参与构建逻辑，但影响可读性与仓库体积，单独在 2.8 / 6.4 处理。

---

## 1. 执行摘要

### 1.1 一句话结论

这是一个**功能密度很高、但历史分层严重、与上游耦合极深**的客户端 addon：约 6.9 万行代码中有 1/3 是 232 个 Mixin，421 个被引用的 BBS 类型里只有 7 个来自官方 addon API；同时存在**至少 8 处已确认的静默失效注入、9 组同名双 Mixin、5 个零引用类、663 个英文缺失翻译**，以及若干会随 BBS 升级直接损坏的资源覆盖。

### 1.2 最需要先知道的 12 件事

| # | 结论 | 级别 | 关键证据 |
|---|---|---|---|
| 1 | 自动旁观模式（进/出影片编辑器切模式）**在 2.6/2.7 构建中不生效**：2.5.2 的 `UIFilmPanel` 有 `appear/close`，2.6 起移入父类 `UIDashboardPanel` 且为 `final` | P0 | `mixin/UIFilmPanelGameModeMixin.java:34,74`；2.5.2 / 2.6.1 / 2.7 jar 逐版本核对 |
| 2 | 另有 5 处注入目标方法在 2.7 中不存在（2.5.2 时均存在）：`UIList#subMouseClicked`、`UIReplayList#subMouseClicked`、`UIFilmPanel#disappear`、`BillboardFormRenderer` 7 参 `renderModel`、`ModelBlockEntityRenderer#getRenderDistance` | P0 | 见 4.1 表；Loom 日志 6 行 `Cannot remap` |
| 3 | 1 个 Mixin 完全没被加载：`MobFormRendererEnsureEntityMixin` 不在任何 mixins.json | P0 | 7 个 json 全扫；该类的目标方法在 2.7 也不存在 |
| 4 | 9 组同名 Mixin 分居 `mixin/` 与 `mixin/client/`、分属两套 config，其中 `UIReplaysEditorUtilsMixin#pickForm` 是**同点双 cancel** | P0 | 见 4.3；`UIReplaysEditorUtilsMixin.java:35` 与 `client/...:32` |
| 5 | 7 个 mixins.json 全部 `defaultRequire: 0`，另有 39 处显式 `require = 0` → **任何上游签名变化都静默消失** | P1 | 见 3.4 |
| 6 | 覆盖了 BBS 核心 shader，但 `model.vsh` 与上游**内容完全相同**（仅末尾换行差异）、`model.json` 多的 `UVTransform` **无人消费** | P1 | `git show 89bd62b`（还原 shader 修崩溃）；`model.vsh` 全文无 `UVTransform` |
| 7 | 8 个方块/物品注册在 **`bbs:` 命名空间**并塞进 `bbs:main` 物品栏，8 个 `bbsvfx:` shader json 覆盖插件资源 | P1 | `BBSPlusPlusBlocks.java:53-74`；见 3.2/3.3 |
| 8 | `/bbsplusplus aaa_particle trigger` 的广播是**空方法**，却回"已成功发送"；根命令 `requires(source -> true)` 无权限 | P1 | `network/BBSPlusPlusNetwork.java:15`；`AAAParticleCommandModule.java:68`；`BBSPlusPlusCommand.java:31` |
| 9 | 英文翻译缺 **663** 键（VFX/VFX-Lights/物品喷射轨道几乎全缺）；`assets/bbspp/lang` 71 键根本不生效 | P1 | 见 4.7 |
| 10 | 1.21.11 产物 `bbsplusplus-shadercurves.mixins.json` 是**非法 JSON**（数组尾逗号），源自 build.gradle 清理正则 | P1 | `build/1.21.11/resources/main/`；`build.gradle:160` |
| 11 | 设置注册在 `CLIENT_STARTED` 太晚；另有一套 `config/bbsplusplus/` 状态文件，配置根分裂；sanitizer 会改写 BBS 主设置并无限备份 | P2 | 见 4.6 / 4.8 |
| 12 | 仓库跟踪了 **2008 个 libs 文件（约 146MB）**、2 个 build 日志；本地 `run/` 600MB、`.gradle/` 386MB | P2 | `git ls-files libs`；见 2.8 |

### 1.3 建议路线（先减面，再立规，最后拆骨）

1. **P1 零风险清理**：删死代码/死资源/死配置，修 README 与构建日志，**不动行为**。
2. **P2 去重**：合并 9 组同名 Mixin、入口/设置、`ModelClient` 双版本、`FluidFormRenderer` 双版本、`util/utils`。
3. **P3 冲突消解**：修 8 处死注入；ID/资源覆盖定策略；`require` 收紧；版本门禁。
4. **P4 结构迁移**：按目标包树搬迁，同步 7 个 mixin config + build.gradle 98 条 excludes + 反射字符串。
5. **P5 巨型类拆分**：先拆 5 个高优先级（翻译表、UIClips、UILinkKeyframeFactory、AAA 渲染器、迷你窗口）。
6. **P6 工程化**：CI 构建矩阵 + Mixin/语言/资源校验任务 + 最小测试集，防止再次长回去。

> 总工作量估算 **约 19–30 人日**（1 人全职 4–6 周；业余 2–3 天/周约 3 个月）。

### 1.4 需要你拍板的 6 个决策（详见第 10 节）

| 决策 | 建议 |
|---|---|
| 是否继续维护 1.21.11 分支？ | 若用户量小，降级为"实验构建"，不随每个版本发布；否则单独排期补齐 shader/UI 移植 |
| `bbs:` 命名空间 ID（8 方块 + 6 表单/剪辑）是否迁移？ | **保留**（数据兼容），但冻结新增并加注册冲突断言；不迁移到 `bbsplusplus:` |
| 是否删除 `libs/` 里的 3 份上游源码快照？ | 移出主仓库（单独 `refs` 仓库或 Git LFS），主仓库只留 5 个构建用 jar |
| 是否把硬编码翻译表改成资源 JSON？ | 是；这是"减约 1,700 行 + 可维护"的最大单项收益 |
| 是否接受 3.7.0 的破坏性变更（设置 id、包名、ID 命名）？ | 建议接受一次，之后进入稳定期 |
| 是否投入 P7（用官方事件替代部分 Mixin）？ | 长期投入；3 个版本内把 Mixin 从 232 降到 150 以下 |

---

## 2. 项目全景盘点

### 2.1 项目定位与"身份"

- **定位**：BBS FS 的客户端功能增强插件（`environment: client`），通过官方 addon 事件 + Mixin 扩展；LGPL-3.0-or-later。
- **合并来源**（README 与代码可互证）：
  1. BBS++（本体增强、物品喷射、AAA 粒子、纹理补间等）
  2. FSloveCML / bbs_snow（CML：流体、粒子 Plus、PBR、迷你窗口、Premiere 导出）
  3. BBSPPP（纹理补间、字幕导出）
  4. tools 插件（光影曲线、结构相关）
  5. 对 VFX / VFX-Lights / IRLights / Lezy / Together / bbs_physics 的适配层
- **身份标识不统一**（这是大量困惑的根源）：

| 用途 | 当前值 | 位置 |
|---|---|---|
| Fabric mod id | `bbsplusplus` | `BBSPlusPlusMod.java:20`、`fabric.mod.json` |
| 第二个"mod id"（日志） | `bbspp` | `BBSFSloveCML.java:15` |
| 第三个"mod id"（纹理补间日志） | `bbsppp` | `BBSPPPModClient.java:12` |
| BBS 设置模块 id / 文件名 | `bbspp` | `BBSPlusPlusModClient.java:32` |
| BBS l10n 主 namespace | `bbspp` | `assets/bbspp/strings/*` |
| 自有资源包前缀（死包） | `bbsplusplus` → `assets/bbsplusplus`（不存在） | `BBSPlusPlusModClient.java:77` |
| 方块/物品命名空间 | `bbs` | `BBSPlusPlusBlocks.java:53` |
| 表单/剪辑注册 key | `bbs:*` | `BBSFSloveCMLAddon.java:43-60,79-83` |
| 声音命名空间 | `bbspp` | `assets/bbspp/sounds.json` |

### 2.2 规模与分布

| 维度 | 数值 |
|---|---|
| Java 文件 / 行数 | 489 / 69,342 |
| Mixin 文件 / 行数 | 232 / 22,673（文件 47%，行 32.7%） |
| 被 mixins.json 声明 | 230 条（全部可解析）；1 个 Mixin 未声明 |
| 非 Mixin 业务代码 | 256 文件 / 45,825 行 |
| 包（含子包） | 30+ |
| 资源文件 | 56 个（`assets/` 下，含 5 套语言/字符串路径） |
| 语言键 | zh 1,114 / en 451（主表）；另有 4 个语言文件 |
| 文档 | README 338 行、README_EN 337 行、术语表 274 行、图标指南 411 行、图片 4 张 |

**代码分布（按包）**

| 包 | 文件 | 行数 | 说明 |
|---|---:|---:|---|
| `mixin/`（含子包） | 232 | 22,673 | 根 132/13,016；client 85/8,255；accessor 5/108；irlite 2/104；shadercurves 5/914；vfx 2/78；client/compat 1/198 |
| `client/` | 77 | 18,270 | renderer 14/5,301、ui 35/5,987、screen 5/1,412、texture 5/1,773、structure 2/674 |
| `ui/` | 52 | 11,396 | film 16/2,763、miniwindow 9/3,663、morphing 11/2,107、forms 9/1,665、particles 5/654 |
| `premiere/` | 12 | 1,978 | XML/SRT/音频导出 |
| `util/` + `utils/` | 24 | 4,722 | 两个包无清晰边界 |
| `forms/` + `clips/` + `keyframes/` | 25 | 2,866 | 数据模型 + 渲染器 |
| `api/` | 26 | 609 | 实为 Mixin 内部桥接接口 |
| 根包 | 14 | 2,893 | 6 入口 + 3 设置 + 翻译表（约 1,750 行） |

**最大的 10 个文件**：`AAAParticleFormRenderer` 1,352；`KeyframeLocalizer` 1,308；`UIFilmPanelMiniWindowSupport` 1,088；`TextureTweenManager` 953；`mixin/UIClipsMixin` 947；`java-1.21.11/FluidFormRenderer` 844；`FluidFormRenderer` 784；`UIMiniWindow` 743；`UIMiniWindowManager` 735；`utils/IKAnimationExporter` 715。

### 2.3 入口与初始化链路（6 个入口类）

`fabric.mod.json` 声明 4 组 entrypoint、6 个类：

| entrypoint | 类 | 实际职责 | 问题 |
|---|---|---|---|
| `main` | `BBSPlusPlusMod` | 注册命令、轨道扩展、反射注册方块 | 同时 `implements BBSAddonMod` 但并未走 `bbs-addon` 入口 |
| `main` | `BBSFSloveCML` | **只打 9 行启动日志** | 纯噪声入口 |
| `client` | `client.BBSPlusPlusModClient` | 设置、事件、反射注册渲染器/编辑器、资源包 | 257 行中大量反射；1.21.11 单独复制一份 |
| `client` | `BBSFSloveCMLClient` | l10n 注册、UI/渲染器注册、Premiere tick | 与 `BBSFSloveCMLClientAddon` 的 l10n 双保险重复 |
| `bbs-addon` | `BBSFSloveCMLAddon` | 注册剪辑/表单/表单修改器/关键帧工厂 | 命名仍带 FSloveCML |
| `bbs-client-addon` | `BBSFSloveCMLClientAddon` | l10n、按键、表单分区、仪表盘面板 | `onRegisterClientSettings` 只打日志 |

另有未被任何入口引用的 `IRLightsZH`（只打日志）、`BBSPPPModClient`（只有 LOGGER）、`events/ClientEventHandler`（只打日志 + 未实现按键）。

**关键时序问题**：`BBSMod.setupConfig(...)` 在 `ClientLifecycleEvents.CLIENT_STARTED` 才执行（`BBSPlusPlusModClient.java:30-35`）。在此之前所有 `BBSAddonsSettings.*` 都是 `null`；代码里已有两处直接解引用（`UIElementMixin.java:69`、`UIKeyframesLayoutLockMixin.java:32`），存在 NPE 风险。BBS 自己在 `BBSMod.onInitialize` 就 `setupConfig`，addon 完全可以在 `RegisterSettingsEvent` 中注册（BBS 2.7 官方事件，已在 `BBSFSloveCMLAddon.java:28` 使用）。

### 2.4 构建矩阵

| 目标 | 用途 | MC / Java | BBS jar | 特殊处理 |
|---|---|---|---|---|
| `1.20.1` | **唯一发布产物**（声明兼容 1.20.1–1.20.4） | 1.20.1 / 17 | `libs/archive/bbs-2.7/bbs-2.7-1.20.1-zh_CN.jar` | 无 |
| `1.20.4` | 编译回归（`runClient1204`），不发布 | 1.20.4 / 17 | `libs/archive/bbs-2.7/bbs-2.7-1.20.4.jar` | aaa 仅编译期 |
| `1.21.11` | 第二发布目标（`build12111`） | 1.21.11 / 21 | `libs/bbs-2.7-1.21.11.jar` | **98 条 source-set excludes（97 唯一）**、替换 client 入口、洗 mixin json、删 `model.json`、复制 330 shader |

**构建事实**

- 三个产物：`build/1.20.1/libs/BBS-Cubed-3.6.0+1.20.1-1.20.4.jar`（1.89MB）、`1.20.4`（1.89MB）、`1.21.11`（1.54MB）。
- 1.21.11 产物比 1.20.1 **小 19%**：98 个类被排除，约 20% 功能静默缺失。
- `excludes` 清单有 1 条重复（`mixin/MotionPathMixin.java` 出现两次）、2 条指向**已删除文件**（`mixin/UIAnimationStateKeyframesMixin.java`、`mixin/xavin/XavinClientMixin.java`，`mixin/xavin` 目录整体不存在）。
- 编译 jar 与运行 jar 一致（SHA256 已验证）；但 dev 环境 `run/*/mods` 里的 Iris 1.7.6 / bbs_physics 1.0.3 等版本**高于** `build.gradle` 声明（Iris 1.7.0 / physics 未声明），意味着部分兼容性只在 dev 验证过。
- 无 CI、无测试、无格式化/静态检查（`src/test` 不存在、无 `.github`）。

### 2.5 包结构现状（问题版）

```
gbeic.bbsplusplus
├── (根)            入口 6 + 设置 3 + 翻译表 2（约 1,750 行）
├── api/            26 个 ≤30 行接口 —— 不是 API，是 Mixin 桥（应改名 bridge）
├── client/         77 ── renderer/ screen/ texture/ structure/ audio/ debug/ settings/ keyframes/ compat/
│   └── ui/         35 ── film/ forms/ pose/ keyframes/ utils/ animations/ curves/ presets/
├── ui/             52 ── film/ forms/ miniwindow/ morphing/ particles/ list/ model_blocks/
├── mixin/          232（根 132 + client 85 + accessor 5 + irlite 2 + shadercurves 5 + vfx 2）
├── util/ (13) 与 utils/ (11)     拼写双包，职责重叠
├── forms/ clips/ keyframes/ timeline/ particles/ premiere/ export/ simulation/ settings/ command/ network/
└── events/         1 个零引用类（死包）
```

**结构问题清单**

| 问题 | 证据 | 影响 |
|---|---|---|
| UI 被切成 `ui/` 与 `client/ui/` 两棵平行树 | 87 文件 / 17,383 行，均为 UI | 找不到东西；同名概念分散 |
| `util/` 与 `utils/` 无边界 | 24 文件 / 3,911 行 | 新人无法判断放哪 |
| `api/` 名不副实 | 26 文件平均 19 行，被 Mixin `implements` | 误以为是公开 API |
| 功能包自带 UI 子包 | `timeline/ui/` 唯一 | 与 `ui/` 分层冲突 |
| `events/`、`network/` 是死包/空壳 | 零引用 / 空方法 | 误导 |
| `premiere/` 与 `export/` 割裂 | 12/1,737 + 1/124 | 同一导出管线两处 |
| `forms/renderers` 与 `client/renderer` 分家 | 4/853 + 14/4,499 | 渲染器找不全 |
| 两套命名 | `BBSPlusPlus*` / `BBSFSloveCML*` / `BBSPP*` / `BBSPPP*` / `UIBBSPP*` | grep/沟通成本 |

### 2.6 资源与语言现状

| 路径 | 键/文件 | 是否生效 | 说明 |
|---|---|---|---|
| `assets/bbs/lang/{en_us,zh_cn}.json` | 16 / 16 | ✅ 原版 I18n（ns `bbs`） | 8 个方块名 + 8 条魔杖提示 |
| `assets/bbs/assets/lang/{en_us,zh_cn}.json` | 67 / 68 | ✅ 借 BBS 内部资源包 | BBS UI 文案补充；缺 1 个英文键 |
| `assets/bbspp/strings/{en_us,zh_cn}.json` | 451 / 1,114 | ✅ BBS L10n 主表 | **英文缺 663 键** |
| `assets/bbspp/lang/{en_us,zh_cn}.json` | 71 / 71 | ❌ 不生效 | 69 键与 strings 重复，2 键独有且无人引用 |
| `assets/xavin/lang/*.json` | 1 / 1 | ⚠️ 跨 mod 覆盖 | 为 xavin 插件的物品名提供翻译 |
| `assets/bbspp/textures/collapse.png` | 1 | ❌ 无引用 | 死资源 |
| `assets/bbs/assets/structures/oak_tree.nbt` | 1 | ❌ 无引用 | 死资源 |
| `assets/bbs/blockstates、models/item/emissive_chroma_*` | 16 | ✅ 但复用 bbs 模型 | 外观与 BBS 原 chroma 完全一致 |
| `assets/bbs/shaders/core/model.*` | 2 | ⚠️ 覆盖 | vsh 与上游内容相同（仅末尾换行）；json 多一个无人用的 uniform |
| `assets/bbs/shaders/core/picker_models.*` | 2 | ✅ 覆盖 | 唯一真正有改动的核心 shader（UV 变换） |
| `assets/bbsvfx/shaders/core/*.json` | 8 | ⚠️ 覆盖插件 | 删除若干 uniform，与 VFX 1.3 强绑定 |
| `assets/bbspp/shaders/core/*` | 8 组 | ⚠️ 1.21.11 可能无效 | 150 时代自定义管线 |
| 1.21.11 产物里的 `picker_models.json/.vsh` | 2 | ⚠️ 过期 | 1.20.1 core shader 格式，BBS 1.21.11 jar 里已无对应 json |

### 2.7 配置与落盘

| 路径 | 写入者 | 风险 |
|---|---|---|
| `config/bbs/settings/bbspp.json` | 本模组设置模块（id `bbspp`） | 与任何用 `bbspp` 作 settings id 的 addon 同路径 |
| `config/bbs/settings/bbs.json` | **本模组 sanitizer 改写** | 动 BBS 主设置；每次 load 都扫描；备份文件无上限 |
| `bbs.json.bbspp-colors-backup[-N]` | sanitizer | 无清理逻辑 |
| `config/bbsplusplus/film_auto_game_mode.json` | 自动旁观恢复 | 与 settings 根分裂 |
| `config/bbs/assets/models/**/config.json` | 自动面剔除 | **写 BBS 资产目录** |
| `config/bbs/assets/effeks/`、`audio/` | AAA / 音频导入 | 写 BBS 资产目录（功能需要，但应文档化） |
| `logs/bbsplusplus/<module>.log` | 调试日志 | 无轮转 |
| BBS 预设目录 | 自动保存预设 | 与 BBS 共享 |

补充：`SettingsFileSanitizer` 处理的 `recent_colors` / `favorite_colors` 在 BBS 2.7 中**仍然存在**（`BBSSettings.java:679-680`），因此功能仍有意义；但它在每次 `load` 时扫描/可能改写 BBS 主设置 `bbs.json`，备份文件无上限，且 `copyUntilArrayStart` 失败路径存在截断风险。

### 2.8 仓库与文档现状

| 项 | 现状 | 问题 |
|---|---|---|
| 跟踪的 `libs/` | 2,008 文件 / 约 146MB | 3 份 BBS 源码（2.6/2.7/1.21.11）+ Iris + 物理 + Lezy + 相机 mod；构建实际只需要 5 个 jar |
| 根目录 build 日志 | `build_build1201.log`、`build_build1204.log` | 被 git 跟踪，属调试残留 |
| 本地垃圾 | `run/` 600MB、`build/` 77MB、`.gradle/` 386MB、`crash*/`、`.workbuddy/`、`.mimocode/`、`mchorse/*.class` | 多数已 ignore，但仍占磁盘；`mchorse/` 是根目录散落的 .class |
| `.gitignore` | 已覆盖 build/gradle/run/crash/private | 未覆盖根目录 `*.log`、`.mimocode/`、`_audit_*` 临时脚本 |
| 文档 | README 中英各 338 行；更新日志从 2.3.x 堆到 3.6.0 | 功能表与更新日志重复；部分描述与实现不符（见 4.9） |
| 测试/CI | 无 | 任何重构都只能靠手测 |

### 2.9 目录地图（"所有内容"一览）

| 路径 | 内容 | 参与构建 | 处置 |
|---|---|---|---|
| `src/main/java` | 488 个 Java（业务 + 232 Mixin） | ✅ | 重构主体 |
| `src/main/java-1.21.11` | 1 个版本专用渲染器（844 行） | 仅 1.21.11 | 纳入版本隔离方案 |
| `src/main/resources` | 56 个资源、7 个 mixin config、`fabric.mod.json` | ✅ | 治理见 5.7 |
| `src/main/resources-shared` | 1.21.11 专用 shader 覆盖（1 文件） | 仅 1.21.11 | 与上游相同，建议删除 |
| `libs/` | 146MB：5 个构建用 jar + 3 份上游源码 + Iris/物理/Lezy/相机参考 | 部分（5 个 jar） | 参考源码移出（6.4） |
| `run/{1.20.1,1.20.4,1.21.11}` | 三个 dev 运行目录（600MB，含 mods/存档/日志） | 仅运行 | 本地保留，勿提交 |
| `build/{1.20.1,1.20.4,1.21.11}` | 三套构建产物与 Loom 缓存（77MB） | 生成物 | 可清理 |
| `docs/` | 术语表、图标指南、4 张图片、本计划 | 否 | 补 ARCHITECTURE/COMPATIBILITY |
| `gradle/`、`gradlew*`、`build.gradle`、`settings.gradle`、`gradle.properties` | 构建入口与依赖矩阵 | ✅ | 清理无效 excludes / 版本漂移 |
| `.idea/runConfigurations` | 唯一被跟踪的 IDE 配置 | 否 | 保留 |
| `.gradle/`、`.workbuddy/`、`.mimocode/`、`crash*/`、`mchorse/`、`build_build*.log` | 本地缓存、审计记忆、崩溃报告、散落 .class、构建日志 | 否 | 删除/忽略（6.3/6.4） |
| `README.md`、`README_EN.md`、`LICENSE` | 说明与许可证 | 否 | 拆 CHANGELOG、修不一致 |

---

## 3. 与上游 BBS FS 的冲突面

### 3.1 冲突分级

| 级别 | 定义 | 处理时限 |
|---|---|---|
| **P0 硬冲突/失效** | 启动崩溃、功能完全不生效、数据互踩 | 立即修 |
| **P1 静默降级** | 上游小版本升级后功能消失但无报错 | 本季度 |
| **P2 升级脆弱** | 上游大版本/资源变动后损坏，需要人工跟进 | 下个版本 |
| **P3 命名/维护** | 不直接影响运行，但让冲突难以发现 | 随重构 |

### 3.2 命名空间与注册 ID 冲突

| 对象 | 当前注册 | 冲突对象 | 触发条件 | 影响 | 建议 |
|---|---|---|---|---|---|
| 8 个发光色度方块/物品 | `bbs:emissive_chroma_*` | BBS 自身 `bbs:chroma_*` | BBS 未来新增同名 ID | **启动崩溃**（Registry 重复） | 迁移到 `bbsplusplus:` 或永久冻结 + 启动断言 |
| 创造模式物品栏 | 修改 `bbs:main` 组 | BBS 物品栏重构 | BBS 改组 id | 方块静默不进物品栏 | 优先 `ItemGroupEvents` 的容错，或自建组 |
| 表单 | `bbs:fluid`、`bbs:item_spray`、`bbs:aaa_particle` | BBS 未来表单 | ID 撞车 | 旧存档解析错乱/覆盖 | 保留（数据兼容），加"注册前查重"日志 |
| 剪辑 | `bbs:hotbar`、`bbs:cinematic`、`bbs:replay` | BBS 未来剪辑（`replay` 是高风险词） | ID 撞车 | 同上 | 同上；`replay` 建议加前缀或用 converter 迁移 |
| 关键帧工厂 | `aaa_effect`（无 namespace） | BBS/其它 addon | 同名 | 覆盖注册表 | 改 `bbspp:aaa_effect` 并保留旧名别名 |
| 设置模块 | `bbspp` | 旧 bbs_snow / 其它 addon | 同 settings id | 设置文件互踩 | 改 `bbsplusplus`，迁移旧文件 |
| 声音 | `bbspp:structure_stick_drag` | 无 | — | 安全 | 保持 |
| 方块模型/贴图 | 直接引用 `bbs:block/chroma_*`、`bbs:block/red` | BBS 改模型/贴图 | BBS 重绘 | 8 个方块外观损坏 | 复制一份自有资源 |

> 结论：**不要新增任何 `bbs:` 命名空间内容**；已有内容保留并文档化，同时加"注册前查重 + 明确 warn/error"。

### 3.3 资源覆盖冲突（最危险的一类）

| 被覆盖文件 | 与上游差异 | 生效方式 | 上游升级后果 | 建议 |
|---|---|---|---|---|
| `assets/bbs/shaders/core/model.vsh` | **内容完全相同**（仅末尾换行差异） | 资源包同名覆盖 | 无功能价值，但会**冻结**上游 vsh | 删除 |
| `assets/bbs/shaders/core/model.json` | 格式重排 + 追加 `UVTransform` | 同上 | 上游新增 uniform/attribute 时缺声明 → 画面错/link 失败 | 删除（uniform 无人消费）或改运行时注入 |
| `assets/bbs/shaders/core/picker_models.vsh/json` | 真正加了 `UVTransform` + `textureSize` | 同上 | 上游改 attribute 列表即坏 | 保留；注释标明依赖 2.7 attribute；加实机回归 |
| `assets/bbsvfx/shaders/core/*.json`（8 个） | **只删不增**（删 ModelViewMat/ProjMat/Fog/uTime 等） | 覆盖 BBS-VFX | VFX 1.4 uniform 变动 → 链接/取值失败 | 只保留最小修复集；尝试上游 PR；`breaks` 锁定 VFX 版本 |
| `assets/bbs/blockstates、models/item、lang`（8 组） | 新 ID 指向 BBS 原模型 | 注册进 bbs ns | 上游改 chroma 模型 → 8 方块全坏 | 复制自有模型/贴图 |
| `assets/xavin/lang/*.json` | 1 个物品名 | 覆盖 xavin | xavin 改键名即失效 | 删除，改为 BBS l10n 键或运行时映射 |
| `assets/bbs/assets/lang/*.json` | 借 BBS 内部包路径 | BBS l10n | BBS 改 internalPrefix（`assets/bbs/assets`）即失效 | 迁移到自有 `bbspp/strings`，合并键 |
| 1.21.11 产物 `picker_models.json/.vsh` | 1.20.1 core shader 格式 | 资源包 | 1.21.5+ RenderPipeline 不读该 json，但 vsh 覆盖仍在 | 1.21.11 构建剔除，或补 330 版本 |

**补充证据**：`model.vsh` 的 UV 变换改动曾在提交 `89bd62b` 被**主动还原**（提交说明："还原 1.20.1 model.vsh 原版 shader，修复 shader 编译导致的启动崩溃"），但 `model.json` 与 `picker_models.*` 保留至今，因此当前是"json 声明了一个没有 shader 消费的 uniform"。`ModelUVTransformRuntime.applyToShader` 只对 picker 路径有意义；正式渲染的纹理变换靠 CPU 顶点路径（`CubicCubeRendererMixin` 等）。

### 3.4 Mixin 注入面冲突

**量化指标**

| 指标 | 数值 | 含义 |
|---|---:|---|
| 引用的 BBS 类型（distinct import） | 421 | — |
| 其中来自官方 API（`mchorse.bbs_mod.api.*`） | **7** | 其余 414 个是内部类 |
| Mixin 文件 | 232 | 47% 文件 |
| `@Inject` / `@Redirect` / `@Overwrite` | 390 / 40 / 8 | — |
| `@Accessor` / `@Invoker` | 32 / 14 | — |
| `@Unique` / `@Shadow` | 351 / 197 | 仍有 25 个成员无 `@Unique` |
| 非默认 priority | 7 个类（500 / 1001 / 1002 / 2000） | 2000 = 最后应用，会压过所有 addon |
| `defaultRequire: 0` 的 config | **7 / 7** | 全部静默失败 |
| 显式 `require = 0` | 39 处 | 额外静默 |
| refmap 声明但文件不存在 | 2 个 config | 启动 warn，当前靠 Loom remap 兜底 |

**与第三方 addon 的注入重叠**（libs 内有源码/jar 可查；指令级同点仍需运行时验证）

| 目标类 | 我方 | 对方 | 评估 |
|---|---|---|---|
| `UIClips` | `UIClipsMixin`（7 个 Redirect）、`UIClipsDoubleClickMixin`、`UIClipsInvoker` | bbs_physics（`UIClipsMixin`，`defaultRequire:1`） | 高：对方会因我方 cancel/redirect 抛异常 |
| `ModelFormRenderer` | root + client 两份 | bbs_physics、BBS-VFX（Pose/Lines） | 高 |
| `UIReplaysEditor` | 3 个文件 | VFX-Lights | 中高 |
| `UIPoseEditor` / `UIPoseKeyframeFactory` | 3 个文件 | BBS-VFX（Smear/Blend） | 中高 |
| `ShaderCurves` | **3 个 @Overwrite** | bbs-lezy（`@Inject`） | **高**：@Overwrite 会吞掉对方注入 |
| `BBSRendering` | 两份 | lezy、BBS-VFX、VFX-Lights | 中 |
| `UIFilmPreview` / `UISoundOverlayPanel` / `TextureManager` / `Window` | 各 1–2 份 | lezy、BBS-VFX | 中 |

**结论**：冲突时对方 `require=1` 会**硬崩**，我方 `require=0` 会**静默**，排障方向相反。建议核心目标收紧 `require=1`，可选目标保持 0 + 显式日志。

### 3.5 版本与 API 门禁

#### 3.5.1 门禁现状

| 项 | 当前值 | 问题 | 建议 |
|---|---|---|---|
| `depends.bbs` | `>=2.7-0` | 语义松散；不校验 API 版本 | 改 `>=2.7-1.20.1`（或 `>=2.7`）+ 启动调用 `BBSApi.requireVersion("bbsplusplus", 2)` |
| API 使用 | 用了 `RegisterFormModifiersEvent` 等 v2 API | 未调用 `requireVersion` | 同上 |
| `depends.minecraft` | `>=1.20.1 <=1.20.4` | 1.20.2/1.20.3 没有 BBS 2.7 jar，实际不可用 | 改 `>=1.20.1 <=1.20.4` 并文档说明，或收窄到 `1.20.1` 或 `1.20.4` |
| `breaks.aaa_particles` | `<2.2.0` 与 `>2.2.3` | 代码对 AAA 是反射可选依赖，却硬拒绝 2.2.4+ | 放宽为 `>=2.2.0`，缺失时降级 |
| `breaks.bbstools/bbspp/bbs_fslovecml` | `*` | 只挡 mod id；挡不住 settings id 冲突 | 保留 + 改 settings id |
| `depends.fabricloader` | `>=0.15.0` | 与 1.21.11 的 `>=0.19.3` 分版本覆盖 | 可接受 |

#### 3.5.2 源码快照与运行 jar 不一致

- `libs/bbs-fs-2.7-1.20.1` 源码中 `ModelBlockEntityRenderer` 有 `getRenderDistance()`（`:381`），但 `bbs-2.7-1.20.1-zh_CN.jar` 的 class **没有该方法**（16 个方法逐一核对）。
- 结论：**参考源码 ≠ 编译 jar**（同名 2.7，内容有漂移）。依赖源码做"目标方法存在性"判断会误判；后续所有兼容性核对应以 jar 为准，并固定 BBS 版本号。

#### 3.5.3 1.21.11 分支的特殊风险

- 98 条 excludes（97 唯一）把约 20% 代码移出构建，功能靠反射探测/占位渲染器兜底。
- 入口类被复制为 `BBSPlusPlusModClient12111`（259 行），与主入口 95% 相同。
- 构建期用正则删 mixin json 条目，**已产生非法 JSON**（数组尾逗号）。
- 产物仍带 1.20.1 格式的 `picker_models.*` 与 `bbspp/*` shader。
- `FilmAutoGameModeRestoreState` 等类被排除 → 设置项在 1.21.11 上"开关还在但没功能"。

### 3.6 冲突登记表（汇总）

| ID | 冲突 | 级别 | 触发条件 | 影响 | 处置 | 工作量 |
|---|---|---|---|---|---|---|
| C-01 | 8 个方块在 `bbs:` 命名空间 | P1 | BBS 新增同名 ID | 启动崩溃 | 冻结 + 注册断言；长期迁 `bbsplusplus:` | 0.5d / 2d |
| C-02 | 6 个表单/剪辑 key 在 `bbs:` | P2 | BBS 新增同名 key | 数据错乱 | 保留 + 查重日志；`replay` 优先迁移 | 1d |
| C-03 | 设置模块 id `bbspp` | P2 | 其它 addon 同名 | 设置互踩 | 改 `bbsplusplus` + 迁移 | 0.5d |
| C-04 | 覆盖 BBS 核心 shader | P1 | BBS 2.8 改管线 | 全模型渲染异常 | 删 `model.*`；保留 `picker_models.*` 并注释 | 0.5d |
| C-05 | 覆盖 BBS-VFX 8 个 shader | P1 | VFX 升级 | VFX 特效损坏 | 最小化 + 版本锁定 + 上游 PR | 1–2d |
| C-06 | 覆盖 `assets/bbs/assets/lang` | P2 | BBS 改 internalPrefix | 补充文案消失 | 合并进 `bbspp/strings` | 0.5d |
| C-07 | 覆盖 `xavin` 语言 | P3 | xavin 改键 | 1 个物品名失效 | 删除/迁移 | 0.2d |
| C-08 | 232 Mixin / 414 内部 import | P1 | BBS 小版本重构 | 功能静默消失 | `require` 收紧 + 死注入修复 + 版本门禁 | 3–5d |
| C-09 | 与 VFX/Lezy/physics 同点注入 | P1 | 组合安装 | 崩溃或行为异常 | 联合测试 + 优先级/require 规范 | 2–3d |
| C-10 | `ShaderCurves` 3 个 @Overwrite | P0 | BBS/Lezy 改实现 | 光影曲线整体坏 | 改 `@Inject` 组合或版本守卫 | 2–3d |
| C-11 | `UIClips#resolveCollisions` @Overwrite | P0 | BBS 改拖拽算法 | 剪辑拖拽回退旧语义 | 逐版本比对 + 换 `@Inject` | 1–2d |
| C-12 | 1.21.11 反射/排除链 | P1 | BBS 1.21.11 变化 | 20% 功能静默缺失 | 版本化源码集 + 自带 smoke | 3–5d |
| C-13 | `breaks`/`depends` 不完整 | P2 | 用户装错版本 | 崩溃而非清晰提示 | 补 `requireVersion` + 版本区间 | 0.5d |
| C-14 | 开发环境 mod 版本高于声明 | P3 | 只在 dev 验证 | 线上回归 | 版本对齐 + 写入文档 | 0.5d |

---

## 4. 代码缺陷

### 4.1 静默失效的注入（P0，8 处）

> **性质**：8 处中 7 处的目标方法在 **BBS 2.5.2** 中确实存在（`UIFilmPanel#appear/close/disappear`、`UIList#subMouseClicked`、`UIReplayList#subMouseClicked`、`BillboardFormRenderer` 7 参 `renderModel`、`ModelBlockEntityRenderer#getRenderDistance`），是 **2.6/2.7 重构后没有同步更新**的回归；`MobFormRendererEnsureEntityMixin` 的目标方法在 2.5.2 / 2.6.1 / 2.7 中都不存在，从未生效。全部被 `defaultRequire: 0`/`require = 0` 掩盖，只在 Loom `remapJar` 阶段留 6 行 `Cannot remap`。

| # | 文件:行号 | 目标#方法 | 为什么失效 | 影响的功能 | 修复 |
|---|---|---|---|---|---|
| D-01 | `mixin/UIFilmPanelGameModeMixin.java:34` | `UIFilmPanel#appear()` | 方法在父类 `UIDashboardPanel`（`final`），目标类未声明（2.5.2 时在 `UIFilmPanel`） | 进编辑器自动旁观在 2.6/2.7 完全无效（2.5.2 可用） | 改注入 `UIDashboardPanel#appear` 并 `instanceof UIFilmPanel` 判断（或监听屏幕打开事件） |
| D-02 | 同上 `:74` | `UIFilmPanel#close()` | 同上 | 退出恢复也不执行（但 `restore tick` 仍在跑） | 同上 |
| D-03 | `mixin/client/UIFilmPanelMiniWindowMixin.java:76` | `UIFilmPanel#disappear()` | 同上 | 离开影片面板时迷你窗口不清理（dashboard 级清理仍在） | 删除该注入，依赖 `UIDashboardPanelMiniWindowMixin` |
| D-04 | `mixin/UIListPoseParameterBrushMixin.java:49` | `UIList#subMouseClicked` | 2.7 中继承自 `UIElement`，目标类未声明（2.5.2 时 `UIList` 有覆写） | 姿势参数刷在列表上的点击逻辑失效 | 注入具体子类（如骨骼列表）或 `UIElement` + 类型判断 |
| D-05 | `mixin/UIReplayListCategoryMixin.java:111` | `UIReplayList#subMouseClicked` | 同上（2.5.2 时 `UIReplayList` 有覆写） | 回放列表分类右键相关交互失效 | 注入 `UIElement#subMouseClicked` + `instanceof UIReplayList` |
| D-06 | `mixin/BillboardFormRendererMixin.java:48` | `BillboardFormRenderer#renderModel(...7 参)` | 2.7 只有 8 参版本（2.5.2 为 7 参） | 旧版兼容分支永远不跑（8 参分支正常） | 删除 7 参注入 |
| D-07 | `mixin/ModelBlockEntityRendererDistanceMixin.java:20` | `ModelBlockEntityRenderer#getRenderDistance` | 2.5.2 存在，2.7 运行 jar 中不存在（源码快照有、jar 无） | "模型方块渲染距离"设置无效 | 确认目标版本后改注入点，或删除设置项 |
| D-08 | `mixin/client/MobFormRendererEnsureEntityMixin.java:27` | `MobFormRenderer#renderUIPreview` | 方法在 2.5.2 / 2.6.1 / 2.7 均不存在 + 整个类未注册 | 双重失效（本来用于修 1.21.11 预览空白，从未生效） | 删除文件，或按实际方法重写并注册 |

> 以上全部被 `defaultRequire: 0`/`require = 0` 掩盖，只在 Loom `remapJar` 阶段留 6 行 `Cannot remap`。**修复优先级最高**，因为其中 4 项是"设置项还显示、功能就是坏的"。

### 4.2 空实现、伪成功与权限

| 问题 | 证据 | 影响 | 建议 |
|---|---|---|---|
| 网络广播是空方法 | `network/BBSPlusPlusNetwork.java:15-18` 只有注释 | `/bbsplusplus aaa_particle trigger` 对多人无效 | 实现 `ServerPlayNetworking`（1.20.1）+ `CustomPayload`（1.21.11），或删命令 |
| 命令伪成功提示 | `AAAParticleCommandModule.java:68-69` 调空方法后回"已成功发送" | 用户误以为生效 | 未实现前返回 `sendError` |
| 根命令无权限 | `BBSPlusPlusCommand.java:31` `requires(source -> true)` | 任何玩家可执行调试命令 | 改 `.requires(source -> source.hasPermissionLevel(2))` |
| `ClientEventHandler` 未注册且只打日志 | `events/ClientEventHandler.java:16-46` | 死代码 + 未实现的快捷键 | 删除（或补全功能并注册） |
| `IRLightsZH` 入口只打日志且未注册 | `IRLightsZH.java:9-15` | 死代码 | 删除 |
| `BBSFSloveCML` 只打 9 行日志 | `BBSFSloveCML.java:19-33` | 启动噪声 + 第二套 mod id | 删除并把真正注册迁到 `BBSPlusPlusAddon` |
| `BBSPlusPlusModClient` 的 `BBSMod.events.register(this)` | `BBSPlusPlusModClient.java:37` | 该类 0 个 `@Subscribe`，空注册 | 删除 |
| `BBSFSloveCMLClientAddon.onRegisterClientSettings` | `:27-29` | 只打日志 | 删除该方法 |

### 4.3 重复实现

| 组 | 文件 | 重复量 | 处理 |
|---|---|---|---|
| 双客户端入口 | `client/BBSPlusPlusModClient.java`（257）+ `BBSPlusPlusModClient12111.java`（259） | 95% 相同 | 合成一个，版本差异抽 `ClientHooks` 接口/条件注册 |
| 双流体渲染器 | `forms/renderers/FluidFormRenderer.java`（784）+ `java-1.21.11/...`（844） | 93.9% 行相同 | 抽 `AbstractFluidFormRenderer`，两版只留 ~100 行后端 |
| 9 组同名 Mixin | `mixin/X.java` 与 `mixin/client/X.java` | 18 个文件 | 逐组合并到 `mixin.client`（例外：`UISoundOverlayPanelMixin` 保留 root） |
| 翻译表 | `KeyframeLocalizer`（1,308）+ `IRLightsZHTranslator`（439） | 1,747 行硬编码 | 迁移到 `assets/bbspp/keyframe_names/*.json` + 加载器 |
| 语言判断 | `LangKeyMixin` / `StringKeyMixin` 各自 20 行同逻辑 | 40 行 | 抽 `ClientLanguage.isChinese()` |
| 回放时间 | `clips/ReplayTimeMath.java:27` + `clips/ReplayTimeBridge.java:43` | 逻辑完全相同（后者纯委托） | 删委托层 |
| 导出 | `premiere/xml/SrtGenerator` 与 `export/AudioSubtitleExporter` | 字幕两条实现 | 统一到 `export` 包 |
| 日志门面 | `util/EnvLogger` 与 `client/debug/BBSPlusPlusDebugLogs` | 两套 | 统一 |
| 影片库列 | `client/ui/film/UIFilmLibraryColumn` 与 `UIFilmLibraryColumnView` | 两套列渲染 | 合并 |
| 缩放交互 | `client/ui/utils/UIEffectPicker` 与 `ui/miniwindow/UIResizeHandles` | 两套 | 抽公共 |
| 导出器 | `utils/PoseAnimationExporter`（704）与 `IKAnimationExporter`（715） | 烘焙/通道写值重复 | 抽基类 |

### 4.4 死代码与死资源

**真死（建议删除，P1）**

| 文件/资源 | 行数/大小 | 证据 |
|---|---:|---|
| `events/ClientEventHandler.java` | 51 | 全库零引用；不在 entrypoint |
| `ui/film/replays/overlays/UIQuickReplayOverlayPanel.java` | 214 | 仅被上面的死类引用 |
| `ui/film/replays/UIReplaysEditorExtended.java` | 117 | 仅自身；注释还写 "Adapted for BBS FS 2.3.1" |
| `ui/film/replays/BoneTextureUtils.java` | 113 | 仅被上一行死类引用 |
| `client/ui/keyframes/UIAAAParticleLinkKeyframeFactory.java` | 29 | 仅自身 + 1.21.11 exclude |
| `IRLightsZH.java` | 15 | 仅自身；不在 entrypoint |
| `mixin/client/MobFormRendererEnsureEntityMixin.java` | 27 | 仅自身；不在任何 mixins.json |
| `BBSFSloveCML.java` | 34 | 只打日志的第二入口 |
| `BBSPPPModClient.java` | 14 | 只有 LOGGER（被 2 个纹理类引用，可并到主 LOGGER） |
| `network/BBSPlusPlusNetwork.java` | 18 | 空实现；配合命令一起处理 |
| `TickToFrameConverter#getFps/isNtsc`、`ModelCullingConfigManager#onResourceReload`、`XRayManager#shutdown` | 4 个方法 | 全库零调用 |
| `assets/bbspp/textures/collapse.png` | 0.2KB | 全库零引用 |
| `assets/bbs/assets/structures/oak_tree.nbt` | 0.3KB | 全库零引用 |
| `assets/bbspp/lang/{en,zh}.json` | 71 键 | 不生效；69 键重复，2 键无引用 |
| `assets/bbs/shaders/core/model.vsh` | 43 行 | 与上游内容相同（仅末尾换行差异） |
| `assets/bbs/shaders/core/model.json` | 30 行 | 只多一个无人消费的 uniform |
| `src/main/resources-shared/shaders-1.21.11/.../model.vsh` | 43 行 | 与 1.21.11 上游 jar 相同 |
| `build_build1201.log` / `build_build1204.log` | 5.4KB | 被跟踪的调试日志 |

**"构建孤儿"（1.21.11 排除，不得删）**：`EnvLogger`、`UIHotbarRenderer`、`SprayGuideRenderer`、`ItemSprayIRLiteBridge`、`ColorGradeRenderer`、`ScreenEffectRenderer`、`UIAnimationPreview`、`UIAnimationPreviewTooltip`、`UIShaderCurvePickerOverlayPanel`、`UITextureTweenOrigin3DPicker`、`PoseBoneMarkerRenderer`、`UIEffectTreeList`、`UIAAAParticleFormPanel`、`ParticleEditorFileControls`、`ParticleComponentParticleMorph/CollisionTinting/CollisionAppearance`、`ActionsTimelineEditorSupport`、`FluidController`、`MorphCategoryIconStorage`、`TextureGradeRenderContext`、`VanillaParticleGradeContext`、`ModelUVTransformRuntime` 等 26 个。判定标准：唯一引用者都在 `build.gradle` 的 excludes 表里。

**仅反射引用（勿删）**：`UIAAAParticleForm`、`UIItemSprayForm`、`VFXDestructionWandTooltip`、`GradedVanillaParticleFormRenderer`。

**技术债（暂留，标注）**：`LegacyTextureTweenTrackMigration`（注释自述"过渡期临时代码"）、`SettingsFileSanitizer`（改写 BBS 主设置，需加次数/备份上限）、`@Deprecated` 0 处。

### 4.5 巨型类与职责混杂（≥350 行，52 个；≥500 行，25 个）

| 类 | 行数 | 混杂的职责 | 优先级 |
|---|---:|---|---|
| `AAAParticleFormRenderer` | 1,352 | 发射器生命周期 + 3D/UI 渲染 + 触发队列 + LRU + X-Ray | 高 |
| `KeyframeLocalizer` | 1,308 | 静态翻译表（约 1,000 行）+ l10n 查找 + 后缀匹配 + 语言判断 | 高 |
| `UIFilmPanelMiniWindowSupport` | 1,088 | 停靠/浮动/检查器/输入转发/布局恢复 | 高 |
| `TextureTweenManager` | 953 | 混合数学 + LRU 缓存 + 纹理生成 + 参数归一化 | 中 |
| `mixin/UIClipsMixin` | 947 | 10+ 个不相关注入：拖拽渲染顺序、粘贴、滚轮、标尺、视频擦洗、碰撞、框选、修复 | 高 |
| `FluidFormRenderer` ×2 | 784 / 844 | 流体数学 + 渲染后端 + 调试线 | 高 |
| `UIMiniWindow` / `UIMiniWindowManager` | 743 / 735 | 窗口模型 + 拖拽/停靠 + 布局持久化 | 高 |
| `utils/IKAnimationExporter` / `PoseAnimationExporter` | 715 / 704 | 反射 + 动画烘焙 + 导出 | 高 |
| `ColorGradeRenderer` | 691 | 单个 `apply` 函数集 | 中 |
| `UIPoseEditorMixin`（client） | 643 | 纹理/PBR/附魔/选择/子骨骼 | 高 |
| `VFXDestructionWandSelection` | 620 | 选择/渲染/音效/输入 | 高 |
| `UILinkKeyframeFactoryMixin` | 619 | 25 个成员、0 个 `@Unique`、20+ 控件 setter | 高（先加 `@Unique`） |
| `ShaderCurvesMixin` | 597 | 3 个 `@Overwrite` + 解析/替换 | 高 |
| 其余 40+ 个 350–500 行的类 | — | 以 UI/Mixin/导出器为主 | 中/低 |

### 4.6 健壮性、线程、IO、内存

| 类别 | 证据 | 影响 | 建议 |
|---|---|---|---|
| 阻塞 UI 线程 | `client/ui/utils/UIEffectPicker.java:113-137`：`ProcessBuilder.start()` 后 `waitFor()` | 慢盘/网络路径卡死客户端 | 改 `process.onExit()` 或异步 |
| 加载期 `join()` | `client/renderer/BBSEffectLoader.java:55,313,394`：锁内 IO + `holder.load().join()` | 首次渲染线程触发时卡顿/死锁 | 预热 + 异步 + 超时 |
| 未关闭流 | `utils/VanillaSoundResource.java:275` 返回 `FileInputStream` | 句柄泄漏 | 返回 `byte[]` 或强制调用方关闭 |
| 空 catch | 10 处（如 `BBSFSloveCMLClient.java:102-104`、`AAAParticleFormRenderer` 多处） | 故障不可见 | 至少 `LOGGER.debug` |
| `printStackTrace` | `ui/morphing/UIBBSPPFormCategory.java:105` | 日志不规范 | 换 LOGGER |
| 反射 | `Class.forName` 34 处、`setAccessible` 20 处、动态 Proxy 7 处 | 上游改内部名即 `NoSuchMethodException`，且多数被吞 | 尽量换官方事件；保留处集中到 `compat/reflection` |
| `Unsafe` | `ParticlePlusClient.java:75-95`、`XRayManager.java:22-104` | Java 22+ 警告；AAA 2.2.3 私有字段强绑定 | 保持 `java <22` 上界；加版本探测与降级 |
| 无界小集合 | `TextureManagerMixin.pbrRetryStamps`、`PoseBoneSkipMixin.COMPARE_BONES`、`ShaderCurvesMixin.LOGGED_SOURCE_HASHES` | 长会话缓慢增长（量级有限） | 加清理/上限 |
| 缓存 | 纹理三缓存有 256/512/192MB 上限；`BBSEffectLoader` reload 清理 | 良好 | 保持 |

### 4.7 国际化（i18n）

| 问题 | 证据 | 影响 | 建议 |
|---|---|---|---|
| 英文缺 663 键 | `bbspp/strings` zh 1,114 / en 451（几乎所有 VFX/VFX-Lights/物品喷射轨道） | 英文用户看到 `bbspp.keyframe.vfx.xxx` | 补键；加 CI 键对齐检查 |
| 3 个代码引用键完全缺 | `bbspp.ui.animation_state.panel_inspector/preview/timeline` | 三处面板标题显示原始 key | 补 zh/en |
| 1 个 BBS 补充键缺英文 | `bbs.ui.camera.clips.bbs:release_use_item` | 英文显示 key | 补 |
| `assets/bbspp/lang` 不生效 | 71 键，69 重复，2 独有且无人用 | 维护错觉 | 删除，独有键并入 strings |
| 硬编码翻译 1,747 行 | `KeyframeLocalizer` + `IRLightsZHTranslator` | 无法由语言文件覆盖、升级易漏 | 迁移为 JSON 资源 |
| 英文键残留 | `bbs.config.bbs_snow.*`（22）、`bbs.config.cml_*`（6）已无引用 | 文件膨胀 | 清理 |
| 语言判断重复 | `LangKeyMixin`/`StringKeyMixin` | 两处同逻辑 | 抽公共 |

### 4.8 构建与产物缺陷

| 问题 | 证据 | 影响 | 建议 |
|---|---|---|---|
| 1.21.11 mixin json 非法 | `build/1.21.11/resources/main/bbsplusplus-shadercurves.mixins.json` 数组尾逗号 | Mixin 的 lenient Gson 能读，但 CI/工具会失败 | 修正清理正则（`,\s*([\]}])`）或重新序列化 |
| excludes 2 条指向不存在文件 | `mixin/xavin/XavinClientMixin.java`、`mixin/UIAnimationStateKeyframesMixin.java` | 无效配置，掩盖遗漏 | 删除 |
| excludes 1 条重复 | `mixin/MotionPathMixin.java` ×2 | 同上 | 去重 |
| 清理路径硬编码 | `build.gradle:153` `fileTree("build/1.21.11/resources/main/")` | 改 buildDir/版本即静默失效 | 用 `layout.buildDirectory` |
| 反射字符串 34 处 | 入口/渲染器/编辑器/内部接口名 | 改包名/内部类名即运行期失败 | 抽 `ClassNames` 常量或换事件 |
| refmap 声明不存在 | 2 个 config 声明 `bbsplusplus-refmap.json`，产物无该文件 | 启动 warn；旧 Loom 下会全量静默失败 | 删除字段或启用 AP |
| 无 CI / 无测试 | — | 重构无护栏 | 见 P6 |
| 设置注册过晚 | `CLIENT_STARTED` | 早期读取 null；两处 NPE 风险 | 移到 `RegisterSettingsEvent` |
| sanitizer 风险 | `SettingsFileSanitizer.java:124-218`；`SettingsManagerMixin.java:29-35` | 每次 load 都跑、无限备份、失败路径可截断、直接改写 BBS 主设置 | 只跑一次 + 失败放弃 + 备份上限 |
| 配置根分裂 | `config/bbs/settings/` vs `config/bbsplusplus/` | 备份遗漏 | 统一 |
| 写 BBS 资产目录 | `ModelCullingConfigManager.java:107,139-141,211` | 与 BBS/用户资源混在一起 | 移到自有目录或加清单 |
| dev/运行版本漂移 | Iris 1.7.6 vs 1.7.0；physics 1.0.3 未声明 | 线上回归 | 版本对齐并写入文档 |

### 4.9 文档与实现不一致

| 文档描述 | 实现现状 | 建议 |
|---|---|---|
| "新增结构棒与结构伪装"（README:93-98） | BBS 2.7 已内置 `StructureForm`/`structure_wand`；本模组没有注册结构表单 | 改为"对 BBS 内置结构功能的增强/汉化"或删除该节 |
| `/bbsplusplus aaa_particle trigger`（README:64-65） | 网络空实现，多人无效 | 标注"仅单人/未完成"或实现 |
| "1.21.11 适配"（多处） | 98 类排除、入口复制、20% 功能缺失 | 标注"实验性构建，功能子集" |
| 术语表基于 BBS FS 2.8 | 依赖/编译是 2.7 | 统一到 2.7，或明确"面向 2.8 的翻译储备" |
| README 功能表与更新日志重复 | 338 行，维护两份 | 功能表保留，日志移 `CHANGELOG.md` |
| `IRLightsZH` 注释"无需额外初始化" | 类仍 `implements ClientModInitializer` 且未注册 | 删除或修注释 |

---

## 5. 结构优化方案

### 5.1 原则（写进 CONTRIBUTING，后续 PR 按此评审）

1. **一域一包**：一个功能域（影片/回放/关键帧/姿势/伪装/粒子/渲染/导出/i18n/设置/核心/桥接/兼容/Mixin）一个顶层包，不再有第二棵 `client/ui`。
2. **Mixin 薄层**：Mixin 只做"定位 + 调用"，业务逻辑放普通类；单个 Mixin 文件 >300 行必须拆。
3. **桥接显式**：`api/` 改名 `bridge/`，只允许 Mixin 与目标类之间使用；普通业务代码不得引用。
4. **上游镜像层跟随命名**：`forms/renderers`、`clips`、`keyframes` 等与 BBS 同构的部分，命名与上游一致，便于对照升级。
5. **自研层不借上游词**：不新增顶层 `clips`/`forms` 这类被 BBS 占用的词；用 `film`/`replay`/`morph` 等领域名。
6. **资源按 namespace 分家**：自有资源只放 `assets/bbsplusplus/`；`assets/bbs/` 只作为"必须覆盖 BBS"的最小集合，且每个文件写注释说明原因与对应版本。
7. **版本隔离显式化**：1.20.x 与 1.21.11 的差异用"共享基类 + 两个薄后端"或独立 source set，不用复制整类 + 反射探测。

### 5.2 目标包结构（15 个顶层包）

| 目标包 | 职责 | 由现有迁入 | 约文件 |
|---|---|---|---:|
| `core` | 入口、命令、mod 常量、日志 | 根入口 4 + `command/` 4 + `network/`（并入或删） | 12 |
| `settings` | 设置注册/持有/迁移、按键冲突 | `BBSAddonsSettings`、`CMLSettings`、`BBSPPPSettings`、`BBSPlusPlusSettings`、`client/settings`、`util/keybind` | 9 |
| `bridge` | Mixin 与目标类之间的接口/Holder | 现 `api/` 26 | 26 |
| `compat` | Iris/VFX/IRLite/Together/Lezy 适配 + mixin plugin | 现 `compat/`、`client/compat/` | 9 |
| `i18n` | 语言表、KeyframeLocalizer、IRLights 翻译、UIKeys | 根翻译表 + 各 `*UIKeys` | 10 |
| `film` | 影片库、影片面板、剪辑 UI | `ui/film`（不含 replays/overlays）+ `client/ui/film` 库相关 | 20 |
| `replay` | 回放动作、时间、重播剪辑、动作时间线 | `clips/` 9 + `ui/film/replays` 5 + `timeline/` 3 | 17 |
| `keyframe` | 关键帧轨道/工厂/编辑器扩展 | `keyframes/` 6 + `client/keyframes` 1 + `client/ui/keyframes` 4 + `KeyframeTrackStyle` + `UndoHistoryLabeler` | 13 |
| `pose` | 姿势编辑、骨骼纹理/PBR/附魔、骨骼跳过 | `client/ui/pose` 7 + `bridge` 中 Pose Holder | 8 |
| `morph` | 伪装面板/分类/表单编辑器 | `forms/` 10 + `ui/morphing` 11 + `ui/forms` 9 + `client/ui/forms` 4 | 30 |
| `particle` | 粒子 Plus、AAA、物品喷射数据 | `particles/` 4 + `ui/particles` 5 | 9 |
| `render` | 渲染器、屏幕效果、纹理补间、结构、流体模拟 | `client/renderer` 14 + `screen` 5 + `texture` 5 + `structure` 2 + `forms/renderers` 4 + `simulation` 2 | 32 |
| `export` | Premiere / SRT / 字幕 / 动画导出 | `premiere/` 12 + `export/` 1 + `utils/` 3 个导出器 | 16 |
| `ui` | 通用 UI 组件：迷你窗口、列表、曲线选择、动画预览、缩放 | `ui/miniwindow` 9 + `list` 1 + `client/ui/utils` 3 + `curves` 1 + `animations` 2 + `presets` 1 | 19 |
| `mixin` | 全部 Mixin，按域再分 `film/ keyframe/ render/ compat/ accessor/` | 232 | 232 |

> 若想更少，可把 `pose` 并入 `keyframe`、`particle` 并入 `render`，得到 13 个顶层包。

### 5.3 迁移映射与执行顺序

**顺序原则**：先搬迁"叶子包"（无反向依赖），再搬迁"被依赖包"，最后动入口与 Mixin。

1. `compat`、`i18n`、`export`（依赖少）
2. `render`、`particle`、`ui`
3. `keyframe`、`pose`、`morph`
4. `replay`、`film`
5. `settings`、`bridge`、`core`
6. `mixin`（最后，因为 7 个 config 的 `package` 字段、`build.gradle` 98 条 excludes、34 处反射字符串都要同步）

**每步必须同步的位置**

| 位置 | 数量 | 说明 |
|---|---:|---|
| `build.gradle` excludes | 98 条 | 全路径，包移动即失效 |
| 7 个 `*.mixins.json` 的 `package` | 7 | `gbeic.bbsplusplus.mixin` / `.client` / `.client.accessor` / `.irlite` / `.shadercurves` / `.vfx` |
| `fabric.mod.json` entrypoints | 6 个类名 | 以及 1.21.11 的 `processResources` 字符串替换 |
| 反射字符串 | 34 处 `Class.forName` + 7 处 Proxy 接口名 | 建议先抽常量 |
| `build.gradle` 的 exclude 正则清洗 | `:150-167` | 按类名删 mixin json 条目 |
| 资源引用字符串 | `Link.assets("...")` 等 | 包移动不影响，但改名会影响 |

### 5.4 命名规范（统一表）

| 概念 | 现状 | 统一为 |
|---|---|---|
| mod id | `bbsplusplus` / `bbspp` / `bbsppp` | `bbsplusplus`（日志与代码）；旧 ID 只作为兼容别名 |
| 入口类 | `BBSPlusPlusMod` / `BBSFSloveCML` / `BBSFSloveCMLClient` / `BBSFSloveCMLAddon` / `BBSFSloveCMLClientAddon` | `BBSPlusPlusMod` / `BBSPlusPlusClient` / `BBSPlusPlusAddon` / `BBSPlusPlusClientAddon` |
| 类前缀 | `BBSPlusPlus*` / `BBSFSloveCML*` / `BBSPP*` / `BBSPPP*` / `UIBBSPP*` | 入口保留品牌前缀；其余按域命名（`FilmLibrary*`、`Pose*`、`Keyframe*`） |
| Mixin 成员前缀 | `bbspp$` / `bbspp_cml$` / `bbsppp$` / `BBSPP$` | **统一 `bbspp$`** |
| Mixin 配置 | `bbsplusplus.mixins.json` + `bbspp-fslovecml.*`（3 个） + 3 个 feature | `bbsplusplus.mixins.json` + `bbsplusplus.<feature>.mixins.json` |
| 纹理术语 | `UVScale` / `UVTransform` | 对外统一 `UVTransform`（内部字段可用 Scale） |
| 纹理术语 | `TextureGrade` / `ColorGrade` | 管线 `TextureGrade`；剪辑保留 `ColorClip` |
| 影片术语 | `Film` / `Video` / `Movie` | `Film`（`Video` 仅指视频文件/剪辑） |
| 迷你窗口 | `MiniWindow` + 5 个抽象 | 收敛为 `MiniWindowHost` + `MiniWindow` |
| 配置 | `config/bbs/settings/bbspp.json`、`config/bbsplusplus/...` | 统一 `config/bbsplusplus/*.json`，旧文件迁移 |
| 资源 | `assets/bbspp/*`、`assets/bbsplusplus/*`（死包） | 二选一：保留 `bbspp` 作为资源/翻译命名空间（改动最小），或统一 `assets/bbsplusplus/*` 并删除死包；不要两套并存 |

### 5.5 Mixin 治理规范

1. **合并 9 组同名类**：统一到 `mixin/client`，逐组移植 root 独有注入；唯一例外 `UISoundOverlayPanelMixin` 保留 root。合并后删除 8 个 root 文件。
2. **修 8 处死注入**（4.1）：优先改父类注入 + `instanceof`，或删除功能。
3. **`@Unique` 全覆盖**：`UILinkKeyframeFactoryMixin` 25 个成员、8 个静态常量、`UIVanillaSoundListMixin.LOGGER` 先补。
4. **handler 命名**：统一 `bbspp$` 前缀（现有少数 `onSet`/`onSetupTail` 等短名）。
5. **`require` 策略**：
   - `bbspp-fslovecml.mixins.json`（8 条，目标全在 BBS main）→ `defaultRequire: 1`；
   - 核心 BBS 目标逐条 `require = 1`（约 100 处）；
   - 可选依赖（IRLite/Iris/VFX/VFX-Lights/Together）保持 0，但由 plugin 守卫 + 缺失时 warn；
   - 跨版本兼容条目用 `expect = 0` 显式声明，不用裸 0。
6. **priority 只用于必要场景**：`UIKeyframes=2000`、`Together=500` 加注释说明理由；其余回 1000。
7. **@Overwrite 收敛**：`ShaderCurves` 3 处、`UIClips#resolveCollisions`、`BinaryReader#skip` 优先改 `@Inject`/`@ModifyVariable`；保留处加 `@author/@reason` 与版本比对说明。
8. **refmap**：删除 2 个失效的 `"refmap"` 字段，或显式开启 Loom AP 生成。
9. **CI 校验**：脚本核对"json 声明 ↔ 文件存在 ↔ 目标方法存在（以 jar 为准）"。

### 5.6 版本隔离方案（替代 98 条 excludes + 反射）

**短期（P3）**：
- 把 `BBSPlusPlusModClient` / `12111` 合并，差异抽 `ClientPlatform` 接口（两实现：`ClientPlatform120`、`ClientPlatform12111`），避免整类复制。
- 反射字符串集中到 `compat/reflection/ClassNames`，缺失时统一 warn + 功能开关。
- 1.21.11 的 shader 资源补齐或剔除（`picker_models.*`、`bbspp/*`、`bbsvfx/*`）。
- 构建期 JSON 清洗改 Gson 重新序列化，杜绝尾逗号。

**中期（P6）**：
- 把 1.21.11 专用代码移入 `src/main/java-1.21.11`，只保留"后端实现"（渲染、shader、输入），共享代码不复制。
- 逐步删除"运行期反射探测"：能编译期分流的就分流，不能的集中到一个 `PlatformSupport` 门面。
- 为 1.20.1 与 1.21.11 各写一份 10 分钟 smoke 清单（见第 8 节）。

### 5.7 资源与语言治理

1. **shader**：删除 `model.vsh`/`model.json`；保留 `picker_models.*` 并注明版本依赖；`bbsvfx` 覆盖最小化并锁定 VFX 版本；1.21.11 产物剔除 1.20.1 core shader。
2. **方块**：长期迁 `bbsplusplus:`；若必须保留 `bbs:`，复制自有模型/贴图，避免与 BBS 视觉资源耦合。
3. **语言单一来源**：以 `assets/bbspp/strings/{lang}.json`（或迁移后的 `assets/bbsplusplus/strings`）为唯一 BBS UI 表；`assets/bbs/lang` 只留原版 I18n 必需键；删除 `assets/bbspp/lang`；`assets/bbs/assets/lang` 的键合并进主表。**不要两套命名空间并存**。
4. **键规范**：`bbsplusplus.<domain>.<feature>.<item>`；旧 `bbspp.keyframe.*` 保留读取别名（迁移期）。
5. **校验**：CI 脚本比对 zh/en 键集合（允许白名单）、代码引用键存在性、资源引用存在性。

### 5.8 仓库治理

| 动作 | 内容 |
|---|---|
| 删除 | 根目录 `build_build*.log`；`.mimocode/`、`_audit_*` 临时文件；`mchorse/*.class` |
| 忽略 | 根 `*.log`、`.mimocode/`、`.workbuddy/`（已）、`.error_report_analysis/`（已） |
| 移出 | `libs/bbs-fs-*` 三份上游源码、`libs/Iris-1.20.1`、`libs/bbs-lezy-main`、`libs/bbs-physics-engine-master`、`libs/better-camera-perspective*` → 单独 refs 仓库 / LFS；主仓库只留 5 个构建 jar |
| 保留 | `libs/archive/bbs-2.7/*`、`libs/bbs-2.7-1.21.11.jar`、`libs/BBS-VFX*`、`libs/VFX-LIGHTS*` |
| 文档 | README 拆 `CHANGELOG.md`；新增 `docs/ARCHITECTURE.md`、`docs/COMPATIBILITY.md`、`docs/RELEASE_CHECKLIST.md` |
| 本地清理 | `run/`、`build/`、`.gradle/`、`crash*`、`.workbuddy/` 可删（需确认无未提交调试数据） |

---

## 6. 删除清单（执行时逐项勾）

### 6.1 代码（P1 净减约 1,100 行；P2 再减约 1,000 行）

| 文件 | 行数 | 前置条件 |
|---|---:|---|
| `events/ClientEventHandler.java` | 51 | 确认无人注册（已确认） |
| `ui/film/replays/overlays/UIQuickReplayOverlayPanel.java` | 214 | 随上一行 |
| `ui/film/replays/UIReplaysEditorExtended.java` | 117 | — |
| `ui/film/replays/BoneTextureUtils.java` | 113 | 随上一行 |
| `client/ui/keyframes/UIAAAParticleLinkKeyframeFactory.java` | 29 | 确认无反射注册（已确认） |
| `IRLightsZH.java` | 15 | — |
| `mixin/client/MobFormRendererEnsureEntityMixin.java` | 27 | — |
| `BBSFSloveCML.java` | 34 | 先把 `fabric.mod.json` 的 main 入口改为只留 `BBSPlusPlusMod` |
| `BBSPPPModClient.java` | 14 | 把 2 处 LOGGER 改到 `BBSPlusPlusMod.LOGGER` |
| `network/BBSPlusPlusNetwork.java` + `command/modules/AAAParticleCommandModule.java` | 18 + 87 | **二选一**：实现网络，或连命令一起删（README 同步） |
| `util/SettingsFileSanitizer.java` + `mixin/SettingsManagerMixin.java` | 371 + 31 | 建议保留，改为只跑一次/失败放弃/备份上限（BBS 2.7 仍有颜色列表） |
| `clips/ReplayTimeBridge.java` | 61 | 把调用点改 `ReplayTimeMath` |
| 8 个重复 Mixin（root 版） | — | 逐组移植后删（见 5.5） |
| `client/BBSPlusPlusModClient12111.java` | 259 | 合并后删 |

### 6.2 资源（净减约 20 个文件）

| 路径 | 原因 |
|---|---|
| `assets/bbs/shaders/core/model.vsh` | 与上游相同 |
| `assets/bbs/shaders/core/model.json` | 无人消费的 uniform |
| `src/main/resources-shared/shaders-1.21.11/assets/bbs/shaders/core/model.vsh` | 与上游相同；同时删 build.gradle 的复制逻辑 |
| `assets/bbspp/lang/{en_us,zh_cn}.json` | 不生效 + 重复（独有 2 键并入主表） |
| `assets/bbspp/textures/collapse.png` | 零引用 |
| `assets/bbs/assets/structures/oak_tree.nbt` | 零引用 |
| `assets/xavin/lang/*.json` | 跨 mod 覆盖，改为运行时映射或删除 |
| 1.21.11 产物中的 `picker_models.json/.vsh`（构建期剔除） | 格式过期 |
| `assets/bbsvfx/shaders/core/*.json` 中的非必要项 | 评估后只留最小集 |

### 6.3 构建/配置

| 项 | 动作 |
|---|---|
| `build_build1201.log`、`build_build1204.log` | 删除 + `.gitignore` 加 `*.log` |
| `build.gradle` excludes：`MotionPathMixin` 重复 | 去重 |
| `build.gradle` excludes：`xavin/XavinClientMixin`、`UIAnimationStateKeyframesMixin` | 删除（文件已不存在） |
| `gradle.properties` 中未使用的 `minecraft_version` / `yarn_mappings` 全局值（注释说是兜底） | 确认后删或保留注释 |
| 2 个 config 的 `"refmap"` 字段 | 删除或启用 AP |
| `ShaderCurvesMixinPlugin` 的 `CelestialUniformsMixin` 条目 | 删除（对应类不存在） |
| `fabric.mod.json` 的 `breaks.aaa_particles >2.2.3` | 评估放宽 |

### 6.4 仓库/本地

| 项 | 动作 |
|---|---|
| `libs/bbs-fs-2.6-1.20.1`、`bbs-fs-2.7-1.20.1`、`bbs-fs-1.21.11`（770 tracked 文件） | 移出主仓库 |
| `libs/Iris-1.20.1`（911）、`bbs-physics-engine-master`（194）、`bbs-lezy-main`（93）、`better-camera-perspective`（29） | 移出主仓库 |
| `libs/bbs-2.5.2-1.20.4-named-dev.jar`、`libs/bbs_physics-1.0.2*.jar`、`libs/archive/bbs-2.6.1/*` | 评估后移除（未被 build.gradle 引用） |
| `mchorse/`（5 个 .class） | 删除 |
| `run/`、`build/`、`.gradle/`、`crash-report-*`、`crash_report/`、`.workbuddy/`、`.mimocode/` | 本地清理（可选，释放 ~1GB） |

---

## 7. 分阶段实施计划表

> 每阶段结束都必须：`build1201` + `build1204` +（若涉及）`build12111` 全绿；启动客户端 smoke；提交为独立 commit/PR，附验证记录。估算为 1 人、熟悉项目的前提下。

### 阶段总览

| 阶段 | 主题 | 目标 | 估算 | 风险 | 产出 |
|---|---|---|---:|---|---|
| **P0** | 基线 | 建立可对比的构建/运行基线 | 0.5–1d | 低 | 基线 jar、日志、smoke 记录、清单 |
| **P1** | 零风险清理 | 删死代码/死资源/死配置，修文档 | 1–1.5d | 极低 | 净减 ~1,100 行；构建仍绿 |
| **P2** | 去重与入口合并 | 9 组 Mixin、双入口、双渲染器、设置、util 合并 | 2–3d | 低–中 | 入口 6→4、设置持有 4→1、重复 -8 文件 |
| **P3** | 冲突消解 | 修 8 处死注入、ID/资源策略、require、版本门禁 | 3–5d | 中 | 功能恢复；冲突可见 |
| **P4** | 包结构迁移 | 15 包目标结构落地 | 4–6d | 中–高 | 结构清晰；构建同步 |
| **P5** | 巨型类拆分 | 5 个高优先级 + 5 个中优先级 | 5–8d | 中 | 可读性提升；逻辑可测 |
| **P6** | 版本隔离与工程化 | 12111 收敛、CI、校验脚本、最小测试 | 3–5d | 中 | 防回归护栏 |
| **P7** | 长期 | 减少 Mixin、采用官方事件、BBS 2.8 跟进 | 持续 | — | 生态兼容 |

**总计：约 19–30 人日**（不含 P7 的持续投入）。

### P0 基线（0.5–1 天）

| 编号 | 任务 | 涉及 | 验收 |
|---|---|---|---|
| P0-1 | 记录当前三构建产物 SHA256、启动日志、mod 列表 | `build/`、`run/*/mods` | 形成 `docs/BASELINE.md` |
| P0-2 | 客户端 smoke 清单跑一遍（现有功能逐项打勾/记录坏项） | 1.20.1 + 1.21.11 | 已知坏项与本文 4.1 对齐 |
| P0-3 | 把本文的 4.1/4.4/6.1 转成可勾选清单 | `docs/` | 清单可执行 |
| P0-4 | 打 tag/分支 `refactor/plan` | git | 可回滚 |

### P1 零风险清理（1–1.5 天）

| 编号 | 任务 | 涉及 | 风险 | 验收 |
|---|---|---|---|---|
| P1-1 | 删 10 个死类（6.1 前 9 项 + 日志类） | 见 6.1 | 极低 | `build1201/1204/12111` 绿；启动无 NoClassDef |
| P1-2 | 删死资源（model.*、bbspp/lang、collapse、oak_tree、xavin lang） | 见 6.2 | 低（shader 需实机看模型） | 模型/方块/UI 正常 |
| P1-3 | 删 2 个 build 日志 + `.gitignore` 补 `*.log` | 根目录 | 无 | `git status` 干净 |
| P1-4 | 清理 build.gradle 3 条无效 excludes + 1 条重复 | `build.gradle` | 无 | 构建绿；1.21.11 jar 无变化 |
| P1-5 | 删 `ShaderCurvesMixinPlugin` 的 `CelestialUniformsMixin` | `compat/` | 无 | 构建绿 |
| P1-6 | 修 README 与实现不一致的 3 处 | `README*.md` | 无 | 文档与代码一致 |
| P1-7 | 合并 `assets/bbspp/lang` 的 2 个独有键到 strings | 资源 | 低 | 键存在；UI 显示正常 |

### P2 去重与入口合并（2–3 天）

| 编号 | 任务 | 涉及 | 风险 | 验收 |
|---|---|---|---|---|
| P2-1 | 合并 `BBSPlusPlusModClient` + `12111`（抽 `ClientPlatform`） | 2 文件 | 中 | 三构建绿；AAA/物品喷射/破坏魔杖注册正常 |
| P2-2 | 合并 `FluidFormRenderer` 双版本（抽象基类） | 2 文件 | 中 | 1.20.1 与 1.21.11 流体外观一致 |
| P2-3 | 合并 9 组同名 Mixin（逐组，先保留 client） | 18 文件 | 中 | 构建绿；重点功能回归（剪辑/姿势/预览/回放） |
| P2-4 | 合并设置持有类为 `settings/ModSettings` + `SettingsRegistry` | 根 + settings | 中 | 设置读写/迁移正常 |
| P2-5 | 入口 6→4：删 `BBSFSloveCML`、`IRLightsZH`、`BBSPPPModClient`；`BBSFSloveCML*` 改名 | fabric.mod.json + 类 | 中 | 启动日志无缺失注册 |
| P2-6 | 合并 `util` + `utils`（保留 `util`，加子包） | 24 文件 | 低 | 构建绿 |
| P2-7 | `LangKeyMixin`/`StringKeyMixin` 抽公共语言判断 | 2 文件 | 低 | 中文/英文切换正常 |
| P2-8 | 删 `ReplayTimeBridge`，调用点改 `ReplayTimeMath` | 2 文件 | 低 | 回放循环正常 |

### P3 冲突消解（3–5 天）

| 编号 | 任务 | 涉及 | 风险 | 验收 |
|---|---|---|---|---|
| P3-1 | 修 D-01~D-08 八处死注入（能改父类则改，否则删功能） | 8 文件 | 中 | 对应功能逐项实机验证 |
| P3-2 | 合并 `UIReplaysEditorUtilsMixin#pickForm` 双 cancel | 2 文件 | 中 | 姿势参数刷/优先级轨道都正常 |
| P3-3 | `UILinkKeyframeFactoryMixin` 25 成员 + 8 静态常量补 `@Unique` | 1 文件 | 低 | 构建绿；UUID/Link 关键帧正常 |
| P3-4 | `bbspp-fslovecml.mixins.json` 设 `defaultRequire: 1`；核心目标逐条 `require=1` | config + mixin | 中 | 启动无注入异常；日志可查 |
| P3-5 | 删 2 个失效 refmap 字段；统一 `remap` 口径 | 2 json + mixin | 低 | 启动无 refmap warn |
| P3-6 | 方块/表单/剪辑加"注册前查重"断言与日志 | 注册点 | 低 | 无重复时安静；重复时明确报错 |
| P3-7 | 设置 id `bbspp` → `bbsplusplus` + 旧文件迁移 | settings | 中 | 旧设置能读入；新文件生成 |
| P3-8 | 补 `BBSApi.requireVersion` + 收紧 `depends/breaks` | fabric.mod.json + 入口 | 低 | 版本不符时清晰报错 |
| P3-9 | 修 1.21.11 JSON 尾逗号 + 剔除过期 shader + 反射集中化 | build.gradle + 资源 | 中 | 1.21.11 产物 JSON 合法；无 shader 报错 |
| P3-10 | 命令权限 + 空广播二选一（实现或删） | command/network | 中 | 非 OP 不能执行；多人行为符合文档 |

### P4 包结构迁移（4–6 天）

| 编号 | 任务 | 涉及 | 风险 | 验收 |
|---|---|---|---|---|
| P4-1 | 先迁移 `compat/i18n/export/render/particle/ui`（叶子） | 约 90 文件 | 中 | 构建绿；UI/渲染回归 |
| P4-2 | 迁移 `keyframe/pose/morph/replay/film` | 约 80 文件 | 中高 | 编辑器全流程回归 |
| P4-3 | 迁移 `settings/bridge/core` + 入口 | 约 40 文件 | 中 | 启动注册顺序正确 |
| P4-4 | 最后迁移 `mixin`，同步 7 个 config + 98 条 excludes + 反射字符串 | 232 文件 + build.gradle | 高 | 三构建绿；无 `ClassNotFound` |
| P4-5 | 更新 `docs/ARCHITECTURE.md` 包说明 | docs | 低 | 新人可按图找代码 |
| P4-6 | 删 `events/`、`network/`（若 P3 已删） | 2 包 | 低 | 无残留引用 |

### P5 巨型类拆分（5–8 天）

| 编号 | 任务 | 目标拆分 | 风险 | 验收 |
|---|---|---|---|---|
| P5-1 | `KeyframeLocalizer` → JSON + `KeyframeNameProvider` | 1,308 → ~250 + 资源 | 中 | 中英文轨道名逐项对比 |
| P5-2 | `mixin/UIClipsMixin` → 4–6 个职责 Mixin/助手 | 947 → 6×~150 | 高 | 剪辑拖拽/粘贴/滚轮/框选全回归 |
| P5-3 | `UILinkKeyframeFactoryMixin` → UI 控件构建器 + 薄 Mixin | 619 → ~200 | 中 | 纹理补间编辑器全功能 |
| P5-4 | `AAAParticleFormRenderer` → emitter/render/preview/trigger | 1,352 → 4×~300 | 高 | AAA 预览/回放/穿透 |
| P5-5 | `UIFilmPanelMiniWindowSupport` → dock/inspector/input | 1,088 → 3×~300 | 中高 | 迷你窗口停靠/浮动/布局 |
| P5-6 | `TextureTweenManager` → math/cache/manager | 953 → 3×~270 | 中 | 三种补间模式对比 |
| P5-7 | `FluidFormRenderer`（P2 合并后）→ 数学/后端 | 784+844 → ~800 | 中 | 流体预览/世界 |
| P5-8 | `UIMiniWindow(Manager)` → window model + drag/dock + persistence | 1,478 → 3 类 | 中 | 拖拽/折叠/记忆布局 |
| P5-9 | `ShaderCurvesMixin` 的 3 个 @Overwrite 改注入或集中 | 597 | 高 | 光影曲线 + Lezy 共存 |

### P6 版本隔离与工程化（3–5 天）

| 编号 | 任务 | 涉及 | 风险 | 验收 |
|---|---|---|---|---|
| P6-1 | 1.21.11 代码分 source set，共享逻辑不复制 | `src/main/java-1.21.11` | 中高 | 两版构建绿；功能差异表 |
| P6-2 | 反射探测集中到 `PlatformSupport`，缺失统一日志 | 34 处 | 中 | 缺失功能时有 warn |
| P6-3 | CI：三构建矩阵 + 产物内容检查（无过期 shader/非法 json） | `.github/workflows` | 低 | PR 必过 |
| P6-4 | Gradle 校验任务：mixin 声明↔文件↔目标方法、lang 键、资源引用、excludes 有效性 | `build.gradle`/脚本 | 中 | 校验失败即红 |
| P6-5 | 最小单元测试：纹理补间数学、时间轴求值、SRT/XML 生成、路径 URL | `src/test` | 低 | 覆盖率不要求高，关键纯函数覆盖 |
| P6-6 | 发布清单：版本号/兼容矩阵/已知限制 | `docs/RELEASE_CHECKLIST.md` | 低 | 每次发布照做 |

### 建议里程碑

| 里程碑 | 内容 | 对应阶段 |
|---|---|---|
| M1 "干净基线" | 死代码清零、构建绿、文档一致 | P0–P1 |
| M2 "功能可信" | 8 处死注入修复、require 收紧、冲突可见 | P2–P3 |
| M3 "结构可读" | 15 包落地、命名统一、巨型类拆分过半 | P4–P5 |
| M4 "可防回归" | CI + 校验 + 测试 + 版本隔离 | P6 |

---

## 8. 验证与回归矩阵

### 8.1 每次提交的最低验证

| 项 | 命令/方式 | 通过标准 |
|---|---|---|
| 1.20.1 构建 | `gradlew build1201` | BUILD SUCCESSFUL；无新增 Loom `Cannot remap` |
| 1.20.4 回归 | `gradlew build1204` | 同上 |
| 1.21.11 构建 | `gradlew build12111` | 同上；产物 JSON 可被严格解析 |
| 启动 | 1.20.1 客户端 | 无 Mixin 注入异常、无 `NoSuchMethodError`、注册日志齐全 |
| 日志 | 搜索 `ERROR`/`Exception`/`Cannot remap` | 无新增 |

### 8.2 功能 smoke 清单（每阶段至少跑一次）

| 模块 | 检查点 |
|---|---|
| 影片库 | 开关开/关；快捷栏；新建/重命名/删除；拖拽移动；搜索排序 |
| 影片编辑 | 打开/关闭；F5 可见性；工具收纳；轨道排序解锁；预览 |
| 剪辑 | 拖拽/吸附/碰撞/粘贴/框选/双击编辑；hotbar/cinematic/replay 三种剪辑 |
| 关键帧 | 增删改/多选/列选择/布局锁/纹理补间三种模式/轨道名中英 |
| 姿势 | 多选粘贴、参数刷、骨骼跳过、PBR/纹理/附魔按钮、树形列表 |
| 伪装 | 新旧布局、网格/列表、分类移动、Blockbench 打开、AAA/物品喷射/流体表单 |
| 粒子 | 粒子 Plus 组件、AAA 触发/循环/穿透、物品喷射渲染/性能开关 |
| 导出 | Premiere XML/SRT、独立音频、动画导出、字幕 |
| 设置 | 中英文切换、重启保留、bbspp→bbsplusplus 迁移（P3 后） |
| 兼容 | 装/不装 VFX、VFX-Lights、IRLights、physics、lezy、together、aaa、MediaPlayer |
| 1.21.11 | 启动、影片库、关键帧、姿势、导出；确认功能子集与文档一致 |

### 8.3 兼容组合矩阵

| 组合 | 目的 |
|---|---|
| BBS 2.7 + 本模组 | 基线 |
| + BBS-VFX 1.3 | shader/渲染重叠 |
| + VFX-Lights 1.0 | 渲染/UI 重叠 |
| + IRLights 1.1.7 | 引导线注入 |
| + bbs_physics 1.0.3 | UIClips/ModelFormRenderer 重叠 |
| + bbs-lezy 0.3.1 | ShaderCurves 重叠 |
| + bbs_together 1.7.3 | 网络/回放选择 |
| + aaa_particles 2.2.3 | AAA/Unsafe |
| + MediaPlayer-BBS 1.0.2 | 视频伪装 |
| 不装 Iris / 装 Iris | shader 曲线分支 |

### 8.4 建议新增的自动校验

| 脚本 | 检查内容 | 失败即阻断 |
|---|---|---|
| `verifyMixins` | 每个 json 条目有文件；每个 `@Mixin` 目标类存在；每个 `@Inject` 方法在目标类（以 jar 为准）；无未声明 mixin | 是 |
| `verifyResources` | `assets/` 下每个 png/nbt/json 至少一处引用（白名单除外）；无跨命名空间意外覆盖 | 告警/阻断 |
| `verifyLang` | zh/en 键集合差异（白名单）；代码引用键存在 | 是 |
| `verifyJar` | 无 `model.json`（1.21.11）、无过期 `picker_models.json`、mixin json 严格合法、jar 内 class 数符合预期 | 是 |
| `verifyExcludes` | excludes 条目都指向存在的文件；无重复；未排除类不引用已排除类 | 是 |

---

## 9. 风险登记册

| 风险 | 概率 | 影响 | 触发信号 | 缓解 |
|---|---|---|---|---|
| 合并 Mixin 时改变注入顺序，导致行为变化 | 中 | 高 | 某功能时好时坏 | 逐组合并；每组单独提交 + 回归；保留 `priority` |
| 删除 `model.vsh` 后 UV 变换在部分路径失效 | 中 | 中 | 模型纹理错位 | 先在实机确认 picker/CPU 路径；单独提交可回滚 |
| `require=1` 导致玩家启动崩溃 | 中 | 高 | 用户崩溃报告 | 只对硬依赖目标收紧；先灰度一个版本 |
| 包迁移漏改 excludes/反射字符串 | 高 | 中 | `ClassNotFoundException`/功能缺失 | P6-4 校验任务先行；按域小步提交 |
| 1.21.11 重构耗时超预期 | 高 | 中 | 工期拖延 | 降级为实验构建；先保 1.20.x |
| ID 迁移导致旧影片/存档失效 | 低 | 高 | 打开旧影片报错 | 保留旧 ID + 别名/转换器；绝不直接改名 |
| 删除 libs 导致历史版本无法复现 | 中 | 中 | 需要旧源码 | 移出到独立 refs 仓库并打 tag |
| 翻译表迁移引入漏译 | 中 | 低 | 轨道名回退英文 | 逐键 diff；保留硬编码兜底一个版本 |
| 第三方 addon 升级（VFX/Lezy）破坏注入 | 高 | 高 | 组合崩溃 | 版本区间 + 联合 smoke + 可选守卫 |
| 项目无人维护导致计划停摆 | 中 | 高 | 长期无提交 | 按里程碑交付；先做 P1/P3 的高收益项 |

---

## 10. 待决策事项

| # | 决策 | 选项 | 建议 | 影响面 |
|---|---|---|---|---|
| Q1 | 1.21.11 是否继续作为发布目标 | A 继续双版本 / B 降级实验 / C 放弃 | **B**：保留构建与文档，不阻塞 1.20.x | 工期、CI、文档 |
| Q2 | `bbs:` 命名空间 ID 是否迁移 | A 保留 / B 迁 `bbsplusplus:` + 兼容层 | **A**（冻结新增 + 断言）；方块长期再议 | 旧影片/存档 |
| Q3 | `libs/` 参考源码是否移出主仓库 | A 保留 / B 移 refs 仓库 / C 删 | **B** | 仓库体积、可复现性 |
| Q4 | 硬编码翻译表是否改资源 JSON | A 改 / B 保留 | **A** | 约 -1,700 行、可维护性 |
| Q5 | 是否接受 3.7.0 破坏性变更（设置 id/包名/命名） | A 接受一次 / B 渐进 | **A**：集中一次，之后稳定 | 用户升级体验 |
| Q6 | 是否投入 P7（用官方事件替代 Mixin） | A 是 / B 只做新增 | **A 长期**：BBS 2.7 已有 30+ 客户端事件 | 冲突面、升级成本 |

### P7 长期方向（可选，但收益最大）

BBS 2.7 的官方 addon API 已有 **11 个通用事件 + 30 个客户端事件**（`RegisterFormRenderersEvent`、`RegisterFormEditorsEvent`、`RegisterClipPanelsEvent`、`RegisterValueWidgetsEvent`、`RegisterTrackStylesEvent`、`RegisterKeyframeEditorsEvent`、`RegisterImportersEvent`、`RegisterFilmToolsEvent`、`RegisterPreviewOverlaysEvent`、`FilmEvents`、`FilmEditEvents` 等）。当前只用了 7 个 import、其中注册类事件 3 个。可优先替换：

1. `BBSPlusPlusModClient.registerFormRenderer/registerFormEditor` 的反射 → `RegisterFormRenderersEvent` + `RegisterFormEditorsEvent`；
2. `UIClip.register(...)` 三处 → `RegisterClipPanelsEvent`；
3. `UIValueMap.register` → `RegisterValueWidgetsEvent`；
4. 轨道颜色/图标 → `RegisterTrackStylesEvent`；
5. 音频拖入 → `RegisterImportersEvent`；
6. 影片预览按钮 → `RegisterPreviewOverlaysEvent` / `RegisterFilmToolsEvent`；
7. 世界播放的 film 生命周期 → `FilmEvents`（替代部分 `BaseFilmController` Mixin）。

目标：3 个版本内把 232 个 Mixin 降到 150 以下，把内部 import 从 414 降到 300 以下。

---

## 附录 A 关键证据索引

| 主题 | 文件:行号 |
|---|---|
| 方块注册进 `bbs:` | `src/main/java/gbeic/bbsplusplus/BBSPlusPlusBlocks.java:53-74` |
| 表单/剪辑注册 key | `src/main/java/gbeic/bbsplusplus/BBSFSloveCMLAddon.java:43,47,51,60,79,83,152-158` |
| 设置注册（晚于 CLIENT_STARTED） | `src/main/java/gbeic/bbsplusplus/client/BBSPlusPlusModClient.java:30-35` |
| 死资源包注册 | `src/main/java/gbeic/bbsplusplus/client/BBSPlusPlusModClient.java:77` |
| 空网络广播 | `src/main/java/gbeic/bbsplusplus/network/BBSPlusPlusNetwork.java:15-18` |
| 命令伪成功/无权限 | `src/main/java/gbeic/bbsplusplus/command/modules/AAAParticleCommandModule.java:68-69`；`BBSPlusPlusCommand.java:31` |
| 死注入（旁观） | `src/main/java/gbeic/bbsplusplus/mixin/UIFilmPanelGameModeMixin.java:34-39,74-79` |
| 死注入（列表） | `mixin/UIListPoseParameterBrushMixin.java:49`；`mixin/UIReplayListCategoryMixin.java:111` |
| 死注入（迷你窗口） | `mixin/client/UIFilmPanelMiniWindowMixin.java:76` |
| 死注入（广告牌旧签名） | `mixin/BillboardFormRendererMixin.java:48-49` |
| 死注入（渲染距离） | `mixin/ModelBlockEntityRendererDistanceMixin.java:20` |
| 未声明 Mixin | `mixin/client/MobFormRendererEnsureEntityMixin.java:21` |
| 版本回归对照 | `bbs-2.5.2-1.20.4-named-dev.jar`：`UIFilmPanel` 有 `appear/close/disappear`；`2.6.1`/`2.7` jar：均无（移入 `UIDashboardPanel` 且 `final`） |
| 同点双 cancel | `mixin/UIReplaysEditorUtilsMixin.java:35`；`mixin/client/UIReplaysEditorUtilsMixin.java:32` |
| 无 @Unique 大 Mixin | `mixin/UILinkKeyframeFactoryMixin.java:36` 起 25 个成员 |
| 9 组同名 | `mixin/{BBSRendering,CubicCubeRenderer,ModelFormRenderer,UIDataContextMenu,UIFilmPreview,UIPoseEditor,UIReplaysEditor,UIReplaysEditorUtils,UISoundOverlayPanel}Mixin.java` 与 `mixin/client/` 同名文件 |
| shader 覆盖 | `src/main/resources/assets/bbs/shaders/core/model.*`、`picker_models.*`；`assets/bbsvfx/shaders/core/*.json` |
| shader 还原提交 | `git show 89bd62b -- src/main/resources/assets/bbs/shaders/core/model.vsh` |
| 英文缺键 | `src/main/resources/assets/bbspp/strings/en_us.json`（451）vs `zh_cn.json`（1,114） |
| 失效语言文件 | `src/main/resources/assets/bbspp/lang/*.json` |
| 1.21.11 非法 JSON | `build/1.21.11/resources/main/bbsplusplus-shadercurves.mixins.json`；`build.gradle:160` |
| excludes 无效项 | `build.gradle:298,301,340,368` |
| 反射点 | `BBSPlusPlusModClient.java:108,177,207`；`BBSFSloveCMLClient.java:177` 等 34 处 |
| Unsafe | `particles/ParticlePlusClient.java:75-95`；`util/XRayManager.java:22-104` |
| 阻塞 UI | `client/ui/utils/UIEffectPicker.java:122-135` |
| 未关闭流 | `utils/VanillaSoundResource.java:275` |
| sanitizer | `util/SettingsFileSanitizer.java:124-218`；`mixin/SettingsManagerMixin.java:29-35` |
| 写 BBS 资产目录 | `util/ModelCullingConfigManager.java:107,139-141,211` |
| BBS API 事件 | `libs/bbs-fs-2.7-1.20.1/src/client/java/mchorse/bbs_mod/api/client/events/`（30 个） |

## 附录 B 统计口径与复现

- 行数：`(Get-Content).Count`（文件物理行，含空行与注释）。
- Mixin 声明：解析 7 个 `*.mixins.json` 的 `mixins`/`client`/`server` 数组，按 `package` 拼 FQN，逐条检查 `src/main/java/**/<Class>.java`。
- 目标方法存在性：解析 `bbs-2.7-1.20.1-zh_CN.jar` 的 class 文件常量池/方法表；只认目标类自身声明。
- 语言键：`json.load` 后取键集合，区分 zh/en。
- shader diff：`Compare-Object`/逐行；`model.vsh` 与上游差异为空。
- 资源引用：类名/文件名在全库 `.java`+`.json` 中检索（排除自身声明）。
- 第三方重叠：读取 `libs/` 内 bbs_physics / lezy / VFX-Lights 源码或 jar 中的 mixin 配置与目标类。
- 所有脚本均只读；审计过程未写入仓库（本文件除外）。

## 附录 C 术语统一建议

| 旧/混用 | 建议 | 备注 |
|---|---|---|
| BBS++ / BBSPlusPlus / FSloveCML / CML / BBSPPP | BBS Cubed / `bbsplusplus` | 对外只保留一个产品名 |
| MiniWindow / Miniwindow | MiniWindow | 现有拼写已一致，收敛抽象类 |
| UVScale / UVTransform | UVTransform（对外） | 轨道 ID `bbspp_uv_transform` 保留 |
| TextureGrade / ColorGrade | TextureGrade（管线）/ ColorClip（剪辑） | — |
| Film / Video / Movie | Film | Video 仅指视频文件/剪辑 |
| Morphing / Form | 伪装（Morphing）/ 形态（Form） | 与 BBS 术语表一致 |
| Track / Clip / Keyframe | 轨道 / 剪辑 / 关键帧 | 与 BBS 术语表一致 |
| IRLite / IRLights | IRLite（插件 id）/ IRLights（界面名） | 翻译类改名 `IRLightsLangTable` |
| `bbspp$` / `bbspp_cml$` / `bbsppp$` | `bbspp$` | Mixin 成员前缀 |

## 附录 D 实施记录

### D.1 P1 零风险清理（2026-10-23 完成）

**代码删除（9 个类，约 620 行）**

| 文件 | 原行数 | 说明 |
|---|---:|---|
| `BBSFSloveCML.java` | 34 | 只打日志的第二入口；`fabric.mod.json` 的 main 入口改为仅 `BBSPlusPlusMod` |
| `BBSPPPModClient.java` | 14 | 旧日志门面；引用改为 `BBSPlusPlusMod.LOGGER` |
| `IRLightsZH.java` | 15 | 未注册的空壳入口 |
| `events/ClientEventHandler.java` | 51 | 零引用；`events/` 目录随之删除 |
| `ui/film/replays/overlays/UIQuickReplayOverlayPanel.java` | 214 | 仅被上一行引用 |
| `ui/film/replays/UIReplaysEditorExtended.java` | 117 | 零引用（注释仍写 BBS 2.3.1） |
| `ui/film/replays/BoneTextureUtils.java` | 113 | 仅被上一行引用 |
| `client/ui/keyframes/UIAAAParticleLinkKeyframeFactory.java` | 29 | 零引用 |
| `mixin/client/MobFormRendererEnsureEntityMixin.java` | 27 | 未声明、目标方法不存在的死 Mixin |

**资源删除**

- `assets/bbspp/textures/collapse.png`（零引用）
- `assets/bbs/assets/structures/oak_tree.nbt`（零引用）
- `assets/bbspp/lang/{en_us,zh_cn}.json`（不生效且与 `strings` 重复；目录删除）
- `build_build1201.log`、`build_build1204.log`（根目录调试日志）

**构建/配置修复**

- `build.gradle` 的 1.21.11 excludes：98 → **93 条**；删除 4 条指向已删文件的项、1 条重复项。
- 1.21.11 mixin 清洗：改用 `layout.buildDirectory`（不再硬编码 `build/1.21.11`），先清理数组尾逗号，再用 `JsonSlurper` 强校验；当前产物 7 个 mixin JSON **全部合法**。
- `ShaderCurvesMixinPlugin` 删除不存在的 `CelestialUniformsMixin` 条目。
- `.gitignore` 增加 `.mimocode/` 与 `*.log`。
- `fabric.mod.json` 的 main 入口从 2 个减为 1 个。

**文档**

- README / README_EN：修正"结构伪装由本模组新增"的过时描述（BBS 2.7 已内置，本模组补充 VFX/Xavin 破坏魔杖交互）；AAA 指令标注为实验性（服务端广播未实现）；新增 1.21.11 实验性构建说明。

**附加修复（实机 smoke 发现）**

- `BBSFSloveCMLClient.registerTranslations`：BBS 的 `RegisterL10nEvent` 早于本模组 client 入口触发，首次 reload 时自有 `assets` 包尚未注册，随后又因 `registeredL10n == l10n` 被短路，导致 `bbspp/strings` **实际从未加载**。现改为：资源包未就绪时延迟注册；资源包就绪后再次调用并**始终 reload**。smoke 日志已出现 `Loading language file "assets:bbspp/strings/{en_us,zh_cn}.json"`。

**验证结果**

| 项 | 结果 |
|---|---|
| `build -Pmc_version=1.20.1`（clean） | ✅ BUILD SUCCESSFUL |
| `build -Pmc_version=1.20.4`（clean） | ✅ BUILD SUCCESSFUL |
| `build -Pmc_version=1.21.11`（clean） | ✅ BUILD SUCCESSFUL |
| 三个 jar 内已删类/资源残留 | ✅ 0 |
| `fabric.mod.json` main/client 入口 | ✅ 正确（P2.1 起 1.20.x 与 1.21.11 共用 `BBSPlusPlusModClient`，不再替换） |
| 230 条 mixin 声明 ↔ 文件 | ✅ 全部可解析；无未声明 Mixin（仅剩 `VisibilityMixinConfigPlugin`，非 Mixin） |
| 1.21.11 产物 mixin JSON | ✅ 7/7 严格合法 |
| 源码中已删类型引用 | ✅ 0 |
| 1.20.1 dev client 启动 smoke | ✅ 进入主菜单；`BBS++ initialized`、剪辑/表单/关键帧工厂注册日志齐全；无 Mixin/注入异常，无 `bbsplusplus` ERROR |
| `bbspp/strings` 翻译加载 | ✅ `assets:bbspp/strings/en_us.json` 与 `zh_cn.json` 均出现在 `Loading language file` 日志中 |
| 新增 Loom 注入告警 | 无（仍为 P3 待修的 6 处已知死注入） |

> P1 以删除"零引用/未加载"内容为主，附带修复了实机 smoke 发现的 l10n 时序缺陷；核心玩法逻辑未改动。P2/P3 将有行为改动，届时继续跑实机 smoke。

### D.2 P2 去重（进行中）

#### D.2.1 客户端入口合并（完成）

- 删除 `client/BBSPlusPlusModClient12111.java`（259 行），1.20.x 与 1.21.11 共用 `BBSPlusPlusModClient`。
- `FilmAutoGameModeRestoreState` 的 tick/restore 改为一次性反射注册；1.21.11 上类不存在时只打一条 info，不再需要第二份入口类。
- 删除空调用 `BBSMod.events.register(this)`（该类无 `@Subscribe`）与指向不存在目录的 `InternalAssetsSourcePack("bbsplusplus", "assets/bbsplusplus", ...)` 死注册。
- `build.gradle` 删除 1.21.11 的 `fabric.mod.json` 入口替换逻辑与 `BBSPlusPlusModClient` 的 exclude。

**验证**：三套 clean 构建通过；1.20.1 与 1.21.11 dev client 均启动到主菜单；1.21.11 日志确认
`自动旁观模式在当前构建不可用 ... 跳过钩子`、AAA/物品喷射/流体渲染器按预期降级，无 `bbsplusplus` ERROR。

#### D.2.2 入口类改名（完成）

- `BBSFSloveCMLAddon` → `BBSPlusPlusAddon`
- `BBSFSloveCMLClientAddon` → `BBSPlusPlusClientAddon`
- `BBSFSloveCMLClient` → `BBSPlusPlusClient`
- 同步 `fabric.mod.json` 的 4 组 entrypoint 与 14 个源文件中的引用（含 Javadoc）；文件用 `git mv` 保留历史。

**验证**：三套 clean 构建通过；1.20.1 与 1.21.11 dev client 均启动到主菜单；BBS addon / client-addon 回调正常，
`addon translations registered and loaded` 与各表单/剪辑/关键帧注册日志齐全，无 entrypoint `ClassNotFoundException`。

### D.3 同名 Mixin 去碰撞（完成）

9 组同名类逐组代码比对后确认**不是重复实现**，而是同一目标类上的互补职责（例如 root 的自动保存 vs client 的纹理/PBR 按钮）。
直接合并会产生 600–700 行的 god mixin，违反 5.1「Mixin 薄层」原则，因此本轮采用「唯一命名 + 职责后缀」，保留两个类、消除误配风险。

| 原 root 类 | 新 root 类 | 职责 |
|---|---|---|
| `BBSRenderingMixin` | `BBSRenderingWorldFilmShaderMixin` | 世界播放时补光影曲线时间/天气/天空色 |
| `CubicCubeRendererMixin` | `CubicCubeRendererUvEmitMixin` | CPU 顶点路径的 UV 变换 |
| `ModelFormRendererMixin` | `ModelFormRendererEquipmentMixin` | 装备/手持物品关键帧变换 |
| `UIDataContextMenuMixin` | `UIDataContextMenuAutoSaveMixin` | 预设右键"自动保存" |
| `UIFilmPreviewMixin` | `UIFilmPreviewIrisButtonMixin` | 预览栏 Iris 按钮 |
| `UIPoseEditorMixin` | `UIPoseEditorAutoSaveMixin` | 姿势编辑器自动保存 |
| `UIReplaysEditorMixin` | `UIReplaysEditorTrackColorMixin` | 轨道颜色/图标 |
| `UIReplaysEditorUtilsMixin` | `UIReplaysEditorUtilsPoseBrushMixin` | 姿势参数刷 pickForm |
| `UISoundOverlayPanelMixin` | `UISoundOverlayPanelTreeMixin` | 音效树形列表 |
| `client/UISoundOverlayPanelMixin` | `client/UISoundOverlayPanelDownloadMixin` | 原版音效下载路径解析 |

同步修改：两个 mixin config 的条目；`build.gradle` 1.21.11 excludes 中两个改名文件
（`ModelFormRendererEquipmentMixin`、`CubicCubeRendererUvEmitMixin`）。

**验证**：
- `mixin/` 与 `mixin/client/` 下已无同名类。
- 230 条 mixin 声明全部可解析，无未声明 Mixin。
- 三套 clean 构建通过；1.20.1 与 1.21.11 dev client 均启动到主菜单。
- 两端日志均无 `Mixin apply failed` / `InjectionError` / `ClassNotFoundException`。

#### D.3.1 待办

- `FluidFormRenderer` 双版本抽公共基类（两版渲染 API 差异大，需与 P6 版本隔离一并设计）。
- 入口 5→4：把 `BBSPlusPlusClient` 的 l10n/源包职责并入 `BBSPlusPlusModClient`（需保持 l10n 事件时序）。

### D.4 设置与工具包合并（完成）

**设置持有类**

- `BBSAddonsSettings`（36 字段 + 枚举）、`CMLSettings`（18 字段 + 2 方法）、`BBSPPPSettings`（1 字段 + 1 方法）
  合并为 `gbeic.bbsplusplus.settings.ModSettings`。
- 逐项校验：迁移后 **55 个 `Value*` 字段、4 个工具方法、1 个枚举** 与原三个类完全一致，无缺漏/多余。
- 更新 64 个文件的引用与 import；删除三个旧类。
- 验证：三套 clean 构建通过；1.20.1 客户端启动到主菜单，`config/bbs/settings/bbspp.json` 正常生成并保留既有值，无 NPE/设置相关 ERROR。

**工具包**

- `gbeic.bbsplusplus.utils` 的 11 个类整体迁入 `gbeic.bbsplusplus.util`，删除空包。
- 同步 17 个文件的 import/包名；全库已无 `gbeic.bbsplusplus.utils` 引用。
- 验证：三套 clean 构建通过；1.20.1 客户端启动到主菜单，无 `ClassNotFoundException`/`NoClassDefFoundError`。

<!-- 后续阶段完成后在此继续追加 D.2.x / D.3 … -->

---

> **执行提醒**：本文件只是计划。真正动手前请先确认第 10 节的 6 个决策；每阶段结束按第 8 节验证并单独提交，确保任何一步都可回滚。若需要，我可以基于本计划先产出 P1 的逐文件 diff 清单（仍不改代码）。
