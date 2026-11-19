# UI 质感与动效改进方向（Union / 郑财校园）

Date: 2026-11-19
基线: `v0.2.16`（prism 设计层已成型，M3 审计 91/100）
范围: 只谈**质感**与**动效**，不推翻现有 prism 体系。结论均带 `文件:行号` 证据。

---

## 实施进度（2026-11-19）

| 批次 | 内容 | 状态 |
|---|---|---|
| P0-1 表面与文字 | Surface 层级重排（Card→`surfaceContainerLowest`、Page→`surfaceContainer`/`surfaceDim`）、`PrismEdge` 1px 描边接入卡片与分组列表、CardHeader/SectionHeader 取消双重 alpha、分隔线 0.14→0.10、chevron 18→16、IconBadge 圆角 16→12、空态图标 44→56、数字负字距 | 已完成 |
| P0-2 动效编排 | `PrismSpec`（tab fade-through / 24dp push-pop / sheet 弹簧 / 按压两段）、5 个 tab 独立转场、`prismPressScale` 接入卡片与按钮、Sheet 入场弹簧 + 把手 32×4、数字位移 1/2→1/3、shimmer 1500ms | 已完成 |
| P1 组件层 | Dock 实心药丸→`primary` 12%/18% tonal、选中图标 1.12 + 按压 0.94、暗色阴影 24→16、分段控件同步 tonal、生活页与今天页改中性卡面 + 强调色 | 已完成 |
| P1 余项 | 空态主按钮、骨架按内容形状排、PullToRefresh 品牌化、CJK 字体 | 待做 |
| P2 | 课程容器变换、课程色板 HCT 化、大屏 ListDetail | 待做 |

验证：`:app:compileDebugKotlin` 通过、`:app:testDebugUnitTest` 通过、`:app:assembleDebug` 产出 APK。
**尚未做真机/模拟器视觉走查**（本机无 AVD），亮/暗两套截图对比待补。

与本文的两处偏差：
- 指标数字保持 34sp（未升 36sp）：双列卡在 360dp 宽度下会挤到单位，先保留。
- `PrismProgressRail` 未改：它已用 `animateColorAsState` 做分段过渡，没有独立进度浮点数可包。

---

## 0. 一句话说清问题

现在的界面**结构是对的，材质是平的**：

- 表面只有一层 1.10:1 的明度差（页面 `#EFEDF0` vs 卡片 `#FCF8F9`），没有边界、没有高光、没有阴影预算 → 卡片像贴纸，不像"放"在页面上。
- 文字层级被**双重衰减**（`onSurfaceVariant` 再乘 0.72）→ 全屏灰蒙蒙，最重要的卡片标题反而是最弱的一档。
- 动效 token 很全，但**没有编排**：转场一套（iOS push）、组件一套（PrismMotion）、M3 一套（`MotionScheme.expressive()` 传了却没人用），缺"容器变换 / 共享轴 / 按压形变"这三类真正让界面"活"的动作。

---

# 一、质感：6 个失效点

## 1.1 表面层级（P0，收益最大）

**现状**（`ui/prism/PrismSurface.kt:18-23`）：

| Level | 浅色 | 深色 |
|---|---|---|
| Page | `surfaceContainer` #EFEDF0 | `surface` |
| Card | `surfaceBright` #FCF8F9 | `surfaceContainer` |
| Block | `surfaceContainer` #EFEDF0 | `surfaceContainerHigh` |
| Inset | `surfaceContainerHighest` | `surfaceContainerHighest` |

实测：Page 与 Card 的对比度是 **1.10:1**（相邻表面建议 ≥1.2 才可辨）。
深色更糟：Card = `surfaceContainer`、Page = `surface`，卡片几乎融进背景，全靠 `MetaChip` 的 inset 才有一点点层次。

**问题**：
- 浅色下"白卡 + 灰页"只差 3.5% 亮度，又没有边框，卡片边界靠运气（屏幕亮度/环境光）。
- `Block` 与 `Page` 在浅色下是**同一个颜色**，这个层级等于不存在。
- 没有任何一级用 `surfaceContainerLowest`（纯白）——最亮的卡位被浪费了。

**改法**：

