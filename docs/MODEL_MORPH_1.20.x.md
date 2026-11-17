# BBS-Cubed Model Morph（1.20.x）

> 基线与范围：`mod_version=3.6.9`，分支 `feat/model-morph-1.20.x`，目标平台 1.20.1（单 jar 兼容 1.20.1–1.20.4）。
> 本文依据 `src/main/java-1.20.x/gbeic/bbsplusplus/` 下的实际代码撰写；**1.21.11 不在本功能范围内**。
> 状态图例：✅ 已通过（有命令/测试输出证据）｜❌ 失败（有证据证明不通过）｜🟡 待验证（实现已存在，缺运行证据）｜⬜ 未测试（尚未验证或依赖未接入）

---

## 1. 概述

Model Morph 让**同一个 Model 轨道上的相邻两个关键帧之间**，从「瞬间硬切模型」变成「Cube 级别的逐步形变」。
它把源模型的每个 Cube 平滑过渡到目标模型中被映射的 Cube：位置线性插值、朝向四元数插值、尺寸分量插值，
贴图同步逐像素交叉淡化。观众看到的是物件"长成"另一个物件，而不是一帧之内换模型。

### 1.1 核心特性

| 特性 | 说明 |
| --- | --- |
| 无独立轨道 | 作为**原生 Model 关键帧**的扩展字段存在，不新增轨道、不新增编辑器页面 |
| 三种映射 | 1:1、1:N（克隆分化）、N:1（完整汇聚），由两侧选中数量自动推导，不需要用户选类型 |
| 视觉映射编辑器 | 在关键帧面板打开，按骨骼 / Cube 配对源与目标 |
| 自动匹配 | 优先保留同 key 配对；其余按模型包围范围归一化位置、相对尺寸、长宽比和骨骼/名称相似度做全局分配；数量不等时生成 1:N 或 N:1 |
| 纹理联动 | 源与目标 albedo 逐像素按进度混合，同时每个顶点的 UV 从源 UV 区域插到目标 UV 区域 |
| 逐点求值 | 进度由「当前段 + 插值参数」直接求出，不累计逐帧状态，拖动 / 倒放 / 循环 / 跳转天然正确 |
| 关闭即原版 | 默认关闭；关闭时渲染输出不变，且不做任何 Cube 匹配、几何生成或贴图工作 |
| 多实例隔离 | 形变状态挂在 `ModelForm` 表单实例上，同一份模型资源被多个回放实例共用也互不影响 |

### 1.2 快速上手

1. 在动画 / 影片编辑器里选中一个 **Model 关键帧**（它必须存在**下一个** Model 关键帧，且模型不同）。
2. 关键帧面板底部「模型形变（可选）」里打开 **模型形变** 开关；此时会自动填入源模型 = 当前关键帧模型、目标模型 = 下一关键帧模型的模型 ID（即使 ID 相同，也可以通过 Cube 映射重组几何）。
3. 点 **自动匹配** 生成建议映射，或点 **编辑 Cube 映射** 手工配对；完整校验结果在映射编辑页中查看。
4. 拖动时间轴穿过该段即可看到过渡；不开「同步纹理」则只形变几何、贴图保持源模型。

### 1.3 非目标（本版明确不做）

- 不建立 Morph 专属轨道（不复制 Texture Tween 的"专属轨道"路线）。
- 不做模型内部骨骼动画的重新采样（形变只处理绑定姿态 + Cube 几何）。
- 不做 LabPBR `_n` / `_s` 伴随贴图混合。
- 不做高级融合（Spatial Assembly / Primary Merge），重叠面只做到"确定性渲染"，不做到"完美无缝"。
- 不支持 1.21.11（新代码全部放在 `src/main/java-1.20.x`）。

---

## 2. 与原有 Model 轨道的关系

### 2.1 只是"给已有轨道加一个系数"

BBS 原版 Model 轨道存的是**模型 ID 字符串**，且该通道的工厂是阶梯型（`isStepped()` 为真）——所以两个关键帧之间模型不会变化，
到下一个关键帧才整体切换。Model Morph 完全复用这个结构：

- **控制区间**：某个 Model 关键帧上的 Morph 开关为 ON 时，它控制「**该关键帧 → 下一个 Model 关键帧**」这一段。
- **切换点不变**：下一个关键帧处目标模型仍然由 BBS 自己的阶梯切换接管；Morph 只把这段区间内的画面从"突变"变成"渐变"。
- **关闭时**：不读映射、不建方案、不改顶点，BBS 原版逻辑逐字节保持。

| 对比项 | 原版 Model 轨道 | 开启 Morph 后 |
| --- | --- | --- |
| 段内模型值 | 恒为源模型（阶梯） | 恒为源模型（阶梯，未改变） |
| 段内几何 | 源模型原始 Cube | 被映射的源 Cube 替换为逐帧生成的过渡几何 |
| 段内贴图 | 源模型贴图 | 可选：源↔目标 albedo 逐像素混合 |
| 下一个关键帧 | 直接切换为目标模型 | 同样直接切换为目标模型（交接点不变） |

### 2.2 与 Texture Tween 是同一套扩展机制

两者都用「**关键帧数据里的一个子树**」来扩展原生关键帧，而不是新增轨道类型：

| 机制 | Texture Tween | Model Morph |
| --- | --- | --- |
| 扩展接口 | `ITextureTweenKeyframe` | `IModelMorphKeyframe` |
| 序列化位置 | 关键帧自身 `MapType` 的子树 | 关键帧自身 `MapType` 的子树（键 `bbsppp_model_morph`） |
| 同步注入点 | `toData` / `fromData` / `copy` / `copyOverExtra` / `equals` | 同上（完全一致的五处） |
| 生效轨道 | `texture` / `*/texture` | `model` / `*/model` |
| 撤销与复制粘贴 | 靠 `equals` + `copy` 自动正确 | 同左 |

因为配置属于关键帧自身，所以**撤销快照比较、复制粘贴、跨影片粘贴、关键帧复制**都天然包含 Morph 配置，不需要额外的编辑历史逻辑。

### 2.3 为什么不是独立轨道

独立轨道会带来三处不可控：轨道与模型关键帧的对齐、轨道被单独删除后的悬空引用、以及"没有 Morph 的旧影片"被强行多出一条空轨道。
挂在关键帧上则满足：旧影片（没有该子树）加载后默认关闭，行为与升级前完全一致。

---

## 3. 数据结构与序列化

### 3.1 类型一览

| 类型 | 职责 |
| --- | --- |
| `keyframes/IModelMorphKeyframe` | 关键帧上的扩展状态接口（get/set/isEnabled） |
| `client/morph/ModelMorphData` | 一条关键帧的 Morph 配置根对象，负责序列化与 equals/hashCode |
| `client/morph/MappingGroup` | 一条映射组（源集合 + 目标集合 + 稳定 ID），映射类型由数量推导 |
| `client/morph/MappingGroup.CubeRef` | 稳定 Cube 引用（key + 几何指纹），不依赖数组下标 |
| `client/morph/ModelMorphSkeleton` | 模型骨骼/Cube 的只读快照（含绑定姿态帧 `restFrame`） |
| `client/morph/ModelMorphAdapter` | 从 BBS `Model` 生成快照、生成稳定 key 与几何指纹 |
| `client/morph/ModelMorphValidation` | 映射校验、冲突检测、统计未映射、展开成逐 Cube 变换 |
| `client/morph/MorphAutoMatch` | 自动匹配建议 |
| `client/morph/MorphPlan` / `MorphPlanCache` | 预计算的形变方案 + LRU 缓存 |
| `client/morph/MutationCube` | 单个源 Cube → 目标 Cube 的预计算形变关系与采样 |
| `client/morph/MorphGeometry` | 每表单实例的采样缓冲区（复用，避免每帧分配） |
| `client/morph/MorphMath` | 安全反求 / 安全分解 / slerp / 法线矩阵等 |
| `client/morph/MorphTextures` | 贴图混合与缓存 |
| `client/morph/ModelMorphState` | 单个表单实例在当前帧的形变状态 |
| `client/morph/ModelMorphRuntime` | 求值入口 + 渲染上下文开关 |
| `client/morph/MorphRenderContext` | 渲染期把采样结果传给 `CubicCubeRenderer` 的线程内栈 |
| `client/morph/LegacyMorphMapping` | 早期原型 JSON 映射的一次性导入 |
| `client/ui/morph/UICubeMappingEditorPanel` | BBS Dashboard 全屏映射页（三列布局 + 3D 预览 + 进度滑条） |

### 3.2 序列化格式

Morph 配置写在**关键帧自己的 `MapType`** 里，键为 `bbsppp_model_morph`：

```json
{
  "<BBS 原生关键帧字段>": "由 BBS 自己写入（tick / value / 插值等，本功能不改动）",
  "bbsppp_model_morph": {
    "version": 1,
    "enabled": true,
    "source_model": "bbs:bed",
    "target_model": "bbs:boat",
    "texture_sync": true,
    "allow_incomplete": true,
    "groups": [
      {
        "id": "3f0c1b8e-2a44-4f4e-9d4f-8e0b7a6c5d41",
        "sources": [
          { "key": "root/leg_front:leg_cube", "signature": "-2.0000,0.0000,-6.0000|4.0000,6.0000,12.0000|0.0000,0.0000,0.0000|0.0000,0.0000,0.0000|0.0000|" }
        ],
        "targets": [
          { "key": "root/hull:hull_main", "signature": "..." }
        ]
      }
    ]
  }
}
```

写入规则（`ModelMorphKeyframeMixin`）：只有当轨道是 Model 轨、且配置**不等于默认值**时才写出子树——
即"没配过 Morph 的关键帧"不会在文件里留下任何字节，旧影片因此完全不受影响。

### 3.3 兼容历史原型

早期开发原型用过三种旧键，`fromData` 会按顺序兜底读取：

| 旧键 | 含义 | 处理 |
| --- | --- | --- |
| `bbsppp_model_morph_mode` | 0/1 开关 | 转成 `enabled` |
| `bbsppp_model_morph_texture_sync` | 纹理同步开关 | 转成 `texture_sync` |
| `bbsppp_model_morph_mapping` | JSON 字符串映射 | 由 `LegacyMorphMapping` 解析成映射组 |
| `mapping_json`（子树内） | 同上，更早期的位置 | 当 `groups` 为空时导入 |

旧格式解析失败时**静默忽略**（保持关闭），不会让影片加载报错。

### 3.4 复制与比较

- `copy()` 深拷贝全部字段与映射组（`MappingGroup.copy()` 保留组 ID，便于撤销后身份稳定）。
- `equals()` / `hashCode()` 覆盖 `version / enabled / textureSync / allowIncomplete / sourceModel / targetModel / groups`，
  因此撤销栈能感知"Morph 配置变了"，而不仅仅是模型 ID 变了。
- `MorphPlanCache` 直接用 `ModelMorphData.hashCode()` 作为缓存键的一部分。

---

## 4. 三种映射（1:1、1:N 克隆、N:1 完整汇聚）

### 4.1 类型由数量自动推导

`MappingGroup.Type` 完全由两侧列表长度决定，用户**不需要也不应该**手选类型：

| 源数量 | 目标数量 | 推导类型 | 标签 | 是否支持 | 语义 |
| --- | --- | --- | --- | --- | --- |
| 1 | 1 | `ONE_TO_ONE` | `1:1` | ✅ | 常规一对一形变 |
| 1 | N>1 | `ONE_TO_MANY` | `1:N` | ✅ | 一个 Cube **克隆分化**成 N 个目标 |
| N>1 | 1 | `MANY_TO_ONE` | `N:1` | ✅ | N 个源 Cube **完整汇聚**到同一个目标 |
| N>1 | M>1 | `MANY_TO_MANY` | `N:N` | ❌ 拒绝 | 校验层报 `unsupported mapping type N:N in group <id>` |
| 0 | * | `EMPTY` | `-` | ❌ 忽略 | 空组不参与 |

