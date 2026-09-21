# 姿势页 PBR 栏 — 实施计划表

> 目标：在 BBS 本体模型方块编辑页 → 姿势子页面中，于**材质栏下方、骨骼纹理按钮上方**，新增一个独立的 PBR 折叠栏，提供五个滑条：光泽度、金属度、散射、自发光、凹凸，且只修改当前选中肢体。

---

## 一、现有架构理解

### 1.1 姿势编辑器布局（原生 BBS 2.7）

`UIPoseEditor` 是一个纵向 column，自上而下排列：

```
骨骼列表 (UIBoneList)
├─ boneVisible 开关
├─ fix 滑条
├─ transform 变换编辑器（位置/旋转/缩放）
└─ material 材质栏 (UISection)          ← 材质栏在此
   ├─ color 颜色
   ├─ overlay 叠加色
   └─ glow 发光
```

本项目通过 `UIPoseEditorMixin` 在 `self.add()` 末尾追加：
```
├─ pickTexture 骨骼纹理按钮             ← 骨骼纹理按钮在此
├─ glint 附魔光效开关（按设置显隐）
└─ glintColor 光效颜色（按设置显隐）
```

**PBR 栏插入位置**：材质栏（`this.material`）与骨骼纹理按钮（`bbspp_cml$pickTexture`）之间。

### 1.2 原生 PBR 系统（本体已有）

本体的 PBR 五值存在于 `FormMaterial`（材质级），五个 `ValueFloat`：

| 字段 | 中文 | 范围 | 说明 |
|------|------|------|------|
| `smoothness` | 光泽度 | 0~1 | LabPBR 光泽度 |
| `metallic` | 金属度 | 0~1 | LabPBR 金属度 |
| `sss` | 散射 | 0~1 | 次表面散射 |
| `pixelEmission` | 自发光 | 0~1 | 像素自发光 |
| `relief` | 凹凸 | 0~1 | 法线/凹凸贴图强度 |

**渲染原理**：PBR 不通过 shader uniform 传递，而是通过 `FormPbr.resolveAlbedo()` 在渲染时把材质的 albedo 纹理替换为一个 **GL variant 纹理副本**，再由 `IrisUtils.trackPbrVariant()` 把五值烘焙进该变体的 PBR metadata。Iris 光影包按 albedo 的 GL id 缓存 PBR holder，变体 key 编码了五值，改滑条即换 id、自动刷新。

**当前局限**：PBR 是**材质级**（`UIMaterialFormPanel` 的材质栏），不是骨骼级。姿势编辑器的 `materialSection()` 只有 color / overlay / glow 三项，不含 PBR。

### 1.3 本项目逐骨骼扩展的成熟模式

现有骨骼纹理 / 附魔光效走的完整链路：

```
UI (UIPoseEditorMixin)
  → PoseTransformMixin 新增 @Unique 字段 + toData/fromData/copy/equals/identity/lerp/autoLerp/add 八处钩子
    → ModelMixin.applyPose() TAIL 把 PoseTransform 字段传播到 ModelGroup 的渲染期字段
      → ModelGroupMixin @Unique 字段（reset() 清空）
        → CubicVAORendererMixin.renderGroup() HEAD 读取 ModelGroup 字段写 shader
```

duck-typing 接口（`BoneTextureHolder` / `GlintHolder`）解耦编译依赖。

---

## 二、实施步骤

### Step 1：新建 `BonePbrHolder` 接口

**文件**：`src/main/java/gbeic/bbsplusplus/api/BonePbrHolder.java`

五个 float 的 getter/setter，与 `GlintHolder` 同构：
```java
float bbspp_cml$getSmoothness();
float bbspp_cml$getMetallic();
float bbspp_cml$getSss();
float bbspp_cml$getEmission();
float bbspp_cml$getRelief();
void bbspp_cml$setSmoothness(float v);
// ... 其余四个同理
```

### Step 2：PoseTransformMixin 增加 PBR 字段与序列化

**文件**：`src/main/java/gbeic/bbsplusplus/mixin/PoseTransformMixin.java`

- 新增五个 `@Unique float bbspp_cml$pbrSmoothness` 等（默认 0 = 无 PBR）
- `toData` TAIL：五值非 0 时写入 MapType
- `fromData` TAIL：读取五值
- `copy` TAIL：从源 Transform 拷贝
- `equals` RETURN：五值参与判等
- `identity` TAIL：五值归零
- `lerp` / `autoLerp` TAIL：五值可插值（与 color 同构，用 `MathUtils.clamp` + interp）
- `add` TAIL：叠加语义——源有 PBR 值则覆盖

### Step 3：ModelGroup + ModelMixin 渲染期传播

**ModelGroupMixin**（`mixin/ModelGroupMixin.java`）：
- 新增 `GroupPbrHolder` 接口或直接在 `ModelGroupMixin` 加五个 float 字段
- `reset()` TAIL 中五值归零