```kotlin
// ui/prism/PrismSurface.kt
private fun ColorScheme.colorOf(level: SurfaceLevel, dark: Boolean): Color = when (level) {
    SurfaceLevel.Page  -> if (dark) surfaceDim          else surfaceContainer        // #EFEDF0
    SurfaceLevel.Card  -> if (dark) surfaceContainer    else surfaceContainerLowest  // #FFFFFF
    SurfaceLevel.Block -> if (dark) surfaceContainerHigh else surfaceContainerLow    // #F5F3F6
    SurfaceLevel.Inset -> if (dark) surfaceContainerHighest else surfaceContainerHighest
}
```

配套两条硬规则：
1. **相邻一级 ≥ 1.2:1**（拿工具量，别凭眼睛）。白卡 + #EFEDF0 页约 1.22:1，够用。
2. 卡片**必须**有一道 1px 边：浅色 `onSurface @5%`（压底边），深色 `white @6%`（提顶边）。这是"材质感"最便宜的一招，成本 1 个 draw call。

```kotlin
// 新增 ui/prism/PrismEdge.kt
fun Modifier.prismEdge(shape: Shape, dark: Boolean) = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val w = 1.dp.toPx()
    drawOutline(outline, (if (dark) Color.White else Color.Black).copy(alpha = 0.05f), style = Stroke(w))
    if (dark) clipRect(0f, 0f, size.width, size.height / 2) {
        drawOutline(outline, Color.White.copy(alpha = 0.08f), style = Stroke(w))
    }
}
```

> 阴影预算：**全 App 只允许 3 处**有阴影 —— 底部 Dock、SnapSheet、Today 的 Hero 卡。其余一律 tonal + hairline。现在只有 Dock 有（双层 24dp/3dp），方向是对的，别扩散。

## 1.2 文字层级：先取消双重衰减（P0）

**现状**：
- `PrismComponents.kt:121` 卡片头：`onSurfaceVariant.withAlpha(0.72)`
- `PrismComponents.kt:293` 分组头：同上
- `PrismComponents.kt:598` / `ElectricBarChart.kt:79` / `TimetableScreen.kt:543`：同上

实测（浅色、卡面 #FCF8F9）：
- `onSurfaceVariant` #414755 原始对比 **8.7:1**
- 乘 0.72 后合成 ≈ #757883，对比 **4.23:1** —— **13sp 脚注低于 4.5:1**，17sp 标题看起来就是"灰的"。

**这是当前"没质感"最隐蔽的原因**：层级不是靠字号/字重拉开的，是靠一遍遍调透明度压出来的，压到所有人都一样灰。

**改法**：定三档**语义角色**，页面只选角色，不写 alpha。

```kotlin
// ui/prism/PrismText.kt
enum class PrismTextRole { Primary, Secondary, Tertiary, Disabled, Accent, Danger }

@Composable
fun PrismTextRole.color(): Color = when (this) {
    Primary   -> MaterialTheme.colorScheme.onSurface                 // 1
    Secondary -> MaterialTheme.colorScheme.onSurfaceVariant          // 不乘 alpha
    Tertiary  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f) // 仅 <=12sp 注记
    Disabled  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Accent    -> MaterialTheme.colorScheme.primary
    Danger    -> MaterialTheme.colorScheme.error
}
```

映射规则（照做即可）：

| 位置 | 现在 | 改成 |
|---|---|---|
| 卡片头 17sp w600 | variant@0.72（4.2:1） | **onSurface**（17:1） |
| 分组头 17sp w600 | variant@0.72 | **onSurface**，字重降 500 |
| 列表副标题 15sp | variant | variant（保持） |
| footer 13sp | variant@0.72 | **variant**（去掉 alpha） |
| caption2 11sp | variant | variant@0.72（唯一允许 alpha 的一档） |

一句话原则：**颜色 role 和 alpha 只能用一层。**

## 1.3 色彩：饱和容器当整卡背景（P0）