> 为什么拒绝 N:N：N:N 需要求解"哪个源对应哪个目标"的排列匹配，语义含糊且无法给出可预测的视觉结果。
> 需要 N→M 时请拆成多条 1:1 / 1:N / N:1 组，让每条边都可预测。

### 4.2 展开规则

`ModelMorphValidation.transitionsOf()` 是唯一定义，`ModelMorphValidation.expand()` 与 `MorphPlan` 都复用它：

| 类型 | 展开出的变换 |
| --- | --- |
| 1:1 | 1 条：`source[0] → target[0]` |
| 1:N | N 条：都以 `source[0]` 为起点，各指向一个目标（同一源 Cube 在同帧渲染 N 份过渡几何） |
| N:1 | N 条：各源 Cube 各一条，都指向 `target[0]`（同一目标由 N 份过渡几何汇聚而成） |

一个源 Cube 允许在同组内对应多个过渡体（1:N），因此渲染侧按 `Map<ModelCube, List<Slot>>` 组织，
`CubicCubeRendererMorphMixin` 会对同一个 Cube 依次提交多个过渡体的顶点。

### 4.3 冲突检测

校验层用两个集合 `usedSources` / `usedTargets` 做唯一性检查：

- 同一个 Cube（无论源还是目标）出现在**多个映射组**里 → 记入 `conflicts`，报 `N cube(s) are used by conflicting mapping groups`。
- 同组内的重复引用会被 `MappingGroup.setSources/setTargets` 直接去重（按 key 去重，保留首次出现）。
- 冲突时不进入形变渲染，而是回退到原版硬切换（避免"一个 Cube 被两条曲线同时拉扯"的不可预测结果）。

### 4.4 稳定引用，而不是数组下标

Cube 引用 `CubeRef(key, signature)` 的 key 生成规则（`ModelMorphAdapter.cubeKey`）：

| 情形 | key 形式 | 示例 |
| --- | --- | --- |
| 有名字、组内唯一 | `<骨骼路径>:<Cube 名>` | `root/leg_front:leg_cube` |
| 有名字但组内重名 | `<骨骼路径>:<Cube 名>#<序号>` | `root/body:panel#2` |
| 无名字 | `<骨骼路径>:#<序号>` | `root/body:#3` |

其中骨骼路径用 `/` 连接各级 `ModelGroup.id`。**序号只用于消除重名歧义，永远不单独作为身份**。

`signature` 是几何指纹，包含 origin / size / pivot / rotate / inflate / material（定点 4 位小数），
作用是"模型资源被改动后还能不能确认是同一个 Cube"：

- 指纹为空（旧数据 / 手写数据）→ 只按 key 解析；
- 指纹不匹配 → 校验报 `<side> cube geometry changed: <key>`，该引用失效，需要用户重新映射。

这解决了"用户改了模型文件里 Cube 顺序，但 key 依然能命中"和"模型换了同名 Cube，几何已变"两类问题。

---

## 5. 形变求解数学（含 Bone Anchor 复用说明与安全防护）

### 5.1 坐标与单位

- BBS 模型单位（1 像素）→ 世界（方块单位）的换算因子为 `MODEL_UNIT = 1/16`。
- BBS 模型空间与渲染空间相差一个 **Y 轴 180°**：`ModelMorphAdapter.skeleton()` 的根帧为 `rotateY(π)`，
  与 Bone Anchor `parentLocal` 中"附件帧带半转"的约定一致。
- 骨骼绑定帧（rest frame）：`translate(pivot) → rotateZYX(rotate) → scale(scale) → translate(-pivot)`，顺序与 BBS 渲染器一致。
- Cube 自身帧：`translate(cubePivot) → rotateZYX(cubeRotate) → translate(-cubePivot)`，与 BBS `renderCube` 一致。

### 5.2 端点姿态：TRS，不是矩阵逐元素插值

`MorphMath.Trs` = 平移 + 旋转 + 缩放。每个端点 Cube 先被分解成 TRS，段内插值为：

```text
translation(t) = lerp(T_from, T_to, t)          // 逐分量线性
rotation(t)    = slerp(Q_from, Q_to, t)         // 四元数最短路径
scale(t)       = lerp(S_from, S_to, t)          // 逐分量线性
```

**绝不对 4×4 矩阵做逐元素插值**：矩阵逐元素插值会让旋转在中间帧出现非刚性缩放与剪切（俗称"幽灵缩放"），
而 TRS 插值保证任意中间帧仍是合法的刚体 + 缩放变换。

slerp 的实现细节：先把两个四元数归一化，若点积 < 0 则取反末端（走短弧），避免转过 180° 时绕远路。

### 5.3 跨骨骼重绑定

源 Cube 与目标 Cube 往往挂在**不同骨骼**上，且骨骼绑定姿态不同。为了让目标 Cube 在源骨骼空间里"落位正确"，
目标 Cube 的帧被反求到源骨骼空间：

```text
targetFrameInSourceSpace =
    inverse(sourceBoneRestFrame)
  * targetBoneRestFrame
  * targetCubePivotFrame
```

即：把目标 Cube 从其目标骨骼空间搬回模型空间（乘目标骨骼 rest frame），再进入源骨骼空间（乘源骨骼 rest frame 的逆）。
这样即使源 Cube 与目标 Cube 分属不同骨骼、骨骼本身还有缩放或旋转，过渡终点仍精确落在目标 Cube 应在的位置。

源骨骼的逆矩阵按骨骼路径缓存复用（`inverseSourceFrames`），同一骨骼下多个 Cube 只求一次逆。

### 5.4 面匹配：按角点规范位，而不是数组顺序

BBS 的 `ModelQuad` 顶点顺序与绕序在不同模型中可能不同，`ModelCube` 也可能只有部分面。
`MutationCube.build()` 因此不按"第 0 个顶点对第 0 个顶点"匹配，而是：

1. 用面法线把 quad 归类到 6 个标准面之一（`classifyFace`）：
   `0 = -Z, 1 = +X, 2 = +Z, 3 = -X, 4 = +Y, 5 = -Y`。
2. 对每个顶点计算**角点规范位**（`bitsOf`）：相对 Cube 中心的 x/y/z 三个方向上的大小关系压成 3 bit（0–7）。
3. 同一位即同一角点。因此**顶点顺序不同、绕序相反、只有部分面**都能正确对上。

| 情况 | 处理 |
| --- | --- |
| 面只在源侧存在 | 目标侧该面所有角点塌缩到目标 Cube 中心（`targetCollapsed`），视觉上"面收进中心" |
| 面只在目标侧存在 | 源侧该面塌缩到源 Cube 中心（`sourceCollapsed`），视觉上"面从中心长出" |
| 两面都存在但绕序相反 | 由 `windingOf` 记录每面三角化方向：`0 = (0,1,2)(0,2,3)`，`1 = (0,2,1)(0,3,2)`，渲染时按记录的方向提交 |
| 两侧都没有该面 | 该面不进 faceMask，完全不渲染 |

### 5.5 退化情形的安全回退

以下任一条件成立时，不做危险的矩阵求逆/分解，而是**逐顶点线性插值**（端点位置在建立方案时一次算好，播放时只 lerp）：

| 触发条件 | 原因 |
| --- | --- |
| 面存在性不一致（`facePresenceMismatch`） | 需要塌缩中心，位置不再是刚体变换 |
| 源帧或目标帧行列式 < 0（镜像） | 镜像会破坏 TRS 的旋转约定 |
| `MorphMath.decompose` 抛异常（零缩放 / 剪切 / 非有限值 / 非仿射） | 无法安全拆出旋转 |
| 采样得到的顶点出现非有限值 | 单点兜底回退到源端点位置 |

逐顶点线性插值不保证中间帧刚体性，但**保证不产生 NaN、不产生无限缩放、不炸画面**——这是本功能的核心取舍：
宁可中间帧"软"一点，也不要为了炫技强行求逆导致整个模型消失。

### 5.6 法线

```text
normalMatrix = inverse(rotationScale(3x3))^T
```

- 若 `|det| < SINGULAR_EPSILON` → 退回单位矩阵（保持原始面法线），避免 NaN 法线导致光照全黑。
- 每个法线在变换后再做一次 `safeNormalize`（长度² < 1e-12 时回退到标准面法线）。
- 最终提交时还会再乘一次 `MatrixStack` 的 normal matrix（见 6.2），因为顶点是在骨骼矩阵下提交的。

### 5.7 Bone Anchor 复用说明（MIT）

本功能的"安全反求 / 安全分解"设计思路来自 **Bone Anchor** 模组的 `AnchorMath`（MIT 许可）。
复用的是**设计思路与防护约定**，不是代码搬运：

| Bone Anchor 的约定 | 本项目对应实现 |
| --- | --- |
| 求逆前先拒绝非有限值与奇异矩阵 | `MorphMath.inverse()`：非有限或 `|det| < 1e-8` 直接抛异常，由调用方回退 |
| 分解前拒绝零缩放与剪切 | `MorphMath.decompose()`：分量 < `1e-6`、剪切点积 > `1e-4`、非仿射行列（`m03/m13/m23/m33`）全部拒绝 |
| 镜像基（负行列式）保留 X 轴符号 | `decompose()`：`det < 0` 时 `scale.x = -scale.x`，与 BBS `Transform.fromMatrix` 约定一致 |
| 法线用逆转置，退化时兜底 | `MorphMath.normalMatrix()` + `safeNormalize()` |
| 宁可放弃形变也不输出非法变换 | `MutationCube` 的逐顶点线性回退路径 |

> ⚠️ **许可合规**：BBS-Cubed 本体是 **LGPL-3.0-or-later**。Bone Anchor 是 MIT，MIT 与 LGPL 兼容，但
> **MIT 要求保留版权声明与许可声明**。因此如果后续把任何 Bone Anchor 代码（而不只是思路）引入仓库，
> 必须在源码树里保留其 MIT 声明（见第 15 节）。

---

## 6. 渲染生命周期

### 6.1 注入点一览

| 注入目标 | Mixin | 时机 | 作用 |
| --- | --- | --- | --- |
| `PropertyTrack#apply` | `PropertyTrackMorphMixin` | HEAD | 由当前段求出形变状态写入表单（仅在 `model` / `*/model` 轨道） |
| `FormProperties#apply` | `FormPropertiesMorphMixin` | HEAD | 每帧先**清空**所有实例的形变状态，防止残留 |
| `ModelForm` | `ModelFormMorphMixin` | 字段 | 表单实例持有 `ModelMorphState`（多实例隔离的关键） |
| `ModelFormRenderer#renderInUI / render3D / renderArm / renderFirstPersonHand` | `ModelFormRendererMorphMixin` | HEAD / RETURN | 在 BBS 选择 VAO 着色器前压入本次实际绘制的表单 |
| `ModelInstance#render` | `ModelInstanceMorphMixin` | HEAD / RETURN | 压栈 / 出栈渲染上下文；绑定与还原混合贴图 |
| `ModelInstance#isVAORendered` | `ModelInstanceMorphMixin` | RETURN | 仅在形变上下文激活时强制返回 `false`（关闭 VAO） |
| `CubicCubeRenderer#renderCube` | `CubicCubeRendererMorphMixin` | HEAD (cancel) | 接管被映射 Cube 的顶点提交（面掩码 / 绕序 / 法线矩阵） |
| `Keyframe#toData / fromData / copy / copyOverExtra / equals` | `ModelMorphKeyframeMixin` | RETURN / TAIL | 序列化、复制粘贴、撤销快照 |
| `UIModelKeyframeFactory#<init>` | `UIModelKeyframeFactoryMorphMixin` | TAIL | 关键帧面板追加 Morph 控件 |
| `TextureManager#delete / accept` | `ModelMorphReloadMixin` | TAIL | F6 重载 / 文件监视器事件后整体失效缓存 |

### 6.2 一帧的完整流程

