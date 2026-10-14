# BBS FS 中文本地化术语表

> **版本**：v1.1（2026-10-04，基于 BBS FS 2.8 更新内容维护）
> **适用范围**：`lang.assets_strings.*.json` 及 BBS FS 后续所有 UI 字符串、文档、教程翻译
> **参考依据**：`zh_cn_FS_2.7_strings.json`（既有译文风格）、`zh_cn_FS_2.8_strings.json`（2.8 新增/修订译文）、`BBS FS 2.6 Changelog.md`
>
> **维护规则**：
> 1. 新增术语时按所属类别插入对应表格，保持表格按字母序排列；
> 2. 修改既有译法前，先全局搜索旧译法出现的所有 Language Key，确保一并更新；
> 3. 「保留英文」表中的词条**禁止翻译**，包括大小写形式；
> 4. 每个词条的「示例 Key」用于快速定位实际用法，改译时以此为锚点核对上下文。

---

## 一、核心概念（BBS 领域术语）

| 英文 | 中文 | 备注 | 示例 Key |
|---|---|---|---|
| Replay | 回放 | 影片中的演员/实体时间轴单位，2.6 起与 Player 合并定名 | `bbs.ui.film.replays.category.replay` |
| Form | 形态 | 2.6 起由 Model 更名而来；作动词时译「变成/伪装」 | `bbs.ui.film.replays.category.form` |
| Morphing | 伪装 | 形态选取面板/功能名 | `bbs.ui.morphing.title` |
| Film | 影片 | 一个完整的动画工程 | `bbs.ui.film.landing.new` |
| Clip | 剪辑 | 摄像机时间轴上的片段 | `bbs.ui.camera.clips.bbs:image` |
| Keyframe | 关键帧 | | `bbs.ui.keyframes.context.keyframe_style` |
| Track | 轨道 | 时间轴上的属性轨道 | `bbs.ui.film.replays.category.form.tooltip` |
| Marker | 标记 | 时间轴标记 | `bbs.ui.film.markers.title` |
| Bone | 骨骼 | | `bbs.ui.forms.editor.bone` |
| Body part | 身体附件 | Form 下的部件 | `bbs.ui.forms.editor.context.add` |
| Pose | 姿势 | | `bbs.ui.model_editor.poses` |
| Gizmo | 操控器 | 视口中的变换手柄 | `bbs.config.transformation.gizmo_elements` |
| Anchor | 锚点 | 跟踪/摆放的对齐基准点 | `bbs.ui.camera.panels.placement.anchor` |
| Pivot | 锚点 | 组的旋转中心；与 Anchor 同译，靠上下文区分 | `bbs.ui.model_editor.model.group.center_anchor` |
| Weld | 焊接 | 骨骼弯曲处的顶点融合 | `bbs.ui.model_editor.welds` |
| Model block | 模型方块 | 世界中的实体方块 | `bbs.ui.model_blocks.title` |
| Framebuffer | 帧缓冲区 | Form 类型名 | `bbs.ui.forms.editors.framebuffer.title` |
| Billboard | 广告牌 | 始终面向相机的渲染方式 | `bbs.ui.forms.editors.billboard.title` |
| Hitbox | 碰撞箱 | | `bbs.ui.model_blocks.body.hitbox.cube` |
| Stencil map | 模板映射 | 点击选取骨骼的拾取机制（Changelog 功能） | — |
| Structure wand | 结构魔杖 | 2.6 新增物品 | `bbs.ui.structure_wand.save_title` |
| Actor | 演员 | Replay 实体化模式 | `bbs.ui.film.replay.actor` |
| Onion skin | 洋葱皮 | 动画残影参考 | `bbs.ui.textures.frames.onion` |
| Shape key | 形态键 | 2.7 功能；2.8 新增控制器与绑定 | `bbs.ui.shape_keys.title` |
| Shape key controller | 形态键控制器 | 2.8 新增，摆放于骨骼上驱动形态键 | `bbs.ui.shape_controllers.title` |
| Shape bindings | 形态键绑定 | | `bbs.ui.shape_controllers.bindings` |
| Category | 类别 | Replay/Form 的分组文件夹 | `bbs.ui.scene.replays.edit_category.title` |

