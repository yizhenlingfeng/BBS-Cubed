# Better Camera Perspective v1.3.2 → BBS Cubed 合并计划

> 源包：
>
> `libs/better-camera-perspective-v1.3.2-sources.jar`
>
> （已解压到 
>
> `libs/better-camera-perspective-v1.3.2/`
>
> ）
> 目标：作为内置功能并入主工程 
>
> `gbeic.bbsplusplus`
>
> （mod id 
>
> `bbsplusplus`
>
> ，BBS 2.7 / MC 1.20.1）。
> 整理日期：2026-10-08



***

## 一、源包源码清单与职责

包根 `perspective.better_camera_perspective`，共 24 个 Java 文件 + 2 个 mixins.json + 2 个语言文件。

### 1. 入口层（4 个）



| 文件                                     | 职责                                                                                                                        | 合并处置                                                                              |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `BetterCameraPerspective`              | `ModInitializer`，空实现                                                                                                      | **丢弃**（无逻辑）                                                                       |
| `client/BetterCameraPerspectiveClient` | `ClientModInitializer`，空实现                                                                                                | **丢弃**（无逻辑）                                                                       |
| `BBSAddonEntry`                        | `bbs-addon` 入口：在 `RegisterSettingsEvent` 把 `multi_tracker` 剪辑注册进 `BBSMod.getFactoryCameraClips()`                         | 逻辑并入 `BBSFSloveCMLAddon.registerCameraClips()`                                    |
| `client/BBSClientAddonEntry`           | `bbs-client-addon` 入口：①注册客户端剪辑类 `MultiTrackerClientClip`；②`event.register(... UIMultiTrackerClip::new)`；③注册冲突设置模块；④注册语言文件 | ①②并入 `BBSFSloveCMLClient.onInitializeClient()`；③并入既有设置体系；④语言键直接并入 `bbspp/strings` |

### 2. 剪辑核心（2 个，服务端 / 客户端成对）



| 文件                                    | 职责                                                                                                                                                                                       |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `clips/MultiTrackerClip`              | 服务端剪辑：继承 `KeyframeClip`（自带 X/Y/Z/yaw/pitch/roll/fov/distance 八通道 + additive），叠加 tracker 参数 `selector/offset/angle/group/lookAt/relative/active(位掩码0-63)/anchor(KeyframeChannel<Anchor>)` |
| `client/clips/MultiTrackerClientClip` | 客户端求值：先追踪（按 active 位掩码覆盖对应轴，支持 anchor 轴跨演员过渡混合 lerp），再 `super.applyClip` 关键帧插值；相对值语义下 XYZ = 锚点 + 关键帧偏移                                                                                   |

### 3. 剪辑 UI（4 个）



| 文件                               | 职责                                                                                                    |
| -------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `client/ui/UIMultiTrackerClip`   | 剪辑面板：组合关键帧编辑器 + 追踪器控件；`editClip` 先反解 offset/angle 再写关键帧；`fillData` 补挂 anchor sheet、给 XYZ sheet 挂 seed |
| `client/ui/UIXyzKeyframeFactory` | X/Y/Z 关键帧双值面板（相对值 / 绝对值联动，主继承模式按钮）                                                                    |
| `client/ui/BcpKeyframeFactories` | BCP 专用 x/y/z 关键帧工厂注册表（避免污染 BBS 全局 PROPERTIES）                                                         |

### 4. 显示名修复（1 个）



| 文件                        | 职责                                                                                                      |
| ------------------------- | ------------------------------------------------------------------------------------------------------- |
| `client/FilmDisplayNames` | 弱引用持有已打开的 `UIFilmPanel`，按 Form 实例反查 Replay 的 `label`/`form.name`，供 mixin 替换 "只显示稳定 ID /bbs:model" 的 bug |

### 5. 剪辑菜单可见性 + 自适应菜单（5 个）



| 文件                                    | 职责                                                                                   |
| ------------------------------------- | ------------------------------------------------------------------------------------ |
| `client/BcpVisibility`                | 剪辑类型可见性筛选：runtime/saved 双层 map，持久化到 `camera_perspective_clips.json` 的 `visibility` 段 |
| `client/ui/BcpAdaptiveClipMenu`       | 自适应多列右键菜单（每列≤10，最多 5 列，跟随鼠标）                                                         |
| `client/ui/GridActionCell`            | 自适应菜单单元格                                                                             |
| `client/ui/BcpVisibilityOverlayPanel` | 「可视性设置」面板（多列网格开关、列数 1/2/3 循环）                                                        |
| `mixin/client/UIClipsAddClipMixin`    | 接管 `UIClips.showAddClips`：恒定 1 号位插入「可视性设置」、按 `BcpVisibility` 过滤剪辑、换用多列菜单             |

