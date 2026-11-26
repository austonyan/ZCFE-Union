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

- **单元测试 127 项全部通过**，其中本次新增：登录加密基准 2 组、时间补年份 / 分档 / 免打扰 / 状态词、页面解析（课程、作业列表、隐藏域、详情时间、登录页与反爬页）、提醒规则（分档、去重、新作业、已交与过期跳过）、列表分组 2 项。
- **设备端 Compose 测试 3 项通过**（`HomeworkContentTest`：概览数量、分组标题、倒计时徽标、只看未交、全部包含已交、截止未知），渲染结果见 `docs/img/xxt-08-ready.png`。
- **登录链路真机实测**：模拟器经宿主代理访问 `passport2.chaoxing.com/fanyalogin`，服务端真实返回 `{"msg2":"用户名或密码错误","status":false}`，App 显示「学习通账号或密码不正确（用户名或密码错误）」。这条链路覆盖 AES 加密、表单字段、HTTPS、JSON 解析与错误映射（`docs/img/xxt-07-login-server.png`）。
- **未登录探测**：匿名访问 `i.chaoxing.com/base` 会被 302 到 `passport2.chaoxing.com/mlogin`。因此失效判定改为“整个 passport2 域 + 重定向链”，只认 `/login` 会把过期会话误判成“已登录、只是没有作业”。
- **反爬拦截**：识别 `antispiderShowVerify` 与正文开头的“操作异常，请输入图片中的验证码”，命中即停止本轮详情请求，避免把拦截页当成“截止时间不存在”。
- **后台任务**：安装签名 Release 包后，`dumpsys jobscheduler` 中可见 WorkManager 下一轮延迟 1h59m59s 的作业，对应本功能的 2 小时周期同步。
- **全量设备回归**：整套 androidTest 74 项，同一模拟器上 69 项通过。5 项失败集中在 `PrismExpressiveComponentsTest`（大字号 + 深色）、`PrismIconButtonSemanticsTest`、`RemindersSelectionTest`、`TargetResolutionLayoutTest`（320dp / 1.4x），均为既有用例在本模拟器环境下的失败：把 `RemindersScreen` 回退到本次改动前重跑，`RemindersSelectionTest` 仍以同样方式失败，可确认与本次改动无关。本次新增的 3 项用例单独运行与整套运行均通过（整套中有过一次 flaky，二次通过）。
- **已知边界**：手上没有可用的真实学习通账号，登录后的课程/作业真实数据未在设备上端到端跑通。该段由 xxt-assistant 的实测协议结论加页面 fixture 回归覆盖；拿到账号后应补一次真实同步核对。

截图：

| 文件 | 内容 |
| --- | --- |
| `docs/img/xxt-02-bind.png` | 未绑定时的整页说明与绑定入口 |
| `docs/img/xxt-03-login.png` | 学习通登录页（只读说明 + 记住密码） |
| `docs/img/xxt-06-login-result.png` | 无网络时的可解释错误（含失败详情） |
| `docs/img/xxt-07-login-server.png` | 真机服务端返回的账号/密码错误 |
| `docs/img/xxt-08-ready.png` | 作业列表：24 小时 / 3 天分组、5h 徽标、行尾截止时间 |
