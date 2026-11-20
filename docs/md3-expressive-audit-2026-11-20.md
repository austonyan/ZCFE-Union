# Material 3 Expressive 前端优化与审计

- 日期：2026-11-20
- 目标：当前 `openchamber-develop` 工作区，基于 `8c17506`。
- 技术栈：Jetpack Compose、Material3 `1.5.0-rc01`、Prism 设计层。

## 范围与实施约束

- 使用主工作树加三个独立工作树并行，分别负责公共组件、教务/今天、账号/设置；主工作树负责自适应导航、集成和验收。最多同时四个工作树，未超出五个上限。
- 保留五个主目的地、滚动内容内的 `PrismPageHeader`、详情页返回路径、Prism 品牌、已有主题及课程配色。不新增折叠顶栏或装饰性循环动画。
- 课表仍仅高亮本周今天，不恢复重复作息说明、空教室入口或调课提醒。
- 不修改生产接口、签名、版本号及支付/设备控制的提交顺序；不执行真实选课、评教、短信发送、支付或设备启停。
- 本轮为前端改进，不是后端写接口联调或正式无障碍认证。

## Audit 结果：92 / 100（达到约 90 分目标）

按技能的十类标准进行独立源码复核，未继承历史报告分数，也不把测试数量当作评分。该分数是项目内部审计，不是 Google 官方认证。

| 类别 | 分数 | 状态 | 主要依据 |
| --- | ---: | --- | --- |
| Color tokens | 9/10 | pass | `ui/theme/Color.kt`、`PresetPalettes.kt`；语义配对及 18 套静态 scheme 对比度检查 |
| Typography | 9/10 | pass | `ui/theme/Type.kt` 与 Prism 语义字阶；Dock 升为 M3 `labelMedium` |
| Shape | 9/10 | pass | `ui/prism/PrismTokens.kt` 的形状角色、`PrismShape.kt`；共享卡片、按钮与控件形变 |
| Elevation | 9/10 | pass | 调和表面分层；Dock 移除双重阴影与描边叠加 |
| Components | 10/10 | pass | 原生 M3 按钮、只读暴露菜单及 Expressive 加载器；修复确认/取消顺序与假成功 |
| Layout | 8/10 | pass | 600dp 导航切换、840dp 正文限宽、欢迎页操作前置；未实现 hinge-aware 多栏 |
| Navigation | 10/10 | pass | 五目的地共享返回栈，绑定返回原调用页，今日课程直达详情；Dock 可见键盘焦点与原生 rail |
| Motion | 9/10 | pass | `MotionScheme.expressive()`；sheet、加载器、按压缩放及状态层支持减少动效 |
| Accessibility | 9/10 | pass | 48dp 按钮、单选/已选/进度语义、聚焦环；原生模态窗口、实际 pane 标题与关闭/焦点返回路径。字号钳制及人工 TalkBack 验收边界保留 |
| Theming | 10/10 | pass | 统一 `MaterialTheme`、浅/暗/动态主题与静态预设 |
| **合计** | **92/100** | **达到约 90 分目标** | 未发现本轮仍未处理的 0–6 分项 |

### 后续优先项（本轮不扩张）

1. 真实 TalkBack、Switch Access 的人工全路径走查及更多 OEM/输入设备覆盖；本轮键盘与语义回归不等于这些人工验收。
2. 在取消全局字体钳制前完成大字号重排，而非直接声称已支持 2 倍字体。
3. 折叠铰链避让及真正的列表详情双栏；当前宽度适配不等价于这些能力。
4. PressNode 在新的交互事件上读取减少动效设置；对正在播放的短状态层动画没有额外的即时中断观察器。

## 已完成的改动

### 继续核验：键盘与模态闭环