### 6. 剪辑处理开关（1 个）



| 文件                         | 职责                                                                                          |
| -------------------------- | ------------------------------------------------------------------------------------------- |
| `client/BcpClipProcessing` | 4 个布尔开关：`reorganize_animation`（整理剪辑平滑过渡，默认关）、`clip_names`、`clip_borders`、`clip_colors`（默认开） |

### 7. 冲突管理（4 个，**合并时需重点重构**）



| 文件                                  | 职责                                                                                                                 |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `client/BcpConflictManager`         | 检测 "同一功能由多个 mod 提供"，未解决则封锁本插件功能并弹窗；持久化 `conflicts.<feature>.chosen`；当前只登记 `clips_visibility`（bcp vs `bbsplusplus`） |
| `client/BcpConflictSettingsModule`  | 注册设置模块 `conflict_features`（图标 `Icons.FRUSTUM`，配置文件 `conflict_features.json`）                                       |
| `client/ui/BcpConflictOverlayPanel` | 进入编辑器时弹的「冲突功能」选择窗口                                                                                                 |
| `client/ui/BcpConflictChoiceRow`    | 设置页里单个冲突功能的选择行（功能名 + 来源 mod 按钮组）                                                                                   |

### 8. Mixin（9 个，全部 client，包 `perspective.better_camera_perspective.mixin.client`）



| Mixin                          | 目标类                            | 注入点                                                                           | 与主项目现有 mixin 关系                                                                                           |
| ------------------------------ | ------------------------------ | ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `FormDisplayNameMixin`         | `forms.forms.Form`             | `getDisplayName`/`getFormIdOrName` HEAD cancellable                           | 无同类，**新增**                                                                                                |
| `UIFilmPanelMixin`             | `ui.film.UIFilmPanel`          | `<init>` TAIL（登记 FilmDisplayNames）；`fillData` HEAD（进编辑器恢复全开 + 冲突检测）           | 主项目有 `UIFilmPanelMiniWindowMixin`/`UIFilmPanelScrollMixin` 等不同方法，**新增独立类**即可                              |
| `UIAnchorKeyframeFactoryMixin` | `UIAnchorKeyframeFactory`      | `displayActors` HEAD cancellable（按类别分组的演员选择列表 + 可辨识名）                         | 主项目有 `UIAnchorKeyframePropTransformResetMixin`，不同方法，**新增独立类**                                             |
| `UIKeyframeDopeSheetMixin`     | `UIKeyframeDopeSheet`          | `addKeyframeAt` HEAD cancellable（XYZ 主继承值注入）                                  | 主项目有 `UIKeyframeDopeSheetTrackHeightScrollMixin`，不同方法，**新增独立类**                                           |
| `UIClipRendererMixin`          | `UIClipRenderer`               | `renderClip` 的 `outline`/`textShadow`、`renderBackground` 的 `box` 三个 @Redirect | 无同类，**新增**                                                                                                |
| `UIClipsAddClipMixin`          | `ui.film.UIClips`              | `showAddClips` HEAD cancellable（接管整个右键添加菜单）                                   | 主项目 `UIClipsMixin` 未碰 `showAddClips`，可**新增独立类**或并入主 `UIClipsMixin`                                        |
| `UIClipsMixin`（BCP）            | `ui.film.UIClips`              | `renderCameraWork` HEAD（层位快照检测）+ `getClipArea` RETURN（整理动画插值）                 | ⚠️ **主项目已有同名&#x20;**`gbeic.bbsplusplus.mixin.UIClipsMixin`（标尺 / 拖拽 / 粘贴 / 碰撞），需把这两个注入**并入现有类**，不能新建同名类    |
| `UIKeyframeFactoryMixin`（BCP）  | `UIKeyframeFactory`            | `getPropertyFactory` HEAD cancellable                                         | ⚠️ **主项目已有同名类**，但主项目注入的是 `createPanel`（UV 面板）。两者注入不同方法，**可在同一类里追加一个静态方法**，或新建 `BcpUIKeyframeFactoryMixin` |
| `UISettingsLayoutMixin`（BCP）   | `settings.ui.UISettingsLayout` | `build` TAIL（注册 BcpConflictChoiceRow）                                         | ⚠️ **主项目已有同名类**在 `build` TAIL 注册 Blockbench 路径行。**合并进现有类**，追加注册行                                          |

