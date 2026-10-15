# 造物集后端（coc-platform-backend）— 试运行版 MVP

全新工程（零继承旧 zaowuji 代码/表），端口 **8090**，产物 `target/coc-platform-backend-0.1.0.jar`。

- 技术栈：Java 21 + Spring Boot 3.5.16 + MyBatis 3.0.5（注解 SQL）+ Flyway 11 + MySQL 8 + spring-boot-starter-websocket
- 认证：JWT（jjwt 0.12.6，HS256）+ BCrypt（仅 spring-security-crypto，不引整个 Spring Security）+ `token_version` 换发失效
- 契约：`../contract/api-mvp.md`（字段/路径以契约为准，本文档「与契约的差异」章节列出新增/偏差）

## 1. 怎么跑

```cmd
:: 1) 打包
cd E:\personalProjects\coc-platform\backend
run-dev.cmd package

:: 2) 启动（自动从 C:\Users\Administrator\.tinker\db_password.txt 注入 COC_DB_PASSWORD）
run-dev.cmd

:: 或开发模式（mvn spring-boot:run）
run-dev.cmd mvn
```

- 数据库：`jdbc:mysql://127.0.0.1:3306/coc_platform?createDatabaseIfNotExist=true&...`，账号 `root`，
  密码只从环境变量 `COC_DB_PASSWORD` 读（`application.yml` 里写的是 `${COC_DB_PASSWORD:}`，**仓库内不含任何密码**）
- 首次启动 Flyway 自动建库建表（已在**空库**上验证：`coc_fresh_check` → `1 | init | success=true`，26 张业务表 + flyway_schema_history）
- 健康检查：`curl http://127.0.0.1:8090/api/v1/system/health`

## 2. 存储根（`coc.storage.root`，本机 `E:\personalProjects\coc-platform\.data`）

```
.data/
  projects/{project_id}/v{n}/source/   # 源码（html 的 zip 会解压到这里，便于沙箱运行）
  projects/{project_id}/v{n}/build/    # scratch 打包产物（由 8091 打包服务写入）
  covers/                              # 封面
  avatars/                             # 头像
  uploads/tmp/                         # 合并后的临时文件
  uploads/staging/{upload_session_id}/ # 分片 part-1..N
```

## 3. 接口清单（全部实现，统一响应 `{"code":0,"message":"ok","data":...}`）

### 3.1 认证 / 系统（公开，除标注）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/system/health` | 健康检查（含 db 状态） |
| POST | `/api/v1/auth/dev-apply` | 开发者申请 → `user(status=0)` + `developer_profile(0)` |
| POST | `/api/v1/auth/login` | 仅 status=1 可登录；0 审核中 / 2 禁用 / 3 已注销 均 403 且带提示 |
| GET | `/api/v1/auth/me` | 🔒 当前用户（含 is_admin / must_change_password） |
| POST | `/api/v1/auth/change-password` | 🔒 改密；改完 token_version+1，返回新 token |
| POST | `/api/v1/auth/password/forgot` | 找回申请（管理员人工核验） |
| POST | `/api/v1/auth/logout` | 🔒 占位（JWT 无状态，前端清 token 即可） |

### 3.2 内容（访客只读；访客不写任何库）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/projects` | 列表：`page,size,category_id,project_type,keyword,sort(latest\|hot)`；只返回 status=2 且未删 |
| GET | `/api/v1/projects/{id}` | 详情（作者/管理员可看未发布；其他人 404） |
| POST | `/api/v1/projects/{id}/view` | 浏览 +1（写 `project_view_daily` + `project_view_hourly` + 总数；无用户维度） |
| GET | `/api/v1/projects/{id}/stats?range=` | 折线：**range=2 → 走小时表 48 点**；7/15/30 → 走天表；评论数动态算 |
| GET | `/api/v1/projects/{id}/run` | 运行信息 `{mode,project_type,sandbox,entry,url(绝对)}` |
| GET | `/api/v1/projects/{id}/comments` | 评论树（楼中楼一层，根评论分页 + replies） |
| GET | `/api/v1/categories` | 启用的分类 |
| GET | `/api/v1/users/{id}` | 个人主页（已注销 → 昵称「用户已注销」） |
| GET | `/api/v1/users/{id}/follow-status` | 关注状态 + 粉丝数 |
| GET | `/api/v1/agreements/current` | 当前用户协议（新增，注册页展示用） |
| GET | `/api/v1/run/{project_id}/**` | **新增**：沙箱静态资源（公开只读，优先 build 否则 source，防目录穿越） |

