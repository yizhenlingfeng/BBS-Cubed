# BBS Cubed 多版本构建兼容性报告

- 项目：BBS Cubed（`gbeic.bbsplusplus`，Fabric 客户端模组）
- 原构建目标：仅 Minecraft 1.20.1
- 本次目标：Java 17~21；Minecraft 1.20.1~1.20.4；新增 1.20.4 构建
- 完成日期：2026-09-03

---

## 1. 需求与结论摘要

| 需求 | 结果 |
| --- | --- |
| Java 版本控制在 17~21 | ✅ 字节码目标 Java 17（major 61），可在 Java 17/21 上运行；Gradle 运行于 JDK 21 |
| 游戏版本控制在 1.20.1~1.20.4 | ✅ `fabric.mod.json` 声明 `minecraft: ">=1.20.1 <=1.20.4"`；已通过 1.20.1、1.20.4 双版本编译与产物验证 |
| 测试兼容性 | ✅ 双版本 `clean build` 均 BUILD SUCCESSFUL，产物与声明已逐项核验（见 §3） |
| 添加 1.20.4 构建 | ✅ `-Pmc_version=1.20.4` 一键切换；另提供 `build1204` 便捷任务 |

---

## 2. 变更清单

### 2.1 `gradle.properties`（双版本坐标表 + 兼容范围声明）

- 新增 `mc_version=1.20.1` 默认目标属性，可用 `-Pmc_version=1.20.4` 覆盖
- 新增兼容范围：`minecraft_version_range=>=1.20.1 <=1.20.4`、`java_version_range=>=17 <22`
  - **为什么是 `>=17 <22` 而不是 `[17,21]`**：① Fabric Loader 按 SemVer 比较，Java 21 实际版本号为 `21.0.x`（大于 `21.0.0`），闭区间 `[17,21]` 上界 `21` 被解析为 `21.0.0`，导致 Java 21.0.x 被误判超范围；② Fabric Loader 对 `[a,b)` OSGi 区间语法存在解析兼容性问题（实测 `[17,22)` 仍报 Java 21 不兼容），故采用生态最通用的谓词组合 `>=17 <22`（空格分隔表示"与"），可稳定覆盖所有 Java 17.x 与 21.x，语义仍为"Java 17 到 21"。
- 为 1.20.1 / 1.20.4 各列一套坐标（`*_1.20.1` / `*_1.20.4` 后缀）

| 依赖 | 1.20.1 | 1.20.4 |
| --- | --- | --- |
| yarn | 1.20.1+build.10 | 1.20.4+build.3 |
| fabric-api | 0.91.0+1.20.1 | 0.96.4+1.20.4 |
| iris | 1.7.0+1.20.1 | 1.7.2+1.20.4 |
| aaa_particles | fabric-1.20.1-2.2.0（modImplementation） | fabric-1.20.1-2.2.0（仅 compileOnly，见 §4.3） |
| forgeconfigapiport | 8.0.0 | 20.4.3（1.20.4 正确版本，`v20.4.3-1.20.4-Fabric` 各仓库均解析失败） |
| architectury | 9.2.14 | 11.1.17 |
| BBSFS（本地） | run/1.20.1/mods/bbs-2.5.1-1.20.1-FS-zh_CN.jar | run/1.20.4/mods/bbs-2.5.1-1.20.4.jar（已下载） |

### 2.2 `build.gradle`（按 `-Pmc_version` 切换的版本表）

- `versions` map + `gprop()` 读取器（属性名含小数点必须用 `providers.gradleProperty`）
- 产物命名 `BBS-Cubed-3.1+<mc>.jar`（`version = "${mod_version}+${minecraftVersion}"`）
- aaa_particles 在 1.20.4 下改 `modCompileOnly`，1.20.1 保持 `modImplementation`
- 新增版本专属源码目录 `src/versions/<mc>/java`（见 §2.4）
- 末尾注册 `build1201` / `build1204` / `runClient1201` / `runClient1204` 四个 `GradleBuild` 便捷任务（经 `startParameter.projectProperties` 传参，不受 shell 参数截断影响）
- `loom.runs.client.runDir = "run/${minecraftVersion}"`：按版本隔离运行目录（`run/1.20.1/` 与 `run/1.20.4/`），避免 mods、saves、config 互相冲突

### 2.3 `src/main/java` 共享代码（跨版本 API 兼容修复，见 §4.2）

- `client/structure/StructureStickSaveNameScreen.java`：`renderBackground` 双版本兼容
- `structure/StructureSaver.java`、`client/renderer/StructureFormRenderer.java`：NBT 读写改走版本兼容层

