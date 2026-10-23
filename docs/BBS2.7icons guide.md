# BBS FS 2.7 图标 AI 字典

> 这是给 AI 看的图标“翻译表”。把本文件发给 AI，然后直接说中文名、英文名、原代码常量或旧 icon id 都可以。
>
> 源：`bbs-2.7-1.20.1-zh_CN.jar`  
> SHA-256：`3616ef4892945b7ab769fc2073b001a789b8d2a110227b3e4d4489879a98da10`  
> 图标图集：`atlas/BBS_FS_2.7_icons_atlas.png`（256×256）  
> 带名字总览：`BBS_FS_2.7_UI_icons_contact_sheet.png`

## 给 AI 的固定指令（建议原样附上）

```text
请阅读 BBS_ICON_AI_GUIDE.md，把它作为 BBS FS 2.7 图标的唯一名称字典。
当我说到一个图标时，先按以下顺序解析：
1. 完整文件名（可省略 .png）
2. canonical 文件名
3. 原始代码常量 constant
4. 原 icon id
5. 中文名、英文别名、同义词

如果匹配到多个候选，先把候选列出来让我确认，不要猜。
替换图标时，请输出：
- 源：canonical（constant / original id / 图集坐标 / 文件路径）
- 目标：canonical（constant / original id / 图集坐标 / 文件路径）
- 操作：替换代码常量、修改图集坐标，或复制 icons/ 中的 PNG。
```

## 怎么描述“把某个图标换成什么”

推荐格式：

```text
把 <源图标> 换成 <目标图标>。
```

示例：

| 说法 | 解析结果 |
|---|---|
| 把设置图标换成太阳 | 源 `gear`（GEAR）→ 目标 `sun`（SUN） |
| 把垃圾桶换成刷新 | 源 `trash`（TRASH）→ 目标 `refresh`（REFRESH） |
| 把播放换成暂停 | 源 `play`（PLAY）→ 目标 `pause`（PAUSE） |
| 用锁的图标替换解锁 | 源 `locked`（LOCKED）→ 目标 `unlocked`（UNLOCKED） |
| 把图片图标换成相机 | 源 `image`（IMAGE）→ 目标 `camera`（CAMERA） |

## 解析与替换规则

- **canonical 是文件名**：`icons/<canonical>.png`，同时也是导出的 4× 预览名。
- **constant 是 BBS `Icons.java` 的字段名**：例如 `Icons.GEAR`、`Icons.SUN`。改 BBS 插件/界面代码时优先用它；也接受 `Icons.GEAR` 这种写法。
- **original id 是 `new Icon(..., "id", ...)` 的字符串**：插件若按 id 查找，使用它。
- **图集坐标**是 256×256 `icons.png` 中的 `(x, y, w, h)`；要直接贴图或做自定义图集时使用。
- **未注册图标**（`unregistered_loop_*`）没有 constant/id，不要在 `Icons.java` 代码里直接引用；它们只作为素材保留。
- `NONE` 是空图标，没有图像，不参与替换。

## 容易混淆的名字

| 说法 | 推荐解析 | 说明 |
|---|---|---|
| 设置 | `gear` | 齿轮/设置按钮；`settings` 是滑杆图标。 |
| 滑杆 / 参数 | `settings` | 不是齿轮。 |
| 保存 | `save` | 软盘带红点。 |
| 已保存 | `saved` | 普通软盘。 |
| 软盘 | `save` / `saved` | 两个都像软盘，先确认要哪一个。 |
| 图片 | `image` | 图像文件图标；`picture` 是相框/图片展示。 |
| 爱心 / 心形 | `heart` | `heart_alt` 是另一版心形。 |
| 相机 | `camera` | `video_camera` 是摄影机/摄像机。 |
| 钥匙 / 按键 | `key` | `key_cap` 是键帽。 |
| 删除 | `trash` | `remove` 是减号/移除；`close` 是关闭叉。 |
| 锁 | `locked` | `unlocked` 是开锁。 |
| 左收起 | `left_load` | `collapse_all` 是全部折叠。 |
| 右展开 | `right_load` | `expand_all` 是全部展开。 |

## 完整字典

共 **239** 枚图集图像：233 枚在 `Icons.java` 注册，6 枚未注册。

