# Bug 分析报告：多选关键帧后"重置变换"只重置部分关键帧

## 1. 问题描述

在 BBSFS 影片编辑器中，在时间轴上多选（框选 / Ctrl+点选）多个关键帧后，点击变换面板中的"重置变换"（右键菜单或快捷键 R），只有**主选中关键帧**（primary）被正确重置为默认值（translate=0, scale=1, rotation=0），其他选中的关键帧并未归零——它们的值被错误地叠加了一个以主关键帧为基准的偏移量。

## 2. 根因分析

### 2.1 类继承与重置调用链

变换编辑器的继承结构（BBS 2.7 源码）：

```
UITransform                          ← 定义 reset() 基类实现
  └── UIPropTransform                ← transform 字段、字段行、回调
        └── UIDeltaPropTransform     ← 重写 setT/setS/setR 为 delta fan-out
              └── UIKeyframePropTransform  ← 重写 getTargetTransform()（auto-key 支持）
                    ├── UIPoseKeyframeFactory.UIPoseTransforms       ✅ 已重写 reset()
                    ├── UITransformKeyframeFactory.UIPoseTransforms  ❌ 未重写
                    ├── UIPoseTransformKeyframeFactory.UIPoseTransforms ❌ 未重写
                    └── UIAnchorKeyframeFactory.UIAnchorTransforms   ❌ 未重写
```

### 2.2 基类 reset() 的 delta 路径

`UITransform.reset()`（`UITransform.java:689`）：

```java
protected void reset()
{
    this.fillSetT(0, 0, 0);   // → fillT(0,0,0) + setT(null, 0, 0, 0)
    this.fillSetS(1, 1, 1);   // → fillS(1,1,1) + setS(null, 1, 1, 1)
    this.fillSetR(0, 0, 0);   // → fillR(0,0,0) + setR(null, 0, 0, 0)
}
```

而 `UIDeltaPropTransform.setT()`（`UIDeltaPropTransform.java:55`）将写入转为**相对于主变换的 delta**：

```java
public void setT(Axis axis, double x, double y, double z)
{
    Transform transform = this.getTargetTransform();   // 主关键帧的变换
    float dx = (float)(x - transform.translate.x);      // dx = 0 - primary.tx
    float dy = (float)(y - transform.translate.y);
    float dz = (float)(z - transform.translate.z);

    this.applyToTarget((t) -> {                        // fan-out 到所有选中关键帧
        t.translate.x += dx;                            // other.tx += (0 - primary.tx)
        t.translate.y += dy;
        t.translate.z += dz;
    });
}
```

### 2.3 为什么只有主关键帧被正确重置

假设多选了 3 个关键帧，主关键帧 A 的 translate.x = 5：

| 关键帧 | 原始 tx | delta = 0 - 5 = -5 | 重置后 tx | 期望值 |
|--------|---------|---------------------|-----------|--------|
| A（主） | 5       | 5 + (-5) = **0**    | 0 ✓       | 0      |
| B      | 10      | 10 + (-5) = **5**   | 5 ✗       | 0      |
| C      | 3       | 3 + (-5) = **-2**   | -2 ✗      | 0      |

scale 和 rotation 同理：只有主关键帧归零，其余关键帧被错误地减去了主关键帧的偏移量。

这在用户视角就是"只重置了部分关键帧"——主关键帧正确归零，其他选中关键帧的值看起来没被重置（或被改成了无意义的值）。

### 2.4 正确的参考实现

`UIPoseKeyframeFactory.UIPoseTransforms.reset()`（`UIPoseKeyframeFactory.java:298`）**已经正确处理了这个问题**：

```java
@Override
protected void reset()
{
    this.applyToTarget((poseT) -> {
        poseT.translate.set(0F, 0F, 0F);   // 绝对设置，而非 delta
        poseT.scale.set(1F, 1F, 1F);
        poseT.resetRotation();              // 同时清零 euler 和 quaternion
    });
    this.refillTransform();
}
```

关键区别：它用 `t.translate.set(0,0,0)` **绝对赋值**，而不是通过 `setT` 的 delta 路径。`applyToTarget` 默认路由到 `applyToSelection`，后者通过 `UIReplaysEditorUtils.forEachSelectedKeyframe` 遍历所有选中关键帧。

