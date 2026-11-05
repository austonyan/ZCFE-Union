# FlClash UI 设计规范报告（用于 Jetpack Compose 完全复刻）

> 分析对象：`D:\Code\Union\UI组件参考项目\FlClash`（Flutter 项目，版本 0.9.3+2026110401）
> 设计语言：**Material You（M3 / M3 Expressive）× Apple 风格交互（iOS 27 / Dynamic Type / SF Symbols 手势隐喻）**
>
> **关键结论（必读）**：FlClash 当前版本的 UI 全部构建在一个自研设计系统包 **`prism_ui`** 上（git 子模块，位于 `plugins/prism_ui/`，`pubspec.yaml:73-74` 声明 path 依赖；`lib/ui/ui.dart:1` 全量导出）。`lib/common/`、`lib/views/`、`lib/ui/` 只是业务层。因此本报告的所有组件规格直接取自 `plugins/prism_ui/lib/`（下文以 `prism_ui/lib/` 缩写），业务层用法取自 `lib/`。所有数值均标注 `文件:行号` 证据。
>
> 术语：行号引用形如 `snap_sheet.dart:26` 的，指 `plugins/prism_ui/lib/snap_sheet.dart` 第 26 行；`home.dart:323` 指 `lib/pages/home.dart`。

---

## 0. 架构总览（复刻前必须理解的三层结构）

| 层 | 位置 | 内容 |
|---|---|---|
| 设计系统 | `prism_ui/lib/`（约 170 文件） | ThemeData、ColorScheme（HCT 种子色）、AppTextStyle、AppCorner/AppShape（连续圆角）、PressSurface（按压态）、Card、ListRow、SnapSheet、NavigationDock、Switch、Slider、TextField 等 |
| App 主题装配 | `lib/manager/theme_manager.dart`、`lib/providers/state/theme.dart`、`lib/bootstrap.dart` | 动态取色 seed 获取、ColorScheme 生成缓存、字体 fallback |
| 业务页面 | `lib/pages/home.dart`、`lib/views/**`、`lib/ui/**` | 首页分页框架、Dashboard 卡片、代理卡、设置分组列表等 |

---

## 1. 主题系统

### 1.1 动态取色（Dynamic Color）实现链路

1. **取 seed**：启动时通过平台插件取系统种子色（Android 12+ Monet 的 core palette 的 light/dark 两个 seed + 强调色）：
   - `lib/bootstrap.dart:49-73`：`DynamicColorPlugin.getSystemSeeds()`（light/dark 双 seed）与 `getAccentColor()`；失败回退 `defaultPrimaryColor`。
   - `lib/common/constant.dart:175`：`const defaultPrimaryColor = 0xFF007AFF;`（iOS 蓝）。
   - `lib/common/constant.dart:186-197`：可选主色板为 iOS 系统色（深蓝 iOS 蓝的 macOS 强调色序）：
     `0xFF007AFF, 0xFFCB30E0, 0xFFFF2D55, 0xFFFF383C, 0xFFFF8D28, 0xFFFFCC00, 0xFF34C759, 0xFF8E8E93`。
   - 动态色插件在 `plugins/dynamic_color/`（本地包，含 Android Kotlin 端）。
2. **生成 ColorScheme**：`lib/providers/state/theme.dart:38-69`（`genColorScheme`）：seed 优先级 = 用户自选主色 > 系统 seed（分亮暗）> 强调色；用 `seededColorScheme(seedColor, brightness, style)` 生成并按 `(seed, brightness, style, pureBlack)` 做 64 条 LRU 缓存（`theme.dart:71-94`）。
3. **四种配色风格**（`prism_ui/lib/color.dart:12-23`，用户默认 `vivid`，见 `lib/models/config.dart:478-480`）：
   - `vivid` / `tinted` → Material `content` 变体，primary 直接用 seed 原色（tone 被夹取）；
   - `soft` → `tonalSpot` 变体（M3 默认 40/80 tone 法）；
   - `monochrome` → 单色变体。
4. **seed → primary 的处理（vivid/tinted）**（`color.dart:167-187` `_accent`）：
   - 亮色：`tone = clamp(seed.tone, 35, 60)`，`tone` 等于 seed 本身时直接用原色；`onPrimary`：tone ≤ 60 → 白色，否则取该 hue/chroma 的 tone 10。
   - 暗色：`tone = clamp(seed.tone + 3, 50, 75)`（`_darkAccentLift = 3.0`，`color.dart:161-165`）。
5. **中性面（surface）的色度控制**（`color.dart:139-145`）：
   - 暗色 surface 系列色度上限 `_surfaceChroma = 3.0`；亮色"纸面"色度上限 `_paperChroma = 1.5`（比 M3 默认更灰、更冷净）。
   - `tinted` 风格给中性面注入 `min(seed.chroma/6, 10)` 的色度（`color.dart:143-145`）。
6. **暗色特殊 tone**（`color.dart:149-155`）：`onSurface` 用中性 palette 的 **tone 97**（比 M3 的 100 柔和）；`surfaceContainer = 15`、`surfaceContainerHigh = 21`、`surfaceContainerHighest = 27`（即暗色下"卡片/区块/内嵌"三级固定 tone，而非 M3 默认的 12/17/22/24 —— 这是偏 iOS 的低对比层级）。
   - `primaryContainer`：色度夹到 48（`_primaryContainerChroma`，`color.dart:147`），亮 tone 90 / 暗 tone 30（`color.dart:36-39,67-68`）。
7. **纯黑模式**（`color.dart:294-311` `toPureBlack`）：`surface/surfaceDim/surfaceContainerLowest` → 纯黑，其余 container 级整体下移 `surface.tone`。
8. **语义扩展色**（`color.dart:227-229, 313-337`）：
   - success = `0xFF4CAF50` palette（tone 40 亮 / 80 暗），successContainer tone 90/30；
   - warning = `0xFFFF9800` palette（同上 tone 规则）；
   - `destructive`（iOS systemRed）：暗 `0xFFFF4245` / 亮 `0xFFFF383C`；
   - `modalScrim = alertScrim`：黑色 scrim，暗色 alpha **0.48**、亮色 **0.20**（对齐 iOS 27 的 sheet/alert 压暗）。

### 1.2 Surface 层级体系（亮/暗不对称，是本项目最重要的质感规则）

`prism_ui/lib/surface.dart:10-34`（`SurfaceLevel` 四级 + `colorOf`）：

| SurfaceLevel | 亮色 | 暗色 | 典型用途 |
|---|---|---|---|
| `page` | **surfaceContainer** | **surface** | 页面背景、snap sheet 底色（`snap_sheet.dart:912`） |
| `card` | **surfaceBright** | **surfaceContainer** | 卡片、列表分组（亮色卡片比页面更亮，暗色比页面更亮一档 tone 15） |
| `block` | surfaceContainer | surfaceContainerHigh | 卡内区块（tone 21） |
| `inset` | surfaceContainerHighest | surfaceContainerHighest | 内嵌块/键位（tone 27） |

