# 前端 / MD3 合规审查 — Union v0.2.44

- Target: `master` HEAD `81f2b9a`（v0.2.44，versionCode 30）
- Date: 2026-11-19
- Stack: Compose（AGP 9.3.2 / Compose BOM 2026.09.00 / material3 1.5.0-rc01 Expressive）+ 自定义 `prism` 设计层
- 规模: 100 个 .kt / 23,446 行；`ui/screens` 11,426 行（23 文件）、`ui/prism` 3,652 行、`ui/theme` 1,194 行
- 方法: 源码逐类审查 + 配色对比度实算（WCAG 2.x，sRGB 相对亮度）+ `./gradlew :app:lintDebug`
- Lint: **0 error / 25 warning**

Overall Score: **71 / 100**（修复后 **88 / 100**，见文末《本轮修复记录》）

> 口径说明：上一份 `m3-audit-report.md`（v0.2.16，91 分）没有把「9 套预设配色 × 亮/暗两态」纳入对比度计算，也没把
> Android 16 屏幕方向策略计入布局项。本次分数下降是**口径变严**，不是代码退步——v0.2.17 之后新增的
> classroom / grades 改版、预设色板、按压三态都属于净改进。

## 分项得分

| 类别 | 分数 | 状态 | 依据 |
|---|---|---|---|
| Color tokens | 6/10 | warn | 页面 0 处裸 hex（仅设置页色卡预览 9 处），但 8/9 套预设的主色/白字双向不达标 |
| Typography | 8/10 | pass | `PrismType` 12 档唯一定义、全项目仅 1 处 `fontSize` 字面量（dock 标签，已注释）；fontScale 钳到 1.4 |
| Shape | 8/10 | pass | 连续圆角 + `PrismMaterialShapes` 桥接 M3；3 处 `RoundedCornerShape` 绕过、色阶缺 12 档 |
| Elevation | 8/10 | pass | SurfaceLevel 四级 tonal；默认品牌暗色 page/card 仅 1.13:1（低于自定 1.2 阈值） |
| Components | 7/10 | pass | 13 个 Prism 组件、Switch/Tab/Button role 齐；Chip 缺 selected 语义、IconButton 缺 role/disabled 语义 |
| Layout | 5/10 | warn | 仅 `UnionScreenScaffold` 限宽 840dp；4 个 tab/引导页无限宽；0 处 WindowSizeClass；Android 16 将忽略竖屏锁 |
| Navigation | 9/10 | pass | Dock = `selectable(Role.Tab)` + `selectableGroup`、saveState/restoreState、popBackStack 去重、sheet BackHandler |
| Motion | 7/10 | pass | `PrismSpec` 语义化规格；48 处散写 tween/spring、reduce-motion 仅 4 个组件生效 |
| Accessibility | 5/10 | warn | 对比度硬伤（见 P0）；0 个 testTag、0 个 androidTest、0 处 stateDescription；48dp 触达达标 |
| Theming | 8/10 | pass | 动态取色 + 9 预设 + 系统栏图标跟随；品牌青在 Color.kt、其余预设由脚本生成，两套来源 |

## P0 — 必修（有具体数值）

### 1. 8 套预设的 `onPrimary` 不足 4.5:1 → 所有填充按钮/选中 chip 的白字不达标

填充按钮（`PrismButton(filled = true)`）与选中 chip 都是 `primary` 底 + `onPrimary` 字（12–15sp），
WCAG 正常文字要求 4.5:1。实测（浅色）：

| 预设 | onPrimary/primary | primary/page（作文字色） |
|---|---|---|
| blue | 4.02 | 3.27 |
| purple | 4.17 | 3.40 |
| pink | 3.65 | 2.98 |
| red | 3.57 | 2.91 |
| orange | 3.18 | 2.59 |
| yellow | 3.18 | 2.59 |
| green | 3.18 | 2.59 |
| gray | 3.26 | 2.66 |
| **teal（默认/品牌）** | **9.28** | **7.61** |