### 通用 / 操作

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 1 | `gear.png` | 齿轮、设置、配置、选项、settings gear | `GEAR` | `gear` | 16×16 | (0, 0) |
| 2 | `more.png` | 更多、菜单、省略号、ellipsis | `MORE` | `more` | 16×16 | (16, 0) |
| 5 | `add.png` | 添加、加号、plus | `ADD` | `add` | 16×16 | (64, 0) |
| 6 | `dupe.png` | 复制、克隆、clone | `DUPE` | `dupe` | 16×16 | (80, 0) |
| 7 | `remove.png` | 移除、减号、minus | `REMOVE` | `remove` | 16×16 | (96, 0) |
| 8 | `pose.png` | 姿态、骨骼 | `POSE` | `pose` | 16×16 | (112, 0) |
| 9 | `filter.png` | 过滤、漏斗、筛选、funnel | `FILTER` | `filter` | 16×16 | (128, 0) |
| 12 | `collapse_all.png` | 全部折叠、收起全部 | `COLLAPSE_ALL` | `collapse_all` | 16×16 | (32, 176) |
| 13 | `expand_all.png` | 全部展开、展开全部 | `EXPAND_ALL` | `expand_all` | 16×16 | (16, 176) |
| 14 | `locked.png` | 锁定、锁、上锁、lock | `LOCKED` | `locked` | 16×16 | (160, 0) |
| 15 | `unlocked.png` | 解锁、开锁、unlock | `UNLOCKED` | `unlocked` | 16×16 | (176, 0) |
| 16 | `layout.png` | 布局 | `LAYOUT` | `layout` | 16×16 | (240, 144) |
| 17 | `copy.png` | 复制 | `COPY` | `copy` | 16×16 | (192, 0) |
| 18 | `paste.png` | 粘贴 | `PASTE` | `paste` | 16×16 | (208, 0) |
| 19 | `cut.png` | 剪切 | `CUT` | `cut` | 16×16 | (224, 0) |
| 20 | `refresh.png` | 刷新、重载、重新加载 | `REFRESH` | `refresh` | 16×16 | (240, 0) |
| 26 | `edit.png` | 编辑 | `EDIT` | `edit` | 16×16 | (80, 16) |
| 28 | `close.png` | 关闭、叉、取消 | `CLOSE` | `close` | 16×16 | (112, 16) |
| 29 | `limb.png` | 肢体、骨骼段 | `LIMB` | `limb` | 16×16 | (128, 16) |
| 30 | `code.png` | 代码 | `CODE` | `code` | 16×16 | (144, 16) |
| 33 | `help.png` | 帮助 | `HELP` | `help` | 16×16 | (160, 16) |
| 34 | `left_handle.png` | 左手柄 | `LEFT_HANDLE` | `left_handle` | 16×16 | (176, 16) |
| 35 | `main_handle.png` | 主手柄 | `MAIN_HANDLE` | `main_handle` | 16×16 | (192, 16) |
| 36 | `right_handle.png` | 右手柄 | `RIGHT_HANDLE` | `right_handle` | 16×16 | (208, 16) |
| 38 | `block.png` | 方块 | `BLOCK` | `block` | 16×16 | (240, 16) |
| 39 | `favorite.png` | 收藏、星标 | `FAVORITE` | `favorite` | 16×16 | (0, 32) |
| 48 | `all_directions.png` | 全方向、循环、全方位、循环箭头 | `ALL_DIRECTIONS` | `all_directions` | 16×16 | (144, 32) |
| 54 | `graph.png` | 图表 | `GRAPH` | `graph` | 16×16 | (240, 32) |
| 56 | `exclamation.png` | 感叹号、警告 | `EXCLAMATION` | `exclamation` | 16×16 | (16, 48) |
| 57 | `left_load.png` | 向左加载、收起、左收起、collapse left | `LEFTLOAD` | `leftload` | 16×16 | (32, 48) |
| 58 | `right_load.png` | 向右加载、展开、右展开、expand right | `RIGHTLOAD` | `rightload` | 16×16 | (48, 48) |
| 59 | `bubble.png` | 气泡、对话 | `BUBBLE` | `bubble` | 16×16 | (64, 48) |
| 61 | `processor.png` | 处理器、芯片 | `PROCESSOR` | `processor` | 16×16 | (96, 48) |
| 62 | `maze.png` | 迷宫 | `MAZE` | `maze` | 16×16 | (112, 48) |
| 63 | `bookmark.png` | 书签 | `BOOKMARK` | `bookmark` | 16×16 | (128, 48) |
| 64 | `sound.png` | 声音、喇叭 | `SOUND` | `sound` | 16×16 | (144, 48) |
| 65 | `search.png` | 搜索、放大镜、查找 | `SEARCH` | `search` | 16×16 | (160, 48) |
| 66 | `cylinder.png` | 圆柱 | `CYLINDER` | `cylinder` | 16×16 | (176, 48) |
| 67 | `line.png` | 线条 | `LINE` | `line` | 16×16 | (192, 48) |
| 68 | `redo.png` | 重做 | `REDO` | `redo` | 16×16 | (208, 48) |
| 69 | `undo.png` | 撤销 | `UNDO` | `undo` | 16×16 | (224, 48) |
| 70 | `console.png` | 控制台 | `CONSOLE` | `console` | 16×16 | (240, 48) |
| 71 | `curve_in.png` | 缓入曲线 | `IN` | `in` | 16×16 | (0, 64) |
| 72 | `curve_out.png` | 缓出曲线 | `OUT` | `out` | 16×16 | (16, 64) |
| 73 | `properties.png` | 属性 | `PROPERTIES` | `properties` | 16×16 | (32, 64) |
| 74 | `font.png` | 字体 | `FONT` | `font` | 16×16 | (48, 64) |
| 75 | `frustum.png` | 视锥体 | `FRUSTUM` | `frustum` | 16×16 | (64, 64) |
| 80 | `plane.png` | 飞机 | `PLANE` | `plane` | 16×16 | (144, 64) |
| 81 | `helicopter.png` | 直升机 | `HELICOPTER` | `helicopter` | 16×16 | (160, 64) |
| 83 | `curves.png` | 曲线 | `CURVES` | `curves` | 16×16 | (192, 64) |
| 84 | `envelope.png` | 包络 | `ENVELOPE` | `envelope` | 16×16 | (208, 64) |
| 86 | `trash.png` | 垃圾桶、删除、垃圾 | `TRASH` | `trash` | 16×16 | (240, 64) |
| 89 | `chicken.png` | 鸡 | `CHICKEN` | `chicken` | 16×16 | (32, 80) |
| 92 | `tree.png` | 树 | `TREE` | `tree` | 16×16 | (80, 80) |
| 93 | `crops.png` | 作物 | `CROPS` | `crops` | 16×16 | (96, 80) |
| 94 | `slab.png` | 台阶、半砖 | `SLAB` | `slab` | 16×16 | (112, 80) |
| 95 | `stair.png` | 楼梯 | `STAIR` | `stair` | 16×16 | (128, 80) |
| 96 | `globe.png` | 地球 | `GLOBE` | `globe` | 16×16 | (144, 80) |
| 97 | `bullet.png` | 子弹 | `BULLET` | `bullet` | 16×16 | (160, 80) |
| 98 | `particle.png` | 粒子 | `PARTICLE` | `particle` | 16×16 | (176, 80) |
| 99 | `scene.png` | 场景 | `SCENE` | `scene` | 16×16 | (192, 80) |
| 100 | `editor.png` | 编辑器 | `EDITOR` | `editor` | 16×16 | (208, 80) |
| 102 | `external.png` | 外部链接、外链、open external | `EXTERNAL` | `external` | 16×16 | (240, 80) |
| 103 | `film.png` | 胶片、影片 | `FILM` | `film` | 16×16 | (0, 96) |
| 104 | `outline.png` | 轮廓 | `OUTLINE` | `outline` | 16×16 | (16, 96) |
| 105 | `bricks.png` | 砖块 | `BRICKS` | `bricks` | 16×16 | (32, 96) |
| 106 | `convert.png` | 转换 | `CONVERT` | `convert` | 16×16 | (48, 96) |
| 107 | `joystick.png` | 摇杆 | `JOYSTICK` | `joystick` | 16×16 | (64, 96) |
| 108 | `cup.png` | 奖杯、杯子、trophy | `CUP` | `cup` | 16×16 | (80, 96) |
| 109 | `checkmark.png` | 对勾、完成 | `CHECKMARK` | `checkmark` | 16×16 | (96, 96) |
| 110 | `structure.png` | 结构 | `STRUCTURE` | `structure` | 16×16 | (112, 96) |
| 111 | `arc.png` | 弧线 | `ARC` | `arc` | 16×16 | (128, 96) |
| 112 | `list.png` | 列表 | `LIST` | `list` | 16×16 | (144, 96) |
| 113 | `settings.png` | 滑杆设置、参数、滑杆、sliders | `SETTINGS` | `settings` | 16×16 | (160, 96) |
| 114 | `gallery.png` | 画廊 | `GALLERY` | `gallery` | 16×16 | (176, 96) |
| 116 | `arrow_up.png` | 上箭头 | `ARROW_UP` | `arrow_up` | 16×16 | (208, 96) |
| 117 | `arrow_down.png` | 下箭头 | `ARROW_DOWN` | `arrow_down` | 16×16 | (224, 96) |
| 118 | `arrow_right.png` | 右箭头 | `ARROW_RIGHT` | `arrow_right` | 16×16 | (240, 96) |
| 119 | `arrow_left.png` | 左箭头 | `ARROW_LEFT` | `arrow_left` | 16×16 | (0, 112) |
| 120 | `heart.png` | 心形、爱心、喜欢 | `HEART` | `heart` | 16×16 | (16, 112) |
| 121 | `shard.png` | 碎片、水晶 | `SHARD` | `shard` | 16×16 | (32, 112) |
| 123 | `snowflake.png` | 雪花 | `SNOWFLAKE` | `snowflake` | 16×16 | (64, 112) |
| 124 | `outline_sphere.png` | 线框球 | `OUTLINE_SPHERE` | `outline_sphere` | 16×16 | (80, 112) |
| 126 | `fading.png` | 渐隐 | `FADING` | `fading` | 16×16 | (112, 112) |
| 128 | `light.png` | 灯光 | `LIGHT` | `light` | 16×16 | (144, 112) |
| 134 | `voice.png` | 语音、麦克风 | `VOICE` | `voice` | 16×16 | (240, 112) |
| 138 | `link.png` | 链接 | `LINK` | `link` | 16×16 | (48, 128) |
| 139 | `user.png` | 用户 | `USER` | `user` | 16×16 | (64, 128) |
| 140 | `morph.png` | 形态、变身、morphing | `MORPH` | `morph` | 16×16 | (80, 128) |
| 146 | `color.png` | 颜色 | `COLOR` | `color` | 16×16 | (176, 128) |
| 147 | `clip_handle_left.png` | 剪辑左手柄 | `CLIP_HANLDE_LEFT` | `clip_hanlde_left` | 8×16 | (192, 128) |
| 148 | `clip_handle_right.png` | 剪辑右手柄 | `CLIP_HANLDE_RIGHT` | `clip_hanlde_right` | 8×16 | (200, 128) |
| 149 | `action.png` | 动作 | `ACTION` | `action` | 16×16 | (208, 128) |
| 150 | `drop.png` | 水滴 | `DROP` | `drop` | 16×16 | (224, 128) |
| 151 | `onion_skin.png` | 洋葱皮 | `ONION_SKIN` | `onion_skin` | 16×16 | (240, 128) |
| 152 | `sun.png` | 太阳、阳光、日光、太阳光 | `SUN` | `sun` | 16×16 | (0, 144) |
| 154 | `heart_alt.png` | 心形（另一版）、生命、描边心 | `HEART_ALT` | `heart_alt` | 16×16 | (32, 144) |
| 155 | `skull.png` | 骷髅 | `SKULL` | `skull` | 16×16 | (48, 144) |
| 156 | `shapes.png` | 形状 | `SHAPES` | `shapes` | 16×16 | (64, 144) |
| 161 | `tri_star.png` | 三星 | `TRI_STAR` | `tri_star` | 16×16 | (144, 144) |
| 162 | `four_star.png` | 四星 | `FOUR_STAR` | `four_star` | 16×16 | (160, 144) |
| 163 | `five_star.png` | 五星 | `FIVE_STAR` | `five_star` | 16×16 | (176, 144) |
| 164 | `six_star.png` | 六星 | `SIX_STAR` | `six_star` | 16×16 | (192, 144) |
| 165 | `like.png` | 赞、喜欢 | `LIKE` | `like` | 16×16 | (208, 144) |
| 166 | `dislike.png` | 踩、不喜欢 | `DISLIKE` | `dislike` | 16×16 | (224, 144) |
| 167 | `horizontal.png` | 水平 | `HORIZONTAL` | `horizontal` | 16×16 | (0, 160) |
| 168 | `vertical.png` | 垂直 | `VERTICAL` | `vertical` | 16×16 | (16, 160) |
| 169 | `arrow_small.png` | 小箭头 | `ARROW_SMALL` | `arrow_small` | 16×16 | (80, 160) |
| 174 | `ik.png` | 反向动力学 | `IK` | `ik` | 16×16 | (224, 160) |
| 175 | `physics.png` | 物理 | `PHYSICS` | `physics` | 16×16 | (240, 160) |
| 179 | `flip_horizontal.png` | 水平翻转 | `FLIP_HORIZONTAL` | `flip_horizontal` | 16×16 | (80, 176) |
| 180 | `flip_vertical.png` | 垂直翻转 | `FLIP_VERTICAL` | `flip_vertical` | 16×16 | (96, 176) |
| 218 | `checkerboard.png` | 棋盘格、透明底、棋盘 | `CHECKBOARD` | `checkboard` | 16×16 | (0, 240) |
| 219 | `disabled.png` | 禁用、不可用、斜线 | `DISABLED` | `disabled` | 16×16 | (16, 240) |

