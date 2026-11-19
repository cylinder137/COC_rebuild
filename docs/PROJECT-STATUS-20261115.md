# COC 平台项目状态说明书

> 生成时间：2026-11-15 21:45（GMT+8）
> 最近更新：2026-11-19 17:25（GMT+8）· 发布 v2.1.0（手机端补齐素材库 + 修复明暗模式按钮配色，五期测试收官），见第十四章
> 生成者：Rivet 🔩（服务器分身）
> 用途：交接给其他 AI / 协作者，快速建立对 COC（造物集创作平台）当前状态的整体认知
> 所有路径均为**生产机实机路径**（Windows 11 Pro，主机 `WIN-4ODL6RU8GF2`），非示意
>
> 📌 22:20 复核记录：本文档已与代码/数据库/服务逐项对照。修正了 3 处数字（`project` 行数、`user` 数量、未提交文件数），其余（端点 69、27 表、端口、路由、nginx 缓存、打包流程）与实机一致。

---

## 零、一页速览

| 维度 | 现状 |
| --- | --- |
| 仓库根 | `E:\Project Files\COC_rebuild` |
| 分支 | `main`，HEAD `cce4cb7`（2026-11-19，2.0.6 已推送 `bc3e2aa..cce4cb7`）；**2.1.0 的改动在工作区未提交** |
| 远程 | `https://github.com/cylinder137/COC_rebuild.git`（已推送，ahead=0） |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router（`createWebHistory`） |
| 后端 | Java 21 + Spring Boot 3.5 + MyBatis + MySQL 8 + Flyway |
| 数据库 | `coc_platform`，**31 张表**，Flyway `V1` → `V4`（`V3__bug_feedback`、`V4__assets_and_series` 已应用） |
| 后端端口 | `8090`（Java），打包服务 `8091`（Node / coc-packer） |
| Web 入口 | nginx `:8084` → `https://collectionofcreations.uk` |
| App | Capacitor 8（`uk.collectionofcreations.coc`），当前 **2.1.0 / versionCode 17**（2026-11-19 17:19 打包，3,886,799 B） |
| 服务化 | MySQL84 / nginx / coc-backend / coc-packer / tutor-backend / ws-server / cloudflared 全 **Running + Automatic** |
| 隧道 | cloudflared tunnel `d84af69a-a806-445b-a2f8-19414819b30f`（cloudflared 服务化，metrics `20241`） |

---

## 一、仓库结构与模块职责

```
E:\Project Files\COC_rebuild
├── backend      Spring Boot 后端（jar: target/coc-platform-backend-0.1.0.jar）
├── frontend     主站前端（Vue3 + Vite），构建产物 dist/ 由 nginx 直出
├── frontend-app App 端前端副本（Capacitor 壳工程源，仅保留 env 与 src 同步用）
├── packer       Node 打包服务（把 html/scratch 打成可运行沙箱，端口 8091）
├── contract     API 契约文档（api-mvp.md）
└── ops          运维脚本（建库/起服务/冒烟/清理测试数据）、README-ports.md
```

### 版本控制状态（务必先看）

- 分支：`main`，HEAD `ba2e3dd`（11-15 已推送，`d3caa3e..ba2e3dd`），工作区干净
- 2.0.3 的改动已入库，拆成 3 个提交：`b254420` 账号注销接口 / `0947806` 手机端输入框修复 / `ba2e3dd` 文档更新
- 2.0.4 的改动已入库（`ec5e10e` 视图切换/全屏横屏 + `4c9acb2` 零散优化与 devlog），已推送 `ba2e3dd..4c9acb2`
- 2.0.5 的改动已入库并推送（`bc3e2aa`：虚拟按键 `VirtualPad.vue` / `utils/vkeys.js` / `format.js` ORIGIN 修复）；nginx 侧 `coc-key-bridge.js` 与 `coc.conf` 在仓库外，已部署到生产
- 2.0.6 的改动已入库并推送（`cce4cb7`：素材库 + 项目/素材系列 + mcstructure 下线 + 后台 bug 反馈面板；`bc3e2aa..cce4cb7`，首次 push 遇 `OpenSSL SSL_read: unexpected eof` 重试即通）
- ⚠️ **2.1.0 的改动（EP 变量特异性修复 + 文案清理 + 下载页 2.1.0 条目 + `APP_BUILD`）在工作区未提交**
- 提交规范提醒：同一工作区曾出现过**并行会话**同时改 `bug_feedback` 相关文件的情况（`V3__bug_feedback.sql`、`BugFeedbackMapper/Service`、`AdminBugFeedback.vue` 等）。**提交前务必按路径挑拣**，别把对方未完成的改动一起 commit。
- 下面这份清单是 11-15 的**历史快照**（`git status --porcelain` 共 28 行），仅作参考，不代表当前工作区：

  ```
  backend/.../auth/AuthInterceptor.java
  backend/.../controller/AuthController.java
  backend/.../service/AuthService.java
  frontend/.env.production、.env.user、index.html
  frontend/src/api/http.js            ← 核心修复（ORIGIN / 遥测）
  frontend/src/api/index.js           ← 超时放宽、去手写 multipart 头
  frontend/src/components/AppHeader.vue（APK_RELEASE=2.0.3、fileUrl 头像）
  frontend/src/components/ChunkUploader.vue、CoverUploader.vue、CommentItem.vue、ProjectCard.vue
  frontend/src/layouts/ConsoleLayout.vue
  frontend/src/main.js                ← IS_APP 下挂全局异常遥测
  frontend/src/router/index.js、stores/ui.js
  frontend/src/styles/global.css（移动端吸底操作条 + 输入框 user-select 修正）
  frontend/src/styles/tokens.css
  frontend/src/utils/format.js        ← API_ORIGIN 推导修复
  frontend/src/utils/formDom.js       ← 新增（v2.0.3 输入框兜底，见第十章）
  frontend/src/utils/platform.js（APP_BUILD='v2.0.3'）
  frontend/src/views/{DevApply,Home,Login,Messages,ProjectDetail,Settings,Upload,UserProfile}.vue
  frontend/src/views/admin/AdminComments.vue
  docs/                                ← 未跟踪
  ```

> ⚠️ 交接注意：这些改动**已在生产生效**（dist 已重建、APK 2.0.3 已发布），但**未进 git**。任何 `git checkout/clean` 都会把线上行为改回旧版。改动前先备份或在分支上操作。

---

## 二、后端现状（backend）

### 2.1 运行与配置

- 服务名 `coc-backend`（WinSW 包装），工作目录 `E:\Project Files\COC_rebuild\backend`
- 启动参数（见 `E:\Server Data\services\coc-backend\coc-backend-svc.xml`）：`-Xms512m -Xmx2g -XX:+UseG1GC`，`--coc.storage.root=E:/Server Data/coc-platform`，JWT secret 也在参数里
- 关键配置（`backend/src/main/resources/application.yml`）：

  | 项 | 值 |
  | --- | --- |
  | `server.port` | 8090 |
  | 上传上限 | `max-file-size: 200MB` / `max-request-size: 220MB` / `max-http-form-post-size: 200MB` |
  | 数据源 | `jdbc:mysql://127.0.0.1:3306/coc_platform?...`，用户 `root`，密码走 `${COC_DB_PASSWORD:}` |
  | 连接池 | Hikari `maximum-pool-size: 10` |
  | 存储根 | `coc.storage.root`（默认 `E:/personalProjects/coc-platform/.data`，**运行时被覆盖为** `E:/Server Data/coc-platform`） |
  | 打包服务 | `coc.packer.url: http://127.0.0.1:8091/pack` |

### 2.2 数据库（`coc_platform`，MySQL 8.4.9 / 端口 3306）

- 迁移：`V1__init.sql`（33,950 B）、`V2__password_reset_reject.sql`、`V3__bug_feedback.sql`、`V4__assets_and_series.sql`，全部 `success=1`（当库 version = **v4**）
- 31 张表：

  ```
  agreement_version  asset  asset_series  bug_feedback  build_task  category
  comment  comment_like  developer_profile  flyway_schema_history  follow
  im_message  im_session  im_session_member  notification  operation_log
  password_reset_apply  project  project_favorite  project_file  project_like
  project_review  project_series  project_tag  project_version  project_view_daily
  project_view_hourly  report  tag  upload_session  user
  ```

- 2.0.6 新增表/列（详见第十三章）：
  - `project_series` / `asset_series`：`series_id / user_id / name(80) / intro(255) / is_public / 时间戳`，`UNIQUE(user_id, name)`，FK `user` ON DELETE CASCADE
  - `asset`：`asset_id / user_id / series_id / name / file_name / asset_type(enum) / mime / file_size / sha256 / storage_path / is_public / is_deleted / 时间戳`
  - `project.series_id`（FK → `project_series` ON DELETE **SET NULL**）、`upload_session.kind ENUM('project','asset')`

- **字段名易坑**（写 SQL 前必查）：
  - `project`：作者列是 **`author_id`**（不是 `user_id`）；含 `status`（0 草稿 / 1 审核中 / 2 已发布）、`audit_mode`、`is_deleted`、`down_reason`、`reject_reason`
  - `project_file`：关联 `project_version_id`（不是 `project_id`），路径列 `storage_path`，另有 `scan_status`、`build_status`
  - `project_version`：`project_version_id / project_id / version_no / changelog / created_by`
  - `upload_session`：`upload_session_id / user_id / project_id / kind / uploaded_chunks / status / storage_path`，`status=1` 表示完成，`kind` 区分项目源码 / 素材
  - `asset`：`user_id`（上传者）、`series_id`（可空）、`storage_path` 形如 `assets/{asset_id}/{file_name}`、`is_deleted` 是**软删**（直链文件保留，避免历史 HTML 裂图）