- 紧凑 Dock 恢复焦点状态层，并为键盘聚焦目的地绘制 primary 3dp 聚焦环；不靠选中胶囊充当焦点指示。18 套配色新增 primary 对普通/选中导航背景的 3:1 对比度断言。
- `PrismSnapSheet` 改用原生 `Dialog` 承担模态窗口和焦点隔离，保留原 detent、进出场动画及自绘遮罩；平台 dim 设为 0，避免双重遮罩。
- 面板提供 pane 标题和 dismiss 语义，加入底部导航栏安全区。遮罩只通过触摸手势关闭，不创建无名称的 Tab 停靠点。
- 新模态窗口独立同步浅/暗主题的系统栏图标，并关闭平台窗口淡入；进出场仅由原有 Prism 动效控制。为实际 Dialog window 的 dim、动画样式和系统栏明暗补充属性回归，避免双重淡入造成背景透出。
- 新增 `PrismModalAccessibilityTest` 四项，验证可见焦点像素、Enter 切换、自然 Tab/Shift+Tab 循环、原生 Dialog、关闭语义、实际重新激活的焦点返回、背景键盘事件隔离及浅/暗窗口属性；真实页面截图流程补充浅/暗色免打扰面板及系统返回键关闭。
- 独立源码复核从前一轮 89 分推进至 92 分：导航 9→10，无障碍 7→9；没有靠增加测试数量或改写目标分数得分。旧截图/旧版测试未直接作为新增模态路径的通过证据。

### 原生 Material3 / Expressive 组件

- `PrismButton`、`PrismTextButton`、`PrismIconButton` 采用原生 M3 按钮，保留既有调用接口、Prism 形变、触感及配色。加载时保留标签和尺寸、阻止重复提交；文字按钮和普通按钮明确保证至少 48dp 的布局触控尺寸。
- `PrismLoadingIndicator` 使用原生 Expressive `LoadingIndicator`；关闭动画时显示静态不确定进度环。
- `PrismDropdownField` 使用 `ExposedDropdownMenuBox` 和只读锚点，去掉透明覆盖点击层；保留唯一焦点、已选项及禁用语义。
- `PrismOptionSheet` 增加单选分组、选中状态及标题语义，删除重复的“选择即保存”。
- `PrismSnapSheet` 的入场、退出和吸附尊重系统减少动效；关闭动效时直接 `snapTo`，不等待动画帧。

### 导航与操作逻辑

- 600dp 以下保留五项 Dock；600dp 起使用原生 `NavigationRail`，共享同一个导航控制器和返回栈。导航轨支持滚动，不把底部导航简单拉宽。
- Dock 使用 `surfaceContainer` 与 `secondaryContainer/onSecondaryContainer`；移除双层阴影及重复边框，采用 80dp 高度、M3 `labelMedium` 标签。洗浴进行中通过静态圆点和朗读状态表达，不依赖呼吸动画。
- 今天课程卡直接进入对应课程详情，不再丢弃课程信息后跳到课表；区分“课表未同步”和“今日无课”，删除重复空态及“选择卡片查看详情”。
- 选课界面由误导性的多选改为按课程 ID 保存的单选，未选择时禁用“查看课程”。旧提交请求没有映射当前课程，因此不猜测写接口；预览页提供可执行的“打开教务系统”和“返回选课”，由官方系统承接实际选课，不再留下禁用按钮死路。
- 官方入口采用与 `JwClient.jwUrl` 一致的固定 HTTPS origin，不附带凭证、会话、课程参数或 Intent extras，不声称浏览器内已登录或已完成选课。
- 入口位于课程状态分支之外，加载、失败、空数据和正常课程列表中均可达；未登录也可直接进入官方系统。匿名只读 GET 已确认入口返回 200、到达 `/caslogin` 并指向统一认证登录页；未输入凭证或执行选课。
- 统一外链助手报告启动是否被系统接受，空 Context 或不可用处理器均触发一次失败反馈。教务入口、找回密码和两处充值入口不再静默失败；充值确认门槛及顺序保留。
- 新增交接 Intent、失败回调和预览页重试回归，均使用 ContextWrapper 拦截，不实际启动浏览器或执行交易。错误 Snackbar 提供 Polite 语义、遵循系统推荐停留时长，并在减少动效时同时禁用位移、淡入和容器尺寸动画。
- 对话框确认先执行 `onConfirm` 再执行清理，避免提前清空所选对象；教材确认固定目标快照、增加提交锁与失败解锁，删除没有实际批量请求的入口，不再无请求却显示“已提交”。
- 从洗浴或凭证中心进入绑定，完成/取消后返回调用页，不再误入整套引导；调用来源从返回栈判断，支持 Activity 重建。
- 生活页刷新改为直接图标操作，不再经“更多”菜单；通用设置仍从“我的”进入。精简缺失数据说明，不把没有数据宣称为未绑定。
- 今天及生活页在电费未绑定时不发起房间查询，避免可选服务的请求拖住今天页的首次展示。
- 成绩口径使用互斥分段控件，学期显示实际选中状态，无成绩时禁用分享；移除无效搜索入口和没有依据的发布时间断言。