### 3.3 互动（**全部要求登录**，访客 401）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/comments` | 发评论/回复（`parent_comment_id`）；已发布项目才可评论 |
| POST | `/api/v1/comments/{id}/like` | 评论点赞开关 |
| POST | `/api/v1/projects/{id}/like` | 项目点赞开关 |
| POST | `/api/v1/projects/{id}/favorite` | 收藏开关 |
| POST | `/api/v1/users/{id}/follow` | 关注开关 |
| POST | `/api/v1/reports` | 举报（project/comment/user） |
| GET | `/api/v1/reports/me` | 我的举报 + `reward_points` + `unread` |
| POST | `/api/v1/reports/me/read` | 处理结果标记已读 |

### 3.4 投稿 / 文件
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/projects` | 🔒 建草稿（html→manual；scratch/structure→auto） |
| PUT | `/api/v1/projects/{id}` | 🔒 改草稿（仅作者，status 0/4） |
| POST | `/api/v1/projects/{id}/submit` | 🔒 html→待审(1)；scratch/structure→免审直发(2) 并建项目群 |
| POST | `/api/v1/projects/{id}/build/retry` | 🔒 重新触发 8091 打包 |
| GET | `/api/v1/users/me/projects` | 🔒 我的作品 |
| GET | `/api/v1/users/me/favorites` | 🔒 我的收藏（新增） |
| POST | `/api/v1/uploads/init` | 🔒 sha256 秒传（命中直接返回 project_file_id + instant=true） |
| POST | `/api/v1/uploads/{id}/parts` | 🔒 multipart `part_number` + `chunk`，乱序/续传 |
| GET | `/api/v1/uploads/{id}` | 🔒 进度（uploaded_chunks / missing_chunks） |
| POST | `/api/v1/uploads/{id}/complete` | 🔒 校验 sha256 → 落盘 `v{n}/source` → 建 version + project_file；scratch 触发打包 |
| GET | `/api/v1/files/{project_file_id}/content` | 🔒 **仅登录**，写 `operation_log`(download_source) |

### 3.5 IM
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/im/conversations` | 会话列表（单聊 + 我参与的项目群，含未读数） |
| POST | `/api/v1/im/conversations/direct` | **新增**：打开/创建单聊（契约未给入口，前端需要） |
| GET | `/api/v1/im/conversations/{id}/messages?cursor=` | **cursor 分页，每页 20**，返回 `next_cursor` |
| POST | `/api/v1/im/conversations/{id}/messages` | 发送（落库 + WS 推送 + 站内通知） |
| POST | `/api/v1/im/conversations/{id}/read` | 已读上报 |
| GET | `/api/v1/im/unread` | 未读汇总 |
| WS | `ws://127.0.0.1:8090/ws?token=<jwt>` | JSON 协议：收 `{"type":"hello"}`；发 `{"type":"ping"}` 收 `{"type":"pong"}`；新消息 `{"type":"message","session_id":..,"message":{...}}`；无/错 token → close 4001 |