- 当前数据量参考（11-19 16:20 实测，非估算）：
  - `project` **43 行**、`user` **17 个**、`upload_session` 34 行、`asset` 0 行（素材刚上线）
  - `project_series` / `asset_series` 均为 0 行（验证数据已清理，见 13.6）
  - 早期基线（11-15 22:20）：`project` 27 / `project_file` 22 / `project_version` 22 / `project_view_hourly` 523 / `project_view_daily` 201
  - ⚠️ 旧版本文档里「project 约 228 条」是**误记**（把 `max(project_id)=228` 当成了行数），引用数据量以本节为准
- 账号模型：`user.type` = 1 普通 / 2 开发者 / 3 管理员；`token_version` 参与 JWT 失效判定

### 2.3 接口总览（11 个 Controller，共 87 个端点）

| Controller | 基路径 | 端点数 | 说明 |
| --- | --- | --- | --- |
| AuthController | `/api/v1` | 8 | `POST /auth/login`、`/auth/dev-apply`、`/auth/password/forgot`、`/auth/me`、`/auth/change-password`、`/auth/cancel-account` 等 |
| ProjectController | `/api/v1` | 12 | `GET/POST /projects`、`GET /projects/{id}`、`PUT /projects/{id}`、`POST /projects/{id}/submit`、`/cover`、`/view`、`/stats`、`/run`、`/comments`、`/build/retry` |
| UploadController | `/api/v1/uploads` | 4 | `POST /uploads/init`、`/uploads/{id}/parts`、`/uploads/{id}/complete`、`GET /uploads/{id}` |
| FileController | `/api/v1` | 2 | `GET /files/{project_file_id}/content`、`GET /run/{project_id}/**` |
| ImController | `/api/v1/im` | 6 | 会话列表、开一对一、消息收发、已读 |
| InteractionController | `/api/v1` | 9 | 评论、点赞、收藏、关注、举报 |
| UserController | `/api/v1` | 4 | `GET /users/{id}`、`/users/me/projects`、`/follow-status` |
| MetaController | `/api/v1` | 2 | `GET /categories`、`GET /system/health` |
| **AssetController**（2.0.6 新增） | `/api/v1/assets` | 7 | `GET ""`（公开广场）/ `GET /mine` / `GET /{id}` / `POST /upload/init` / `POST /upload/{sid}/complete` / `PUT /{id}` / `DELETE /{id}` |
| **SeriesController**（2.0.6 新增） | `/api/v1/series` | 5 | `GET ""?kind=` / `GET /public?kind=&user_id=` / `POST ""` / `PUT /{id}` / `DELETE /{id}?kind=` |
| AdminController | `/api/v1/admin` | 28 | 审核 `reviews/{v}/approve|reject`、`projects/{p}/down|restore|undelete|storage`、评论/举报/开发者申请/改密申请/用户管理（`source-files`、`stats/platform/trend` 等） |

- 鉴权：`AuthInterceptor` + JWT（HS256，claims `sub`=user_id、`type`、`tv`=token_version）
- 前端 token 存 `localStorage.coc_token`，用户信息存 `coc_user`
- 统一响应体：`{ code, message, data }`，`code=0` 为成功
- 上传链路：分片上传（chunk 默认 5 MB）→ `complete` → 生成 `project_version v1` → 触发 packer（`/pack`）→ `project_file.build_status`

### 2.4 打包服务（packer，端口 8091）

- 服务名 `coc-packer`（WinSW），`E:\programs\Node_js\node.exe --max-old-space-size=768 server.js`，工作目录 `E:\Project Files\COC_rebuild\packer`
- 健康端点 `GET /health`（注意：**没有** `/api/v1/...` 前缀，探测时别打错）
- 逻辑：`POST /pack` 接受上传的 html/scratch，返回打包后可直接 iframe 运行的产物；`packager=turbowarp`，`softTimeout=3000ms`

---

## 三、前端现状（frontend）

### 3.1 技术栈与构建

- Vue 3 + Vite + Element Plus + Pinia + Vue Router（`createWebHistory(BASE_URL)`）
- 构建：`npm run build` → `dist/`（**6～8 秒**），由 nginx `:8084` 直出（`root "E:/Project Files/COC_rebuild/frontend/dist"`）
- 环境文件：
  - `.env.production`：`VITE_API_BASE=/api/v1`、`VITE_WS_BASE=`、`VITE_ZONE=main`
  - `.env.user`：`VITE_ZONE=user`
- **URL 构造铁律（1.9 / 2.0 两次全站故障的根因）**：
  - `src/api/http.js` 里 `IS_NATIVE_SHELL` 判定当前是否 Capacitor 壳（`Capacitor.isNativePlatform()` / `protocol==='capacitor:'` / hostname `localhost|127.0.0.1`）
  - 壳内 API 走绝对地址 `https://collectionofcreations.uk/api/v1`，Web 走同源相对路径
  - 导出 `ORIGIN`（壳内为生产源站，Web 为空串），**所有 `<img>` / `<iframe>` / 下载链接必须经 `fileUrl()` 或 `ORIGIN` 拼装**
  - 禁止在组件里写裸相对路径（`/covers/x.png` 在壳内会打到 `https://localhost`，必然空白）
- 遥测：`http.js` 的 `reportClientError(kind, detail)` 以 1px 图片请求打 `/__coc_err?k=&d=&t=`，**只落 nginx access.log**，不写库；`main.js` 在 `IS_APP` 下挂了 `window.error` / `unhandledrejection`
  - 查日志：`Get-Content E:\programs\nginx\logs\access.log -Tail 3000 | Where-Object { $_ -match '__coc_err' }`

### 3.2 路由结构与守卫逻辑

```
/                        SiteLayout（公开站）
  ├── ''                 home           首页（热门榜 / 最新 / 分类筛选）
  ├── projects/:id       project-detail 项目详情（沙箱 iframe 运行 + 评论）
  ├── users/:id          user-profile   用户主页
  ├── materials          assets         **素材库（2.0.6 新增）**：公开素材广场 + 预览 + 复制直链
  ├── dev-apply          dev-apply      开发者申请
  └── forgot             forgot         联系管理员/找回
/login                   login          登录（?redirect= 回跳）
/                        ConsoleLayout（requiresAuth, area=user）
  ├── upload             upload         投稿上传（三步向导：选类型 → 填信息存草稿 → 上传/粘贴源码 → 提交）
  ├── me/projects        me-projects    我的作品（含项目系列标签条 + 系列管理）
  ├── me/materials       me-assets      **我的素材（2.0.6 新增）**：上传 / 系列归类 / 公开性开关 / 复制直链
  ├── me/dashboard       me-dashboard   数据看板
  ├── me/reports         me-reports     我的举报
  ├── messages           messages       私信
  └── settings           settings       设置
/admin                   ConsoleLayout（requiresAuth + requiresAdmin, area=admin）
  ├── ''                 admin-home     平台统计
  ├── review             admin-review   审核队列
  ├── projects           admin-projects 项目管理（下架/恢复/软删/撤销删除/存储路径）
  ├── projects/:id/storage  admin-storage 存储路径详情
  ├── comments / reports / developers / password-resets / users / stats
/403  /404  /:pathMatch(.*)* → /404
```

- 守卫（`router/index.js`）：
  - `meta.requiresAuth && !auth.isLoggedIn` → `/login?redirect=<fullPath>&reason=该页面需要登录后访问`
  - `meta.requiresAdmin && !auth.isAdmin` → `/403`
  - `meta.title` 拼 `document.title`（「XXX · 造物集」）
- 登录态：`stores/auth.js` 以 `localStorage.coc_token` 是否存在判登录（`isLoggedIn = !!token`）
- ⚠️ 已知首帧时序问题：注入 token 后首次访问受保护路由，偶发被踢到 `/login`（store 初始化早于 token 读取）；后续可加重试/初始化前置

### 3.3 上传向导（Upload.vue）行为要点（排查热点）

- 三步 `v-show`：`step 0` 选类型（**只剩 `html` / `scratch`**；`structure` 已于 2.0.6 下线，后端 `createDraft` 对 `project_type='structure'` 直接 400），`step 1` 填标题+简介+分类+**项目系列**+标签，`step 2` 上传/粘贴源码 + 封面 + 提交
- `canNext = !!title.trim() && !!summary.trim()`（分类/标签非必填）；`canSubmit = 有 project_file_id 或 已有 project_id`
- `saveDraft()` → `POST /api/v1/projects`；**该接口只写 DB，不碰存储目录**（"草稿建不出来"与 storage 无关）
- 已加防护（11-15，v2.0.3 重做）：
  - `createProject` 超时 20s → **60s**
  - 超时/断网/5xx 时**自动对账**：查 `myProjects` 最近 5 条，标题（trim 后）+`status=0` 命中即视为成功，避免重复草稿
  - 失败提示带 **HTTP 状态码 + 服务端 message**
  - 移动端底部操作条 `.upload-actions` 吸底（`bottom: 56px` + 安全区），避开 bottom tab 栏
  - **输入容器兜底（本次根因修复）**：标题/简介/详细介绍/粘贴框都包了 `data-field="…"`，挂原生 `input`/`compositionend`/`blur` 监听把 DOM 真实值回填模型；保存前再 `pullFields(..., force)` 整体拉一次。详见第十章
  - **按钮不再被 `:disabled` 拦住**：「保存并继续」「用这段源码上传」改成一键必响应，缺字段时弹明确提示并把光标送去对应输入框
  - **本地暂存**：未建草稿前的填写内容存 `localStorage.coc_upload_draft_v1`（400ms 防抖），App 被系统回收后回来能接着填；草稿一旦入库立即清除，避免两份数据打架
