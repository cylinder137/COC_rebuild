# 造物集 coc-platform · 前端开发与验证说明

> 最后更新：2026-10-15（前端收尾会话：跑通构建 + dev 冒烟 + 修 2 处数据映射）
> 作用域：本文件只描述 `frontend/`。契约以 `../contract/api-mvp.md` 为准。

---

## 1. 环境与安装

- Node：本机 nvm 的 **v24.20.0**（`node -v` 可直接用）
- 技术栈：Vue 3.5 + Vue Router 4 + Pinia 2 + Element Plus 2.8 + ECharts 5 + Axios + Vite 6

```powershell
cd E:\personalProjects\coc-platform\frontend
npm install --include=dev      # 必须带 --include=dev
npm run build                  # 主站产物 -> dist\
npm run build:user             # 用户区产物 -> dist-user\
```

**坑（已处理）**：本机环境变量 `NODE_ENV=production` 会让 npm 默认 `omit=dev`（vite 装不上）。
已在 `frontend/.npmrc` 写入 `include=dev`，所以直接 `npm install` 也行。
若 npm 提示 `install-scripts not yet covered by allowScripts`（esbuild postinstall），
执行 `npm install-scripts approve esbuild` 后再 `npm install`。

### 实测结果（2026-10-15）

| 命令 | 结果 |
| --- | --- |
| `npm install --include=dev` | ✅ `up to date in 461ms` |
| `npm run build` | ✅ `✓ built in 18.73s`（修完映射后复跑 `21.33s`），**无 warning / error** |
| `npm run build:user` | ✅ `✓ built in 15.74s`，无 warning / error |

产物分块：`element` ~1.09 MB、`charts` ~1.04 MB、`vendor` ~166 KB（gzip 后分别 ~342/343/63 KB）。

---

## 2. 运行（两个端口）

同一套代码、两种模式，用 `VITE_ZONE` 区分区域（**不搞两套代码**）。

```powershell
# 主站：8085（mode=development / VITE_ZONE=main，读取 .env）
npm run dev

# 用户内容区：8086（mode=user / VITE_ZONE=user，读取 .env.user）
npm run dev:user
```

- `vite.config.js` 里 `strictPort: true` → 端口被占直接报错，不会偷偷换端口
- **只用 8085/8086**（8082 被别的前端占用）
- dev 服务默认 `host: 127.0.0.1`，仅本机可访问
- `/api` 有 proxy 配置指向 `VITE_PROXY_TARGET`（默认 `http://127.0.0.1:8090`），
  但前端默认**直连** `VITE_API_BASE`（见下），proxy 仅备用

### 环境变量

| 文件 | 变量 | 值 |
| --- | --- | --- |
| `.env` | `VITE_API_BASE` | `http://127.0.0.1:8090/api/v1` |
| `.env` | `VITE_WS_BASE` | `ws://127.0.0.1:8090` |
| `.env` | `VITE_ZONE` | `main` |
| `.env.user` | 同上三项 | `VITE_ZONE=user` |

> 改动 `.env*` 或 `vite.config.js` 后必须重启 dev 服务（vite 不会热加载配置）。

### 冒烟验证（原始输出）

```
=== curl http://127.0.0.1:8085/ ===
HTTP/1.1 200 OK
Vary: Origin
Content-Type: text/html
Cache-Control: no-cache
Etag: W/"3dd-aVmvMXqp6m2YkGLYmdr7y6gjrzg"
--- has <div id="app"> ? ---  True

=== curl http://127.0.0.1:8086/ ===
HTTP/1.1 200 OK
Vary: Origin
Content-Type: text/html
Cache-Control: no-cache
Etag: W/"3dd-aVmvMXqp6m2YkGLYmdr7y6gjrzg"
--- has <div id="app"> ? ---  True
```

`src/` 下 **40 个模块**（.vue/.js/.css）在 8085 全部返回 200 → Vite 编译零错误。

---

## 3. 页面清单（路由）

