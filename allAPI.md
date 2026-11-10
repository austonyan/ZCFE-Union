# 校园一体化 API 总表（allAPI）

> 整理范围：**教务系统**（郑州财经学院 / Kingosoft 青果）、**洗澡 App**（大白U帮 `com.zhuochi.hydream` 2.2.1）、**电费**（完美校园 / 新开普）。
> 整理日期：2026-11-05。
> 整理原则：教务只保留**学生（STU）身份可用**的接口，教师端、辅导员端、审批端、教务管理端已剔除，完整剔除清单见 [§1.8](#18-已剔除教师--辅导员--管理端接口)；洗澡按确认**只保留原生接口**，Flutter `dabai` 接口不收录。
> 所有结论均来自仓库内三份文档的实测、抓包或逆向结果，未在本文件重新发起请求。

## 0. 总览

| 子系统 | Base URL | 认证方式 | 协议 | 学生端接口规模 |
|---|---|---|---|---|
| 教务 · H5 | `https://jw.zzife.edu.cn/wap/{接口名}.action` | CAS 登录后持 `JSESSIONID` Cookie | POST 表单 + 参数加密（JSON 响应） | **163** 个 |
| 教务 · 辅助 | `https://jw.zzife.edu.cn` | 同上 | 上传/下载/登录辅助 | 8 个 |
| 教务 · PC | `https://jw.zzife.edu.cn` | 同上 + `Referer` | JSP/Action，GBK HTML/XML | 13 个 Kingo 端点 + 10 个补充数据接口 |
| 洗澡 · 原生 | `http://lz.hydream.cn/api/v1/` | `header.token`（`LoginEntity.token` / `TOKEN_ID`） | DES + `ztechMsg` + SHA-1 | **113** 个 |
| 电费 · 上游 | `https://xqh5.17wanxiao.com/smartWaterAndElectricityService` | 明文查询接口**不校验登录**；支付需 `loginCheck` 的 token | 表单 `cmd` 命令 / AES 加密通道 | 13 个已确认命令 + 支付/开放平台链路 |
| 电费 · 本地封装 | `http://127.0.0.1:8000` | 无 | 本项目 `webapp.py` 自有 HTTP JSON | 12 个 |

### 0.1 三套系统互不共享凭证

| 凭证 | 所属 | 能做什么 | 不能做什么 |
|---|---|---|---|
| `JSESSIONID` | 教务 | H5 JSON API、PC 页面/数据接口 | 不能调洗浴、电费 |
| `LoginEntity.token`（`TOKEN_ID`） | 洗澡原生（`lz`） | `DeviceApi/*`、`DeviceAreaApi/*`、`KailuApi/*` | 不能调教务、电费 |
| 学校编码 + 学号 | 电费明文通道 | 查询/绑定/解绑房间与用量 | 不能支付、不能解绑用户 |
| `loginCheck` token / `wxToken` | 电费支付 | 下单、收银台、支付状态 | 不能查教务/洗浴 |

### 0.2 标记说明

- ✅ 已实测（返回真实业务数据；写入类会单独标注）。
- ⚠️ 端点存在，但需更具体参数、角色受限，或本部署返回 404。
- ➖ 未实测（多为写入类，文档整理时未真实提交）。
- 🟡 仅从 DEX/AOT 字符串或 `rest_client` 方法名恢复，未单独请求验证。
- 🔑 需要登录/凭证；❌ 不需要。

---

# 1. 教务系统（郑州财经学院 Kingosoft）

## 1.1 基本信息与架构

| 项 | 值 |
|---|---|
| 教务入口 | `https://jw.zzife.edu.cn/` |
| 统一认证 | `https://authserver.zzife.edu.cn/authserver/login` |
| 学校代码 | `13497` |
| H5 端 | `/h5/index.html` + `/wap/*.action`（uni-app「喜鹊儿 H5」） |
| H5 参数加密密钥 | `G_ENCRYPT = <token>` |
| PC 端 DES 密钥 | `_deskey=a2luZ29zb2Z0LTEzNDk3`（= Base64(`kingosoft-13497`)） |
| 会话 | `JSESSIONID`（域 `jw.zzife.edu.cn`，HttpOnly）；同一 Cookie 同时适用于 PC 与 H5 |
| 角色 | 本文仅覆盖 `usertype=STU`（学生） |

架构：

```text
用户 ──账号密码──▶ 统一身份认证 CAS (authserver.zzife.edu.cn)
                        │ 302: ticket=ST-xxxx
                        ▼
        教务 jw.zzife.edu.cn/caslogin ──▶ 写入 JSESSIONID
                        │
        ┌───────────────┴────────────────┐
        ▼                                ▼
  PC 网页端（HTML/JSP）            H5 移动端（JSON API）
  /student/**  /kbbp/**            /wap/*.action
```

## 1.2 CAS 登录（4 步，全部可用）

| 步骤 | 请求 | 关键点 |
|---|---|---|
| ① | `GET https://authserver.zzife.edu.cn/authserver/login?service=https%3A%2F%2Fjw.zzife.edu.cn%2Fcaslogin` | 从 HTML 解析 `pwdEncryptSalt`（AES 盐，每次会话不同）与 `execution`（如 `e1s1`）；`lt` 可为空 |
| ② | `GET /authserver/needCaptcha.html?username={学号}&pwdEncrypt2={pwdEncryptSalt}&_={毫秒时间戳}` | 返回文本 `true`/`false`；`true` 时需 `GET /authserver/captcha.html` 图形验证码 |
| ③ | `POST /authserver/login`（`application/x-www-form-urlencoded`，跟随重定向） | 成功后 302 → `https://jw.zzife.edu.cn/caslogin?ticket=ST-…` → `/frame/homes.html`，写入 `JSESSIONID` |
| ④ | `GET /frame/homes.html` | 页面含 `KINGOSOFT` 即登录成功；之后任一 H5 API 成功即会话有效 |

③ 的表单字段：

```text
username=学号
password=AES-CBC 密文（见 1.2.1）
captcha=（空）
_eventId=submit
cllt=userNameLogin
dllt=generalLogin
lt=（可空）
execution=（步骤①解析）
rememberMe=true
rmShown=1
```

> ⚠️ **必须带浏览器 User-Agent**：不带 UA 请求 `authserver.zzife.edu.cn` 会返回 **502**。
> ⚠️ 密码错误时返回登录页 HTML，含 `id="msg"` / `errormessages`；连续失败后 `needCaptcha=true`。
> ① 的替代入口：`GET https://jw.zzife.edu.cn/caslogin`，未登录时返回 JS 重定向到 authserver。

### 1.2.1 密码加密（AES-CBC）

复刻自 `authserver/zzifeThemec/static/common/encrypt.js`，已用 Python 实测：

```python
import base64, secrets
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"  # 无 l/v/I/O/0/1 等易混字符

def encrypt_password(password: str, salt: str) -> str:
    rand64 = ''.join(secrets.choice(AES_CHARS) for _ in range(64))
    iv     = ''.join(secrets.choice(AES_CHARS) for _ in range(16))
    cipher = AES.new(salt.encode(), AES.MODE_CBC, iv.encode())
    return base64.b64encode(cipher.encrypt(pad((rand64 + password).encode(), 16))).decode()
```

## 1.3 H5 JSON API 调用规范

### 1.3.1 统一流水线

```text
业务参数 biz ──┐
              ├─→ ① data = biz + extraParam + {userid, userId, yhzh, usertype}
extraParam ───┘        （已有键不覆盖；mapping 重命名；deleteParam 删除）
       ↓ ② qs = "k1=v1&k2=v2…"        （直接字符串拼接，不做 URL 编码）
       ↓ ③ param  = of_encrypt(qs, "<token>")
         param2 = getMd5_2(qs)
       ↓ ④ POST https://jw.zzife.edu.cn/wap/{接口名}.action
          Content-Type: application/x-www-form-urlencoded;charset=UTF-8
          Body: action=jw_apply&param={param}&param2={param2}&timestamp={毫秒时间戳}
```

- 通用注入：`userid` = `userId` = `yhzh` = **学号**，`usertype` = `STU`（注入纯学号，不是 `13497_学号`）。
- 参数映射：`mapping`（如 `xktype→type`、`yhzh→loginid/gh/userID`）、`deleteParam`（删 `hidekey/dataset` 等）、`extraParam`（固定附加）。
- 响应：HTTP 200 + JSON（UTF-8），**无统一包裹层**；顶层键随接口而异（`resultSet`/`result`/`xnxq`/`xscj`…）；`flag="0"` 为成功；空数组/空对象不是错误。
- 空学期、未开放功能通常返回空数组，而不是错误。

### 1.3.2 参数加密算法（Python 复刻）

```python
import hashlib
_B36 = '0123456789abcdefghijklmnopqrstuvwxyz'

def _b36(n):
    if n < 0: return '-' + _b36(-n)
    if n == 0: return '0'
    out = ''
    while n > 0:
        out = _B36[n % 36] + out
        n //= 36
    return out

def of_encrypt(data: str, key: str = '<token>') -> str:
    """逐字符 (ord(data)+ord(key)+offset) 取后 3 位数字；每 9 位数字转 base36 补齐 6 位。"""
    if not data or not key: return data
    s, o = len(key), len(data)
    g = -(-o // s)                  # ceil(o/s)
    c = 6 * -(-o // 3)
    u = c % s                       # 偏移量
    m = ''
    for t in range(g):
        for i in range(1, s + 1):
            r = t * s + i
            if r > o: break
            f = ord(data[r-1]) + ord(key[i-1]) + u
            m += ('000' + str(f))[-3:]
    h, r = '', 0
    while r < len(m):
        j = min(r + 9, len(m))
        h += ('000000' + _b36(int(m[r:j])))[-6:]
        r += 9
    return h

def get_md5_2(data: str) -> str:
    a = hashlib.md5(data.encode()).hexdigest()
    s = [''] + list(a)
    out = ''.join(c for i, c in enumerate(s) if i not in (3, 10, 17, 25))
    return hashlib.md5(out.encode()).hexdigest()
```

> 密钥 `<token>` 即页面注入的 `G_ENCRYPT`。学校若更换，从 `/h5/` 首页引用的 `/custom/js/SetRootPath4H5.jsp` 读取。

### 1.3.3 会话失效的三种形态（HTTP 状态码恒为 200）

1. HTTP 200 + JS `alert('温馨提示：凭证已失效，请重新登录!')` + 跳转 `/`（`/jw/common/*`、`/student/*`）。
2. HTTP 200 + HTML「错误:无效访问请求」页（PC 页面/教评类）。
3. JSON `{"type":"noLogin"|"invalidSession"}`（部分 H5 接口）。

客户端必须检查响应体，不能只看状态码。

## 1.4 H5 学生端接口总表（163 个）

> URL：`POST https://jw.zzife.edu.cn/wap/{接口名}.action`。
> 「主要参数」只列**业务参数**，`userid/userId/yhzh/usertype` 等通用参数不重复列；`—` 表示只带通用参数即可。
> 「实测」：✅=已返回 JSON；⚠️200=需更具体参数或角色受限；⚠️404=本部署未开放；➖=写入类未真实提交。

| 模块 | step | 接口名（URL 文件名） | 功能 | 主要业务参数 | 类别 | 实测 |
|---|---|---|---|---|---|---|
| Getaskbjdyskdmc | Getaskbjdyskdmcdetail | `Getaskbjdyskdmcdetail_H5` | 上课点名册（随堂考勤） | xn、xq、kcdm、skbjdm | 查询 | ✅ |
| Getaskbjdyskdmc | Getaskbjdyskdmclist | `Getaskbjdyskdmclist_H5` | 上课点名册（随堂考勤） | xn、xq、loginid | 查询 | ⚠️200 |
| Getaskbjdyskdmc | xnxq | `getxnxq_xl` | 上课点名册（随堂考勤）·学年学期列表 | —（通用参数） | 查询 | ✅ |
| cjfb | detail | `getCjfb_xq` | 成绩分布统计·详情 | dj | 查询 | ✅ |
| cjfb | list | `getCjfb` | 成绩分布统计·列表/查询 | —（通用参数） | 查询 | ✅ |
| cxcx | detail | `MyCxKc` | 重修查询·详情 | —（通用参数） | 查询 | ✅ |
| djksbm | detail | `MyDjKs` | 等级考试报名·详情 | —（通用参数） | 查询 | ✅ |
| djksbm | tj | `DjKsBm` | 等级考试报名·提交 | color、fontSize | 写入 | ✅ |
| djkscj | detail | `djkscj` | 等级考试成绩·详情 | —（通用参数） | 查询 | ✅ |
| gfcxsq | getGfcxsqLb | `getGfcxsqLb` | 功夫查询申请（自定义查询工作流） | —（通用参数） | 查询 | ✅ |
| gfcxsq | getXq | `getGfcxsqLc` | 功夫查询申请（自定义查询工作流） | —（通用参数） | 查询 | ✅ |
| gfcxsq | getdefaulttemplate | `getGfcxsqLc` | 功夫查询申请（自定义查询工作流） | —（通用参数） | 查询 | ✅ |
| gfcxsq | submitgetXq | `getGfcxsqSubmit` | 功夫查询申请（自定义查询工作流） | —（通用参数） | 写入 | ✅ |
| h5aqfdJxpj | getHistoryTeachingEvaluation | `getJxpj_yjs` | 教学评价（安全反馈/历史） | xnxq、hidekey、xnxqmc | 查询 | ✅ |
| h5aqfdJxpj | getHistoryXnxq | `getxnxq_xl` | 教学评价（安全反馈/历史） | —（通用参数） | 查询 | ✅ |
| h5aqfdJxpj | getJxpjDetail | `getJxpjnr` | 教学评价（安全反馈/历史） | hidekey、qfdflag | 查询 | ✅ |
| h5aqfdJxpj | getdefaulttemplate | `getJxpj_wjs` | 教学评价（安全反馈/历史） | —（通用参数） | 查询 | ✅ |
| h5aqfdJxpj | submitgetSaveJxpjDetail | `submitJxpj` | 教学评价（安全反馈/历史） | —（通用参数） | 写入 | ➖未测 |
| h5aqfdJxpj | submitgetSubmitJxpjList | `submitJxpj_qfd` | 教学评价（安全反馈/历史） | —（通用参数） | 写入 | ➖未测 |
| h5bksq | cancelBksq | `cancelBksq` | 补考申请 | kcdm、xh、xnxqdm、kslcdm | 写入 | ➖未测 |
| h5bksq | getBksqjl | `getBksqjl` | 补考申请 | xh | 查询 | ✅ |
| h5bksq | getBksqxx | `getBksqxx` | 补考申请 | xh | 查询 | ✅ |
| h5bksq | submitBksq | `submitBksq` | 补考申请 | kcdm、xh、xnxqdm、kslcdm | 写入 | ➖未测 |
| h5bzrpj | getBzrpjList | `getBzrpjList` | 班主任评价 | lx、usertype | 查询 | ⚠️404 |
| h5bzrpj | getBzrpjZb | `getBzrpjZb` | 班主任评价 | bjdm、bzrdm、pjlcdm、pjzt、usertype、pjztdm | 查询 | ⚠️404 |
| h5bzrpj | submitBzrpj | `submitBzrpj` | 班主任评价 | bjdm、bzrdm、pjlcdm、pjnr、pjztdm | 写入 | ➖未测 |
| h5ck | gethbbj | `gethbbj` | 蹭课（他班课表） | xnxq、xn、xq_m、skbjdm | 查询 | ✅ |
| h5ck | kc | `getckkb` | 蹭课（他班课表） | xnxq、xn、xq、page、cs | 查询 | ✅ |
| h5ck | xnxq | `getxnxq_kb` | 蹭课（他班课表）·学年学期列表 | —（通用参数） | 查询 | ✅ |
| h5ckpyfa | list | `getPyfa` | 查看培养方案·列表/查询 | —（通用参数） | 查询 | ✅ |
| h5cxsq | lb | `cxsqlb` | 重修申请·列表 | —（通用参数） | 查询 | ✅ |
| h5cxsq | sq | `cxsqtj` | 重修申请·提交申请 | xnxq、xn、xq_m、kcdm、flag、cxxn、cxxq_m、kclb1 | 写入 | ✅ |
| h5grxx | detail | `MyXjda` | 个人信息（学籍档案）·详情 | userId、usertype、other、sfid | 查询 | ⚠️200 |
| h5hksq | cancelHksq | `cancelHksq` | 缓考申请 | kcdm、xh、xnxqdm、kslcdm | 写入 | ➖未测 |
| h5hksq | getHksqHklb | `getHksqHklb` | 缓考申请 | xh | 查询 | ✅ |
| h5hksq | getHksqLcxx | `getHksqLcxx` | 缓考申请 | xh | 查询 | ✅ |
| h5hksq | getHksqjl | `getHksqjl` | 缓考申请 | xh | 查询 | ✅ |
| h5hksq | getHksqxx | `getHksqxx` | 缓考申请 | xn、xq、xh、kslcdm | 查询 | ✅ |
| h5hksq | sqhk_getXq | `sqhk_getXq` | 缓考申请 | xn、xq、kcdm、xh、xnxqdm、kslcdm、zt、kcmc | 查询 | ✅ |
| h5hksq | submitHksq | `submitHksq` | 缓考申请 | kcdm、xh、xnxqdm、kslcdm、sqhk_scfj、sqhk_yysm、sqhk_hklb、zlhklb | 写入 | ➖未测 |
| h5jskb | bjlb | `getAllbj` | 教室/班级课表 | xnxq、xn、xq、nj、jstj | 查询 | ✅ |
| h5jskb | detail | `mycourseschedule` | 教室/班级课表·详情 | xnxq、week、jsdm、bjdm | 查询 | ✅ |
| h5jskb | kbjs | `getAllJs` | 教室/班级课表 | building | 查询 | ✅ |
| h5jskb | kbjxl | `GetJxlbyXQ` | 教室/班级课表 | —（通用参数） | 查询 | ✅ |
| h5jslqjc | getJslqjcxx | `getJslqjcxx` | 教材领取/确认 | —（通用参数） | 查询 | ✅ |
| h5jslqjc | list | `getQrxyjc` | 教材领取/确认·列表/查询 | —（通用参数） | 查询 | ✅ |
| h5jslqjc | tj | `doQrxyjc` | 教材领取/确认·提交 | xnxq、jc | 写入 | ➖未测 |
| h5jsyy | getJsyyInfo | `getJsyyInfo` | 教室预约 | —（通用参数） | 查询 | ✅ |
| h5jsyy | getJsyyQx | `getJsyyQx` | 教室预约 | xn、xq_m、hddm | 查询 | ✅ |
| h5jsyy | getJsyySqr | `getJsyySqr` | 教室预约 | dataset | 查询 | ⚠️200 |
| h5jsyy | getJsyyTj | `getJsyyTj` | 教室预约 | dataset | 查询 | ✅ |
| h5jsyy | getJsyyYsq | `getJsyyYsq` | 教室预约 | xn、xq_m | 查询 | ✅ |
| h5jsyy | jsyy_hddd | `getJsyyHdddMb` | 教室预约 | —（通用参数） | 查询 | ✅ |
| h5jsyy | jsyy_hddd_js | `getJsyyHdddMbJs` | 教室预约 | —（通用参数） | 查询 | ✅ |
| h5jsyy | jsyy_hdsj | `getJsyyHdsjMb` | 教室预约 | —（通用参数） | 查询 | ⚠️200 |
| h5jsyy | jsyy_hdsj_js | `getJsyyHdsjMbJs` | 教室预约 | —（通用参数） | 查询 | ✅ |
| h5jsyy | jsyy_jxl | `GetJxlbyXQ` | 教室预约 | —（通用参数） | 查询 | ✅ |
| h5jxpj | getJxpjJg | `getJxpjJg` | 教学评价（评教） | xnxq、pjlcdm | 查询 | ✅ |
| h5jxpj | getJxpjJg_ejzb | `getJxpjJg_ejzb` | 教学评价（评教） | xnxq、kcdm、pjlcdm、pjztdm、yjzbdm | 查询 | ✅ |
| h5jxpj | getJxpj_wjs | `getJxpj_wjs` | 教学评价（评教） | —（通用参数） | 查询 | ✅ |
| h5jxpj | getJxpj_yjs | `getJxpj_yjs` | 教学评价（评教） | xnxq | 查询 | ✅ |
| h5jxpj | getJxpjlcByXnxq | `getJxpjlcByXnxq` | 教学评价（评教） | xnxq | 查询 | ✅ |
| h5jxpj | getJxpjnr | `getJxpjnr` | 教学评价（评教） | xnxq、kcdm、jsdm、pjlcdm、pjlxdm、pjztdm | 查询 | ✅ |
| h5jxpj | getxnxq_xl | `getxnxq_xl` | 教学评价（评教） | —（通用参数） | 查询 | ✅ |
| h5jxpj | submitJxpj | `submitJxpj` | 教学评价（评教） | param | 写入 | ➖未测 |
| h5jxpj | submitJxpjXjkc | `submitJxpjXjkc` | 教学评价（评教） | userid、userId、usertype、xnxqdm、pjlcdm、jsandkc | 写入 | ➖未测 |
| h5ksap | getSysCustomer | `getAllExam` | 考试安排·考试安排查询 | —（通用参数） | 查询 | ✅ |
| h5ksap | ksap_getSysCustomer | `ksap_getSysCustomer` | 考试安排 | uuid、xxdm | 查询 | ✅ |
| h5ksap | xnxq | `getxnxq_kb` | 考试安排·学年学期列表 | —（通用参数） | 查询 | ✅ |
| h5kxjs | detail | `getJsxx` | 空闲教室查询·详情 | rq、jxl | 查询 | ✅ |
| h5kxjs | jxl | `GetJxlbyXQ` | 空闲教室查询 | —（通用参数） | 查询 | ✅ |
| h5sxpy | getbj | `getxzbj` | 实习评优 | nj、zy | 查询 | ✅ |
| h5sxpy | getnj | `getnj_xnxq` | 实习评优 | xnxq、xn、xq | 查询 | ✅ |
| h5sxpy | getxnxq | `getxnxq_kb` | 实习评优 | —（通用参数） | 查询 | ✅ |
| h5sxpy | getyx | `getyxb` | 实习评优 | —（通用参数） | 查询 | ✅ |
| h5sxpy | getyxxs | `getSxpy_ckyxxslist` | 实习评优 | xnxq、nj、zy、bj、page、yxb | 查询 | ✅ |
| h5sxpy | getzy | `getzy` | 实习评优 | nj、yx | 查询 | ✅ |
| h5wsxk | jsjl | `GetTeaResume` | 网上选课 | jsdm | 查询 | ✅ |
| h5wsxk | kcfw | `kcfw_wsxk` | 网上选课 | flag | 查询 | ✅ |
| h5wsxk | kcxq | `GetCourse_Detail` | 网上选课 | kcdm | 查询 | ✅ |
| h5wsxk | njzy | `njzy_wsxk` | 网上选课 | xktype、sellx | 查询 | ✅ |
| h5wsxk | skbj | `zxkc_skbj_wsxk` | 网上选课 | xktype、kcid、lx、sellx、njzy | 查询 | ✅ |
| h5wsxk | tjyxjg | `Tjyxjg_wsxk` | 网上选课 | xh | 写入 | ➖未测 |
| h5wsxk | tjyxkc | `Tjyxkc_wsxk` | 网上选课 | njzy、sellx、xh | 写入 | ➖未测 |
| h5wsxk | tjzxjg | `Tjzxjg_wsxk` | 网上选课 | xktype、xh | 写入 | ➖未测 |
| h5wsxk | tjzxkc | `Tjzxkc_wsxk` | 网上选课 | xktype、njzy、sellx、xh | 写入 | ➖未测 |
| h5wsxk | xnxq | `getxnxq_wsxk` | 网上选课·学年学期列表 | xktype | 查询 | ✅ |
| h5wsxk | yxjg | `Yxjg_wsxk` | 网上选课 | —（通用参数） | 查询 | ✅ |
| h5wsxk | yxkclb | `Yxkc_wsxk` | 网上选课 | xktype、njzy、sellx | 查询 | ✅ |
| h5wsxk | zxjg | `Zxjg_wsxk` | 网上选课 | xktype | 查询 | ✅ |
| h5wsxk | zxkclb | `Zxkc_wsxk` | 网上选课 | xktype、njzy、sellx | 查询 | ✅ |
| h5xlcx | detail | `getxl` | 校历查询·详情 | xnxq、xn、xq | 查询 | ✅ |
| h5xlcx | xnxq | `getxnxq_kb` | 校历查询·学年学期列表 | —（通用参数） | 查询 | ✅ |
| h5xscjfb | getSkbjXscjfb | `getSkbj_xscjfb` | 成绩分布（原始/有效成绩） | xnxq | 查询 | ✅ |
| h5xscjfb | getXnxq | `getxnxq_cjcx` | 成绩分布（原始/有效成绩） | —（通用参数） | 查询 | ✅ |
| h5xscjfb | getYscjfbXscjfb | `getYscjfb_xscjfb` | 成绩分布（原始/有效成绩） | xnxq、kcdm、skbjdm | 查询 | ✅ |
| h5xscjfb | getYscjxqXscjfb | `getYscjxq_xscjfb` | 成绩分布（原始/有效成绩） | xnxq、kcdm、skbjdm、fsddm | 查询 | ✅ |
| h5xscjfb | getYxcjfbXscjfb | `getYxcjfb_xscjfb` | 成绩分布（原始/有效成绩） | xnxq、kcdm、skbjdm | 查询 | ✅ |
| h5xscjfb | getYxcjxqXscjfb | `getYxcjxq_xscjfb` | 成绩分布（原始/有效成绩） | xnxq、kcdm、skbjdm、fsddm | 查询 | ✅ |
| h5xycj | detail | `MyCourseScore` | 学业成绩·详情 | xnxq、flag | 查询 | ✅ |
| h5xycj | xnxq | `getxnxq_cjcx` | 学业成绩·学年学期列表 | —（通用参数） | 查询 | ✅ |
| jcdz | detail | `MyJcDz` | 教材对账·详情 | xnxq | 查询 | ✅ |
| jcdz | xnxq | `GetXnXq_JcDz` | 教材对账·学年学期列表 | —（通用参数） | 查询 | ✅ |
| jxrw | detail | `MySkbjxsmc` | 教学任务（课程安排）·详情 | xnxq、xn、xq、skbjdm | 查询 | ✅ |
| jxrw | lb | `MyCdKc_JxRw` | 教学任务（课程安排）·列表 | xnxq | 查询 | ✅ |
| jxrw | xnxq | `GetXnXq_JxRw` | 教学任务（课程安排）·学年学期列表 | —（通用参数） | 查询 | ✅ |
| kbvueh5 | detail | `mycourseschedule` | 我的课表（首页课表）·详情 | xnxq、week、userid、usertype | 查询 | ✅ |
| kbvueh5 | getBxYxb | `getyxb` | 我的课表（首页课表） | —（通用参数） | 查询 | ✅ |
| kbvueh5 | getZxjzgqd | `getZxjzgqd` | 我的课表（首页课表） | yxdm | 查询 | ✅ |
| kbvueh5 | kb_kbdetail_bz | `kb_kbdetail_bz` | 我的课表（首页课表） | xnxq、zc、userId、uuid、xxdm、usertype | 查询 | ✅ |
| kbvueh5 | xnxq | `getxnxq_xl` | 我的课表（首页课表）·学年学期列表 | —（通用参数） | 查询 | ✅ |
| knjzyxk | cddw | `getCddw` | 跨年级/专业选课 | xnxq | 查询 | ✅ |
| knjzyxk | ckKnjzyxkYm | `knjzy_ck_java` | 跨年级/专业选课 | —（通用参数） | 查询 | ✅ |
| knjzyxk | ckWodeSqYm | `knjzy_wdsq_java` | 跨年级/专业选课 | —（通用参数） | 查询 | ✅ |
| knjzyxk | getChooiceButton | `knjzy_query_info` | 跨年级/专业选课 | —（通用参数） | 查询 | ✅ |
| knjzyxk | getnj_xnxq | `getnj_xnxq` | 跨年级/专业选课 | xn、xq | 查询 | ✅ |
| knjzyxk | getyxb | `getyxb` | 跨年级/专业选课 | flag | 查询 | ✅ |
| knjzyxk | knjzy_query_java_kclb1 | `knjzy_query_java_kclb1` | 跨年级/专业选课 | —（通用参数） | 查询 | ✅ |
| knjzyxk | knjzy_query_java_kclb2 | `knjzy_query_java_kclb2` | 跨年级/专业选课 | —（通用参数） | 查询 | ✅ |
| knjzyxk | knjzyxk_njyxzy | `knjzy_query_pycc` | 跨年级/专业选课 | —（通用参数） | 查询 | ✅ |
| knjzyxk | knjzyxk_njyxzy_js | `knjzy_query_java_zy` | 跨年级/专业选课 | —（通用参数） | 查询 | ✅ |
| knjzyxk | lookAllRecord | `knjzy_query_java` | 跨年级/专业选课 | xn、xq、nj、page、pagenum、dataset、xspycc、xsnj | 查询 | ✅ |
| knjzyxk | qxKnjzyxkYm | `knjzy_template_java` | 跨年级/专业选课 | —（通用参数） | 写入 | ✅ |
| knjzyxk | submitknjzyxk_quxiao | `knjzy_cancel_java` | 跨年级/专业选课 | —（通用参数） | 写入 | ✅ |
| knjzyxk | submitknjzyxk_tijiao | `knjzy_submit_java` | 跨年级/专业选课 | —（通用参数） | 写入 | ✅ |
| knjzyxk | tjKnjzyxkYm | `knjzy_template_java` | 跨年级/专业选课 | —（通用参数） | 写入 | ✅ |
| login | login | `loginverify_NODES` | 登录认证（喜鹊儿通道） | —（通用参数） | 查询 | ✅ |
| mxsq | xgerWorkflowSqjl | `xqerWorkflowSqjl` | 免修申请（工作流） | —（通用参数） | 查询 | ✅ |
| mxsq | xqerWorkflowCancelSq | `xqerWorkflowCancelSq` | 免修申请（工作流） | dataset、lcid、systemsource | 写入 | ⚠️200 |
| mxsq | xqerWorkflowCondition | `xqerWorkflowCondition` | 免修申请（工作流） | lcid、systemsource | 查询 | ⚠️200 |
| mxsq | xqerWorkflowDic | `xqerWorkflowDic` | 免修申请（工作流） | ywid、lcid、systemsource | 查询 | ⚠️200 |
| mxsq | xqerWorkflowLookDetail | `xqerWorkflowLookDetail` | 免修申请（工作流） | dm、lcid、systemsource、dataset | 查询 | ⚠️200 |
| mxsq | xqerWorkflowSubmit | `xqerWorkflowSubmit` | 免修申请（工作流） | —（通用参数） | 写入 | ⚠️200 |
| mxsq | xqerWorkflowTemplate | `xqerWorkflowTemplate` | 免修申请（工作流） | lcid、systemsource、dataset | 查询 | ⚠️200 |
| myddc | studentlookwj | `MyddcLb` | 满意度调查问卷 | page、xklx、pagenum | 查询 | ✅ |
| myddc | submitSjxxXswj | `MyddcTj` | 满意度调查问卷 | —（通用参数） | 写入 | ✅ |
| myddc | wqxqlook | `MyddcXq` | 满意度调查问卷 | flag、wjid | 查询 | ✅ |
| qywxlogin | jwlogin | `jwlogin_H5` | 登录认证（企业微信通道） | userID、passWD、id | 查询 | ✅ |
| qywxlogin | loginfirst | `loginfirst_H5` | 登录认证（企业微信通道） | userID、passWD、id | 查询 | ✅ |
| tkxx | detail | `MyTkXx` | 调课信息·详情 | —（通用参数） | 查询 | ✅ |
| wnjxk | getTkclSx | `getWnjxkSx` | 外年级选课 | —（通用参数） | 查询 | ✅ |
| wnjxk | getTkclSx_js | `getWnjxkSx_js` | 外年级选课 | dataset | 查询 | ⚠️200 |
| wnjxk | getWnjxkCondition | `getWnjxkCondition` | 外年级选课 | hidekey | 查询 | ✅ |
| wnjxk | getlist | `getWnjxkCheckList` | 外年级选课 | —（通用参数） | 查询 | ⚠️200 |
| wnjxk | submitgetClTk | `getWnjxkTjTk` | 外年级选课 | —（通用参数） | 写入 | ⚠️200 |
| wnjxk | submitgetSave | `getWnjxkTjSave` | 外年级选课 | —（通用参数） | 写入 | ⚠️200 |
| wspjwjdc | pjjg | `MyWjdc` | 问卷调查 | xnxq、pjlc、kclx、kclb、pjzt、kc | 查询 | ⚠️404 |
| wspjwjdc | pjjgkclb | `MyWjdckc_jg` | 问卷调查 | xnxq、pjlc | 查询 | ⚠️404 |
| wspjwjdc | pjlc | `GetWsPjLcLx` | 问卷调查 | —（通用参数） | 查询 | ⚠️200 |
| xqerNews | listbyjw | `edunews` | 教务新闻/公告 | —（通用参数） | 查询 | ✅ |
| xqjs | list | `getXqjs` | 学情警示·列表/查询 | —（通用参数） | 查询 | ✅ |
| xyjz | detail | `MyXyJz` | 学业进展·详情 | —（通用参数） | 查询 | ✅ |
| ydsq | detail | `MyYdSq` | 学籍异动申请·详情 | —（通用参数） | 查询 | ✅ |
| ydsq | qx | `YdSq` | 学籍异动申请·取消 | xnxq、ydlb | 写入 | ✅ |
| ydsq | tj | `YdSq` | 学籍异动申请·提交 | xnxq、yhzh、ydlb、ydyy、bz、xxdm、lcid、systemsource | 写入 | ✅ |
| zgjbxx | delJtcy | `delJtcy` | 增改基本信息（家庭等） | —（通用参数） | 写入 | ➖未测 |
| zgjbxx | getDicSearch | `getDicSearch` | 增改基本信息（家庭等） | —（通用参数） | 查询 | ✅ |
| zgjbxx | getJtgxDic | `getJtgxDic` | 增改基本信息（家庭等） | —（通用参数） | 查询 | ✅ |
| zgjbxx | getSfzjlxDic | `getSfzjlxDic` | 增改基本信息（家庭等） | —（通用参数） | 查询 | ✅ |
| zgjbxx | getZgjbxx | `getZgjbxx` | 增改基本信息（家庭等） | —（通用参数） | 查询 | ✅ |
| zgjbxx | saveJtcy | `saveJtcy` | 增改基本信息（家庭等） | —（通用参数） | 写入 | ➖未测 |
| zgjbxx | tjZgjbxx | `doZgjbxx` | 增改基本信息（家庭等） | dataset | 写入 | ➖未测 |
| zxsj | detail | `getZxsj` | 作息时间·详情 | —（通用参数） | 查询 | ✅ |

### 1.4.1 常用接口速查

| 场景 | 接口 | 关键参数 | 说明 |
|---|---|---|---|
| 学期字典 | `getxnxq_xl` | — | 最轻量，可作会话保活探针；`dm` 即各接口的 `xnxq`，`xn=dm[0:4]`，`xq=dm[4]` |
| 我的课表 | `mycourseschedule` | `xnxq`、可选 `week` / `jsdm` / `bjdm` | 返回 `week1..week7` 七天课程数组 |
| 学业成绩 | `MyCourseScore` | `xnxq`、可选 `type=1` 有效 / `0` 原始 | 返回 `xscj[]`；空学期为 `{"xscj":[]}` |
| 考试安排 | `getAllExam` | 建议 `xnxq` | 返回 `result[]`（课程/时间/地点/座位号） |
| 学籍档案 | `MyXjda` | `usertype=STU`、`loginId=学号` | 80+ 字段；身份证/出生日期服务端已脱敏 |
| 学情警示 | `getXqjs` | — | 必修/限选/任选学分进展 |
| 培养方案 | `getPyfa` | — | 课程列表 |
| 教材对账 | `MyJcDz` | `xnxq` | 教材费与结算明细 |
| 空闲教室 | `getJsxx` | `rq`、`jxl` | 指定日期/教学楼 |
| 重修查询 | `MyCxKc` | — | 可重修课程 |

## 1.5 H5 平台级辅助接口（8 个）

| 接口 | 方法 | 说明 | 实测 |
|---|---|---|---|
| `POST /wap/FilesUpload.action` | POST(multipart) | 附件上传；`name=fileName`，formData 含 `param`=of_encrypt(参数串)、`param2`=getMd5_2(参数串) | ➖ |
| `POST /wap/mp_YdFilesAction.do` | POST(multipart) | 移动端文件上传；`name=file`，formData 传 `enc=AES(JSON)` | ➖ |
| `GET/POST /wap/H5FileStreamServlet` | GET | 申请附件下载流 | ➖ |
| `POST /wap/baseInfoServlet` | POST | 平台基础信息 | ➖ |
| `POST /wap/selectSchoolH5` | POST | 学校选择（多校部署用） | ➖ |
| `POST /wap/loginH5` | POST | H5 会话登录辅助 | ➖ |
| `POST /h5if/dingDingGetUserInfo_h5.action` | POST | 钉钉通道用户信息（本部署未启用） | ➖ |
| `POST /wap/jwApi.jsp`、`/wap/wapController.jsp` | POST | 通用分发入口；拦截器按 `type/step` 重写为具体 `*.action`，单独调用无业务意义 | ✅(跳板) |

## 1.6 PC 网页端接口

通用约束：Cookie `JSESSIONID` + 请求头 `Referer: https://jw.zzife.edu.cn/frame/homes.html`（或具体页面 URL）；响应多为 **GBK HTML**，部分为 XML。缺 `Referer` 返回「错误:无效访问请求」。

### 1.6.1 Kingo 工具 13 个端点（全部存活，2026-11-05 实测）

| # | 端点 | 方法 | 用途 | 实测结论 |
|---|---|---|---|---|
| 1 | `/caslogin` | GET | 登录入口 | ✅ 未登录返回 JS 重定向；已登录返回 `frame/homes.html` |
| 2 | `authserver/login?service=…` | GET/POST | CAS 统一认证 | ✅ 盐值 + AES 流程可用；**无 UA 时 502** |
| 3 | `/cas/genValidateCode?dateTime=Sat` | GET | 教务侧验证码图片 | ✅ 200，image/jpeg，免登录 |
| 4 | `/custom/js/SetKingoEncypt.jsp` | GET | Kingo 前端加密 JS | ✅ 200，2695 字节，免登录 |
| 5 | `/jw/common/showYearTerm.action` | POST | 当前学年学期 | ✅ JSON：`{xn:"2026", xqM:"0", xqName:"第一学期", userCode:…}` |
| 6 | `/jw/common/getStuGradeSpeciatyInfo.action` | POST | 学生年级/院系/专业 | ✅ `result` 内 `nj=2024, yxdm=02, zydm=022300` |
| 7 | `/student/wsxk.xskcb10319.jsp?params=<base64>` | GET | 个人课表 | ✅ `params`=Base64(`xn=<学年>&xq=<0|1>&xh=<学号>`)；返回 GBK HTML 课表 |
| 8 | `/taglib/DataTable.jsp?tableId=2508` | POST | 培养方案 | ✅ body 见下；实测 76 行真实课程数据 |
| 9 | `/student/xscj.stuckcj_data.jsp` | POST | 成绩查询 | ✅ `sjxz=sjxz3`（学期）实测 13 行；`sjxz=sjxz1`（入学以来）53 行 |
| 10 | `/jw/wspjZbpjWjdc/getPjlcInfo.action` | POST | 教评轮次信息 | ⚠️ 端点存活；当前无开启轮次返回空 body |
| 11 | `/taglib/DataTable.jsp?tableId=50058&fre=1` | POST | 教评教师列表 | ⚠️ 无有效轮次参数时返回「错误:无效访问请求」 |
| 12 | `/jw/wspjZbpjWjdc/save.action` | POST | 教评提交 | ⚠️ 端点存活；未提交真实数据 |
| 13 | `/frame/droplist/getDropLists.action` | POST | 通用下拉列表 | ⚠️ 200 空 body；源码定义但未调用（死代码） |

请求体示例：

```text
# #8 培养方案（menucode S20101，依赖 #6 的 nj/yxdm/zydm）
xn=2026&xn1=2027&xq=0&_xq=第一学期&xh=&nj=2024&fxnj=&menucode=S20101&yxdm=02&zydm=022300&zyfxdm=&fxzydm=&count=1&initQry=0&nj_=2024&fzyxs=zx&menucode_current=S20101

# #9 成绩（menucode S40303；sjxz1=入学以来 sjxz2=学年 sjxz3=学期；yscj=原始 yxcj=有效）
sjxz=sjxz1&ysyx=yscj&zx=1&fx=1&rxnj=2024&btnExport=%B5%BC%B3%F6&xn=&xn1=&xq=&ysyxS=on&sjxzS=on&zxC=on&fxC=on&xsjd=1&menucode_current=S40303
# （rxnj = "20" + 学号[6:8]；学期模式另传 xn/xn1/xq）

# #10 教评轮次（menucode S902）
pjzt_m=20
```

`menucode` 对照：`S20101`=培养方案、`S20301`=课表、`S20403`=考试安排、`S40303`=成绩、`S902`=教评。

### 1.6.2 PC 补充数据接口

| 数据接口 | 方法 | 关键参数 | 返回 | 实测 |
|---|---|---|---|---|
| `POST /student/ksap.ksapb_date.jsp` | POST(表单) | `xn`、`xq`、`kslc`（考试轮次代码，取自 `Ms_KSSW_FBKSLC`，下拉源 `DataTable.jsp?tableId=2538&fre=1`）、`title` | HTML 考试安排表 | ⚠️ 缺 `kslc` 返回 0 字节（必填） |
| `POST /STU_BaseInfoAction.do` | POST(表单) | `hidOption=InitData`、`menucode_current` | **XML** 学籍档案（GBK） | ✅ |
| `POST /STU_BaseInfoAction.do` | POST(表单) | `hidOption=CHECKPHOTO` | 证件照校验/流 | ✅ |
| `POST /STU_DynamicInitDataAction.do` | POST(表单) | `classPath={页面 ClassPathLoader 提供的路径}` | XML 初始化数据 | ✅ |
| `POST /kbbp/dykb.bjkb_data.jsp` | POST(表单) | `xnxq`/`xn`、`hidXQ`、`hidNJ`、`hidBJDM`、`hidPYCC`、`hidYXB`、`hidZYDM` | HTML 班级课表 | ➖ |
| `GET /jw/output/excel.action?exptype=ksapb\|bjkb…` | GET | `exptype` 导出类型 | Excel 文件流 | ➖ |
| `POST /STU_ElectCourseResultAction.do` | POST(表单) | `hidOption=cancel`（退选，**写入**）+ 课程参数 | JSON/HTML | ➖ |
| `GET /student/wsxk.zxjg_all.jsp?xh=学号` | GET | `xh` | HTML 全部选课结果 | ➖ |
| `POST /jw/common/setYearTerm.action` | POST | 学期代码 | 设置当前学期（框架用） | ➖ |
| `GET /frame/normalusemenu/getBreadcrumbsInfoNew.action?menucode=` | GET | `menucode` | 菜单位置 JSON | ➖ |

其他学生可访问 `.do` 端点：`STU_MinorInfoAction.do?hidOption=isBInfo`（辅修）、`XJ_SetModifiableNjzyAction.do?hidOption=validate_student`（信息修改窗口）、`AdjustElectiveStudentForOucAction.do`、`docmanager/showFileInfos.action`、`docmanager/fileDownload.action`（制度文件）、`common/popmsg/popmsg.*`（站内消息）、`frame/desk/showUserModifyPassword4PostLogin.action`（修改密码）。

> PC 端另有 **46 个学生功能页**（`/student/**`、`/kbbp/**`，GET，需会话）。它们返回 HTML 页面而非 JSON API，完整清单见 `教务/API_FINAL.md §3.3` 与 `教务/APIDOC.MD §4.1`，本文不重复。

## 1.7 错误处理与调用约束

| # | 场景 | 识别特征 | 处理建议 |
|---|---|---|---|
| 1 | 未登录/会话失效 | 200 + JS `alert('凭证已失效…')`；或 HTML「错误:无效访问请求」；或 JSON `{type:"noLogin"/"invalidSession"}` | 重新走 CAS；勿只看 HTTP 状态码 |
| 2 | 页面/接口未部署 | HTTP 404 + 「错误:页面不存在」 | 功能未开放（`MyWjdc`、`MyWjdckc_jg`、`getBzrpj*`、`xqerWorkflowShBack` 等） |
| 3 | 参数缺失/非法 | 200 + HTML 错误页；空响应体（如 `ksapb_date` 缺 `kslc`）；JSON 空对象/空数组；个别接口挂起 | 补参；空数组不是错误；统一 15~30s 超时 |
| 4 | CAS 密码错误 | 登录页 HTML 含 `errormessages` / `id="msg"` | 解析文案；连续失败触发验证码 |
| 5 | CAS 盐/execution 过期 | 提交后返回新登录页且无错误文案 | 重新 GET 登录页再提交 |
| 6 | authserver 无 UA | **502** | 必须携带浏览器 User-Agent |
| 7 | 喜鹊儿/企微 wap 登录 | `loginverify_NODES` → `{"flag":"-1"}`；`loginfirst_H5`/`jwlogin_H5` → `{"flag":"1","msg":"该用户不存在！"}` | 本部署不开放该通道，一律走 CAS |
| 8 | 工作流参数不足 | `xqerWorkflow*` 业务错误 JSON | 先调 `xqerWorkflowGetLcid` / `Template` 获取 `lcid` 与表单 |
| 9 | 权限不足（教师功能） | 空 `resultSet`/`result` 或错误 JSON | 本文已剔除教师端入口 |
| 10 | H5 param2 校验失败 | 200 + 「错误:无效访问请求」 | 检查 `of_encrypt`/`getMd5_2` 与 `G_ENCRYPT` |
| 11 | 选课未开放 | 业务 JSON `flag/msg` 提示未开放 | 按 msg 展示，勿重试提交 |

## 1.8 已剔除（教师 / 辅导员 / 管理端）接口

按需求，以下模块**未写入 §1.4 总表**。共 **92 个端点**：255 − 92 = **163** 个学生端接口。若后续需要教师/辅导员端，可按模块名回查 `教务/APIDOC.MD`。

| 模块 | 端点数 | 角色 / 原因 |
|---|---|---|
| `bsdt_sh` | 8 | 办事大厅**审核**（审批端，非学生申请页） |
| `cltksq` | 9 | 调课处理（教务 / 教师） |
| `h5bkesq` | 8 | 补课申请（教师） |
| `h5bysjcjlr` | 5 | 毕业设计成绩录入（教师） |
| `h5ckcjTea` | 4 | 教师查学生成绩 |
| `h5jkap` | 3 | 监考安排（教师） |
| `h5tinksq` | 8 | 停课申请（教师） |
| `h5tksq` | 16 | 调课申请（教师） |
| `h5xsxxcx` | 13 | 学生信息查询（辅导员视图） |
| `hjcjlr` | 5 | 环节成绩录入（教师） |
| `jxgzl` | 2 | 教学工作量（教师） |
| `kcjl` | 4 | 考场记录 / 监考（教师） |
| `xqbd` | 5 | 学期报到（辅导员视图） |
| `h5ksap` 中的 `ksap_addCustomer`、`ksap_delCustomer` | 2 | 教师登记/删除监考（同模块的 `getAllExam` 等 3 个学生接口已保留） |

> 保留但需注意的模块：`h5bzrpj`（班主任评价，本部署 404）、`wspjwjdc`（问卷调查，本部署 404）、`h5sxpy`（实习评优，部分查询接口可能需要更具体的年级/专业参数）。这些接口不涉及教师端登录，仍列在总表中并标注实测状态。

---

# 2. 洗澡 App（大白U帮 · 原生接口）

> 本项目洗澡只接入**原生接口** `lz.hydream.cn`；App 内另一套 Flutter `dabai.hydream.cn` 接口不纳入本表。

## 2.1 基础信息

| 项 | 值 |
|---|---|
| Base URL | `http://lz.hydream.cn/api/v1/`（登录响应 `service_host` 可覆盖） |
| 使用界面 | 原生 `HomeContent`、`OriginFragment`、`LineUpFragment`、`ServicingFragment` |
| 认证 | `header.token` = 登录响应 `LoginEntity.token`（App 存 SharedPreferences `TOKEN_ID`） |
| 协议 | `POST` 表单，唯一字段 `ztechMsg`；内层 DES + SHA-1，可 zlib |
| 设备类型键 | `faucet`（超级澡堂），不是 `shower` |
| 核心入口 | `DeviceAreaApi/selectDevicesByAreaId`、`DeviceApi/turnOn`、`DeviceApi/turnOff`、`BusApi/exchangeMsgGz` |
| 接口数量 | **113** 个 |

> 原生 token 与手机号/密码不是一回事，只从 `LoginEntity.token` 取；`service_host`/`service_uri` 可能把后续请求切到校区专属域名。
> 旧的 `sw.hydream.cn/api_v1/Bathroom/*` 常量未被当前洗浴 UI 引用，不要按它开发。

## 2.2 协议

### 2.2.1 基础

| 项 | 值 |
|---|---|
| Base URL | `http://lz.hydream.cn/api/v1/`（登录响应 `service_host` 可覆盖） |
| 方法 | 全部 `POST`，`application/x-www-form-urlencoded` |
| 唯一表单字段 | `ztechMsg`，值为**内层** `{"head":{...},"data":"..."}`（不要多包一层） |
| 请求加密 | DES/CBC/PKCS5Padding，key `<DES_KEY>`，IV `11111111`，Base64 |
| 签名 | `sha1(msg_id + uuid + length + "<SHA1_SALT>" + data)` |
| 响应加密 | 同样 DES/CBC；`head.enc=GZ` 时先 **zlib inflate**（不是 raw deflate） |
| 业务成功码 | `data.code == 200` |
| 未登录 | `header.auth="00001"`，常见 `data.code=90` |

> `<DES_KEY>` 与 `<SHA1_SALT>` 是占位符，具体值以 `洗澡/docs/shower-api/native-api.md`、`洗澡/apidoc.md` 中的注入值为准，本文不展开。

### 2.2.2 请求结构

```text
POST http://lz.hydream.cn/api/v1/DeviceApi/turnOn
Content-Type: application/x-www-form-urlencoded

ztechMsg={"head":{...},"data":"<DES-Base64>"}
```

内层 `head`：

```json
{
  "verify": "sha1(msg_id + uuid + length + \"<SHA1_SALT>\" + data)",
  "msg_id": "1793802359_881682",
  "length": 180,
  "uuid": "e58d984c-920d-4091-9011-130bc1dae4a5",
  "enc": "GZ"
}
```

- `msg_id`：`%010d` 秒级时间戳 + 6~7 位随机数；服务端返回带 `_` 的形式。
- `length`：非 GZ 时是明文 JSON 的 UTF-8 字节数；GZ 时是转义后字符串字符数。
- `uuid`：设备 UUID（`Build` 派生）；未登录探测用随机 UUID 也能通过。
- `enc`：仅压缩请求带 `"GZ"`；压缩用 zlib（`Deflater` 默认包装）。

内层 `data` 加密前的明文：

```json
{
  "data": { "uuid": "demo", "deviceType": "faucet" },
  "header": {
    "device_type": "android",
    "source_sys_version": "14",
    "source_version": "2.2.1",
    "source_model": "Pixel",
    "token": "<TOKEN_ID>",
    "act": "turnOn"
  }
}
```

- `act` 是**方法名**（`turnOn`、`queueUp`、`getOrgs`、`bathBeginOrder`），不是路径。
- `token` 来自登录响应 `LoginEntity.token`（SharedPreferences `TOKEN_ID`）。
- 真机抓包会带 `mobile`/`pwd`/`version_code`/`client_type`/`device` 等共享字段，**不要照抄**；服务端接受只带必要字段的干净请求。

### 2.2.3 响应结构

```json
{
  "head": {"msg_id":"...","uuid":"SERVER","length":185,"ver":"3.0","verify":"..."},
  "data": "<DES-Base64>"
}
```

解密后：

```json
{
  "header": {"auth": "00001", "error": "PgelCtrlException", "errorMsg": "您尚未登陆"},
  "data": {"code": 90, "msg": "您尚未登陆", "data": [], "time": 1793802359}
}
```

`code == 200` 成功；`100` 也被客户端当作成功；`100127` 走特殊失败分支。

## 2.3 接口总表（113 个）

> 路径前缀统一为 `http://lz.hydream.cn/api/v1/`；全部 `POST`；`data` 为内层业务字段。
> 认证：「需 token」= `header.token`；「登录前」= 不需要；「?」= 文档未恢复请求构造。

### 2.3.1 UserApi — 登录 / 注册 / 用户 / 钱包（15）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `UserApi/addUserFace` | `addUserFace` | `image` | 需 token | 上传人脸 |
| `UserApi/fastLogin` | `fastLogin` | `mobile`, `verifyCode` | 登录前 | 短信验证码登录 |
| `UserApi/forgetPassword` | `forgetPassword` | `mobile`, `verifyCode`, `pwd` | 登录前 | 找回密码 |
| `UserApi/getFaceUrl` | `getFaceUrl` | — | 需 token | 获取人脸 URL |
| `UserApi/getSetting` | `getSetting` | —（真机带 `orgAreaId=57`） | 需 token | 校区配置 |
| `UserApi/getUserAuthcode` | `getUserAuthcode` | — | 需 token | 用户授权码 |
| `UserApi/getWalletInfo` | `getWalletInfo` | — | 需 token | 钱包（`balance`/`available_balance` 等） |
| `UserApi/loginWidthPwd` | `loginWidthPwd` | `pwd`, `mobile` | 登录前 | 账号密码登录；`pwd` 明文；返回 `LoginEntity` |
| `UserApi/oneClickLogin` | `oneClickLogin` | `loginToken` | 登录前 | 运营商一键登录 |
| `UserApi/reg` | `reg` | `pwd`, `mobile`, `verifyCode` | 登录前 | 注册 |
| `UserApi/sendSMSCode` | `sendSMSCode` | `code_type`, `mobile` | 登录前 | `code_type=login/reg` |
| `UserApi/setFaceUrl` | `setFaceUrl` | `faceUrl` | 需 token | 设置人脸 URL |
| `UserApi/unregister` | `unregister` | `pwd`, `types`, `reason` | 需 token | 注销账号 |
| `UserApi/updateAuthCode` | `updateAuthCode` | `lastTwo`, `expireDays` | 需 token | 更新授权码 |
| `UserApi/upgradeApp` | `upgradeApp` | `client_type`, `device`, `version_code` | 可选 | 版本检查 |

登录成功 `data.data` 为 `LoginEntity`：`token`、`id`、`mobile`、`user_type`、`user_status`、`user_nickname`、`avatar`、`default_org_id`、`default_org_area_id`、`default_building_id`、`default_device_area_id`、`default_device_area_name`、`user_oauth`、`service_uri`、`service_host`。`service_host`/`service_uri` 可能切换后续 Base URL，客户端需保存并替换。

### 2.3.2 OauthApi — token / 推送 / 上传授权（3）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `OauthApi/getPushAuth` | `getPushAuth` | `package_name` | 可选 | 推送授权 |
| `OauthApi/getToken` | `getToken` | — | 登录前 | 获取 OAuth token |
| `OauthApi/getUploadFileToken` | `getUploadFileToken` | `package_name` | 可选 | 上传文件 token |

### 2.3.3 MemberApi — 会员资料 / 设备信息 / 实名（9）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `MemberApi/changBaseInfo` | `changBaseInfo` | `birthday, sex, signature, user_nickname, grade_current, grade_length, id_card_type, id_card, real_name, mobile, userInfo` | 需 token | 修改资料 |
| `MemberApi/changeMobile` | `changeMobile` | `from_mobile, pwd, to_mobile, verifyCode` | 需 token | 换绑手机号 |
| `MemberApi/changePassword` | `changePassword` | `mobile, old_pwd, changed_pwd` | 需 token | 修改登录密码 |
| `MemberApi/getDeviceInfo` | `getDeviceInfo` | `mobile` | 需 token | 已绑定设备信息 |
| `MemberApi/getOtherAuthInfo` | `getOtherAuthInfo` | `mobile` | 需 token | 其他授权信息 |
| `MemberApi/getUserBaseInfo` | `getUserBaseInfo` | `mobile`（真机另带 `deviceTypeKey`/`orgAreaId`） | 需 token | 用户资料 |
| `MemberApi/setAvatar` | `setAvatar` | `mobile, avatar` | 需 token | 设置头像 |
| `MemberApi/setDeviceInfo` | `setDeviceInfo` | `mobile, default_org_id, default_org_area_id, default_building_id, default_device_area_id, deviceInfo` | 需 token | 设置默认校区/楼栋/区域 |
| `MemberApi/validateIdNo` | `validateIdNo` | `onecard_username, mobile, idcard, idtype, proof_params` | 需 token | 身份证/一卡通校验 |

### 2.3.4 DeviceAreaApi — 学校 / 楼栋 / 区域 / 排队（17）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `DeviceAreaApi/cancelQueue` | `cancelQueue` | `deviceAreaId, deviceTypeKey` | 需 token | 取消排队 |
| `DeviceAreaApi/deviceSelector` | `deviceSelector` | `org_type` | 需 token | 组织/设备选择器 |
| `DeviceAreaApi/getBuildings` | `getBuildings` | `orgAreaId` | 需 token | 楼栋列表 |
| `DeviceAreaApi/getDeviceArea` | `getDeviceArea` | `orgAreaId, buildingId` | 需 token | 楼栋下设备区域 |
| `DeviceAreaApi/getDeviceAreaById` | `getDeviceAreaById` | `deviceAreaId` | 需 token | 区域详情（int/string 两种重载） |
| `DeviceAreaApi/getMyQueueInfo` | `getMyQueueInfo` | `deviceTypeKey` | 需 token | 我的排队；空闲时 `data` 为空字符串 |
| `DeviceAreaApi/getOrgAreas` | `getOrgAreas` | `orgId` | 需 token | 校区/组织区域 |
| `DeviceAreaApi/getOrgs` | `getOrgs` | `org_type, org_name` | 需 token | 学校/组织 |
| `DeviceAreaApi/getStateByDeviceType` | `getStateByDeviceType` | `deviceTypeKey` | 需 token | 排队/设备/剩余时间/远程控制/用户状态 |
| `DeviceAreaApi/queueChange` | `queueChange` | `oldDeviceAreaId, deviceAreaId, deviceTypeKey` | 需 token | 换队 |
| `DeviceAreaApi/queueStateNoticed` | `queueStateNoticed` | — | ? | 无请求构造；疑似推送/轮询 |
| `DeviceAreaApi/queueUp` | `queueUp` | `deviceAreaId, deviceTypeKey` | 需 token | 加入排队 |
| `DeviceAreaApi/refreshState` | `refreshState` | `deviceTypeKey` | 需 token | 刷新设备状态 |
| `DeviceAreaApi/selectDeviceAreasWithDeviceState` | `selectDeviceAreasWithDeviceState` | `orgAreaId, deviceTypeKey` | 需 token | 区域 + 空闲统计 `idleNum/totalNum` |
| `DeviceAreaApi/selectDeviceTypeByOrgAreaId` | `selectDeviceTypeByOrgAreaId` | `orgAreaId` | 需 token | 校区下设备类型 |
| `DeviceAreaApi/selectDevicesByAreaId` | `selectDevicesByAreaId` | `deviceAreaId, deviceTypeKey` | 需 token | 设备列表；真机 area 875/faucet 返回 19 台 |
| `DeviceAreaApi/selectQueueCountByOrgAreaId` | `selectQueueCountByOrgAreaId` | `orgAreaId, deviceTypeKey` | 需 token | 区域排队人数 |

### 2.3.5 DeviceApi — 开关 / 预约 / 设备密码（8）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `DeviceApi/cancelReserve` | `cancelReserve` | `uuid, deviceType` | 需 token | 取消预约 |
| `DeviceApi/changeDevicePwd` | `changeDevicePwd` | `originalDevicePwd, inputDevicePwd` | 需 token | 修改设备密码 |
| `DeviceApi/getSoundInfo` | `getSoundInfo` | `deviceKey, orderSn` | 需 token | 设备提示音/订单 |
| `DeviceApi/reserve` | `reserve` | `uuid`, `version`（真机 `"55"`） | 需 token | 预约 |
| `DeviceApi/setDevicePwd` | `setDevicePwd` | `devicePwd` | 需 token | 设置设备密码 |
| `DeviceApi/showErrorCodes` | `showErrorCodes` | — | 需 token | 设备错误码表 |
| `DeviceApi/turnOff` | `turnOff` | `uuid, deviceType` | 需 token | 关闭洗浴 |
| `DeviceApi/turnOn` | `turnOn` | `uuid, deviceType`；预付费重载另带 `chargeType, prepaidAmount` | 需 token | 开启洗浴 |

### 2.3.6 KailuApi — 开鲁直连洗浴（3）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `KailuApi/bathBeginOrder` | `bathBeginOrder` | `kailuOrder` | 需 token | 开鲁直连开始洗浴 |
| `KailuApi/bathEndOrder` | `bathEndOrder` | `kailuOrder` | 需 token | 开鲁直连结束洗浴 |
| `KailuApi/syncAmount` | `syncAmount` | — | 需 token | 开鲁金额同步 |

### 2.3.7 BusApi — MQTT 设备状态轮询（2）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `BusApi/exchangeMsg` | `exchangeMsg` | `deviceAreaId, deviceTypeKey, lastId, isManual, usingMqtt` | 需 token | MQTT 消息交换（非 GZ） |
| `BusApi/exchangeMsgGz` | `BusApi/exchangeMsgGz` | `deviceAreaId, deviceTypeKey, lastId, isManual, usingMqtt` | 需 token | 真机实际状态轮询；`lastId` 递增 |

### 2.3.8 OrderApi / ConsumptionApi / CapitalApi（6）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `OrderApi/doubtAboutOrder` | `doubtAboutOrder` | `orderSn, reason` | 需 token | 订单申诉 |
| `OrderApi/getOutstandingOrder` | `getOutstandingOrder` | — | 需 token | 未完成订单；`data` 为 bool |
| `ConsumptionApi/consumptionLog` | `consumptionLog` | `mobile, uid, device_type_key, page, limit` | 需 token | 消费/订单明细；真机 10 条 |
| `ConsumptionApi/orderDetail` | `orderDetail` | `mobile, uid, order_item_id, order_sn` | 需 token | 消费订单详情 |
| `CapitalApi/balanceLog` | `balanceLog` | `uid, mobile, handle_type, search_begin, search_end, page, limit` | 需 token | 余额流水 |
| `CapitalApi/balanceLogDetail` | `balanceLogDetail` | `mobile, uid, log_id` | 需 token | 余额流水详情 |

### 2.3.9 BankrollApi — 退款 / 转账 / 充值卡（12）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `BankrollApi/allowRechargeMethod` | `allowRechargeMethod` | `uid, mobile` | 需 token | 可用充值方式 |
| `BankrollApi/allowRefundType` | `allowRefundType` | `mobile` | 需 token | 可用退款方式 |
| `BankrollApi/allowTransferType` | `allowTransferType` | `mobile` | 需 token | 可用转账方式 |
| `BankrollApi/applicationForRefund` | `applicationForRefund` | `uid, mobile, refund_type, hide_password_recovery_info` | 需 token | 申请退款 |
| `BankrollApi/doApplicationForRefund` | `doApplicationForRefund` | `uid, mobile, pwd, payeeAccount, refund_type, refund_balance, payeePlatform` | 需 token | 提交退款 |
| `BankrollApi/getHandleType` | `getHandleType` | `mobile` | 需 token | 处理方式 |
| `BankrollApi/getRefundList` | `getRefundList` | `page, limit` | 需 token | 退款列表 |
| `BankrollApi/getRefundProgress` | `getWithdrawProgress` | `id` | 需 token | 退款进度 |
| `BankrollApi/improveUserPaymentInfo` | `improveUserPaymentInfo` | `capital_apply_id, payment_type, account_info` | 需 token | 完善收款信息 |
| `BankrollApi/rechargeCards` | `rechargeCards` | `uid, mobile` | 需 token | 充值卡 |
| `BankrollApi/transfer` | `transfer` | `from_mobile, capital_type, amount, to_mobile, vcode` | 需 token | 余额转账 |
| `BankrollApi/userCancelRefund` | `userCancelRefund` | `uid, mobile, pwd, refund_id` | 需 token | 取消退款 |

### 2.3.10 PaymentApi — 支付 / 充值（9）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `PaymentApi/creditAuth` | `creditAuth` | — | 需 token | 信用授权 |
| `PaymentApi/doRecharge` | `doRecharge` | `user_id, mobile, mode, recharge_id, idcard, pwd, onecard_username, proof_params, options` | 需 token | 充值下单 |
| `PaymentApi/getAllowedPayType` | `getAllowedPayType` | `orderSn` | 需 token | 订单可用支付方式 |
| `PaymentApi/getDepositInfo` | `getDepositInfo` | `deviceAreaId` | 需 token | 押金信息 |
| `PaymentApi/getRechargeInfo` | `getRechargeInfo` | — | 需 token | 充值信息 |
| `PaymentApi/getRechargePayType` | `getRechargePayType` | — | 需 token | 充值支付方式 |
| `PaymentApi/marketingPay` | `marketingPay` | `or_sn, mode` | 需 token | 营销支付 |
| `PaymentApi/orderPay` | `orderPay` | `orderSn, mode, pwd, options` | 需 token | 订单支付 |
| `PaymentApi/payDeposit` | `payDeposit` | `settlementAreaId, mode` | 需 token | 支付押金 |

### 2.3.11 CouponsApi / CardApi / MessageApi（7）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `CouponsApi/couponTransfer` | `couponTransfer` | `couponId, toMobile` | 需 token | 转赠优惠券 |
| `CouponsApi/getMyCouponList` | `getMyCouponList` | `page, state` | 需 token | 我的优惠券 |
| `CouponsApi/getMyCouponLogList` | `getMyCouponLogList` | `page` | 需 token | 优惠券流水 |
| `CardApi/bindCard` | `bindCard` | `cardId, remark` | 需 token | 绑定一卡通 |
| `MessageApi/messageDetail` | `messageDetail` | `mobile, msg_content_id` | 需 token | 消息详情 |
| `MessageApi/messageListByType` | `messageListByType` | `mobile, msg_sort` | 需 token | 按类型消息列表 |
| `MessageApi/messageType` | `messageType` | `mobile` | 需 token | 消息类型 |

### 2.3.12 FeedbackApi — 反馈 / 客服（6）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `FeedbackApi/feedback` | `feedback` | `type_id, content, imgs, phone, mobile, feedbackInfo` | 需 token | 提交反馈 |
| `FeedbackApi/feedbackAppraisal` | `feedbackAppraisal` | `appraisal, is_solved, relation_su_id, type_id, content, imgs, dialogInfo` | 需 token | 反馈评价 |
| `FeedbackApi/feedbackDetailList` | `feedbackDetailList` | `mobile, su_id` | 需 token | 反馈对话 |
| `FeedbackApi/feedbackDialog` | `feedbackDialog` | `mobile, relation_su_id, type_id, content, imgs, dialogInfo` | 需 token | 反馈追加对话 |
| `FeedbackApi/feedbackTypeList` | `feedbackTypeList` | `mobile` | 需 token | 反馈类型 |
| `FeedbackApi/myFeedbackList` | `myFeedbackList` | `mobile` | 需 token | 我的反馈 |

### 2.3.13 SettingApi / AdApi / AdmApi / ActApi（10）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `SettingApi/commonProblem` | `commonProblem` | `mobile` | 需 token | 常见问题 |
| `SettingApi/contactManager` | `contactManager` | `mobile` | 需 token | 联系管理员 |
| `SettingApi/getAdList` | `getAdList` | `orgAreaId` | 需 token | 广告列表 |
| `SettingApi/getInit` | `getInit` | —（真机带 `client_type/device/version_code`） | 可选 | 初始化配置 |
| `SettingApi/getManagerList` | `getManagerList` | `deviceAreaId` | 需 token | 管理员列表 |
| `SettingApi/registrationAgreement` | `registrationAgreement` | `mobile` | 需 token | 注册协议 |
| `settingApi/userInfoCollectionChecklist` | `userInfoCollectionChecklist` | — | 需 token | 个人信息收集清单 |
| `AdApi/getAd` | `AdApi/getAd` | `deviceAreaId, deviceTypeKey` | 需 token | 真机广告（`875`/`1`） |
| `AdmApi/getConfig` | `getConfig` | — | ? | 管理配置；无请求构造 |
| `ActApi/getList` | `getList` | — | ? | 活动列表；无请求构造 |

### 2.3.14 RankingListApi / UserTaskApi / HealthApi（6）

| 路径 | act | data 字段 | 认证 | 说明 |
|---|---|---|---|---|
| `RankingListApi/getOrderRankingList` | `getOrderRankingList` | — | 需 token | 消费排行 |
| `RankingListApi/getRankingListByType` | `getRankingListByType` | `mobile, rankType, page, limit` | 需 token | 按类型排行 |
| `RankingListApi/getRankingType` | `getRankingType` | `mobile, serviceType` | 需 token | 排行榜类型 |
| `UserTaskApi/latest` | `latest` | — | 需 token | 最新任务 |
| `UserTaskApi/status` | `status` | —（真机带 `deviceTypeKey`/`orgAreaId`） | 需 token | 任务状态 |
| `HealthApi/createLog` | `createLog` | `bodyTemperature` | 需 token | 体温/健康上报 |

## 2.4 典型流程

### 2.4.1 标准洗浴流程

```text
UserApi/loginWidthPwd {mobile, pwd}
  -> 保存 data.token 为 TOKEN_ID
  -> 保存 service_host / service_uri（如需切换校区域名）
  -> DeviceAreaApi/getOrgs {org_type:"school", org_name:""}
  -> DeviceAreaApi/getOrgAreas {orgId}
  -> DeviceAreaApi/getBuildings {orgAreaId}
  -> DeviceAreaApi/getDeviceArea {orgAreaId, buildingId}
  -> DeviceAreaApi/selectDevicesByAreaId {deviceAreaId, deviceTypeKey:"faucet"}
  -> (need_reserve=1 时) DeviceApi/reserve {uuid, version:"55"}
  -> DeviceApi/turnOn {uuid, deviceType:"faucet"}
  -> BusApi/exchangeMsgGz {deviceAreaId, deviceTypeKey:"faucet", lastId, isManual:0, usingMqtt:"1"}
  -> DeviceApi/turnOff {uuid, deviceType:"faucet"}
```

> `uuid` 用设备列表里的 `device_key`（每台唯一，如 `<controller_key>_faucet_01`）；`controller_key` 是整层共用。`need_reserve=1` 的设备未预约直接 turnOn 会返回 code 130，即使处于正常服务时段。预约倒计时以 `DeviceAreaApi/getStateByDeviceType` 的 `remainTime` 为准（`userState=reserved` 时每秒递减），不要写死 5/8 分钟。

排队：

```text
DeviceAreaApi/queueUp {deviceAreaId, deviceTypeKey}
  -> 轮询 DeviceAreaApi/getMyQueueInfo {deviceTypeKey}
  -> DeviceAreaApi/cancelQueue {deviceAreaId, deviceTypeKey}
  -> 或 DeviceAreaApi/queueChange {oldDeviceAreaId, deviceAreaId, deviceTypeKey}
```

预约：

```text
DeviceApi/reserve {uuid, version:"55"}
  -> DeviceApi/cancelReserve {uuid, deviceType}
```

开鲁直连：

```text
KailuApi/bathBeginOrder {kailuOrder}
  -> KailuApi/bathEndOrder {kailuOrder}
```

## 2.5 错误码

| code | 含义 | 触发 |
|---|---|---|
| 90 | 您尚未登陆 | 空 token 调 `DeviceAreaApi/getOrgs` |
| 102 | 手机号/账号或密码不正确 | `loginWidthPwd` 错误账号 |
| 139 | 设备不存在: device area ID: x | `queueUp` / `selectDevicesByAreaId` 传不存在区域 |
| 271 | 订单不存在 | `bathEndOrder` / `getSoundInfo` 传不存在订单 |
| 298 | 校区不存在 | `selectDeviceAreasWithDeviceState` 传不存在校区 |
| 130 | 您尚未预约任何设备 | `need_reserve=1` 的设备未 `reserve` 直接 `turnOn` |
| 100170 | 您没有正在使用的设备 | 无进行中订单时 `turnOff` |
| 100287 | 支付方式设置错误，请联系管理员 | `bathBeginOrder` 无有效支付方式 |
| 200 | 操作成功 | `cancelQueue` 有效请求 |

> 未登录调用 `turnOn` 时，服务端可能直接抛 PHP `Undefined variable: result`（500/业务 0），这不是协议错误。

---

# 3. 电费（完美校园 / 新开普）

## 3.1 上游基础

| 项 | 值 |
|---|---|
| Base | `https://xqh5.17wanxiao.com/smartWaterAndElectricityService` |
| 落地页 | `https://xqh5.17wanxiao.com/userwaterelecmini/index.html#/?params=<加密串>` |
| 学校编码 | 本校 `7268`（`config.json` 固化） |
| 服务命令 | `JBSWaterElecService`（本校；部分学校可省略） |
| Content-Type | `application/x-www-form-urlencoded` |

公共请求头：

```text
Content-Type: application/x-www-form-urlencoded
X-Requested-With: com.newcapec.mobile.ncp     ← 与 App 包名一致
Origin / Referer: https://xqh5.17wanxiao.com
User-Agent: … Wanxiao/5.8.34
```

> 该接口偶发 SSL EOF / 读超时（限流或抖动），客户端应内置重试（本项目 `_post` 重试 3 次）。

## 3.2 登录态链路

### 3.2.1 `GET /userwaterelecmini/index.html#/?params=<加密串>`

- 链接本身携带按用户生成的登录凭证 `params`；在完美校园 App「全部应用 → 智能水电」中获取。
- 作用：取得 `params`，供 `loginCheck` 换取登录态；查询前也可"热身"访问一次保持一致会话。

### 3.2.2 `POST /loginCheck` —— 用 `params` 换登录态

- 请求：`data=<params 原始值>`（**原始值勿 URL 解码**）。
- 响应：`{"resultdata": "<Base64>"}`。
- 解密：**Base64 → AES-128-ECB / PKCS7，密钥 `1234567812345678`**（硬编码）。
- 解密后 `body.data` 关键字段：

| 字段 | 用途 |
|---|---|
| `customerCode` / `customerid` | 学校编码 → 后续 `customercode` |
| `command` | 服务命令 → 后续 `command` |
| `outid` / `userid` | 账号 → `account` |
| `token` / `wxToken` | 支付流程用 |
| `name` / `schoolname` | 姓名 / 学校名 |

## 3.3 `POST /SWAEServlet` —— 业务接口（明文版，本项目使用）

**不需要登录**：无 token 校验，凭 `customercode` + `account` 即可调用（本项目按「学号直查」直连）。

通用报文（form）：

```text
param        = {"cmd":"<命令>", "account":"<学号>", "timestamp":"<yyyyMMddHHmmss>", …}
customercode = <学校编码，本校 7268>
method       = <命令>
command      = <该校命令，可空，本校 JBSWaterElecService>
```

通用响应：

```json
{"code_":0,"result_":"true","body":"<JSON字符串>"}
```

外层只代表网关通过，**业务结果在内层 `body`**（需二次解析）：

| 内层字段 | 含义 |
|---|---|
| `result` = `"0"` / 0 | 成功 |
| `_result` = false | 业务失败，`_message` 说明 |
| `-90004` | 该 `roomverify` 未绑定 |
| `-87` | `getroombyaccountnum` 房间户号查不到 |
| `-20005` | 命令字不存在 |
| `-90002` | 参数异常（如 `roomverifylist` 为空） |
| `-62001` | 必要请求参数缺失 |
| `-1` | 业务失败（如解绑时未绑定） |
| `8099` | 客户学校不存在（外层） |

### 3.3.1 已实现并实测的命令（13）

| cmd | 附加参数 | 作用 | 登录 |
|---|---|---|---|
| `h5_getstuindexpage` | `roomverify`（空=全部绑定房间） | **查余额/余量**：`modlist[]`（旧版 `detaillist[]`）中 `businesstype` 0=电费 / 1=冷水 / 2=热水；余额 `odd`，另有 `todayuse/price/devicename` | ❌ |
| `getbindroom` | 无 | 查账号绑定房间（`existflag=2` 时含 `roomlist[]`） | ❌ |
| `getstroomday` | `businesstype,roomverify,starttime,endtime,page,pagesize` | 每日读数（余额 `odd`、日用量 `dayuse`、累计 `sumse`、日期 `statdate`）；约 84 天窗口 | ❌ |
| `getzonelist` | 无 | 园区列表 | ❌ |
| `getbuildlist` | `zoneid` | 楼栋列表 | ❌ |
| `getfloorlist` | `buildid` | 楼层列表 | ❌ |
| `getroomnumlist` | `floorid` | 房间列表 | ❌ |
| `bindroom` | `zoneid,buildid,floorid,roomnum(=roomnum.id)` | **绑定房间（写）**；返回 `roomfullname/roomverify/existflag/detaillist/account_num` | ❌ |
| `getroombyaccountnum` | `account_num`（房间**户号**，非房间号） | 按户号反查房间四要素 | ❌ |
| `gettimeusedetail` | `roomverify, businesstype, startdate, enddate`（`YYYY-MM-DD`） | **区间每日用量**：`sumuse` + `dayuselist[]`（按日期升序） | ❌ |
| `getusedetail` | `roomverify, businesstype, yearmonth`（`YYYY-MM`） | **月度每日用量**：`monthuse` + `dayuselist[]`（按日期倒序） | ❌ |
| `unbindroom` | `roomverifylist`（必填；多个用英文逗号分隔） | **解绑房间（写）**；成功 `{"result":"0","message":"操作成功"}` | ❌ |
| `unbinduser` | `username`、`outid`（学号）、`userid`（`loginCheck` 内部 id，与 `outid` 不同）、`password`（查询密码 MD5 **大写**） | 解绑学号（账号整体退出智能水电） | ❌（需 `loginCheck` 提供 `userid`） |

> `roomverify` 格式：`<zoneid>-<buildid>--<floorid>-<roomid>`，例如 `102-22--112-11330`。
> `gettimeusedetail` / `getusedetail` 不登录、不加密，凭学校编码 + 学号即可，但房间须已绑定到该账号。

### 3.3.2 `h5_getstuindexpage` 响应中的用量字段（无需额外请求）

每个 `modlist[]` 还自带：`weekuselist[]`（本周每日，`date/use/weekday`）、`monthuselist[]`（历史月度，`yearmonth` 为点分隔 `"2026.09"`）、`sumbuy`（实测与 `odd` 同值）、`linestatus[]`（如「第一路一般送电」）、`isshowpay`（是否显示缴费入口）、`accode`（充值类型码，电费 `51101`）、`todayuse`、`price`、`devicename` 等。

### 3.3.3 H5 前端出现过的其余命令（未逐项验证）

`login`、`loginCheck`、`getLocalParam`、`geth5config`、`getbindmsg`、`userbind`、`changepwd`、`bindusertelephone`、`getalertsubinfo`、`getwxopenid`、`h5_getrechargelist`（充值记录）、`h5_userrefund`、`placeorder`、`getPayInfo`、`getorderdetail`、`getbackfarelist` / `getbackfareinfobyid`（退款）、`getexpplan`、`uploadplanresult`、`getrepairlist` / `getrepairdetail` / `saverepair` / `saverepairimg` / `getrepairimg`（报修）、`getthirdroomdayuse`、`writeLogs`、`GetNotiResult`、`getpwdconfig`。

> 绑定/解绑均为**写操作且上游不校验身份**：知道学号即可查房间，知道房间 id 即可绑房，解绑同理。对外部署必须自行加防护（内网 / 口令 / 学校 CAS / 操作日志），并只操作本人账号与房间。

## 3.4 `POST /SWAEEncryptServlet` —— 业务接口（加密版）

- 用途：与 `SWAEServlet` 同一套业务命令；App/H5 现行通道。
- 请求：`encryptData`（AES 加密的 `{param, customercode, method, command}` JSON）+ `randomStr` + `timestamp` + `method`。
- 响应：外层 `encryptData`（同法解密）→ 内层结构与 §3.3 相同。
- 密钥由**时间戳/随机串派生**（H5 内 `d(timestamp)`、响应 `d(timestamp, randomStr)`；**具体派生算法未逆出**）。
- `saverepairimg` 例外走明文 FormData。
- 旧明文 `SWAEServlet` 仍可用，本项目即走明文。

## 3.5 支付链路（未实现，仅供迁移参考）

> 来源：公开项目 `JustaSage/ZCST_FEE_Bot`（AGPL-3.0）+ 社区逆向，未在本项目验证。涉及真实资金，务必只做「生成支付链接 → 用户自行付款」，不托管支付密码。

充值类型 `txcode`：**51101 电费 / 51201 冷水 / 51301 热水**。

| 步骤 | 接口 | 登录 | 作用 |
|---|---|---|---|
| 0（备选） | `POST {学校CAS}/v1/tickets` → `{TGT}` → `GET {SERVICE_URL}&ticket=…` → `POST hub.17wanxiao.com/bsacs/redirect.action` | 学校统一认证账号密码 | 自动换取落地页链接（`flag=cassso_*` 为学校专属值）；本项目走手动复制链接 |
| 1 | `POST .../smartWaterAndElectricityService/goPay`（`cmd=getPayInfo`） | `loginCheck` 的 `token`/`wxToken` | 下单，返回 `body.data = {url, jsonData, sign}` |
| 2 | `GET {url}?jsonData=&sign=`（禁止跟随 302） | 同上 | 从 `Location` 提取 `uuid` |
| 3 | `POST https://cloudpaygateway.59wanmei.com:8087/paygateway/smallpaygateway/trade`（`getPayInfoByUuid`） | `uuid` | 查询支付单 |
| 4 | `POST .../paygateway/paytrans/gateway`（`callWapCashDeskData`） | 同上 | 组装收银台数据 |
| 5 | `POST {学校收银台}/WapCashDesk/prepayOrder`（`paywayid=9711` 支付宝 WAP） | `wxToken` | 生成支付宝支付链接 `paymsg.request_url` |
| 6 | `POST https://openapi.alipay.com/gateway.do` → 302 收银台 → `alipays://` scheme | 用户在支付宝确认 | 实际付款 |
| 7 | 轮询 `h5_getstuindexpage` | ❌ | 到账监控（余额增加即成功） |

## 3.6 官方开放平台（需商务对接）

- Base：`https://api.17wanxiao.com/`；文档：`open.17wanxiao.com` / `open.wanmeiqiye.com/kdword_*.html`。

| 接口 | 作用 |
|---|---|
| `POST /api/1/wx/recharge/apply` | 三方支付平台向一卡通系统发起充值付款申请 |
| `POST /api/1/wx/recharge/submit` | 充值结果回执通知 |
| `POST /api/1/wxpay/gateways` | 获取可用支付方式（微信/翼支付等） |

- 认证：`access_token` + `app_id` + `pay_code` + `norce_str` + **签名**；`app_id` / `pay_code` 由新开普在商务接入时分配，个人/学生无法自助申请。

## 3.7 其他已知域名

| 端点 | 说明 |
|---|---|
| `https://apipay.17wanxiao.com/PayService` | App 自身支付/缴费服务入口（未调用） |
| `server.17wanxiao.com` / `share.17wanxiao.com` / `opentest.17wanxiao.com` | 主服务 / 分享 / 开放测试域 |
| `.../bleUtil` | 蓝牙（电表）相关工具接口，未使用、未验证 |

## 3.8 本地封装 API（`wanxiao-query` / `webapp.py`，非上游）

> 本项目自己提供的 HTTP 服务，默认 `http://127.0.0.1:8000`，**全部无需登录**。它把上面的上游命令封装成 JSON，便于网页/脚本调用。若你的 App 直接调上游，可跳过本节。

| # | 接口 | 方法 | 关键参数 | 作用 |
|---|---|---|---|---|
| 1 | `/`（`/index.html`） | GET | — | 查询页面（学号 → 房间列表 → 明细） |
| 2 | `/stats`（`/stats.html`） | GET | `?month=YYYY-MM` | 统计页面 |
| 3 | `/api/config` | GET | — | 返回 `{"customercode":"7268","command":"JBSWaterElecService"}` |
| 4 | `/api/stats` | GET | `?month=YYYY-MM` | 统计聚合（只读 SQLite，不打上游、不含学号） |
| 5 | `/api/all` | GET | `account` | 该学号全部已绑定房间余量（含本周/月度汇总、线路状态） |
| 6 | `/api/rooms` | GET | `account` | 绑定房间列表；每间附电费余额 |
| 7 | `/api/tree` | GET | `account`、`level`∈`zone/build/floor/roomnum`、`parent` | 绑定向导：园区→楼栋→楼层→房间 |
| 8 | `/api/usage` | GET | `account`、`roomverify`、`startdate`、`enddate` | 区间每日用量 + 每日剩余电量 |
| 9 | `/api/month` | GET | `account`、`roomverify`、`yearmonth` | 月度每日用量 |
| 10 | `/api/balance` | GET/POST | `account`、`roomverify` | 单房间明细（余额 + 本周/月度 + 线路状态） |
| 11 | `/api/bind` | POST | `account`、`zoneid`、`buildid`、`floorid`、`roomnum` | **绑定房间（写）** |
| 12 | `/api/unbind` | POST | `account`、`roomverify` | **解绑房间（写）**；先核对归属 |

通用参数 `cache=1`：只读缓存、不打上游（适用 `/api/all`、`/api/rooms`、`/api/tree`、`/api/usage`、`/api/month`、`GET|POST /api/balance`；写操作不支持）。前端每次并行发「`cache=1` 秒出缓存」+「无 `cache` 强制回源」两个请求，即为命中回源。

---

# 4. 统一接入建议

## 4.1 凭证与调用矩阵

| 场景 | 第一步 | 凭证 | 后续 |
|---|---|---|---|
| 教务 | CAS 登录（UA 必带） | `JSESSIONID` | 直接调 `/wap/*.action`；PC 另加 `Referer` |
| 洗澡原生 | `loginWidthPwd`（`pwd`） | `LoginEntity.token` | 放进 `header.token`；`service_host` 可换域名 |
| 电费查询 | 无需登录 | 学校编码 `7268` + 学号 | `SWAEServlet` + `JBSWaterElecService` |
| 电费支付 | 完美校园「智能水电」链接 | `params` → `loginCheck` 的 `token`/`wxToken` | `goPay` → 收银台 → 支付宝 |

## 4.2 通用工程约定

- **超时**：教务 15~30s（个别接口会挂起）；电费上游偶发 SSL EOF，建议 3 次重试；洗浴原生状态轮询（`BusApi/exchangeMsgGz`）需处理断线与 `lastId` 递增。
- **重试**：GET/查询类可重试；所有「提交/取消/删除/支付/开关/排队」不可自动重试，必须二次确认与防抖。
- **响应判断**：教务不能只看 HTTP 200；洗浴看 `code==200`；电费看内层 `body.result`。
- **编码**：教务 PC 返回 GBK；H5/洗浴/电费为 UTF-8。
- **空数据**：教务空数组/空对象是正常结果；电费未绑定房间返回空 `rooms`；洗浴空闲 `data: []` 或空字符串。
- **写入类**：本文学生端总表中的 31 个教务写入接口、洗浴开关/排队/预约/支付/充值、电费绑定/解绑/支付，均需在真实账号上先小流量验证。

## 4.3 安全与合规

1. 只使用本人有权使用的账号、校园与设备；不要绕过授权或访问他人数据。
2. 不要把 `JSESSIONID`、`LoginEntity.token`、`params`、`loginCheck` 结果写入日志或前端明文存储。
3. 电费明文通道不校验身份，公开部署必须加网关防护；绑定/解绑只允许本人房间。
4. 支付链路只生成支付链接，由用户自行在支付宝确认；不要托管支付密码。
5. 洗浴 DES key / 签名盐是客户端内置常量，可能随 APK 升级变化；升级后需重新提取。

---

# 5. 来源文件

| 文件 | 内容 |
|---|---|
| `教务/API_FINAL.md` | 教务最终合并版（CAS / H5 / PC / 错误处理） |
| `教务/APIDOC.MD` | 教务 255 个 H5 接口总表 + PC 功能页 |
| `教务/apidetail.md` | 教务逐模块明细、PC 接口、写入风险、教师端清单 |
| `教务/king.md` | Kingo 工具 13 端点实测明细 |
| `洗澡/apidoc.md` | 原生 `lz` 113 接口总表 |
| `洗澡/docs/shower-api/native-api.md` | 原生 `lz` 协议、字段、错误码、真机抓包 |
| `电费/API.md` | 完美校园上游命令、登录态、支付链路、本地封装 |
| `电费/DESIGN.md` | 电费项目设计与数据流（背景参考） |