- `floatingOf`：暗色浮动件（导航 dock、搜索条）用 `block`，亮色用 `card`（`surface.dart:16-17`）。
- **暗色从不回退**（注释 `surface.dart:8-9`）：暗色永远只升不降。
- **弹层整体抬升**：暗色下 sheet 打开时整套 ColorScheme "抬升"（`elevated`，`surface.dart:75-95`）：surface→container，container→high，high→highest，highest 再按差值上移；`lib/manager/theme_manager.dart:47-49` 将其设为 `elevatedColorScheme`，`SnapSheetRoute.buildPage` 里用 `ElevatedSurfaceTheme` 包裹（`snap_sheet.dart:197-203`）。亮色不抬升。
- `chromeSurface`：暗 `surfaceContainerLow` / 亮 `surfaceContainerHighest`（`surface.dart:69-73`）。

### 1.3 ThemeData 与组件默认

`prism_ui/lib/theme_data.dart`：
- 桌面平台默认 `VisualDensity.compact(-2,-2)` + `TapTargetSize.shrinkWrap`；移动端 `standard` + `padded`（:125-141）。
- `scaffoldBackgroundColor = SurfaceLevel.page.colorOf`（:143）。
- 图标默认色：亮 `0xDD000000` / 暗 `Colors.white`（:174-178）。
- 主题切换动画时长 `kThemeAnimationDuration = 200ms`（:12）；主题变更时整树交叉淡入淡出（`theme_cross_fade.dart`，注释见 `theme_manager.dart:14`）。

### 1.4 字体与字号体系

**字体家族**（`prism_ui/lib/theme_data.dart:94-108` `systemFontOf`）：
- Android/Fuchsia：`Roboto`；iOS：`CupertinoSystemDisplay`（≥20pt 用 Display）/`CupertinoSystemText`；macOS：`.AppleSystemUIFont`；Windows：`Segoe UI`；Linux：`Roboto`（fallback Ubuntu/Cantarell/DejaVu Sans/…）。
- 中文 Han 字形按 UI locale 选择家族 fallback（`prism_ui/lib/font.dart:8-79`）：Windows→Microsoft YaHei UI，macOS→PingFang SC，Linux→Noto Sans CJK SC 等。
- 内置字体：`JetBrainsMono`（代码编辑器）、`Twemoji`（emoji 渲染）（`pubspec.yaml:108-114`）。
- 可由用户覆盖 `fontFamily`（`theme_manager.dart:19-21,39-50`）。

**字号体系 = iOS Dynamic Type（默认档），1pt = 1dp，另加两档**（`prism_ui/lib/typography.dart:7-27`）：

| AppTextStyle | size (dp/sp) | 行高 | 默认 weight | emphasized weight | 用途（实际代码） |
|---|---|---|---|---|---|
| display | 40 | 44 | w400 | **w700** | 仪表盘大数字 |
| largeTitle | 34 | 41 | w400 | w700 | — |
| title1 | 28 | 34 | w400 | w700 | — |
| pageTitle | 24 | 30 | **w600** | w700 | 页面 AppBar 大标题（`scaffold.dart:782`） |
| title2 | 22 | 28 | w400 | w700 | — |
| title3 | 20 | 25 | w400 | w600 | 页面标题（compact 用 headline） |
| headline | 17 | 22 | **w600** | w700 | 分组头 ListHeader、卡片头、alert 标题 |
| body | 17 | 22 | w400 | w600 | 列表项标题（`context.dart:55-56`） |
| callout | 16 | 21 | w400 | w600 | — |
| subheadline | 15 | 20 | w400 | w600 | 列表副标题、卡片内容、按钮文案、Sheet 内容 |
| footnote | 13 | 18 | w400 | w600 | 列表 footer、dashboard 卡片标题 |
| caption1 | 12 | 16 | w400 | w600 | 导航栏 label、chip、时间 |
| caption2 | 11 | 13 | w400 | w600 | 最小注记（MetaChip） |

- 全部 `letterSpacing: 0`、`height = leading/size`、`leadingDistribution: even`、baseline alphabetic（`typography.dart:61-66`、`theme_data.dart:183-195`）。
- 颜色装饰扩展（`prism_ui/lib/text_style.dart:5-12`）：`toLight` = alpha 0.8、`toLighter` = 0.6、`toTabular` = 等宽数字（`FontFeature.tabularFigures()`，所有流量/延迟数字都用）。
- 文本缩放：允许 0.8–1.4（`lib/common/constant.dart:28-29`）；AppBar 标题最多放大 1.34（`scaffold.dart:581`）；**内边距随文字缩放半速**：`ap()/mAp()` = `1 + (textScale-1)*0.5`（`metrics.dart:5-9`）。
- 常用文本角色（`context.dart:55-68`）：listTitle = body/onSurface；listSubtitle = subheadline/onSurfaceVariant；listCaption = footnote/onSurfaceVariant；cardHeader = headline/onSurfaceVariant·α0.8。

---

## 2. 形状系统（连续圆角）

### 2.1 实现方式：Flutter 框架级 `RoundedSuperellipseBorder`（iOS 风格平滑圆角）

- 全项目**没有一处** `RoundedRectangleBorder` 作为组件默认形状；统一用 Flutter 3.32+ 框架类 **`RoundedSuperellipseBorder`**（绘制 RSuperellipse 超椭圆连续曲率角），见 `prism_ui/lib/shape.dart:67-108`（`AppShape` 全是 `RoundedSuperellipseBorder`）与渲染端 `canvas.drawRSuperellipse / ClipRSuperellipse`（如 `snap_sheet.dart:1079,1270`、`list_selected.dart:106`）。
- 证据：`shape.dart:68-108`（`AppShape.none/sm/md/… = RoundedSuperellipseBorder(...)`）；Flutter 官方文档 https://api.flutter.dev/flutter/painting/RoundedSuperellipseBorder-class.html 。
- 输入框描边另有封装 `AppInputBorder`（`shape.dart:110-190`），内部同样包 `RoundedSuperellipseBorder`，默认 radius = `AppRadius.md(16)`。
- 另一套 **M3 Expressive 圆角多边形**（cookie 形、12 边形等）在 `prism_ui/lib/shapes/`（Material rounded-polygon 移植），用于仪表盘开关图标的"徽章"：`MaterialShapes.cookie6Sided` ↔ `circle` 切换（`lib/views/dashboard/widgets/card.dart:730-752`）。

### 2.2 圆角半径常量表（`prism_ui/lib/shape.dart:4-27` `AppCorner`）

| Token | dp | 使用处（证据） |
|---|---|---|
| none | 0 | 列表内行（`list_selected.dart:6-13`） |
| xs | 4 | 延迟标签（`lib/views/proxies/card.dart:322`） |
| sm | 8 | TonalChip/MetaChip（`chip.dart:112,229`） |
| **md** | **16** | **默认卡片**（`card.dart:208`）、输入框（`shape.dart:91`）、slider 气泡（`slider.dart:296`） |
| lg | 20 | 代理卡（`lib/views/proxies/card.dart:201`）、MoreActionButton（`button.dart:38`） |
| **xl** | **24** | 分组列表首/尾行、OpenContainer 收起卡、拖拽提升的行（`list_selected.dart:6-13,107`）、卡片式 sheet 内卡 |
| xxl | 28 | 无系统圆角报告时的 sheet 兜底角（`sheet.dart:59-64`） |
| alert | **34** | iOS 27 UIAlertController 同款（对话框，`shape.dart:14`；`dialog.dart` 用 `_AlertFrame`） |
| full | 1000 | 胶囊：所有按钮、开关轨道、导航指示器（`common_button.dart:283,362`） |