### 站点壳 `SiteLayout`（访客可读）

| 路径 | name | 组件 | 说明 |
| --- | --- | --- | --- |
| `/` | `home` | `views/Home.vue` | 首页：项目列表 / 分类 / 排序 / 快捷标签 / 热门 Top10 / 活跃作者 |
| `/projects/:id` | `project-detail` | `views/ProjectDetail.vue` | 详情：沙箱 iframe 试玩、浏览点赞收藏、评论树、举报、源码下载、趋势图入口 |
| `/users/:id` | `user-profile` | `views/UserProfile.vue` | 个人主页：作品列表 / 关注 |
| `/dev-apply` | `dev-apply` | `views/DevApply.vue` | 开发者申请 |
| `/forgot` | `forgot` | `views/Forgot.vue` | 忘记密码 → 联系管理员 |

### 单页

| 路径 | 组件 | 说明 |
| --- | --- | --- |
| `/login` | `views/Login.vue` | 登录（支持 `?redirect=`、`?reason=` 落地） |

### 工作台壳 `ConsoleLayout`（`requiresAuth`）

| 路径 | name | 组件 | 说明 |
| --- | --- | --- | --- |
| `/upload` | `upload` | `views/Upload.vue` | 投稿上传（含 `ChunkUploader` 分片/秒传/断点续传） |
| `/me/projects` | `me-projects` | `views/MeProjects.vue` | 我的作品（含下架/拒绝原因） |
| `/me/dashboard` | `me-dashboard` | `views/MeDashboard.vue` | 数据看板（ECharts 折线，range=2 走小时表） |
| `/me/reports` | `me-reports` | `views/MeReports.vue` | 我的举报（含正反馈积分） |
| `/messages` | `messages` | `views/Messages.vue` | 消息中心（WS + 轮询兜底） |

### 管理后台 `/admin`（`requiresAuth + requiresAdmin`，`type=3`）

| 路径 | name | 组件 |
| --- | --- | --- |
| `/admin` | `admin-home` | `views/admin/AdminStats.vue` |
| `/admin/review` | `admin-review` | `views/admin/AdminReview.vue` |
| `/admin/projects` | `admin-projects` | `views/admin/AdminProjects.vue` |
| `/admin/projects/:id/storage` | `admin-storage` | `views/admin/AdminStorage.vue` |
| `/admin/comments` | `admin-comments` | `views/admin/AdminComments.vue` |
| `/admin/reports` | `admin-reports` | `views/admin/AdminReports.vue` |
| `/admin/developers` | `admin-developers` | `views/admin/AdminDevApplications.vue` |
| `/admin/password-resets` | `admin-resets` | `views/admin/AdminPasswordResets.vue` |
| `/admin/users` | `admin-users` | `views/admin/AdminUsers.vue` |
| `/admin/stats` | `admin-stats` | `views/admin/AdminStats.vue`（与 `/admin` 同一组件） |

### 兜底

| 路径 | 组件 |
| --- | --- |
| `/403` | `views/Forbidden.vue` |
| `/404` | `views/NotFound.vue` |
| `/:pathMatch(.*)*` | → redirect `/404` |

### 复用件

- 布局：`layouts/SiteLayout.vue`、`layouts/ConsoleLayout.vue`
- 组件：`AppHeader`、`AppFooter`、`AppState`（loading/error/empty/retry 统一状态块）、
  `ProjectCard`、`CommentItem`、`ChunkUploader`
- store：`stores/auth.js`（token/user/type/登录落地页）、`stores/ui.js`（主题 + 侧栏 + 登录引导弹窗；
  **`useThemeStore` 也在 ui.js 里，没有独立的 stores/theme.js**）
- 工具：`utils/format.js`（`fmtTime`、`compactNum`、`statusOf`、`TYPE_LABEL`、`pickList`、`pickTotal`、`fileUrl`）
- 样式：`styles/tokens.css`（设计变量）、`styles/global.css`
- 请求：`api/http.js`（axios 实例 + 401 单次跳转 + `safe()` 包裹）、`api/index.js`（按契约分组的 API）

