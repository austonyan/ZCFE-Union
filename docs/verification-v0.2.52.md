# v0.2.52 验收证据

本页只放可复现的命令与实测输出，供逐条核对。功能与协议说明见 `docs/study-homework-v0.2.51.md`。

环境：Windows 11 + Gradle 9.7.1（AGP 9.3.2 / Kotlin 2.3.0）+ MUMU 模拟器（Android 12，设备号 `127.0.0.1:16384`）。

## 1. 参考仓库分析（github.com/led629263224-cmyk/xxt-assistant）

| 参考文件 | 读到的结论 | 本项目落点 |
| --- | --- | --- |
| `xxt/client.py` | 登录 `passport2.chaoxing.com/fanyalogin`；课程中间页 `stucoursemiddle` 的 `workEnc/openc/enc`；作业列表 `mooc2/work/list` 需 `t` + `stuenc`；通知 `notice.chaoxing.com/pc/notice/getNoticeList` | `data/remote/xxt/XxtClient.kt` |
| `xxt/parse.py` | AES-128-CBC key=iv=`u2oh6Vu^HWe4_AES`；课程/作业/详情/通知解析；`MM-DD HH:MM` 取最近年份；完成态词表 | `core/crypto/XxtCrypto.kt`、`data/repo/XxtParse.kt`、`domain/XxtHomework.kt` |
| `xxt/notify.py` | 24/6/1 分档、同档只响一次、journal 去重（保留 14 天）、免打扰只发 1 小时档 | `domain/XxtHomework.kt`、`data/repo/XxtRepository.kt`、`work/XxtHomeworkWorker.kt` |
| `xxt/sync.py` | 串行限速、详情按需补全（每次 8 条、6 小时刷新）、单课失败不打断、首轮不补发新作业 | `data/repo/XxtRepository.kt` |
| `xxt/store.py` | 增量 diff 识别“新增” | JSON 快照 + `key\|tag` 去重日志（`xxt.reminder.journal`） |

只读边界保持一致：不提交作业、不答题、不刷课，所有请求都是查询语义。

## 2. 单元测试

```
$ ./gradlew.bat :app:testDebugUnitTest
BUILD SUCCESSFUL
app/build/test-results/testDebugUnitTest/*.xml → tests=131 failures=0 errors=0
```

本次新增用例：`XxtCryptoTest`（openssl 基准向量）、`XxtTimeTest`（跨年补年份/分档/免打扰/状态词）、`XxtParsingTest`（课程、作业列表、隐藏域、详情、登录页、反爬页、通知 JSON）、`XxtReminderTest`（分档、去重、新作业、通知、已交/过期跳过）、`HomeworkGroupingTest`（列表分组）。

## 3. 设备端 Compose 测试（MUMU / Android 12）

```
$ adb install -r app-debug.apk && adb install -r app-debug-androidTest.apk
$ adb shell am instrument -w -e class com.union.campus.ui.screens.homework.HomeworkContentTest \
      com.union.campus.test/androidx.test.runner.AndroidJUnitRunner
...
OK (4 tests)
```

覆盖：概览数量、分组标题、倒计时徽标、只看未交、全部含已交、截止未知、未读通知区块。
渲染截图：`docs/img/xxt-08-ready.png`、`docs/img/xxt-09-notices.png`。

v0.2.53 追加通知到达用例（共 5 项设备端用例）：

```
$ adb shell am instrument -w -e class com.union.campus.ui.screens.homework.XxtNotificationTest       com.union.campus.test/androidx.test.runner.AndroidJUnitRunner
OK (1 test)
```

全量 `androidTest`（74 项）在同一模拟器上 69 项通过；5 项失败均为既有用例（Prism 组件大字/深色、图标语义、提醒选择、320dp 布局）。把 `RemindersScreen` 回退到改动前重跑，`RemindersSelectionTest` 仍以同样方式失败，可确认与本次改动无关。

## 4. 真机链路

```
$ adb shell settings put global http_proxy 10.0.2.2:7890   # 模拟器经宿主代理出网
填写 19900000000 / 错误密码 → 点击「登录学习通」
界面：学习通账号或密码不正确（用户名或密码错误）
# 服务端原始响应：{"msg2":"用户名或密码错误","status":false}
```

这条链路覆盖 AES 加密表单、HTTPS、JSON 解析与错误映射；截图 `docs/img/xxt-07-login-server.png`。
从教务进作业页、绑定页、登录页均为 Release 包实机截图（`xxt-02` / `xxt-03` / `xxt-10`）。

## 5. 后台任务

```
$ adb shell dumpsys jobscheduler | grep -A5 com.union.campus/androidx.work
JOB #u0a57/2 ... Minimum latency: +1h59m59s931ms   # 学习通作业：2 小时周期
JOB #u0a57/0 ... Minimum latency: +17h46m59s998ms  # 低电费：每日 20:00
```

## 6. 构建产物与发布

```
$ apksigner verify --verbose 郑财校园-release-v0.2.52.apk
Verified using v2 scheme (APK Signature Scheme v2): true
$ sha256sum 郑财校园-release-v0.2.52.apk
7E011B4906A5B6A44746150FF461278934CFA358EA05EBF9CDA3C666DAB003AF
$ adb shell dumpsys package com.union.campus | grep version
versionCode=57 versionName=0.2.52
$ gh release view v0.2.52 --json assets
[{"name":"ZCFE-Union-v0.2.52.apk","size":18040510}]
$ gh api repos/austonyan/ZCFE-Union/commits/master --jq .sha
b9e17440a7189eed6bbecb908eeb57619e77f89b   # 与本地 HEAD 一致
```

- Release：<https://github.com/austonyan/ZCFE-Union/releases/tag/v0.2.52>（上一版 v0.2.51 保留）
- 本地产物：`郑财校园-release-v0.2.52.apk`

## 7. 通知真的到达通知栏（v0.2.53）

Worker 与测试共用 `work/XxtNotifications.notify()`；测试发一条「6 小时内截止」提醒后，从系统侧查回：

```
$ adb shell dumpsys notification --noredact | grep android.title
android.title=String (6 小时内截止：单元测试作业)
$ adb shell cmd statusbar expand-notifications
$ adb exec-out screencap -p > docs/img/xxt-11-notification.png
```

截图里能看到「郑财校园 · 现在 / 6 小时内截止：单元测试作业 / 高等数学 还有 5 小时（11-27 23:59）」。

## 9. 端到端链路（v0.2.54）

`XxtFlowTest` 在本机起一个极简 HTTP 服务，按学习通真实响应形状回包，让**真实 OkHttp 客户端**走完整链路：

```
login → checkLogin → fetchCourses → fetchHomeworkList → fetchHomeworkDetail → fetchNotices
```

断言覆盖：会话 cookie、课程名/教师、作业标题/状态/详情直链、题量与作答时间、通知未读状态。这条用例验证的是“网络层 → 解析层”之间的接线，不依赖账号。

## 8. 未覆盖

没有可用的真实学习通账号，登录后的课程 / 作业 / 通知真实数据未在设备上端到端跑通。
该段由参考项目的实测协议结论 + 页面 fixture 回归覆盖；拿到账号后应补一次真实同步核对。