暗色还有 blue 3.61 / purple 3.75 / pink 3.29 / red 3.23 不达标。

原因：8 套色板用 FlClash `seededColorScheme` 生成，直接把 iOS 系统强调色当 `primary`（橙/黄/绿 ≈ tone-55），
既不够深到承载白字，也不够浅到单独作文字色——两个方向同时失败。
`app/src/main/java/com/union/campus/ui/theme/PresetPalettes.kt`（16 个 scheme）。

**修法**：生成脚本改为按 HCT 取 tone—亮色 `primary = tone40`、`onPrimary = tone100`，暗色 `primary = tone80`、
`onPrimary = tone20`（这正是 M3 的做法）；品牌青已经符合，可直接沿用为参照。改完重算 8 × 2 × 2 个值即可闭环。

### 2. `PrismAlpha.Secondary` / `Hint` 叠在 `onSurfaceVariant` 上，浅色下双重压暗

`onSurfaceVariant` 本身已经是对比度调好的令牌（浅色 9.1:1），再乘 0.72 就掉到：

- `PrismAlpha.Secondary = 0.72`（24 处）→ **页面 3.81–3.85:1、卡片 4.25–4.32:1**（低于 4.5）
- `PrismAlpha.Hint = 0.55`（9 处）→ **2.63–2.86:1**（占位说明、图表刻度）

`ui/prism/PrismTextRole.kt:34`、`PrismComponents.kt:372/388/444/486/554/612`、`ui/screens/me/MeScreens.kt:269` 等。
暗色两档都合格，**只有浅色 9 套主题**受影响。

**修法**：文字层级不要再乘 alpha。`Secondary` 直接用 `onSurfaceVariant`（1.0），`Hint` 若一定要更弱，
用 0.72 且只用于 ≥13sp 的说明文字；或按主题新增 `onSurfaceMuted`（亮 tone50 / 暗 tone70）并参与对比度校验。

### 3. 品牌暗色的页/卡层级 1.13:1，低于自己写下的 1.2 阈值

`PrismSurface.kt` 注释明确「相邻层级低于 1.2:1 时边界不可辨」，实测：

| | page | card | 比值 |
|---|---|---|---|
| 默认品牌（DarkColors） | `#0D1515` | `#1A2121` | **1.13** |
| 其余 8 套预设（dark） | — | — | 1.22–1.23 |

`PresetPalettes.scheme("teal")` 返回 null → 回退 `Color.kt` 的 `DarkColors`，所以**唯一不达标的正好是默认主题**。
`ui/theme/Color.kt:136`（surfaceDim = TealSurfaceDark）+ `surfaceContainer`。

**修法**：`surfaceDim` 压到 `#0A1010` 或 `surfaceContainer` 提到 `#1C2424`，两边任选一个即可回到 1.2 以上。

### 4. 竖屏锁在 Android 16 起被系统忽略，而布局没有为大屏准备

Lint 已报警：`LockedOrientationActivity` + `DiscouragedApi`（`AndroidManifest.xml:23`）——
「Fixed screen orientations will be ignored in most cases, starting from Android 16」。届时横屏/平板展开会真实发生，而：

- `WindowSizeClass` / `currentWindowAdaptiveInfo` / `ListDetailPaneScaffold`：**0 处**
- 宽屏限宽 `widthIn(max = 840.dp)` 只在 `ui/common/ScreenScaffold.kt:96`，**17/23 个屏幕文件**走这条路径
- `TodayScreen.kt`、`AcademicsScreen.kt`、`LifeScreen.kt`、`WelcomeScreen.kt` 四个根页面无限宽

**修法**（按性价比排序）：① 把 840dp 限宽抽成 `Modifier.pageWidth()` 并补到 4 个 tab 根页；② 至少接
`WindowSizeClass` 做 600dp 以上的双栏（今天页/课表最合适）；③ 产品层面确认是否放弃竖屏锁。