```text
[属性应用阶段]
  FormProperties.apply HEAD
    └─ ModelMorphRuntime.clearAll(root)          // 清空全部实例状态
  PropertyTrack.apply HEAD（仅 model 轨道）
    └─ ModelMorphRuntime.update(form, channel.find(tick))
         ├─ 段无效(a==b) / 未开启 / 无组 / 模型 ID 不匹配 → 直接返回（状态保持清空）
         └─ state.set(sourceId, targetId, progress, data)

[渲染阶段]
  ModelInstance.render HEAD
    └─ ModelMorphRuntime.beginRender(instance)
         ├─ 表单状态未激活 / 当前模型 ID 与状态不符 → 不接管，原版渲染
         ├─ ensureGeometry()：惰性从 LRU 取方案 + 每实例缓冲区（方案为空 → 不接管）
         ├─ 实例有焊接绑定 → 不接管，原版渲染
         └─ sample(progress) → MorphRenderContext.push(...)  +  绑定混合贴图
  CubicCubeRenderer.renderCube HEAD（每个 Cube 一次）
    ├─ 上下文为空 → 不接管（原版）
    ├─ 该 Cube 没有过渡体 → 不接管（原版，例如未映射的源 Cube）
    └─ 有过渡体 → 逐 face 逐顶点提交，取消原版调用
  ModelInstance.render RETURN
    └─ 还原贴图 → MorphRenderContext.pop()
```

### 6.3 为什么必须"每帧生成"

形变几何随进度连续变化，无法烘焙。因此：

- **不写共享资源**：`ModelCube` / `Model` / 纹理资源**一律不被修改**。过渡顶点写进每实例的 `float[]` 缓冲。
- **关闭 VAO**：BBS 的 VAO 在模型加载时烘焙，无法承载逐帧变化的顶点；`isVAORendered` 只在形变上下文激活的那一帧返回 `false`，
  离开形变区间立刻恢复 VAO 路径（渲染成本回到原版水平）。
- **上下文用栈而不是单槽**：模型块渲染里可能嵌套其它形态渲染，栈保证 push/pop 正确配对。
- **焊接模型回退**：`instance.getWeldBindings()` 非空时不做形变（v1 明确不处理焊接附件）。

### 6.4 交接点

- **进度 = 0**：`ModelMorphState.active()` 要求 `progress > 0F`，因此关键帧刻度上渲染的是**真实源模型**，不会出现"源模型 + 过渡体"双重叠加。
- **进度 → 1**：到了下一个关键帧，BBS 阶梯切换把模型值换成目标模型；`beginRender` 发现
  `form.model != state.sourceId()` → 不接管 → 渲染**真实目标模型**。
- 因此"过渡体"只在 `0 < p < 1` 的开区间内存在，两端都是真实模型，没有接缝。

---

## 7. 纹理联动

### 7.1 混合规则

`MorphTextures.blend(source, target, progress)`：

```text
Color(p) = (1 - p) * SourceColor(uv) + p * TargetColor(uv)
```

- 逐像素（不是逐 Cube、不是整体 alpha 叠加），四个通道各自线性插值后四舍五入回 0–255。
- 两张图尺寸不同时，把目标图**最近邻重采样**到源图尺寸再混合（源图尺寸作为基准）。
- 进度被量化到 `STEPS = 32` 步；`step == 0` 或 `step == 32` 时**不生成混合贴图**（直接返回 `null`），
  调用方保持原版贴图绑定 —— 即两端不会有一张"多余的全量拷贝"。
- 任一侧贴图为空、或源=目标时同样返回 `null`。

### 7.2 UV 也在一起动

几何侧每个顶点的 UV 从**源 Cube 的 UV 区域**插到**目标 Cube 的 UV 区域**（`MutationCube.writeUv`）。
这带来一个重要效果：**每个克隆分化体都能收敛到自己目标的 UV**，而不是所有克隆共用源 UV 导致取到同一块图。

### 7.3 绑定与还原

- 混合贴图在 `ModelInstance.render` HEAD 绑定（记忆 `TextureManager.getLastBound()`），RETURN 时还原原贴图。
- 由关键帧上的「同步纹理」开关（`texture_sync`，默认开）控制；关闭则只形变几何，贴图始终是源模型贴图。

### 7.4 缓存

| 项 | 值 |
| --- | --- |
| 缓存条目上限 | `MAX_ENTRIES = 8`（按 `(source, target)` 存取，LRU） |
| 进度量化步数 | `STEPS = 32` |
| 纹理过滤 | `GL_NEAREST`（与 BBS 像素风格一致，避免线性过滤产生糊边） |
| 更新策略 | 同一进度重复请求时直接复用；进度变化时只 `updateTexture` 重写像素，不重建纹理对象 |
| 失效 | `MorphPlanCache.invalidateAll()` 会一并调用 `MorphTextures.invalidateAll()`（删除 GL 纹理与 Pixels 缓冲） |

---

## 8. 时间轴行为

### 8.1 逐点求值，不累计状态

```text
raw = segment.getInterpolationProgress()                 // 段内归一化进度 x
if 工厂 isStepped:  p = clamp01(raw)                     // ← 模型轨走这条分支
else:               p = clamp01(keyframe.interpolation.interpolate(0, 1, raw))
```

- 因为 **Model 轨的值类型是字符串，其工厂在实际数据中是阶梯型**，所以通常走 `p = clamp01(raw)`，即**段内线性**推进；
  代码里显式判断 `factory.isStepped()`，一旦某个工厂不是阶梯型就会改走 `keyframe.getInterpolation()` 的曲线。
- 没有"上一帧进度 / 累计时间"这类状态，所以：

| 操作 | 结果 |
| --- | --- |
| 拖动播放头到任意位置 | 直接从该点求出进度，正确 |
| 倒放 | 进度自然减小，正确 |
| 循环 | 每轮都从段起点重新求值，正确 |
| 跨段跳转 / 时间轴跳转 | 不依赖历史，正确 |
| 暂停在中间 | 稳定在同一进度，正确 |

### 8.2 状态清空与残留防护

`FormProperties.apply` 每帧 HEAD 先 `clearAll`，递归遍历 `root.parts` 所有形态清空状态。
没有这一层时，删关键帧、把播放头移出区间、或关掉 Morph 都会残留上一帧的形变几何。

`ModelMorphState.set()` 还会比较 `data.hashCode()`：配置变了就丢弃已解析的方案和几何缓冲，强制重新解析。

### 8.3 这些情况不会形变（静默回退原版）

| 条件 | 位置 |
| --- | --- |
| 段无效（`a == b`，例如最后一个关键帧之后的保持段） | `ModelMorphRuntime.update` |
| 关键帧未开启 Morph 或没有任何映射组 | 同上 |
| 段两端模型 ID 为空或相同 | 同上 |
| 配置里记录的 `source_model` / `target_model` 与当前段两端模型 ID 不符 | 同上（防止用户改模型后错配） |
| 方案不可播放（有校验错误） | `MorphPlanCache` |
| 模型不是纯 Cube 模型（含 OBJ mesh） | `MorphPlan.build` |
| 实例有焊接绑定 | `ModelMorphRuntime.beginRender` |
| 渲染时当前模型的 ID ≠ 状态里的源模型 | `beginRender`（交接点保护） |

---

## 9. 映射编辑器使用说明

### 9.1 关键帧面板控件

选中 Model 关键帧后，面板底部出现「模型形变（可选）」分组：

| 控件 | L10n 键 | 行为 |
| --- | --- | --- |
| **模型形变** 开关 | `bbsppp.ui.keyframes.model_morph` | 打开/关闭本段形变；打开时自动补全源模型（= 当前关键帧模型）与目标模型（= 同轨道下一个不同 tick 的 Model 关键帧，允许模型 ID 相同） |
| **同步纹理** 开关 | `bbsppp.ui.keyframes.model_morph_texture_sync` | 是否在形变期间混合 albedo（默认开） |
| **编辑 Cube 映射** 按钮 | `bbsppp.ui.keyframes.model_morph_edit` | 打开可视化映射编辑器（传入配置副本 + 源/目标模型 ID + 回写回调） |
| **自动匹配** 按钮 | `bbsppp.ui.keyframes.model_morph_auto` | 全局生成 1:1 / 1:N / N:1 映射（**覆盖**现有映射组） |

映射校验详情集中在全屏映射编辑页，关键帧面板只保留开关、纹理同步和编辑入口。

### 9.2 自动匹配（全局分配 + 数量差异归并）

`MorphAutoMatch.suggest(source, target)`：

| 阶段 | 策略 | 说明 |
| --- | --- | --- |
| 1 | **完整 key 完全一致**（骨骼路径 + Cube 名） | 先固定确定性配对，避免后续全局分配改变同名 Cube 关系 |
| 2 | **归一化几何特征 + 全局最小代价分配** | 把每个模型的绑定姿态中心相对自身包围范围归一化，再计算位置、相对尺寸、长宽比与骨骼/名称相似度；骨骼名不再是硬门槛。Hungarian 分配同时优化整组配对，不会由遍历顺序逐个抢最近 Cube |
| 3 | **处理 Cube 数量差异** | 数量较多一侧的剩余 Cube 并到最近的已匹配对象，生成 1:N 或 N:1；不生成不受支持的 N:N |

匹配代价权重：位置 0.60、相对尺寸 0.20、长宽比 0.12、骨骼/名称相似度 0.08。按包围范围归一化能消除模型整体平移和均匀缩放对距离的影响。

- 算法尽量给两侧每个 Cube 都分配关系，因此这是**最佳努力建议**，不是语义保证；完全无关的模型也会得到一组几何上最接近的映射。请检查映射列表和形变预览，再用于动画。
- 每次点「自动匹配」都会**替换**当前全部映射组，手工映射会被清掉——请先做完自动匹配再手工微调。

### 9.3 批量映射

面板上的所有修改都通过同一个 `bbsppp$apply` 落盘，其行为是**批量**的：

1. `editor.cacheKeyframes()` 先把编辑器里的改动落到关键帧上。
2. 遍历编辑器所有 sheet，挑出「属于同一 `ModelForm` 且轨道 ID 是 `model` / `*/model`」的 sheet。
3. 对这些 sheet 里**当前选中的所有关键帧**写入**同一份**配置（`bbsppp$setModelMorph`）。
4. `editor.submitKeyframes()` 一次性提交。

因此：**多选若干相邻 Model 关键帧后改一次映射 = 一次改完整条链**。
对"整段动画里模型多次切换"的场景，这是最快的做法；只想改一个关键帧时请确保只选中它。

> ⚠️ 批量写入的是同一份配置（深拷贝后分别写入），因此源/目标模型 ID 也是同一份。
> 如果多选的关键帧对应的目标模型各不相同，请逐个关键帧单独配置。

### 9.4 全屏映射页（`UICubeMappingEditorPanel`）

「编辑 Cube 映射」按钮打开 `UICubeMappingEditorPanel`（`gbeic.bbsplusplus.client.ui.morph`），挂载到 BBS 2.7 已有的 `UIDashboard` / `UIDashboardPanel` 全屏宿主，不再作为可拖动、可缩放的悬浮窗。
关闭页面会重新打开启动映射编辑器的 BBS 菜单，恢复原关键帧编辑上下文。
它操作的是配置的**工作副本**，每次改动都通过 `onChange` 回调交给关键帧面板写回（写回逻辑见 9.3，因此修改同样会批量落到选中的关键帧）。
编辑器本身不修改源/目标模型 ID —— 这两个 ID 由关键帧面板根据时间轴推导后传入；它也不改 `enabled()` 开关。

源/目标模型未加载时的降级行为：摘要显示 `Source model not found` / `Target model not found`，
「自动匹配」与「建立映射」禁用，任何操作都不会崩。

#### 布局

| 区域 | 内容 |
| --- | --- |
| 顶部 BBS 工具栏 | 标题在左；自动匹配、建立映射、删除选中、全部清空、映射编辑/形变展示模式切换、返回关键帧编辑器以 BBS 原生图标 action bar 呈现，悬停显示本地化 tooltip |
| 内容区第一行 | 源模型 / 目标模型 ID 标签（模型 ID 为空时显示 `-`；模型未加载时由摘要行提示 `… not found`） |
| 左列 | 源模型 3D 预览（上）+ 源模型结构树（下），预览可直接拾取 Cube |
| 中列 | 目标模型 3D 预览（上）+ 目标模型结构树（下），预览可直接拾取 Cube |
| 右列 | “建立映射”选择卡片 + 映射关系表；映射编辑模式不保留形变预览，关系表铺满右列 |
| 形变展示模式 | 形变预览独占整个内容区，不额外显示底部统计、进度条或「允许不完整」开关 |

