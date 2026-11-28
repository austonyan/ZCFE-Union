# 界面 UX 审查与提升（尼尔森十大原则）

- 日期：2026-11-28
- 范围：`app/src/main/java/com/union/campus/ui`（34 屏 + `ui/prism` 组件层）与 `app` 导航壳
- 方法：按原则逐条走查代码与状态机，每条给出 `文件:行` 证据；未连接设备，未做真机视觉/TalkBack 复核（见「未覆盖」）
- 约束：本轮只改界面与提示文案，不动协议、仓库层数据流与发布版本号

## 结论摘要

| # | 原则 | 现状 | 本次 |
|---|------|------|------|
| 1 | 系统状态可见性 | 骨架屏 / 下拉刷新 / 同步进度 / 上次同步时间已有；**成绩、考试没有主动刷新与时间信息** | 已补顶栏刷新（就地转圈）+ 成绩缓存时间 |
| 2 | 环境贴切 | 达成：深色/跟随系统、动态取色、9 套预设、夜间启动主题 | 无改动 |
| 3 | 用户可控 | 破坏性操作均有二次确认与 destructive 样式；**缺"撤销"与失败后的恢复路径** | 已补提醒暂停「撤销」、解绑「重新绑定」 |
| 4 | 一致性 | 达成：组件层单点、页面框架统一 | 帮助页沿用既有组件，不新造样式 |
| 5 | 防错 | 达成：逐字段校验、验证码按需、短信冷却、本人学号限制 | 无改动 |
| 6 | 易取（识别而非回忆） | 达成：学期/周次/页码/筛选均可恢复 | 无改动 |
| 7 | 灵活高效 | 达成：「返回本周」、下拉刷新、分档筛选 | 刷新入口补到成绩/考试 |
| 8 | 优美简约 | 达成：统一分区节奏、限宽、无装饰性图表 | 帮助页沿用同一节奏 |
| 9 | 容错 | **原始异常文案会直接上屏**（`java.net.*`、HTTP 片段） | 已修：统一 `userFacingMessage()`，37 处 |
| 10 | 人性化帮助 | 有局部帮助（登录页弹窗、空态下一步）；**没有集中帮助入口** | 新增「帮助与常见问题」页 + 两个入口 |

## 逐条证据与处理

### 1. 系统状态可见性

已达成：

- 加载即骨架，不闪空白：`ui/common/ScreenScaffold.kt:131`（`PrismSkeletonList`）、`ui/screens/study/GradesScreen.kt:299`
- 今天 / 生活支持下拉刷新：`ui/screens/today/TodayScreen.kt:250`、`ui/screens/life/LifeScreen.kt:97`
- 作业同步有"转圈图标 + 进度文案 + 上次同步时间"：`ui/screens/homework/HomeworkScreen.kt:172`、`:532`
- 电费、洗浴、作业、空闲教室均有右上角刷新：`ui/screens/life/ElectricScreens.kt:143` 一带

缺口（本次修复）：

- 成绩、考试只有"出错后的重试"，没有用户主动刷新入口；成绩缓存回读时 `updatedAt` 已有值却没展示。
  → `GradesScreen.kt:194` 新增 `refresh()`（保留列表、只在顶栏转圈，完成给成功/失败提示），`:249` 起顶栏加刷新；`:248` 用 `formatUpdatedAt` 展示缓存时间（只有可信时间戳时才写）。
  → `ExamsScreen`（`ui/screens/study/DetailScreens.kt:305` 起）顶栏加刷新；`RouteWrappers.kt:42` 起区分"首次加载"与"刷新"，刷新时不清空列表。

### 2. 环境贴切

- 外观三档 + 动态取色 + 9 套预设：`ui/screens/me/SettingsScreen.kt`「外观」分区
- 深色启动主题：`app/src/main/res/values-night/themes.xml`
- 洗浴/电费使用各自业务语义（空闲数、余额、预计可用天数），不是通用占位文案

### 3. 用户可控

已达成：

- 全部退出 / 单账号退出均二次确认，确认按钮走 destructive 样式：`ui/screens/me/CredentialScreens.kt:88`、`:107`
- 洗浴启动/结束、解绑房间、清除数据均有确认

缺口（本次修复）：

- 提醒总开关一关就静默生效，误触只能再点一次 → `ui/screens/me/RemindersScreen.kt:183` 的提示条加「撤销」，点击恢复（提示带动作时停留 5s，见 `ui/prism/PrismMessenger.kt:109`）
- 电费解绑后 `rooms` / `currentRoom` 停在旧值，切换菜单可能残留已解绑房间，且没有回到刚解绑状态的路径 → `ui/screens/life/ElectricScreens.kt:352` 起：解绑后按服务端重建房间列表、自动落到下一间，提示条给「重新绑定」

### 4. 一致性

