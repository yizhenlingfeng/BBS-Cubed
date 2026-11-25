# BBS Cubed 3.6.9 构建与适配状态

| Minecraft | 构建与启动 | 当前范围 |
| --- | --- | --- |
| 1.20.1 | `./gradlew.bat build1201`、`./gradlew.bat runClient1201` | 主发布目标。发布 JAR 声明兼容 1.20.1–1.20.4，包含 [Model Morph](MODEL_MORPH.md)。安装匹配版本的 AAA Particles 后启用 AAA 功能。 |
| 1.20.4 | `./gradlew.bat compileJava -Pmc_version=1.20.4`、`./gradlew.bat runClient1204` | 1.20.x 编译与启动回归目标。AAA Particles 仅作编译依赖。 |
| 1.21.11 | `./gradlew.bat build12111`、`./gradlew.bat runClient12111` | 实验性目标。运行共享测试，Model Morph 与 AAA 粒子渲染器尚未移植。 |

Gradle 守护进程使用 JDK 21。1.20.x 编译 Java 17 字节码，1.21.11 使用 Java 21。构建按版本使用独立的 `build/<版本>/`，开发客户端默认使用 `run/<版本>/`。运行前将对应的 BBSFS 2.7 JAR 放入该版本的 `mods/`。

## 1.21.11 适配

- 影片、时间轴和表单扩展使用共享实现。流体、物品喷射、粒子外观、模型调色、光效与描边使用相应的 1.21.11 渲染实现。
- `src/main/java-1.21.11` 保存专属实现。仍依赖旧渲染 API 的共享类由 [build.gradle](../build.gradle) 排除，相关 Mixin 配置在资源任务中同步过滤。
- `bbsplusplus-12111.mixins.json` 只进入 1.21.11 产物。客户端启动自检按实际配置检查类资源，着色器注入和功能行为另有运行时检查。
- 用于编译的 VFX 和 VFX Lights JAR 是 1.20.1 版本。第三方兼容性以对应版本实际启动和游戏内验证为准。

各版本的验证结果和待复验项目见[项目整理计划](项目整理与代码结构优化计划-3.6.9.md)。3.6.8 的两份 1.21.11 适配与施工文档属于历史记录，可在 Git 历史中查阅。

## 本地依赖

| 路径 | 用途 |
| --- | --- |
| `libs/archive/bbs-2.7/` | 1.20.1 与 1.20.4 的 BBS 编译和测试依赖。目录名是 archive，3.6.9 仍通过 `bbs_jar_*` 引用。 |
| `libs/bbs-2.7-1.21.11.jar` | 1.21.11 的 BBS 编译和测试依赖。 |
| `libs/aaa_particles-fabric-1.20.1-2.3.4.jar` | 1.20.1 的编译与开发运行依赖。用于 1.20.4、1.21.11 编译时只解析类型。 |
| `libs/BBS-VFX-*.jar`、`libs/VFX-LIGHTS-*.jar` | 可选模组兼容层的编译依赖。 |
| `run/<版本>/mods/` | 开发客户端实际使用的 BBS 和可选模组。目录内容由本地运行配置决定。 |
| `libs/bbs-fs-2.7-1.20.1/`、`libs/bbs-fs-1.21.11/`、`libs/bbs-fs-dev-1.20.1/` | BBS 上游与开发参考源码。 |
| `libs/bbs-lezy-main/`、`libs/bbs-ui-rabbit-1.0.1-源码/` | 第三方插件的兼容参考源码。 |
| `libs/archive/bbs-2.6.1/`、`libs/archive/irlite/`、`libs/bbs-2.5.2-1.20.4-named-dev.jar` | 历史版本和兼容参考。 |
| `libs/bbs_physics-1.0.2-1.20.1-1.20.4.jar` | 物理插件参考产物。 |
| `libs/bbs-addon-2.7-to-2.8.zip` | 上游 BBS Addon API 迁移参考包，供 2.8 适配使用。 |

可用的私有开发脚本由 [private-dev-runtime.gradle](../gradle/private-dev-runtime.gradle) 发现。没有这些脚本时仍可用公开构建任务。