### 文件 / 资源

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 3 | `saved.png` | 已保存、保存完成、软盘、floppy | `SAVED` | `saved` | 16×16 | (32, 0) |
| 4 | `save.png` | 保存、软盘、floppy、write | `SAVE` | `save` | 16×16 | (48, 0) |
| 21 | `download.png` | 下载 | `DOWNLOAD` | `download` | 16×16 | (0, 16) |
| 22 | `upload.png` | 上传 | `UPLOAD` | `upload` | 16×16 | (16, 16) |
| 23 | `server.png` | 服务器 | `SERVER` | `server` | 16×16 | (32, 16) |
| 24 | `folder.png` | 文件夹 | `FOLDER` | `folder` | 16×16 | (48, 16) |
| 25 | `image.png` | 图像、图片、photo | `IMAGE` | `image` | 16×16 | (64, 16) |
| 27 | `material.png` | 材质 | `MATERIAL` | `material` | 16×16 | (96, 16) |
| 60 | `file.png` | 文件 | `FILE` | `file` | 16×16 | (80, 48) |
| 115 | `exchange.png` | 交换 | `EXCHANGE` | `exchange` | 16×16 | (192, 96) |
| 177 | `picture.png` | 相框、照片、photo frame | `PICTURE` | `picture` | 16×16 | (64, 176) |