### 输入、状态与文字

- 登录提交防重入，提交时收起键盘、锁定相关输入；页面离开后取消页面级协程，避免恢复到永久 loading。
- 验证码按需加载，切换学号清空旧验证码；短信发送增加并发锁、同号码重发冷却和明确重发入口。
- 更换电费上级位置立即清除下级旧值，重复选择可重试；更换账号立即隐藏旧房间，保留本人房间确认和上游身份校验限制。
- 修复提醒中心首档配置被错误显示成第二档；免打扰编辑草稿支持状态恢复。
- 生物识别失败/异常后释放操作锁；移除常态开关、已选主题和已保存状态的重复提示。保留风险、错误恢复及本地保存与服务器验证的区别。
- 实际截图发现欢迎页作息编辑把主要操作推到首屏外：将“开始绑定 / 暂不绑定”前置，作息编辑改为可展开区域，保留全部配置能力并删除重复进度说明。

## 验证方法

- `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:connectedDebugAndroidTest`、`:app:lintDebug`。
- `python tools/check-contrast.py` 覆盖 18 套浅/暗色 scheme 的文字、边界、语义容器及表面层级。
- 新增测试覆盖 599/600dp 导航切换与选中状态、洗浴绑定返回与重建、首档提醒配置、原生按钮、只读菜单、单选面板及减少动效。
- 保留 `TargetResolutionLayoutTest` 的 1200px 测试前提，覆盖 320/356/400dp 与 1.0/1.4 倍字体。
- 设备测试使用临时 Gradle init script，为 debug 增加 `.md3audit` 包名后缀；不覆盖或清空已安装的 `com.union.campus` 用户数据。
- Impeccable 静态检测返回 `[]`；其结果不是 Compose 运行态或 Material3 合规证明。

复现隔离安装时可在仓库外保存下列临时 init script，并在 Gradle 命令中通过 `-I <文件路径>` 加载；不要把测试后缀写进生产配置：

```groovy
allprojects {
    plugins.withId('com.android.application') {
        androidComponents.finalizeDsl { android ->
            android.buildTypes.getByName('debug').applicationIdSuffix = '.md3audit'
        }
    }
}
```

### 发现问题后的修复闭环

- 原生按钮接入后实测布局仅约 40–45dp，已显式增加 48dp 最小尺寸；没有通过降低测试阈值绕过。
- sheet 的 `animateTo(..., snap())` 仍存在帧等待，减少动效路径已改为直接 `snapTo`。
- 1200px 布局套件首轮误在 1092px 物理宽度运行，已按套件约定配置为 1200×2670 再完整回归。
- 一轮 55 项测试在第 54 项因 MuMu 进程退出、设备离线中断，没有记为通过；重新启动设备后重跑。
- 欢迎页单元屏幕用例曾随模拟器启动器转到横屏，现与真实 `MainActivity` 的竖屏窗口约束保持一致，保留两个 CTA 无需滚动即可显示的断言。
- 模拟器在主 Activity 与无方向声明的组件宿主切换时反复横竖屏重建，导致 `setContent` 所在根节点被销毁；系统旋转锁不足以约束该行为。新增 `app/src/debug/AndroidManifest.xml`，仅为 Compose 测试宿主声明与真实主 Activity 相同的竖屏方向，不改变 release 清单，也不放宽断言。
- 首轮原始窗口截图捕获到转场中的旧 Tab，已改为等待页面标题与 Compose 帧，并通过 PixelCopy 获取当前根节点截图；骨架占位截图不作为完成态视觉证据。
- 新原生模态的真实合成截图暴露浅色系统栏白图标与平台二次淡入问题；局部修复窗口属性，不以延长截图等待时间掩盖它。

## 最终验证结果