---

## 二、保留英文（禁止翻译）

| 原文 | 说明 | 示例 Key |
|---|---|---|
| Tick / Ticks | 游戏时间单位，不译作「刻」；与量词连用写作「N 个 Tick」 | `bbs.ui.film.markers.tick`、`bbs.ui.film.replay.bake_ik.range` |
| IK | 反向动力学 | `bbs.ui.film.replay.bake_ik.title` |
| Spline IK | 样条反向动力学；Spline 可译「样条」，组合术语保留 IK 原文 | `bbs.ui.forms.editors.model.spline.title` |
| CEM | OptiFine 自定义实体模型 | `bbs.ui.forms.editor.model.cem` |
| PBR / LabPBR | 基于物理的渲染材质标准 | `bbs.ui.forms.editors.material.section_pbr` |
| NBT | 实体数据格式 | `bbs.ui.forms.editors.mob.nbt` |
| MoLang | 基岩版表达式语言 | `bbs.ui.onboarding.tour.particles.molang.text` |
| ffmpeg | 视频编码器，小写 | `bbs.ui.onboarding.encoder.found` |
| OptiFine / Iris / Sodium | Mod 名称 | `bbs.ui.forms.editor.model.cem-tooltip` |
| FOV | 视野 | `bbs.config.appearance.fov` |
| Alpha | 颜色通道名 | `bbs.ui.color.channel.alpha` |
| PNG / GIF / URL / NBT / GUI / UI / HUD | 通用技术缩写 | — |
| .mcmeta / .jem / .ttf / .bbs.json / .dat | 文件扩展名，保留小写和点前缀 | `bbs.ui.textures.frames.toggle` |
| BBS / BBS FS / BBS Studio | 产品名 | `bbs.ui.onboarding.layout.bbs_old` |
| YouTube Shorts / Discord / Wiki | 平台名 | `bbs.ui.onboarding.layout.short-description` |
| Ctrl / Shift / Alt / F6 / F9 / Space / Enter / LMB / RMB | 按键名；Space 可译「空格键」 | `bbs.ui.textures.browser.status.copied` |
| Complementary / Photon | 光影包名称 | `bbs.ui.forms.editors.material.section_pbr-tooltip` |
| player:Notch | 示例语法，整体保留 | `bbs.ui.textures.player_skin-description` |

---

## 三、渲染与材质

| 英文 | 中文 | 备注 | 示例 Key |
|---|---|---|---|
| Material | 材质 | 2.6 新增 Form 标签页 | `bbs.ui.forms.editors.material` |
| Material map | 材质贴图 | 2.8 新增，纹理的材质效果贴图 | `bbs.ui.textures.material.load_error` |
| Color overlay | 颜色叠加 | 整张贴图混色 | `bbs.ui.forms.editors.material.overlay` |
| Glow | 发光 | 忽略世界光照的程度 | `bbs.ui.forms.editors.material.glow` |
| Emission | 自发光 | PBR 逐像素发光 | `bbs.ui.forms.editors.material.pixel_emission` |
| Metallic | 金属度 | PBR 参数 | `bbs.ui.forms.editors.material.metallic` |
| Gloss / Smoothness | 光泽度 | PBR 参数（键名为 smoothness，显示名以 Gloss 译） | `bbs.ui.forms.editors.material.smoothness` |
| Relief | 凹凸 | PBR 高度效果 | `bbs.ui.forms.editors.material.relief` |
| Scattering / SSS | 散射 | 次表面散射 | `bbs.ui.forms.editors.material.sss` |
| Render layer | 渲染层 | | `bbs.ui.forms.editors.material.layer` |
| Culling | 面剔除 | 单双面控制 | `bbs.ui.forms.editors.material.culling` |
| Two-sided / One-sided | 双面 / 单面 | | `bbs.ui.forms.editors.material.culling.off/on` |
| Render last | 最后渲染 | 半透明排序修正 | `bbs.ui.forms.editors.material.render_last` |
| Smooth filtering | 平滑过滤 | 纹理采样 | `bbs.ui.camera.panels.image.smooth` |
| Shader(s) | 光影着色器 | 指 MC 光影包；技术语境用「着色器」 | `bbs.ui.forms.editors.material.section_pbr` |
| Shader pack | 光影包 | 2.8 新增，指可选择加载的光影 | `bbs.ui.utility.open_shaders` |
| Lighting | 光照 | | `bbs.ui.forms.editors.general.lighting` |