分栏由 BBS-FS 原生 `UIDockLayout` + `EditorLayoutNode` 管理，布局树保存到 `ValueEditorLayout` 的 `bbsppp.morph` 条目。分界线悬停/拖动时使用 BBS 主题色，交点会联动两个方向的 splitter；双击均分、最小面板尺寸、布局撤销和持久化均复用 BBS 原逻辑。

「允许不完整映射」开启时，未映射源 Cube 在过渡中保持源形状，未映射目标 Cube 在目标模型接管时出现；关闭时，不完整配置不会播放形变，会回退到原模型切换。

#### Cube 树

- **骨骼行**是分支：显示 `骨骼路径 (N)`，可展开/折叠，缩进 10 px/级；Cube 行是叶子（重名时显示 `名字#序号`）。
- **点击骨骼行 = 把该骨骼下所有 Cube 一起选中**，再点一次整块取消（按骨骼成组映射是最常见的工作方式）。
- 折叠：点行首的折叠箭头；←/→ 的展开折叠行为沿用 BBS `UIList` 的键盘处理（本类未自定义）。
- 点击 Cube 行按列表多选处理；列表启用了 BBS 的 `UIList.multi()`，具体多选修饰键沿用 BBS 其它列表的既有行为（裸点击 = 单选）。
- **搜索框**：大小写不敏感的子串匹配（骨骼路径 + Cube 名），过滤时**强制展开所有骨骼**，否则命中的 Cube 可能藏在折叠分支里让人以为没搜到。
- 行底色含义（`tint()`，红色优先级最高）：

| 底色 | 含义 |
| --- | --- |
| 红色 50% | 该 Cube 出现在**冲突或失效**引用里（必须处理） |
| 主色 50% | 属于**当前选中**的那条映射组 |
| 主色 25% | 已映射到别的组 |
| 无底色 | 未映射 |

#### 建立映射

1. 左列选 1 个或多个源 Cube，中列选 1 个或多个目标 Cube；
2. 点「建立映射」；
3. 数量直接决定类型：`1+1 → 1:1`、`1+N → 1:N`、`N+1 → N:1`；
4. **两侧都 >1 时拒绝创建**，弹错误提示（`bbsppp.ui.cube_mapping.many_to_many`），提示拆成 1:N 与 N:1——
   非法数据因此根本进不了渲染管线，而不是等到播放时才报错。此时「建立映射」按钮**故意保持可点**，否则提示根本弹不出来；
5. 新组会自动**顶掉复用同一批 Cube 的旧组**（`reuses()` 检查），所以"一个 Cube 属于两个组"的冲突在编辑器里建不出来；
6. 创建后自动清空两侧选择，并把新组设为选中。

按钮可用性（`refreshButtons()`）：

| 按钮 | 可用条件 |
| --- | --- |
| 自动匹配 | 源与目标模型都已加载 |
| 建立映射 | 两模型都已加载，且**两侧各至少选中 1 个** Cube（N:N 时仍可点，见上） |
| 清空全部 | 当前至少有 1 条映射 |
| 删除选中 | 映射列表里有选中的组 |

#### 映射列表

- 每行：`<类型> <源> -> <目标>`；多目标追加 `(N targets)`，多源追加 `(N sources)`。
- 立方体一律显示**短名**：取稳定 key 里最后一个 `:` 之后的部分（即不含骨骼路径的 Cube 名）。
- 不支持的组（历史数据中的 N:N / 空组）行首加 `!` 并有红色左边条，避免它们看起来像正常数据。
- 点某行 = 选中该组（决定「删除选中」的目标，同时驱动树里的高亮定位）。
- 注意：`(N targets)` / `(N sources)` 后缀目前是**硬编码英文**，尚未接入 L10n。

#### 三联 3D 预览

Dock 布局顶部并列三个预览面板：源模型、目标模型、形变预览。形变预览构造临时 `ModelForm`，把
`ModelMorphState` 写到这个表单上，再走 BBS 正式渲染路径。它以固定速度循环播放当前映射的形变进度，
用于快速观察当前映射组是否有明显错位；源/目标两个面板只负责静态 Cube 拾取。
初始机位 `rotate(25°, -10°)` / `distance 4`，加载时按绑定几何自动取景；相机交互沿用 `UIModelRenderer` 基类（本类未加任何自定义手势）。

| 情况 | 预览表现 |
| --- | --- |
| 两个模型都在 | 形变面板自动按固定速度循环播放，源/目标面板保持静态 |
| 源模型缺失 | 退化为**静态目标模型**预览（至少能看到目标长什么样） |
| 两个模型都缺失 | 只剩一个空的深色框 |
| 进度循环端点 | 目标端点与源端点之间循环切换，不残留上一次几何 |

> 自动播放**只驱动预览**，不改关键帧数据、也不等于时间轴 scrub；时间轴上的实际进度由第 8 节的求值逻辑决定。

#### 快捷键与关闭

| 操作 | 行为 |
| --- | --- |
| `Delete` | 删除映射列表中当前选中的映射组（等价「删除选中」）；仅在**有选中卡片**时生效；焦点在搜索框时 Delete 只作用于文本框，不会误删映射 |
| `Esc` | 关闭弹层（由 BBS 的 `UIOverlayPanel` 基类处理，非自定义逻辑） |
| 鼠标左键 | 点行选中；点骨骼行整块选择；点折叠箭头展开/收起 |
| ← / → | 展开/折叠骨骼分支（沿用 BBS `UIList` 的键盘处理，本类未自定义） |
| 鼠标滚轮 | 滚动树 / 映射列表 |
| 进度滑条 | 拖动，或直接输入 0–100 的整数 |

> 配对**必须**按「建立映射」按钮：没有"先点左边一行再点右边一行自动配对"的交互。

#### 本版明确没有的东西（避免误解）

- 编辑器内**没有独立的撤销/重做栈**（撤销走影片编辑器自己的关键帧撤销）；
- 没有 `Ctrl+Z` / `Ctrl+A`、批量重命名、拖拽配对、右键菜单等交互；
- 没有按材质 / UV / 单个面级别的映射（映射粒度是 Cube）；
- 预览没有「solo/隔离」或相机重置按钮；
- 关闭弹层后**不保留**树的展开状态与搜索文字（每次打开都是默认展开、搜索为空）；
- 没有「只匹配未映射项」按钮——「自动匹配」是**全覆盖**的；
- 不能在编辑器内切换源/目标模型，也不改 `ModelMorphData.enabled()`（开关始终在关键帧面板上）。

> 编译状态：该文件已由编辑器作者用 `javac` 对 BBS 2.7 编译期 jar 做过**单文件**编译验证；
> 这不等于整个 1.20.x 源码集通过了 Gradle 构建（见 13.1 / 13.4）。

### 9.5 推荐工作流

```text
1. 先把两个关键帧的模型选好（源、目标），确认两者都是纯 Cube 模型（不含 OBJ 网格）
2. 打开「模型形变」
3. 点「自动匹配」——先得到全局 1:1 配对，并按数量差异归并成 1:N / N:1
4. 检查映射列表和形变预览；不合适的组在「编辑 Cube 映射」中手工调整
5. 需要克隆分化（1:N）时，在编辑器里把 1 个源 Cube 选上、再选多个目标 Cube；
   需要完整汇聚（N:1）时反过来
6. 播放该段，检查交接点（两端）没有跳变
7. 需要贴图也过渡时确认「同步纹理」已开
```

---

## 10. 数据结构字段表

### 10.1 `ModelMorphData`（序列化根）

| 字段 | 类型 | 默认 | 序列化键 | 说明 |
| --- | --- | --- | --- | --- |
| `version` | int | `1` | `version` | 数据版本，读取时 `max(1, …)` |
| `enabled` | boolean | `false` | `enabled` | 本段是否开启形变 |
| `sourceModel` | String | `""` | `source_model` | 记录的源模型 ID（空 = 不校验） |
| `targetModel` | String | `""` | `target_model` | 记录的目标模型 ID（空 = 不校验） |
| `textureSync` | boolean | `true` | `texture_sync` | 是否混合 albedo |
| `allowIncomplete` | boolean | `true` | `allow_incomplete` | 映射不完整时是否仍然播放（`true` = 播放并给警告，`false` = 回退硬切） |
| `groups` | `List<MappingGroup>` | 空 | `groups` | 映射组列表 |

> 注：`allowIncomplete` 的 Javadoc 写的是"默认关闭"，但**字段与 `fromData` 的实际默认值是 `true`**；
> 本文以代码为准。若要改为"不完整即回退硬切"，需要同时改字段初始值与 `fromData` 的默认参数。

### 10.2 `MappingGroup` / `CubeRef`

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `id` | String (UUID) | `id` | 组 ID，复制时保留，便于撤销后身份稳定 |
| `sources` | `List<CubeRef>` | `sources` | 源 Cube 引用集合 |
| `targets` | `List<CubeRef>` | `targets` | 目标 Cube 引用集合 |
| `CubeRef.key` | String | `key` | 稳定 key（见 4.4） |
| `CubeRef.signature` | String | `signature` | 几何指纹（可空） |

读取时 `sources` / `targets` 也接受**纯字符串数组**（旧数据 / 手写数据）。

### 10.3 关键常量

| 常量 | 值 | 位置 | 含义 |
| --- | --- | --- | --- |
| `MODEL_UNIT` | `1/16` | `ModelMorphAdapter` | 模型单位 → 方块单位 |
| `SINGULAR_EPSILON` | `1e-8` | `MorphMath` | 判定矩阵奇异/不可逆 |
| `SCALE_EPSILON` | `1e-6` | `MorphMath` | 判定零缩放 / 非仿射行列 |
| `SHEAR_EPSILON` | `1e-4` | `MorphMath` | 判定剪切 |
| `FACE_COUNT` / `CORNERS` | `6` / `4` | `MutationCube` | 面数与每面顶点数 |
| `CONVERGENCE_SUPPRESS_PROGRESS` | `0.98` | `MorphPlan` | 汇聚末端抑制重复过渡体的进度阈值 |
| `CLONE_STAGGER` | `0.12` | `MorphPlan` | 克隆错峰起始比例的总幅度 |
| `MAX_ENTRIES`（方案） | `32` | `MorphPlanCache` | LRU 上限 |
| `MAX_ENTRIES`（贴图） | `8` | `MorphTextures` | LRU 上限 |
| `STEPS` | `32` | `MorphTextures` | 贴图进度量化步数 |
| `POSITION_WEIGHT` | `0.60` | `MorphAutoMatch` | 归一化位置代价权重 |
| `SIZE_WEIGHT` | `0.20` | `MorphAutoMatch` | 相对尺寸代价权重 |
| `SHAPE_WEIGHT` | `0.12` | `MorphAutoMatch` | 长宽比代价权重 |
| `SEMANTIC_WEIGHT` | `0.08` | `MorphAutoMatch` | 骨骼路径与 Cube 名代价权重 |
| `DATA_KEY` | `"bbsppp_model_morph"` | `ModelMorphData` | 关键帧数据子树键 |

### 10.4 缓存键

| 缓存 | 键 | 值 | 失效 |
| --- | --- | --- | --- |
| `MorphPlanCache` | `(sourceId, targetId, data.hashCode())` | `MorphPlan`（不可变） | `invalidateAll()`：F6 重载、文件监视器事件 |
| `MorphTextures` | `(sourceLink, targetLink)` | 混合纹理 + 像素缓冲 | 同上（连同 GL 纹理一起删除） |
| `ModelMorphState` | 表单实例自身 | 当前帧状态 + 方案哈希 | 每帧 `clear()` / 配置哈希变化 |