**ModelMixin**（`mixin/ModelMixin.java`）：
- `applyPose()` TAIL 循环中，把 PoseTransform 的 PBR 五值传播到对应 ModelGroup
- 跳过条件改为 `texture == null && !pivoted && !glint && pbr 全 0`

### Step 4：渲染端覆盖（关键难点）

**目标**：当某骨骼设了 PBR 值时，该骨骼渲染使用骨骼级 PBR 而非材质级。

原生 `FormPbr.resolveAlbedo(form, material, link, texture)` 按材质解析 albedo 变体。需要：

- **方案 A（推荐）**：在 `CubicVAORendererMixin.renderGroup()` 中，当 group 带有 PBR 覆盖时，用骨骼五值重新调用 `IrisUtils.trackPbrVariant()` 覆盖该次绘制的 PBR metadata。需确认 `trackPbrVariant` 支持 per-draw 覆盖还是只认 GL id 缓存。
- **方案 B**：在 `FormPbr.resolveAlbedo` 的 mixin 中，检查当前正在渲染的 group 是否有 PBR 覆盖，若有则用骨骼五值构造 variant key。

> 此步骤需在实际运行中验证 Iris 光影下的行为；无光影时 PBR 本就无效果（本体设计如此）。

### Step 5：UIPoseEditorMixin 新增 PBR 折叠栏

**文件**：`mixin/client/UIPoseEditorMixin.java`

- 在构造器 `bbspp_cml$createPoseButtons` 中：
  - 创建 `UISection pbrSection`，标题复用本体 `UIKeys.FORMS_EDITORS_MATERIAL_SECTION_PBR`（"Shaders (PBR)"）
  - 五个 `UISliderTrackpad`，`limit(0, 1)`，每个回调走 `bbspp_cml$writeToSelection()` 写入 PoseTransform
  - 滑条标签复用本体 key：
    - `FORMS_EDITORS_MATERIAL_SMOOTHNESS`（光泽度）
    - `FORMS_EDITORS_MATERIAL_METALLIC`（金属度）
    - `FORMS_EDITORS_MATERIAL_SSS`（散射）
    - `FORMS_EDITORS_MATERIAL_PIXEL_EMISSION`（自发光）
    - `FORMS_EDITORS_MATERIAL_RELIEF`（凹凸）
- **插入顺序**：`self.add(this.bbspp_cml$pbrSection)` 必须在 `self.add(this.bbspp_cml$pickTexture)` **之前**，确保 PBR 栏在骨骼纹理按钮上方
- `setPose` / `selectBone` TAIL：同步五值滑条为当前骨骼的实际值
- 每个滑条挂右键菜单「应用到子骨骼」（复用 `bbspp_cml$forEachSelectedChildren` 模式）

### Step 6：设置开关（可选）

参照 `CMLSettings.pickLimbTexture` / `enchantGlint`，新增 `CMLSettings.posePbr` 开关控制 PBR 栏显隐。

---

## 三、注意事项

1. **八处钩子不能漏**：与骨骼纹理/光效一样，漏 `lerp`/`autoLerp` 会导致关键帧回放时 PBR 闪烁或丢失；漏 `equals` 会导致关键帧去重错误合并。
2. **默认值为 0**：五值全 0 = 无 PBR，与本体 `FormMaterial.hasPbr()` 语义一致，无需额外开关。
3. **多宿主兼容**：`writeToSelection()` 已处理 `UIPoseFactoryEditor`（关键帧面板）和 `UIModelPoseEditor`（伪装编辑界面）两种宿主，PBR 滑条复用同一分派即可。
4. **Iris 光影依赖**：PBR 效果仅在 Iris 光影包下可见（本体设计），无光影时滑条可调但无视觉变化——与本体材质栏 PBR 行为一致。
5. **不要改原生 materialSection**：姿势编辑器的材质栏（color/overlay/glow）保持原样，PBR 栏独立折叠在其下方。

---

## 四、文件清单

| 操作 | 文件 |
|------|------|
| 新建 | `api/BonePbrHolder.java` |
| 修改 | `mixin/PoseTransformMixin.java`（加五值字段 + 八钩子） |
| 修改 | `mixin/ModelGroupMixin.java`（加渲染期五值 + reset） |
| 修改 | `mixin/ModelMixin.java`（applyPose 传播五值） |
| 修改 | `mixin/client/CubicVAORendererMixin.java`（渲染端 PBR 覆盖） |
| 修改 | `mixin/client/UIPoseEditorMixin.java`（PBR 折叠栏 UI + 同步） |
| 修改 | `settings/CMLSettings.java`（可选：显隐开关） |
| 修改 | `resources/assets/bbspp/lang/*.json`（可选：翻译 key） |