**专属超 scales**：
- Sheet 圆角 `_sheetCorner = 38`，浮起时 bottom 角跟随设备屏幕圆角插值，浮动 inset 8dp（`sheet.dart:50-74` `floatingSheetRadius` + `sheetFloatInset`）。
- Dashboard 卡：桌面 **32** / 移动 **34**，区块 block 20/22，tab 16，表格头 12，徽章 14（`lib/views/dashboard/style.dart:105-115`，注释明确说明"仪表盘卡比 sheet 更大"）。
- 小圆角：Checkbox 盒 2dp（`checkbox.dart:29`）、slider 刻度内角 2dp（`slider.dart:331`）。
- 自适应保护：`AppCorner.fit(shortestSide)` = 最短边/3 向下取档（`shape.dart:17-26`）。

### 2.3 组件圆角速查

| 组件 | 圆角 | 证据 |
|---|---|---|
| Card（含列表项） | md 16（可传覆盖） | `card.dart:208` |
| 分组列表行 | 首行 top 24 / 尾行 bottom 24 / 中间 0 | `list_selected.dart:6-13` |
| FilledButton/OutlinedButton/TextButton/IconButton | full 1000（胶囊） | `common_button.dart:283,362` |
| TonalButton(组) | full（胶囊），fill = raised surface | `button.dart:147-153,377-396` |
| Chip | full；TonalChip/MetaChip sm 8 | `chip.dart:35,112,229` |
| Bottom sheet | top 38（或≤屏幕圆角），浮起后 bottom 38 | `sheet.dart:56-74`、`snap_sheet.dart:1268-1276`（ClipRSuperellipse） |
| 对话框 | alert 34 | `dialog.dart` + `shape.dart:14` |
| 输入框 | outline 默认 md 16；FilledInputTheme 圆胶囊 full | `shape.dart:113`、`input.dart:244-279` |
| Switch | 轨道/拇指均 full | `switch.dart:225-243` |
| Slider | 轨道 8、拇指胶囊、气泡 md 16 | `slider.dart:330,428,296` |
| 导航指示器 | 64×32 胶囊 | `navigation_bar.dart:67,97-101` |
| Dashboard 卡 | 32/34；块 20/22 | `style.dart:107-115` |
| 代理卡 | lg 20 | `lib/views/proxies/card.dart:201` |

---

## 3. 组件规格

### 3.1 卡片 Card / 列表行

**Card**（`prism_ui/lib/card.dart:108-259`）：
- 最小尺寸 64×40，触控目标 48（:141-142）；padding 默认 0（由内容决定），`SettingTextCard` 用 all 12（`lib/ui/setting_text_card.dart:22-23`）；代理卡 h12（`card.dart:206`）；Dashboard 卡 padding standard 22/20、compact 20/16、mobile 20/18（`lib/views/dashboard/widgets/card.dart:14-27`）。
- 填充 = `context.raisedSurfaceLevel.colorOf`（SurfaceScope 层级+1，即 page 上的卡 = card 层色）；文本 `subheadline emphasized` 色 onSurfaceVariant，图标 primary、size **20**（`card.dart:104,179-193,219-228`）。
- 选中态：`isSelected && fillsSelection` 时填 **primary**、内容 onPrimary（:177-206）；错误态填 errorContainer（:203-205）。
- 按压反馈 = `PressSurface`（ink 10%，见 §4.4）；入场动画可选 `FadeScaleEnterBox`（:231）。
- 卡片头 InfoHeader：行高 = cardHeaderHeight，图标 20 + 间距 12，标题 headline/onSurfaceVariant·α0.8（:28-102、`context.dart:66-68`）；Dashboard 卡头：高 24（移动 26），glyph 18，间距 8（`dashboard/widgets/card.dart:92-167`）。

**ListRow（列表行，无自反馈）**（`prism_ui/lib/list_row.dart:16-100,200-204`）：
- padding 水平 16；标题-图标 gap 16；`minHeight = 56`、`minVerticalPadding = 10`（`metrics.dart:21-22`）；
- 双行几何：标题基线 y=32、副标题基线 y=52，leading 最小宽 24、trailing 最小宽 32、side 最大高 56（:200-204）；
- 标题 body/onSurface，副标题 subheadline/onSurfaceVariant，leading/trailing caption2 emphasized（:49-53、`context.dart:55-64`）。

**分组列表（iOS inset grouped 风格）**：
- 行包在 `Card(shape = 首尾 xl/其余 none)` 里（`list_selected.dart:62-99`）；
- 分隔线：0 厚 hairline，色 `onSurface α0.14`（`divider.dart:11-15`），从**标题起始端**缩进 `endIndent: 14`（`list_selected.dart:66`），按压高亮时本行与相邻行的分隔线隐藏（`list_row_separator.dart:1-7`）——iOS 行为；
- 分组头：`headline`/onSurfaceVariant，padding L16 R16 **T24 B8**（`metrics.dart:17-19`），最多 2 行（`list.dart:31-108`）；footer：footnote，L16 R16 T8（:111-125）；
- 页面横向 padding = 16（`lib/views/config/general.dart:400`、`profiles.dart:654`）；Dashboard 页边距 fromSTEB(16, contentTopPadding, 24, 24+bottom)（`dashboard.dart:132-137`）。
- ListItem 业务封装（`lib/ui/list_item.dart:77-385`）：`ListItem.toggle/.checkbox/.options/.input/.open`；options 行尾是带 picker 图标的 TextButton（:389-420）；`.open` 移动端用 **OpenContainer 容器变换**展开整页（:280-297）。

### 3.2 按钮（全部胶囊 + iOS 式按压）

公共默认（`prism_ui/lib/common_button.dart:351-372` `_defaultsOf`）：`shape = AppShape.full`，`minimumSize 64×40`，字号 subheadline emphasized，图标 18，`pressLook = PressLook.tint`（无 ripple），文字缩放自适应 padding。

| 按钮 | 前景 | 背景 | padding(1x) | 证据 |
|---|---|---|---|---|
| FilledButton | primary 上 onPrimary；禁用 onSurface·α0.38 / bg onSurface·α0.12 | primary | h24；带图标 16/24 | `common_button.dart:486-521,472-484` |
| OutlinedButton | primary | 透明 + 1dp `outline` 描边（禁用 α0.12） | 同 Filled | `common_button.dart:523-561` |
| TextButton | primary | 透明 | h12 v8；带图标 12/8/16/8 | `common_button.dart:428-470` |
| IconButton | onSurfaceVariant（选中 primary） | 透明（`.filled` = primary/onPrimary） | all 8，min 40×40，icon 24 | `common_button.dart:261-293` |
| TonalButton / AppBar 按钮组 | onSurface | raised surface 胶囊；尺寸 bar 44/icon24、section 36/20、compact 32/20 | h14(填充) | `button.dart:50-60,359-398,472-505` |
| 按钮组 hover 标记 | — | onSurface α0.08 胶囊，内缩 h2 v4，滑移弹簧 stiffness 2350 ratio 0.95 | — | `button.dart:62-74` |
| 禁用统一规则 | fg onSurface·α0.38 | bg onSurface·α0.12 | — | `common_button.dart:421-426` |