- 标准包（未加载隔离 init script）：`compileDebugKotlin`、`compileDebugAndroidTestKotlin`、`testDebugUnitTest`、`assembleDebug`、`lintDebug` 全部通过；产物包名复核为 `com.union.campus`。
- 单元测试 **91 项通过，0 失败、0 错误、0 跳过**。
- Android 15 当前最终代码的完整设备回归 **68 项通过，0 失败、0 错误、0 跳过**。最终 XML：`app/build/outputs/androidTest-results/connected/debug/TEST-24031PN0DC - 15-_app-.xml`。58/61/62/65/67 项是中间版本，不用于替代当前结果。
- lint **0 error、10 warning、2 hint**；保持既有非错误项数量，未新增 lint baseline。debug 测试宿主仅沿用竖屏产品约束的方向提示豁免；编译仍有既有图标、Slider 及测试 API 弃用提示。
- `python tools/check-contrast.py`：**18 套 scheme 全部通过**；`git diff --check` 通过。
- 对当前最终代码额外独立运行 `AppVisualAuditTest` 和 `ConfirmationContractTest` 三组，均为 **4 项通过**：400dp / 1.0×、364dp / 1.4×、800dp / 1.0×。覆盖欢迎页、五 Tab、设置、免打扰、未登录选课入口、浅/暗课程预览及失败反馈，共采集 **60 张有效页面/交互截图**。
- 已查看三套新截图汇总及大字号选课入口、预览和失败反馈原图：主要操作和导航未见裁切，800dp 使用导航轨；欢迎页无需先经过作息表单，选课没有禁用死按钮；模态面板无背景透字，浅/暗系统栏图标均可见。预览与失败画面使用明确标为“测试课程”的 fixture，不向生产数据写入。大字号部分摘要正常省略，未把它当作完整 2 倍字体或 TalkBack 验收。
- 最新截图保留在本机临时目录 `C:/Users/austo/AppData/Local/Temp/opencode/union-md3-final/complete/tablet` 的 `main`（51 张）和 `preview`（9 张）子目录；不提交设备截图或账号数据。此前 `final-validation`、`accepted` 为中间版本，不作为当前代码的最终视觉证据。
- 已核对本轮两个 `.md3audit` 隔离包均不再安装，原应用及其测试包仍在；恢复并读取确认设备为原来的 1092×1920、273dpi、1.0× 字体、无尺寸/密度 override，自动旋转保持原值。最终恢复曾遇到 ADB 断连，重连后逐项严格核验，再关闭本轮启动的模拟器；不能只以关机命令成功代替恢复完成。所有构建命令均已结束。
- 改动已集成主工作区；三个并行工作树保留便于复查，未创建提交、发版或推送远端。

## 原始目标闭环

| 要求 | 当前实现与直接验收依据 | 结论 |
| --- | --- | --- |
| Jetpack Compose Material3 Expressive 优化 | 原生按钮、菜单、Expressive 加载器、MotionScheme、Dialog 与自适应 rail；当前编译、组件回归和三尺寸浅暗截图 | 完成 |
| 使用技能 audit 达到约 90 分 | 独立源码复核十类合计 92/100，保留支持边界，不沿用旧版分数冒充当前结果 | 完成 |
| 优化操作逻辑 | 真实 App 的五 Tab/绑定返回/未登录选课入口，官方交接 Intent、失败可重试及确认顺序均有当前设备回归 | 完成 |
| 精简说明与避免误导 | 欢迎页操作前置、作息展开、去除重复提示、伪检查和假成功；课程预览改为可执行官方交接，60 张新画面复核 | 完成 |
| 工作流且最多五个独立工作树 | 主工作树加三个独立工作树，共四个；主区集成、独立复核、验证后恢复环境；未提交/发布 | 完成 |

## 后续边界

- `app/src/main/java/com/union/campus/ui/theme/Theme.kt:50` 的全局 0.8–1.4 字号钳制及课表网格字号约束仍在，不宣称支持系统 2 倍字体。
- `app/src/main/AndroidManifest.xml:27` 保留当前竖屏产品约束；宽度适配及导航切换不等于完成折叠铰链避让、多窗状态机或列表详情双栏。
- 未覆盖所有 OEM、Android 版本、动态壁纸色及正式 TalkBack 人工验收；未建立帧耗时 benchmark。
- 无账号回归不替代真实教务/洗浴/电费业务验收，尤其不能证明既有写接口协议正确。