`MorphPlanCache.get()` 只在模型 ID 为空时返回 `null`；源/目标 ID 相同仍会按映射哈希建立方案，用于同一模型资源内的 Cube 重组。

---

## 11. 已知限制

| # | 限制 | 影响 | 现状 |
| --- | --- | --- | --- |
| 1 | **仅 1.20.x** | 1.21.11 无此功能 | 明确不在范围内 |
| 2 | **不做 LabPBR `_n` / `_s` 混合** | 过渡期间法线/高光贴图仍用源模型那张，强 PBR 材质下中间帧光照会偏 | 已知限制，v1 不处理 |
| 3 | **焊接模型（weld bindings）回退** | 带焊接附件的模型不做形变 | v1 明确不处理 |
| 4 | **含 OBJ 网格的模型不支持** | `isCubeOnly()` 为假时直接不建方案 | 限制 |
| 5 | **不支持 N:N** | 需要时拆成多条组 | 校验层明确报错 |
| 6 | **未映射的目标 Cube 在过渡期不出现** | 到下一关键帧才随目标模型出现，会有突兀跳变 | 尽量做完整映射 |
| 7 | **重叠面只做到确定性，不做到无缝** | 克隆起点 / 汇聚终点的重合面在 GL_LEQUAL 下顺序确定、不闪烁；但**近乎重合**的表面仍可能在极窄进度带内 z-fighting | 已用错峰（0.12）与末端抑制（0.98）缓解 |
| 8 | **贴图混合按"同一 UV 坐标"逐像素** | 源与目标 atlas 布局差异越大，中间帧越像"两张图叠在一起"；靠顶点 UV 插值收敛到各自目标区域 | 设计取舍 |
| 9 | **镜像 / 零缩放 / 剪切 / 退化 → 逐顶点线性插值** | 这类情形中间帧不保证刚体性（可能"软"） | 安全优先的取舍 |
| 10 | **修改关键帧模型后需重新配置** | 记录的模型 ID 与当前不符时静默不形变 | 防止错配 |
| 11 | **每帧清空状态的遍历开销** | `FormProperties.apply` 每帧递归遍历所有 part 清空状态，**即使 Morph 全程关闭**也存在这一常数级遍历 | 微开销，见 8.2 |
| 12 | **`MorphPlanCache` 只按配置哈希失效** | 模型文件内容变化只有靠 F6 / 文件监视器触发 `invalidateAll` 才失效 | 已有重载钩子覆盖 |
| 13 | **「自动匹配」是全覆盖的** | 每次点都会替换现有全部映射组，手工映射会被清掉 | 先自动匹配再手工微调 |
| 14 | **编辑器无独立撤销栈** | 编辑器内的改动即时回写关键帧，撤销依赖影片编辑器自身的撤销系统 | 设计取舍 |
| 15 | **无法在编辑器内改源/目标模型** | 模型由关键帧面板按时间轴推导 | 需回到面板改关键帧模型后再重新映射 |
| 16 | **编辑器交互有限** | 无撤销/拖拽配对/右键菜单/按材质或面级映射/预览相机重置；关闭后不保留展开与搜索状态 | 见 9.4 末尾清单 |

---

## 12. 后续扩展建议

1. **高级融合（Spatial Assembly / Primary Merge）**：把"重合面只保证顺序确定"升级为"面级融合/去重"，
   消除窄带 z-fighting 与汇聚末端的双重表面。这是本功能最值得做的下一步。
2. **LabPBR 伴随贴图混合**：`_n` 需要按切线空间正确插值（不能线性 lerp 法线），`_s` 可线性；
   建议复用现有 `TextureTweenSourceCache` 的源贴图缓存与显存预算机制。
3. **焊接模型支持**：需要在焊接附件的生命期内生成过渡几何，或把形变结果烘焙进附件的局部空间。
4. **骨骼级映射（组级 1:N）**：当前映射的最小单位是 Cube；按骨骼映射可减少大模型的配置量。
5. **进度曲线**：目前进度段内线性（因 Model 轨工厂是阶梯型）。若要让用户对过渡做缓入缓出，
   建议在 `ModelMorphData` 里加一个"进度曲线"字段，而不是去改 BBS 的模型值工厂。
6. **编辑器内预览已有实现**：当前预览是"把 `ModelMorphState` 写到临时表单、再走正式渲染管线"，
   因此所见即所得，编辑器的预览也走同一条真实渲染管线；
   如果后续要做"多进度并排缩略图"，可以直接用它。
7. **N:N 的可选支持**：只允许"按 key 前缀自动配对"的受限 N:N，仍保持每条边可预测。
8. **1.21.11 移植**：`morph` 包只依赖 JOML 与 BBS 的模型/渲染类，主要工作量在
   `CubicCubeRenderer`（1.21 已换 RenderPipeline）与 `ModelInstance#isVAORendered`（1.21.11 可能无此方法）。

---

## 13. 测试矩阵

**状态定义**（本表严格遵守）：

- ✅ **已通过**：有可复现的命令输出或测试报告作为证据。
- ❌ **失败**：有证据证明不通过。
- 🟡 **待验证**：实现已存在，但缺少运行证据，需要人跑一次。
- ⬜ **未测试**：尚未验证，或所需依赖/构建尚未就绪。

> 本文的目标范围是 1.20.x；1.21.x 不在本功能审查与验证范围内。当前 1.20.1 / 1.20.4 构建与测试结论见 13.1、13.2。
> 原有客户端启动冒烟见 13.4；本轮新增的 `ModelFormRendererMorphMixin` 尚未做启动验证。
> **游戏内人工场景（13.3 的 20 项）全部未测**，不得在发布说明中声称已验证。

### 13.1 构建与静态检查

| 项 | 命令 / 方式 | 状态 | 证据 / 说明 |
| --- | --- | --- | --- |
| 1.20.1 编译通过 | `bash gradlew compileJava --offline` | ✅ 已通过 | BUILD SUCCESSFUL，仅剩既有的过时 API 提示 |
| 1.20.4 编译回归 | `bash gradlew compileJava -Pmc_version=1.20.4 --offline` | ✅ 已通过 | BUILD SUCCESSFUL |
| 1.20.1 完整构建 | `bash gradlew build1201 --offline` | ✅ 已通过 | 产物 `build/1.20.1/libs/BBS-Cubed-3.6.9+1.20.1-1.20.4.jar`；包含 Model Morph class 与 10 条 mixin 登记 |
| 单元测试（gradle 路径） | `bash gradlew test --offline` | ✅ 已通过 | 16 个测试类 / 128 例，128/128 通过、0 失败（见 13.2） |
| Morph 关闭时渲染路径不改动 | 静态审查 | 🟡 待验证（静态） | 三处渲染注入点均有"上下文为空 / 状态未激活 → 直接 return"守卫；仍需实机对比截图 |
| 新增依赖仅限测试 | 静态审查 `build.gradle` diff | ✅ 已通过 | 仅 `testImplementation` 增加 `junit-jupiter:5.11.4`、`junit-platform-launcher:1.11.4` 与（测试路径用的）BBS jar、`joml:1.10.5`；主代码零新依赖 |

### 13.2 单元测试（数学 / 校验 / 自动匹配）

测试源码：`src/test/java/gbeic/bbsplusplus/client/morph/`、`client/ui/morph/` 与 `mixin/morph/`，共 **16 个类 / 128 个 `@Test`**。

| 测试类 | 用例数 | 覆盖内容 | 状态 |
| --- | --- | --- | --- |
| `MorphMathTest` | 18 | 安全求逆（null/NaN/Inf/奇异拒绝）、`inverseOrNull`、`M*M⁻¹=I`、`decompose↔compose` 往返、镜像 det<0 时 `scale.x` 取负、零缩放/剪切/非有限拒绝、`normalMatrix`（纯旋转=自身转置；奇异→单位阵）、`safeNormalize` 兜底、slerp 最短路径与端点、`Trs.lerp` 端点/中点、`boneFrame`（中性=单位阵 / pivot 不动 / 先转后缩 / 父帧组合）、`cubePivotFrame`、`clamp01`（含 NaN/Inf→0） | ✅ 已通过 |
| `ModelMorphSkeletonTest` | 4 | `CubeSpec.displayName()` 回退、`signature()` 精确格式与确定性、与 `ModelMorphAdapter.signature()` 一致、`byPath/cubesByKey/cubeCount/isEmpty` | ✅ 已通过 |
| `MorphMappingGroupTest` | 10 | 1:1/1:N/N:1/N:N/EMPTY 推导、标签、`isSupported()`、按 key 去重（先到先得）、空/null 引用拒绝、`copy()` 深拷贝与相等、空 ID 自动生成、不可变视图 | ✅ 已通过 |
| `MorphTreeFilterTest` | 3 | 空组识别、过滤不漏 Cube、全空骨架不产生行 | ✅ 已通过 |
| `MorphAlphaTest` | 7 | alpha 平滑插值、透明源不污染颜色、颜色连续渐变、translucency 扫描阈值 | ✅ 已通过 |
| `MorphDataHashTest` | 6 | 数据和映射组结构变化会使缓存哈希失效 | ✅ 已通过 |
| `MorphScreenPicksTest` | 22 | 投影面命中深度、遮挡优先级、完整组路径、重名 Cube 序号、采集生命周期、Cube Pivot 旋转角点 | ✅ 已通过 |
| `MorphPreviewModelSelectionTest` | 6 | 100% 进度切到目标模型、缺源/目标模型的预览回退、同一模型对切换端点保留视角 | ✅ 已通过 |
| `MorphRenderContextTest` | 4 | 纹理开关传递、UV 区域缓存键、嵌套普通渲染隔离、表单作用域恢复 | ✅ 已通过 |
| `MorphValidationTest` | 16 | 完整映射可播放、禁用数据不可播放、`allowIncomplete` 对可播放性的影响、冲突组、N:N 拒绝、未映射统计与警告、源模型 ID 变化报错、1:N / N:1 展开、空骨架、不可播放原因可读性（`fallbackReason()` 两个回归用例） | ✅ 已通过 |
| `MorphAutoMatchTest` | 7 | 同 key、异骨骼名/平移/缩放时的空间匹配、数量差异生成 1:N/N:1、全局分配优于逐个贪心、生成结果通过完整映射校验 | ✅ 已通过 |
| `ModelMorphStateTest` | 8 | 默认未激活、激活条件（开关/双模型/同模型重组/进度>0）、进度钳制、`clear()` 只清瞬时状态且键保留、配置变化使缓冲失效、无数据安全返回 | ✅ 已通过 |
| `MorphSmoothnessTest` | 5 | 克隆分离连续性、汇聚末段抑制、超采样保留源面细节 | ✅ 已通过 |
| `MorphTextureCanvasTest` | 5 | 源内容落到目标矩形、目标矩形用该面专属源内容、画布等于目标图集尺寸、退化区域跳过、无区域仍可用 | ✅ 已通过 |
| `MorphInverseBindTest` | 6 | 起点/终点包围盒、UV 固定、几何形变、零尺寸轴安全退化、剪切回退保留端点矩阵 | ✅ 已通过 |

**执行方式与证据**：`bash gradlew test --offline` 实际执行，128/128 通过、0 失败。

需要区分运行时依赖：

| 分组 | 用例 | 运行时是否需要 BBS |
| --- | --- | --- |
| 纯数学 / 快照测试 | 部分用例 | 否（纯 JDK + JOML） |
| 映射、纹理缓冲与 BBS 模型测试 | 部分用例 | **是**：这些类会触发 BBS `MapType`、`ModelCube` 等类型的链接校验 |

> 这两点已在 `build.gradle` 里补齐：`testImplementation files(mcVer.bbs_jar)` 与 `testImplementation "org.joml:joml:1.10.5"`，
> 因此 `gradlew test` 可以直接跑通，不需要手工拼类路径。

**未覆盖**：没有序列化往返测试（`ModelMorphData.toData/fromData` 需要 `MapType`，为保持"不加载 BBS"而刻意避开）；
没有 `MutationCube` 端到端几何测试（依赖 `ModelCube`/`ModelQuad`，属游戏内验证范围）。