- FilledButton 焦点环外扩（`ringStandsOff: true`，`common_button.dart:515`）；焦点环 = 2dp primary，外扩 1dp 间隙（`surface.dart:129-139`）。
- AppBar 布局：动作间距 4、组间 8、容器边距 compact 16 / regular 20（`scaffold.dart:434-468`）。

### 3.3 Snap Sheet（底部弹层，本项目交互核心）

`prism_ui/lib/snap_sheet.dart` + `sheet.dart` + `sheet_header.dart`：

- **Detents（吸附高度）**：`snapSheetDetents = [9/16, 0.9]`（占可用高度），即两档：半屏 56.25% 与 90%（`snap_sheet.dart:26`）。`showSheet` 打开的表单型 sheet `fitsContent: true`：内容矮于第一档时按内容高度，但**不会低于第一档**（`sheet.dart:129-144`、`snap_sheet.dart:394-409`）。
- **弹簧**：UIKit presentation spring，`mass 3 / stiffness 1000` 临界阻尼（ω=√(1000/3)≈18.26 rad/s；`snap_sheet.dart:29-32` 注释 + `_stiffness = 1000/3`），自定义解析解 `_SheetSpring` 保证精确落点（:253-304）；投掷（fling）释放按 UIScrollView `decelerationRate.fast` 投影（:39-41）。
- **呈现动画**：`transitionDuration 500ms`（:190-194），barrier 颜色为 `modalScrim`（暗 0.48 黑）。
- **拖拽行为**：
  - 内容滚动与 sheet 拖动手势接续（`prefersScrollingExpandsWhenScrolledToEdge`，`snap_sheet.dart:1529-1632`）：上滑先长高 sheet 到最高档再滚内容，一气呵成；
  - 超过最高档的橡皮筋 `_rubberBandExtent 100 / coefficient 0.55`（:42-44）；下拉越过最低档时整个路由跟随下拉（拖掉 sheet），barrier 同步（:668-692）；
  - 滚轮一次一格 detent，500ms 内合并为一次（:55,893-907）；
  - 键盘弹出把最高档抬高，浮动高度 `_keyboardUnfloat = 120`（:59-61,440-445）。
- **堆叠 sheet**（sheet 之上再开 sheet）：下方 sheet 顶部缩进 `stackedSheetPeek 10dp`、缩放 `stackedSheetScale 0.9204`（`sheet.dart:45-48`），scrim 乘 `0.6`（`snap_sheet.dart:57,84-89`）；只有下方处于最高档才整体缩小（:96-98）。
- **圆角与裁剪**：`ClipRSuperellipse` + `floatingSheetRadius(显示圆角, float)`（top ≤38、bottom 随浮起 lerp 到 38）（`snap_sheet.dart:1268-1276`、`sheet.dart:56-74`）；底色 = `SurfaceLevel.page`（`snap_sheet.dart:912`），暗色下整 sheet 用 elevated 色板（:197-203）。
- **把手（grabber）**：宽 **28**、厚 **4**、顶部留白 **6**、全圆角（r=2），色 `onSurfaceVariant`（`sheet_header.dart:13-37`）；把手 + 工具栏 + 余量合计 `sheetAppBarHeight 68`（`metrics.dart:25`、`sheet_header.dart:41-67`）。
- **浮动头部**：AppBar 用 `FloatingHeader`：背景 α0.9，从 50% 高度起分 8 步按 easeInOut 淡出渐变（内容滚过时透出）（`sheet_header.dart:69-133`）。
- 桌面/宽屏变体：同一 API 转为侧边 sheet / 居中 form sheet（`sheet.dart:93-127`）。

### 3.4 导航栏

**M3 风格 NavigationBar（非浮动模式）**（`prism_ui/lib/navigation_bar.dart`）：
- 高 **80**（:28）；填浮层色 `floatingOf`（:33-35）。
- 项：指示器 **64×32 胶囊**，选中时从 0 以 **scaleX 展开动画 500ms `Curves.easeInOutCubicEmphasized`**（:67-106）；图标 24，选中 primary 未选 onSurfaceVariant；label caption1 emphasized，选中 onSurface 否则 onSurfaceVariant（:107-131）；图标有填充变体 AnimatedGlyph（`home.dart:125-128`）。

**浮动 NavigationDock（iOS 式浮动 dock + 放大镜）**（`prism_ui/lib/navigation_dock.dart`）：
- 条高 **62**、内边距 4、左右外边距 21、阴影余量 8、项最大宽 86、lens 最小宽 84、图标 **28**、label 10/14.5（:24-37）；
- 选中"lens"随手指/悬停弹性移动：press 弹簧 (k=311, ζ=0.593)、跟随 (1024, 0.9)、jelly (100, 0.4)、settle (500ms, bounce 0.32)、hover (260ms, bounce 0.18)（:63-104）；
- 放大：lens 增长 8dp、放大 0.16、按压膨胀 1/8（≤16dp）、悬停磁性 0.2 / 视差 0.5 / α0.08、辉光 reach 28 peak 0.06（:38-58）；
- 阴影 `dockShadows`：`shadow α0.08 blur24 y8` + `α0.04 blur3 y1`，暗色强度 ×3（:236-251）；背衬可为 LiquidGlass（:254-286）。

### 3.5 文本输入框

`prism_ui/lib/text_field.dart` + `input.dart` + `shape.dart`：
- 最小高度 **48**（:18）；outline 模式 padding STB E = 12/20/12/12（dense 16/8）（:688-709）；filled 模式 h12 v8。
- 默认描边 `AppShape.input`（AppInputBorder，圆角 16）（`shape.dart:91,113`）；边色规则（:365-389）：未聚焦 1dp（未填 `outline`/已填 `onSurfaceVariant`），**聚焦 2dp primary**，hover onSurface，error `error`；边框过渡 **167ms**（:22），hint 淡出 20ms（:23）。
- FilledInputTheme（对话框/独立输入）：填 `raisedSurfaceLevel` 色、四态无边框，聚焦时 2dp primary 描边胶囊（radius full）（`input.dart:242-283`）；密码显隐按钮 `Glyphs.eye/eyeOff`（:50-76）。
- 前后缀图标 24、与文本 gap 4（:19-21,420）；错误/计数行间距 4。

### 3.6 开关 / 滑块 / 复选框

