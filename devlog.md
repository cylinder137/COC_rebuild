# COC 项目开发日志（devlog）

本文件用于追踪、追述 COC 平台按「期」推进的优化改动内容：每完成一次优化改动，就在本文件新增一条记录，写清改动目的、涉及文件与关键行号，方便后续开发、交接和版本回归时快速回溯。记录按时间倒序排列，最新改动放在最前面。

---

## 2026-11-19 · 项目详情页：手机端默认手机版视图 + 电脑版切换 + 全屏横屏与控制侧栏（v2.0.4）

> 本条属于 **COC 五期测试** 的改动，已随 **2.0.4 / versionCode 14** 打包发布。

### 改动目的

1. 手机端 App 里 HTML 作品原来一律按 1280×720 渲染再整体缩小，等于「把电脑版塞进手机」，字小、点不准，也不是作者希望玩家在手机上看到的样子。改为**默认按手机格式访问**（iframe 直接拿到设备宽度，作品自己走手机断点）。
2. 「我要看作者原版分栏」这种需求仍然存在，所以在**详情区（展开详情）**提供「手机版 / 电脑版」自主切换，而不是强制其一。
3. 手机端进全屏后**自动横屏**，并提供**左旋 / 右旋**按钮，适配不同握持习惯。
4. 全屏时**侧栏（控制栏）默认隐藏**，点右边缘把手才展开；展开是浮层，**不挤压作品画面**。

### 具体改动

全部集中在 `frontend/src/views/ProjectDetail.vue`：

| 位置 | 行号 | 改动说明 |
| --- | --- | --- |
| 模板 · 舞台 | L51-L84 | 新增 `.stage` 作为作品画面的「虚拟视口」：缩放与旋转只作用在它身上，`iframe` 内部代码一行不改，`sandbox="allow-scripts"` 保持不变 |
| 模板 · 电脑版分支 | L60-L72 | 原 `needScale`（窄屏一律缩放）改为 `desktopSim`：只在「窄屏 + HTML + 用户选了电脑版」时固定 1280×720 再等比缩放，并显示「电脑版 1280×720 · 等比缩放」小标 |
| 模板 · 手机版分支 | L73-L82 | 默认分支：`iframe` 铺满舞台（设备宽度），作品自适应手机 |
| 模板 · 全屏侧栏 | L86-L108 | 右边缘 26px 把手 `.rail-tab` + 浮层 `.rail`：重载 / 左旋 / 右旋 / 视图切换 / 新窗口 / 退出。默认收起，展开前后舞台尺寸不变 |
| 模板 · 视图切换 | L153-L167 | 「展开详情」里新增 `.view-mode-row`（手机版 / 电脑版 分段按钮）+ 一行说明文案；顶栏同时显示当前视图小标（`view-pill`） |
| 脚本 · 视图状态 | L413-L444 | 新增 `RUN_VIEW_KEY='coc_run_view'`（localStorage 记住同设备的选择）、`BASE_W/BASE_H`、`isHtml`、`canSwitchView`、`desktopSim` |
| 脚本 · 舞台几何 | L446-L485 | `box` / `stageBox` / `stageStyle`：旋转 90°/270° 时宽高互换（`translate + rotate`），`iframe` 拿到的是真正的横向视口；`holderStyle` / `frameStyle` 负责电脑版的等比缩放 |
| 脚本 · 缩放计算 | L486-L503 | `updateScale`：全屏按「横竖都装得下」计算（旋转时宽高互换），非全屏按容器宽度计算 |
| 脚本 · 全屏与旋转 | L801-L856 | `toggleFull` 先 `requestFullscreen()` 再 `screen.orientation.lock('landscape')`；锁定不生效（WebView 限制 / 桌面浏览器）自动兜底 CSS `rot=90`。`rotateView(±1)` 原生锁生效时切 `landscape-primary/secondary`，否则切 CSS 90°/270° |
| 脚本 · 退出回收 | L836-L868 | `exitFull` 与 `onFullscreenChange`（返回手势 / ESC / F11）统一还原旋转、`orientation.unlock()`、清 `railOpen`，避免卡在 fixed 遮罩里 |
| 脚本 · 监听 | L891-L908 | 新增 `resize` → `syncNarrow`、`fullscreenchange` → `onFullscreenChange`；`ResizeObserver` 观察舞台尺寸变化 |
| 样式 · 舞台 | L944-L961 | `.viewer-body` 不再用 `padding-top` 撑比例（改由 `.stage` 控制），全屏时 `.viewer-body{flex:1}` |
| 样式 · 侧栏 | L992-L1021 | `.rail-tab`（把手）/ `.rail`（浮层，`position:absolute` + `translateX(64px)` 收起）/ `.rail-btn` |
| 样式 · 视图切换 | L1024-L1036 | `.view-mode-row` / `.seg`（分段按钮，选中态主色） |
| 样式 · 手机端 | L1087-L1088 | `.viewer{position:static}`：手机端不再吸顶（默认手机版后画面占比变小，吸顶只会盖住下面的详情与按钮） |
| 版本号 | `utils/platform.js` L27 | `APP_BUILD` 2.0.3 → **v2.0.4** |
| 版本号 | `components/AppHeader.vue` L156 | `APK_RELEASE` 同步 **2.0.4**（该常量在下一条改动中已随后台下载页调整移除） |