---

## 四、动画、相机与时间轴

| 英文 | 中文 | 备注 | 示例 Key |
|---|---|---|---|
| Auto keyframe | 自动关键帧 | 2.6 核心功能 | `bbs.ui.film.auto_keyframe` |
| Bake | 烘焙 | IK/动画固化到关键帧 | `bbs.ui.film.replay.bake_ik.bake` |
| Chain | 链条 | IK 链；骨骼首尾相接的骨骼序列 | `bbs.ui.forms.editors.model.ik.chain_length` |
| Detach | 分离 | 锚点解除挂接 | `bbs.ui.generic_keyframes.anchor.detach` |
| Distribute | 分布 | 样条 IK 关节沿曲线分布 | `bbs.ui.forms.editors.model.spline.fit` |
| Euler / Quaternion | 欧拉角 / 四元数 | 旋转存储模式 | `bbs.ui.transforms.rotation.mode_tooltip` |
| Graph | 曲线 | 关键帧编辑器的曲线图表模式 | `bbs.ui.keyframes.mode.graph` |
| Influence | 影响 | 样条 IK 等的影响权重 | `bbs.ui.forms.editors.model.spline.influence` |
| Joint | 关节 | 链条中的骨骼节点 | `bbs.ui.forms.editors.model.ik.joint.stiffness_title` |
| Motion shift handle | 运动偏移手柄 | 2.8 新增，时间轴上的补间调整手柄 | `bbs.config.timeline.show_motion_shift_handles` |
| Repetition | 重复 | 样条副本重复方式（按数量/按距离） | `bbs.ui.spline.repeat.title` |
| Spline | 样条 | 2.8 新增路径功能；相机剪辑与形态 IK 共用 | `bbs.ui.spline.title` |
| Squash | 压缩 | IK 链参数 | `bbs.ui.forms.editors.model.ik.squash` |
| Stretch | 拉伸 | IK 链参数 | `bbs.ui.forms.editors.model.ik.stretch_tooltip` |
| Stiffness | 刚度 | 关节/物理参数 | `bbs.ui.forms.editors.model.ik.joint.stiffness_title` |
| Pole | 极点 | IK 极向量 | `bbs.ui.forms.editors.model.ik.pole` |
| Pole target | 极点目标 | | `bbs.ui.forms.editors.model.ik.pole_target_label` |
| Controller | 控制器 | IK 链的控制骨骼 | `bbs.ui.forms.editors.model.ik.squash_tooltip` |
| Tip bone | 末端骨骼 | 链条末端的骨骼，烘焙/列表以此为名 | `bbs.ui.film.replay.bake_ik.chains` |
| Twist | 扭转 | 样条 IK 沿曲线的扭转 | `bbs.ui.forms.editors.model.spline.twist` |
| Mirror bone | 镜像骨骼 | | `bbs.ui.model_editor.bone.mirror` |
| Shake | 摇晃 | 相机剪辑类型 | `bbs.ui.camera.clips.bbs:shake` |
| Noise | 噪波 | Shake 的随机源 | `bbs.ui.camera.panels.shake_noise` |
| Orbit | 轨道 | 相机模式 | `bbs.ui.film.replay.orbit.orbit` |
| Dolly | 滑轨 | 相机剪辑类型 | `bbs.ui.camera.clips.bbs:dolly` |
| Free look | 自由视角 | 飞行中免按住观察 | `bbs.ui.film.controller.free_look-tooltip` |
| Interpolation | 插值 | | `bbs.ui.camera.panels.interpolation` |
| Envelope | 包络 | 淡入淡出控制 | `bbs.ui.camera.panels.envelopes.title` |
| Playhead | 时间轴光标 | 时间轴上贯穿整列的标记，拖动控制播放进度 | `bbs.config.timeline.jump-comment` |
| Deterministic | 确定性 | 可复现的随机算法 | `bbs.ui.camera.panels.shake_noise_tooltip` |
| Step | 步进 | 烘焙间隔 | `bbs.ui.film.replay.bake_ik.step` |
| Range | 范围 | | `bbs.ui.film.replay.bake_ik.range` |
| Fade | 淡入淡出 | | `bbs.ui.forms.editors.actions.fade` |
| Loop | 循环 | | `bbs.ui.camera.panels.video.loop` |
| Ping-pong | 往返 | 帧序列往返播放 | `bbs.ui.textures.frames.ping_pong` |
| Track search | 轨道搜索 | 2.8 新增，按名称/部件查找轨道 | `bbs.ui.film.track_search.title` |

