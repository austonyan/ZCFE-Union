# v0.2.46 — MD3 Expressive 前端改造

## 范围与边界

- 保留 Compose Material3 + Prism，不更换 UI 库或增加字体依赖。
- 保留五个 Tab、滚动内容中的 `PrismPageHeader`、详情页小顶栏与返回路径。
- 表现力来自主次层级、调和色、成组形状和状态反馈；不添加循环装饰动画。
- 课表继续只标记本周今天；不恢复作息横幅、底部 S/J 小字、空闲教室重复入口或调课提醒。
- 不改接口、缓存、绑定、支付、设备操作及提交确认顺序，不引入生产假数据。

## 并行编排

从 `071d4bf` 创建五个独立工作树；主分支 `openchamber-develop` 负责审查、集成、回归与发包。

| 分支 | 所有权 |
| --- | --- |
| `md3-046-components` | `ui/prism`、`ui/theme` 与公共组件测试 |
| `md3-046-today` | `ui/screens/today` |
| `md3-046-study` | `ui/screens/study` |
| `md3-046-life` | `ui/screens/life` |
| `md3-046-profile` | `ui/screens/me`、`ui/screens/onboarding` |

页面使用既有公共 API，公共组件新增参数必须有默认值，避免并行开发形成依赖阻塞。

## 已集成的页面与组件改造

- 公共层：`PrismShapeRole` 语义形状，普通卡 20dp、主卡 28dp，连接式列表与分段控件；筛选采用 `secondaryContainer/onSecondaryContainer` 与勾选；按钮按压形变、加载不改宽且不可重复提交；加载、错误、空态统一容器层次。
- 今天：课程主卡突出课程名、地点与时段，以静态图形表达当前状态；课程时间轨与主卡共享课程色；倒计时使用分钟级文字，不逐秒翻滚。
- 教务与课表：四个常用入口成为连接式功能组；学期主卡与课程详情强调来源关联；切周尾槽固定 88dp，减小方向位移并支持减少动效。
- 生活：服务卡分离地点、读数和状态；低余额、预约、排队、使用中采用配套语义色；不再展示虚构查询时段、固定排队耗时估计或按 40 分钟填充的参考圆环。
- 我的与引导：个人卡与账号保存状态更清晰，不把“凭证已保存”说成“服务器验证成功”；九种预设展示实际浅/暗色板；登录/绑定统一说明、错误容器和键盘避让；房间绑定保留校验并增加本人房间确认。
- 未确认预约仅显示“已提交，请刷新确认”，收到有效预约后才触发成功反馈。超时取消失败不再宣称“已自动取消”。

## 主分支补充

- `UnionApp` 的 Dock、详情和 Tab 转场响应系统减少动效设置。
- `StateContent` 的加载/失败/空/正常状态转场使用可退化为 snap 的动效规格。
- 新增 `AppTabNavigationTest`，覆盖五个 Tab 可达、选中语义、设置入口及返回“我的”。
- 新增 5 个公共组件测试，覆盖 18 主题调和对比度、大字号分段、加载按钮、芯片宽度及动态删行。
- 配色脚本扩充 `onSurface/onSurfaceVariant` 在全部中性表面上的校验，覆盖连接式功能组所用的不同色阶。

## 校验记录

### 改造前基线

- 编译、91 个单元测试、debug 打包、lint 全部成功；单元测试 0 失败、0 跳过。
- lint 基线：0 error、10 warning、2 hint（共 12 个非错误项）。
- `python tools/check-contrast.py`：18 套 scheme 的配色与表面层级检查全部通过。
- 使用已签名 v0.2.45 APK，在 MuMu Android 15 采集五个 Tab 的 364dp 宽基线截图。
- 截图保存于本机临时目录 `union-md3-046`，不将设备截图或账号数据提交到仓库。

### 集成后

- `compileDebugKotlin`、`compileDebugAndroidTestKotlin`、`testDebugUnitTest`、`assembleDebug`、`connectedDebugAndroidTest`、`lintDebug`、`assembleRelease` 均通过一次完整集成构建。
- 单元测试：91 项通过；Android 15 instrumentation：12 项通过，包含五 Tab 导航与新增 5 项组件回归。
- 视觉收口后再次执行单测、instrumentation、lint 与 release 构建，全部通过；第二轮 instrumentation 在系统三项动画缩放均为 0 时运行，12 项通过。
- 最终 lint：0 error、10 warning、2 hint，与改造前基线相同；新增服务卡的 ModifierParameter 警告已修复。保留项主要为既有参数顺序、上游加密协议、Locale 与依赖升级提示；编译仍有既有/调用处的弃用 API 警告。
- 扩充后的配色脚本：18 套 scheme 全部通过。
- Android 15 / 364dp：采集并查看五 Tab、设置、欢迎页亮暗截图；额外检查 1.4 倍字号，主要操作与底栏未见裁切。根据截图缩小今天空态主卡，移除生活缺失数据的超大破折号占位，避免无数据时首屏被空指标占满。
- 第一轮集成 lint 曾发生 IntelliJ PSI 分析内部异常；冻结源码后完整重跑成功，没有通过抑制检查绕过。
- 测试安装通过本机临时 Gradle init script 为 debug 复用既有 release 签名，保留模拟器已安装应用的数据；未改仓库的 debug 签名配置，未卸载或清空用户应用数据。
- 已验证 release 的版本为 `0.2.46` / versionCode `51`，包名 `com.union.campus`，签名证书与旧版相同，APK Signature Scheme v2 通过。
- 五个临时工作树均在提交、集成且确认干净后移除；保留 `md3-046-*` 分支与提交历史。

## 发包

- 文件：仓库根目录 `郑财校园-release-v0.2.46.apk`（按现有 `.gitignore` 不提交 APK 二进制）。
- 大小：17,909,438 字节。
- SHA-256：`a037a417c5a7145ebd3c1ff8a4b4bf8ca0391c070df1034917140228ec756312`。
- 最终 APK 已覆盖安装并冷启动，重新查看今天与生活空数据卡片截图。模拟器字体、主题、密度与动画设置在检查后恢复原值。

### 验证边界

- 设备走查为无账号场景；没有实际发送选课、评教、支付或洗浴设备开关等写操作。
- 未逐台覆盖 Android 8–16、全部 OEM、动态壁纸色或全部折叠屏形态；18 套静态预设的数学对比度检查不等于全部动态主题已完成实机验收。
- 现有字号钳制（最高 1.4）与课表网格字号锁定未改变，不能声称支持 2 倍字号。
- 未建立正式帧耗时 benchmark，不对 60/120Hz 流畅性作量化承诺。