### 变换 / 空间

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 10 | `move_up.png` | 上移 | `MOVE_UP` | `move_up` | 16×8 | (144, 0) |
| 11 | `move_down.png` | 下移 | `MOVE_DOWN` | `move_down` | 16×8 | (144, 8) |
| 31 | `move_left.png` | 左移 | `MOVE_LEFT` | `move_left` | 6×16 | (146, 16) |
| 32 | `move_right.png` | 右移 | `MOVE_RIGHT` | `move_right` | 6×16 | (152, 16) |
| 50 | `shift_to.png` | 移到 | `SHIFT_TO` | `shift_to` | 16×16 | (176, 32) |
| 51 | `shift_forward.png` | 前移 | `SHIFT_FORWARD` | `shift_forward` | 16×16 | (192, 32) |
| 52 | `shift_backward.png` | 后移 | `SHIFT_BACKWARD` | `shift_backward` | 16×16 | (208, 32) |
| 53 | `move_to.png` | 移动到 | `MOVE_TO` | `move_to` | 16×16 | (224, 32) |
| 82 | `orbit.png` | 轨道 | `ORBIT` | `orbit` | 16×16 | (176, 64) |
| 141 | `scale.png` | 缩放 | `SCALE` | `scale` | 16×16 | (96, 128) |
| 142 | `axis_x.png` | X 轴 | `X` | `x` | 16×16 | (112, 128) |
| 143 | `axis_y.png` | Y 轴 | `Y` | `y` | 16×16 | (128, 128) |
| 144 | `axis_z.png` | Z 轴 | `Z` | `z` | 16×16 | (144, 128) |
| 170 | `space_global.png` | 全局空间 | `SPACE_GLOBAL` | `space_global` | 16×16 | (160, 160) |
| 171 | `space_local.png` | 局部空间 | `SPACE_LOCAL` | `space_local` | 16×16 | (176, 160) |
| 172 | `space_view.png` | 视图空间 | `SPACE_VIEW` | `space_view` | 16×16 | (192, 160) |
| 173 | `space_parent.png` | 父级空间 | `SPACE_PARENT` | `space_parent` | 16×16 | (208, 160) |