### 两个必须记住的坑

1. **grid 子项 `min-width:auto`**：电脑版舞台初始 1280px 会把 `.main-col` 撑宽，`updateScale` 量到的宽度永远是 1280 → 缩放恒为 1，画面被裁。已改为 `grid-template-columns:minmax(0,1fr) 320px` + `.main-col{min-width:0}`，并把 `frameScale` 初值设为 `0.3` 而不是 `1`。
2. **`isNarrow` 不能只看宽度**：横屏手机 `innerWidth` 会到 800+，会被误判成桌面端（视图切换按钮消失、缩放逻辑走错分支）。已改为 `UA_MOBILE || innerWidth < 768`。

### 验证方式（无头 Edge + CDP，脚本留在 `E:\Server Data\tmp\coc-verify\`）

| 场景 | 结果 |
| --- | --- |
| 手机 390×844 进详情 | `iframe` 视口 356×267（设备宽度，零缩放），顶栏「手机版」 |
| 切电脑版 | 舞台 356×200，`iframe` 内视口 1280×720，等比缩放 |
| 切回手机版 | 恢复 356×267 |
| 点全屏 | 舞台 796×390（横向），`matrix(0,1,−1,0,390,0)` = 旋转 90°，`iframe` 视口 796×390 |
| 打开控制侧栏 | 侧栏位移 64px → 0，舞台仍 796×390（不挤压内容） |
| 左旋 | 矩阵切到 270°，画面继续铺满 |
| 退出全屏 | 回到 356×267，`transform:none`，无残留 |
| 桌面 1280×900 | 舞台 876×522 正常铺满，无切换按钮、无缩放，0 error |

### 备注

- 只对 `project_type === 'html'` 且已发布的作品启用视图切换；scratch 与不可运行状态不受影响。
- 桌面端行为完全不变（`isNarrow` 为 false 时 `canSwitchView` / `desktopSim` 均为 false）。
- 详细的状态与版本记录见 `docs/PROJECT-STATUS-20261115.md` 第十一章。

---

## 2026-11-19 · 官网新增 App 版本下载页（仅网页端）

### 改动目的

官网此前只能通过顶栏「下载 App」直接拿到 `coc-app-latest.apk`，用户无法查看历史版本、也无法按版本选择。本次为**官网网页端**新增 `/downloads` 页面，集中列出已发布的所有 APK 版本（版本号、发布日期、包大小、更新说明），由用户自行决定下载哪一个；App 端内部内容与行为不受影响。

### 具体改动

| 文件 | 行号 | 改动说明 |
| --- | --- | --- |
| `frontend/src/views/Downloads.vue` | L1-L133 | **新增页面**：最新版卡片（大号版本号 + 下载按钮）、历史版本列表（版本 / 日期 / 大小 / 说明 + 下载按钮）、安装提示。最新版优先读取服务器 `/downloads/version.json`（nginx 已单独放行），取不到时回落到页面内 `RELEASES` 常量首项；历史列表来自该常量 |
| `frontend/src/router/index.js` | L17 | 在 `SiteLayout` 下新增 `downloads` 路由，`meta.title = 'App 下载'` |
| `frontend/src/components/AppHeader.vue` | L63-L65 | 桌面端「下载 App」入口由直链 APK 改为跳转 `/downloads` |
| `frontend/src/components/AppHeader.vue` | L69-L77 | 手机端顶栏下载图标同样改为跳转 `/downloads`（仍仅网页端显示，App 内不出现） |
| `frontend/src/components/AppHeader.vue` | L133-L139、L153-L155 | 移除仅用于直链的 `ORIGIN` 导入与 `APK_RELEASE` / `apkUrl` 常量（缓存穿透改由下载页使用版本化文件名解决） |