### 2.4 `src/versions/<mc>/java`（版本隔离源码，新增）

- `gbeic/bbsplusplus/util/NbtCompat.java`（1.20.1 与 1.20.4 各一份），把 `NbtIo` 读写差异封装成统一 API

---

## 3. 兼容性测试结果

| 项目 | 1.20.1 | 1.20.4 |
| --- | --- | --- |
| 构建命令 | `.\gradlew.bat clean build` | `.\gradlew.bat clean build "-Pmc_version=1.20.4"`（PowerShell 必须加引号） |
| 结果 | BUILD SUCCESSFUL | BUILD SUCCESSFUL |
| 产物 | `build/libs/BBS-Cubed-3.1+1.20.1.jar`（1,638,833 B） | `build/libs/BBS-Cubed-3.1+1.20.4.jar`（1,638,942 B） |
| 字节码版本 | major 61（Java 17） | major 61（Java 17） |
| fabric.mod.json `minecraft` | `>=1.20.1 <=1.20.4` | `>=1.20.1 <=1.20.4` |
| fabric.mod.json `java` | `>=17 <22` | `>=17 <22` |
| fabric.mod.json `fabricloader` | `>=0.15.0` | `>=0.15.0` |
| remap 警告 | 仅 `Cannot remap renderModel ... [BillboardFormRenderer]`（既有，BBSFS 相关） | 与 1.20.1 相同的唯一一条警告（初次构建曾出现的若干 `Cannot remap` 在 clean 构建中未复现） |

`build1204` 便捷任务实测通过（嵌套构建以 1.20.4 配置完成并产出 jar）。`runClient1201` / `runClient1204` 任务已注册并通过配置阶段验证（`gradlew tasks --group=bbsplusplus` 列出全部四个任务）；运行目录 `run/1.20.1/`（含原 saves、config、mods）与 `run/1.20.4/mods/bbs-2.5.1-1.20.4.jar` 已就位。

---

## 4. 跨版本 API 差异与修复

### 4.1 差异点（1.20.2 引入的破坏性改动）

1. **`NbtIo` 压缩读写 API 变更**（1.20.2 起）：
   - 1.20.1：`readCompressed(File)` / `readCompressed(InputStream)` / `writeCompressed(NbtCompound, File)` / `writeCompressed(NbtCompound, OutputStream)`，无 NbtSizeTracker 重载
   - 1.20.4：`readCompressed(Path, NbtSizeTracker)` / `readCompressed(InputStream, NbtSizeTracker)` / `writeCompressed(NbtCompound, Path)` / `writeCompressed(NbtCompound, OutputStream)`，无 File / 无单参重载；`NbtTagSizeTracker` 也更名为 `NbtSizeTracker`
   - **两版本无任何公共读取重载** → 无法用单次调用兼容
2. **`Screen.renderBackground` 签名变化**（1.20.2 起）：
   - 1.20.1：`renderBackground(DrawContext)`；1.20.4：`renderBackground(DrawContext, int, int, float)`
   - 1.20.4 将"游戏内暗化背景"抽为 `renderInGameBackground(DrawContext)`（1.20.1 无此方法）

### 4.2 修复方式

| 位置 | 原代码（1.20.1 写法） | 修复 |
| --- | --- | --- |
| StructureSaver.java L91 | `NbtIo.writeCompressed(nbt, file)` | `NbtCompat.writeCompressed(nbt, file)` |
| StructureFormRenderer.java L217 | `NbtIo.readCompressed(nbtFile)` | `NbtCompat.readCompressed(nbtFile)` |
| StructureFormRenderer.java L224 | `NbtIo.readCompressed(stream)` | `NbtCompat.readCompressed(stream)` |
| StructureStickSaveNameScreen.java L91 | `this.renderBackground(context)` | 按 `client.world != null` 分支：`context.fillGradient(0,0,width,height, -1072689136, -804253680)` 或 `renderBackgroundTexture(context)` |

- **NBT 读写**：新增版本隔离的 `NbtCompat`（`src/versions/<mc>/java`）。1.20.1 实现走 File/InputStream 重载；1.20.4 实现走 `Path`/`InputStream + NbtSizeTracker.ofUnlimitedBytes()`。共享源码统一调用 `NbtCompat`，编译期与运行时均正确解析。
- **renderBackground**：经 `javap` 反编译 1.20.1/1.20.4 两个版本的 `Screen` 确认，两版本"游戏内背景"行为完全一致（同一组 `fillGradient` 渐变色 `-1072689136`/`-804253680`）。因此用两版本通用的 `fillGradient` 直接复刻原版行为，菜单场景回退到两版本都存在的 `renderBackgroundTexture(DrawContext)`。**不使用反射**（vanilla 方法运行时为 intermediary 混淆名，字符串反射不可靠）。