- 组件单点：卡片/对话框/开关/分段控件都在 `ui/prism`，页面不各自造样式（如 `PrismAlertDialog`：`ui/prism/PrismComponents.kt:941`）
- 页面框架统一顶栏与返回语义：`ui/common/ScreenScaffold.kt:88` 起
- 新的帮助页只组合 `PrismSection` / `PrismCard` / `PrismRow` / `GroupDivider`，与设置、关于页同一节奏

### 5. 防错

- 登录表单逐字段定位焦点，而不是一句"输入有误"：`ui/screens/onboarding/LoginAcademicScreen.kt:119`
- 验证码按需出现、刷新有独立加载态：同文件 `:265` 一带
- 洗浴短信 60s 冷却、验证码登录与密码登录互斥校验：`ui/screens/onboarding/LoginShowerScreen.kt:101`
- 电费只允许本人学号：`ui/screens/onboarding/BindElectricScreen.kt`

### 6. 易取（识别而非回忆）

- 学期与"全部/必修"口径、周次、订单分页、作业筛选都做了保存：`ui/screens/study/GradesScreen.kt:105`、`ui/screens/study/TimetableScreen.kt:118`、`ui/screens/life/ElectricScreens.kt:209`、`ui/screens/homework/HomeworkScreen.kt:99`
- 帮助页正文也按此原则写：给"去哪儿点"，而不是让人记概念

### 7. 灵活高效

- 课表翻周后可一键「返回本周」：`ui/screens/study/TimetableScreen.kt:400`
- 作业按截止压力分组、可在"未交/全部"间切换
- 本次把刷新入口补齐到成绩与考试，减少"退出重进"这种低效操作

### 8. 优美简约

- 分区标题、间距、卡片层级只有一套：`PrismSection` / `SectionHeader` / `PrismSpace`
- 大屏限宽：`ui/common/PageWidth.kt`，正文不出现超长行宽

### 9. 容错（本次重点）

问题：14 个文件的 `catch (e: Exception)` 直接把 `e.message` 拼进提示，网络层会把 `java.net.UnknownHostException`、`HTTP 500`、解析片段原样带到界面上；`toAppError()` 也把原始 message 当 `userMessage` 用。

处理：

- `core/AppError.kt:31` `toAppError()` 现在区分 `IOException → Network`，其余 `Unknown`，原始文案只进 `debugDetail`
- `core/AppError.kt:41` 新增 `Throwable.userFacingMessage()`：`AppError` 用既定说法，其它异常给"网络连接失败…"/"操作失败，请稍后重试"
- 37 处界面提示改用它，覆盖教务、学习通、洗浴、电费、评教、选课、学籍、凭证中心（`grep -rn "userFacingMessage()" app/src/main/java` 可复核）
- 考试刷新失败不再把整页换成错误态：保留上次列表，用 warning 横幅说明"显示的是上次结果"并给「重试」（`DetailScreens.kt:352`）
- 已有良好实践保留：认证失效会指路「我的 → 凭证中心」（`ScreenScaffold.kt:141`）、洗浴业务限制有专门文案（`ShowerErrorTest`）

### 10. 人性化帮助

已达成（局部）：登录页「帮助」弹窗、初始化 5 步进度、空态文案给下一步、错误态说明原因。

缺口（本次新增）：

- 应用没有一处能"自己查"的地方：用户遇到"课程拉不到""收不到提醒""电费为什么没有金额"只能猜。
- 新增 `ui/screens/me/HelpScreen.kt`：7 个主题、16 条问答（账号、课表与教务、学习通作业、洗浴、电费、提醒与通知、隐私与数据），点问题展开答案，展开状态可恢复，读屏播报"已展开/已收起"；底部给 GitHub Issues 反馈入口（`ExternalLinks.Feedback`）
- 入口两处：「我的」列表（`ui/screens/me/MeScreens.kt:231`）、「设置 → 支持」（`ui/screens/me/SettingsScreen.kt:176`）
- 内容只写已验证行为（如"只在只读同步，不代提交""首次同步不补发历史提醒""免打扰时段可设"），不承诺未实现的能力

## 本次改动清单