---

## 五、界面与交互

| 英文 | 中文 | 备注 | 示例 Key |
|---|---|---|---|
| Dashboard | 仪表盘 | 主面板区 | `bbs.ui.dashboard.category` |
| Landing | 起始页 | 编辑器空标签页（键名中出现，界面文案可不直译） | `bbs.ui.panels.landing.recent` |
| Panel | 面板 | | `bbs.config.appearance.taskbar_side` |
| Tab | 标签页 | | `bbs.ui.model_editor.keys.cycle_tabs` |
| Section | 选项 | 面板内的分组；时间轴肢体分区同译 | `bbs.ui.film.replays.collapse_all` |
| Shortcut preset | 快捷键预设 | 2.8 新增，按键绑定方案预设 | `bbs.ui.config.keybind-presets.title` |
| Placement | 摆放 | 字幕/图片/视频剪辑的定位系统 | `bbs.ui.camera.panels.placement.title` |
| Transform | 变换 | 位置/旋转/缩放总称 | `bbs.config.transformation.title` |
| Inherit | 继承 | 身体附件跟随父级 | `bbs.ui.inherit.title` |
| Pick / Pickable | 选取 / 可选取 | 区别于「选择」（普通选定） | `bbs.ui.forms.editors.general.pickable` |
| Pin / Unpin | 固定 / 取消固定 | 纹理管理器；运动路径的「锁定/固定显示」同译 | `bbs.ui.textures.browser.pin` |
| Preview icon(s) | 预览图标 | 2.8 新增，视口预览工具栏按钮 | `bbs.config.viewport.preview_icons` |
| Recent | 最近使用 | | `bbs.ui.panels.landing.recent` |
| Onboarding / Tour | 新手引导 / 引导 | welcome screen 译「欢迎界面」 | `bbs.ui.onboarding.settings.reset_tours` |
| Layout | 布局 | 编辑器面板排布 | `bbs.ui.onboarding.layout.title` |
| Preset | 预设 | | `bbs.ui.film.layout_presets` |
| Macro(s) | 宏命令 | 纹理编辑器快捷操作 | `bbs.ui.textures.macros.tooltip` |
| Context menu | 上下文菜单 | 界面文案中多用「右键菜单」语境隐去 | — |
| Tooltip | 工具提示 | 键名后缀，不进入译文 | — |
| Utility panel | 工具面板 | 2.8 新增，结构/资源/光影/时间等快捷操作面板 | `bbs.ui.utility.structures` |
| World assets | 世界资源 | 2.8 新增，随世界保存的资源文件夹 | `bbs.ui.utility.open_world` |

---

## 六、纹理与像素编辑