### 4.3 已知限制与运行时注意事项（构建之外）

1. **aaa_particles 无 1.20.4 版本**：1.20.4 构建沿用 1.20.1 的 jar 作编译期依赖；运行时 `isModLoaded("aaa_particles")` 会自动禁用相关功能。若在 1.20.4 下需要该功能，需等待其发布 1.20.4 版本。
2. **BBSFS 运行时依赖**：模组大量 Mixin 直接注入 BBSFS 类（`EffekAssetLoaderMixin`、`BBSModMixin` 等）。1.20.4 运行时需用户自行安装 **BBSFS 1.20.4**（`run/1.20.4/mods/bbs-2.5.1-1.20.4.jar` 已放入 dev 运行目录）及 1.20.4 版外围 mod（sodium、iris 等）。两个版本的运行目录已隔离（`run/1.20.1/` 与 `run/1.20.4/`），不会互相污染。
3. **Mixin 目标差异风险**：初次 1.20.4 构建时曾出现若干 `Cannot remap`（builder、renderBlockEntities、renderGlobalBlockEntities、getRendertypeTranslucentNoCrumblingShader、draw、renderOffsetShadow、sodium$moveToNextVertex、sodium$setActive 等）；clean 重建后未复现，但上述方法在 1.20.4 vanilla/BBSFS 中可能存在真实差异。**建议在真实 1.20.4 客户端中验证相关 Mixin 路径的运行时行为**（本环境无法启动游戏运行时验证）。
4. **remap 警告（两版本共有）**：`Cannot remap renderModel ... [BillboardFormRenderer]`，与 1.20.1 基线一致，非本次引入。

---

## 5. 构建与运行方法

### 5.1 构建

```powershell
# 环境：JDK 21（Gradle 9.7.1 需 21 运行；产物字节码为 Java 17）
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"

# 构建 1.20.1（默认）
cd "D:\Desktop\Mod\源码\BBS-PlusPlus-main"
.\gradlew.bat clean build --console=plain

# 构建 1.20.4（PowerShell 中 -P 参数必须加引号，否则被拆断成 "-Pmc_version=1" + ".20.4"）
.\gradlew.bat clean build "-Pmc_version=1.20.4" --console=plain

# 便捷任务（等价，不受引号问题影响）
.\gradlew.bat build1201
.\gradlew.bat build1204
```

产物输出到 `build/libs/`：
- `BBS-Cubed-3.1+1.20.1.jar`
- `BBS-Cubed-3.1+1.20.4.jar`

### 5.2 运行开发客户端

两个版本的运行目录已完全隔离，避免 mods、saves、config 互相冲突：

| 版本 | 运行目录 | 便捷任务 |
| --- | --- | --- |
| 1.20.1 | `run/1.20.1/` | `.\gradlew.bat runClient1201` |
| 1.20.4 | `run/1.20.4/` | `.\gradlew.bat runClient1204` |

```powershell
# 启动 1.20.1 开发客户端（使用 run/1.20.1/ 下的 mods、saves、config）
.\gradlew.bat runClient1201

# 启动 1.20.4 开发客户端（使用 run/1.20.4/ 下的 mods、saves、config）
.\gradlew.bat runClient1204
```

也可直接用 `-P` 切换：`.\gradlew.bat runClient "-Pmc_version=1.20.4"`（PowerShell 需加引号）。

**1.20.4 运行目录说明**：`run/1.20.4/mods/` 已预置 `bbs-2.5.1-1.20.4.jar`。sodium、iris、modmenu 等外围 mod 需用户自行下载 1.20.4 对应版本放入该目录；fabric-api、fabric-loader 由 loom 自动注入运行时 classpath，无需手动放置。

---

## 6. 验证方式与覆盖范围

- 双版本 `clean build` 全量通过（编译、资源、remapJar、assemble）。
- 产物逐一核验：字节码 major=61（Java 17）；`fabric.mod.json` 内 `minecraft: ">=1.20.1 <=1.20.4"`、`java: ">=17 <22"`、`fabricloader: ">=0.15.0"` 展开正确。
- 跨版本 API 差异（NbtIo、Screen.renderBackground）经 `javap` 反编译两版本目标 jar 逐一比对后按双兼容方案修复。
- 未覆盖：真实 1.20.4 客户端运行时验证（环境无游戏运行时）——见 §4.3 已知限制。