## P1 — 应修

| 项 | 证据 | 建议 |
|---|---|---|
| 筛选 chip 没有选中语义 | `PrismControls.kt:57` `Surface(onClick=…)`，TalkBack 只读「按钮」，读不出当前筛选项 | 改 `selectable(selected, role = Role.RadioButton)` 或 `toggleable(role = Role.Checkbox)`，外加 `selectableGroup()` |
| IconButton 缺 role 与禁用语义 | `PrismComponents.kt:642` 裸 `clickable(enabled)`，禁用只压 0.38 alpha | 补 `role = Role.Button`；禁用时用 `Modifier.semantics { disabled() }` |
| reduce-motion 只覆盖 4 个组件 | `prismAnimationsEnabled()` 仅在 Skeleton / GpaRuler / Dock 脉冲 / 控件呼吸点使用 | 让 `PrismMotion` 读取该开关，系统关闭动画时统一返回 `snap()` |
| 动效参数重复定义 | `PrismGpaRuler.kt:86`、`DetailScreens.kt:80` 重写 `spring(1f, 420f)`（= `PrismSpec.sheetIn`）；`PrismDock.kt:120` 重写 `pressDown`；`BindDoneScreen.kt:71` 自造 `spring(0.7f, 320f)` | 一律引用 `PrismSpec`，只保留语义条目 |
| 间距令牌基本没落地 | `PrismSpace.*` 全项目仅 14 次引用；`ui/screens` 里 465 处 dp 字面量（16/12/8/6/5/4/2） | 要么删掉 `PrismSpace` 避免假令牌，要么 lint 规则强制；建议保留并逐步替换 |
| 圆角令牌有漏档 | `PrismCorner` 缺 12；`prismShape(12f)` 2 处、`SettingsScreen.kt:255` `RoundedCornerShape(12.dp)`、`GradesScreen.kt:538` `RoundedCornerShape(4.dp)`、`ClassroomScreens.kt:1167` `RoundedCornerShape(Corner)` | 补 `SmPlus = 12f`；这 3 处改走 `prismShape()`，否则和连续圆角的边缘不一致 |
| 色卡 hex 双写 | `SettingsScreen.kt:226-234` 的 9 个 `Color(0xFF…)` 与 `PresetPalettes`/`Color.kt` 各自维护 | 让 `PresetPalettes` 暴露 `seedColor(id)`，设置页取同一来源 |
| 全局可变状态不可恢复 | 0 个 ViewModel；`app/AppState.kt` 用 `object` 单例塞路由载荷（7 个 `mutableStateOf`） | 进程被杀后由 `popBackStack` 兜底，但会丢上下文；换 `SavedStateHandle` 或至少 `rememberSaveable` 存 id |
| 单文件过大 | `ShowerScreens.kt` 1830 行、`ClassroomScreens.kt` 1371、`MeScreens.kt` 1040 | 按「列表 / 详情 / 组件」拆，纯文件体量问题，改动风险可比性最高 |
| UI 无测试 | `testTag` 0 处、无 `androidTest`；16 个单测全在数据/解析层 | 先给课表、洗浴预约、电费三条主链加 3 个 `composeTestRule` 冒烟 |

## P2 — 顺手清