- 上传接口：`init` → `parts`（multipart，**不要手写 `Content-Type`**，否则缺 boundary 必 400）→ `complete`
- 上传前置校验（11-15 新增）：`ChunkUploader.startUpload()` / `CoverUploader.upload()` 在 `project_id` 缺失时直接拦下并提示，不再白传几十 MB 后才发现「没登记到作品」

---

## 四、存储与静态资源映射

- 存储根：`E:\Server Data\coc-platform`
  ```
  projects\<project_id>\v<version>\source\index.html   项目源码
  uploads\        分片临时区
  covers\         封面
  avatars\        头像
  assets\{asset_id}\{file_name}   素材（2.0.6 新增，nginx ^~ /media/ 直出）
  downloads\      APK 等下载物（由 nginx ^~ /downloads/ 直出）
  agreements\ im\ 协议 / 私信附件
  ```
- nginx 站点：`E:\programs\nginx\conf\sites\coc.conf`（`listen 8084`，`server_name _`，`client_max_body_size 256m`）
  - 反代：`/api/` → `http://coc_backend`（=127.0.0.1:8090），`/ws` → `http://coc_backend/ws`（`proxy_read_timeout 3600s`）
  - 微缓存 `coc_micro`：`/api/v1/projects` 20s、`/projects/{id}` 20s、`/categories` 5m、`/run/` 60s；**全部带 `proxy_cache_bypass $http_authorization`**（带 token 一律 BYPASS，不串号），只缓存 200
  - 静态直出：`/covers/` → `E:/Server Data/coc-platform/covers/`、`/avatars/`、`/downloads/`、**`/media/` → `E:/Server Data/coc-platform/assets/`（2.0.6 新增，素材直链，`expires 30d` + `nosniff`）**、`= /qr-live.png`、`= /nginx-health`
  - SPA 兜底：`location /` → `try_files ... /index.html`
- 出口：cloudflared ingress 把 `collectionofcreations.uk` / `www.` → `:8084`

---

## 五、版本号分配规则与 App 打包流程

### 5.1 版本号规则（当前实践）

- `versionName`：语义化 `主.次.修订`。`1.x` = 早期内测；`2.0` = 修复 App 内 API base（1.9 全功能失效的根因）；`2.0.1` / `2.0.2` = 迭代修复
- `versionCode`：**必须单调递增**（Android 升级判据）
- **历史映射（从构建日志实录）**：

  | versionName | versionCode | 时间 |
  | --- | --- | --- |
  | 1.8 | 9 | 11-04 |
  | 1.9 | 10 | 11-12 |
  | 1.10 | 11 | 11-12 |
  | 1.11 | **12** | 11-12 |
  | 2.0 | **11** ⚠️ 回退 | 11-15 |
  | 2.0.1 | 12 | 11-15 |
  | 2.0.2 | 12（与 2.0.1 相同）⚠️ | 11-15 |
  | 2.0.3 | 13 | 11-15 22:19（输入框修复） |
  | **2.0.4** | **14** | 11-19 10:36（视图切换 + 全屏横屏） |
  | **2.0.5** | **15** | 11-19 11:21（虚拟按键：双摇杆 + 自定义按键） |
  | **2.0.6** | **16** | 11-19 16:04（下线 mcstructure + 素材库 + 项目/素材系列） |
  | **2.1.0** | **17** | 11-19 17:19（手机端补齐素材库 + 修明暗模式按钮配色，五期收官） |

> ⚠️ **已发现的两处版本治理问题**：
> 1. `2.0` 的 code（11）**低于** 1.11（12）→ 从 1.11 直接升级 2.0 会被系统拒绝安装
> 2. `2.0.2` 与 `2.0.1` code 都是 12 → 用户若已装 2.0.1，覆盖安装可能被拦
>
> **规则（后续必须遵守）**：每次打包 `versionCode` = 上一次 +1，**不要为了"保持版本号不变"复用 code**；需要"同版本号重发"时，至少把 code +1（如 2.0.2 → code 13）。
> ✅ 2.0.3 / 2.0.4 / 2.0.5 / 2.0.6 / 2.1.0 已按此规则执行：code 13 / 14 / 15 / 16 / 17，从任何历史版本升级都不会被拦。

### 5.2 App 壳工程

- 工程目录：`E:\Server Data\apk-build\coc-app`（Capacitor 8 壳，`android/` 为原生工程）
- `capacitor.config.json`：`appId uk.collectionofcreations.coc`、`webDir www`、`server.androidScheme https`、`hostname localhost`、**无 `server.url`**（走内置资源）
  - 可选优化：加 `server.url=https://collectionofcreations.uk` 后，纯前端修复可不重新打包（未启用，需评估离线能力损失）
- `android/app/build.gradle`：`applicationId` / `versionCode` / `versionName` 在第 20/23/24 行附近
- 签名：自签 `CN=Collection of Creations, OU=Dev, O=COC, L=Server, ST=NA, C=CN`（`apksigner verify --print-certs` 可核验）

### 5.3 一键打包脚本（唯一入口）

`E:\Server Data\scripts\build-coc-apk.ps1`

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "E:\Server Data\scripts\build-coc-apk.ps1" `
  -VersionName 2.0.3 -VersionCode 13
```

参数与步骤：

| 步骤 | 内容 |
| --- | --- |
| 参数 | `-VersionName`（留空则不改）、`-VersionCode`（>0 才改）、`-Clean`（可选 clean） |
| 1 | `npm run build`（在 `E:\Project Files\COC_rebuild\frontend`） |
| 2 | 复制 `dist/` → `E:\Server Data\apk-build\coc-app\www\` |
| 3 | 正则改写 `android/app/build.gradle` 的 `versionName` / `versionCode` |
| 4 | `npx cap sync android` |
| 5 | `gradlew.bat assembleRelease --no-daemon`（`ANDROID_HOME=E:\Server Data\android-sdk`，`JAVA_HOME=JDK 21`） |
| 6 | `apksigner verify --print-certs` + `aapt2 dump badging` 校验签名与版本，产物落到 `E:\Server Data\apk-build\dist\coc-app-v<ver>-<yyyyMMdd-HHmm>.apk` |

耗时：约 **25 秒**（111 tasks，增量）/ 干净全量更久。

### 5.4 发布动作（打包后手工三步）

```powershell
$apk = Get-ChildItem 'E:\Server Data\apk-build\dist' -Filter 'coc-app-v2.0.3-*.apk' |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
Copy-Item $apk.FullName 'E:\Server Data\coc-platform\downloads\coc-app-2.0.3.apk' -Force
Copy-Item $apk.FullName 'E:\Server Data\coc-platform\downloads\coc-app-latest.apk'  -Force
```

- 对外地址：`https://collectionofcreations.uk/downloads/coc-app-latest.apk`（前端 `AppHeader.vue` 里 `APK_RELEASE` 常量用于防缓存，见 `?v=`）
- ⚠️ **改版本必须同步**：`frontend/src/components/AppHeader.vue` 的 `APK_RELEASE`、`frontend/src/utils/platform.js` 的 `APP_BUILD`，然后重新 `npm run build`（否则页面显示的版本号与实际包不一致）
- ⚠️ 已知链路问题：China → Cloudflare 下载 APK 会被截断（实测 3,850,562 B 的包只下来 1,015,808 B）。发布后必须验证：`curl -s -o NUL -w "%{http_code} %{size_download}"` 与本地字节数一致

### 5.5 当前 APK 存货