### 13.3 游戏内人工场景

| # | 场景 | 预期 | 状态 |
| --- | --- | --- | --- |
| 1 | Bed → Boat 基础形变 | 床位逐渐长成船形，两端无跳变 | ⬜ 未测试 |
| 2 | Player → Block | 人形收敛为方块，UV 收敛正确 | ⬜ 未测试 |
| 3 | 1:N 克隆分化（一源多目标） | 克隆体依次错峰分离，起点不明显重叠 | ⬜ 未测试 |
| 4 | N:1 完整汇聚（多源一目标） | 末端不闪、不重影（0.98 抑制生效） | ⬜ 未测试 |
| 5 | 时间轴拖动（手动 scrub 来回） | 任意位置画面与进度一致，无残影 | ⬜ 未测试 |
| 6 | 倒放播放 | 形变反向正确 | ⬜ 未测试 |
| 7 | 循环播放 | 每轮都从段起点重新开始，无漂移 | ⬜ 未测试 |
| 8 | 保存后重载影片 | 映射与开关完整恢复 | ⬜ 未测试 |
| 9 | 撤销 / 重做（改映射后） | 撤销能恢复旧映射（依赖 `equals` + `copy` 注入） | ⬜ 未测试 |
| 10 | 复制粘贴关键帧 | 粘贴出的关键帧带同样 Morph 配置 | ⬜ 未测试 |
| 11 | 同一模型两个回放实例 | 只有一个开 Morph 时另一个不受影响 | ⬜ 未测试 |
| 12 | Morph 关闭回归 | 画面与未开启时完全一致 | ⬜ 未测试 |
| 13 | 视频导出与预览一致 | 导出帧与编辑器预览一致 | ⬜ 未测试 |
| 14 | 多选关键帧批量改映射 | 选中项全部更新 | ⬜ 未测试 |
| 15 | 贴图同步关闭 | 只形变几何，贴图保持源 | ⬜ 未测试 |
| 16 | 贴图尺寸不同（如 64×64 → 128×128） | 最近邻重采样后正常混合，无错位 | ⬜ 未测试 |
| 17 | F6 资源重载 | 方案与混合贴图缓存失效，不沿用旧几何 | ⬜ 未测试 |
| 18 | 含 OBJ 网格的模型 | 静默回退硬切，不崩 | ⬜ 未测试 |
| 19 | 焊接附件模型 | 静默回退硬切，不崩 | ⬜ 未测试 |
| 20 | 映射冲突（同一 Cube 在两个组） | 摘要显示问题，回退硬切 | ⬜ 未测试 |

### 13.3.0 联调中发现并修复的真实缺陷

以下三个问题**不是设计推演**，而是在 `runClient1201` 实际播放测试影片时暴露出来的：

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 形变期间每帧一条 `NullPointerException: buffer is null` | `Texture.uploadTexture(Pixels)` 会在上传后 `delete()` 掉传入的 `Pixels`，而混合缓冲区要跨帧复用，第二帧起就是空缓冲 | 改用 `Texture.updateTexture`（`glTexSubImage2D`，不接管所有权），并给失败路径补上纹理/缓冲区释放与失败缓存 |
| 2 | 映射不可播放时日志只有「不可播放：」后面空无一物 | 映射合法但不完整、且用户关掉「允许不完整映射」时 `errors` 为空 | 新增 `ModelMorphValidation.Result.fallbackReason()`，把「没有映射组 / 不完整且开关关闭」写成可读原因 |
| 3 | 一个不可播放的映射会每帧重建骨架、跑校验并刷日志 | 形变状态每帧 `clear()` 后重新解析，失败结果没有记忆 | `MorphPlanCache` 增加失败键缓存（资源重载时清空）；同时把 `ModelMorphState.clear()` 改为只清瞬时状态，采样缓冲区按 (源, 目标, 配置) 复用，避免每帧重新分配顶点数组 |

另外修正了一处 `@Shadow` 可见性：`CubicCubeRenderer.captureOnly` 在 BBS 里是 `private`，镜像成员必须同可见性。

#### 第三轮联调（用户反馈：最后一帧闪源模型 / UV 不可控偏移）

两个问题同源：都是**区间端点**处理不当。

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 6 | 形变最后一刻闪回一帧源模型 | `MorphTextures.blend()` 在 `step >= STEPS`（进度≈1）时返回 `null`，mixin 于是不绑定纹理，沿用渲染器先前绑定的**源贴图**；此时几何已是目标形状、贴图却还是源的 | 端点改为显式返回对应一侧的**真实**纹理（`textureOf(source)` / `textureOf(target)`），不再返回 `null` |
| 7 | 形变中 UV 出现不可控偏移 | 早期实现无条件在源/目标 UV 之间线性插值 | **彻底移除 UV 插值**：UV 全程固定采样源区域（见下方「UV 语义」） |

##### UV 语义（重要设计约束）

**UV 是采样坐标，不是空间量。** 正常动画里缩放骨骼、移动 Cube 都只改变几何，
UV 矩形原地不动——所以形变期间 UV 也绝不能移动。

跨模型时两个图集布局毫无关系，任何 UV 插值的中间值都会扫过图集上的无关区域，
表现为贴图内容乱跳与缺面。因此：

- **UV 恒定不变**：`MutationCube.writeUv()` 只写源 Cube 的 UV，与进度无关。
- **内容过渡交给贴图**：`MorphTextures` 按 `UvRegion` 把目标图集里每个面的内容
  **重映射进源图集对应的矩形**，再与源内容交叉淡化。这样 UV 不动、内容连续。
- 该行为不是可选项：早期曾提供一个 `uvLerp` 开关，后被判定方向错误而删除
  （UV 固定是硬性语义，留开关只会诱导用户产生不可控效果）。

区域重映射的验证（源像素取到目标区域内容、区域外不被改动）：

```
面0 源(0,0) 目标(4,0)
  源像素 (0,0) 重映射后 = FF000400, 期望取目标 (4,0) 的 FF000400
  重映射生效: true
  区域外未改动: true
```

已知限制：一对多克隆时多个目标共享同一个源 UV 矩形，而一个纹理像素无法同时呈现
多个内容，此时只有主过渡体的内容参与重映射。

#### 第四轮联调（用户反馈：鼠标影响光照 / 终点 UV 严重错误 / 纹理不跟随目标）

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 8 | 播放头停在形变中间时，**移动鼠标会改变形变几何的光照** | 形变几何混进了模板拾取通道。BBS 靠逐骨骼写入拾取索引做肢体高亮/选择，鼠标移动会重绘拾取通道；形变几何的面数与骨骼归属都和真实几何不同，索引错乱 → 光照/高亮随鼠标变化 | `beginRender` 在拾取通道（`stencilMap != null`）直接返回 `null`，拾取一律使用真实几何 |
| 9 | **最后一帧 UV 严重错误**、纹理不跟随目标 | 进度 1 时 `blend()` 返回**原始目标图集**，但 UV 已固定为**源区域** → 用源 UV 采目标图集，取到完全错误的位置 | 移除终点特判：终点同样使用**重映射后的目标内容**（目标各面搬进源区域的那张图），与固定 UV 自洽 |
| 10 | 形变过程中纹理与目标不完全一致 | 同上（#9 的中间态表现） | 同上 |

第 9/10 条是同一个 bug，也说明了一个自洽性要求：

> **UV 固定 ⇒ 贴图必须按「源区域布局」提供内容。**
> 只要有一帧返回原始目标图集，源 UV 就会采到目标图集的错误位置。
> 因此终点不再是"切回目标图集"，而是"重映射图在进度 1 时正好等于目标内容"。

重映射单元测试（`MorphUvRemapTest`，5 例）锁定：目标区域被搬进源区域、区域外不被改动、
无区域时安全退化、退化区域被跳过、重映射确实改变了内容。

#### 第五轮联调（用户反馈：鼠标移动仍影响光照 / 终点贴图发糊）

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 11 | 鼠标在界面<b>任何位置</b>移动，形变几何光照都变 | `isVAORendered()` 在 BBS 里同时决定<b>着色器选择</b>，而它在 `render()` <b>之前</b>被调用。此前用「渲染上下文是否已压栈」判断，那时栈还是空的，于是着色器走一支、几何走另一支，光照随渲染次序抖动 | 新增 `ModelMorphRuntime.isMorphing(instance)`，直接按表单状态判定，不依赖渲染上下文；`isVAORendered()` 改用它，保证着色器选择与几何提交始终一致 |
| 12 | 终点贴图明显发糊、对比目标显得突兀 | 重映射前先把目标图集整体缩放到源图集尺寸，再压进源矩形 = 对目标做了<b>两次降采样</b>（例：船 128×64 先缩到 64×64 再重映射） | 重映射改为按<b>目标图集自身尺寸</b>换算像素：`remapIntoSource(source, sw, sh, target, tw, th, regions)`，目标内容以原生分辨率采样一次到位 |

第 12 条的量化依据：船的 `bottom` 面在图集里占 28×3 px，床的 `head` 面矩形是 16×16 px，
内容被非等比拉伸本身就损失细节；再叠加一次预缩放，终点必然发糊。

新增测试 `targetIsSampledAtItsOwnResolution` 锁定「目标图集比源大时必须按目标原生尺寸采样」，
并验证区域外保留源内容（否则模型其它部分会被目标内容污染）。

#### 第六轮联调（用户反馈：贴图<b>全程</b>不清晰，不只是最后一帧）

这一轮推翻了我之前两版的贴图方案，也修正了需求理解。

**关键澄清**：不是最后一帧糊，而是**从源 Cube 到目标 Cube 的整个过程贴图都不清晰**。

**根因（用真实模型数据实测）**：把目标内容塞进源 UV 矩形时，两侧矩形形状完全不同：

| 源矩形（bed, 64×64） | 目标内容（boat, 128×64） | 横缩放 | 纵缩放 |
| --- | --- | --- | --- |
| head/front 16×16 | front/front 16×6 | 1.00× | **2.67×** |
| head/right 6×16 | front/right 2×6 | **3.00×** | **2.67×** |
| head/top 16×6 | front/top 16×2 | 1.00× | **3.00×** |

把 6 行内容拉成 16 行，细节必然糊；而且它**全程参与混合**，所以整个过程都不清晰。

**这是三个要求在跨模型时不可能同时满足的矛盾**：

- 要求 A：UV 固定不动（正确，符合骨骼动画直觉）
- 要求 B：全程贴图清晰
- 要求 C：终点显示目标贴图内容

UV 固定在源区域时，要显示目标内容就必须把目标内容压进源区域 → 矩形形状不同 → 必然拉伸。

**最终取舍**（优先保证 B，即用户明确指出的"贴图必须清晰"）：

- **只有两侧 UV 布局一致时才做逐像素混合**（同一图集布局 → 1:1 采样 → 完全清晰）
- 布局不一致时**不做任何重映射与混合**，直接保持源贴图：几何照常形变，贴图全程清晰，
  终点由 BBS 自己的模型切换接上真实目标模型与目标贴图

判定实现为 `MorphTextures.isLayoutCompatible(regions)`：逐面比较源/目标矩形尺寸，
允许 1 像素取整误差；任一面不兼容即整体放弃混合（避免部分面清晰、部分面糊）。

真实模型验证：

```
真实模型场景（bed head 16x16x6 -> boat front 16x6x2）
  UV 区域数: 6
  布局兼容: false
  => 混合已禁用（保持源贴图，全程清晰）
```

#### 第七轮：改为目标驱动（逆向绑定），彻底解决贴图模糊

前六轮反复在「以源 Cube 为基准」的框架里打补丁，方向本身就是错的。用户指出的正确思路是：

> 把目标模型的块看作一个组（骨骼），对它做缩放变换时纹理显示完全正常。
> 所以先把目标块缩放/变换到与源块完全对齐，再通过形变过程恢复目标块即可。

这就是任务书里写的**目标驱动逆向绑定**，而且它天然解决贴图问题：