### 9. 资源



* `assets/better_camera_perspective/strings/{en_us,zh_cn}.json`：34 个语言键


  * `bbs.ui.camera.clips.bbs:multi_tracker`（剪辑显示名）

  * `bbs.ui.camera.panels.xyz.*`（相对 / 绝对值 / 主继承）

  * `bbs.ui.camera.panels.axis_anchor`、`anchor_uncategorized`、`back`

  * `bbs.ui.camera.visibility_settings`、`bbs.ui.camera.panel.columns*`

  * `bbs.ui.camera.conflict.*`（冲突窗口文案）

  * `conflict_features.config.*`（设置页标题 / 开关名）

* `better_camera_perspective.mixins.json`：服务端 mixin 列表**为空**，合并后废弃

* `better_camera_perspective.client.mixins.json`：上述 9 个 client mixin



***

## 二、合并目标落点（建议包结构）

主包根 `gbeic.bbsplusplus`。建议在 `camera/` 子包下集中放置，与既有 `clips/`、`client/ui/film/` 并列：



```
gbeic/bbsplusplus/
├── clips/
│   └── camera/
│       └── MultiTrackerClip.java                 ← 原 clips/MultiTrackerClip
├── client/
│   └── clips/
│       └── MultiTrackerClientClip.java           ← 原 client/clips/MultiTrackerClientClip
│   └── ui/film/clips/
│       ├── UIMultiTrackerClip.java               ← 原 client/ui/UIMultiTrackerClip
│       └── ...（既有 UICinematicClip 等已在此）
│   └── ui/keyframes/
│       ├── UIXyzKeyframeFactory.java             ← 原 client/ui/UIXyzKeyframeFactory
│       └── BcpKeyframeFactories.java             ← 原 client/ui/BcpKeyframeFactories
├── camera/
│   ├── FilmDisplayNames.java                     ← 原 client/FilmDisplayNames
│   ├── BcpClipProcessing.java                    ← 原 client/BcpClipProcessing
│   ├── BcpVisibility.java                        ← 原 client/BcpVisibility
│   └── conflict/                                 ← 冲突管理（见第三节决策）
│       ├── BcpConflictManager.java
│       ├── BcpConflictSettingsModule.java
│       ├── BcpAdaptiveClipMenu.java
│       ├── GridActionCell.java
│       ├── BcpVisibilityOverlayPanel.java
│       ├── BcpConflictOverlayPanel.java
│       └── BcpConflictChoiceRow.java
└── mixin/
    └── client/                                   ← 9 个 mixin 按目标类放入，命名加 Bcp 前缀避免撞名
        ├── BcpFormDisplayNameMixin.java
        ├── BcpUIFilmPanelMixin.java
        ├── BcpUIAnchorKeyframeFactoryMixin.java
        ├── BcpUIKeyframeDopeSheetMixin.java
        ├── BcpUIClipRendererMixin.java
        ├── BcpUIClipsAddClipMixin.java
        └── ...（UIClips / UIKeyframeFactory / UISettingsLayout 三个注入并入既有同名类，不新建文件）
```

> 包名不是硬约束；也可整体放到 
>
> `gbeic.bbsplusplus.camera.bcp.*`
>
>  保持与原包一一对应、机械改包名。两种都行，关键是 
>
> **9 个 mixin 类名必须改名**
>
> （
>
> `Bcp`
>
>  前缀），避免与主项目 
>
> `mixin/`
>
>  根包下已有的 
>
> `UIClipsMixin`
>
> /
>
> `UIKeyframeFactoryMixin`
>
> /
>
> `UISettingsLayoutMixin`
>
>  同名冲突。



***

## 三、关键决策点（动手前需确认）

### 决策 1：冲突管理模块如何处理（最重要）

BCP 作为独立 mod 时，`BcpConflictManager` 的逻辑是 " 本插件 vs `bbsplusplus` 二选一 "：



* `sources` 恒含 `better_camera_perspective`，若 `FabricLoader.isModLoaded("bbsplusplus")` 则再加一个。

* 合并进 Cubed 后，**本工程自己就是&#x20;**`bbsplusplus`，这个判断恒真 → 两个来源永远同时存在 → 用户每次进编辑器都会被要求 "二选一"，且选哪一边都是同一套代码，自相矛盾。