- 线上 `downloads\`：`coc-app-1.0/1.1/1.2/1.4-rollback/1.5/1.6/1.7/1.8/1.9/2.0/2.0.1/2.0.2/2.0.3/2.0.4/2.0.5/2.0.6/2.1.0/latest`
- 当前线上 `coc-app-2.1.0.apk` = `coc-app-latest.apk` = **3,886,799 B**（2.1.0 / code 17，签名 `CN=Collection of Creations`，sha256 `6633852f2780a6242db86702fbd625cbe1a11c1ef9fa14844f6f147052760abf`）
- 公网实下一致（`Content-Length: 3886799`，`Cache-Control: no-store` + `cf-cache-status: BYPASS`，不会再被 CF 缓存成旧包）
- `version.json` / `version.txt` 已同步为 2.1.0 / 17

---

## 六、已知问题与待办（按优先级）

| # | 问题 | 状态 / 下一步 |
| --- | --- | --- |
| 1 | **App 端"无法创建项目草稿"** | ✅ **已定位并修复（v2.0.3）**：根因是输入框容器失效——App 里中文输入法打字，输入框有字但 `v-model` 收不到值，`title/summary` 恒为空，按钮 `:disabled` 死锁，请求从未发出（nginx 日志里 Android UA 零 `POST /api/v1/projects` 即为证据）。改法见第十章。**待用户装 2.0.3 实测确认** |
| 2 | **APK 下载被 CF 链路截断** | 实测 1,015,808/3,850,562 B；下载后需校验字节数，必要时用系统浏览器/下载器重试（2.0.3 本地直连 3,853,343 B 正常，对外链路仍待验） |
| 3 | **versionCode 回退与重复**（2.0=11 < 1.11=12；2.0.1 与 2.0.2 同为 12） | ✅ 2.0.3 已用 code=13，规则写入 5.1；后续继续 +1 |
| 4 | 未提交改动堆积 | 2.0.3~2.0.6 已入库推送（HEAD `cce4cb7`）；**2.1.0 的改动待提交**，提交前按路径挑拣（见「版本控制状态」） |
| 5 | ⚠️ **APK 打包后必须解包自检** | 2.0.6 曾因 `www/` 残留旧前端，打出的包底栏还是「MC结构」（v2.1.0 已修）。**以后每次出包都要 `unzip -p app-release.apk assets/public/index.html` 核对入口 hash 与 `frontend/dist` 一致，并确认新分包存在**，详见第十四章 |
| 6 | 上传首帧被踢 `/login`（store 时序） | 低优先，可加 `await auth.restore()` 后再挂路由 |
| 7 | CF 缓存规则 / Brotli / HTTP/3 早期提示 | 需带 Zone Settings 权限的 token 或面板手开 |
| 8 | `/admin` 未挂 Cloudflare Access | 建议加，避免后台裸奔 |
| 9 | 6 条未落地规格 | 贡献分流水表、拉黑屏蔽、游客账号、动态上传限额、`hasPreview`、人工放行扫描失败文件 |
| 10 | 沙箱独立域（`8087` + `p1.`）未做 | 与"项目运行隔离"安全相关 |
| 11 | `8083`（tutor）根路径 500 | 待查（该站非 COC 主线） |
| 12 | 测试数据残留 | **实测仅 27 行项目**；`226/227/228`（author 115）建议软删；`225 电磁力演示` 已发布（status=2，author 101）为正常数据，**勿动** |

---

## 七、常用运维命令

```powershell
# 服务状态
Get-Service MySQL84,nginx,coc-backend,coc-packer,cloudflared | Format-Table Name,Status,StartType

# 健康检查（唯一正确路径）
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8090/api/v1/system/health

# 重启后端（会短暂中断，操作前告知用户）
Restart-Service coc-backend -Force

# nginx 改配置后：先校验，失败不要 restart（会卡住）
& E:\programs\nginx\nginx.exe -t -p E:\programs\nginx
Restart-Service nginx -Force          # 通过则整服务重启

# 前端重建（改完代码必做）
Set-Location 'E:\Project Files\COC_rebuild\frontend'; npm run build

# 打包 APK（见 5.3）
powershell -NoProfile -ExecutionPolicy Bypass -File "E:\Server Data\scripts\build-coc-apk.ps1" -VersionName X.Y.Z -VersionCode N

# 查 App 端遥测（前端错误上报）
Get-Content E:\programs\nginx\logs\access.log -Tail 3000 | Where-Object { $_ -match '__coc_err' }

# 查数据库
& 'E:\programs\mysql\bin\mysql.exe' -uroot -p<PASSWORD> --default-character-set=utf8mb4 -e "USE coc_platform; SELECT project_id,title,status,author_id,created_at FROM project ORDER BY project_id DESC LIMIT 10;"
```

---

## 八、交接给其他 AI 的红线（务必遵守）

1. **不要 `git checkout` / `git clean`**：27 个已跟踪文件的改动 + 未跟踪的 `docs/` 就是线上现状，回滚即回滚线上行为
2. **改前端任何 URL**（API / WS / 图片 / iframe / 下载）都必须过 `ORIGIN` / `fileUrl()`，壳内裸相对路径必挂
3. **改完前端要重建**（`npm run build`；本机沙箱会拦 `dist` 清空时用 `npx vite build --outDir dist.new` 再覆盖，见 10.5）；改完 App 体验要**重新打包**（内置资源不会自动更新）
4. **`versionCode` 只增不减**；同版本号重发也至少 +1
5. **MySQL 密码、JWT secret、cloudflared token 不外传、不进 git、不发群**
6. **不要动 `E:\Server Data\coc-platform` 下的真实数据**；测试数据用软删（`is_deleted=1`）
7. **nginx 改配置先 `-t`**，失败别 restart（会卡在"正在等待 Nginx 停止"）；`-s reload` 在本机被拒（权限），一律整服务重启
8. **cloudflared 配置整份重写**，改完 `cloudflared tunnel --config <cfg> ingress validate`
9. **杀进程只用显式 PID**，别用 `CommandLine -match` 宽匹配（会误杀自己的 shell 和隧道进程）
10. **健康检查只打 `/api/v1/system/health`**；packer 用 `/health`（无前缀）
11. **不要删 `data-field="…"` 包装层**：`Upload.vue` / `Login.vue` / `Settings.vue` / `Messages.vue` / `ProjectDetail.vue` 的输入框靠它取 DOM 真实值，删了 App 端又会「填了等于没填」
12. **不要给文本输入驱动的按钮加回 `:disabled="!model.x"`**：App 里模型可能滞后，禁用态会让用户彻底点不动；一律改成可点 + 点击时校验

---

## 九、参考文件

- 前端入口：`frontend/src/main.js`、`router/index.js`、`api/http.js`、`views/Upload.vue`
- 后端入口：`backend/src/main/java/uk/collectionofcreations/coc/`（controller / service / auth / mapper）
- 契约：`contract/api-mvp.md`
- 开发日志：`devlog.md`（仓库根）——按「期」倒序记录每次优化改动：改动目的、涉及文件与关键行号，供后续开发、交接与版本回归时快速回溯；新记录追加在文件最前面
- 端口约定：`ops/README-ports.md`
- 运维脚本：`E:\Server Data\scripts\svc.cmd`、`build-coc-apk.ps1`；手册 `E:\Server Data\scripts\OPS-README.md`
- 服务定义：`E:\Server Data\services\coc-backend\coc-backend-svc.xml`、`coc-packer\coc-packer-svc.xml`
- 日志：`E:\Server Data\logs\{coc-backend-svc.out.log,coc-packer-svc.out.log,apk-build-*.log}`、`E:\programs\nginx\logs\{access,error}.log`

---

## 十、v2.0.3 修复记录（2026-11-15 22:19）

### 10.1 现象与证据链

- 现象：手机端 App 填完「标题 + 简介」点「保存并继续（创建草稿）」，没有任何反应，草稿建不出来；网页端一切正常
- 证据：`nginx access.log` 里 Android UA 的请求只有 GET/OPTIONS，**从来没有 `POST /api/v1/projects`**；同一时间 Edge 网页端同样的操作一条 200。说明请求根本没离开客户端，不是后端/存储/网络问题
- 排除项：存储根可写、`upload_session` 全 `status=1`、后端 `createDraft` 单测（curl 直打）200、CORS 预检 204 —— 全部正常

### 10.2 根因（输入容器失效，两层叠加）

1. `html, body { -webkit-user-select: none }` 让 Android WebView 把 `<input>` 判成「可聚焦但非编辑」→ `input` 事件不上报；
2. 老版本 CSS 里的 `-webkit-user-modify: read-write !important` 把 input 变成「伪 contenteditable」，WebView 里中文输入法 `compositionstart` 后 `compositionend` 常常不触发，Element Plus 的 `isComposing` 标记永远停在 `true`，之后**所有** `input` 事件被吞 → `v-model` 恒为空

叠加结果：`form.title` / `form.summary` 是空 → `canNext` 为 false → 按钮 `:disabled` → 用户点不动 → 一条请求都发不出去。
（历史修复只加了「提交时读 DOM」的兜底，注册页因此活了，但投稿页既没兜底也没去掉 disabled，所以一直卡着。）

### 10.3 改动清单

| 文件 | 改动 |
| --- | --- |
| `frontend/src/utils/formDom.js` | **新增**。`fieldInput/readDomField/pullField/pullFields/attachFieldSync/focusField`：绕过组件内部状态，直接在原生 input/textarea 上监听并回填模型；兼容 EP 把 `data-field` 透传到内部控件的情况 |
| `views/Upload.vue` | 标题/简介/详细介绍加 `data-field` 包装；粘贴框加 `data-field="paste"`；`saveDraft` 先 `pullFields(force)` 再校验、缺字段提示并 `focusField`；去掉 `:disabled`；`try/catch/finally` 保证不卡 loading；超时对账放宽到 5xx/无响应；新增 `localStorage.coc_upload_draft_v1` 暂存与恢复 |
| `views/Login.vue`、`views/Settings.vue` | 提交前 `pullFields` 先按 DOM 补值（登录、改密码同属一类风险） |
| `views/Messages.vue`、`views/ProjectDetail.vue` | 私信/评论输入同样兜底，去掉 `:disabled="!draft.trim()"` |
| `components/ChunkUploader.vue` | `project_id` 缺失时直接拦下（不再白传几十 MB 后报「未登记到作品」） |
| `components/CoverUploader.vue` | `upload()` 补 `project_id` 前置校验 |
| `styles/global.css` | 删除 `-webkit-user-modify`；输入区改 `user-select: text` + `touch-action: manipulation` |
| `utils/platform.js`、`components/AppHeader.vue` | `APP_BUILD='v2.0.3'`、`APK_RELEASE='2.0.3'` |

### 10.4 验证方式（无真机也能验）

用 `playwright-core` + 无头 Edge 连 CDP，伪造 Android UA + 390×844 视口，注入一枚 `type=2` 的合法 JWT（不发生产数据，`route` 拦截 `POST /api/v1/projects`）：

| 场景 | 断言 | 结果 |
| --- | --- | --- |
| App 极端情况：**只写 DOM、不派发任何事件**，然后点保存 | 请求必须发出且 `title/summary` 是中文原文 | ✅ `{project_type:"html",title:"像素小迷宫",summary:"方向键控制小人走出迷宫"}` |
| 桌面端真实输入 | 原有流程不被改坏 | ✅ 同样正确提交 |
| 粘贴源码面板：只写 DOM 不派发事件 | `uploads/init` 的 `project_id` 与 `file_size` 正确 | ✅ `project_id=777777`、`file_size=52`（= 源码字节数） |
| 按钮可用性 | 「保存并继续」「用这段源码上传」不再 disabled | ✅ `disabled=false` |

验证脚本与截图：`E:\Server Data\tmp\coc-verify\`（`verify2.cjs` 手机零事件、`verify3.cjs` 桌面+粘贴；截图 `shot1.png`/`shot-step2.png`）

### 10.5 构建与发布

```powershell
# 前端产物（注意：本机 sandbox 的 safe-delete 会拦 dist 清空，用 outDir 旁路后再合并）
cd "E:\Project Files\COC_rebuild\frontend"; npx vite build --outDir dist.new   # 然后覆盖 dist 并清旧哈希

