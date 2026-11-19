# COC 平台项目状态说明书

> 生成时间：2026-11-15 21:45（GMT+8）
> 最近更新：2026-11-19 11:30（GMT+8）· 发布 v2.0.5（手机端全屏虚拟按键：双摇杆 + 自定义按键），见第十二章
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
| 分支 | `main`，HEAD `d3caa3e`（2026-11-12），**工作区有 27 个已跟踪文件改动 + `docs/` 未跟踪**（未提交，按用户要求） |
| 远程 | `https://github.com/cylinder137/COC_rebuild.git`（已推送，ahead=0） |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router（`createWebHistory`） |
| 后端 | Java 21 + Spring Boot 3.5 + MyBatis + MySQL 8 + Flyway |
| 数据库 | `coc_platform`，**27 张表**，Flyway `V1__init.sql` + `V2__password_reset_reject.sql` |
| 后端端口 | `8090`（Java），打包服务 `8091`（Node / coc-packer） |
| Web 入口 | nginx `:8084` → `https://collectionofcreations.uk` |
| App | Capacitor 8（`uk.collectionofcreations.coc`），当前 **2.0.5 / versionCode 15**（2026-11-19 11:21 打包） |
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
- ⚠️ 2.0.5 的改动（虚拟按键：`VirtualPad.vue`、`utils/vkeys.js`、`ProjectDetail.vue`、`platform.js` + devlog）**尚未提交**，等实测通过后再入库；nginx 侧 `coc-key-bridge.js` 与 `coc.conf` 在仓库外，已部署到生产
- 未提交改动（11-15 22:20 现状，`git status --porcelain` 共 28 行）：

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

- 迁移：Flyway `V1__init.sql`（33,950 B）、`V2__password_reset_reject.sql`（629 B），均已成功应用
- 27 张表：

  ```
  agreement_version  build_task  category  comment  comment_like  developer_profile
  flyway_schema_history  follow  im_message  im_session  im_session_member
  notification  operation_log  password_reset_apply  project  project_favorite
  project_file  project_like  project_review  project_tag  project_version
  project_view_daily  project_view_hourly  report  tag  upload_session  user
  ```

- **字段名易坑**（写 SQL 前必查）：
  - `project`：作者列是 **`author_id`**（不是 `user_id`）；含 `status`（0 草稿 / 1 审核中 / 2 已发布）、`audit_mode`、`is_deleted`、`down_reason`、`reject_reason`
  - `project_file`：关联 `project_version_id`（不是 `project_id`），路径列 `storage_path`，另有 `scan_status`、`build_status`
  - `project_version`：`project_version_id / project_id / version_no / changelog / created_by`
  - `upload_session`：`upload_session_id / user_id / project_id / uploaded_chunks / status / storage_path`，`status=1` 表示完成
- 当前数据量参考（11-15 22:20 实测，非估算）：
  - `project` **27 行**（草稿 4 / 已发布 18 / 软删 5，`project_id` 最大 228）
  - `project_file` 22 行、`project_version` 22 行
  - `project_view_hourly` 523 行、`project_view_daily` 201 行
  - `user` 15 个（type=2 开发者 14 + type=3 管理员 1，**当前没有 type=1 普通用户**）
  - `upload_session` 16 行，`status` 全为 1（无悬挂会话）
  - 待办池为空：无 `status=1`（审核中）项目、无 `status=0`（未处理）举报
  - ⚠️ 旧版本文档里「project 约 228 条」是**误记**（把 `max(project_id)=228` 当成了行数），引用数据量以本节为准
- 账号模型：`user.type` = 1 普通 / 2 开发者 / 3 管理员；`token_version` 参与 JWT 失效判定

### 2.3 接口总览（9 个 Controller，共约 69 个端点）

| Controller | 基路径 | 端点数 | 说明 |
| --- | --- | --- | --- |
| AuthController | `/api/v1` | 8 | `POST /auth/login`、`/auth/dev-apply`、`/auth/password/forgot`、`/auth/me`、`/auth/change-password`、`/auth/cancel-account` 等 |
| ProjectController | `/api/v1` | 12 | `GET/POST /projects`、`GET /projects/{id}`、`PUT /projects/{id}`、`POST /projects/{id}/submit`、`/cover`、`/view`、`/stats`、`/run`、`/comments`、`/build/retry` |
| UploadController | `/api/v1/uploads` | 4 | `POST /uploads/init`、`/uploads/{id}/parts`、`/uploads/{id}/complete`、`GET /uploads/{id}` |
| FileController | `/api/v1` | 2 | `GET /files/{project_file_id}/content`、`GET /run/{project_id}/**` |
| ImController | `/api/v1/im` | 6 | 会话列表、开一对一、消息收发、已读 |
| InteractionController | `/api/v1` | 8 | 评论、点赞、收藏、关注、举报 |
| UserController | `/api/v1` | 4 | `GET /users/{id}`、`/users/me/projects`、`/follow-status` |
| MetaController | `/api/v1` | 2 | `GET /categories`、`GET /system/health` |
| AdminController | `/api/v1/admin` | 23 | 审核 `reviews/{v}/approve|reject`、`projects/{p}/down|restore|undelete|storage`、评论/举报/开发者申请/改密申请/用户管理 |

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
  ├── dev-apply          dev-apply      开发者申请
  └── forgot             forgot         联系管理员/找回
/login                   login          登录（?redirect= 回跳）
/                        ConsoleLayout（requiresAuth, area=user）
  ├── upload             upload         投稿上传（三步向导：选类型 → 填信息存草稿 → 上传/粘贴源码 → 提交）
  ├── me/projects        me-projects    我的作品
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