### 3.6 管理端（`/api/v1/admin/**` 一律要求管理员，非管理员 403）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/projects` | 列表（status/keyword/include_deleted） |
| GET | `/api/v1/admin/reviews/queue` | 待审队列 |
| POST | `/api/v1/admin/reviews/{project_version_id}/approve` | 通过 → status=2 + 审核记录 + 建项目群 + 通知作者 |
| POST | `/api/v1/admin/reviews/{project_version_id}/reject` | 驳回 → status=4 + 原因 + 通知作者 |
| POST | `/api/v1/admin/projects/{id}/down` | 下架 → status=3 + 原因 |
| POST | `/api/v1/admin/projects/{id}/restore` | 恢复 → status=2 |
| DELETE | `/api/v1/admin/projects/{id}` | 软删 + **物理删代码目录** + 审计 |
| GET | `/api/v1/admin/projects/{id}/storage` | **服务器绝对存储路径**（仅管理员，写审计 `view_storage`） |
| GET | `/api/v1/admin/comments` / DELETE `/api/v1/admin/comments/{id}` | 评论列表 / 删任意评论 |
| GET | `/api/v1/admin/developer-applications` | 申请列表（status 过滤） |
| POST | `/api/v1/admin/developer-applications/{id}/approve\|reject` | 审批（通过 → user.status=1，可登录） |
| GET | `/api/v1/admin/reports` / POST `/api/v1/admin/reports/{id}/handle` | 举报列表 / 处理（`{action:resolve\|reject,result}`；resolve 写 notification 并给举报人加分） |
| GET | `/api/v1/admin/password-resets` | 找回申请列表 |
| POST | `/api/v1/admin/users/{id}/reset-password` | 生成一次性临时密码（24h、首登强制改密、旧 token 立即失效） |
| POST | `/api/v1/admin/users/{id}/cancel` | 注销（status=3 软删；作品保留、作者名回退「用户已注销」） |
| GET | `/api/v1/admin/users` | 用户列表（新增，按 QQ 号找人） |
| GET | `/api/v1/admin/stats/platform` | 平台统计 |
| GET | `/api/v1/admin/logs` | 审计日志（新增） |

## 4. 自测（脚本在 `tools/selftest/`，日志同级）

```cmd
cd backend\tools\selftest
powershell -ExecutionPolicy Bypass -File .\selftest-api.ps1     :: API 全链路 25 组
node .\selftest-ws.mjs                                          :: WebSocket（hello/ping-pong/推送/无 token 拒绝）
```

- `TestData.java`：`java -cp "<mysql-driver.jar>;<spring-security-crypto.jar>;<spring-jcl.jar>" TestData.java seed|cleanup`
  - seed → 直插临时管理员 **900000 / admin12345**（type=3, status=1）
  - cleanup → 删除自测账号 900001/900002 及其作品
- `FreshCheck.java`：`drop`（删临时库）/ `inspect`（看 flyway 记录），用于验证空库迁移
- 证据日志：`selftest-api.log`、`selftest-ws.log`、`freshdb-migration.log`

自测结果（全绿）：`mvn -q -DskipTests package` 成功 → health ok → 申请开发者 → 审核中登录 403 →
管理员登录 → 审批开发者 → 登录 → 建草稿 → init/parts/complete（秒传命中）→ submit(待审) →
列表看不到 → 管理员 approve → 列表看得到 → 详情 → 浏览 ×3 → 折线(7 天/48 小时) → 运行目录取回 index.html →
评论/回复/点赞 → 项目点赞/收藏/关注 → 举报 → 管理员处理(+5 积分) → 我的举报 → IM 单聊收发/未读/已读 →
下载（带 token 200 / 访客 401）→ 管理员存储路径/统计/日志 → 重置密码 → 临时密码登录 → 强制改密（旧 token 401）→
下架/恢复/注销（已注销账号登录 403、主页昵称回退）。

## 5. 与契约的差异 / 新增（重要）

1. **新增接口**（契约没写但前端/沙箱必需，均已在上面标注「新增」）：
   `GET /run/{project_id}/**`（沙箱运行静态资源）、`POST /im/conversations/direct`、
   `GET /users/me/favorites`、`GET /admin/users`、`GET /admin/logs`、`GET /admin/comments`、
   `GET /agreements/current`、`POST /auth/logout`、`GET /admin/reviews/queue`。