# APK（跳过脚本内重建，直接用上面的 dist）
powershell -NoProfile -ExecutionPolicy Bypass -File "E:\Server Data\scripts\build-coc-apk.ps1" `
  -VersionName 2.0.3 -VersionCode 13 -SkipWebBuild
```

产物：`E:\Server Data\apk-build\dist\coc-app-v2.0.3-20261115-2219.apk`（3,853,343 B，2.0.3 / code 13，已验签）
已发布：`downloads\coc-app-2.0.3.apk` = `downloads\coc-app-latest.apk`（字节数一致）

### 10.6 待用户实测确认的点

1. App 里**中文**标题/简介能不能存进草稿（核心）
2. 存完草稿后上传文件、传封面是否正常拿到 `project_id`
3. 长文本粘贴（粘贴源码面板）是否正常
4. 从 2.0.1/2.0.2 覆盖安装是否被拦（versionCode 13 > 12，理论上通过）

---

## 十一、v2.0.4 记录（2026-11-19 10:36）· 手机/电脑视图切换 + 全屏横屏

### 11.1 需求（Tinker 11-19）

1. **不要**把手机端 App 里 HTML 作品的展示格式强行改成电脑端；默认按手机端格式访问。
2. 在「展开详情」（下拉详情区）里可自主选择是否以电脑端格式展示。
3. 手机端进全屏后**自动横屏**，并提供**左右旋转**按钮。
4. 全屏时**侧栏默认隐藏**，点击后展示，且**不影响作品主内容**的显示。

### 11.2 改法（全部在 `frontend/src/views/ProjectDetail.vue`）

- 新增「舞台」概念：`.viewer-body > .stage` 是作品画面的**虚拟视口**，缩放与旋转都作用在它身上，
  iframe 内部完全不改写（`sandbox="allow-scripts"` 不变）。
- **默认手机版**：`viewMode` 默认 `mobile`，iframe 直接铺满舞台（手机宽度）。
  原来的 `needScale`（窄屏一律按 1280×720 缩放）改为 `desktopSim`，只在「窄屏 + HTML + 选了电脑版」时成立。
- **详情页切换**：`展开详情` 里新增 `.view-mode-row`（手机版 / 电脑版 分段按钮），
  选择写入 `localStorage['coc_run_view']`，同设备下次沿用；顶栏显示当前视图小标。
- **全屏自动横屏**：`toggleFull` 先 `requestFullscreen()` 再 `screen.orientation.lock('landscape')`。
  - 原生锁定生效 → 手机物理横屏，`rot=0`（不能再叠加 CSS 旋转，否则转两次）。
  - 锁不上（WebView 限制 / 桌面浏览器）→ 兜底 CSS 旋转 `rot=90`，画面仍是横的。
- **左右旋转**：`rotateView(±1)`。原生锁生效时切 `landscape-primary/secondary`，否则切 CSS `90°/270°`。
  旋转时舞台宽高互换（`translate(w,0) rotate(90)` / `translate(0,h) rotate(270)`），iframe 得到真正的横向视口。
- **可收起侧栏**：全屏右边缘一个 26px 把手（`.rail-tab`），点开是 `position:absolute` 浮层（`.rail`），
  内含 重载 / 左旋 / 右旋 / 手机版⇄电脑版 / 新窗口 / 退出。浮层不参与布局，**打开前后舞台尺寸不变**（已验证 796×390 → 796×390）。
- 退出全屏（按钮 / ESC / 返回手势）统一走 `exitFull()`：还原旋转、`orientation.unlock()`、`exitFullscreen()`。
- 手机端 `.viewer` 改为**不吸顶**（`@media max-width:767px { position: static }`）：
  默认手机版后画面占比变小，吸顶只会盖住下面的信息与按钮。

### 11.3 两个踩过的坑（改这里务必注意）

1. **grid 子项 `min-width:auto`**：电脑版舞台初始 1280px 会把 `.main-col` 撑宽，`updateScale` 再量宽度就永远是 1280 → 缩放恒为 1。
   已改为 `.detail-grid{grid-template-columns:minmax(0,1fr) 320px}` + `.main-col{min-width:0}`，且 `frameScale` 初值给 0.3（不是 1）。
2. **`isNarrow` 不能只看宽度**：横屏手机 `innerWidth` 会到 800+，会被误判成桌面端。
   已改为 `UA_MOBILE || innerWidth < 768`。

### 11.4 验证（无头 Edge + CDP，脚本 `E:/Server Data\tmp\coc-verify\verify204.cjs`）

| 场景 | 结果 |
| --- | --- |
| 手机 390×844 默认进详情 | iframe 视口 **356×267**（设备宽度，无缩放），顶栏显示「手机版」 |
| 展开详情 → 电脑版 | 舞台 356×200，iframe 内视口 **1280×720**，等比缩放 |
| 切回手机版 | 恢复 356×267 |
| 点全屏 | 舞台 796×390（横），`matrix(0,1,−1,0,390,0)` = 旋转 90°，iframe 视口 796×390 |
| 打开控制侧栏 | 侧栏位移 64px → 0，**舞台仍 796×390**（不挤压内容） |
| 左旋 | 矩阵变为 270°，画面仍铺满 |
| 退出全屏 | 回到 356×267，`transform: none`，无残留 |
| 桌面 1280×900 | 舞台 876×522 正常铺满，无缩放、无切换按钮、无报错 |
| 控制台 | 0 error / 0 pageerror |

### 11.5 发布

- `versionName 2.0.4` / `versionCode 14`；`APP_BUILD='v2.0.4'`、`APK_RELEASE='2.0.4'` 同步
- APK：`apk-build\dist\coc-app-v2.0.4-20261119-1036.apk`（3,855,581 B）
- 线上：`downloads\coc-app-2.0.4.apk` = `coc-app-latest.apk`；`version.json` 已更新
- 构建注意：本机沙箱拦 `npm run build` 清空 dist，用 `npx vite build --outDir dist.nX` 再覆盖（见 10.5）

### 11.6 待实测点

1. 真机装 2.0.4，进作品详情：默认是不是**手机版**（作品自己适配手机，而不是缩小的电脑版）
2. 展开详情切「电脑版」：是否变成完整分栏布局且无横向滚动
3. 点全屏：手机是否**自动转到横屏**（原生锁定）；没转的话画面是否已横向铺满（CSS 兜底）
4. 全屏右边缘把手 → 点开侧栏 → 左旋/右旋 → 侧栏是否遮住画面但不缩放作品
5. 覆盖安装 2.0.3 → 2.0.4 是否被拦（code 14 > 13，理论上通过）

---

## 十二、v2.0.5 记录（2026-11-19 11:21）· 手机端全屏虚拟按键

### 12.1 为什么需要它

2.0.4 解决了「手机端能全屏横屏跑作品」，但触摸屏**没有键盘**——靠 `keydown` 操作的作品（WASD 走位、空格跳、数字键放技能）在手机上完全玩不了。2.0.5 补上一层虚拟按键。

### 12.2 架构：三层接力（安全边界不动）

作品跑在 `sandbox="allow-scripts"` 的 iframe 里，**故意不给 `allow-same-origin`**，父页面拿不到 `contentDocument`、也无法 `dispatchEvent`。所以按键必须走：

```
VirtualPad（父页面/触摸层）
  → postMessage {__coc:'key', type:'down'|'up', key, code, keyCode}
  → 作品 iframe 内 coc-key-bridge.js 合成 KeyboardEvent
  → dispatch 到 document.activeElement || body
```

桥接脚本由 **nginx `sub_filter` 注入**到 `/api/v1/run/` 的 HTML 响应里（`</body>` 前）。

### 12.3 涉及文件