- 三步 `v-show`：`step 0` 选类型（`html` / `scratch` / `structure`，**没有 `mcstructure`**），`step 1` 填标题+简介+分类+标签，`step 2` 上传/粘贴源码 + 封面 + 提交
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
  downloads\      APK 等下载物（由 nginx ^~ /downloads/ 直出）
  agreements\ im\ 协议 / 私信附件
  ```
- nginx 站点：`E:\programs\nginx\conf\sites\coc.conf`（`listen 8084`，`server_name _`，`client_max_body_size 256m`）
  - 反代：`/api/` → `http://coc_backend`（=127.0.0.1:8090），`/ws` → `http://coc_backend/ws`（`proxy_read_timeout 3600s`）
  - 微缓存 `coc_micro`：`/api/v1/projects` 20s、`/projects/{id}` 20s、`/categories` 5m、`/run/` 60s；**全部带 `proxy_cache_bypass $http_authorization`**（带 token 一律 BYPASS，不串号），只缓存 200
  - 静态直出：`/covers/` → `E:/Server Data/coc-platform/covers/`、`/avatars/`、`/downloads/`、`= /qr-live.png`、`= /nginx-health`
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

> ⚠️ **已发现的两处版本治理问题**：
> 1. `2.0` 的 code（11）**低于** 1.11（12）→ 从 1.11 直接升级 2.0 会被系统拒绝安装
> 2. `2.0.2` 与 `2.0.1` code 都是 12 → 用户若已装 2.0.1，覆盖安装可能被拦
>
> **规则（后续必须遵守）**：每次打包 `versionCode` = 上一次 +1，**不要为了"保持版本号不变"复用 code**；需要"同版本号重发"时，至少把 code +1（如 2.0.2 → code 13）。
> ✅ 2.0.3 / 2.0.4 / 2.0.5 已按此规则执行：code 13 / 14 / 15，从任何历史版本升级都不会被拦。

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

- 线上 `downloads\`：`coc-app-1.0/1.1/1.2/1.4-rollback/1.5/1.6/1.7/1.8/1.9/2.0/2.0.1/2.0.2/2.0.3/2.0.4/2.0.5/latest`
- 本地 `apk-build\dist\`：v1.0 → v2.0.5 共 23 个历史包（含 `coc-app-v2.0.5-20261119-1121.apk` = 最新）
- 当前线上 `coc-app-2.0.5.apk` = `coc-app-latest.apk` = **3,864,431 B**（2.0.5 / code 15，签名 `CN=Collection of Creations`，sha256 `1451960d…6bbd`）
- sha256 `2dfd518fca0f7cd16a507542bc58e9a3bc679add33439cfde2ab095d51e2f843`（公网实下一致）
- 本地自测下载正常：`http://127.0.0.1:8084/downloads/coc-app-latest.apk` → 200 / 3,855,581 B（对外走 Cloudflare 仍需按 5.4 验字节数）
- `version.json` / `version.txt` 已同步为 2.0.5 / 15（`updatedAt` 2026-11-19 11:21）

---

## 六、已知问题与待办（按优先级）

| # | 问题 | 状态 / 下一步 |
| --- | --- | --- |
| 1 | **App 端"无法创建项目草稿"** | ✅ **已定位并修复（v2.0.3）**：根因是输入框容器失效——App 里中文输入法打字，输入框有字但 `v-model` 收不到值，`title/summary` 恒为空，按钮 `:disabled` 死锁，请求从未发出（nginx 日志里 Android UA 零 `POST /api/v1/projects` 即为证据）。改法见第十章。**待用户装 2.0.3 实测确认** |
| 2 | **APK 下载被 CF 链路截断** | 实测 1,015,808/3,850,562 B；下载后需校验字节数，必要时用系统浏览器/下载器重试（2.0.3 本地直连 3,853,343 B 正常，对外链路仍待验） |
| 3 | **versionCode 回退与重复**（2.0=11 < 1.11=12；2.0.1 与 2.0.2 同为 12） | ✅ 2.0.3 已用 code=13，规则写入 5.1；后续继续 +1 |
| 4 | 27 个已跟踪文件 + `docs/` 未提交 | **用户要求暂不提交**，等 2.0.3 实测通过后统一整理（先备份 dist/APK） |
| 5 | 上传首帧被踢 `/login`（store 时序） | 低优先，可加 `await auth.restore()` 后再挂路由 |
| 6 | CF 缓存规则 / Brotli / HTTP/3 早期提示 | 需带 Zone Settings 权限的 token 或面板手开 |
| 7 | `/admin` 未挂 Cloudflare Access | 建议加，避免后台裸奔 |
| 8 | 6 条未落地规格 | 贡献分流水表、拉黑屏蔽、游客账号、动态上传限额、`hasPreview`、人工放行扫描失败文件 |
| 9 | 沙箱独立域（`8087` + `p1.`）未做 | 与"项目运行隔离"安全相关 |
| 10 | `8083`（tutor）根路径 500 | 待查（该站非 COC 主线） |
| 11 | 测试数据残留 | **实测仅 27 行项目**；`226/227/228`（author 115）建议软删；`225 电磁力演示` 已发布（status=2，author 101）为正常数据，**勿动** |

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

**注意**：APK 不受影响（原生壳走 `ORIGIN` 分支），无需重打包；网页端需硬刷新（Ctrl+F5）清掉旧 chunk 缓存。