## 3. 受影响的类

以下三个内部类继承了 `UIKeyframePropTransform` 但**没有重写 `reset()`**，因此走基类的 delta 路径：

| 类 | 文件 | 用途 |
|----|------|------|
| `UITransformKeyframeFactory.UIPoseTransforms` | `factories/UITransformKeyframeFactory.java:27` | 通用变换关键帧轨道（模型根变换、摄像机变换等） |
| `UIPoseTransformKeyframeFactory.UIPoseTransforms` | `factories/UIPoseTransformKeyframeFactory.java:112` | 单个 PoseTransform 值关键帧轨道 |
| `UIAnchorKeyframeFactory.UIAnchorTransforms` | `factories/UIAnchorKeyframeFactory.java:251` | 锚点（Anchor）关键帧轨道 |

不受影响的类：
- `UIPoseKeyframeFactory.UIPoseTransforms`（pose 骨骼轨道）——已在 BBS 2.7 中正确重写 `reset()`。

## 4. 修复方案

在上述三个类中添加 `reset()` 重写，与 `UIPoseKeyframeFactory.UIPoseTransforms.reset()` 保持一致：

```java
@Override
protected void reset()
{
    this.applyToTarget((t) -> {
        t.translate.set(0F, 0F, 0F);
        t.scale.set(1F, 1F, 1F);
        t.resetRotation();
    });
    this.refillTransform();
}
```

### 4.1 实现方式

由于 BBS 本体是 `modCompileOnly` 依赖（jar 在 `libs/` 中），不能直接修改 BBS 源码，需要通过 **Mixin 注入**实现修复。建议新增以下 Mixin（或合并为一个）：

1. `UITransformKeyframeFactoryMixin`（如已存在则补充）→ 注入 `UITransformKeyframeFactory$UIPoseTransforms`
2. `UIPoseTransformKeyframeFactoryMixin` → 注入 `UIPoseTransformKeyframeFactory$UIPoseTransforms`
3. `UIAnchorKeyframeFactoryMixin` → 注入 `UIAnchorKeyframeFactory$UIAnchorTransforms`

每个 Mixin 使用 `@Override` 语义：通过 `@Inject(method = "reset", at = @At("HEAD"), cancellable = true)` 拦截基类 delta 路径的 `reset()`，改为绝对赋值后 `cir.cancel()`。

注意事项：
- `resetRotation()` 同时清零 euler `rotate` 和 quaternion `quat`（`Transform.java:228`），无需额外处理旋转模式。
- `applyToTarget` → `applyToSelection` → `forEachSelectedKeyframe` 已正确处理多选 fan-out 和关键帧 `preNotify/postNotify`（undo 分组）。
- `refillTransform()` 从 `getTransform()` 重新填充字段显示。
- 无需额外调用 `preCallback()/postCallback()`，因为 `forEachSelectedKeyframe` 内部已做 `selected.preNotify()/postNotify()`。

## 5. 验证方法

1. 在影片编辑器中创建一个模型轨道，添加至少 3 个变换关键帧，赋予不同的 translate/scale/rotation 值。
2. 框选这 3 个关键帧。
3. 打开其中一个关键帧的变换面板，右键 →"重置"（或按快捷键 R）。
4. **预期**：所有 3 个关键帧的 translate 变为 (0,0,0)、scale 变为 (1,1,1)、rotation 变为 0。
5. **修复前**：只有主关键帧归零，其余关键帧的值为 `自身值 - 主关键帧偏移量`。
6. 对锚点轨道、PoseTransform 轨道重复上述测试。
7. 确认 pose 骨骼轨道（UIPoseKeyframeFactory）行为不变。
8. 确认撤销（Ctrl+Z）能一次性撤销所有关键帧的重置。

## 6. 结论

这是 BBS 2.7 上游的一个遗漏：`UIPoseKeyframeFactory` 的 pose 骨骼面板正确重写了 `reset()` 为绝对赋值，但其余三个使用 `UIKeyframePropTransform` 的关键帧 factory 忘记了同样的重写，导致它们回退到 `UIDeltaPropTransform` 的 delta 路径——重置在多选时只对主关键帧正确。修复方式是在这三个类中补上与 pose 面板一致的 `reset()` 重写。