**Switch**（`prism_ui/lib/switch.dart:27-43,164-267`）：
- 轨道 **52×32** 全圆角；拇指 24；按压拇指长到 **40 并横向拉伸 (10,-2)**，中途态 34×22；
- 颜色：轨道 `surfaceContainerHighest → primary` lerp；拇指 `outline → onPrimary`；按住时拇指变**纯白**并加阴影 `α0.2·lift, y2 blur6`（:195-231）——iOS 实心拇指质感；
- 动画：toggle **300ms `Curves.easeOutBack`**（过冲回弹）、按压 **150ms easeOutCubic**、点击抬升保持 200ms（:41-43）；支持拖动切换（:121-154）。

**Slider**（`prism_ui/lib/slider.dart:320-436`）：
- 默认"断轨"式：轨道高 **16**（圆角 8）、拇指为高 **44** 的胶囊竖条（宽 4，按下 2）、**gap 6**；激活段 primary、未激活 `secondaryContainer`；刻度圆点 r2；激活动画 100ms（:62,325-332）；值气泡 32 高、md16 圆角、inverseSurface（:255-257,283-305）。

**Checkbox**（`prism_ui/lib/checkbox.dart:18-73`）：
- 触控目标 48、盒 **18**、方角 2（或圆形）；选中填 primary、勾线 onPrimary 宽 2；150ms `easeOut` 逐段绘制勾（:97-131）；多态（半选横线）。

### 3.7 Chip / 标签

（`prism_ui/lib/chip.dart`）
- `Chip`：高 **36**（section 档）、胶囊、padding L16 R16/12（可删）、间距 6、删除图标 16 close、文案 subheadline emphasized；选中填 primary/onPrimary（:33-69）。
- `TonalChip`：padding h6 v1、sm 8 圆角、caption1 emphasized（:73-125）。
- `MetaChip`：raised surface 底、sm 8、padding h4 v2、caption2 emphasized（:218-244）。
- Dashboard chip：高 24/28、胶囊、h10/12、圆点 6（`dashboard/widgets/card.dart:436-487`）。

### 3.8 分割线 / 空状态 / 加载态 / Tooltip

- **Divider**：占位高 16、线厚 0（hairline），色 `onSurface α0.14`（`divider.dart:5-32`）；列表行分隔线缩进 14（§3.1）。
- **空状态 NullStatus**：插图 + 文案 stagger 入场（每项 **60ms**、上浮 **12dp**），紧凑高 560；退出 150ms；loading 判定延迟 400ms（`null_status.dart:73-74,265-267`）。
- **加载**：顶部 LoadingBar 贴 AppBar 底（`scaffold.dart:258-268`）；`ActivityIndicator` 弧线循环 **3200ms**、3 圈 2 段拉伸、sweep 0.1–0.62、线宽 1.5–4（`loading.dart:151-162`）；"relay"三点跳变循环 1200ms（:227-239）。
- **Tooltip**：垂直偏移 24（`tooltip.dart:24`）。

### 3.9 对话框（iOS Alert 风格）

`prism_ui/lib/dialog.dart`：
- 宽 **348**、内容最大 300、文字 inset 30、动作 inset 16、动作高 48、动作 gap 8（:20-27）；
- 结构：标题居中（headline）/ body（subheadline, onSurfaceVariant 暗全亮、亮 α0.85）；**最后一个动作 filled**，破坏性动作用 `destructive` 红并红底（:361-384,639+）；
- 入场：404ms 总时长，scale 1.2→1，弹簧 275ms + SpringCurve（:35-41）；barrier = `alertScrim`（暗 0.48 / 亮 0.2）。
- 表单行内输入圆胶囊（`_actionHeight/2 = 24` 圆角）（:442-451）。

### 3.10 AppBar / 页面框架

- 页面工具栏高 **72**、sheet 工具栏 **48**、sheet 总 AppBar 高 **68**（`metrics.dart:23-25`）；bar 无自身表面，浮在内容上（`scaffold.dart:564-699`），页顶渐隐遮罩（`FloatingHeader`，`_headerOverhang 16`，`scaffold.dart:437`）。
- 标题：页面 `pageTitle(24/30 w600)`、紧凑 `headline(17)`（`lib/ui/scaffold/scaffold.dart:776-782`）；leading 槽宽 = 44 + 2×边距（:446-447）；动作 24dp onSurfaceVariant（`scaffold.dart:648-655`）。
- 页面切换：全部卡片路由共享 iOS 27 push（见 §4.2）；底部 docked 搜索条高 `BottomInsetScope.dockedSearchHeight`，胶囊浮层 + dockShadows（`lib/ui/scaffold/scaffold.dart:341-444`）。
- 搜索动画：按钮→整栏搜索的 droplet/pinch 变形（`scaffold.dart:167-211`）。

---

## 4. 动效系统

### 4.1 缓动与时长总表

**M3 Easing 常量**（`prism_ui/lib/theme_data.dart:25-32`）：
`emphasizedDecelerate Cubic(0.05,0.7,0.1,1)`、`legacyDecelerate Cubic(0,0,0.2,1)`、`standard Cubic(0.2,0,0,1)`、`standardAccelerate Cubic(0.3,0,1,1)`、`standardDecelerate Cubic(0,0,0,1)`。

**常用 Curve**：`Curves.fastOutSlowIn`（填充过渡/affix 淡入 `press_surface.dart:377`、`text_field.dart:436`）、`easeInOut`（主题/tint 释放/渐变）、`easeOutCubic`（选中态 `dashboard/widgets/card.dart:397`）、`easeInOutCubicEmphasized`（导航指示器）、`easeOutBack`（Switch）、`easeOut`（Checkbox）、`Curves.easeOut`（Tab 切换 `home.dart:327`）。

**标准时长**（`metrics.dart:31-32`）：`midDuration 200ms`、`commonDuration 300ms`；其它关键值：

| 动效 | 时长 / 弹簧 | 证据 |
|---|---|---|
| 主题切换 | 200ms | `theme_data.dart:12` |
| 按压 ink 淡入/淡出 | 200ms（ink linear，tint 释放 250ms easeInOut） | `press_surface.dart:91-93,300-305` |
| 填充色过渡 | 200ms fastOutSlowIn | `press_surface.dart:91,377` |
| 滚动视图内按压高亮延迟 | 150ms | `press_surface.dart:90` |
| 页面 push/pop | 临界阻尼 300ms 弹簧，初速 9×距离/s | `page_transition.dart:14-20` |
| OpenContainer 展开/收起 | 开 340ms 临界、收 340ms bounce 0.08 | `open_container.dart:160-173` |
| 交互式收起变形 | morph 400ms / scale 420ms b0.2 / place 450ms b0.18 / 回弹 250ms | `open_container.dart:199-215` |
| Sheet 呈现 | 500ms（内部弹簧见 §3.3） | `snap_sheet.dart:190-194` |
| 导航指示器 | 500ms easeInOutCubicEmphasized | `navigation_bar.dart:68,92` |
| Tab 切换 | fade 220ms easeInOut 或滑动 kTabScrollDuration easeOut | `motion.dart:6-7`、`home.dart:323-328` |
| Switch | toggle 300ms easeOutBack / press 150ms | `switch.dart:41-42` |
| Checkbox | 150ms easeOut | `checkbox.dart:21` |
| 输入框边框 | 167ms | `text_field.dart:22` |
| 侧栏/底部导航显隐 | 300ms（进 standardDecelerate / 出 standardAccelerate） | `animated_visibility.dart:6,73-75` |
| 列表项增删 | 300ms（size easeInOutCubic + fade Interval(0.5,1)） | `animated_entries.dart:133-134,192` |
| 连接按钮选中 | 240ms easeOutCubic | `dashboard/widgets/card.dart:251,396-397` |
| 对话框 | 404ms / 弹簧 275ms | `dialog.dart:35-41` |
| Android ink sparkle | 617ms（shader） | `press_sparkle.dart:105` |
| 禁用动画 | `MediaQuery.disableAnimations` → 全部 0 | `context.dart:50-53` |