### 播放 / 时间线

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 37 | `reverse.png` | 反向 | `REVERSE` | `reverse` | 16×16 | (224, 16) |
| 42 | `play.png` | 播放 | `PLAY` | `play` | 16×16 | (48, 32) |
| 43 | `pause.png` | 暂停 | `PAUSE` | `pause` | 16×16 | (64, 32) |
| 46 | `stop.png` | 停止 | `STOP` | `stop` | 16×16 | (112, 32) |
| 76 | `frame_next.png` | 下一帧 | `FRAME_NEXT` | `frame_next` | 16×16 | (80, 64) |
| 77 | `frame_prev.png` | 上一帧 | `FRAME_PREV` | `frame_prev` | 16×16 | (96, 64) |
| 78 | `forward.png` | 向前 | `FORWARD` | `forward` | 16×16 | (112, 64) |
| 79 | `backward.png` | 向后 | `BACKWARD` | `backward` | 16×16 | (128, 64) |
| 85 | `player.png` | 玩家 | `PLAYER` | `player` | 16×16 | (224, 64) |
| 127 | `time.png` | 时间 | `TIME` | `time` | 16×16 | (128, 112) |
| 153 | `stopwatch.png` | 秒表 | `STOPWATCH` | `stopwatch` | 16×16 | (16, 144) |
| 176 | `hotbar.png` | 快捷栏 | `HOTBAR` | `hotbar` | 16×16 | (0, 176) |