**现状**：
- `Theme.kt:39` `dynamicColor = true` 默认开启 → Android 12+ 上 `PresetPalettes` 全是死代码，换台设备就是另一套配色，品牌为零；壁纸太花时 surface 会发灰发绿。
- `LifeScreen.kt` 电费卡 `secondaryContainer`、洗浴卡 `tertiaryContainer`；`TodayScreen.kt` 底部"全部生活入口"也是 `tertiaryContainer`。
- 蓝色预设里 `tertiaryContainer = #A949CD`（高饱和紫）—— 和电费的蓝、洗浴的绿并排，就是"色块拼贴"。

**改法**：
1. **默认用品牌预设**，动态取色降级为设置项（"跟随系统壁纸"，默认关）。校园 App 的识别度比个性化值钱。
2. **一屏一个强调色**：整卡上色只留给"需要立刻行动"的状态（低电费、排队中、上课中）。普通指标卡 = 中性卡面 + 12% 色调图标底 + 状态 chip。
3. 容器色不当背景，统一"中性面 + 强调元素"：

```kotlin
// 反例（现在）
PrismMetricCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer, ...)

// 正例
PrismMetricCard(
    containerColor = surfaceColorOf(SurfaceLevel.Card),
    accent = MaterialTheme.colorScheme.tertiary,  // 只染 icon badge / chip / 进度条
)
```

4. `PrismStatus`（`PrismStatus.kt:11-33`）改成 HCT 派生（tone 40 / 90 / 10），不要手写 6 组 hex：现在 `SuccessLight #2E7D4F` 与 `primary` 毫无色相关系，叠在一起显脏。
5. 课程 12 色（`PrismCourseColors.kt`）限色相到 teal→blue→violet，明度固定 tone 90/30 —— 现在的"彩虹课表"是质感最大杀手之一。

## 1.4 形状：圆角只有一档"大"（P1）

现状：卡 16 / 组 24 / 按钮胶囊 / Sheet 38 / Dialog 34（连续圆角做得好），但：
- `PrismMetricCard` 的图标底 40dp 用 `PrismCorner.Md`（16）→ **内圆角 = 外圆角**，违反同心圆角规则（inner = outer − padding，或至少小 4dp），视觉上"卡住"。
- 缺少 4/8 的微圆角，界面全是"大气泡"。

**改法**：固定四档嵌套律 —— `24 (组) > 16 (卡) > 12 (内块) > 8 (图标底/chip) > pill`。
卡内 padding 16 时内块 ≤ 8；padding 20 时内块 ≤ 12。现在 icon badge 40dp 改 `PrismCorner.Sm`(8) 或 10。

## 1.5 字体：CJK 与 Roboto Flex 打架（P1）

`theme/Type.kt` 的 `FlexFamily` 只装了 `roboto_flex`。**Roboto Flex 没有中文字形**，中文全部落到 OEM 系统字体（MIUI 是 MiSans，ColorOS 是 OPlusSans，模拟器是 Noto Sans CJK）。

后果：
- 同一个 `w600`，拉丁字母是 600，中文可能是 500 也可能是 700，中英混排时字重断层。
- `PageTitle 28 w700` 在部分机型上会撑宽，也可能被系统字体的行高裁掉一点。
- 数字用 tnum 对齐了（做得好），但 `Display 40` 没有负字距，`2026` 这种四位数显得散。

**改法（二选一）**：
- **省事版**：把中文字体固定住 —— `res/font` 放 `MiSans VF` / `SourceHanSansSC VF`（仅 wght 轴，subset 到常用 3500 字 ≈ 1.2MB），在 `FontFamily` 里做 fallback。
- **极简版**：`PrismType` 的中文档统一降一档字重（700→650、600→550），因为 CJK 的 600 视觉上约等于拉丁 700。

另外：`Display` / `Metric` 补 `letterSpacing = (-0.5).sp / (-0.3).sp`，大数字立刻"紧"起来。

## 1.6 细节质感清单（P1，逐条都是 1 行改动）