### 4.2 页面切换动画（iOS 27 push/zoom）

`prism_ui/lib/page_transition.dart`：
- push：新页为"卡片"，临界阻尼 **300ms** 弹簧、初始速度 9×距离/s（:14-20）；被覆盖页**回退平移 30%**（`_coveredSlide 0.3`）并压暗 10% 黑（:38-39）。
- zoom 变体（OpenContainer 的 covered 效果）：覆盖页向中心收缩 **34.667dp 宽（最小 0.8 倍）** 并压暗 15%（:41-45）；卡片投影为 4% 黑、σ12 的高斯 6 段渐变（:50-56）。
- `HomeRoute` 为默认路由（:414；`application.dart:195-198`），并禁用 Hero 双重转场（`delegatedTransition → null`，:106-112）——**项目不用 Hero，用 OpenContainer**。
- `OpenContainer`（`open_container.dart:160-215`）：closed tile → open page 的容器变换：内容淡入/淡出占行程 65%（`_fadeEnd 0.65`），阴影从 σ3/dy-3 长到 α0.4/σ40/dy10；支持手指拖拽关闭（slop 10、越 60% 缩 0.4、150dp+0.1s 速度即关闭）。
- 拖拽返回：`drag_back.dart` 混入 route（`page_transition.dart:157-160`）。

### 4.3 列表动画

- 增删：`AnimatedEntries`/`EntryTransition`——高度收放 `Curves.easeInOutCubic`、透明度从行程 50% 起，300ms（`animated_entries.dart:132-158,192`）；被删项先占位收起，其余行合拢（grouped, :161-234）。
- 入场 stagger（空状态）：60ms 步进 + 12dp 上浮（`null_status.dart:265-267`）。
- **无逐项 stagger 的长列表入场**；Dashboard 卡为静态布局 + 重绘边界（`dashboard.dart:154-163`）。

### 4.4 点击反馈（PressSurface —— 最核心的交互原语）

`prism_ui/lib/press_surface.dart` + `card_surface.dart` + `press_sparkle.dart`：
- 两种形态 `PressLook`（`card_surface.dart:10-18`）：
  - `ink`（Card/列表行/透明面）：内容色 **α0.10** 的 ink 罩填充色（`press_surface.dart:387,417-418`）；Android 上叠加 **ink sparkle shader**（617ms，`press_sparkle.dart:8-13,105`）；
  - `tint`（所有按钮）：按住时 iOS 27 式整体提亮——**亮色透明度降至 75%**（`_tintFade 0.25`）、**暗色混入 20% 白**（`_tintLift 0.2`）（`card_surface.dart:193-196,646-677`）。
- 悬停：onSurface **α0.04**（桌面，`press_surface.dart:385-386`）。
- 在可滚动容器内延迟 **150ms** 再高亮（UIScrollView 行为，:89-90,188-196）；手指仍在滚动容器中松开过快则不显示按压。
- 填充色/描边/前景色三者在 200ms fastOutSlowIn 下互相插值（:363-381）。
- 触感：`Feedback.forTap`（:269）。
- **ElasticPress 弹性按压**（dock/tonal 按钮）：最长边按 `min(2/8, 16/side)` 膨胀 + 指尖拖拽 rubber band（限幅 `7/32` 最短边、系数 0.55），弹簧 (k=311, ζ=0.593)（`navigation_dock.dart:38-70,449-451,573-584`）。

---

## 5. 质感 / 细节

### 5.1 阴影规范（低高度、双影、偏 iOS）

| 场景 | 阴影 | 证据 |
|---|---|---|
| Dock/浮动搜索条 | `shadow α0.08 blur24 y8` + `α0.04 blur3 y1`；暗色 ×3 | `navigation_dock.dart:236-251` |
| Dashboard 卡 e1 亮 | `α0x0D y0.5 b1` / `α0x0A y2 b6` / `α0x17 y12 b32 spread-6`（CSS blur 换算 σ） | `lib/views/dashboard/style.dart:66-94` |
| Dashboard 卡 e1 暗 | `α0x80 y1 b2` + `α0x8C y10 b28 spread-6` | `style.dart:94` |
| Dashboard 卡 e2 | 亮 3 层最深 `α0x38 y32 b64 -12`；暗 `α0x80 y2 b6`+`α0xB3 y32 b64 -12` | `style.dart:96-102` |
| OpenContainer 展开 | α0.4 σ40 dy10 | `open_container.dart:174-179` |
| Switch 按住 | α0.2 y2 blur6 | `switch.dart:213-220` |
| 普通卡片 | **无阴影**（靠 surface 色阶分层） | `card.dart:196-233` |

### 5.2 描边

- 焦点环 2dp primary（外扩 1dp，`surface.dart:129-139`）；OutlinedButton 1dp outline；输入框聚焦 2dp；选中卡无描边（用填充）。其余组件一律无描边、无 elevation 分层。

### 5.3 内边距体系

- 页面水平 16；分组头 T24/B8；Dashboard fromSTEB(16, contentTopPadding, 24, 24+bottom)；
- 行内水平 16；卡片内容 16（`metrics.dart:14-15`）；分组行之间 0（连续面 + 分隔线）；
- 按钮内边距见 §3.2；AppBar 动作边距 16/20、间距 4/8；
- 半速文字缩放系数 `mAp`（`metrics.dart:8`）。

### 5.4 深浅色差异速查

| 维度 | 亮色 | 暗色 |
|---|---|---|
| 页面/卡片 | page=surfaceContainer（灰），card=surfaceBright（白，更亮） | page=surface（近黑），card=surfaceContainer（tone15） |
| 图标/正文 | 0xDD000000 | white |
| scrim | 0.20 | 0.48（堆叠 sheet 再 ×0.6） |
| 按压 tint | 不透明度 75% | +20% 白 |
| 浮动层级 | card 色 | block 色 + ColorScheme 整体抬升 |
| 阴影强度 | 1× | 3× |
| 纯黑选项 | — | surface 系全黑（pureBlack 设置） |

### 5.5 图标风格

- 自研 **SF Symbols 风格矢量 glyph 库**（`prism_ui/lib/icons/`）：24dp 网格、**描边 1.8**、描边居中（非填充轮廓）、支持 `fill` 参数切换实心（选中态）与方向翻转（`glyph.dart:8-16`、`glyph_icon.dart`；RTL/RTT：`Glyphs.backFor(platform)`）。
- 导航栏图标选中切实心（`AnimatedGlyph`，`home.dart:125-128`）；动效 glyph（`icons/animated_glyph.dart`）。
- M3 Expressive 形状徽章（cookie6 等）作开关行图标容器（`dashboard/widgets/card.dart:730-752`）。