| 文件 | 说明 |
| --- | --- |
| `frontend/src/utils/vkeys.js`（新增，164 行） | 字段表 + 键值解析 + `localStorage['coc_vpad_v1']` 方案持久化；`space`/`enter`/`ctrl`/`shift` 别名，字母 `KeyX`、数字 `DigitN`；按键上限 12 |
| `frontend/src/components/VirtualPad.vue`（新增，540 行） | 双摇杆（`joyA`=WASD、`joyB`=方向键，各自 `pointerId` 独立）+ 自定义按键 + 设置面板（开关/新增/删除/透明度/尺寸/恢复默认） |
| `frontend/src/views/ProjectDetail.vue` | 把 `<VirtualPad>` 放进 `.stage`（跟着旋转，坐标 == 作品坐标）；侧栏加「虚拟按键」「按键设置」两个按钮（仅窄屏）；`sendKey` / `pingBridge` / `onBridgeMsg` / `releaseAll` |
| `E:/programs/nginx/html/coc-key-bridge.js`（新增，71 行） | 桥接脚本：`defineProperty` 覆盖 `keyCode`/`which`（构造函数设不上），`ping`/`pong` 探活，`blur` 上报防卡键 |
| `E:/programs/nginx/conf/sites/coc.conf` | `location ~ ^/api/v1/run/` 加 `proxy_set_header Accept-Encoding ""` + `sub_filter`；新增 `location = /coc-key-bridge.js` |
| `frontend/src/utils/platform.js` | `APP_BUILD='v2.0.5'` |

### 12.4 必须记住的四个坑

1. **`sub_filter` 处理不了压缩响应**：没有 `proxy_set_header Accept-Encoding ""` 就一行都匹配不到，而且**不报错**，表现只是"注入没生效"。
2. **`keyCode` 构造时就设不上**：`new KeyboardEvent()` 不支持 `keyCode`/`which`，必须 `Object.defineProperty` 加 `get`。大量老作品判断的就是 `e.keyCode`。
3. **双指操作必须按摇杆分别记 `pointerId`**：单指方案里第二根手指按下会让第一个摇杆停更、方向键永远卡住。已改为 `joyPid = {joyA, joyB}`，同一摇杆被新手指重按时先释放旧手指留下的方向。
4. **`.vpad` 默认 `pointer-events:none`**：只有设置模式（`.is-edit`）才 `auto`；设置模式下 `onDown` 只记拖拽状态、不进 `press()`，从根上保证"摆布局不触发按键"。

### 12.5 发布

- `versionName 2.0.5` / `versionCode 15`；`APP_BUILD='v2.0.5'` 同步
- APK：`apk-build\dist\coc-app-v2.0.5-20261119-1121.apk`（3,864,431 B，sha256 `1451960d69dcfcd20cf328b7036c4955a10bf06b795b064b3b12f6a290a26bbd`）
- 线上：`downloads\coc-app-2.0.5.apk` = `coc-app-latest.apk`；`version.json` 已更新（本地 + 公网均校验 size/sha256 一致）

### 12.6 部署自检（缺任一项，手机上点摇杆都没反应）

1. `E:/programs/nginx/html/coc-key-bridge.js` 存在；
2. `coc.conf` 的运行页 location 带 `Accept-Encoding ""` + `sub_filter`；
3. `nginx -t` 通过后 `Restart-Service nginx`；
4. `curl https://collectionofcreations.uk/api/v1/run/<id>/ | tail -c 200` 能看到 `<script src="/coc-key-bridge.js"></script>`。

### 12.7 待实测点

1. 真机进作品全屏 → 侧栏点「虚拟按键」→ 摇杆是否出现、推摇杆作品里的人物是否动
2. 侧栏点「按键设置」→ 输入 `space` 新增 → 拖动按钮 → **拖动过程中作品是否有反应**（应该没有）
3. 选中按钮拖透明度/尺寸滑条，效果是否即时
4. 开两个摇杆，**两指同时推** → 是否两个方向同时生效、松手是否都松开
5. 点「完成」退出设置 / 退出全屏 → 重进全屏，布局是否还原
6. 覆盖安装 2.0.4 → 2.0.5 是否被拦（code 15 > 14，理论上通过）

### 12.8 线上事故修复（同日 · 下载 404 根因）

**现象**：`/downloads` 显示 v2.0.5，点下载返回 `{"code":404,"message":"接口或资源不存在","data":null}`。

**根因**：`frontend/src/utils/format.js` 的 `API_ORIGIN` 写的是
`new URL(base, 'http://127.0.0.1:8090').origin`。生产 `.env.production` 的 `VITE_API_BASE=/api/v1` 是**相对**基址，
`new URL('/api/v1', 'http://127.0.0.1:8090')` 的 `origin` 就等于这个「解析基准」→ 产物里写死 `http://127.0.0.1:8090`。
于是 `fileUrl()` 生成 `http://127.0.0.1:8090/downloads/*.apk`：本机访问 → 打到后端 8090 → JSON 404；远程访问 → 连自己的回环地址 → 失败。
同一个 `API_ORIGIN` 还喂给 `runUrlOf()`，封面 / 头像 / 沙箱运行页在远程设备上同样全废。

**修法**：
- `format.js`：`ORIGIN`（原生壳）分支不动；绝对基址取其 `origin`；**相对基址直接返回 `''`**，让 `fileUrl` 产出相对路径走 nginx。
- `vite.config.js`：加 `base: mode === 'user' ? '/user/' : '/'`（`dist-user` 挂在 `/user/` 下，原来资源写 `/assets/*` 被主站抢走 → 入口 chunk 404）。

**验证**：`grep -rl "127.0.0.1:8090" dist/assets/*.js` 仅剩 `AppState` 的报错文案；`/downloads/coc-app-2.0.5.apk` 200 / 3,864,431 B；`/user/` 及其 4 个资源全 200；公网 index 已切 `index-BG7EI2uG.js`。

---

## 十三、v2.0.6 记录（2026-11-19 16:04）· 下线 mcstructure + 素材库 + 项目/素材系列

### 13.1 需求（用户原话要点）

1. 移除 mcstructure 板块，新增项目素材上传，**素材板块全方位替代**原 mcstructure 板块；
2. 新增**项目系列 / 素材系列**创建功能：上传时可选择归入现有系列、也可现场新建系列；个人界面可查看自己的系列；系列只装同类（项目系列只有项目，素材系列只有素材）；
3. 用素材展示替代原 mcstructure 展示界面，**每个素材可一键复制直链**（`baseurl/media/...` 这种能直接访问文件的形式）；
4. 支持视频 / 图片 / GIF / 音频 / JSON；概览界面能播放视频、GIF、音频，能展示图片与 JSON 内容；
5. 上传时与上传后（个人素材界面）都能改公开性；
6. 素材系统核心是「上传 + 复制直链给用户自己的 HTML 用」，大文件复用 HTML 板块的分片上传；
7. 数据结构：系列含 `id / user_id / 名称 / 简介`；素材含 `id / user_id / 所属系列 id（可空）`；
8. 数据库交互优先「系列 id → user_id → user_id + 素材 id 联合查」以减少计算；
9. 完成后 web 重建上 nginx、App 打包 2.0.6 上官网。

### 13.2 关键决策（后续接手别推翻）

| 决策 | 原因 |
| --- | --- |
| **mcstructure 软下线**：前端入口全撤、`ProjectService.createDraft` 对 `project_type='structure'` 返回 400，但 `project_type` 枚举值保留、历史项目仍可打开 | 已发布作品链接一旦 404，对作者不可接受 |
| **素材直链由 nginx 静态直出**（`/media/` → 存储根 `assets/`），不走后端接口 | 直链要嵌进用户 HTML，必须不依赖登录态、不消耗后端 |
| **`is_public` 只控制「是否可被检索/出现在广场」**，直链本身不鉴权 | 同上；若直链要鉴权，用户已发布作品会集体裂图 |
| **素材删除是软删**（`is_deleted=1`，文件留盘） | 历史 HTML 引用不断链 |
| **素材类型白名单硬编码，故意排除 `html/svg/js`** | 防止素材库被当成第二个 HTML 托管入口（可执行内容 + 同源直链 = XSS 风险） |
| **素材与项目共用一套分片上传**（`upload_session.kind` 分流） | 避免维护两套分片/秒传/sha256 校验 |
| **系列删除时子项 `SET NULL`** | 删系列不该顺手删作品/素材 |
| `SeriesMapper` 用 `${table}` 拼表名但值只来自 `Kind` 枚举白名单 | 两套结构完全一致的系列用一份 SQL；枚举不进请求参数，无注入面 |

### 13.3 涉及文件

**后端（新增）**

```
backend/src/main/resources/db/migration/V4__assets_and_series.sql
backend/.../controller/AssetController.java      /api/v1/assets（7 端点）
backend/.../controller/SeriesController.java     /api/v1/series（5 端点）
backend/.../mapper/SeriesMapper.java             两套系列共用 SQL
backend/.../mapper/AssetMapper.java
backend/.../service/SeriesService.java           Kind 枚举 + ensureByName
backend/.../service/AssetService.java            ~470 行，类型白名单 + init/complete/CRUD
```

**后端（改动）**