| 文件 | 改动 | 原则 |
|------|------|------|
| `core/AppError.kt` | `toAppError()` 分级 + 新增 `userFacingMessage()` | 9 |
| 14 个屏幕文件 | 37 处原始异常文案 → 用户可读文案 | 9 |
| `ui/screens/study/GradesScreen.kt` | 顶栏刷新（就地转圈）、缓存时间副标题、刷新结果提示 | 1 / 7 |
| `ui/screens/study/DetailScreens.kt` + `RouteWrappers.kt` | 考试顶栏刷新、刷新不清空内容、刷新失败保留旧数据 + 横幅 | 1 / 7 / 9 |
| `ui/screens/life/ElectricScreens.kt` | 解绑后重建房间列表、恢复路径「重新绑定」 | 3 / 1 |
| `ui/screens/me/RemindersScreen.kt` | 暂停全部提醒给「撤销」 | 3 |
| `ui/screens/me/HelpScreen.kt`（新增） | 集中帮助页（7 主题 / 16 问答 + 反馈） | 10 |
| `navigation/Routes.kt`、`app/UnionApp.kt` | `Help` 路由与页面注册 | 10 |
| `ui/screens/me/SettingsScreen.kt`、`MeScreens.kt` | 「帮助与常见问题」入口 | 10 |
| `ui/common/ExternalLinks.kt` | `Feedback` 链接常量 | 10 |
| `app/src/test/.../AppErrorCopyTest.kt`（新增） | 3 项：原始异常不上屏、IO → 网络文案、AppError 保留原文案 | 9 |
| `work/UnionNotifications.kt` | 新增 `requestPostNotificationPermission()`：固定 requestCode，绕开启动器自动码超 16 位的崩溃 | 5 / 9 |
| `ui/screens/me/RemindersScreen.kt`、`onboarding/BindDoneScreen.kt` | 通知权限改用上面的固定码入口（设备实测崩溃修复） | 5 / 9 |

## 验证

构建与单测（本机 Android SDK 已迁至 `D:\Android\Sdk`，JDK 25 + Gradle 9.7.1）：

```bash
./gradlew.bat assembleDebug          # BUILD SUCCESSFUL
./gradlew.bat testDebugUnitTest      # 含新增 AppErrorCopyTest
./gradlew.bat lintDebug
```

设备实测（模拟器 `Medium_Phone_API_37.0`，Android 17，WHPX）：

- 首次启动仍是「一帧空白 → 欢迎页」（`app/UnionApp.kt:119`，见「未覆盖」第 1 条）
- 今天 / 生活 / 教务 / 我的 / 设置渲染正常，空态文案与「去绑定」入口都在
- 帮助页：两处入口可用，问答展开/收起、分隔线随内容重排正常，展开状态在页面内保持
- 提醒中心：关掉总开关 → 提示条带「撤销」；点撤销后 `union_settings.preferences_pb` 里 `remind_enabled` 回写为 `true`（模拟器上 DataStore 落盘约 2–3s，不是逻辑问题）
- 成绩页：顶栏刷新在未登录时给「登录教务后才能同步成绩」，不再点了没反应
- **发现并修复的崩溃**：点「开启提醒」/ 绑定完成页的提醒开关时，`rememberLauncherForActivityResult` 自动生成的 requestCode 超出低 16 位，抛 `IllegalArgumentException: Can only use lower 16 bits for requestCode`（栈顶 `FragmentActivity.checkForValidRequestCode` → `ActivityCompat.requestPermissions`）。改用 `UnionNotifications.requestPostNotificationPermission()`：固定 `0x6e01` + `ActivityCompat.requestPermissions`，结果由 ON_RESUME 重读 `canPost`。修复后系统授权框正常弹出，允许后「系统通知未开启」卡片自动消失

静态自查：

- 新用到的 API 均对齐仓库既有签名：`listSaver` 用法同 `GradesScreen.kt:667`；`PrismLoadingIndicator(modifier, size)` 见 `ui/prism/PrismControls.kt:190`；`PrismMessenger.show(text, tone, actionLabel, onAction)` 见 `ui/prism/PrismMessenger.kt:45`
- 新增参数一律带默认值（`ExamsScreen.refreshing`、`SettingsScreen.onOpenHelp`、`ProfileScreen.onOpenHelp`），不破坏既有调用
- 数据层零改动：本轮不涉及协议、缓存键与 WorkManager

回归面与风险：

- 错误文案统一影响所有 `catch (e: Exception)` 分支：只会把"技术文案"换成"可读文案"，`AppError` 分支行为不变
- 考试刷新不再清空列表：首次进入仍是骨架屏，行为不变
- 帮助页是纯静态内容 + 一个外链，无网络与状态依赖

## 未覆盖（建议下一轮）

1. 冷启动 `Box(Modifier.fillMaxSize())`（`app/UnionApp.kt:119`）与系统启动画面之间仍有一帧空白（设备实测复现），可合并为品牌启动页
2. 欢迎页「暂不绑定」不写 `boundDone`，所以没绑账号的用户每次冷启动都会再看一次欢迎页；是否改成「跳过即视为完成后不再拦」需要产品确认
3. 表单返回直接丢弃已输入内容（只有评教页有 `BackHandler`：`ui/screens/study/EvaluationScreen.kt:222`），登录/绑定页可加"放弃填写？"
4. 空闲教室、洗浴缺少手动刷新入口，依赖回到前台重载
5. 高频操作快捷方式（如洗浴"再来一次上次设备"）仍缺，属灵活高效原则的下一层
6. 设备走查只覆盖了 720×1600 一档：TalkBack、大字号、1200×2670 视觉复核仍未做（上一轮见 `docs/layout-regression-1200x2670.md`）