### 视图 / 显示

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 40 | `visible.png` | 可见、眼睛、显示 | `VISIBLE` | `visible` | 16×16 | (16, 32) |
| 41 | `invisible.png` | 不可见、闭眼、隐藏 | `INVISIBLE` | `invisible` | 16×16 | (32, 32) |
| 44 | `maximize.png` | 最大化 | `MAXIMIZE` | `maximize` | 16×16 | (80, 32) |
| 45 | `minimize.png` | 最小化 | `MINIMIZE` | `minimize` | 16×16 | (96, 32) |
| 47 | `fullscreen.png` | 全屏 | `FULLSCREEN` | `fullscreen` | 16×16 | (128, 32) |
| 101 | `looking.png` | 观察、视角 | `LOOKING` | `looking` | 16×16 | (224, 80) |
| 125 | `camera.png` | 相机、拍照、photo camera | `CAMERA` | `camera` | 16×16 | (96, 112) |
| 145 | `video_camera.png` | 摄影机、摄像机、录像 | `VIDEO_CAMERA` | `video_camera` | 16×16 | (160, 128) |

### 形状 / 评级

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 49 | `sphere.png` | 球体 | `SPHERE` | `sphere` | 16×16 | (160, 32) |
| 157 | `square.png` | 正方形 | `SQUARE` | `square` | 16×16 | (80, 144) |
| 158 | `triangle.png` | 三角形 | `TRIANGLE` | `triangle` | 16×16 | (96, 144) |
| 159 | `diamond.png` | 菱形 | `DIAMOND` | `diamond` | 16×16 | (112, 144) |
| 160 | `circle.png` | 圆形 | `CIRCLE` | `circle` | 16×16 | (128, 144) |

### 工具 / 绘制

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 55 | `wrench.png` | 扳手、工具 | `WRENCH` | `wrench` | 16×16 | (0, 48) |
| 90 | `spray.png` | 喷漆 | `SPRAY` | `spray` | 16×16 | (48, 80) |
| 91 | `bucket.png` | 桶 | `BUCKET` | `bucket` | 16×16 | (64, 80) |
| 122 | `pointer.png` | 指针 | `POINTER` | `pointer` | 16×16 | (48, 112) |
| 220 | `cursor.png` | 光标 | `CURSOR` | `cursor` | 16×16 | (32, 240) |
| 227 | `eraser.png` | 橡皮擦 | `ERASER` | `eraser` | 16×16 | (32, 160) |
| 228 | `eyedropper.png` | 吸管 | `EYEDROPPER` | `eyedropper` | 16×16 | (48, 160) |
| 229 | `brush.png` | 笔刷 | `BRUSH` | `brush` | 16×16 | (64, 160) |

### 社交平台

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 87 | `youtube.png` | YouTube | `YOUTUBE` | `youtube` | 16×16 | (0, 80) |
| 88 | `twitter.png` | Twitter | `TWITTER` | `twitter` | 16×16 | (16, 80) |
| 135 | `twitch.png` | Twitch | `TWITCH` | `twitch` | 16×16 | (0, 128) |
| 136 | `bilibili.png` | Bilibili | `BILIBILI` | `bilibili` | 16×16 | (16, 128) |
| 137 | `tiktok.png` | TikTok | `TIKTOK` | `tiktok` | 16×16 | (32, 128) |
| 178 | `discord.png` | Discord | `DISCORD` | `discord` | 16×16 | (48, 176) |

### 输入 / 鼠标 / 键帽

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 129 | `key_cap.png` | 键帽 | `KEY_CAP` | `key_cap` | 16×16 | (160, 112) |
| 130 | `left_stick.png` | 左摇杆 | `LEFT_STICK` | `left_stick` | 16×16 | (176, 112) |
| 131 | `right_stick.png` | 右摇杆 | `RIGHT_STICK` | `right_stick` | 16×16 | (192, 112) |
| 132 | `trigger.png` | 扳机 | `TRIGGER` | `trigger` | 16×16 | (208, 112) |
| 133 | `key.png` | 钥匙、按键 | `KEY` | `key` | 16×16 | (224, 112) |
| 221 | `mouse_body.png` | 鼠标主体 | `MOUSE_BODY` | `mouse_body` | 14×18 | (241, 237) |
| 222 | `mouse_lmb.png` | 鼠标左键、左键、LMB | `MOUSE_LMB` | `mouse_lmb` | 6×7 | (242, 229) |
| 223 | `mouse_rmb.png` | 鼠标右键、右键、RMB | `MOUSE_RMB` | `mouse_rmb` | 6×7 | (248, 229) |
| 224 | `key_cap_left.png` | 键帽左侧、键帽左、keycap left | `KEY_CAP_LEFT` | `key_cap` | 4×20 | (220, 236) |
| 225 | `key_cap_right.png` | 键帽右侧、键帽右、keycap right | `KEY_CAP_RIGHT` | `key_cap` | 4×20 | (236, 236) |
| 226 | `key_cap_repeatable.png` | 可重复键帽、重复键、keycap repeat | `KEY_CAP_REPEATABLE` | `key_cap_repeatable` | 12×20 | (224, 236) |