### 优雅降级约定（重要）

- 页面数据一律走 `safe(promise)` → 返回 `{ ok, data, error }`，**不抛未捕获异常**
- 区块级失败用 `<AppState>` 渲染「错误文案 + 重试」；空数据渲染「空状态」
- 后端 8090 未启动时：首页显示「无法连接后端服务（127.0.0.1:8090 可能尚未启动）。请稍后重试。」+ 重试按钮，
  头部/搜索/底栏仍正常渲染，**不白屏、不弹未捕获异常堆栈**
- 需要登录的操作 → 路由守卫跳 `/login?redirect=&reason=`；互动类操作 → 弹登录引导弹窗
- `main.js` 注册了 `app.config.errorHandler` 兜底

---

## 4. 与后端对接的接口清单

前缀 `/api/v1`（= `VITE_API_BASE`），全部按 `contract/api-mvp.md §3` 实现；响应统一 `{code,message,data}`，
`code === 0` 时 axios 拦截器直接解包 `data`。

### 3.1 认证

| 方法 | 路径 | 前端用途 |
| --- | --- | --- |
| POST | `/auth/dev-apply` | 开发者申请 |
| POST | `/auth/login` | 登录（返回 `{token,user:{user_id,qq_account,nickname,type,avatar_path},must_change_password}`） |
| GET | `/auth/me` | 静默校验登录态 |
| POST | `/auth/change-password` | 首登强制改密 |
| POST | `/auth/password/forgot` | 找回申请 |

### 3.2 内容（访客可读）

| 方法 | 路径 | 前端用途 |
| --- | --- | --- |
| GET | `/projects` | 首页列表（`page,size,category_id,project_type,keyword,sort`） |
| GET | `/projects/{id}` | 详情（404 → 跳 `/404`） |
| POST | `/projects/{id}/view` | 浏览计数（静默，失败忽略） |
| GET | `/projects/{id}/stats?range=2\|7\|15\|30` | 趋势折线 |
| GET | `/projects/{id}/run` | 运行地址（`{mode,url,sandbox}`） |
| GET | `/projects/{id}/comments` | 评论树（楼中楼一层） |
| GET | `/categories` | 分类 |
| GET | `/users/{id}` | 个人主页 |
| GET | `/users/{id}/follow-status` | 关注状态（静默） |
| GET | `/system/health` | 探活（静默） |

### 3.3 互动（需登录）

`POST /comments`、`POST /comments/{id}/like`、`POST /projects/{id}/like`、
`POST /projects/{id}/favorite`、`POST /users/{id}/follow`、`POST /reports`、
`GET /reports/me`、`POST /reports/me/read`

### 3.4 投稿与文件（需登录）

`POST /projects`、`PUT /projects/{id}`、`POST /uploads/init`、`POST /uploads/{sid}/parts`、
`GET /uploads/{sid}`、`POST /uploads/{sid}/complete`、`POST /projects/{id}/submit`、
`GET /users/me/projects`、`GET /files/{fid}/content`（blob）、`POST /projects/{id}/build/retry`

### 3.5 私信 / 项目群（需登录）

`GET /im/conversations`、`GET /im/conversations/{kind}/{id}/messages`、
`POST /im/conversations/{kind}/{id}/messages`、`POST /im/conversations/{kind}/{id}/read`、
`GET /im/unread`
WebSocket：`{WS_BASE}/ws?token=<token>`，连不上自动降级为轮询/手动刷新。

### 3.6 管理端（需 `type=3`）

`GET /admin/projects`、`GET /admin/projects/{id}/storage`、
`POST /admin/reviews/{vid}/approve|reject`、`POST /admin/projects/{id}/down|restore`、
`DELETE /admin/projects/{id}`、`DELETE /admin/comments/{id}`、
`GET /admin/developer-applications`、`POST /admin/developer-applications/{id}/approve|reject`、
`GET /admin/reports`、`POST /admin/reports/{id}/handle`、`GET /admin/password-resets`、
`POST /admin/users/{id}/reset-password`、`POST /admin/users/{id}/cancel`、
`GET /admin/stats/platform`