| # | 位置 | 现在 | 改成 | 为什么 |
|---|---|---|---|---|
| 1 | `PrismComponents.kt` 卡片头图标 | `primary` 20dp | `onSurfaceVariant` 20dp | 满屏蓝图标在抢注意力 |
| 2 | `PrismRow` chevron | variant@0.55，18dp | variant@0.38，16dp | 箭头太显眼 |
| 3 | `GroupDivider` | onSurface@0.14 | onSurface@0.10 | 深色下 14% 发白 |
| 4 | `MetaChip` | `labelSmall`（未走字阶） | `caption2Style()` | 两套字号并存 |
| 5 | `PrismGroupedList` | 无边缘描边 | `prismEdge()` | 与卡片同材质 |
| 6 | `CardHeader` | 20dp 图标 + 17sp | 18dp + 17sp/600 | 头重脚轻 |
| 7 | 指标卡单位"度" | 与数字同字号 | 15sp w500，baseline 对齐 | 数字才该大 |
| 8 | 空态图标 | 44dp 线性、alpha 0.55 | 64dp 双色（primary 8% 底 + 24dp 图形） | 44 线性太"占位" |
| 9 | 骨架 | 132 + 4×64 统一块 | Hero 132 / 双列 96×2 / 行 56 | 加载形状 = 内容形状 |
| 10 | PullToRefresh | M3 默认转圈 | 品牌小弧（2dp 线、primary） | 最常看到的原生感 |
| 11 | 数字变化 | slide 1/2 高度 | slide 1/3 + fade 160ms | 现在会"跳行" |
| 12 | 空列表文案 | "暂无数据" | "本周还没有待办" + 主动作按钮 | 空态是体验，不是错误 |

---

# 二、动效：从"有 token"到"有编排"

## 2.1 现状盘点

| 能力 | 现状 |
|---|---|
| Motion token | 有 `PrismMotion.kt:17-23`（90/140/180/220/300/500/1200 + 3 条 easing + 2 条弹簧） |
| 按压缩放 | 没有，只有 ink/tint 变色，无形变 |
| Tab 转场 | 走全局 `NavHost` 横向 push（`UnionApp.kt:137-140`），切 tab 也横滑 |
| 详情转场 | 同上，无容器变换 |
| 列表增删 | `animateItem()` 0 处 |
| 内容高度变化 | `animateContentSize` 0 处 |
| 数字变化 | 有 `PrismAnimatedText` |
| 骨架 shimmer | 有，1200ms |
| 空态 stagger | 有 `PrismReveal`（50ms×index） |
| Sheet | 入场 tween 300，只有 snap 用弹簧 |
| 减弱动效 | `prismAnimationsEnabled()` 每次组合都读 `Settings.Global`（Dock 里 5 个 tab 各读一次） |
| M3 MotionScheme | `Theme.kt:84` 传了 `MotionScheme.expressive()`，但**没有任何组件读它** |

**最刺眼的一条**：底部 tab 切换走的是全局 push 转场。从"生活"切回"今天"，页面还是从左往右滑入 —— 方向语义是反的，用户会觉得"卡了一下又弹回来"。这是现在"动效廉价"最直接的来源。

## 2.2 建一层编排（P0）

不要在每个页面写 `tween(220)`，收成 4 组语义：

```kotlin
// ui/prism/PrismMotion.kt 追加
object PrismSpec {
    /** Tab 之间：fade-through，不做位移 */
    val tabEnter = fadeIn(tween(210, easing = Decelerate)) +
        scaleIn(initialScale = 0.98f, animationSpec = tween(210, easing = Decelerate))
    val tabExit = fadeOut(tween(90, easing = Accelerate)) +
        scaleOut(targetScale = 1.01f, animationSpec = tween(90, easing = Accelerate))

    /** Push：24dp 位移 + fade（M3 shared-axis X 的克制版） */
    val pushEnter = slideInHorizontally(spring(1f, 420f)) { it / 12 } + fadeIn(tween(240))
    val pushExit = slideOutHorizontally(tween(240, easing = Decelerate)) { -it / 24 } + fadeOut(tween(240))

    /** Sheet */
    val sheetIn = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)

    /** 按压 */
    val pressDown = spring<Float>(dampingRatio = 0.7f, stiffness = 900f)
    val pressUp = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)
}
```

## 2.3 九条具体动作（按性价比排序）

**① Tab 切换改成 fade-through（P0，1 小时）**
给 `Screen.Tabs` 的 `composable()` 单独传转场，别用全局横向：

