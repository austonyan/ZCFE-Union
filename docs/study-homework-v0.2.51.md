# 学习通作业提醒（v0.2.51）

把学习通（超星）网页端的**未交作业**搬进 App：按截止时间排序、按剩余时间分档配色，
截止前 24 / 6 / 1 小时各发一次本地通知，并在今天页聚合出一条待办。

协议结论复用自开源项目 [xxt-assistant](https://github.com/led629263224-cmyk/xxt-assistant)
（MIT，作者自行实测），App 内为 Kotlin 重新实现，未引入该项目的任何代码或依赖。
**只读**：不提交作业、不答题、不刷课、不修改任何学习通数据。

## 一、用到的接口

| 用途 | 请求 | 关键点 |
| --- | --- | --- |
| 登录 | `POST https://passport2.chaoxing.com/fanyalogin` | `uname`/`password` 为 AES-128-CBC(PKCS7)+base64，key=iv=`u2oh6Vu^HWe4_AES`；`fid=-1`、`refer` 为 URL 编码的 `https://i.chaoxing.com` |
| 会话探测 | `GET https://i.chaoxing.com/base` | 被跳回 `passport2.chaoxing.com/login` 或含“用户登录”即视为失效 |
| 课程列表 | `GET https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata` | 需要个人主页里的 `fid`（学校标识），`courseType=1` |
| 课程签名 | `GET https://mooc1.chaoxing.com/visit/stucoursemiddle` | 隐藏域 `enc` / `openc` / `workEnc`；**作业列表必须用 `workEnc`**，通用 `enc` 不通用 |
| 作业列表 | `GET https://mooc1.chaoxing.com/mooc2/work/list` | 必须带 `t`（毫秒时间戳）与 `stuenc`（=课程页 `enc`）；返回“没有此页面访问权限”说明签名过期，重拉课程页再试一次 |
| 作业详情 | 列表条目 `data` 属性的直链 | 截止时间只存在于文本“作答时间: MM-DD HH:MM 至 MM-DD HH:MM” |

登录失败的报文实测：`{"msg2":"用户名或密码错误","status":false}`（加密字段被服务端正常解开，
才会走到账号校验这一步）。验证码/二次验证无法在客户端处理，页面会提示先在网页端登录一次。

## 二、客户端实现

- `core/crypto/XxtCrypto.kt`：登录字段加密，基准向量由 openssl 生成并写进 `XxtCryptoTest`。
- `data/remote/xxt/XxtClient.kt`：OkHttp 客户端；会话 Cookie 走独立 `XxtCookieJar`，可整段导出加密落盘。
  请求串行且每次至少间隔 600 ms；课程签名在同一会话内缓存 6 小时（省掉一半请求）。
- `data/repo/XxtParse.kt`：纯解析（课程 / 作业列表 / 详情 / 隐藏域 / 登录页与反爬页识别），
  不依赖 Android，单元测试直接覆盖。
- `data/repo/XxtRepository.kt`：同步编排 + 缓存（15 分钟）+ 提醒去重日志（保留 14 天）。
  详情页只为“未完成且没读到截止时间、或距上次尝试超过 6 小时”的作业补，每次最多 8 条；单课失败不打断整轮。
- `work/XxtHomeworkWorker.kt`：每 2 小时同步一次；免打扰时段内只发 1 小时档的加急提醒。

## 三、提醒规则

- 分档：剩余 ≤ 24h → `h24`；≤ 6h → `h6`；≤ 1h → `h1`。进入更近的一档才补一次，
  同一作业同一档只响一次（`key|tag` 记在 `xxt.reminder.journal`）。
- 新作业：本轮首次出现且未发过 `new` 才提醒；**首次同步只记日志不补发**，避免一次弹十几条。
- 已交、已过期、截止未知一律不提醒。
- 免打扰时段（沿用提醒中心的 23:00–07:00 设置）内只发 `h1`。
- 后台提醒受 WorkManager 调度限制，可能延迟；页面上始终有倒计时可核对。

## 四、界面

- 入口：教务 → 教务事务 → **学习通作业**；未绑定时整页是一张说明卡 + 绑定按钮。
- 顶部概览卡：未交数量 + 最紧急一条的课程/标题/倒计时；≤6 小时切到 error 语义色。
- 列表：按「24 小时内 / 3 天内 / 更晚 / 截止未知 / 已过期 / 已交」分组，
  行首 44dp 倒计时徽标（形状固定、只用语义色），行尾同色系倒计时文字。
- 登录页：只读说明 + 账号/密码 + 记住密码；密码与 Cookie 都进 `CredentialStore`（AndroidKeyStore AES-GCM）。
- 今天页：三天内要交的作业折叠成一条待办，点击进入作业页。

## 五、验收记录

环境：MUMU 模拟器（Android 12，1080×1920 @440dpi，竖屏）。

- **单元测试 130 项全部通过**（v0.2.51 起的累计；v0.2.52 增补通知解析 2 项、通知提醒 1 项；v0.2.53 只抽通知构建，不改规则），其中本次新增：登录加密基准 2 组、时间补年份 / 分档 / 免打扰 / 状态词、页面解析（课程、作业列表、隐藏域、详情时间、登录页与反爬页）、提醒规则（分档、去重、新作业、已交与过期跳过）、列表分组 2 项。
- **设备端 Compose 测试 5 项通过**（`HomeworkContentTest` 4 项：概览数量、分组标题、倒计时徽标、只看未交、全部包含已交、截止未知、未读通知区块；`XxtNotificationTest` 1 项：提醒真的进入通知栏），渲染结果见 `docs/img/xxt-08-ready.png`、`docs/img/xxt-09-notices.png`、通知栏截图 `docs/img/xxt-11-notification.png`。
- **登录链路真机实测**：模拟器经宿主代理访问 `passport2.chaoxing.com/fanyalogin`，服务端真实返回 `{"msg2":"用户名或密码错误","status":false}`，App 显示「学习通账号或密码不正确（用户名或密码错误）」。这条链路覆盖 AES 加密、表单字段、HTTPS、JSON 解析与错误映射（`docs/img/xxt-07-login-server.png`）。
- **未登录探测**：匿名访问 `i.chaoxing.com/base` 会被 302 到 `passport2.chaoxing.com/mlogin`。因此失效判定改为“整个 passport2 域 + 重定向链”，只认 `/login` 会把过期会话误判成“已登录、只是没有作业”。
- **反爬拦截**：识别 `antispiderShowVerify` 与正文开头的“操作异常，请输入图片中的验证码”，命中即停止本轮详情请求，避免把拦截页当成“截止时间不存在”。
- **后台任务**：安装签名 Release 包后，`dumpsys jobscheduler` 中可见 WorkManager 下一轮延迟 1h59m59s 的作业，对应本功能的 2 小时周期同步。
- **全量设备回归**：整套 androidTest 74 项，同一模拟器上 69 项通过。5 项失败集中在 `PrismExpressiveComponentsTest`（大字号 + 深色）、`PrismIconButtonSemanticsTest`、`RemindersSelectionTest`、`TargetResolutionLayoutTest`（320dp / 1.4x），均为既有用例在本模拟器环境下的失败：把 `RemindersScreen` 回退到本次改动前重跑，`RemindersSelectionTest` 仍以同样方式失败，可确认与本次改动无关。本次新增的 4 项用例单独运行与整套运行均通过（整套中有过一次 flaky，二次通过）。
- **已知边界**：手上没有可用的真实学习通账号，登录后的课程/作业真实数据未在设备上端到端跑通。该段由 xxt-assistant 的实测协议结论加页面 fixture 回归覆盖；拿到账号后应补一次真实同步核对。

截图：

| 文件 | 内容 |
| --- | --- |
| `docs/img/xxt-02-bind.png` | 未绑定时的整页说明与绑定入口 |
| `docs/img/xxt-03-login.png` | 学习通登录页（只读说明 + 记住密码） |
| `docs/img/xxt-06-login-result.png` | 无网络时的可解释错误（含失败详情） |
| `docs/img/xxt-07-login-server.png` | 真机服务端返回的账号/密码错误 |
| `docs/img/xxt-08-ready.png` | 作业列表：24 小时 / 3 天分组、5h 徽标、行尾截止时间 |
| `docs/img/xxt-09-notices.png` | 作业列表下方的「学习通通知 · 1 条未读」区块 |
| `docs/img/xxt-11-notification.png` | 系统通知栏里的作业提醒（由 `XxtNotificationTest` 真实发出） |

## 六、v0.2.52 增补：学习通通知中心

把参考项目里的“未读通知”接口一并接进来，作为作业页的补充信息，不改变只读边界。

| 用途 | 请求 | 关键点 |
| --- | --- | --- |
| 通知列表 | `POST https://notice.chaoxing.com/pc/notice/getNoticeList` | 表单 `type/notice_type/lastValue/sort` 等空字段；游标 `lastGetId`，`lastPage` 结束；需要 `Referer: notice.chaoxing.com/pc/notice/myNotice` |
| 字段 | `notices.list[]` | `id`/`noticeId`、`title`、`content`（HTML 压成纯文本）、`createrName`、`completeTime`（毫秒时间戳）、`isread`（布尔或 `"1"`） |

- 页面：作业分组下方新增「学习通通知 · N 条未读」，最多展示 5 条，未读行尾标注「未读」。
- 提醒：只提醒**未读且没发过**的通知；首次同步只记日志不补发，避免一次弹一串；提醒中心可单独关闭「学习通通知」。
- 容错：通知接口失败只记一条错误，不影响作业同步；会话失效仍按整体失效处理。

## 七、v0.2.53：通知构建抽出并可验

- `work/XxtNotifications.kt`：通道、深链（`deepLink=homework`）、标题/正文/优先级集中一处；Worker 与设备端测试共用同一段代码，避免“测试测的是另一份实现”。
- `XxtNotificationTest`（androidTest）：调用 `XxtNotifications.notify()` 发一条「6 小时内截止」提醒，再从 `NotificationManager.activeNotifications` 查回标题；`dumpsys notification` 与通知栏截图双证（`docs/img/xxt-11-notification.png`）。
- 版本 0.2.53（versionCode 58），功能行为与 v0.2.52 一致，仅提升可验证性。

## 八、v0.2.54：接口地址可注入 + 整链路回归

- `XxtEndpoints` 把五个接口地址集中成默认值参数，线上行为不变，测试可以指向本机 mock。
- `XxtFlowTest`（JVM）：本机 HTTP 服务回放登录、课程、作业列表、详情、通知的真实响应形状，真实 OkHttp 依次走完；断言会话 cookie、课程、作业状态、截止时间与未读通知。
- 版本 0.2.54（versionCode 59），行为与 v0.2.53 一致。

## 九、v0.2.55：真实账号端到端 + 明文跳转修复

用真实账号在 MUMU（Android 12）走完整链路，暴露并修掉两个真实缺陷：

1. **明文跳转被系统拦掉**：登录成功后 `GET https://i.chaoxing.com/base` 会
   302 到 `http://i.chaoxing.com/base?ws=1` 再跳回 https；Android 默认
   cleartext 策略把这一跳判死，`checkLogin()` 报“网络连接失败”，同步在第一步就中断
   （界面表现就是“N 门课程未拉到”）。`network_security_config.xml` 现对
   `chaoxing.com` 放行明文，其它域仍禁止。
2. **失败原因不可见**：全部课程失败时只弹“同步完成，N 门课程未拉到”。
   现在把第一门课的具体原因带进提示（如“学习通会话已失效”），且全部失败时进入失败态，
   不再用“课程里还没有作业”掩盖。

实测结果（设备端 instrument + 应用缓存）：

| 项 | 值 |
| --- | --- |
| 课程 | 7 门（去重后；其中 1 门确实没有作业） |
| 作业条目 | 54 条，未交 9 条 |
| 截止时间 | 3 条有（12-19 / 12-20），51 条详情页无“作答时间”，按“待公布”展示 |
| 未读通知 | 7 条 |

证据：`docs/img/xxt-12-live-ready.png`（debug 包）、`xxt-14-live-release.png`（release 包）、
`xxt-13-live-login.png`；单元测试 131 项、设备端 5 项通过。
版本 0.2.55（versionCode 60）。