2. `POST /uploads/{id}/complete` **额外接受 `project_id` / `file_type` / `changelog`**：
   不带 project_id 也能完成会话，但不会登记 `project_file`（契约的 complete 无法从会话本身推出 project），
   **前端请在 complete 时带上 project_id**。
3. `reports/me` 的正反馈积分落在 `user.reward_points`（额度 `coc.report.reward-points`，默认 5），
   单条举报也记录本次奖励 `report.reward_points`。
4. 时间字段统一序列化为 `yyyy-MM-dd HH:mm:ss`（不是 ISO 带 T）。
5. 未发布的 scratch/structure 也会在「提交」时建项目群（kind=2，作者入群）。
6. 契约未要求、我**未实现**的：分类/标签的后台 CRUD（无对应契约接口）、封面/头像上传接口
   （`cover_path` / `avatar_path` 只作字段透传，前端可先留空）、全文/标签/作者多维筛选（只做了
   keyword/分类/类型/排序）、消息撤回、通知列表接口（`notification` 表已建并写入，无读取接口）。

## 6. 已知未做 / 偷懒的地方（诚实清单）

- 打包：`complete` 时 scratch 会 HTTP 调 `http://127.0.0.1:8091/pack`（超时 4s）。**8091 没起时优雅降级**：
  写 `build_task(status=0)` 并返回成功（日志 + `build_task.message` 说明），可调 `/build/retry` 重试。
  8091 的请求/响应体格式见 `UploadService.triggerBuild()`：请求 `{project_id, project_version_id,
  project_file_id, file_name, source_path, output_dir, file_type}`，成功需返回 `{ok:true}` 或
  `{"status":"ok"}`，可带 `output_path`。**这一格式需要与打包子会话对齐**。
- html 项目：只做 zip 解压到 `v{n}/source`（剥一层根目录、防穿越），没有做资源改写/校验；非 zip 的 html 单文件直接原样存放。
- 文件安全扫描（`scan_status`）恒为 1（未接入扫描），`structure` 类型仅落盘不做转换。
- 访问统计无防刷（每次调 `/view` 都 +1），无 IP/UA 维度（契约明确「无用户维度」）。
- IM 的未读数是实时子查询（MVP 够用，量大需缓存）；WS 只有文本帧、无鉴权重连/离线消息补偿（离线消息靠拉取）。
- 管理员没有「新建分类/协议版本」的接口（契约未列），分类/协议需要 SQL 种子。
- 依赖 `token` 明文放 query（WS 握手与下载链接）符合契约，但生产建议改一次性票据。
- 单测为零（只有端到端自测脚本）；异常分支靠人工 curl 覆盖。

## 7. 需要主会话/运维配合

1. **种子数据**（运维子会话）：至少 1 个真管理员（`type=3,status=1`，BCrypt 密码）、`category` 若干、
   `agreement_version` 当前版本；自测遗留：管理员 `900000/admin12345`、开发者 `900001/selftest123`
   （作品 1 个，已发布，标题 `selftest-html-project`）→ 可用 `TestData.java cleanup` 清掉 900001/900002，
   管理员 900000 建议改成正式账号或删除。
2. **打包服务 8091**：起好后调 `/build/retry` 验证 scratch 真能出包（用 `Admin` 或作者 token）。
3. **隧道/nginx**：本机 cloudflared 若要把 `/api/*` 指到 8090，需运维改 ingress（我没动任何隧道配置）；
   跨域白名单已放 `8085/8086`（`coc.cors.allowed-origins`）。
4. 前端对接两个易踩点：时间格式已是 `yyyy-MM-dd HH:mm:ss`；`/run/{id}/` 必须在 iframe 里以**同源**访问
   （后端已设 `X-Content-Type-Options: nosniff`，未设 CSP，方便沙箱跑）。