```kotlin
composable(
    Screen.Today.route,
    enterTransition = { PrismSpec.tabEnter },
    exitTransition = { PrismSpec.tabExit },
) { ... }
```
效果：切 tab 是"内容淡入 + 98%→100% 的轻微推进"，页面不横移，dock 胶囊照旧弹性滑动。这一条改完，整个 App 立刻"安静"下来。

**② 按压缩放（P0，半天）**
现在按压只有 10% ink，手指按下去"没吃到力"。加形变：

```kotlin
@Composable
fun Modifier.prismPressScale(interactionSource: InteractionSource, target: Float = 0.985f) =
    composed { /* graphicsLayer { scaleX = s; scaleY = s }，读 PressInteraction */ }
```
- 卡片 0.985、按钮 0.96、dock tab 0.94
- 按下 90ms（stiffness 900），抬起 220ms（stiffness 500）—— **抬起比按下慢**，这是"有重量"的关键
- 和现有 ink/tint 叠加，不替换

**③ 列表用 `animateItem()`（P0，2 小时）**
待办增删、洗浴设备列表、教室列表——现在都是"啪"地插入。`LazyColumn` 的 `Modifier.animateItem()` 一行就够（默认 fade + placement spring）。

**④ Sheet 入场换弹簧（P1，1 小时）**
`PrismSnapSheet.kt` 现在是 `tween(Emphasized, Decelerate)` 入场，拖拽才是临界阻尼。改成 `spring(0.9, 380)`、遮罩 180ms 线性 —— 入场和拖拽才会是"同一种物理"。

**⑤ 数字变化收敛（P1，30 分钟）**
`PrismAnimatedText` 的 slide 是 `it/2`，33sp 的数字会窜半行。改 `it/3` + 160ms；另外**每秒刷新的倒计时必须关掉动画**（Hero 卡的倒计时若包在 AnimatedText 里，每秒都会滑一次）。

**⑥ 骨架降噪（P1）**
1200ms 常驻 shimmer 偏快、偏"闪"。改 1400–1600ms、135° 斜向，base 从 `onSurface@0.06` 降到 `0.045`。骨架的价值是"占位不抢戏"。

**⑦ 循环动效上限（P1）**
现在同屏最多可能有两个无限动画：dock 绿点 900ms 呼吸 + 骨架 shimmer。规则：**同屏 ≤1 个循环动效，周期 ≥1.8s，且只在数据真的"活"的时候跑**（洗浴正在使用才呼吸，否则常亮）。

**⑧ 容器变换（P2，最有"高级感"，成本也最高）**
课程块 → 课程详情、洗浴设备 → 设备详情、教室 → 教室详情，现在都是整页替换。用 `SharedTransitionLayout`（Compose 1.7+ 已稳定）做 `sharedBounds`：小卡扩成大页，标题和数字跟着走。做 1 处（课程）就够撑住门面；做不动就退化成 `scale 0.94 → 1 + fade 220ms`（"假容器变换"，80% 观感，10% 成本）。

**⑨ 减弱动效下沉到 CompositionLocal（P1）**

```kotlin
val LocalPrismMotionEnabled = staticCompositionLocalOf { true }
```
在 `UnionTheme` 里读一次 `Settings.Global` 并 `remember`，全树共享。解决两件事：Dock 里 5 次重复读系统设置；页面里散落的 `prismAnimationsEnabled()` 调用。

## 2.4 动效预算（写进代码规范）

- 常用转场 **≤300ms**；超过 300ms 只在"页面级共享元素"用。
- 单次位移 **≤24dp**（方向性 push 除外）。
- 同一元素**不同时**用 scale + slide + rotate，最多两样。
- 同屏循环动画 **≤1**。
- 每个装饰动效都必须能被 `LocalPrismMotionEnabled` 关掉。
- 每秒刷新内容（倒计时、排队秒数）**不加**入场动画，只保证 tnum 不抖。

---

# 三、组件级 checklist

## Dock（`PrismDock.kt`）
- [ ] 选中指示器从**整格实心 `primary` 药丸**改成**图标底 tonal 药丸**（56×32，`primaryContainer`），或"图标染 primary + 标签 w600"。
      理由：一屏 5 个 tab，实心 primary 占 20% 宽度、每屏可见，是最"廉价"的一块颜色；tonal 更贵。