**推荐方案 A（内置版直接生效，仅对 "外部独立 BCP mod" 做让位检测）：**



* 删除 `BcpConflictManager` / `BcpConflictOverlayPanel` / `BcpConflictChoiceRow` / `BcpConflictSettingsModule` 这一整套二选一 UI。

* 改为：若运行时检测到外部独立 mod `better_camera_perspective` 已加载（`FabricLoader.isModLoaded("better_camera_perspective")`），则内置版的 "剪辑菜单可见性 / 自适应菜单 / 剪辑处理开关" 全部禁用，把功能让给外部 mod（避免双方 mixin 同时接管 `showAddClips` 双重菜单）。

* 未检测到外部 BCP 时，内置版直接生效，不再弹任何选择窗。

* `camera_perspective_clips.json` 的 `visibility` 段保留读写（用户筛选持久化），`conflicts` 段可废弃。

**方案 B（保留二选一 UI）：** 仅当你计划让 "内置版 vs 外部独立 BCP mod" 并存可选时才保留。但这样 `sources` 里要把 `bbsplusplus` 改成 "内置版"、`better_camera_perspective` 改成 "外部独立版"，并把 `BcpVisibility.isBlocked()` 的语义反过来。工作量更大且用户价值低。

> 默认按 
>
> **方案 A**
>
>  实施。

### 决策 2：剪辑 UI 注册方式

主项目惯例（`BBSFSloveCMLClient.onInitializeClient`）：



```
UIClip.register(HotbarClip.class, UIHotbarClip::new);
```

BCP 用的是 `RegisterClipPanelsEvent.event.register(...)` + 在工厂里重复 `factory.register(MultiTrackerClientClip.class, ...)`。

**推荐：跟主项目惯例走**—— 服务端类注册进 `BBSFSloveCMLAddon.registerCameraClips()`，客户端 UI 用 `UIClip.register(MultiTrackerClientClip.class, UIMultiTrackerClip::new)` 注册进 `BBSFSloveCMLClient.onInitializeClient()`。客户端剪辑类是否需要在工厂里再 register 一次，需对照 BBS 2.7 `UIClip.register` 是否已自动覆盖客户端求值类（主项目 HotbarClip 等就是这么跑的，应一致）。

### 决策 3：设置页归属

BCP 原本用 `BBSMod.setupConfig(Icons.FRUSTUM, "conflict_features", ...)` 单独建了一个设置模块。

合并后按方案 A，"冲突功能" 分类消失，只剩 "剪辑处理设置" 分类（4 个开关）。

**推荐**：把这 4 个开关并入主项目既有的 `BBSAddonsSettings`（找 film/camera 相关分区追加），不再单独建 `conflict_features.json` 设置文件；`BcpClipProcessing.isEnabled(key)` 改为读 `BBSAddonsSettings` 对应字段。这样设置页只有一处。

### 决策 4：语言键归属

直接把 34 个键追加进 `src/main/resources/assets/bbspp/strings/zh_cn.json` 和 `en_us.json`（已确认当前无重复键）。删除独立的 `assets/better_camera_perspective/strings/`。`BBSFSloveCMLClient.registerTranslations` 无需改动（它本就读 `bbspp/strings/`）。

> 注意：BCP 键名前缀是 
>
> `bbs.ui.camera.*`
>
> （BBS 命名空间）和 
>
> `conflict_features.config.*`
>
> 。合并后建议把 
>
> `conflict_features.config.*`
>
>  重命名为 
>
> `bbspp.ui.camera.*`
>
>  或并入主项目既有 key 规范，避免与未来 BBS 官方键冲突；
>
> `bbs.ui.camera.*`
>
>  段保留（本就是挂在 BBS 相机 UI 下的扩展键）。



***

## 四、执行步骤（按顺序）



1. **解压归档**：源码已在 `libs/better-camera-perspective-v1.3.2/`，作为参考底本保留，不进 `src/`。

2. **建包落位**：按第二节包结构创建目录。

3. **搬核心剪辑**：`MultiTrackerClip` → `clips/camera/`；`MultiTrackerClientClip` → `client/clips/`。改 package、删 import 里的 `perspective.better_camera_perspective.*`。

4. **搬 UI**：`UIMultiTrackerClip`、`UIXyzKeyframeFactory`、`BcpKeyframeFactories`、`FilmDisplayNames`。