---

## 6. 迁移到 Jetpack Compose 的映射建议清单

> 目标：在 Compose 项目中复刻上述体系。建议自建一个 `prism` 设计包（对齐 prism_ui 的分层），而不是直接散落在 MaterialTheme 里。

### 6.1 主题系统

| Flutter 侧 | Compose 实现要点 |
|---|---|
| `seededColorScheme`（HCT 自定义 tone） | 引入 material-color-utilities（Java 官方移植）或 `com.kmpalette.materialkolor`；写 `FlClashColorScheme(seed, brightness, style)`：复刻常量——中性面 chroma ≤3（暗）/≤1.5（亮）、primaryContainer chroma ≤48 & tone 90/30、暗色 onSurface tone 97、container 系 15/21/27、accent tone clamp 亮 35–60 / 暗 50–75(+3)、success/warning 0xFF4CAF50/0xFFFF9800 palette 40/80+90/30（`color.dart:139-165,227-229`） |
| 动态取色 seeds | Android 12+：`dynamicLightColorScheme(context)` 只给角色不给 seed；需要 light/dark **双 seed** 时用 material-color-utilities 的 `QuantizerCelebi + Score` 从 wallpaper 提取，或直接用 `MonetColorExtractor`；非动态回退 0xFF007AFF |
| 纯黑模式 | `colorScheme.copy(surface=Black, …)`：把 container 系整体 `shiftTone(-surfaceTone)`（`color.dart:294-311`） |
| `SurfaceLevel` | 自建 `enum SurfaceLevel { Page, Card, Block, Inset }` + `@Composable fun colorOf(scheme, dark)`，映射表照抄 §1.2；提供 `CompositionLocal<SurfaceLevel>`（对应 SurfaceScope） |
| elevated 色板（sheet 抬升） | 在暗色 sheet 的 CompositionLocal 里换一套 copy 后的 scheme（`surface.dart:75-95` 的 shiftTone 逻辑） |
| `AppTextStyle` | 自建 `enum PrismTextStyle(size, leading, weight, emphasizedWeight)` + `@Composable fun of(emphasized)`，产出 TextStyle；尺寸表照抄 §1.4；提供 `LocalPrismTextStyle`；tabular figures：`fontFeatureSettings = "tnum"` |
| 系统字体 | Android 上 Roboto 即默认；Han fallback 交给系统；`fontFeatureSettings` 处理 tabular |
| 文本缩放半速 padding | `fun Float.mAp(density, fontScale) = this * min(1f, 1f + (fontScale-1)*0.5f)` |

### 6.2 形状（连续圆角）

| Flutter 侧 | Compose 实现要点 |
|---|---|
| `RoundedSuperellipseBorder` | Compose 无内建平滑圆角。三个方案：① **androidx.graphics:graphics-shapes** 的 `RoundedPolygon`（M3 Expressive 同源实现，支持每角 radius+smoothing，`MaterialShapes` 在 material3 1.4+ 也有）→ 包成 `Shape`；② 自写超椭圆 Path：`|x/a|^n + |y/b|^n = 1`，n 取 4.2–5.0（iOS 视感 ≈4.83，Figma smoothing 0.6 等价），按角分段贝塞尔；③ 折衷：`RoundedCornerShape` 仅用于极小半径（≤4）。封装 `sealed class AppShape(radius)` 返回 Outline.Generic(path)，并实现 `RsuperellipseShape.top(radius)`/`.vertical(top,bottom)`/`.pill` |
| 半径 token | `object AppCorner { none=0, xs=4, sm=8, md=16, lg=20, xl=24, xxl=28, alert=34, full=1000 }` + Sheet 专属 38 |
| `AppInputBorder` | `OutlinedTextField` 自定义 shape（同上 Shape），聚焦 border 2dp；或自绘 `Modifier.drawBehind` |
| `ClipRSuperellipse` | `Modifier.clip(shape)`（Shape 方案①②直接可用） |

### 6.3 组件映射