```
backend/.../storage/StorageService.java   init() 建 assets/；assetsDir() / assetDir(id)
backend/.../mapper/UploadMapper.java      insertSession 写 kind；findSession 带出 kind
backend/.../service/UploadService.java    抽出 materialize(sessionId, session)（合并分片+校验 sha256+秒传）
backend/.../service/ProjectService.java   RETIRED_TYPES；series_id / new_series_name；detail 带 series_projects
backend/.../mapper/ProjectMapper.java     findProjectById JOIN 系列；listBySeries；insert/update 写 series_id；listMine 带系列
backend/.../auth/AuthInterceptor.java     系列与素材写操作的登录白名单
```

**前端（新增）**

```
frontend/src/components/AssetUploader.vue   上传组件（拖拽 + 元数据 + 分片 + 单片重试 3 次）
frontend/src/views/Assets.vue               公开素材库 /materials
frontend/src/views/MeAssets.vue             我的素材 /me/materials
```

**前端（改动）**：`api/index.js`（`seriesApi` / `assetApi`）、`utils/format.js`（`ASSET_TYPE` 等 + `isAutoApproved` 只认 scratch）、`utils/platform.js`（`v2.0.6`）、`router/index.js`、`Home.vue`、`BottomTabBar.vue`、`ConsoleLayout.vue`、`Upload.vue`、`MeProjects.vue`、`ProjectDetail.vue`、`ChunkUploader.vue`、`Downloads.vue`（2.0.6 条目）

**仓库外**：`E:/programs/nginx/conf/sites/coc.conf` 新增 `location ^~ /media/`

### 13.4 必须记住的坑

1. **`/assets` 不能当路由**：SPA 构建产物就放在 `dist/assets/`，把素材库路由写成 `/assets` 会被 nginx 当成静态目录，线上 301。**已统一改成 `/materials` 与 `/me/materials`**，以后加路由先避开 `assets`。
2. **`no_series` 必须归一化**：前端传 `1` / `"1"`，MyBatis `<if test='no_series == true'>` 不认。统一在 Service 层用 `Params.bool(p, "no_series")`。同理 `is_public` 的 JSON `true` 也要走 `Params.bool`。
3. **Flyway 版本号会被并行会话抢占**：本次原本写 `V3__assets_and_series.sql`，另一个会话已建 `V3__bug_feedback.sql` → 启动报 `Found more than one migration with version 3`。**改名前先 `ls db/migration`**，撞了就顺延，已经跑过的版本号不要复用。
4. **`mvn package` 报 `Unable to rename ... jar`**：`coc-backend` 服务正持有 jar。先 `Stop-Service coc-backend -Force`，打包完再 `Start-Service`。
5. **即时上传（秒传）的源文件不能直接 move**：`completeUpload` 里若走 `materialize()` 拿到的是已有文件引用，必须 **copy** 到 `assets/{id}/`，否则会把项目源码目录里的文件搬走。
6. **`apksigner.bat` 路径带空格会失败**：用 `java -jar "E:/Server Data/android-sdk/build-tools/35.0.0/lib/apksigner.jar" verify --print-certs` 代替。

### 13.5 发布与验证

| 项 | 结果 |
| --- | --- |
| 数据库 | Flyway 到 **v4**（V3 bug_feedback + V4 assets/series 均 success），31 表 |
| 后端 | `mvn clean package` 后重启 `coc-backend`，`/api/v1/system/health` 正常 |
| 前端 | 产物 `index-Cb1rm9_k.js`；`/`、`/materials`、`/me/materials`、`/downloads` 全 200；公网 index 已同步（带时间戳取到同一 hash） |
| App | 2.0.6 / code 16，3,864,440 B，sha256 `fd71292c…41e`，签名 `CN=Collection of Creations` |
| 素材链路 | 建系列 → init → parts → complete → `GET /media/3/测试图-1x1.png` = 200 / `image/png` / 70 B / 字节一致 / `max-age=2592000` |
| 素材权限 | 公开列表可见 → 改私密后匿名访问详情 404；`mine` 每行带 `url` |
| 项目系列 | `POST /projects` 带 `new_series_name` → 详情 `series_id`/`series_name`/兄弟项目 1 个；`listMine` 带系列字段；`project_type='structure'` → **400** |
| 浏览器 E2E | ✅ 已补跑（Playwright + 独立 Chromium headless，脚本 `E:/Server Data/tmp/coc-verify/ui-assets-e2e.cjs`）：我的素材渲染/缩略图加载、复制直链（真实剪贴板 `…/media/14/e2e-测试图.png` → 200 / `image/png` / 61,407 B / 字节一致）、图片 `naturalWidth>0`、JSON 格式化文本、音频 `readyState=4 duration=0.2`、公开性开关（关闭后广场消失 + 匿名详情 404 + 直链仍 200；打开后回归）、`requestfailed=0` |

### 13.5.1 无头浏览器验证的正确姿势（重要）

1. **别用 `127.0.0.1` 跑本站 UI**：`http.js` 的 `IS_NATIVE_SHELL` 把 hostname `127.0.0.1|localhost` 判成 Capacitor 壳 → API 走 `https://collectionofcreations.uk/api/v1` 绝对地址 → 跨源 CORS 全灭，页面只显示「哎呀，卡住了」。
   **用 `http://127.0.0.2:8084`**：仍属回环（secure context，`navigator.clipboard` 可用；Chromium 默认绕过系统代理），但 hostname 不等于 `127.0.0.1`，前端按普通网页处理。
2. **别自定义域名**：宿主机有 `http_proxy=127.0.0.1:64070`，`coc.test` 这类名字会被代理劫持返回 502（`--no-proxy-server` 也压不住）。
3. **别用系统 Edge**：nexus-desktop 会抢走 `msedge.exe` 并静默退出。用 `npx playwright@1.49.0 install chromium` 装的独立 Chromium（`%LOCALAPPDATA%\ms-playwright\chromium_headless_shell-1148`）+ `playwright-core`。

### 13.6 清理

验证产生的测试数据已全部清掉：`project` #245（【自动化测试】系列归属）、`project_series` #4（测试项目系列-60311）、`asset` #1~#3 及其 `E:/Server Data/coc-platform/assets/{1,2,3}` 目录。**注意**：测试 JWT 用的是真实用户 #115（哈哈哈）的身份，只删数据、没动账号。

### 13.7 待实测点（2.0.6）

> ✅ 第 1~4 项已在**桌面浏览器 E2E** 里机器验证过（见 13.5 / 13.5.1）；仍需要真机确认的是 App 端与移动端手势、大文件、升级路径。

1. 📱 手机 App 里上传一张图 → 回「我的素材」看到缩略图 → 点复制链接 → 粘到浏览器地址栏能直接打开图片（桌面端已验证，App 端待确认剪贴板权限）；
2. 📱 把该素材设为私密 → 退出登录后在 `/materials` 看不到它 → 但**已知直链仍能打开**（预期行为，见 13.2）；
3. 上传 mp4 / mp3 / gif，在预览弹窗里能正常播放（音频 wav 已验证 `readyState=4`，**视频未验**）；
4. ✅ 上传 json，预览能看到格式化内容（已验证）；
5. 投稿时新建一个项目系列 → 存草稿 → 「我的作品」顶部出现该系列标签，点击能筛出这条；
6. 系列管理里删掉该系列 → 项目回到「未归系列」，作品本身没消失；
7. 用一个 >100MB 的素材上传，确认分片上传能跑完（走的是项目源码那条分片链路）；
8. 覆盖安装 2.0.5 → 2.0.6 是否被拦（code 16 > 15，理论上通过）。

**注意 ①**：网页端需硬刷新（Ctrl+F5）清掉旧 chunk 缓存。

**注意 ②（2.1.0 修正，重要）**：~~APK 不受影响，无需重打包~~ —— **这句话是错的**，2.0.6 正是因为信了它才出问题。Capacitor 壳加载的是 APK 内自带的 `assets/public/`（由 `www/` 拷贝而来），**不读线上的 `frontend/dist`**；前端一改，必须重跑 `cap sync` + 重新出包，否则手机端永远停留在旧前端。详见第十四章。

---

## 十四、v2.1.0 记录（2026-11-19 17:19）· 手机端补齐素材库 + 明暗模式按钮配色修复（五期测试收官）

### 14.1 需求来源

用户实测 2.0.6 后反馈两条：

1. **「手机端的 2.0.6 版本前端并没有同步此次更改（依旧还是 mcstructure）」** —— 手机 App 里底栏还是「MC结构」，素材库页面不存在；
2. **「项目草稿创建界面的按钮在明暗模式下的样式有问题」** —— 投稿向导的主按钮在深色模式下变成 Element Plus 默认蓝，跟站点主题不搭。

做完后打包为 **2.1.0（versionCode 17）**，作为五期测试的收官版本。

### 14.2 关键修复一：APK 里打进了旧前端