| 方案 | 基准 | UV | 贴图 |
| --- | --- | --- | --- |
| 旧（错误） | 源 Cube | 源 UV 固定，需把目标内容塞进源矩形 | 矩形宽高比不同（16×16 vs 16×6）→ 拉伸 2.67× → **全程糊** |
| 新（正确） | **目标 Cube** | **目标 UV 固定**，全程不动 | 采样目标图集原生区域 → **全程清晰** |

实现要点（`MutationCube.build`）：

```
bind      = translate(sourceMin - targetMin*scale) * scale(sourceSize/targetSize)
startPose = sourceFrame * bind     // 目标块被缩放成源块形状
endPose   = targetFrame            // 目标块自己的姿态
```

- 顶点始终由**目标 Cube 的包围盒**生成，UV 取**目标 Cube 自己的 UV**，全程不变。
- 贴图只绑定目标图集，**不做任何逐像素混合或 UV 区域重映射**（相关代码已整体删除）。

数值验证（真实场景 bed head 16×16×6 → boat front 16×6×2）：

```
t=0 包围盒  min(-0.500,-0.438,1.000) max(0.500,0.563,1.375)
     源Cube期望 min(-0.500,-0.438,1.000) max(0.500,0.563,1.375)   <- 完全一致
t=1 包围盒  min(0.000,0.438,-1.000) max(1.000,0.813,-0.875)      <- 目标形状
UV 全程不变: true
UV U 范围 [0.000, 0.266]（落在目标图集区域内）
```

新增 `MorphInverseBindTest`（5 例）锁定：起点包围盒等于源 Cube、终点等于目标 Cube、
UV 全程不动、几何确实形变、零尺寸轴安全退化。

#### 第八轮：贴图在源帧之后错乱 / 光照随鼠标变化

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 13 | 鼠标移动时光照不稳 | `ModelInstance.form` 是**共享资源上的 public 可变字段**，被 BBS 渲染器每帧写入；UI 预览与回放共用同一 `ModelInstance` 时会互相覆盖。`isMorphing()` 依赖它判断，于是鼠标移动触发预览重绘 → 字段被换成预览表单 → 判定翻转 → 着色器切换 → 光照变 | `isMorphing()` 不再读 `instance.form`，改为遍历登记的活跃表单集合，只看表单**自身**的形变状态；渲染作用域与贴图绑定也从实例字段改为线程内上下文栈 |
| 14 | 源模型帧之后连续几刻贴图严重错乱 | 进度接近 0 时 `step` 量化到 0，代码返回**原始源图集**；但过渡体顶点用的是**目标 UV**，于是用目标 UV 去采源图集上完全无关的位置 | 进度 0 不再返回原始源图集；始终使用「源内容已映射进目标区域」的画布——它在进度 0 时恰好等于源模型外观 |

第 14 条的关键认识：**几何用什么 UV，就必须绑定与之匹配的图集布局**。
过渡体用目标 UV，因此任何时刻都不能绑定原始源图集。

画布验证（真实 bed→boat 数据，进度 0 的画布）：

```
把 bed 内容按面映射进 boat 图集的对应矩形后，
画布上仍能看到 bed 的木纹、白床单与红床单 —— 即进度 0 的外观等于源模型。
```

新增 `MorphTextureCanvasTest`（5 例）锁定：源内容落到目标矩形、目标矩形使用该面的
专属源内容、画布尺寸等于目标图集、退化区域跳过、无区域时仍产出可用画布。

#### 第九轮：首帧突兀 + 影片报「target model changed」

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 15 | 源模型帧之后第一刻几何形变显得突兀 | 两处叠加：<br>① 克隆体用固定进度阈值（localProgress ≥ 0.06）判定是否绘制，会在某一 tick 从「不画」直接跳到「画」；<br>② 源内容被压进比它更小的目标 UV 矩形（实测 bed 面 16×16、boat 面 16×6），纵向 16 行只剩 6 行，分辨率不可逆丢失 | ① 可见性改用<b>几何位移</b>判定：位移从 0 连续增长，越过 1/4096 模型单位（远小于一像素）的时刻本身连续；错峰跨度也从 0.35 降到 0.12，分化从第一刻展开<br>② 画布按目标图集<b>超采样 4 倍</b>存储，目标矩形像素变多，足以容纳源面全部细节 |
| 16 | 影片提示「无效: target model changed; revalidate mapping」 | 模型 id 漂移被当作<b>错误</b>，直接阻塞播放。但映射记录的是 Cube 的稳定 key 与几何指纹，只要这些仍能解析，形变就能正常工作 | 降级为<b>警告</b>，并说明重新打开编辑器可刷新记录的名字；运行时也不再因 id 过期而停止形变 |

超采样的量化效果（真实 bed→boat 数据，对比 t≈0 的形变几何与真实源模型）：

| 超采样 | 画布尺寸 | 平均通道差 |
| --- | --- | --- |
| 1× | 128×64 | 17.0 / 255 |
| 4× | 512×256 | **0.0 / 255** |

UV 是归一化坐标，画布放大不改变采样结果，只是让每个面的目标矩形有足够像素容纳源细节。

新增 `MorphSmoothnessTest`（4 例）锁定：重合阶段不绘制、开始分离后立刻可见且不再消失、
汇聚在终点前完成、超采样画布保留源面全部行细节。

#### 第十轮：换模型后旧映射组失效，报错难以理解

用户换掉源模型后，界面显示：

```
无效: source cube not found: front:#0    source cube not found: left:#0
```

**这不是 1:N 未实现**（1:N 早已实现，见 `MappingGroup.Type.ONE_TO_MANY` 与
`MorphValidationTest.oneToManyExpandsToTransitionsSharingOneSource`）。真正原因是：
旧映射组里存的是<b>上一个模型的骨骼名</b>（`front`/`left` 来自 boat），换模型后这些
key 自然解析不到。

原来的报错有两个问题：

1. 逐条列出内部 key（`front:#0`、`left:#0`…），用户无法判断该怎么办；
2. 后面还会追加一条 `empty or invalid mapping group <uuid>`，进一步干扰判断。

改为：

- **任一侧整组都解析不到**时，折叠成一条可操作提示：
  `mapping group <id> references cubes that no longer exist (...); clear the mapping and re-create it`
- **个别 key 写错**时保留精确报错 `source cube not found: <key>`，便于定位
- 已报「引用已不存在」时不再重复追加 `empty or invalid mapping group`

新增 2 例测试：`staleMappingGroupGivesActionableMessage`、
`partiallyStaleMappingKeepsPreciseError`。

#### 第十一轮：端点一致性校验（形变目标 ≠ 区间终点模型）

用户反馈「从 cube_test 形变为 steve 时纹理渐变有些突兀」。解包 `dwadwadwa.dat`
（`BBS1` + `DataStorage` 格式）逐帧核对，确认**不是渐变算法的问题**，而是映射数据本身错了。

model 轨道实际内容：

| tick | 关键帧 value | morph.target_model | enabled | 插值 |
| --- | --- | --- | --- | --- |
| 8 | `model_morph_test/bed` | `model_morph_test/boat` | 1 | exp_out |
| 39 | `model_morph_test/boat` | `cube` | 0 | exp_out |
| 58 | `model_morph_test/boat` | `cube` | **1** | constant |
| 92 | `model_morph_test/cube_test` | `cube` | 0 | constant |

区间 `[58, 92)` 有两个问题：

1. `target_model` 指向 `cube`，而 `models/cube` 目录下**只有 `config.json`**，
   没有 `model.bbs.json`、也没有贴图——是个空模型；
2. `cube` ≠ `cube_test`，形变几何长成 A 的形状、纹理采样 A 的贴图，区间结束却硬切到 B。

另外 `cube_test` 的贴图是**纯白**（4096 像素全为 `255,255,255`），而 `boat` 的贴图有 8 种颜色。
从木船渐变到纯白方块，中途突兀是必然结果，与渐变实现无关。

**新增校验**：`ModelMorphValidation.validate(..., String nextModelId)` 在形变目标与
区间终点模型不一致时追加警告（不阻塞播放）：

```
morph target (<recorded>) differs from the model the interval ends on (<next>);
the transition will not match its endpoint
```

`nextModelId` 由关键帧区间的下一个 `value` 取得，同时接入：
- 运行时 `ModelMorphRuntime.update` → `ModelMorphState.set` → `MorphPlanCache.get`
- 编辑器摘要 `bbsppp$summary`，打开面板即可看到

新增 2 例测试：`morphTargetMustMatchTheIntervalEndpoint`、`matchingEndpointProducesNoMismatchWarning`。

#### 第十二轮：cube_test → steve 渐变突兀（alpha 阈值跳变）

用户明确指出是 **`cube_test` 形变到 `player/steve`**，中间能逐帧对比出渐变突兀。
重新解包 `dwadwadwa.dat`（影片已被修改），拿到该区间的真实数据：

| tick | value | morph.target_model | enabled | 映射 | 插值 |
| --- | --- | --- | --- | --- | --- |
| 3 | `model_morph_test/bed` | `model_morph_test/boat` | 1 | 6 组 | exp_out |
| 20 | `model_morph_test/boat` | `cube` | 1 | 9 源→1 | exp_out |
| 29 | `model_morph_test/boat` | `cube` | 1 | 9 源→1 | exp_out |
| 47 | `model_morph_test/cube_test` | `cube` | 1 | 1 源→12 | exp_out |
| 54 | `model_morph_test/cube_test` | `cube` | 1 | 1 源→12 | exp_out |
| 79 | `player/steve` | `cube` | 0 | — | constant |

**根因（量化确认）**：两张贴图的 alpha 分布差异巨大——

| 贴图 | 尺寸 | 颜色数 | 全透明像素 |
| --- | --- | --- | --- |
| `cube_test/model.png` | 64×64 | 1（纯白） | **0** |
| `player/steve/steve.png` | 64×64 | 11 | **2464 / 4096（60%）** |

旧实现把 alpha 线性插值后按 cutout 阈值二值化成 0/255。于是跨过 128 的那一级：

| 进度 | 不透明像素数 | 与上一级变化 |
| --- | --- | --- |
| 0% ~ 25% | 1632 | 0 |
| **50%** | **4096** | **+2464** |

**2464 个像素在同一级整块出现**，这就是肉眼可见的突兀。

**修复**：alpha 直接取**目标**像素的 alpha，不插值、不混合。

理由是语义正确：目标驱动方案下，过渡体的形状与 UV 都属于目标模型，那么「这块像素
属不属于该模型」（alpha）也应当属于目标模型。颜色负责视觉过渡，alpha 负责定义形状边界。

修复后同一测度：

| 实现 | 单级最大不透明像素跳变 |
| --- | --- |
| 旧（alpha 插值 + 二值化） | **2464** |
| 新（alpha 取目标值） | **0** |

新增 `MorphAlphaTest`（3 例）锁定：alpha 全程等于目标像素、颜色仍连续渐变、
alpha 在任意相邻两级之间不跳变。

后续实现已取代这一轮的 target-alpha 方案：当前代码对 alpha 做连续插值，并在通过
`glTexSubImage2D` 更新纹理后同步 `Texture.translucent`，由 BBS 的半透明队列绘制过渡值。
当前回归用例见上方 `MorphAlphaTest`（7 例）；交接说明中的 4.2 节记录的是现行行为。

#### 第十三轮：3D 预览可视化选块

**问题**：编辑器里只能在树里看到 `bone/cube_3` 这样的名字，用户无法判断它对应模型上的哪一块，
只能靠猜。参考 BBS 模型编辑器的做法，改为支持直接在 3D 预览里点选。

**技术选型：渲染期屏幕投影缓存，而非 BBS 的模板拾取（stencil）**

| 方案 | 能否精确到 Cube | 对渲染的影响 |
| --- | --- | --- |
| BBS `StencilMap` 模板拾取 | **不能**：索引写的是 `ModelGroup.index`，粒度是**骨骼** | 会替换渲染状态，与形变几何的顶点提交相互干扰 |
| **渲染期屏幕投影缓存**（采用） | **能**：逐个 Cube 的真实面投影，按鼠标处深度命中 | 不替换 GL 状态，也不重复推导相机射线 |