5. **搬可见性 / 菜单 / 剪辑处理**：`BcpVisibility`、`BcpAdaptiveClipMenu`、`GridActionCell`、`BcpVisibilityOverlayPanel`、`BcpClipProcessing`。

6. **重构冲突模块**（决策 1，方案 A）：删除 `BcpConflictManager`/`BcpConflictOverlayPanel`/`BcpConflictChoiceRow`/`BcpConflictSettingsModule`；把 `BcpVisibility`/`BcpClipProcessing` 里对 `BcpConflictManager.isBlocked(...)` 的调用改为 "检测外部 better\_camera\_perspective mod 是否加载" 的静态开关。

7. **改注册入口**：

* `BBSFSloveCMLAddon.registerCameraClips()` 追加 `factory.register(Link.bbs("multi_tracker"), MultiTrackerClip.class, new ClipFactoryData(Icons.USER, 0xFFB74D));`

* `BBSFSloveCMLClient.onInitializeClient()` 追加 `UIClip.register(MultiTrackerClientClip.class, UIMultiTrackerClip::new);`

* `BcpKeyframeFactories.register()` 在 `UIMultiTrackerClip.registerUI()` 里已有调用，保留。

1. **搬并改 9 个 mixin**：

* 新建 6 个独立 mixin（加 `Bcp` 前缀）：FormDisplayName / UIFilmPanel / UIAnchorKeyframeFactory / UIKeyframeDopeSheet / UIClipRenderer / UIClipsAddClip。

* **合并进既有类**：BCP 的 `UIClipsMixin` 两个注入方法 → 追加到 `gbeic.bbsplusplus.mixin.UIClipsMixin`；BCP 的 `UISettingsLayoutMixin` 注册行 → 追加到既有 `UISettingsLayoutMixin`（按方案 A 不再注册冲突行，可能此步省略）；BCP 的 `UIKeyframeFactoryMixin.getPropertyFactory` → 追加到既有 `UIKeyframeFactoryMixin`（或新建 `BcpUIKeyframeFactoryMixin`）。

* 全部 import 从 `perspective.better_camera_perspective.*` 改为新包。

1. **登记 mixin**：把新建的 6 个 mixin 类名追加进 `src/main/resources/bbsplusplus.mixins.json` 的 `client` 数组（包已是 `gbeic.bbsplusplus.mixin`，放 `client/` 子包则写 `client.BcpXxxMixin`）。

2. **合并语言**：34 个键追加进 `assets/bbspp/strings/{zh_cn,en_us}.json`。

3. **构建验证**：`./gradlew build1201`（或 `build1204`），修编译错误；`runClient1201` 进摄像机编辑器验证：①「多样追踪」剪辑可添加 / 打关键帧；②右键多列菜单 + 可视性设置；③整理剪辑动画开关；④剪辑名 / 边框 / 配色开关；⑤演员锚点列表显示名；⑥无外部 BCP mod 时不弹任何冲突窗。

4. **回归检查**：主项目既有剪辑（hotbar/cinematic/replay）、Blockbench 设置行、UV 关键帧面板、F5 影片可见性菜单均不受影响。



***

## 五、风险点



* `UIClipsMixin`**&#x20;合并**：主项目该类已 947 行，含 `@Overwrite resolveCollisions` 和大量 `@Unique` 助手。追加两个注入方法时注意 `@Shadow` 字段不要重复声明（`clips`、`grabbing` 已 shadow），BCP 的 `bcp$prevLayers`/`bcp$moving`/`bcp$animStart` 用 `@Unique` 前缀。

* `UIKeyframeFactoryMixin`**&#x20;双重 HEAD cancellable**：主项目注入 `createPanel`、BCP 注入 `getPropertyFactory`，是两个不同方法，互不干扰；但若合到一个类，注意 BCP mixin 里 `@Shadow private static Map PROPERTIES` 的字段描述符要与目标类一致。

* `BcpVisibility`**&#x20;配置兼容**：独立 BCP 用户的 `config/camera_perspective_clips.json` 里 `visibility` 段合并后应继续可读（字段名不变），`conflicts` 段忽略即可。

* **anchor 关键帧通道**：`UIKeyframeEditor.setClip` 只遍历 8 个数值通道，`UIMultiTrackerClip.fillData` 手动 `appendSheet("anchor", ...)`—— 这是既有 hack，照搬。

* **BBS 版本**：源包 `depends bbs >=2.7-1.20.1`，主项目编译用 `libs/archive/bbs-2.7/bbs-2.7-1.20.1-zh_CN.jar`，匹配。