### 备注

- 下载链接统一走 `fileUrl()`，网页端为同源相对路径，Capacitor 壳内自动补线上绝对源。
- 页面展示的版本、日期、字节数取自服务器 `downloads/` 目录实机文件（截至 2026-11-19 含 1.0 至 2.0.4）。
- 本次同属 **COC 五期测试** 过程中的小改动，已随 2.0.4 一起构建验证。

---

## 2026-11-19 · 登录/注册入口优化 + App 端固定视口

> ⚠️ 本条属于 **COC 五期测试** 过程中的小改动，仅包含下列内容；**并不代表 COC 五期测试的全部更新内容**，后续五期改动会继续追加到本文件。

### 改动目的

1. 手机端顶栏的「注册」入口原来在 ≤767px 被隐藏、App 内更是完全不显示，只剩「登录」按钮，注册按钮明显弱于登录按钮。
2. 登录失败（账号或密码错误）时没有引导，用户容易卡在登录页，无法判断是否需要注册。
3. 登录页底部的「申请成为开发者」只是文字链接，视觉权重远低于登录按钮。
4. App（Capacitor WebView）内部分页面存在页面级横向滚动：固定宽度弹窗（如 640px 协议弹窗）、宽表格等超出屏幕宽度的元素会把整页撑宽，无法贴合手机屏幕渲染。

### 具体改动

| 文件 | 行号 | 改动说明 |
| --- | --- | --- |
| `frontend/src/views/Login.vue` | L35-L42 | 「登录」「注册开发者」改为上下排列的同尺寸通栏按钮（仅颜色区分主次），替换原底部文字链接 |
| `frontend/src/views/Login.vue` | L45-L47 | 底部链接区只保留「忘记密码？联系管理员」 |
| `frontend/src/views/Login.vue` | L102-L111 | 登录失败捕获：仅对 401（账号或密码错误）在 600ms 后自动跳转注册页；403（审核中/禁用/注销）与网络错误不跳转 |
| `frontend/src/views/Login.vue` | L113-L117 | 新增 `goRegister()`：跳转 `/dev-apply` 并携带已填 QQ 号 |
| `frontend/src/views/Login.vue` | L155-L159 | 新增 `.auth-actions` 样式：纵向排列、通栏、间距 12px |
| `frontend/src/components/AppHeader.vue` | L119-L127 | 注册按钮去掉 `v-if="!isApp"` 与 `hide-sm`，手机端与 App 内均可见；窄屏文案缩为「注册」，桌面端仍为「注册开发者」 |
| `frontend/src/components/AppHeader.vue` | L296-L307 | 窄屏/App 下注册按钮标签切换、收紧顶栏间距、窄屏仅保留品牌图标，避免按钮挤爆顶栏 |
| `frontend/src/api/http.js` | L94-L99 | 新增 `skipAuthRedirect` 分支：登录接口自身返回 401 时不触发全局「登录已失效」跳转/重载，改由调用方处理，并显示后端原始错误信息 |
| `frontend/src/api/index.js` | L5 | `authApi.login` 请求带上 `{ skipAuthRedirect: true }` |
| `frontend/src/views/DevApply.vue` | L71-L83 | 支持从 `?qq_account=` 预填 QQ 号（登录失败跳转时续填） |
| `frontend/src/styles/global.css` | L116-L138 | App 内（`html.is-native`）全局锁定视口宽度：`overflow-x: clip` 禁用页面级横向滚动、保留内部滚动容器；窄屏下固定宽度弹窗/消息框收窄为 `calc(100vw - 32px)` |
| `frontend/src/main.js` | L16-L28 | App 内改写 viewport 为固定宽度、`maximum-scale=1`、`user-scalable=no`，禁止缩放把页面拉出屏幕 |

### 备注

- 全部规则均限定在 App（`html.is-native`）或 ≤767px 场景，网页桌面端行为不变。
- 按用户安排，本次改动**未单独打包**，已随后续 2.0.4 一起构建验证（冒烟：`/login`、`/dev-apply`、`/` 均正常渲染，0 console error）。
