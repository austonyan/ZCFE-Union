# 学习通作业提醒 · 交付证据

一句话：把 [xxt-assistant](https://github.com/led629263224-cmyk/xxt-assistant) 的学习通**登录 / 作业 / 通知**接口用 Kotlin 重新实现，接进本项目的 Prism 设计系统，做成「作业提醒」；已在 MUMU 模拟器验收，并发布 v0.2.51 → v0.2.53 三个 Release。

## 1. 需求 → 产物对照

| 需求 | 产物 |
| --- | --- |
| 探索并复用登录接口 | `core/crypto/XxtCrypto.kt`（AES-128-CBC，key=iv=`u2oh6Vu^HWe4_AES`）、`data/remote/xxt/XxtClient.kt#login` |
| 复用作业接口 | `XxtClient#fetchCourses / fetchHomeworkList / fetchHomeworkDetail`（课程页 `workEnc` 签名 + `t`/`stuenc` + 详情页作答时间） |
| 复用通知接口 | `XxtClient#fetchNotices`（`notice.chaoxing.com/pc/notice/getNoticeList`，游标 `lastGetId`/`lastPage`） |
| 作业提醒功能 | `domain/XxtHomework.kt`（分组 / 24·6·1 分档 / 免打扰 / 新作业·新通知规则）、`work/XxtHomeworkWorker.kt`（2 小时同步）、`ui/screens/homework/*`（Prism 组件） |
| 利用 skill + 设计系统 | `frontend-design` skill 参与设计取舍；界面只用 `ui/prism/*` 组件与 M3 expressive 令牌 |
| 全面验收 | 本页第 2–5 节；`docs/verification-v0.2.52.md`（命令 + 输出） |
| 构建新版本 | `app/build.gradle.kts` versionCode 58 / 0.2.53；`郑财校园-release-v0.2.53.apk` |
| 推送远端 | commit `12d0300`，GitHub master 与本地一致 |
| 发布 Release | <https://github.com/austonyan/ZCFE-Union/releases/tag/v0.2.53>（v0.2.51 / v0.2.52 保留） |

## 2. 自动化测试

- 单元测试 **130 项 / 0 失败**：登录加密 openssl 基准、时间补年份、分档与免打扰、页面解析（课程/作业/详情/通知/登录页/反爬页）、提醒去重、列表分组。
- 设备端用例 **5 项通过**（MUMU / Android 12）：
  - `HomeworkContentTest` × 4：概览数量、分组标题、倒计时徽标、只看未交、全部含已交、截止未知、未读通知区块
  - `XxtNotificationTest` × 1：提醒真的进入通知栏（`dumpsys notification` + 截图 `docs/img/xxt-11-notification.png`）

## 3. 真机链路

- 匿名访问 `i.chaoxing.com/base` 会被 302 到 `passport2.chaoxing.com/mlogin` —— 失效判定按此实现。
- 经宿主代理填写测试账号提交登录，服务端返回 `{"msg2":"用户名或密码错误","status":false}`，App 显示「学习通账号或密码不正确（用户名或密码错误）」（截图 `docs/img/xxt-07-login-server.png`）。
- 安装签名 Release 包后，`dumpsys jobscheduler` 可见 `+1h59m59s`（作业同步）与 `+17h46m59s`（每日电费）。

## 4. 产物校验

```
versionCode=58  versionName=0.2.53
apksigner verify --verbose → APK Signature Scheme v2: true
sha256 郑财校园-release-v0.2.53.apk = C536FBD05EC8582BA5B09D7C458CEFD1041933097F186872BFDCBB963B6A7A58
gh release view v0.2.53 → ZCFE-Union-v0.2.53.apk (18,040,510 B)，下载 HTTP 200
```

## 5. 边界（如实）

没有可用的真实学习通账号，登录后的真实课程 / 作业 / 通知数据未端到端跑通；该段由参考项目的实测协议结论 + 页面 fixture 回归覆盖。拿到账号后应补一次真实同步核对。