- `PrismPress.kt:42` 注释仍写「禁用 ripple」，但 `Theme.kt` 已不再设 `LocalRippleConfiguration = null`（注释过期）
- `AndroidManifest.xml:11` `enableOnBackInvokedCallback` 在 minSdk 26 下标注 API 33（可搬进 `values-v33` 或忽略）
- `ShowerScreens.kt:1760` `String.format` 未指定 Locale（`DefaultLocale`）
- `ShowerScreens.kt:1279/1645` `mutableStateOf<Long/Int>` 应换 `mutableLongStateOf/mutableIntStateOf`
- `PrismDock.kt:102`、`PrismSegmented.kt:71`、`PrismSwitch.kt:94` 用非 lambda 的 `offset`，滑块动画每帧重组
- `ModifierParameter` ×5（`PrismComponents.kt:117/689`、`ElectricScreens.kt:302`、`ShowerScreens.kt:118/1220`）——`modifier` 应是第一个可选参数
- `ServiceLocator` 三处 `StaticFieldLeak`：应用级单例持 Application Context，实际不泄漏，可 `@Suppress("StaticFieldLeak")` 并注明
- `supportsRtl="true"` 但没有 RTL 适配、面向中文校园场景，建议移除该声明
- fontScale 钳到 0.8–1.4：保护了单行页头和课表行，但系统允许 2.0，属于可访问性取舍，建议在设置页提供「界面字号」而不是硬钳

## 通过项（不用动）

- **导航语义**：Dock `selectable(Role.Tab)` + `selectableGroup()`、Segmented 同款、图标 `contentDescription = null` 交由文字承载（`PrismDock.kt`、`PrismSegmented.kt`）
- **按压三态**：`PrismPressIndication` 同时处理 Press / Hover / Focus（ink α0.10、hover 0.06、focus 0.12），无涟漪但键盘/鼠标有反馈
- **边到边**：`enableEdgeToEdge()` + `WindowInsetsControllerCompat` 跟随 `darkTheme` 切状态栏图标明暗；Dock `navigationBarsPadding()`
- **页面外壳**：33 处页面统一 `UnionScreenScaffold`，0 处裸 `Scaffold`；Loading/Failed/Empty/Ready 走 `StateContent` 淡入淡出
- **语义状态色**：`PrismStatus` 六色实测 onContainer/container **9.9–11.0**，accent/page 亮 4.6–6.5、暗 10.7–11.4 —— 全部达标（上一份报告的 W8 已解决）
- **课程 12 色板**：容器/文字 **7.46–8.67（亮）/ 9.54–10.65（暗）**，冷色相收敛，全部达标
- **字阶单点定义**：`PrismType` 12 档，全项目仅 dock 标签 1 处字面量；`MaterialTheme(typography/shapes/motionScheme)` 均接入
- **形状桥接**：`PrismMaterialShapes` 把 8/20/16/24/28 映射到 M3 `Shapes`，原生 Menu/DatePicker/Slider 与 Prism 圆角一致
- **触达尺寸**：`PrismIconButton` 48dp、`PrismButton` `minHeight 40 + minimumInteractiveComponentSize`、chip 同样处理
- **空态文案**：`NullStatus` + `PrismErrorState` 区分 auth 失败（提示去凭证中心）与普通失败，重试入口统一

## 修复顺序建议

1. 预设色板按 tone 重算（P0-1，一次脚本改动覆盖全部按钮与 chip）
2. 文字层级去掉双重 alpha（P0-2，改 PrismTextRole + PrismAlpha 两个文件，收益 33 处）
3. 默认暗色 surfaceDim 调一档（P0-3，一行值）
4. 840dp 限宽抽成修饰符铺到 4 个根页（P0-4 第一步，可先挡 Android 16）
5. Chip / IconButton 语义补齐（P1，半小时内完成）
6. PrismMotion 接 reduce-motion 开关 + 收敛散写 spring（P1）
7. 拆分 ShowerScreens / ClassroomScreens（P1，纯结构，不改行为）
8. 3 条主链 compose UI 冒烟（P1，防回归）


---

# 本轮修复记录（2026-11-19）

6 个修复分支，各自独立 worktree 并行完成，逐次合并后在 master 上全量验证。

## 已修复

