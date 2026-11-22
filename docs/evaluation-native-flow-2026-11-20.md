# 原生教学评价闭环

## 依据

- 已有 `教务/apidetail.md`、`教务/API_FINAL.md` 记录 `h5jxpj` 查询/写接口；本次纠正了列表和详情调用参数文档。
- 匿名读取当前 H5 首页及其公开入口 bundle，定位 `pages-jw-subPagesA-h5jxpjJava` 与 `pages-jw-subPagesA-jxpjJavaEdit`。对照线上调用代码确认真实字段及提交树。没有读取学生登录会话，也没有发送评价写请求。

## H5 契约

- 列表：`getJxpj_wjs` 无业务参数。`resultSet[]` 是轮次，含 `xnxqdm/xnxqmc/pjlcdm/pjlcmc/kssj/jssj/kcSet[]`；课程行含课程、教师、学分、评价类型和状态字段。
- 详情：`getJxpjnr(xnxq,pjlcdm,kcdm,jsdm,pjlxdm,pjztdm)`；返回 `zt` 与 `resultSet[] → pjfsdm/pjfsmc → yjzbSet[] → ejzbSet[] → xxSet[]` 分层问卷。
- 类型：`1` 单选，`2` 范围分值，`3` 多选，`4` 文本。初始答案读取 `isxz/tkjg/wdjg`；全部必答，并按 `tkxx/tksx` 限制数值题。
- 提交：用户确认后调用 `submitJxpj`，传 `param=escape(JSON.stringify(e))`；内部 `e` 含课程/轮次标识及 `pjnrSet` 分层回答。只有 `resultSet[0].flag == "1"` 显示成功。

## 实现与安全边界

- `EvaluationScreen.kt` 展示学期、轮次、起止时间、课程、教师、学分、评价类型；课程详情按服务端问卷逐层渲染，题目在分类内按 `1、2、…` 编号，与官方页面一致；不预置或臆造评价选项。
- `EvaluationContract.kt` 解析官方结构、初始化已有答案、校验完整性，并按原生 H5 的 `escape()` 和多选 `|` 格式构造提交数据。未知题型或缺失字段会阻止写入。
- 提交前二次确认；写请求禁用 OkHttp 自动连接重试及重定向。只有服务端明确成功旗标才清除草稿；网络/会话/不完整回包时标为结果未确认，提示刷新并核对，避免直接重发。
- “评教”和“选课”界面均无跳转教务系统的按钮；外链 Intent 测试改为只覆盖仍保留的找回密码/充值入口，不再断言已删除的教务入口。
- 保留 H5 列表返回的全部 primitive 课程/轮次原始字段于内存，主 UI 优先展示人类可读字段。
- 列表课程按 `zt` 升序展示，待评课程排在最前，与官方 H5 的排序一致。

## 验证范围

- `EvaluationContractTest`（6 项）覆盖分组时间与课程信息、四种题型、预置答案、必答/数值区间、嵌套写入树、JS legacy escape、多选序列化和成功旗标。
- `EvaluationQuestionEditorTest`（4 项）覆盖真实 Compose 单选/多选/分值/文本交互、列表→课程→二次确认→提交回调仅一次，以及不显示外跳按钮。
- 本次实测（1200×2670 / 480dpi 覆盖、字体 1.0×）：单测 **97 项通过**，设备测试 **71 项通过**（含 `TargetResolutionLayoutTest` 六组宽字号、五 Tab、评教/选课、无障碍与减少动效回归），`lintDebug` **0 error**；`tools/check-contrast.py` 18 套配色全部通过。
- 未使用真实学生会话做现场查询，也未调用 `submitJxpj` 实测，以避免提交不可撤回的真实评价。运行期接口可用性依赖用户在应用内已绑定的教务会话。

## 明确未实现的边界

- 官方列表还有“一键评教”分支：轮次 `kqxjpj == "1"` 且 `wcxjpj != "1"` 时不进单课程问卷，而是多选课程后调用 `submitJxpjXjkc`。本应用按项目既定规则不自动批量评价，未实现该分支；`canEvaluate/completedCourseCount/maxCount` 已解析但未使用。当前截图所示的逐门“评价”轮次不受影响。
- 分值题在官方页面失焦时把越界值钳制到 `tkxx/tksx`，本应用改为提交前拒绝并提示；两者都不会写出越界值。
- 服务端是否接受空 `pjlxdm/pjztdm`、以及单课程提交在批量轮次中的行为，均未实测。
