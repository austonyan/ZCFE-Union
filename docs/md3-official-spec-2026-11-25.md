# Material 3 官方规范复核与修复（2026-11-25）

- 目标：沿用 `material-3` skill 的官方规范文档，对整应用做一次独立复核，修复与官方 M3 冲突的真实偏差。
- 范围决定（用户确认）：**保留 Prism 品牌**（复刻 FlClash 的设计层），只改与官方规范冲突的令牌角色、语义与无障碍问题；不做全量替换，不改业务逻辑、签名与版本号。
- 官方依据：`material-3` skill 的 `color-system`、`typography-and-shape`、`component-catalog`、`navigation-patterns`、`layout-and-responsive`、`theming-and-dynamic-color` 六份 reference。

## 审计结论

三路只读审计（主题令牌 / 组件无障碍 / 布局导航动效）均未发现 P0。发现并本轮修复的偏差如下。

| 类别 | 文件:位置 | 偏差 | 修复 |
|---|---|---|---|
| 形状 | `ui/prism/PrismTokens.kt` `PrismMaterialShapes` | `extraSmall=8 / small=20 / medium=16 / large=24 / extraLarge=28` 非单调，Chip 比 Card 更圆 | 原生 M3 组件改为官方单调映射 `4 / 8 / 12 / 16 / 28`；Prism 自绘品牌圆角不变 |
| 字体 | `ui/prism/PrismComponents.kt` `PrismAlertDialog` | 标题 17/22、正文 15/20；官方对话框为 Headline Small 24/32 + Body Medium 14/20 | 新增 `dialogHeadlineStyle()` / `dialogBodyStyle()`，仅对话框使用 |
| 语义 | `ui/prism/PrismComponents.kt` `PrismAlertDialog` | 原生 `Dialog` 未暴露 `dialog()` / `paneTitle` / `dismiss`，TalkBack 不播报对话框上下文 | 在 Surface 上补三件套语义 |
| 色彩 | `ui/prism/PrismComponents.kt` `GroupDivider` | 分割线用 `onSurface` 叠 alpha | 改为官方 `outlineVariant` |
| 色彩 | `ui/prism/PrismEdge.kt` | 卡片发丝线硬编码 `Color.White/Black` | 改为 `colorScheme.outline/outlineVariant`，动态取色与暗色预设可参与 |
| 对比度 | `ui/prism/PrismComponents.kt` 行尾 chevron | `onSurfaceVariant @0.38` 约 2:1，低于导航图标 3:1 | 改为完整 `onSurfaceVariant` |
| 色彩 | `ui/prism/PrismProgressRail.kt` | 进度轨道用 `onSurface@0.12` / `secondaryContainer` | 改为官方 `surfaceContainerHighest` |
| 语义 | `ui/prism/PrismSegmented.kt` | 分段选择器用 `Role.Tab`（无 tablist） | 改为单选组 `Role.RadioButton` |
| 无障碍 | `ui/prism/PrismTextField.kt` | `isError` 只朗读通用 Invalid input | 用 `semantics { error(supportingText) }` 朗读具体错误 |
| 色彩 | `ui/prism/PrismMessenger.kt` | 仅 Info 用 inverse 配色，其余用状态容器色 | 所有 snackbar 统一 `inverseSurface/inverseOnSurface`，动作文字用 `inversePrimary`；tone 由图标 + 触觉 + liveRegion 表达 |
| 语义 | 课表设置 / 用电用量 chip 组 | chip 为单选按钮但调用点缺 `selectableGroup()` | 在分组 Row 补 `Modifier.selectableGroup()` |
| 无障碍 | `ui/prism/PrismSkeleton.kt`、`PrismControls.kt` | 骨架 / 加载块无进度语义 | 骨架暴露 `ProgressBarRangeInfo.Indeterminate`；加载块把 label 作为 `contentDescription` |
| 交互 | `ui/screens/today/TodayScreen.kt` | 通知横幅整卡可点 + 嵌套「查看课表」按钮，两个同义目标 | 去掉嵌套按钮，保留整卡单一目标 |
| Insets | `ui/common/ScreenScaffold.kt` | 内层 Scaffold 与外层导航壳重复消费系统栏 insets，详情页标题被下推 | 内层 `contentWindowInsets = WindowInsets(0)` |
| IME | `ui/prism/PrismSnapSheet.kt` | 边到边面板只加导航栏 padding，键盘会遮选项 | 内容列补 `imePadding()` |
| 语义 | `ui/prism/PrismPageHeader.kt` | 页标题无 heading 语义 | 标题加 `semantics { heading() }` |
| Insets | `ui/prism/PrismDock.kt` | 底栏 Scaffold 默认已加导航栏内边距，又显式 `navigationBarsPadding()` | 移除重复修饰符 |

## 有意保留（Prism 品牌 / 产品决定）

- Prism 品牌圆角（卡 20 / 主卡 28 / 对话框 34 / sheet 38）与连续圆角平滑度。
- 五个固定 Tab；Tab 页 `PrismPageHeader`，不引入折叠顶栏；详情页透明小顶栏。
- 竖屏锁定、`fontScale` 0.8–1.4 钳制、teal 预设默认与动态取色设置项。
- 对话框确认按钮的品牌强调级别；危险动作 `errorContainer/onErrorContainer`。

## 本轮未做（需专门验收或属产品决策）

1. Expanded ≥840dp 侧边 sheet（当前竖屏锁定，触达设备有限）。
2. medium+ 列表详情双栏 / supporting pane（涉及约 40 个详情页与导航状态交接）。
3. Predictive back 进度动画（需先确认 Navigation Compose 当前版本的返回手势行为）。
4. `calculateWindowSizeClass` / 折叠铰链避让（当前 600dp 切换已工作）。
5. 把 `MaterialTheme.motionScheme` 接入 Prism 动效配置（官方令牌当前未被 Prism 读取；改动会影响全局动画节奏）。
6. teal fallback scheme 补 `*Fixed` 角色（当前无本地消费者，属潜伏问题）。
7. 页面/卡片/内嵌三档表面色的单调重排（品牌灰页白卡是有意设计）。
8. 国际化、真实 TalkBack 人工全路径验收、设备端截图回归。

## 验证

- `:app:compileDebugKotlin` 通过（仅既有弃用警告）。
- `:app:testDebugUnitTest` **104 项通过，0 失败、0 错误、0 跳过**。
- `:app:lintDebug` 通过：**0 error**，11 条既有 warning/hint（未新增；为版本更新提示、既有 ModifierParameter、AES/ECB 等）。
- `:app:assembleDebug` 通过。
- `:app:assembleRelease` 通过；产物 `郑财校园-release-v0.2.50.apk`（18.0 MB），versionCode **55** / versionName **0.2.50**，包名 `com.union.campus`，minSdk 26 / targetSdk 37。
- `apksigner verify --print-certs`：**v2 签名 true**，`CN=Zhengcai Union, OU=Campus, O=Union, L=Zhengzhou, ST=Henan, C=CN`。
- `zipalign -c -P 16`：**Verification successful**（16 KB 对齐）。
- SHA-256：`3F9214FF4FED2C9BEB03288AD29F5648A2BDFE07230D993EC3C1DC233D54A1EE`。
- 未连接设备，未做设备端视觉与 TalkBack 人工走查；本报告不把编译通过当作视觉/无障碍认证。