### 插值曲线

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 181 | `interp_linear.png` | 插值·线性 | `INTERP_LINEAR` | `interp_linear` | 16×16 | (0, 192) |
| 182 | `interp_const.png` | 插值·常量 | `INTERP_CONST` | `interp_const` | 16×16 | (16, 192) |
| 183 | `interp_step.png` | 插值·阶梯 | `INTERP_STEP` | `interp_step` | 16×16 | (32, 192) |
| 184 | `interp_quad_inout.png` | 插值·二次缓入缓出 | `INTERP_QUAD_INOUT` | `interp_quad_inout` | 16×16 | (48, 192) |
| 185 | `interp_cubic_inout.png` | 插值·三次缓入缓出 | `INTERP_CUBIC_INOUT` | `interp_cubic_inout` | 16×16 | (64, 192) |
| 186 | `interp_quart_inout.png` | 插值·四次缓入缓出 | `INTERP_QUART_INOUT` | `interp_quart_inout` | 16×16 | (80, 192) |
| 187 | `interp_quint_inout.png` | 插值·五次缓入缓出 | `INTERP_QUINT_INOUT` | `interp_quint_inout` | 16×16 | (96, 192) |
| 188 | `interp_exp_inout.png` | 插值·指数缓入缓出 | `INTERP_EXP_INOUT` | `interp_exp_inout` | 16×16 | (112, 192) |
| 189 | `interp_back_inout.png` | 插值·回弹缓入缓出 | `INTERP_BACK_INOUT` | `interp_back_inout` | 16×16 | (128, 192) |
| 190 | `interp_elastic_inout.png` | 插值·弹性缓入缓出 | `INTERP_ELASTIC_INOUT` | `interp_elastic_inout` | 16×16 | (144, 192) |
| 191 | `interp_bounce_inout.png` | 插值·弹跳缓入缓出 | `INTERP_BOUNCE_INOUT` | `interp_bounce_inout` | 16×16 | (160, 192) |
| 192 | `interp_sine_inout.png` | 插值·正弦缓入缓出 | `INTERP_SINE_INOUT` | `interp_sine_inout` | 16×16 | (176, 192) |
| 193 | `interp_circle_inout.png` | 插值·圆弧缓入缓出 | `INTERP_CIRCLE_INOUT` | `interp_circle_inout` | 16×16 | (192, 192) |
| 194 | `interp_bezier.png` | 插值·贝塞尔 | `INTERP_BEZIER` | `interp_bezier` | 16×16 | (208, 192) |
| 195 | `interp_bspline.png` | 插值·B 样条 | `INTERP_BSPLINE` | `interp_bspline` | 16×16 | (208, 208) |
| 196 | `interp_auto.png` | 插值·自动 | `INTERP_AUTO` | `interp_auto` | 16×16 | (224, 192) |
| 197 | `interp_auto_clamped.png` | 插值·自动钳制 | `INTERP_AUTO_CLAMPED` | `interp_auto_clamped` | 16×16 | (224, 208) |
| 198 | `interp_quad_out.png` | 插值·二次缓出 | `INTERP_QUAD_OUT` | `interp_quad_out` | 16×16 | (48, 208) |
| 199 | `interp_cubic_out.png` | 插值·三次缓出 | `INTERP_CUBIC_OUT` | `interp_cubic_out` | 16×16 | (64, 208) |
| 200 | `interp_quart_out.png` | 插值·四次缓出 | `INTERP_QUART_OUT` | `interp_quart_out` | 16×16 | (80, 208) |
| 201 | `interp_quint_out.png` | 插值·五次缓出 | `INTERP_QUINT_OUT` | `interp_quint_out` | 16×16 | (96, 208) |
| 202 | `interp_exp_out.png` | 插值·指数缓出 | `INTERP_EXP_OUT` | `interp_exp_out` | 16×16 | (112, 208) |
| 203 | `interp_back_out.png` | 插值·回弹缓出 | `INTERP_BACK_OUT` | `interp_back_out` | 16×16 | (128, 208) |
| 204 | `interp_elastic_out.png` | 插值·弹性缓出 | `INTERP_ELASTIC_OUT` | `interp_elastic_out` | 16×16 | (144, 208) |
| 205 | `interp_bounce_out.png` | 插值·弹跳缓出 | `INTERP_BOUNCE_OUT` | `interp_bounce_out` | 16×16 | (160, 208) |
| 206 | `interp_sine_out.png` | 插值·正弦缓出 | `INTERP_SINE_OUT` | `interp_sine_out` | 16×16 | (176, 208) |
| 207 | `interp_circle_out.png` | 插值·圆弧缓出 | `INTERP_CIRCLE_OUT` | `interp_circle_out` | 16×16 | (192, 208) |
| 208 | `interp_quad_in.png` | 插值·二次缓入 | `INTERP_QUAD_IN` | `interp_quad_in` | 16×16 | (48, 224) |
| 209 | `interp_cubic_in.png` | 插值·三次缓入 | `INTERP_CUBIC_IN` | `interp_cubic_in` | 16×16 | (64, 224) |
| 210 | `interp_quart_in.png` | 插值·四次缓入 | `INTERP_QUART_IN` | `interp_quart_in` | 16×16 | (80, 224) |
| 211 | `interp_quint_in.png` | 插值·五次缓入 | `INTERP_QUINT_IN` | `interp_quint_in` | 16×16 | (96, 224) |
| 212 | `interp_exp_in.png` | 插值·指数缓入 | `INTERP_EXP_IN` | `interp_exp_in` | 16×16 | (112, 224) |
| 213 | `interp_back_in.png` | 插值·回弹缓入 | `INTERP_BACK_IN` | `interp_back_in` | 16×16 | (128, 224) |
| 214 | `interp_elastic_in.png` | 插值·弹性缓入 | `INTERP_ELASTIC_IN` | `interp_elastic_in` | 16×16 | (144, 224) |
| 215 | `interp_bounce_in.png` | 插值·弹跳缓入 | `INTERP_BOUNCE_IN` | `interp_bounce_in` | 16×16 | (160, 224) |
| 216 | `interp_sine_in.png` | 插值·正弦缓入 | `INTERP_SINE_IN` | `interp_sine_in` | 16×16 | (176, 224) |
| 217 | `interp_circle_in.png` | 插值·圆弧缓入 | `INTERP_CIRCLE_IN` | `interp_circle_in` | 16×16 | (192, 224) |