| 编号 | 问题 | 结果 |
|---|---|---|
| P0-1 | 8 套预设 `onPrimary/primary` 3.18–4.17 | 按 M3 tone 重算（亮 tone40/onPrimary tone100，暗 tone80/tone20）；实测 **5.23–5.49**（文字类最小对），`onPrimary/primary` 亮 6.43–6.48、暗 7.69–7.76 |
| P0-1' | `primary` 作文字色 2.59–3.40 | 重算后 **5.23–5.30** |
| P0-2 | `Secondary(0.72)`/`Hint(0.55)` 二次压暗 | 新增 `prismTextMuted()`（onSurfaceVariant 1.0）与 `prismTextFaint()`（0.85）；31 处文字迁移完毕，亮色实测 muted **9.14:1**、faint **6.08:1**（最差预设 5.21 / 6.02） |
| P0-3 | 品牌暗色 page/card **1.13:1** | 新增 `TealSurfaceDimDark = #060C0C`，现 **1.20:1**；container 五档排序仍单调 |
| P0-4 | 大屏正文无限宽 | 新增 `ui/common/PageWidth.kt` 的 `Modifier.pageWidth()`（840dp 居中）；`ScreenScaffold` 复用之，并补到 Today / Academics / Life / Welcome / Timetable / Profile 共 6 处 |
| P1 | Chip 无选中语义 | 全部调用点经确认为单选 → `selectable(role = Role.RadioButton)`，保留「已选中不再回调」行为 |
| P1 | IconButton 缺 role/禁用语义 | `role = Button` + `semantics { disabled() }` |
| P1 | reduce-motion 覆盖不足 | `PrismMotion.animationsEnabled(context)` + `rememberPrismMotion()`；Dock / Segmented / Switch / GpaRuler / Chip / DetailScreens / BindDone 全部接入，关动画时退化 snap |
| P1 | 动效参数重复定义 | GpaRuler + DetailScreens → `PrismSpec.sheetIn`；Dock → `pressDown`；BindDone → 新增 `PrismSpec.entranceBouncy` |
| P1 | 超大屏幕文件 | ShowerScreens 1830→1000（+4 文件）、ClassroomScreens 1371→4 文件（最大 523）、MeScreens 1040→4 文件（最大 337）；纯搬运，`XxxScreen` 签名零改动 |
| P1 | UI 无测试 | 新增 `app/src/androidTest`：Chip 选中语义、IconButton disabled 语义、Switch 状态、MainActivity 启动冒烟，共 4 类 |
| P2 | lint 遗留 | StaticFieldLeak×3（抑制+注释）、UseKtx、ObsoleteSdkInt×2、UnusedAttribute、DiscouragedApi、LockedOrientationActivity、supportsRtl 移除、PrismPress 过期注释、mipmap-anydpi-v26 合并 |

## 验证

- `:app:compileDebugKotlin` `--rerun-tasks` → BUILD SUCCESSFUL
- `:app:compileDebugAndroidTestKotlin` + `:app:assembleDebugAndroidTest` → BUILD SUCCESSFUL（产出测试 APK）
- `:app:testDebugUnitTest` → 全部通过
- `:app:lintDebug` → **0 error / 12 warning**（修复前 0 error / 25 warning）
  - 剩余：`ModifierParameter`×4、`NewerVersionAvailable`×3、`AutoboxingStateCreation`×2、`DefaultLocale`、`AndroidGradlePluginVersion`、`GetInstance`（均为明确不做的项：需同步改外部调用点 / 依赖升级 / 上游协议要求）
- `python tools/check-contrast.py` → 18 个 scheme 全 PASS（含 container 排序与 1.2 层级阈值）

## 仍未处理（下一轮候选）

- `NullStatus` 的 `ModifierParameter`：需同步改 6+ 处位置参数调用点
- `ShowerScreens.kt` 仍有 1000 行（单 composable 内部尚未提取子件，属行为敏感改动）
- `WindowSizeClass` / `ListDetailPaneScaffold` 双栏仍未接入（只做了限宽，未做多窗格）
- `AppState` 全局单例（0 ViewModel）未改
- 依赖版本升级（`NewerVersionAvailable`×3、AGP/Gradle）