`StencilMap.addPicking(Form, String)` 的第二个参数是骨骼名，`CubicCubeRenderer` 里写入的是
`group.index`，因此模板拾取天然做不到 Cube 级——这是采用渲染期投影缓存的决定性原因。

**实现**（由 `MorphScreenPicks` 与渲染器 Mixin 协作）：

- `CubicCubeRendererMorphMixin`：在每个 Cube 实际渲染时记录投影面、深度、完整骨骼路径与 Cube 序号
- `MorphScreenPicks.pick(view, model, x, y, padding)`：按鼠标位置先选真实面深度，再以屏幕面积作稳定 tie-break
- `MorphScreenPicks.Box`：保存 3D 棱边投影，供 2D UI pass 画高亮线框

采集使用渲染栈已经包含的模型矩阵，只补相机投影矩阵；因此屏幕拾取与实际画面使用同一套坐标链。

**交互**：

- 预览区支持**点击选块**，命中后同步选中对应侧的列表行；点空白处清空该侧选择
- 当前选中的 Cube 在预览里画**黄色包围盒线框**，用户能立刻确认点的是哪一块
- 新增**「预览：源模型 / 目标模型」切换按钮**：两侧骨骼结构通常完全不同，必须能分别对着看、对着点
- 命中的 Cube 若藏在折叠的骨骼里，会自动展开再选中

**测试**：相关回归集中在 `MorphScreenPicksTest`，覆盖投影面命中深度、遮挡优先级、完整组路径、
重名 Cube 序号、采集生命周期和 Cube Pivot 旋转角点。

#### 第十四轮：两个独立可拾取面板 + 过滤空组

上一轮把拾取做进了右下角的形变预览，但那块地方是用来检查过渡效果的，不该兼作选块。
本轮改为**左右各一个独立的可拾取 3D 面板**，并把面板整体放大到 1000×660。

**布局**：左侧「搜索 → 源模型拾取面板 → 源树」，右侧「搜索 → 目标模型拾取面板 → 目标树」，
右下角仍是形变预览。视线动线从上到下：先看到模型、点中目标块，再在下方列表里确认。

两个拾取面板只显示**静态模型**，不叠加形变——选块需要看清每个方块的真实形状，
形变会让轮廓变糊、反而点不准。

**关于「复用模型编辑器的选块」**：查证后确认 BBS 模型编辑器的 `UIModelEditorRenderer`
虽然有 `setCubePicking(true)` / `onPick` / `highlight`，但它的拾取粒度实际是**骨骼**——
`cubePicking` 分支里调用的是 `hoveredGroup(Model)`，返回 `ModelGroup` 而非单个 Cube；
而且它依赖 `ModelSlotTarget`（gizmo 编辑状态）、`StencilFormFramebuffer` 等内部类型，
无法脱离模型编辑器上下文使用。因此继续使用独立的 `MorphScreenPicks`，它在正常渲染期
记录真实 Cube 面并提供 Cube 级拾取。

**过滤空组**：不含 Cube 的组只承载层级关系，用户无法对其做任何映射操作，因此直接从树里跳过。
实测效果：

| 模型 | 组数 | 空组 | 有 Cube 的组 | Cube 数 | 过滤前 | 过滤后 | 减少 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `player/steve` | 37 | **30** | 7 | 12 | 49 行 | 19 行 | **61%** |
| `player/alex_3d` | 37 | **30** | 7 | 1510 | 1547 行 | 1517 行 | 2% |

`player/steve` 这种典型人形模型里，空组占了 81% 的组数——正是用户说的「对我们没有任何用途」。

**测试**：`MorphTreeFilterTest`（3 例）覆盖空组识别、过滤不漏 Cube、全空骨架不产生行。

### 13.3.1 注入点加固（`require = 1`）

BBS-Cubed 的 mixin 配置是 `defaultRequire: 0`，注入点找不到目标方法时**静默跳过**。
Model Morph 的 22 个 `@Inject` 全部显式写了 `require = 1`：一旦 BBS 升级后方法签名变化，
启动阶段就会直接抛 `InvalidInjectionException`，而不是让功能"看起来装了其实不形变"。
此前的 `runClient1201` 冒烟覆盖了旧注入集；新增的 `ModelFormRendererMorphMixin` 仍需启动验证。

### 13.4 接入状态、构建证据与风险

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 全屏编辑器类存在并且签名一致 | ✅ 已通过 | `client/ui/morph/UICubeMappingEditorPanel.java`，由 `UIModelKeyframeFactoryMorphMixin` 调用 |
| 10 个 Morph mixin/accessor 全部登记 | ✅ 已通过 | `bbsplusplus.mixins.json` 的 `client` 数组含 10 条 `morph.*`，发布 jar 中也含 10 条 |
| 1.20.1 / 1.20.4 编译 | ✅ 已通过 | 两个目标 `compileJava` 均 BUILD SUCCESSFUL |
| 1.20.1 发布构建 | ✅ 已通过 | 本轮 `bash gradlew build1201 --offline` 成功产出 jar |
| 单元测试（Gradle） | ✅ 已通过 | `bash gradlew test --offline` 128/128 |
| 渲染器 / 关键帧 Mixin 启动验证 | ✅ 已通过 | 本轮 `runClient1201 --offline` 越过 Mixin 应用并完成资源加载；先前非 private static helper 已移出 Mixin。Microsoft 认证 401 与缺少 Iris/Sodium 的可选 Mixin 警告仍有记录，但客户端继续进入 BBS 初始化与资源加载 |
| 形变方案在真实影片中编译 | ✅ 已通过（游戏内） | 播放 `model_morph_test/bed → model_morph_test/boat` 时输出 `ready: 6 mapped source cube(s), 9 transition(s)`：映射解析、跨骨骼反求与过渡几何构建全部成功 |
| 贴图混合在游戏内运行 | ✅ 已通过（游戏内） | 同一场景 `texture blend failed` 计数为 0，`NullPointerException` 计数为 0 |
| 形变几何的视觉质量 | ⬜ 未测试 | 方案已编译、贴图已混合、无异常；但「形变是否好看、端点是否跳变、克隆分化与汇聚的重叠表现」需要人眼判断，见 13.3 的 20 项 |

---

## 14. 构建与验证命令

### 14.1 编译（最快反馈）

```bash
cd /path/to/BBS-Cubed
bash gradlew compileJava --offline
```

只编译主源码集（`src/main/java` + `src/main/java-1.20.x`），适合改完代码立刻验证签名是否对得上。
`--offline` 依赖本地 Gradle 缓存完整；若报缺依赖，先联网跑一次完整构建补缓存。

### 14.2 发布构建（1.20.1，单 jar）

```bash
bash gradlew build1201
```

- 这是一个 `GradleBuild` 包装任务：内部以 `-Pmc_version=1.20.1` 执行 `build`。
- 等价于 `bash gradlew build -Pmc_version=1.20.1`。
- 产物在 `build/1.20.1/libs/`（构建目录按 `targetMc` 隔离，避免跨版本污染）。

### 14.3 1.20.4 编译回归

```bash
bash gradlew build -Pmc_version=1.20.4
```

1.20.4 **只作为编译回归目标**，不单独发布。产物在 `build/1.20.4/libs/`，不进入发布。

### 14.4 单元测试

```bash
bash gradlew test --offline
```

- `build.gradle` 已加 JUnit 5（`junit-jupiter:5.11.4`）+ `junit-platform-launcher:1.11.4` + `useJUnitPlatform()`，失败时输出完整异常。
- 测试源码：`src/test/java/gbeic/bbsplusplus/client/morph/` 与 `client/ui/morph/`，16 个类 128 个用例，**已实际跑通 128/128**。
- **编译期**只需 JDK + JOML；**运行期**有 24 个用例需要 BBS 类（会触发 `BaseType` / `MapType` 链接校验），
  已通过 `testImplementation files(mcVer.bbs_jar)` 与 `testImplementation "org.joml:joml:1.10.5"` 补齐，
  所以直接 `gradlew test` 即可，无需手工拼类路径。
- 报告位置：`build/1.20.1/test-results/test/TEST-*.xml` 与 `build/1.20.1/reports/tests/test/index.html`。

### 14.5 启动开发客户端

```bash
bash gradlew runClient1201     # 1.20.1，运行目录 run/1.20.1
bash gradlew runClient1204     # 1.20.4，运行目录 run/1.20.4（编译回归用）
```

### 14.6 合并前待办

| 项 | 状态 | 说明 |
| --- | --- | --- |
| `gradlew compileJava`（1.20.1 / 1.20.4） | ✅ 已通过 | 两个目标全部 BUILD SUCCESSFUL |
| `gradlew build1201` | ✅ 已通过 | 单 jar 产物含 Morph 类与 10 条 mixin 登记 |
| `gradlew test` | ✅ 已通过 | 16 类 128 例全绿 |
| 新增 Renderer Mixin 的 `runClient1201` 启动 | 🟡 待验证 | 需关闭已有客户端后单独执行一次启动冒烟 |
| 游戏内人工场景（13.3，20 项） | ⬜ 未测试 | **合并前必须由人工完成**，尤其是 Bed→Boat、Player→Block、时间轴 scrub/倒放/循环、保存重载、双实例隔离与 Morph 关闭回归 |

---

## 15. 许可说明

### 15.1 本体许可

BBS-Cubed 以 **LGPL-3.0-or-later** 发布（`gradle.properties` 的 `mod_license=LGPL-3.0-or-later`，仓库根 `LICENSE` 为 GNU LGPL v3 全文）。
本功能的所有新增文件（`client/morph/*`、`client/ui/morph/*`、`keyframes/IModelMorphKeyframe.java`、`mixin/morph/*`）随仓库一起按 LGPL-3.0-or-later 授权。

### 15.2 Bone Anchor（MIT）的引用边界

- 本功能的**安全求逆 / 安全分解 / 镜像符号约定 / 法线退化兜底**这套防护思路，参考了 **Bone Anchor** 模组的 `AnchorMath`（**MIT** 许可）。
- 当前实现为**独立重写**：复用的是**设计思路与防护约定**（"不满足条件就拒绝/回退，绝不强行求逆"），
  没有直接复制 Bone Anchor 的源码文本；具体实现基于 JOML 与 BBS 自身的矩阵约定。
- MIT 与 LGPL-3.0-or-later 兼容。**MIT 要求保留版权声明与许可声明**，因此除了在 `MorphMath.java` 的类注释里署名，
  仓库根还提供了独立的第三方声明文件 **`LICENSES/MIT-bone-anchor.txt`**（含 MIT 全文）：

```text
The Model Morph implementation in this repository reuses design conventions
from the "BBS Bone Anchor" project (mathematical guards for matrix inversion,
decomposition and pose snapshotting). That project is distributed under the
MIT License, reproduced below in full.

----
MIT License

Copyright (c) 2026 McAxolotl
...
```

- 现状：✅ 声明文件已就位（`LICENSES/MIT-bone-anchor.txt`，28 行，含完整 MIT 正文）；
  `MorphMath.java:11` 的类注释也注明了"沿用 Bone Anchor（MIT）AnchorMath 的防护思路"。
- 建议（可选但推荐）：
  1. 在 `MorphMath.java` 注释里补一行 `See LICENSES/MIT-bone-anchor.txt for the full MIT notice`，让"代码 → 声明"可追溯；
  2. 打包发布时确认 `LICENSES/` 是否随 jar 一起分发（LGPL 要求随二进制提供许可文本，MIT 要求保留声明）。

### 15.3 文档与代码的一致性

本文按**代码现状**撰写（编辑器交互与快捷键取自 `UICubeMappingEditorPanel` 的实现）。
若代码与本文冲突（例如后续把 `allowIncomplete` 默认值改成 `false`，或给编辑器加了更多快捷键/撤销栈），
请以代码为准并同步更新本文对应小节。
