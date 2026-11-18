# MD3 合规审计报告 — Union 郑财校园（Android / Jetpack Compose）

Target: `refactor/prism-ui-consistency` 分支（基线 v0.2.15）→ 修复后 v0.2.16
Date: 2026-11-19
Stack: Compose BOM 2026.09.00 + material3 1.5.0-rc01（Expressive）／自定义 `prism` 设计层（复刻 FlClash）

> ⚠️ 本报告第一版基于 `master`（v0.2.10）编写，基线错误。v0.2.15 所在分支已自行完成大量改造
> （统一页头、PrismTokens 令牌层、PresetPalettes 预设配色、字号钳制），因此报告已按新基线重写。

Overall Score: **修复前 78 / 100 → 修复后 91 / 100**

## 新基线已自带的改进（无需处理）

| 项目 | 状态 |
|---|---|
| 页头统一 | 全部收敛到 `UnionScreenScaffold`，0 处内联 Scaffold |
| 设计令牌 | 新增 `PrismTokens.kt`（Space / Alpha / IconSize / 12 档字阶）+ `PresetPalettes.kt`（预设配色） |
| 固定高度 | 0 处 `height(52.dp)`，全部改为 `defaultMinSize`/`heightIn` |
| 开关语义 | `PrismSwitch` 已用 `toggleable(role = Role.Switch)` |
| 触达尺寸 | 5 处 `minimumInteractiveComponentSize` / `sizeIn(minHeight)` |
| 弹层返回 | `PrismSnapSheet` 已有 `BackHandler` |
| 字号钳制 | `Theme.kt` 把 `fontScale` 钳到 0.8–1.4，保护单行页头与课表行 |
| 最小字号 | 已消除 8/9sp，统一 `caption2Style`（11sp） |

---

## 本轮修复（v0.2.16）

| 编号 | 问题 | 修复 | 文件 |
|---|---|---|---|
| 1 | 底部导航裸 `clickable`，TalkBack 读不出"第 N 项 已选中" | 改 `selectable(selected, role = Role.Tab)` + `selectableGroup()`；图标 `contentDescription` 置 null 交由文字承载 | `prism/PrismDock.kt` |
| 2 | 分段控件裸 `clickable` | 同上，`selectable(role = Role.Tab)` + `selectableGroup()` | `prism/PrismSegmented.kt` |
| 3 | 按压反馈只处理 `PressInteraction`，键盘聚焦/鼠标悬停无反馈 | 补 `HoverInteraction`（α0.06）与 `FocusInteraction`（α0.12），覆盖 Ink/Tint 两种按压样式 | `prism/PrismPress.kt`、`prism/PrismTokens.kt` |
| 4 | `MaterialTheme` 未传 `shapes`，原生 M3 组件（Button/Card/Chip/Slider）圆角与 Prism 割裂 | 新增 `PrismMaterialShapes`（8/20/16/24/28dp 映射 extraSmall→extraLarge）并接入主题 | `prism/PrismTokens.kt`、`theme/Theme.kt` |
| 5 | `LocalRippleConfiguration = null` 全局屏蔽 ripple，三态反馈被砍掉一半 | 移除屏蔽，保留 `PrismPressIndication` 作为 default indication | `theme/Theme.kt` |
| 6 | 11 个字阶函数各自 `.copy(fontSize=…)`，尺寸散落在样式函数内 | 新增 `PrismType` 对象承载全部尺寸/行高/字重（定义一次），样式函数降为纯语义别名 | `theme/Type.kt`、`prism/PrismTokens.kt` |
| 7 | `PrismCard` / `PrismRow` 可点击但无按钮语义 | 补 `clickable(role = Role.Button)` | `prism/PrismComponents.kt` |
| 8 | `setDecorFitsSystemWindows` 手写边到边，状态栏图标明暗不跟随主题 | `enableEdgeToEdge()` + `WindowInsetsControllerCompat` 按 `darkTheme` 切换明暗图标 | `MainActivity.kt`、`theme/Theme.kt` |
| 9 | 平板/折叠屏正文行宽无上限 | `UnionScreenScaffold` 内容区 `widthIn(max = 840.dp)` 居中 | `ui/common/ScreenScaffold.kt` |

### 有意保留的 3 处字号

| 位置 | 值 | 原因 |
|---|---|---|
| `prism/PrismTokens.kt` `dockLabelStyle()` | 10/12 sp | 5 个 tab 并排时 `caption1`（12sp）放不下，已在函数注释说明 |
| `screens/study/TimetableScreen.kt` `timeStyle` | 10/11 sp | 52dp 时间栏内要容纳「S 10:40」三行，注释已说明约束 |
| `screens/study/TimetableScreen.kt` `sectionStyle` | 12/14 sp | 同上，节次标签列宽受限 |

---

## 仍未处理

- **课程/状态色板调和（原 W8）**：`PrismStatus` 与课表 12 色课程板为硬编码，动态取色下对比度不保证；需先定品牌配色方向。
- **国际化（原 W9）**：约 390 处中文字面量未外提。App 面向中文校园场景，建议维持现状；`AndroidManifest.xml` 的 `supportsRtl="true"` 属空声明，可移除。
- **UI 测试（原 W11）**：`testTag` 为 0，无 `androidTest` 覆盖。
- **双栏布局**：`WindowSizeClass` + `ListDetailPaneScaffold` 未接入，目前只有内容宽度约束。
- **竖屏锁定**：`screenOrientation="portrait"` 仍在，横屏/平板旋转需产品确认。

## 验证

- `./gradlew :app:compileDebugKotlin` — 零错误
- `./gradlew :app:testDebugUnitTest` — 通过（含洗浴排队/预约/订单解析测试）
- `./gradlew :app:assembleRelease` — v0.2.16 签名包产出