- [ ] 指示器位移保留 `springSnappy(560)`（这条做得好），可加 0.98→1 宽度呼吸。
- [ ] 图标 scale 1.08 → 1.12，配 `pressScale(0.94)`。
- [ ] 未选中 outlined / 选中 filled —— 现在已是，保持。
- [ ] 顶部柔光阴影保留，**dark 下 24dp → 16dp**（深色阴影看不见，只会糊边）。

## 卡片 / 指标卡
- [ ] 加 `prismEdge()`。
- [ ] 图标底 40dp → 圆角 8/10（现在 16，与外框同半径）。
- [ ] 数值 34 → 36，`letterSpacing -0.5.sp`；单位 15sp w500。
- [ ] `compact` 双列卡 padding 14 → 16，保证两卡等高（`IntrinsicSize.Min`）。
- [ ] 整卡上色只留给告警态。

## 空态 / 错误 / 加载
- [ ] 空态：64dp 图形 + 主文案 17/600 + 副文案 15/variant + 一个 `PrismButton`。
- [ ] 错误：区分"网络 / 未绑定 / 服务时间外"，每种给一个**下一步动作**（现在统一"重试"）。
- [ ] 骨架按页面形状排（Hero + 双列 + 行），不要统一 4 行。

## Sheet
- [ ] 入场弹簧（见 2.3④）。
- [ ] 把手 28×4 → 32×4，`Grip` 0.6 → 0.45（现在偏重）。
- [ ] 遮罩 0.20/0.48 → 0.24/0.52，可选配 2% 页面 `scale` 下沉。
- [ ] 内容区顶部加 8dp 渐隐（避免内容直接贴把手）。

## 按钮
- [ ] 主按钮加 1px 顶部高光（dark）或 8% 底部阴影（light），提升"可按"感。
- [ ] 按压 0.96 + 90/220ms。
- [ ] 加载态：文字 → 环，**宽度保持**（不要跳）。

---

# 四、落地顺序

| 阶段 | 内容 | 工作量 | 观感提升 |
|---|---|---|---|
| **P0-1 表面与文字** | `PrismSurface` 重排 + `prismEdge()` + 三档文字角色 + 关掉默认动态取色 | 1–1.5 天 | 立刻"干净、有边界" |
| **P0-2 动效编排** | `PrismSpec` + tab fade-through + 按压缩放 + `animateItem` + 减弱动效下沉 | 1.5–2 天 | 立刻"安静、跟手" |
| **P1 组件层** | Dock tonal 药丸、指标卡数字、空态、骨架、Sheet 弹簧 | 2 天 | 明显更"贵" |
| **P2 进阶** | 课程容器变换、课程色板 HCT 化、CJK 字体、大屏 ListDetail | 3–5 天 | 门面级 |

**验收方式**（不然改完没人知道好坏）：
1. 固定 6 屏 × 亮/暗 = 12 张基线截图（今天 / 课表 / 生活 / 成绩 / 设置 / 一个 Sheet），改前改后并排看。
2. `JankStats` 跑一遍滚动与转场，确认不掉帧（重点看 `prismEdge` 和按压缩放的 `graphicsLayer`）。
3. 脚本扫一遍 `onX` / `container` 组合的对比度，目标正文 ≥4.5:1、大字 ≥3:1。

---

# 五、克制清单（明确"不做"）

- ❌ 不要给每张卡加阴影 —— 只会脏，不会贵。
- ❌ 不要上全局 blur / 毛玻璃 —— 中端机掉帧，且和 prism 的"连续圆角 + tonal"语言不搭。
- ❌ 不要让常用转场超过 300ms。
- ❌ 不要给每秒刷新的数字加入场动画。
- ❌ 不要同一屏超过 1 个循环动效。
- ❌ 不要把动效当装饰：每个动作都要回答"它在解释什么"（层级变化 / 方向 / 因果）。答不上来就删。

---

## 附：如果只能改三处

1. **`PrismSurface` 换成 `surfaceContainerLowest` 白卡 + 1px edge** —— 质感的一半。
2. **文字去掉双重 alpha（卡片头/分组头回到 `onSurface`）** —— 另一半。
3. **Tab 切换从横向 push 改成 fade-through + 按压缩放 0.985** —— 动效的全部"贵感"。