| Flutter 组件 | Compose 要点 |
|---|---|
| `PressSurface`（核心） | 自定义 `Modifier.pressSurface()`：`InteractionSource` 收集 pressed/hovered；pressed 用 `Animatable`：可滚动祖先（`LocalScrollableContainer`/检查 `scrollableState`）内延迟 150ms；ink = 画 onSurface·α0.10 覆盖（fill 后），tint = 亮色 `graphicsLayer alpha 0.75` 或暗色 `ColorMatrix` 加白 20%（`card_surface.dart:646-677` 同款 ColorFilter.matrix）；hover α0.04；色值过渡 200ms fastOutSlowIn |
| `ElasticPress` | `Animatable` + `spring(311f, 0.593f)`；scale = 1 + min(0.25, 16dp/side)；pointerInput 捕获拖拽做 rubber band（`1 - 1/(x*0.55/limit+1)`） |
| `Card` | `Surface`/`Box` + pressSurface；min 64×40、target 48（`Modifier.minimumInteractiveComponentSize` 或 `defaultMinSize`）；fill = SurfaceLevel.Card 色；selected&&fillsSelection → primary |
| `ListRow` | 自写 layout：`Row` 高 ≥56dp、水平 padding 16、titleGap 16、minVerticalPadding 10；两行时标题 17sp/副 15sp |
| 分组列表 | `Column`/`LazyColumn` 中按 index 判定首尾行 → shape top24/bottom24；分隔线 `HorizontalDivider(thickness = Dp.Hairline, color = onSurface.copy(alpha=0.14f), modifier = Padding(start=16 或标题缩进 14))`，监听相邻行 pressed 隐藏 |
| `FilledButton` 等 | M3 组件重定义：`ButtonDefaults.buttonShape = Pill`、`contentPadding h24`、minSize 64×40；禁用色 38%/12%；按压改 tint（自定义 Indication 替换 ripple：Compose 1.5+ `RippleConfiguration` 置 none + 自 indication） |
| `IconButton` | 40×40、icon 24、tint press |
| TonalButton(组) | Surface(pill, raised 色) 内排 FilledButton(flat)；hover 标记 α0.08 滑移动画 spring(2350, 0.95) |
| **SnapSheet** | 自实现（M3 `ModalBottomSheet` 不支持多 detent）：`BoxWithConstraints` + `AnchoredDraggableState`（anchors = [0.5625H, 0.9H]，或 fitsContent 时 [contentH, 0.9H]）；spring `spring(dampingRatio = 1f, stiffness = 333f)`（ω≈18.3 临界阻尼，等效 mass3/k1000）；fling 投影 = velocity × 0.99/(1-0.99)/1000 选最近 anchor；`NestedScrollConnection` 把内容滚动接到 sheet 高度（先长高再滚动）；超顶 rubber band (0.55/100dp)；把手 28×4dp r2 onSurfaceVariant topPadding 6；堆叠 peek 10dp scale 0.9204 |
| `NavigationBar` | 高 80；自绘项：指示器 64×32 pill，`animateFloatAsState` scaleX 0→1（500ms, `Cubic(0.2,0,0,1)`+decel 组合或 `easeInOutCubicEmphasized`）；或 M3 NavigationBar 调 `NavigationBarItemColors` + 自定 indicator 代码 |
| `NavigationDock` | `Layout` + 多个 `Animatable`（lens 位置 1024/0.9、lift 250ms、jelly 100/0.4）；放大 `graphicsLayer { scaleX/Y }`；阴影 `Modifier.shadow` 不够 → `drawBehind` 双层 `drawRect/roundRect` blur（RenderEffect createBlurEffect） |
| TextField | M3 OutlinedTextField：shape=16dp 超椭圆，focusedBorder 2dp primary，unfocused 1dp outline；filled 变体：`BasicTextField` + Surface(fill=raised, pill, focus 描边 2dp)；minHeight 48 |
| Switch | 自绘（M3 Switch 不可到 52×32/拇指 24→40）：`Canvas` + `Animatable`（300ms easeOutBack 反向翻转），按压 thumb 拉伸 (10,-2)、变白加影 |
| Slider | 自绘 `Canvas`：轨 16dp r8 断口 6、thumb 44×(4→2) 胶囊；`Modifier.pointerInput` 拖拽 + divisions snap |
| Checkbox | 自绘 18dp 盒 + 勾路径 `PathMeasure` 逐段 150ms |
| Chip | Surface(pill, 36dp) 或 sm8 |
| Divider | `HorizontalDivider(thickness = Dp.Hairline, color = onSurface.copy(0.14f))`，外包 16dp 间距 |
| Dialog | `Dialog` + Surface(34dp 超椭圆, 348dp 宽)；入场 `scaleIn(initialScale = 1.2f)` + spring(275ms bounce≈0.15)/404ms；最后一动作 FilledButton，破坏性 `destructive` 色 |
| NullStatus | `AnimatedVisibility`/`AnimatedContent` + `LaunchedEffect` delay(60*i) 每项 `fadeIn + slideIn { 12dp }` |
| Loading 弧线 | `Canvas` + 无限 `rememberInfiniteTransition`（3200ms，3 圈 sweep 0.1–0.62）；或 M3 `CircularProgressIndicator(strokeWidth)` 简化 |
| Ink sparkle | 可选：AGSL RuntimeShader（Android 13+）复刻 617ms sparkle；否则 ink α0.10 已足够 |
| OpenContainer | Compose 1.7+ `SharedTransitionLayout`/`sharedBounds`（spring 340ms 临界），或 `AnimatedContent` scaleIn(0.92→1)+fade；拖拽关闭用 `Modifier.draggable` + velocity 阈值 150dp/s |
| 页面 push | NavHost 转场：`slideInHorizontally(full) + slideOutHorizontally(-0.3×)` + 被覆盖层 `overlayDim(10% black)`，spring/tween ≈300ms `Cubic(0.2,0,0,1)`；卡片圆角在转场中用 `Modifier.clip(shape)` 渐变（Android 12+ display 圆角可从 `RoundedCornerShape` 系统属性取） |
| Tab 切换 | `HorizontalPager`+`NeverScrollable` 或 `AnimatedContent`：fade 220ms easeInOut（`FractionalTranslation+alpha` 同步位移可选，照 `home.dart:433-455`） |
| 触感 | `LocalHapticFeedback.performHapticFeedback(TextHandleMove/LongPress)` 对应 `Feedback.forTap`；建议 `HapticFeedbackType.Confirm` |
| 系统栏 | 透明状态栏/导航栏，图标亮度随主题反转（`theme_manager.dart:60-85`）；`enableEdgeToEdge` |
| 阴影 | `Modifier.shadow(elevation, shape)` 单影不够时用 `drawBehind { drawIntoCanvas { Paint().apply { setShadowLayer } } }` 或 RenderEffect；dock 双影常量照 §5.1 |
| 阻止 ripple 全局 | Compose 1.5+：`CompositionLocalProvider(LocalIndication provides NoIndication)` + 自 indication；`LocalRippleConfiguration provides null`（material3 1.6+） |

### 6.4 复刻优先级建议

1. **第一层（决定观感）**：SurfaceLevel 色阶 + 超椭圆 Shape + AppTextStyle + 无 ripple 的 PressSurface（tint/ink + 150ms 延迟）。
2. **第二层（决定手感）**：SnapSheet 双 detent 弹簧 + 把手 + 内容滚动接续；OpenContainer 页面变换；ElasticPress。
3. **第三层（决定精致度）**：Dock 镜头/果冻、搜索 droplet 变形、Switch 过冲、分组行分隔线按压隐藏、暗色 elevated 色板。

---

## 附录 A：本次未展开但相关的文件索引

- `prism_ui/lib/hct/*`：HCT 色彩空间与 Material 变体完整移植（tonal palette 计算）。
- `prism_ui/lib/liquid_glass.dart`：可选 Liquid Glass 材质（blur 2.5–16、暗色 tint α0.48–0.63），由设置项 `LiquidGlassMode` 开关（`application.dart:185-189`），默认关闭。
- `prism_ui/lib/shapes/*`：M3 Expressive RoundedPolygon（cookie9/12 边形等）。
- `prism_ui/lib/goo.dart`：果冻凝胶表面（搜索条按压）。
- `prism_ui/lib/side_sheet.dart / form_sheet.dart`：宽屏 sheet 变体。
- `prism_ui/lib/press_sparkle.dart + shaders/ink_sparkle.frag`：Android ink sparkle 着色器。
- `lib/views/dashboard/*`：仪表盘布局体系（列数/密度/缩放，`layout.dart:1-55`）。

## 附录 B：主要证据文件清单（绝对路径）

- 设计系统：`D:\Code\Union\UI组件参考项目\FlClash\plugins\prism_ui\lib\{theme_data,color,color_scheme,surface,shape,typography,metrics,context,card,card_surface,press_surface,list_row,list,button,common_button,snap_sheet,sheet,sheet_header,navigation_bar,navigation_dock,text_field,input,switch,slider,checkbox,chip,divider,null_status,loading,scaffold,dialog,page_transition,open_container,animated_entries,animated_visibility,motion,press_sparkle}.dart`
- App 侧：`D:\Code\Union\UI组件参考项目\FlClash\lib\{application,bootstrap}.dart`、`lib\manager\theme_manager.dart`、`lib\providers\state\theme.dart`、`lib\common\constant.dart`、`lib\pages\home.dart`、`lib\ui\{list_item,setting_text_card}.dart`、`lib\ui\scaffold\scaffold.dart`、`lib\views\dashboard\{dashboard,layout,style}.dart`、`lib\views\dashboard\widgets\card.dart`、`lib\views\proxies\card.dart`
- 外部依据：Flutter `RoundedSuperellipseBorder`（Flutter 3.32+ 内建连续圆角）：https://api.flutter.dev/flutter/painting/RoundedSuperellipseBorder-class.html
