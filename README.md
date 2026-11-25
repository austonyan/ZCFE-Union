# 郑财校园（Union 校园）

把「教务 + 洗浴 + 电费」装进同一个 App 的郑州财经学院校园生活入口。

## 交付物

构建产物（`*.apk`）与签名密钥不入库；以下版本说明仅作发布记录。

- **`郑财校园-release-v0.2.50.apk`** — 已签名 release APK（versionCode 55，v2 签名，CN=Zhengcai Union，16 KB 对齐）
- 上一版 `郑财校园-release-v0.2.49.apk`（versionCode 54）保留，便于回滚
- v0.2.50 变更：按 `material-3` skill 官方规范复核并修复 17 个文件的真实偏差（原生组件形状单调映射、对话框 Headline Small 24/32 与对话框语义、分割线/进度轨道/卡片描边改用 colorScheme、snackbar 统一 inverse 配色、分段控件单选语义、输入错误与骨架加载语义、重复 insets 与 IME、页标题 heading）；保留 Prism 品牌与既有产品约定。范围与未做项见 `docs/md3-official-spec-2026-11-25.md`。
- v0.2.50 校验：104 项单元测试通过；Debug/Release 构建通过；lint 0 error、11 条既有 warning/hint；v2 签名、版本与 16 KB 对齐验证通过。当前未连接设备，未做设备端视觉/TalkBack 走查。
- **`郑财校园-release-v0.2.47.apk`** — 已签名 release APK（versionCode 52，v2 签名，CN=Zhengcai Union，有效期 10000 天）
- v0.2.47 变更：按 `docs/前端文本审计.csv` 应用 285 条已填写文案，共替换 45 个生产源码文件中的 317 处字符串；空白项不变，保留变量、必要换行及业务逻辑。同步更新文案相关测试断言。
- v0.2.47 校验：91 项单元测试通过；Debug/Release 构建及 AndroidTest 编译通过；lint 0 错误、10 警告；APK 版本、v2 签名和 16 KB 对齐验证通过。当前未连接设备，未运行设备端界面测试。
- v0.2.46 变更：MD3 Expressive 活泼化（语义形状、连接式列表与分段控件、调和选中态、今天课程主卡、教务入口组、生活状态卡、账号与引导层次）；导航与通用状态转场响应减少动效；新增五 Tab 与公共组件回归测试。范围与校验见 `docs/md3-expressive-v0.2.46.md`。
- v0.2.46 布局补修：按 1200×2670 竖屏及三档密度、常规/大字号复测，修复预约按钮挤压、长弹窗动作、分段选项、欢迎页截字与周切换宽度；36 项设备测试通过，详见 `docs/layout-regression-1200x2670.md`。
- v0.2.45 变更：MD3 全量审查修复（8 套预设按 M3 tone 重算、文字层级去掉二次 alpha、品牌暗色页/卡层级 1.13→1.20、`pageWidth` 大屏限宽铺到 6 个根页、Chip 选中语义 + IconButton 禁用语义、动效接入 reduce-motion、三个超大屏文件拆分、androidTest 从 0 到 4 类、lint 25→12 warning），详见 `docs/m3-audit-v0.2.44.md`
- v0.2.26 变更：课表页头改为「学期第一行、日期第二行」
- v0.2.25 变更：课表字号按 dp 锁定（不受系统字号放大影响）、「现在」徽标收进时间栏、页头副标题允许两行（学期名不再截断）、浅色页面压到 surfaceContainerHigh（页/卡对比 1.17→1.22:1）
- v0.2.23 变更：字号锁定 + 时间栏宽度固定 38dp
- v0.2.21 变更：课程卡内容水平+垂直居中、课名最多 4 行（超长走省略号并保证教室/教师可见）、地点方括号内的中文说明自动过滤
- v0.2.20 变更：课名字号回到 12sp（对齐参考图）、卡片内容垂直居中、时间栏恢复上下课两行时间（仍是单套作息，不再并列 S/J）
- v0.2.19 变更：时间栏压到 34dp，按本周该节实际楼栋自动选作息；课程卡片加宽；课表支持左右滑动切周
- v0.2.18 变更：课表单元格按块高等比排版（课名/地点/教师完整显示）、去掉「本周 N 节课」与「显示周末」开关、周末有课时自动出列
- v0.2.17 变更：UI 质感与动效第一批（表面层级 / 1px 边缘 / 文字层级 / tab fade-through / 按压形变 / Dock tonal 选中态），详见 `docs/ui-polish-direction.md`
- 签名密钥：`app/union-release.keystore`（仅本地保留，已被 git 忽略；store/key 密码从 `local.properties` 的 `union.storePassword` / `union.keyPassword` 读取，或使用 `UNION_STORE_PASSWORD` / `UNION_KEY_PASSWORD` 环境变量，alias `union`）
- H5 加密常量：`local.properties` 的 `jw.h5EncryptKey`，或 `JW_H5_ENCRYPT_KEY` 环境变量（仓库不保存）