| 英文 | 中文 | 备注 | 示例 Key |
|---|---|---|---|
| Texture | 纹理 | MC 惯例译法（非「贴图」） | `bbs.ui.textures.tooltip` |
| Frame | 帧 | 纹理动画单帧 | `bbs.ui.textures.frames.duplicate` |
| Frametime / Frame time | 帧时长 | | `bbs.ui.textures.frames.frametime` |
| Layer | 图层 | 像素编辑器内 | `bbs.ui.textures.layers` |
| Selection | 选区 | | `bbs.ui.textures.tools.selection` |
| Eyedropper | 吸管 | 取色工具；屏幕取色译「从屏幕拾取颜色」 | `bbs.ui.color.eyedropper` |
| Flip | 翻转 | 水平/垂直翻转 | `bbs.ui.textures.keys.flip_h` |
| Combine | 合并 | 多纹理合成动画 | `bbs.ui.textures.browser.combine-title` |
| Make animated | 制作为动画 | | `bbs.ui.textures.make_animated` |
| Import | 导入 | | `bbs.ui.textures.browser.import` |
| Folder tree | 文件夹树 | | `bbs.ui.textures.browser.tree` |
| Read-only | 只读 | | `bbs.ui.textures.browser.info.read_only` |
| Built-in | 内置 | Mod 自带资源 | `bbs.ui.textures.browser.read_only` |

---

## 七、模型方块与结构

| 英文 | 中文 | 备注 | 示例 Key |
|---|---|---|---|
| Hardness | 硬度 | 挖掘时间系数 | `bbs.ui.model_blocks.body.hardness` |
| Solid | 固体 | 方块碰撞属性（注意区别于渲染层 Solid） | `bbs.ui.model_blocks.body.solid` |
| Camera collision | 摄像机碰撞 | | `bbs.ui.model_blocks.body.camera` |
| Equipment | 装备 | 盔甲与手持槽位 | `bbs.ui.model_blocks.equipment.title` |
| Head / Chest / Legs / Feet | 头盔 / 胸甲 / 护腿 / 靴子 | 装备槽位按物品名译 | `bbs.ui.model_blocks.equipment.*` |
| Main hand / Off hand | 主手 / 副手 | | `bbs.ui.model_blocks.equipment.mainhand/offhand` |
| Region | 区域 | 魔杖选取范围 | `bbs.ui.structure_cut.confirm` |
| Corner A / B | 角点 A / B | | `bbs.ui.structure_wand.corner_a` |
| Push face | 推移面 | 调整选区边界面 | `bbs.ui.structure_wand.push` |
| Structure | 结构 | MC 结构文件 | `bbs.ui.structure_wand.save_title` |
| Build into a form | 将建筑转为形态 | 2.6 功能名 | `bbs.ui.structure_cut.title` |

---

## 八、CEM 状态枚举

| 英文 | 中文 | 示例 Key |
|---|---|---|
| Aggressive | 攻击性 | `bbs.ui.forms.editor.model.cem.aggressive` |
| Burning | 燃烧中 | `bbs.ui.forms.editor.model.cem.burning` |
| Climbing | 攀爬中 | `bbs.ui.forms.editor.model.cem.climbing` |
| Crawling | 爬行中 | `bbs.ui.forms.editor.model.cem.crawling` |
| Health | 生命值 | `bbs.ui.forms.editor.model.cem.health` |
| In lava | 在熔岩中 | `bbs.ui.forms.editor.model.cem.in_lava` |
| On a shoulder | 在肩膀上 | `bbs.ui.forms.editor.model.cem.on_shoulder` |
| Sitting | 坐下 | `bbs.ui.forms.editor.model.cem.sitting` |
| Tamed | 已驯服 | `bbs.ui.forms.editor.model.cem.tamed` |

---

## 九、格式与文风规范

### 9.1 占位符与转义（不可改动）