---

## 5. 本次收尾修的问题

1. **`views/admin/AdminStats.vue` KPI 字段全部对不上**（会导致 6 张卡几乎全显示 0）
   - 后端 `/admin/stats/platform` 实际返回：`users_total / developers / admins / cancelled_users /
     projects_total / projects_published / projects_pending / projects_down / projects_rejected /
     views_today / reports_pending / dev_applies_pending / storage_root`
   - 原代码读的是 `user_count / project_count / pending_count / comment_count / report_pending`
     —— 除 `views_today` 外全部取不到，一律退化成 0（页面底部"原始数据"里却能看到真值，观感很假）
   - 已改为按真实字段名取值（保留旧名作兜底），并**移除后端没有返回的「评论总数」卡**，
     换成真实存在的「开发者数 / 已发布项目 / 待处理开发者申请」，避免展示假 0
2. **`views/MeDashboard.vue` ECharts 空数据时创建 0 尺寸画布**
   - 图表容器是 `v-show`，无数据时 `display:none`，此时 `echarts.init()` 量不到宽高
   - 已在 `renderChart()` 里加 `if (!points.value.length) return`，有数据才初始化

> 构建层面**没有需要修的编译错误**：上一轮会话留下的 40 个文件一次通过 `vite build`（两种 mode 都过）。

---

## 6. 已知未做项 / 待办

**后端侧（前端已适配，但功能不完整）**

- `GET /api/v1/run/{id}/` 后端未实现（现为 404）→ 详情页沙箱 iframe 与「试玩」按钮打不开。
  前端原样使用后端返回的 `run.url`，只在**后端没返回 url 时**才回退到
  `${origin}/static/p/{id}/index.html`。后端补上该路由即可，前端无需改。
- 评论管理：契约里没有「全站评论列表」接口。`AdminComments.vue` 走
  `GET /admin/comments`（代码里标注为兼容入口、`silent` 调用），后端未实现则优雅失败。
  建议后端补 `GET /admin/comments?project_id=&page=&size=`。
- 管理台「平台统计」缺评论总数指标（上面已从 UI 移除）。

**前端侧（有意留白）**

- 无单元测试 / E2E；无 ESLint、无 TypeScript（纯 JS）
- Element Plus 是**全量引入**（`main.js` + `element-plus/dist/index.css`），未做按需自动导入
  → `element` chunk ~1.09 MB；后续接 `unplugin-vue-components` 可显著瘦身
- ECharts 用全量 `import * as echarts from 'echarts'` → `charts` chunk ~1.04 MB；
  只有 MeDashboard/趋势图用到，可改按需引入
- `structure`（MC 结构文件）仅元数据卡片，无 3D 预览
- 未接后端 WebSocket 时的私信：走 HTTP 游标分页 + 手动刷新（WS 连接失败自动降级）
- 移动端做了基础响应式（`@media max-width: 991px`），未逐页精修

---

## 7. 排查小抄

| 现象 | 处理 |
| --- | --- |
| `Port 8085 is already in use` | `netstat -ano \| findstr :8085` → 结束对应 PID；或换个端口（但契约固定 8085/8086） |
| 页面一直「无法连接后端服务」 | 后端起没起：`curl http://127.0.0.1:8090/api/v1/system/health` 应返回 `{"code":0,...}` |
| 全局组件（`<el-xxx>`）不渲染 | `main.js` 已 `app.use(ElementPlus)` + 全量注册 `@element-plus/icons-vue`，别只引图标 |
| 改 `.env` 不生效 | dev 服务不热加载 env/config，重启 |
| 构建报 `element-plus/theme-chalk/dark/css-vars.css` 找不到 | 装的是按需主题版；本仓库依赖全量包 |