### 盔甲

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 230 | `armor_helmet.png` | 头盔、helmet | `ARMOR_HELMET` | `armor_helmet` | 16×16 | (96, 160) |
| 231 | `armor_chestplate.png` | 胸甲、chestplate | `ARMOR_CHESTPLATE` | `armor_chestplate` | 16×16 | (112, 160) |
| 232 | `armor_leggings.png` | 护腿、leggings | `ARMOR_LEGGINGS` | `armor_leggings` | 16×16 | (128, 160) |
| 233 | `armor_boots.png` | 靴子、boots | `ARMOR_BOOTS` | `armor_boots` | 16×16 | (144, 160) |

### 图集中未注册的遗留符号

| # | 文件 | 中文名 / 别名 | 原始常量 | 原 id | 尺寸 | 图集坐标 |
|---:|---|---|---|---|---|---|
| 234 | `unregistered_loop_01.png` | 未注册循环符号 01 | `—` | `—` | 16×16 | (48, 240) |
| 235 | `unregistered_loop_02.png` | 未注册循环符号 02 | `—` | `—` | 16×16 | (64, 240) |
| 236 | `unregistered_loop_03.png` | 未注册循环符号 03 | `—` | `—` | 16×16 | (80, 240) |
| 237 | `unregistered_loop_04.png` | 未注册循环符号 04 | `—` | `—` | 16×16 | (96, 240) |
| 238 | `unregistered_loop_05.png` | 未注册循环符号 05 | `—` | `—` | 16×16 | (112, 240) |
| 239 | `unregistered_loop_06.png` | 未注册循环符号 06 | `—` | `—` | 16×16 | (128, 240) |



## 文件位置

```text
BBS tubiao/
├─ icons/<canonical>.png          # 原始尺寸 PNG
├─ preview_4x/<canonical>.png     # 4× 最近邻预览
├─ atlas/BBS_FS_2.7_icons_atlas.png
├─ BBS_FS_2.7_UI_icons_contact_sheet.png
├─ BBS_ICON_AI_GUIDE.md           # 本文件
├─ BBS_ICON_AI_MANIFEST.json      # 机器可读字典（含 aliases 查找表）
├─ icon_index.csv                 # Excel 索引
├─ resolve_icon.py                # 本地名称解析器
└─ standalone/                    # 模组 icon 等独立材质
```

## 本地解析器

如果在同一工作区，AI 可以直接运行：

```bash
python resolve_icon.py 设置
python resolve_icon.py sun
python resolve_icon.py clip_hanlde_left
python resolve_icon.py --json 太阳
```

它会输出 canonical、文件路径、原 constant/id、图集坐标；有歧义时会列出所有候选。

## 备注

- 本字典只针对 **BBS FS 2.7 本体** 的 `icons.png`，不是 RefreshedUI 的替换图集。
- 6 枚 `unregistered_loop_*` 位于图集 `x=48..128, y=240`，是未注册的遗留符号，已在总览图中用黄色名字标出。
- 221 枚注册图标是标准 16×16；其余是 BBS 用于拼合控件的局部尺寸，详见 CSV 的 `width` / `height`。
- 所有图片都是原始像素裁切，没有 AI 重绘。