## 构建

```bash
./gradlew.bat assembleDebug          # 调试包
./gradlew.bat assembleRelease        # 签名 release 包
./gradlew.bat testDebugUnitTest      # 单元测试（含加密算法基准对照 + 网络冒烟）
```

SDK 位于 `D:\Program Files (x86)\Android SDK`（见 local.properties）。

## 技术栈

- AGP 9.3.2（内置 Kotlin）+ Kotlin Compose 插件 2.3.0 + Gradle 9.7.1
- Compose BOM 2026.09.00 + material3 **1.5.0-rc01**（Expressive API：MotionScheme.expressive 等）
- compileSdk 37 / minSdk 26 / targetSdk 37，竖屏
- OkHttp 5.5.1 / kotlinx-serialization / DataStore / WorkManager / Biometric / graphics-shapes 1.1.0

## 架构

```
ui/            34 屏 Compose UI + prism 设计层（FlClash 风格复刻）
di/            ServiceLocator 轻量 DI
data/repo/     教务 / 洗浴 / 电费 / 待办 仓库层（缓存策略 + 会话状态）
data/remote/   三套协议客户端：
  jw/        CAS 4 步登录（AES-CBC 密码）+ 喜鹊儿 H5（of_encrypt/getMd5_2）
  shower/    大白U帮原生 DES/CBC + SHA-1 信封（5yoOxt9w / q9KswPoz62jQk12W）
  electric/  完美校园 SWAEServlet 命令通道（7268 / JBSWaterElecService）+ loginCheck AES-128-ECB
data/store/    DataStore 设置 + JSON 实体缓存 + AndroidKeyStore 加密凭证
work/          低电费每日 20:00 检查 Worker
```

## 设计语言（prism，复刻自 FlClash prism_ui）

- 连续圆角（RoundedSuperellipse 等价实现，androidx.graphics-shapes）：普通卡 20 / 主卡 28 / 连接分组外 24、内 6 / 对话框 34 / sheet 38；按钮带轻按压形变
- Surface 四级色阶（亮：灰页白卡；暗：近黑页 tone15 卡）
- 无涟漪 iOS 按压反馈（ink α0.10 / tint 75%+白 20%）
- 双档 SnapSheet（9/16 与 0.9，临界阻尼弹簧）、连接式列表 2dp 间隙、统一 Prism 字阶；Expressive 状态变化遵守系统减少动效设置
- 详见 `docs/flclash-design-spec.md`（逐条文件:行号证据）

## 行为约定（power.md）

- 三套账号独立绑定，全部可跳过；凭证 Keystore 加密，不出设备
- 读接口缓存优先（带更新时间），写操作（开关设备/绑定房间/评教/选课/确认教材）全部二次确认、不自动重试
- 支付只做跳转/链接指引，不托管支付密码
- 电费学号固定本人，不提供任意学号查询
- 无假数据：上游不可达/未绑定时显示可解释的空态与引导

## 已验证

- 教务 H5 加密算法与 Python 参考实现逐字节对照通过
- 教务接口按真实账号验证：课表（20260/第 10 周）、学籍档案（MyXjda）、成绩、考试、调课通知、空闲教室（教学楼 dm + getJsxx）
- 兼容 `MyXjda` 返回的单引号 JavaScript 对象（`{'resultSet':[...]}`）与未加引号键
- 洗浴支持学校 / 校区 / 楼栋 / 区域四级选择，并记住上次位置；无空闲设备时可加入排队、查看排队进度与取消排队
- 电费首页兼容 `bussnesstype` 拼写与 `monthuse` 字段，余额/近 7 天/本月用量可正常显示
- 待办会过滤早于今天的考试与截止日期；课表改为按周网格（节次时间、课程块、午休/晚休、周末切换）
- 洗浴非服务时间/未开放等业务限制会显示“当前不在洗浴服务时间，请按浴室开放时间使用”，不再统一“数据源返回异常”
- 洗浴开启按上游 `need_reserve=1` 先 `DeviceApi/reserve` 预约再 `DeviceApi/turnOn`（直接 turnOn 返回 code 130「您尚未预约任何设备」），开启失败会释放预约；关闭时 code 100170「您没有正在使用的设备」按已结束幂等处理
- 洗浴预约倒计时读取 `DeviceAreaApi/getStateByDeviceType` 的 `remainTime`（`userState=reserved` 时服务端实时剩余秒数），首页与设备详情按服务端值显示并每 15 秒回读；不再写死 5/8 分钟
- 洗浴区域加载以 `selectDeviceAreasWithDeviceState` 为主；`getBuildings` / `getDeviceArea` / 设备接口失败时自动降级到登录默认校区和全部区域，不再整页“数据源异常”
- 洗浴/电费上游从本机可达，协议冒烟测试通过（未登录探测返回预期错误码）
- MUMU 模拟器（Android 15）走查 16+ 屏，无崩溃；修复：竖屏锁定、学期代码解析、卡片满宽、冷启动跳过引导、未绑定态引导入口
