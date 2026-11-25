# Model Morph（BBS Cubed 3.6.9）

Model Morph 随 1.20.x 目标发布。它在现有 Model 关键帧上启用，并作用到下一枚 Model 关键帧。粒子编辑器中的粒子变形是独立功能。

## 使用与验证

1. 在 Model 轨道设置源与目标模型关键帧，在源帧勾选形变。
2. 打开 Cube 映射编辑器，先运行自动匹配，再在源与目标视口核对匹配结果。支持一对一、一对多和多对一，映射类型由选择数量确定。
3. 在预览滑块检查起点、中点和终点。要验证持久化，可复制关键帧、撤销映射编辑、关闭并重新打开影片。
4. 用 `./gradlew.bat test` 跑映射、数学、纹理和预览相关测试，用 `./gradlew.bat runClient1201` 验证实际渲染和交互。

## 设计约束

- 在两帧之间逐点求值，每次 `FormProperties.apply` 先清空临时状态。跳转、回播和循环不依赖上一帧结果。两个端点仍走各自的正常模型渲染。
- `bbsppp_model_morph` 随关键帧保存。拷贝、撤销快照和相等性判断应包含扩展数据。载入时仍支持早期 `mapping_json` 与扁平键原型。
- 正常端点按平移、四元数旋转和缩放插值。跨骨骼映射以源骨骼空间为参照，遇到奇异矩阵等退化情况时使用顶点插值。焊接模型回退普通渲染。
- 形变中间帧使用按表单隔离的 CPU 顶点缓冲。着色器选择与几何提交必须采用同一形变条件，避免错用预烘焙 VAO。纹理同步使用目标 UV 布局，退出形变时应还原纹理状态。
- 3D 拾取按实际投影面与深度选择，缓存按视口实例和模型分开。同一模型出现在多个视口时，要验证源视口点击与树节点选择是否一致。

## 代码入口

- [关键帧数据与运行时](../src/main/java-1.20.x/gbeic/bbsplusplus/client/morph)包含 `ModelMorphData`、`LegacyMorphMapping`、`ModelMorphRuntime` 与渲染缓存。
- [映射编辑器](../src/main/java-1.20.x/gbeic/bbsplusplus/client/ui/morph)包含面板、列表、选择、历史和预览组件。
- [Mixin](../src/main/java-1.20.x/gbeic/bbsplusplus/mixin/morph)将保存、采样、渲染和编辑器接入 BBS。
- [数据与数学测试](../src/test/java/gbeic/bbsplusplus/client/morph)、[UI 测试](../src/test/java/gbeic/bbsplusplus/client/ui/morph)和[目标遍历测试](../src/test/java/gbeic/bbsplusplus/mixin/morph)只在 1.20.x 执行。

旧的 `MODEL_MORPH_1.20.x.md`、`MODEL_MORPH_HANDOFF.md` 和 `MODEL_MORPH_PR.md` 可从 Git 历史查阅。历史分支 `feat/model-morph-1.20.x`、Linux 路径和当时的测试计数对应旧开发环境。