| 形式 | 规则 | 示例 |
|---|---|---|
| `%s` | 原样保留，语序可调，数量必须一致 | "Found: ffmpeg %s" → "已找到：ffmpeg %s" |
| 双占位符 | 两个 `%s` 均须保留且顺序可辨 | "Rename group: %s → %s" → "重命名组：%s → %s" |
| `\"` | JSON 内引号转义保留 | `"移除组 \"%s\" 及其中的所有组和立方体？"` |
| `\n` / `\n\n` | 换行分段结构保留 | `bbs.ui.generic_keyframes.anchor.keep_transform_tooltip` |
| `«%s»` | 特殊引号样式保留原文符号 | `bbs.ui.structure_cut.done` |
| `{name}` 等 | 文件名模板变量原样保留 | `bbs.config.video.filename_format-comment` |
| `%WIDTH%` 等 | FFmpeg 参数占位符原样保留 | `bbs.config.video.arguments-comment` |

### 9.2 标点与排版

- 使用中文全角标点：，。：；？！（）
- 双引号：在较长的文本中使用 `「」` 代替中文全角双引号
- 省略号：按钮/菜单/工具提示结尾用 `...`（三个半角点，与既有文件一致）
- 中英文之间加空格：`将 %s 个形态复制到类别...`、`每 N 个 Tick 一个关键帧`
- 破折号解释说明用 `—`（单 em dash），保留两侧空格
- 单位换算：`%s blocks` → `%s 个方块`，国际标准计量单位如 `m`（米） `s` （秒）等可保留原文

### 9.3 量词约定

| 对象 | 量词 | 示例 |
|---|---|---|
| 形态 / 组 / 方块 / 文件 / 关键帧 | 个 | `移除 %s 个形态` |
| 纹理 / 图片 | 张 | `%s 张纹理` |
| 帧（动画） | 帧 | `动画：%s 帧` |

### 9.4 人称与语气

- 正式 UI 文案（设置、提示、错误）：用「您」或省略主语
- 新手引导（onboarding/tour）：用「你」，语气可轻快（与原文风格一致）
- 祈使句按钮：动词开头，不加「请」（添加 / 移除 / 复制）

### 9.5 常用动词对照

| 英文 | 中文 | 反例（不用） |
|---|---|---|
| Add | 添加 | 新增（「新建」仅用于创建实体对象） |
| Remove | 移除 | 删除（Delete 才译「删除」） |
| Delete | 删除 | — |
| Duplicate | 复制 | 拷贝（Copy 译「拷贝」以示区分） |
| Copy | 拷贝 | — |
| Rename | 重命名 | 改名 |
| Reset | 重置 | 复位 |
| Toggle | 切换 | 开关 |
| Enable / Disable | 启用 / 禁用 | 打开 / 关闭（功能开关语境） |
| Create / New | 新建 | — |
| Save as | 另存为 | 储存为 |
| Kill（/kill 命令） | 清除 | 击杀（实体清除语境） |

---

## 十、待定与争议记录（后续版本处理）

| 词条 | 现状 | 说明 |
|---|---|---|
| Tick | ✅ 已统一（2.8） | 2.8 合并时已按第二节规则全局统一为「Tick」，共修订旧文件 21 处（含 `每一游戏刻`→`每个 Tick` 等）；时间轴「刻度线」为标尺刻度，不属游戏 Tick，保留不变 |
| Pivot / Anchor | 均译「锚点」 | 两者在模型编辑器中同屏出现时可能混淆，后续可考虑 Pivot →「枢轴」 |
| Shader | 按语境分译「光影 / 着色器」 | 面向创作者的功能名用「光影」，技术属性（shader shadow）用「着色器」 |
| Body (model block) | 暂译「本体」 | 该 Section 实为方块物理属性集合，若 UI 反馈不佳可改「物理属性」 |
| Shorts (layout) | 译「竖屏」 | 布局预设名，直译 Shorts 反而不达意；保留观察用户反馈 |
| Spline | 译「样条」 | 2.8 新增；相机剪辑与 Spline IK 共用该词。若社区惯用「曲线」产生歧义（与 Curve 剪辑冲突），可评估改译 |
| Record actor（preview icon） | 译「录制演员」 | 2.8 新增预览图标，直译自英文；实际功能为录制回放，若 UI 反馈不佳可改「录制回放」 |
| Cubic model | 译「立方体模型」 | Spline IK 错误提示用语，指由立方体构成的模型；如官方文档另有定义需回译修正 |