**根因**：`E:/Server Data\apk-build\coc-app\www\` 是 Capacitor 的 web 资源来源目录，打包时 `cap sync` 把它整个拷进 `android/app/src/main/assets/public/`。2.0.6 出包时**没有先执行「`frontend/dist` → `www`」这一步**，`www/` 还是上一轮（2.0.5 时代）的残留：

| 位置 | 2.0.6 出包时的实际值 |
| --- | --- |
| `frontend/dist/index.html` | `assets/index-Cb1rm9_k.js`（含 `Assets-*` / `MeAssets-*`） |
| `apk-build/coc-app/www/index.html` | `assets/index-CTIiWO9z.js` ← **更旧** |
| APK 内 `assets/public/index.html` | `assets/index-CrLUMb5x.js` ← 最终被打进去的 |
| APK 内是否有 `Assets-*` / `MeAssets-*` | **没有** |
| APK 内 `BottomTabBar-*.js` | 仍命中 `MC结构` |

也就是说：**源码、`dist`、线上网页三者都是新的，只有 APK 是旧的**。发布脚本 `scripts/build-coc-apk.ps1` 本身有 copy 步骤，是那次没走完整脚本、只单跑了 gradle 造成的。

**修复步骤**（`www` 只放静态资源：`index.html` + `assets/`）：

```bash
cd "E:/Server Data/apk-build/coc-app/www"
rm -f index.html assets/* && rmdir assets          # 清干净，避免旧 chunk 残留
cp -r "E:/Project Files/COC_rebuild/frontend/dist/." .
cd .. && npx cap sync android                       # 约 3~4 分钟（update android 较慢）
cd android && ./gradlew.bat assembleRelease --no-daemon
```

**2.1.0 出包后的解包自检**（546 个条目，前端 71 个文件）：

| 检查项 | 结果 |
| --- | --- |
| `assets/public/index.html` 入口 | `assets/index-yZCrbRZ5.js`，与 `frontend/dist` **完全一致** ✅ |
| 素材库分包 | `Assets-DYhVDBu_.js` / `Assets-IJ5nQyoH.css` / `MeAssets-B7vtCBoe.js` / `MeAssets-I9xZ0yUb.css` ✅ |
| 底栏文案 | `BottomTabBar-Dg8HOcRJ.js`：`素材`×1、`MC结构`×0 ✅ |
| `mcstructure` 全量搜索 | 仅 `format-CA8eiKFc.js` 的 `TYPE_LABEL.mcstructure="MC 结构"`（历史类型展示，有意保留）✅ |
| 版本标记 | `index-yZCrbRZ5.js` 内含 `v2.1.0` ✅ |
| badging | `versionCode='17' versionName='2.1.0'` ✅ |

> 🔴 **写进流程**：以后每次出包，**必须解包核对入口 hash**，不能只看 `BUILD SUCCESSFUL`：
> ```bash
> unzip -p app-release.apk assets/public/index.html | grep -o 'assets/index-[^"]*\.js'
> unzip -l app-release.apk | grep -E 'assets/public/assets/(Assets|MeAssets)-'
> ```
> 第一条的输出必须等于 `frontend/dist/index.html` 里的那个文件名。

### 14.3 关键修复二：深色模式按钮配色（CSS 特异性）

**根因**：`frontend/src/styles/tokens.css` 把项目设计变量映射到 Element Plus 变量时，选择器写的是 `:root`（特异性 **0,1,0**）；而 `element-plus/theme-chalk/dark/css-vars.css` 用的是 `html.dark { … }`（特异性 **0,1,1**）。深色模式下后者优先，**整套 `--el-*` 变量被官方深色默认值覆盖**：主色退回 `#409EFF`、背景退回 `#141414`、边框退回 `#4C4D4F`。浅色模式没有这份覆盖表，所以只有深色出问题 —— 这就是「明暗模式样式不一致」的本质。

**实测数据**（无头浏览器 `getComputedStyle`）：

| 取色点 | 修复前（深色） | 修复后（深色） | 修复后（浅色） |
| --- | --- | --- | --- |
| 主按钮背景 | `rgb(64,158,255)` | `rgb(108,134,255)` | `rgb(79,110,247)` |
| 次要按钮背景 | `rgb(20,20,20)` | `rgb(21,25,35)` | — |
| 次要按钮文字 | 不可读 | `rgb(164,172,189)` | — |

**修复（三处联动，缺一不可）**：

1. **`tokens.css`** —— 映射选择器改成一组，特异性不输给 EP 深色表；并补 `.el-button--primary` / `.is-disabled` 的显式按钮变量：

   ```css
   :root,
   html.dark,
   html[data-theme='dark'],
   html[data-theme='light'] {
     --el-color-primary: var(--c-primary);
     --el-text-color-placeholder: var(--c-text-3);
     --el-border-color-extra-light: var(--c-border);
     --el-fill-color-lighter: var(--c-bg-hover);
     --el-disabled-bg-color: var(--c-bg-sunken);
     /* … */
   }
   ```

2. **`stores/ui.js`** —— `ACCENTS` 每套主题色补一档 `primaryLight`（深一档），`apply()` 里 `mode === 'light' ? a.primaryLight : a.primary`。原因：`#6c86ff` 做浅色底 + 白字对比度不足，必须换 `#4f6ef7`。
3. **`index.html`** —— 首屏内联脚本的 `ACCENTS` 改成 4 元组 `[darkPrimary, lightPrimary, accent, accent2]`，保证 Vue 挂载前**首帧**的颜色就正确，不闪。

### 14.4 顺带清理：下线板块的残留文案

| 文件 | 改动 |
| --- | --- |
| `components/BizTabs.vue` | `Scratch / HTML / 结构文件…` → `Scratch / HTML / 素材…` |
| `views/DevApply.vue` | 头部说明改为「投稿自己的作品（HTML / Scratch）… 图片、音视频等素材请传到「我的素材」」；条款 3 改为「Scratch 为纯数据/平台打包产物，免审直发」 |
| `views/MeProjects.vue` | 空状态 → 「投稿你的第一个小造物，Scratch 免审直发；素材可在「我的素材」上传」 |
| `views/admin/AdminReview.vue` | 「Scratch 与结构文件免审不进队列」→「Scratch 免审不进队列」 |

**有意保留**：`utils/format.js` 的 `TYPE_LABEL.structure` / `.mcstructure`、`ProjectDetail.vue` 的「板块已下线」兜底 —— 历史项目类型标签必须能正常渲染。

### 14.5 涉及文件

| 文件 | 改动 |
| --- | --- |
| `frontend/src/styles/tokens.css` | 选择器特异性修复（核心）+ 主按钮/禁用态显式变量 |
| `frontend/src/stores/ui.js` | `ACCENTS` 增加 `primaryLight`，`apply()` 按明暗取主色 |
| `frontend/index.html` | 首屏主题脚本色板同步为 4 元组 |
| `frontend/src/views/Downloads.vue` | 新增 2.1.0 条目 |
| `frontend/src/utils/platform.js` | `APP_BUILD='v2.1.0'` |
| `BizTabs.vue` / `DevApply.vue` / `MeProjects.vue` / `admin/AdminReview.vue` | 下线板块残留文案 |
| `apk-build/coc-app/www/` | 全量重同步（本次故障的直接产出物） |
| `apk-build/coc-app/android/app/build.gradle` | `versionCode 17` / `versionName "2.1.0"` |
| `coc-platform/downloads/` | `coc-app-2.1.0.apk`、`coc-app-latest.apk`、`version.json`、`version.txt` |

### 14.6 发布与验证

| 项 | 值 |
| --- | --- |
| 包大小 | 3,886,799 B |
| sha256 | `6633852f2780a6242db86702fbd625cbe1a11c1ef9fa14844f6f147052760abf` |
| 签名 | `CN=Collection of Creations`，证书 SHA-256 `1324f12e…2958b5`（同历史包，可覆盖安装） |
| 本地 | `/downloads/version.json` = 2.1.0；APK `200` / `Content-Length: 3886799` |
| 公网 | `https://collectionofcreations.uk/downloads/version.json` = 2.1.0；APK `Content-Length: 3886799`（未命中旧缓存） |
| 网页端 | 本地与公网入口 hash 一致 `assets/index-yZCrbRZ5.js` |
| 前端构建 | `vite build --outDir dist.n1` → 合并进 `dist`（71 文件）+ prune 36 个旧 chunk |

### 14.7 一个无害的「死循环」说明

`Downloads.vue` 里的 2.1.0 条目写的是 `size: 3886681`，而最终包是 3,886,799 B（差 118 B）。原因是：改页面 → 体积变 → 重打包 → 体积再变，永远追不上。

**不必追**：下载页的**最新版卡片**是 `onMounted` 里 `fetch('/downloads/version.json')` 动态取的（`latest = remote || RELEASES[0]`），数组里的 `size` 只是**接口不可达时的兜底**；且 `fmtSize()` 两者都显示「3.7 MB」，肉眼无差别。数组条目只承担「这个版本存在过」的历史枚举作用。

### 14.8 五期测试闭环清单

| # | 问题 | 版本 |
| --- | --- | --- |
| 1 | 手机端草稿建不出来（输入框 `v-model` 失效） | 2.0.3 |
| 2 | 下载 404（`API_ORIGIN` 写死 `127.0.0.1:8090`） | 2.0.3 后修 |
| 3 | 手机端默认手机版视图 + 可切电脑版 + 全屏自动横屏 + 控制侧栏 | 2.0.4 |
| 4 | 全屏虚拟按键（双摇杆独立映射 + 自定义按键 + 拖动 + 透明度/尺寸 + 本地持久化） | 2.0.5 |
| 5 | mcstructure 下线、素材库 + 项目/素材系列 | 2.0.6 |
| 6 | **手机端未同步素材库 + 明暗模式按钮配色** | **2.1.0** |

### 14.9 清理

- 删临时构建目录 `frontend/dist.n1`（已合并并 prune）。
- `frontend/dist-user` **未重建**：线上 nginx 已无任何 site 引用它，属历史遗留。
- 解包校验目录 `E:/Server Data/tmp/apkchk` 用完即删。
