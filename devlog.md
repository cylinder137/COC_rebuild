# COC 项目开发日志（devlog）

本文件用于追踪、追述 COC 平台按「期」推进的优化改动内容：每完成一次优化改动，就在本文件新增一条记录，写清改动目的、涉及文件与关键行号，方便后续开发、交接和版本回归时快速回溯。记录按时间倒序排列，最新改动放在最前面。

---

## 2026-11-20 · v2.1.5：虚拟摇杆 PWM 连续占空比调制

> 对 2.1.3 引入的摇杆方波（PWM）微调做重构：从离散 on/off 阶段切换改为每帧连续占空比，打包为 **2.1.5（versionCode 22）**。

### 改动目的

2.1.3 的 PWM 微调用「固定 on 时长 + on/off 阶段切换」的离散模式，推杆幅度变化时手感不够线性，且 PWM 频率与 rAF 不同步会产生拍频抖动。本次改为连续占空比调制，让微调更平滑、导通占比更精确。

### 具体改动

| 文件 | 改动说明 |
| --- | --- |
| `frontend/src/components/VirtualPad.vue` | 去掉固定 on 时长与 on/off 阶段切换的离散模式，改为每帧连续占空比 |
| 同上 | PWM 60Hz：每帧一个周期，用 Bresenham 误差扩散累加器摊占空比，长期导通占比精确等于 duty，且不受 rAF 与 PWM 相位对齐的拍频抖动 |
| 同上 | duty 随推杆幅度线性连续变化（死区 0.16 → 全速 0.82） |
| `frontend/src/utils/platform.js` | `APP_BUILD` 升 `v2.1.5` |
| `frontend/src/views/Downloads.vue` | 新增 2.1.5 条目，2.1.4 回填实际包体积 |

### 备注

- 本次为纯前端改动，无后端 / 数据库变更。

---

## 2026-11-20 · v2.1.4 发布汇总

> 本版把 11-20 的几项管理端 / 前端改动一起打包发布，versionCode 20 → **21**。后端改动需单独 `mvn package` 并重启 `coc-backend`，不在 APK 打包范围内。

### 本版包含

| 改动 | 说明 |
| --- | --- |
| 管理端 Bug 反馈增删改查 | 后端 `/admin/bug-feedback` 补详情/新增/编辑/删除端点（新增支持代用户补录）；前端 AdminBugFeedback 页加新增按钮与行内编辑/删除 |
| 管理员后台「设为管理员」 | `POST /admin/users/{id}/set-admin`，账号管理页新增按钮（已管理员/已注销禁用）+ 二次确认，写审计 |
| 项目详情「复制链接」修正 | 统一用 `SITE_ORIGIN` 拼链接，改为「标题 + 链接」分享格式 |
| 手机端控制台导航修复 | 侧边栏隐藏后加横向滚动菜单 |

### 版本标记

- `frontend/src/utils/platform.js`：`APP_BUILD` 升 `v2.1.4`
- `frontend/src/views/Downloads.vue`：新增 2.1.4 条目，2.1.3 回填实际包体积

---

## 2026-11-20 · 项目详情「复制链接」修正（正确 baseurl + 新格式）

> 详情页「复制链接」原来复制 `window.location.href`，App 壳里是 `https://localhost`、本地调试是 `127.0.0.1:8084`，复制给别人打不开；且格式不符合分享需求。本次修正为固定站点源 + 统一分享格式。

### 改动目的

修正复制链接的 baseurl（统一用站点唯一对外源 `SITE_ORIGIN`），并把复制内容改为「标题 + 链接」两行的分享格式。

### 具体改动

| 文件 | 改动说明 |
| --- | --- |
| `frontend/src/views/ProjectDetail.vue` | `copyLink()`：改为 `${SITE_ORIGIN}/projects/${pid}` 拼链接；复制文本格式 `【造物集】{title}\n{link}`；新增 `import { SITE_ORIGIN } from '@/api/http'` |
| `frontend-app/src/views/ProjectDetail.vue` | 同上（App 端同代码库，同步修正） |
| `frontend-app/src/api/http.js` | 补导出 `SITE_ORIGIN = 'https://collectionofcreations.uk'`（App 端原本无此常量；`frontend` 的 http.js 已有） |

### 备注

- **链接格式**：`【造物集】{项目标题}\n{https://collectionofcreations.uk/projects/{项目id}}`，标题与链接以换行分隔。
- **baseurl 来源**：统一走 `SITE_ORIGIN`（`https://collectionofcreations.uk`），不碰 `window.location.origin`（App 壳里是 localhost）。
- **无数据库 / 接口变更**：纯前端改动。
- **生效仍需重建**：前端 `npm run build` 重建 dist；App 端上线需重新打包（内置资源不自动更新）。

---

## 2026-11-20 · v2.1.4：管理员后台支持「设为管理员」

> 后台「账号管理」页新增把普通 / 开发者用户提升为管理员的能力（`user.type` 1/2 → 3）。

### 改动目的

管理员需要在后台直接把他人设为管理员，而不是手工改数据库；同时保证操作有审计、有安全边界。

### 具体改动

| 文件 | 改动说明 |
| --- | --- |
| `backend/.../mapper/UserMapper.java` | 新增 `updateType`：`UPDATE user SET type=#{type}, updated_at=NOW()` |
| `backend/.../service/AdminService.java` | 新增 `setAdmin(userId, ip)`：校验「用户存在 / 非已管理员 / 非已注销」→ 置 type=3 → 写审计 + 通知目标用户 |
| `backend/.../controller/AdminController.java` | 新增 `POST /api/v1/admin/users/{user_id}/set-admin` |
| `frontend/src/api/index.js` | `adminApi` 新增 `setAdmin(uid)` |
| `frontend/src/views/admin/AdminUsers.vue` | 操作列新增「设为管理员」按钮（已管理员 / 已注销时禁用）+ 二次确认弹窗 |

### 备注

- **无数据库结构变更**：复用 `user.type`（3=管理员）。
- **即时生效**：`type` 由 `AuthInterceptor` 每次请求从库读取（`AuthContext.isAdmin()` 判断），改完 `type=3` 下一次请求即具备管理员权限，无需失效 JWT 或重登。
- **安全边界**：已注销（`status=3`）与已管理员（`type=3`）不可操作；操作写 `operation_log` 审计。
- **生效仍需重建**：后端 `mvn package` 后重启 `coc-backend`；前端 `npm run build` 重建 dist。

---

## 2026-11-20 · v2.1.3：手机端虚拟摇杆优化（方波微调 + 摇杆吸附）

> 本次改动来自 PR #1（`feature/vpad-mobile-opt`），打包为 **2.1.3（versionCode 20）**。

### 改动目的

五期测试反馈：虚拟摇杆小幅推杆时模拟量不细腻、触摸热区偏小、偏心按下手感生硬，且切后台偶发卡键。本次对摇杆做一轮手感优化。

### 具体改动

| 文件 | 改动说明 |
| --- | --- |
| `frontend/src/components/VirtualPad.vue` | 摇杆小幅推杆改用按键方波（PWM）模拟模拟量，占空比随幅度变化实现慢速微调 |
| 同上 | 触摸热区扩大至约 1.9 倍；偏心按下时底座吸附至拇指落点，松手弹回原位 |
| 同上 | 切后台自动松键防卡键；禁用长按菜单与点按高亮；卸载时清理 rAF |
| `frontend/src/utils/platform.js` | `APP_BUILD` 升 `v2.1.3`（顶栏构建标记） |
| `frontend/src/views/Downloads.vue` | 版本清单新增 2.1.3；2.1.2 回填实际包体积 |

### 备注

- 本次同属 **COC 五期测试** 过程中的优化，随 2.1.3 一起构建并发布。

---

## 2026-11-19 · v2.1.2：素材直链统一站点源 + 新增独立「上传素材」页

> 两件事都属于**「入口/链接指向错位」**：一个把用户带到了打不开的地址，一个把用户带到了不该去的页面。打包为 **2.1.2（versionCode 19）**。

### 一、素材链接全是 localhost

**现象**：素材库里点「复制直链」、图片右键复制地址、分享出去的素材链接，拿到手全是 `https://localhost/media/22/x.png` 或 `http://127.0.0.1:8084/media/...`，别人打不开（五期反馈）。

**根因**：`utils/format.js` 的 `absoluteUrl()` 用 `window.location.origin` 补全相对路径：

```js
// 旧代码
export function absoluteUrl(p) {
  if (!p) return ''
  if (!/^https?:\/\//i.test(p)) return window.location.origin + p   // ← 罪魁
  return p
}
```

`window.location.origin` 是「**当前页面**的源」，而当前页面有三种源，全都不是对外地址：

| 环境 | `window.location.origin` | 结果 |
| --- | --- | --- |
| Capacitor App 壳 | `https://localhost` | 壳里根本没有 `/media` 这个路径 → 图裂、链接作废 |
| 本地调试 | `http://127.0.0.1:8084` | 别人电脑上没有这个服务 |
| 线上网页 | `https://collectionofcreations.uk` | 只有这一种是对的 |

即「只有用户刚好在线上用网页版时才正确」，而手机端才是主场景，所以几乎必错。

**修复**：在 `api/http.js` 里立一个**唯一站点源**常量，凡是「要交给用户带走的地址」一律用它，不再问浏览器：

```js
// api/http.js:14-20
const PROD_ORIGIN = 'https://collectionofcreations.uk'
/**
 * 站点唯一对外源。凡是「要交给用户带走」的地址（素材直链、分享链接）都必须用它，
 * 不能用 window.location.origin —— App 壳里那是 https://localhost，本地调试是 127.0.0.1:8084，
 * 拼出来的链接别人打不开（五期反馈：素材链接全是 localhost）。
 */
export const SITE_ORIGIN = PROD_ORIGIN
```

`utils/format.js` 两处跟着改（`:58`、`:69`）：

```js
export function mediaUrlOf(a) {
  if (!a) return ''
  const raw = a.url ? a.url
    : (a.asset_id && a.file_name
      ? `/media/${a.asset_id}/${a.file_name.split('/').map(encodeURIComponent).join('/')}`
      : '')
  return absoluteUrl(raw)          // 统一走 absoluteUrl，不再各拼各的
}

export function absoluteUrl(p) {
  if (!p) return ''
  const s = String(p)
  if (s.startsWith('data:') || s.startsWith('blob:')) return s
  if (!/^https?:\/\//i.test(s)) return SITE_ORIGIN + (s.startsWith('/') ? s : '/' + s)
  try {
    const u = new URL(s)
    // 历史数据里已经存了 localhost 的绝对地址，这里顺手改写，避免老链接继续作废
    if (/^(localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1\])$/i.test(u.hostname)) {
      return SITE_ORIGIN + u.pathname + u.search + u.hash
    }
    return s
  } catch (e) { return s }
}
```

最后那个 `URL` 改写是给**存量数据**兜底的：之前用户已经把 `http://localhost:8084/media/...` 粘进自己的 HTML 里，前端再渲染时会被就地换成线上地址。另外 `views/Assets.vue:115` 的示例文本也从 `{{ siteOrigin }}` 取常量（`:9`），让用户第一眼看到的就是完整域名，而不是 `/media/12/x.png` 这种相对路径。

**验证**（三套环境，脚本在 `E:/Server Data/tmp/coc-verify/`）：

| 场景 | 脚本 | 结果 |
| --- | --- | --- |
| 普通网页（`127.0.0.2:8084`，避开壳分支） | `link-211.cjs` | 所有 `src` 均以 `https://collectionofcreations.uk/media/` 开头，`badSrcs: []`，图片 `naturalWidth` 360/497（真加载） |
| 模拟 Capacitor 壳（`localhost:8084`，`is-native` 生效） | `shell-211.cjs` | `isNativeClass: true`，图片仍从线上源加载成功 |
| 真实上传一次 | `upload-flow-212.cjs` | 拿到 `https://collectionofcreations.uk/media/22/up-test.png`，`HEAD` → `200 / image/png / 50331 B` |

产物层面再确认一遍：最终 bundle 里 `location.origin` 出现 **0** 次，`collectionofcreations.uk` 恰好 1 处（`BottomTabBar`、`Home` 里的域名是分享文案，本来就有）。

> 关于「写死域名安不安全」：线上 nginx 只有一个 `server_name _;` 的兜底 server（配 `conf/sites/coc.conf`），没有第二个对外域名，所以固定源不会把某个域名下的访问指错。

### 二、点「素材上传」直接跳到素材概览

**现象**：网页版点「素材」卡 / 侧栏入口，进去是**已上传素材的列表**（概览），想上传还得自己在页面上找按钮，容易误以为「点了没反应」或「上传坏了」。

**修复**：新增独立页面 `views/AssetUpload.vue`，路由与入口一起接上。

| 项 | 内容 |
| --- | --- |
| 路由 | `router/index.js:36` → `me/materials/upload`，`name: 'asset-upload'`，`requiresAuth: true` |
| 页面 | `views/AssetUpload.vue`：`AssetUploader` 上传区 + 「本次上传成功」清单（每条带可复制的直链）+ 用法提示卡 |
| 向导入口 | `views/Upload.vue:320` `MATERIALS_CARD.to` 改指 `/me/materials/upload` |
| 侧栏入口 | `layouts/ConsoleLayout.vue:60`「创作」组内新增「上传素材」（`UploadFilled` 图标），排在「我的素材」上面 |

页面里的直链展示一律用 `SITE_ORIGIN`，并用 `absoluteUrl(mediaUrlOf(a))` 兜一层，保证「上传完立刻复制到手的那个链接」就是能用的对外链接：

```js
const absUrlOf = (a) => absoluteUrl(mediaUrlOf(a))
function onDone(data) {
  if (data?.asset_id) uploaded.value.unshift(data)   // 本次会话的新上传，置顶
  loadSeries()
  ElMessage.success('上传成功，可以复制直链了')
}
```

上传成功不清空列表、只往前面插，是为了让用户「传完还能回头复制刚传的那个」；素材概览页（`/me/materials`）保持原样，负责浏览/删除/复制历史素材，两个页面的职责分开。

**验证**（`uploadpage-212.cjs`，移动 390×844 + 桌面 1280×900，最终产物）：

| 检查 | 暗色 | 亮色 |
| --- | --- | --- |
| 向导「素材」卡落点 | `/me/materials/upload` | `/me/materials/upload` |
| 标题 | 上传素材 | 上传素材 |
| 上传区存在 | ✔ | ✔ |
| 提示卡存在 | ✔ | ✔ |
| 页面底色 | `rgb(13,15,21)` | `rgb(246,247,251)` |
| `pageerror` / `requestfailed` | 0 | 0 |

侧栏导航另做了点击回归（`probe-nav3.cjs`）：`/me/materials` → 点「上传素材」→ `/me/materials/upload`（标题「上传素材」）→ 点「我的素材」→ 回到 `/me/materials`，全程 0 报错。

### 三、本次涉及文件

| 文件 | 改动 |
| --- | --- |
| `frontend/src/api/http.js` | 新增 `PROD_ORIGIN` / `SITE_ORIGIN` 常量（`:14-20`） |
| `frontend/src/utils/format.js` | `mediaUrlOf` 统一走 `absoluteUrl`（`:58`）；`absoluteUrl` 改用 `SITE_ORIGIN` 并改写本机源历史链接（`:69`） |
| `frontend/src/views/AssetUpload.vue` | **新增**，独立上传页 |
| `frontend/src/router/index.js` | 新增 `me/materials/upload` 路由（`:36`） |
| `frontend/src/layouts/ConsoleLayout.vue` | 侧栏新增「上传素材」（`:60`） |
| `frontend/src/views/Upload.vue` | 素材卡改指上传页（`:320`） |
| `frontend/src/views/Assets.vue` | 示例文本用完整域名（`:9`、`:115`） |
| `frontend/src/views/Downloads.vue` | 补 2.1.2 条目 |
| `frontend/src/utils/platform.js` | `APP_BUILD` → `v2.1.2` |
| `android/app/build.gradle` | `versionCode 19` / `versionName "2.1.2"` |

### 四、发布数据

| 项 | 值 |
| --- | --- |
| 版本 | 2.1.2 / versionCode 19 |
| APK | 3,891,763 B |
| sha256 | `f897260a24b1604f27745eb59f5d9395cfc6a2c00ad536f1ab665ce4e3393b06` |
| 前端入口 | `assets/index-CmwXZJxb.js`（dist 与 APK 内 `assets/public/index.html` 一致，已 unzip 自查） |

### 五、经验

1. **「给用户带走的地址」和「当前页面的地址」是两个概念。** 只要一个 URL 会被复制到别处（剪贴板、分享、写进别人 HTML），就必须用固定站点源，绝不能读 `window.location`——它在 App 壳、本地调试、线上三种环境下给出三个不同答案，而只有一个是能用的。
2. **多环境差异要靠「常量集中」消灭，而不是靠判断分支。** 这次没有写成 `IS_NATIVE_SHELL ? prod : location.origin`，而是直接 `SITE_ORIGIN = PROD_ORIGIN`：因为本地/壳里拼出来的链接**本来就不该发给用户**，没有需要保留的场景。
3. **入口错位类 bug 先问「这个入口想让人干什么」。** 素材概览页的职责是「看已有什么」，上传页的职责是「往里加」，合成一个页面必然有一半用户找不到北。分开之后两个入口各自直达目的，概览页也不受影响。
4. **`a[href]` 不是万能的导航断言。** Element Plus 的 `el-menu-item` 渲染成 `<li role="menuitem">`，用 `a[href]` 查会得到「菜单项不存在」的假结论；要么按文本查 `aside .el-menu-item`，要么直接点一次看 URL 变化。

---

## 2026-11-19 · v2.1.1：恢复投稿第一步的类型选择 + 修复暗色下向导操作条变白

> 2.1.0 修的是「打包产物」和「按钮配色」，这次修的是**流程入口**和一个**从来没定义过的 CSS 变量**。打包为 **2.1.1（versionCode 18）**。

### 一、问题一：一进「上传」就被默认成了 HTML

**现象**：手机端点底栏「上传项目」进去，直接就是「填写信息」那一步，类型（HTML / sb3 / 素材）根本没得选，看起来像被默认成了 html。

**根因**：`views/Upload.vue` 的本地暂存 + 恢复逻辑有三处叠加成了这个坑：

```js
// 旧代码
function persistDraft() {                 // 只要没有 project_id 就写暂存
  localStorage.setItem(DRAFT_KEY, JSON.stringify({ v: 1, step: step.value, project_type: form.project_type, ... }))
}
function restoreDraft() {
  for (const k of [...]) if (d[k]) form[k] = d[k]
  if (form.project_type) step.value = 1   // ← 罪魁：有类型就直接推进第 2 步
  return !!(form.title || form.summary)
}
```

1. 用户在第 1 步**点一下类型卡**（哪怕什么都没填、直接退出），`watch(form)` 就会把 `project_type: 'html'` 写进 `localStorage`；
2. 下次进 `/upload`，`restoreDraft()` 命中 `project_type` → **`step.value = 1` 直接跳过类型选择**；
3. 而且 `restoreDraft()` 返回 `!!(title||summary)` 是 false，连「已恢复上次填写」的提示都不弹 —— 用户完全无从察觉。

于是「点过一次 html」就变成了永久默认，`localStorage` 不清就永远回到第 2 步。

**修复**（两处，缺一不可）：

```js
// 1) 暂存不再记录 step（没人读，只是让人误以为「恢复进度」是设计好的）
localStorage.setItem(DRAFT_KEY, JSON.stringify({ v: 1, project_type, title, summary, description, category_id, tags }))

// 2) 恢复时绝不改 step；类型只在「确实填过内容」时才恢复高亮
const hasContent = !!(form.title || form.summary)
if (hasContent && d.project_type) form.project_type = d.project_type
return hasContent
```

这样：内容照常恢复，但**第 1 步永远先出现**；只有「填过标题/简介」的老草稿才会把上次的类型高亮出来，让用户自己确认一次再往下走。

### 二、第三张卡：「素材」

类型网格本来就设计成三列（2.0.6 之前是 html / scratch / structure），2.0.6 把结构文件换成素材库后**只剩两张卡**，第三格空着，入口等于丢了。这次补回来：

| 卡片 | 说明 | 行为 |
| --- | --- | --- |
| HTML 网页 | zip 或 html 包，可含 JS | 选中 → 下一步（人工审核） |
| Scratch 作品 | .sb3 / .sb2 积木项目 | 选中 → 下一步（免审直发） |
| **素材** | 图片 / 视频 / 音频 / JSON，上传后复制直链 | **不选中，点了直接 `router.push('/materials')`** |

素材卡用**虚线边框 + 右上角箭头**（`.tcard.goto`）跟两张类型卡区分开，避免用户以为「素材」也是一种作品类型；卡片下方补一句「素材不会单独成『作品』，上传后拿到直链，写进自己的 HTML 里即可引用」。

顺带把页头那个「该类型需管理员人工审核」的标签改成按状态显示 —— 没选类型时它显示的是「请先选择发布类型」（原来 `project_type` 为空时 `isAutoApproved('')` 为 false，一进来就提示「需审核」，纯属误导）。

### 三、问题二：暗色下「上一步 / 保存并继续」的背景是白的

**根因（一句话）**：`styles/global.css` 里写的是 `background: var(--c-bg, #fff)`，而 **`--c-bg` 这个变量在 `tokens.css` 里根本不存在** —— 于是永远取兜底的 `#fff`。浅色模式白底正好对，看不出来；深色模式就是一条白横条压在卡片上。

```css
/* 旧 */
background: var(--c-bg, #fff);
border-top: 1px solid var(--c-line, rgba(0, 0, 0, 0.08));   /* --c-line 也不存在 */
/* 新 */
background: var(--c-bg-card);            /* 吸底条长在卡片内部，底色必须跟卡片走 */
border-top: 1px solid var(--c-border);
```

> `--c-border` 存在（tokens 里有明暗两套），`--c-line` 不存在 —— 顺带把这条分隔线也修了：原来那 8% 黑线在深色底上等于隐形。

全站搜了一遍 `var(--c-bg[,)]`，**只有这一处**（其余都规范地用 `--c-bg-card` / `--c-bg-sunken` / `--c-bg-page`），所以不存在同批次的遗漏。

**实测数值**（无头 Chromium，390×844 移动视口，`.upload-actions` 的 `getComputedStyle`）：

| 取色点 | 深色 | 浅色 |
| --- | --- | --- |
| 操作条背景 | `rgb(21, 25, 35)` = `#151923`（= `--c-bg-card`） | `rgb(255, 255, 255)` |
| 分隔线 | `rgb(42, 48, 64)` | `rgb(226, 229, 238)` |
| 「上一步」背景 / 文字 | `rgb(21,25,35)` / `rgb(164,172,189)` | `#fff` / `rgb(84,92,107)` |
| 「保存并继续」背景 | `rgb(108,134,255)` | `rgb(79,110,247)` |

### 四、涉及文件

| 文件 | 改动 |
| --- | --- |
| `frontend/src/views/Upload.vue` | 类型卡加「素材」跳转卡 + `.tcard.goto` 样式；页头标签按是否已选类型显示；`persistDraft` 去掉 `step`；`restoreDraft` 不再改 `step`、类型仅在填过内容时恢复 |
| `frontend/src/styles/global.css` | `.upload-actions` 的 `--c-bg`/`--c-line` → `--c-bg-card`/`--c-border` |
| `frontend/src/utils/platform.js` | `APP_BUILD='v2.1.1'` |
| `frontend/src/views/Downloads.vue` | 新增 2.1.1 条目（`size: 0`，见下） |
| `apk-build/coc-app/android/app/build.gradle` | `versionCode 18` / `versionName "2.1.1"` |

### 五、发布与验证

| 项 | 值 |
| --- | --- |
| 包大小 | 3,887,190 B |
| sha256 | `bcb26115e0e7e013a6e7147994dc7b416009ccac7309f1fe5abfd61d87f81d0a` |
| 签名 | `CN=Collection of Creations`，证书 SHA-256 `1324f12e…2958b5`（同历史包） |
| versionCode | 18（17 → 18） |
| 内容自检 | APK 内 `assets/public/index.html` = `assets/index-RSD3QEwr.js` = `frontend/dist` ✅（70 个前端文件） |
| 本地 / 公网 | `version.json` 均 2.1.1；APK `Content-Length: 3887190`；网页入口 hash 一致 `assets/index-RSD3QEwr.js` |
| UI 自动化 | `E:/Server Data/tmp/coc-verify/shot-211.cjs`：3 张卡齐全、脏缓存不再跳步、`nextDisabled=true`、暗/亮两套操作条取色如上表、点素材卡落到 `/materials`、`pageerror` 与 `requestfailed` 均为 0 |

### 六、两个小坑（避免下次重复）

1. **`Downloads.vue` 里的 `size` 不要追真实字节数**。改数字会让 chunk 内容变、包体跟着变几个字节，永远追不上（2.1.0 就差了 118 B）。正确做法：填 `0` —— `fmtSize(0)` 返回「—」，而最新版卡片实际是 `fetch('/downloads/version.json')` 动态取的，真实体积由服务端那份 JSON 提供。2.1.0 那条记录里的 `3886681` 是历史遗留，不影响显示。
2. **公网验证要 `--noproxy '*'`**。宿主机 `http_proxy=127.0.0.1:64070` 在拉大文件时会中途断（这次实测下到 26 KB / 413 KB 就停），看起来像 Cloudflare 截断，其实和 CDN 无关。直连后 `Content-Length` 一次就对。

---

## 2026-11-19 · v2.1.0：手机端补齐素材库 + 修复明暗模式按钮配色（五期测试收官）

> 这次不是新功能，而是**给 2.0.6 收尾**：用户实测反馈 2.0.6 的 APK 里「MC 结构还在、素材库没有」，同时投稿向导的按钮在深色模式下颜色不对。两条都修完后打包为 **2.1.0（versionCode 17）**，作为五期测试的收官版本。

### 一、问题一：2.0.6 的 APK 里前端是旧的

**现象**：网页端已经是素材库，但手机上装 2.0.6 打开，底栏还是「MC结构」，素材库页面根本没有。

**排查过程**（不要只看代码，要看**产物**）：

1. 网页端 `frontend/dist` 入口是 `assets/index-Cb1rm9_k.js`，其中已含 `Assets-*` / `MeAssets-*` 分包 —— 源码和网页构建都没问题。
2. 直接解包线上的 `coc-app-2.0.6.apk`，看 `assets/public/index.html`：

   | 检查项 | 2.0.6 APK 实际 | 应该是 |
   | --- | --- | --- |
   | index.html 引用 | `assets/index-CrLUMb5x.js` | `assets/index-Cb1rm9_k.js` |
   | `Assets-*.js` / `MeAssets-*.js` | **不存在** | 存在 |
   | `BottomTabBar-*.js` 内容 | 命中 `MC结构` | 只有 `素材` |

3. 结论：**APK 里打进去的是旧前端**。继续往上找，`E:\Server Data\apk-build\coc-app\www` 的 `index.html` 引用的是 `index-CTIiWO9z.js` —— 比 `frontend/dist` 更旧。

**根因**：`www/` 是 Capacitor 的 web 资源来源目录，打包前必须把最新 `dist` 同步进去。发布脚本 `E:\Server Data\scripts\build-coc-apk.ps1` 里本来有「copy dist → www」这一步，但 2.0.6 这次是没有走完整脚本（只跑了 gradle），`www` 还是上一轮的残留，于是 gradle 忠实地把旧前端打进了 APK —— Capacitor 不会自己去读 `frontend/dist`。

**修复**：

```bash
# 1) 清空旧资源（www 只放静态资源，index.html + assets/*）
cd "E:/Server Data/apk-build/coc-app/www"
rm -f index.html assets/* && rmdir assets
# 2) 同步最新 dist
cp -r "E:/Project Files/COC_rebuild/frontend/dist/." .
# 3) 同步进 android 工程（此步较慢，约 3~4 分钟）
npx cap sync android
# 4) 打包
cd android && ./gradlew.bat assembleRelease --no-daemon
```

**教训（写进流程，别再来一次）**：**打包后必须解包自检**，不能只看 `BUILD SUCCESSFUL`：

```bash
unzip -l app-release.apk | grep 'assets/public/assets/'   # 看是否存在新的分包
unzip -p app-release.apk assets/public/index.html | grep assets/index-   # 入口 hash 必须与 dist 一致
```

本次 2.1.0 解包自检结果（946 个文件里前端部分 71 个）：

| 检查项 | 结果 |
| --- | --- |
| `assets/public/index.html` 入口 | `assets/index-yZCrbRZ5.js` = `frontend/dist/index.html` ✅ |
| 素材库分包 | `Assets-DYhVDBu_.js` / `Assets-IJ5nQyoH.css` / `MeAssets-B7vtCBoe.js` / `MeAssets-I9xZ0yUb.css` ✅ |
| 底栏文案 | `BottomTabBar-Dg8HOcRJ.js` 中「素材」×1、「MC结构」×0 ✅ |
| `mcstructure` 全量搜索 | 仅剩 `format-CA8eiKFc.js` 里的 `TYPE_LABEL.mcstructure = "MC 结构"`（历史项目类型展示，**有意保留**） ✅ |
| 版本标记 | `index-yZCrbRZ5.js` 内含 `v2.1.0` ✅ |

### 二、问题二：投稿向导按钮在明暗模式下配色错乱

**现象**：项目草稿创建（投稿向导）页面的主按钮，深色模式下变成 Element Plus 官方蓝 `#409EFF`、次要按钮背景变成 `rgb(20,20,20)`/边框 `rgb(76,77,79)`，跟站点主题色（靛蓝 `#6c86ff`）和深色底（`#151923`）完全不搭；切回浅色模式又正常。

**排查**：用无头浏览器在两种主题下各取 `getComputedStyle`：

| | 修复前（深色） | 修复后（深色） | 修复后（浅色） |
| --- | --- | --- | --- |
| 主按钮背景 | `rgb(64,158,255)`（EP 默认蓝） | `rgb(108,134,255)` | `rgb(79,110,247)` |
| 次要按钮背景 | `rgb(20,20,20)` | `rgb(21,25,35)` | — |
| 次要按钮文字 | — | `rgb(164,172,189)` | — |

**根因（很隐蔽，CSS 特异性）**：

- `frontend/src/styles/tokens.css` 把项目设计变量映射到 Element Plus 变量时，选择器写的是 **`:root`（特异性 0,1,0）**；
- 但 `element-plus/theme-chalk/dark/css-vars.css` 用的选择器是 **`html.dark { … }`（特异性 0,1,1）**；
- 深色模式下后者的特异性更高，**整套 EP 变量被官方深色默认值覆盖**，项目自定义的 `--el-color-primary` / `--el-text-color-*` / `--el-border-color-*` 全部失效 —— 而浅色模式没有 `html.dark` 这份覆盖表，所以只有深色出问题。

**修复**（三处一起改，缺一不可）：

1. `frontend/src/styles/tokens.css` —— 把映射选择器写成一组，让特异性不输给 EP 的深色表，并补上按钮级变量：

   ```css
   :root,
   html.dark,
   html[data-theme='dark'],
   html[data-theme='light'] {
     --el-color-primary: var(--c-primary);
     /* … text / border / fill / disabled 一整套 … */
   }
   .el-button--primary {
     --el-button-bg-color: var(--c-primary);
     --el-button-border-color: var(--c-primary);
     --el-button-hover-bg-color: var(--c-primary-hover);
     --el-button-active-bg-color: var(--c-primary-active);
     --el-button-text-color: #fff;
   }
   .el-button--primary.is-disabled,
   .el-button--primary.is-disabled:hover {
     --el-button-disabled-bg-color: var(--el-color-primary-light-5);
     --el-button-disabled-text-color: #fff;
     opacity: 0.75;
   }
   ```

2. `frontend/src/stores/ui.js` —— 浅色底上直接用 `#6c86ff` 当按钮底、白字对比度不足，给每套主题色补了一档**深色版** `primaryLight`，`apply()` 里按模式取：`mode === 'light' ? a.primaryLight : a.primary`。

3. `frontend/index.html` —— 首屏内联脚本里的 `ACCENTS` 表同步改成 4 元组 `[深色主色, 浅色主色, accent, accent2]`，保证**首帧**（Vue 挂载前）的颜色和 store 算出的一致，不会闪一下。

### 三、顺带清理：已下线板块的残留文案

素材库上线后，界面上还有几处写着「结构文件」，会误导用户。全部改为「素材」：

| 文件 | 改动 |
| --- | --- |
| `components/BizTabs.vue` | 分类说明 `Scratch / HTML / 结构文件…` → `Scratch / HTML / 素材…` |
| `views/DevApply.vue` | 头部说明改为「投稿自己的作品（HTML / Scratch）… 图片、音视频等素材请传到「我的素材」」；条款 3 改为「Scratch 为纯数据/平台打包产物，免审直发」 |
| `views/MeProjects.vue` | 空状态描述 → 「投稿你的第一个小造物，Scratch 免审直发；素材可在「我的素材」上传」 |
| `views/admin/AdminReview.vue` | 「Scratch 与结构文件免审不进队列」→「Scratch 免审不进队列」 |

**有意保留**：`utils/format.js` 的 `TYPE_LABEL` 仍留 `structure` / `mcstructure` 两个键，`ProjectDetail.vue` 也仍留「板块已下线」兜底 —— 历史项目的类型标签要能正常显示，不能显示成空或 undefined。

### 四、涉及文件一览

| 文件 | 改动 |
| --- | --- |
| `frontend/src/styles/tokens.css` | 修 EP 变量映射的选择器特异性（本次核心修复）+ 主按钮/禁用态显式规则 |
| `frontend/src/stores/ui.js` | `ACCENTS` 增加 `primaryLight`，`apply()` 按明暗模式取主色 |
| `frontend/index.html` | 首屏主题脚本的色板表同步为 4 元组 |
| `frontend/src/views/Downloads.vue` | 新增 2.1.0 条目（体积 3,886,681 B 兜底值；线上以 `version.json` 为准） |
| `frontend/src/utils/platform.js` | `APP_BUILD` → `'v2.1.0'` |
| `components/BizTabs.vue`、`views/DevApply.vue`、`views/MeProjects.vue`、`views/admin/AdminReview.vue` | 下线板块残留文案清理 |
| `E:/Server Data/apk-build/coc-app/www/` | 全量重同步（本次 APK 内容错旧的直接产出物） |
| `E:/Server Data/apk-build/coc-app/android/app/build.gradle` | `versionCode 17` / `versionName "2.1.0"` |
| `E:/Server Data/coc-platform/downloads/` | `coc-app-2.1.0.apk`、`coc-app-latest.apk`、`version.json`、`version.txt` |

### 五、发布与验证

| 项 | 值 |
| --- | --- |
| 包大小 | 3,886,799 B（约 3.71 MB） |
| sha256 | `6633852f2780a6242db86702fbd625cbe1a11c1ef9fa14844f6f147052760abf` |
| 签名 | `CN=Collection of Creations, OU=Dev, O=COC`，证书 SHA-256 `1324f12e…2958b5`（与历史包同证书，可覆盖安装） |
| versionCode | 17（16 → 17，覆盖安装无冲突） |
| 本地验证 | `/downloads/version.json` 返回 2.1.0；`/downloads/coc-app-2.1.0.apk` `200` / `Content-Length: 3886799` |
| 公网验证 | `https://collectionofcreations.uk/downloads/version.json` 已是 2.1.0；APK `Content-Length: 3886799`（未命中旧缓存） |
| 网页端 | 本地与公网入口 hash 一致：`assets/index-yZCrbRZ5.js` |
| 内容自检 | 见上文「一、教训」表，四项全过 |

### 六、清理

- 删掉临时构建目录 `frontend/dist.n1`（构建产物已合并进 `dist` 并 prune 掉 36 个旧 chunk）。
- `frontend/dist-user`（`--base=/user/` 的沙箱区构建）**本次未重建**：线上 nginx 已无任何 site 配置引用 `dist-user`，属历史遗留目录，重建不产生任何线上效果。
- 解包校验目录 `E:/Server Data/tmp/apkchk` 用完即删。

### 七、五期测试收官说明

至此五期测试提出的问题全部闭环：

1. 手机端草稿建不出来（输入框失效）→ 2.0.3
2. 下载链接 404（`API_ORIGIN` 写死 `127.0.0.1:8090`）→ 2.0.3 后修
3. 手机端默认手机版视图 + 可切电脑版 + 全屏横屏 + 控制侧栏 → 2.0.4
4. 全屏虚拟按键（双摇杆独立映射 + 自定义按键 + 拖动 + 透明度/尺寸 + 本地持久化）→ 2.0.5
5. mcstructure 下线、素材库与项目/素材系列上线 → 2.0.6（网页端）
6. **手机端未同步素材库 + 投稿按钮明暗配色错乱 → 2.1.0（本次）**

---

## 2026-11-19 · v2.0.6：下线 mcstructure 板块、上线「素材库」与项目/素材系列

> 本次是五期测试里体量最大的一次结构调整：**新增一套素材体系全面替代原来的 MC 结构板块**，同时给项目与素材都加上「系列」这一层归类维度。网页端已重建上线，App 打包为 **2.0.6（versionCode 16）** 上架官网。

### 一、改动目的

1. **原 mcstructure 板块定位尴尬**：用户上传 `.mcstructure` 后只能下载回去，平台上既不能预览也不能在 HTML 项目里引用，实际使用率极低。
2. **用户做 HTML 项目缺素材**：想放一张图、一段 BGM、一个视频，只能外链到第三方图床/网盘，随时可能挂掉，也没有统一的地方管理。
3. **项目和素材缺少体系化管理**：一个作者做了「物理演示程序」系列十来个作品，列表里只能靠标题自己认，没有分组概念。

因此本次把「结构文件」这一档整体换成**素材库**：素材 = 图片 / GIF / 视频 / 音频 / JSON，上传后拿到的是**服务器直链**，可以直接写进自己 HTML 的 `<img src>`、`<audio src>`；同时给项目和素材各加一套「系列」分组。

### 二、mcstructure 板块下线

采取「**只停入口、不删数据**」的软下线策略：

| 位置 | 处理 |
| --- | --- |
| `frontend/src/views/Home.vue` | 类型入口 `structure` 整块换成 `{ to: '/materials', label: '素材库' }`，点击直接跳素材库 |
| `frontend/src/components/BottomTabBar.vue` | 手机底栏「MC结构」tab 换成「素材」→ `/materials` |
| `frontend/src/views/Upload.vue` | 投稿类型列表去掉 structure；步骤一说明改为「HTML / Scratch」 |
| `frontend/src/utils/format.js` | `isAutoApproved()` 由 `type === 'scratch' || type === 'structure'` 收敛为只认 `scratch` |
| `backend/.../service/ProjectService.java` | 新增 `RETIRED_TYPES = Set.of("structure")`，`createDraft` 命中直接抛 400：`MC 结构板块已下线：.mcstructure 等结构文件请到「素材」里上传，再在作品里引用其直链` |
| `frontend/src/views/ProjectDetail.vue` | 历史 structure 项目的类型兜底文案改为「板块已下线，文件仍可下载」 |
| `V4__assets_and_series.sql` | `project.project_type` 枚举**保留** `structure`，历史项目数据不丢、链接不断 |

> 取舍说明：没有做 `ALTER TABLE ... MODIFY ENUM` 删值，也没有物理删项目——已发布的作品链接一旦 404 对作者是不可接受的，历史 structure 项目仍能正常打开和下载源文件。

### 三、数据结构（V4 迁移）

`backend/src/main/resources/db/migration/V4__assets_and_series.sql`

```sql
project_series(series_id PK, user_id FK→user, name VARCHAR(80), intro VARCHAR(255),
               is_public TINYINT, created_at, updated_at,
               UNIQUE(user_id, name))
asset_series  (同上结构，独立表——项目系列只装项目，素材系列只装素材)

ALTER TABLE project ADD series_id BIGINT NULL
  → FK project_series ON DELETE SET NULL，索引 (series_id, status, is_deleted)

asset(asset_id PK, user_id FK→user, series_id FK→asset_series ON DELETE SET NULL,
      name VARCHAR(120), file_name VARCHAR(255), asset_type ENUM('image','gif','video','audio','json'),
      mime, file_size, sha256, storage_path, is_public TINYINT, is_deleted TINYINT,
      created_at, updated_at)
  → 索引 idx_asset_user / idx_asset_series / idx_asset_public / idx_asset_type / idx_asset_sha

ALTER TABLE upload_session ADD kind ENUM('project','asset') DEFAULT 'project'
```

几个关键设计点：

- **系列删除时子项不跟着删**（`ON DELETE SET NULL`）：用户删掉「物理演示系列」，里面的项目回到「未归系列」，不会连带丢作品。
- **系列名在同一用户下唯一**（`UNIQUE(user_id, name)`），配合「上传时可直接新建系列」的 `ensureByName` 语义：填一个已存在的名字 = 归入该系列，而不是报错。
- **`asset.storage_path = assets/{asset_id}/{file_name}`**：保留用户原始文件名（清洗后），这样直链可读、也能被浏览器按扩展名正确识别。
- **`upload_session.kind`**：素材和项目源码复用同一套分片上传协议，只在 `complete` 阶段按 kind 分流，避免再写一套分片上传。

> 按用户要求「优先用系列 id 找 user_id，再用 user_id + asset_id 联合查下属素材/项目」：`SeriesMapper.listByUser` / `AssetMapper.listMine` / `ProjectMapper.listBySeries` 全部以 `user_id` 为第一过滤条件走复合索引，`AssetService` 与 `ProjectService` 在变更前统一走 `requireOwn(uid, ...)` 校验归属，杜绝越权改别人的系列/素材。

### 四、后端新增模块

| 文件 | 说明 |
| --- | --- |
| `mapper/SeriesMapper.java`（新） | 用 `${table}` / `${child}` / `${idCol}` 占位复用两套系列 SQL（`insert` / `findById` / `listByUser` 带 `item_count` 子查询 / `listPublicByUser` / `update` / `delete` / `detachChildren` / `attachChild` / `countByUser` / `findByUserName`）；占位值只来自 `SeriesService.Kind` 枚举白名单，**不接受请求输入**，无注入风险 |
| `service/SeriesService.java`（新） | `Kind` 枚举 `PROJECT`/`ASSET`；`parseKind()` 兼容 `project/projects/项目/项目系列` 与 `asset/assets/素材/素材系列`；`listMine` / `listPublicOfUser` / `create`（每人上限 50，`DuplicateKeyException` → 「已有同名系列」）/ `update` / `delete`（先 `detachChildren` 再删）/ `ensureByName`（找不到就建，`makePublic` 跟随本次上传的公开性） |
| `mapper/AssetMapper.java`（新） | `insert` / `updateStored` / `findById` / `listPublic` / `countPublic` / `listMine` / `countMine` / `updateMeta`（动态：改名、改公开性、`clear_series` 置 NULL、改 series_id）/ `softDelete` / `countByUser` / `countPublicByUser`；`listMine`/`countMine` 支持 `no_series == true` 过滤「未归系列」 |
| `service/AssetService.java`（新，约 470 行） | 类型白名单 `EXT_TYPE` / `EXT_MIME`（png/jpg/jpeg/webp/bmp/avif/ico→image、gif→gif、mp4/webm/mov/m4v→video、mp3/wav/ogg/m4a/aac/flac→audio、json→json，**故意排除 html/svg/js**，防止素材库被当成二次托管入口）；单文件上限 300MB；`initUpload` → `completeUpload`（校验 owner + `kind=='asset'`，解析 series_id / new_series_name，`uploadService.materialize()` 落盘，写 asset 行，把文件搬/拷到 `assets/{asset_id}/{file_name}`，回写 `storage_path` 与真实直链 url）；`listPublic` / `listMine` / `detail`（私有素材非本人一律 404）/ `update` / `delete`（软删）/ `mediaUrl()` / `sanitize()` / `uniqueTarget()` |
| `controller/AssetController.java`（新） | `/api/v1/assets`：`GET ""`（公开广场）、`GET /mine`、`GET /{id}`、`POST /upload/init`、`POST /upload/{session_id}/complete`、`PUT /{id}`、`DELETE /{id}` |
| `controller/SeriesController.java`（新） | `/api/v1/series`：`GET ""?kind=`、`GET /public?kind=&user_id=`、`POST ""`、`PUT /{id}`、`DELETE /{id}?kind=` |
| `storage/StorageService.java` | `init()` 额外建 `assets/` 目录；新增 `assetsDir()` / `assetDir(assetId)` |
| `mapper/UploadMapper.java` | `insertSession` 写入 `kind`（`IFNULL(#{kind}, 'project')`）、`findSession` 带出 `kind` |
| `service/UploadService.java` | 把「合并分片 + 校验 sha256 / 秒传引用」抽成 `materialize(sessionId, session)`，项目与素材共用；`complete()` 改为调用它 |
| `service/ProjectService.java` | 新增 `RETIRED_TYPES`；`createDraft`/`updateDraft` 支持 `series_id` 与 `new_series_name`；`detail()` 追加 `series_id` / `series_name` / `series_is_public` 与同系列兄弟项目 `series_projects`（未发布的只对作者与管理员可见）；`myProjects()` 归一化 `no_series` |
| `mapper/ProjectMapper.java` | `findProjectById` LEFT JOIN `project_series` 带出系列名与公开性；新增 `listBySeries`；`insertProject` 写 `series_id`；`updateProjectBasic` 支持 `series_id` / `clear_series`；`listMine`/`countMine` 带出 `series_id`/`series_name` 并支持 `no_series` |
| `auth/AuthInterceptor.java` | 登录白名单补充：`GET/POST:/api/v1/series`、`PUT/DELETE:/api/v1/series/*`、`GET:/api/v1/assets/mine`、`POST:/api/v1/assets/upload/init`、`POST:/api/v1/assets/upload/*/complete`、`PUT/DELETE:/api/v1/assets/*` |

### 五、素材直链（核心能力）

nginx 直接开一个静态 location，绕过后端：

```nginx
location ^~ /media/ {
    alias "E:/Server Data/coc-platform/assets/";
    expires 30d;
    add_header Cache-Control "public, max-age=2592000";
    add_header X-Content-Type-Options "nosniff" always;
    autoindex off;
}
```

于是 `assets/{asset_id}/{file_name}` 对应的公开地址就是：

```
https://collectionofcreations.uk/media/{asset_id}/{file_name}
```

用户在前端点「复制链接」拿到的就是这一条，可以**直接贴进自己 HTML 的 `<img src>` / `<audio src>` / `<video src>`**，不经过任何后端接口、不受登录态影响。

> **公开性语义**（重要，已在前端页面与本文档说明）：`is_public` 只控制**是否出现在素材广场、是否能被列表接口检索到**；直链本身对拿到 URL 的人是开放的。这是刻意的取舍——素材链接要嵌进已发布的 HTML 里，如果直链还要鉴权，用户的作品就会集体裂图。因此删除素材做的是**软删**（`is_deleted=1`），文件留在磁盘上，历史 HTML 引用的图片不会突然 404。

### 六、前端新增/改动

| 文件 | 说明 |
| --- | --- |
| `api/index.js` | 新增 `seriesApi`（`mine(kind)` / `publicOf(userId, kind)` / `create` / `update` / `remove`）与 `assetApi`（`list` / `mine` / `detail` / `initUpload` / `completeUpload` / `update` / `remove`） |
| `utils/format.js` | 新增 `ASSET_TYPE` 映射（image/gif/video/audio/json 的 label/icon/颜色）、`assetTypeOf()`、`mediaUrlOf()`、`absoluteUrl()`；`isAutoApproved()` 收敛为只认 scratch |
| `components/AssetUploader.vue`（新） | 拖拽区 + 扩展名白名单校验 + 元数据表单（名称 / 归入系列 / 新建系列 / 是否公开）；`start()` 走 `initUpload` → 逐片 `uploadApi.putPart`（单片重试 3 次）→ `completeUpload`，`defineExpose({start, reset})` 供父组件调用 |
| `views/Assets.vue`（新） | 公开素材库 `/materials`：类型 radio 筛选、关键词搜索、网格缩略（图片/GIF/视频取首帧）、预览弹窗（img/video/audio/JSON 美化输出）、一键复制链接（`navigator.clipboard`，失败回退 `execCommand`），并给出 `<img src="...">` 式用法片段 |
| `views/MeAssets.vue`（新） | 我的素材 `/me/materials`：系列标签条（全部 / 未归系列 / 各系列 / 新建系列）、类型筛选、每个素材卡片带公开性 `el-switch`、下拉菜单（预览 / 归入系列 / 重命名 / 新窗口打开 / 删除）、上传与系列增改删弹窗 |
| `views/MeProjects.vue` | 顶部加「新建系列 / 系列管理 / 新建投稿」，状态筛选上方加系列标签条，卡片显示 `series-tag`；系列管理弹窗支持逐行切换公开性、改名、删除 |
| `views/Upload.vue` | 表单新增「项目系列」下拉（可空）+「或新建系列」输入；`saveDraft` 按填写情况提交 `series_id` 或 `new_series_name` |
| `views/ProjectDetail.vue` | 元信息下方新增系列行，带同系列项目下拉，可直接跳转兄弟作品 |
| `router/index.js` | 新增 `materials`（SiteLayout）与 `me/materials`（ConsoleLayout）两条路由 |
| `layouts/ConsoleLayout.vue` | 用户菜单「创作」组新增「我的素材」 |
| `components/ChunkUploader.vue` | 提示文案补一句「结构文件请到『我的素材』上传」 |
| `utils/platform.js` | `APP_BUILD` → `v2.0.6` |

> **路由命名踩坑**：最初把公开素材库放在 `/assets`，线上返回 301 —— 因为 SPA 构建产物本身就放在 `/assets/` 目录下，两者冲突。全站改为 `/materials` 与 `/me/materials` 后正常。

### 七、发布与验证

- 后端 `mvn clean package` + Flyway 应用到 **v4**（V3 是并行的 bug_feedback 迁移，两者都成功），`coc-backend` 重启后生效。
- 前端重建产物 `index-Cb1rm9_k.js`，nginx `root` 直指 `frontend/dist`，公网 `https://collectionofcreations.uk/?_=时间戳` 取到的已是新 index。
- App：`versionCode 16` / `versionName 2.0.6`，签名 `CN=Collection of Creations`，包体 3,864,440 字节，sha256 `fd71292cd183755ef83f6b78243527cb503f99a97d1335b744c4374b7060d41e`，已放 `/downloads/coc-app-2.0.6.apk` 与 `coc-app-latest.apk`，`version.json` / `version.txt` 同步。
- **接口级验证**（用真实 JWT 跑通全链路）：建系列 → 素材 `init` → 分片 `part` → `complete` → `GET /media/3/测试图-1x1.png` 返回 200 / `image/png` / 70 字节 / 字节一致 / `max-age=2592000` → 素材出现在公开列表 → `mine` 每行都带 `url` → 改公开性为私密成功 → 匿名访问私密素材 404 → 删除素材与系列 200。
- **项目系列验证**：`POST /projects` 带 `new_series_name` → 详情返回 `series_id` / `series_name` / 兄弟项目 1 个；`listMine` 返回系列字段；`POST /projects` 带 `project_type: 'structure'` 返回 **400** 与下线提示。
- **浏览器端 E2E 已补跑（Playwright + Chromium headless）**，脚本 `E:/Server Data/tmp/coc-verify/ui-assets-e2e.cjs`，造 3 个真实素材（360×220 PNG / JSON / 0.2s WAV）+ 1 个素材系列，跑完自清。结果：
  - 「我的素材」渲染 4 张卡（含系列标签条 `E2E素材系列-xxxxx 3 ●`）、缩略图 `naturalWidth > 0`；
  - 点「复制链接」→ 真实剪贴板读到 `http://<host>/media/14/e2e-测试图.png`，toast 同步显示「直链已复制：…」，回抓 200 / `image/png` / 61,407 B / 字节一致 / `max-age=2592000` / `nosniff`；
  - 预览：图片 `naturalWidth > 0`；JSON 弹窗渲染出格式化文本；音频 `readyState=4`、`duration=0.2`（真的解码了，不是空壳）；
  - 公开性：关闭开关后接口 `is_public=0`、素材库（匿名上下文）该卡消失、匿名 `GET /assets/{id}` → 404，**而直链仍 200**（符合 13.2 的设计）；重新打开 → 卡片回归；
  - 全程 `requestfailed` 计数 0。
- ⚠️ **无头浏览器验证的两个拦路坑（下次直接用这招）**：
  1. 访问 `http://127.0.0.1:8084` 时，前端 `IS_NATIVE_SHELL` 判定会把 hostname `127.0.0.1` 当成 Capacitor 壳，于是 API 走 `https://collectionofcreations.uk/api/v1` 绝对地址 → 跨源被 CORS 拦死，页面只剩「哎呀，卡住了」。**改用 `http://127.0.0.2:8084`**：仍是回环（secure context，`navigator.clipboard` 可用、不走系统代理），但 hostname 不等于 `127.0.0.1`，前端就当普通网页处理。自定义域名（如 `coc.test`）反而会被本机 `http_proxy=127.0.0.1:64070` 劫持返回 502。
  2. 宿主机（nexus-desktop）会抢走 `msedge.exe` 并静默退出，所以不再用系统 Edge，改用 `npx playwright install chromium` 装的独立 Chromium（`ms-playwright/chromium_headless_shell-1148`）。

---

## 2026-11-19 · 后台审核源码查看/编辑 + 全站趋势折线

### 改动目的

1. 审核队列此前只能看标题、状态与存储路径，管理员无法在后台直接核对或小幅修正项目代码。
2. 平台统计模块只有 KPI 与原始 JSON，缺少类似用户数据看板的浏览量 / 评论量趋势折线。

### 具体改动

| 文件 | 改动说明 |
| --- | --- |
| `backend/.../mapper/ViewMapper.java` | 新增 `dailyViewsTotal` / `hourlyViewsTotal` / `dailyCommentsTotal` / `hourlyCommentsTotal`：去掉 `project_id` 过滤，做全平台按天 / 按小时聚合 |
| `backend/.../mapper/ProjectMapper.java` | 新增 `updateFileMeta`：在线编辑后回写 `file_size` 与 `sha256` |
| `backend/.../service/AdminService.java` | 新增 `projectSourceFiles`（列出最新版本 `source/` 目录文件，文本文件附带内容，标注是否可编辑）、`saveSourceFile`（仅限文本后缀 / 2MB 内、防目录穿越，写 `edit_source` 审计并回写元数据）、`platformTrend`（range=2 走小时表 48 点，7/15/30 走天表） |
| `backend/.../controller/AdminController.java` | 新增 `GET/PUT /admin/projects/{id}/source-files`、`GET /admin/stats/platform/trend?range=` |
| `frontend/src/api/index.js` | `adminApi` 新增 `sourceFiles` / `saveSource` / `platformTrend` |
| `frontend/src/views/admin/AdminReview.vue` | 操作列新增「查看/编辑代码」按钮；源码弹窗含文件选择、二进制只读标记、可编辑文本域、保存回写与脏状态拦截 |
| `frontend/src/views/admin/AdminStats.vue` | 新增 2 / 7 / 15 / 30 天浏览量 + 评论量 echarts 折线，复用用户看板的主题配色与响应式 resize 逻辑 |

### 备注

- 源码改动只写入服务器 `source/` 目录；已有的 `build/` 打包产物不会自动重建，沙箱在无 build 时回退到 source 运行。
- 可在线编辑后缀：html/htm/js/mjs/cjs/css/json/svg/txt/md/xml/map；图片、压缩包、sb3、wasm 等二进制只读。
- 按约定本次未执行前端重建、后端重启与 APK 打包，留待统一版本验证。

---

## 2026-11-19 · 前端加载/网络失败提示文案口语化

### 改动目的

原来加载失败、断网、超时、服务异常时直接显示「无法连接后端服务（127.0.0.1:8090 可能尚未启动）」这类面向开发的提示，普通用户看不懂。统一改成轻松口语化的文案，网页端与 App 端共用同一套（同一个前端产物）。

### 具体改动

| 文件 | 行号 | 改动说明 |
| --- | --- | --- |
| `frontend/src/api/http.js` | L88、L108、L117-L122 | 业务错误兜底、登录过期、403/404/超时/断网/其它失败的全局提示改为口语化（如「啊哦，网络离家出走啦～」） |
| `frontend/src/components/AppState.vue` | L18、L54-L63 | 列表/详情错误态标题改为「哎呀，卡住了」；断网、5xx、401、其它错误分别给出友好文案 |
| `frontend/src/views/DevApply.vue` | L131 | 提交失败兜底去掉「后端 8090 可能未启动」 |
| `frontend/src/views/Forgot.vue` | L84 | 找回申请失败兜底改为口语化，保留管理员 QQ |
| `frontend/src/components/ChunkUploader.vue` | L203 | 上传初始化失败改为「上传没能开始，网络可能开小差了」 |
| `frontend/src/views/Home.vue` | L114 | 空标签提示去掉「后端未启动」表述 |
| `frontend/src/views/Messages.vue` | L173-L178、L183 | 实时通道断开/异常/降级轮询状态文案口语化，去掉「后端 WS 可能未启动」 |
| `frontend/src/views/admin/AdminStorage.vue` | L17 | 空状态去掉「后端接口可能尚未实现」 |
| `frontend/src/views/admin/AdminUsers.vue` | L12 | 空状态去掉「后端接口可能尚未实现」 |

### 备注

- 只改用户可见文案，不动任何请求、重试、降级逻辑；业务错误仍优先显示服务端返回的具体原因。
- 本次同属 **COC 五期测试** 过程中的小改动，未执行构建与打包。

---

## 2026-11-19 · 修复：官网所有资源地址被写死成 `127.0.0.1:8090`（下载 2.0.5 报 404 的根因）

> 线上事故修复，随 2.0.5 网页产物一起发布（APK 不受影响，无需重打包）。

### 现象

官网 `/downloads` 页面显示 v2.0.5、点「下载最新版」却返回：

```json
{"code":404,"message":"接口或资源不存在","data":null}
```

### 根因

`frontend/src/utils/format.js` 的 `API_ORIGIN`：

```js
const base = import.meta.env.VITE_API_BASE || ''
try { return base ? new URL(base, 'http://127.0.0.1:8090').origin : '' } catch (e) { return '' }
```

生产构建里 `VITE_API_BASE=/api/v1`（**相对**基址），而 `new URL('/api/v1', 'http://127.0.0.1:8090')` 返回的 `origin` 就是那个「解析基准」本身 → **`http://127.0.0.1:8090`**。

于是 `fileUrl('/downloads/coc-app-2.0.5.apk')` 拼成 `http://127.0.0.1:8090/downloads/coc-app-2.0.5.apk`：
- 访问者若就在本机（服务器机器上开浏览器）→ 打到**后端 8090**，`/downloads/*` 不是后端路由 → 就是那句 JSON 404；
- 访问者若在别的设备 → `127.0.0.1` 指向他自己 → 连接被拒。

同一个 `API_ORIGIN` 还喂给 `runUrlOf()`，所以官网的**封面、头像、沙箱运行页**其实全都指着 `127.0.0.1:8090`（本机还能歪打正着跑起来，远程设备上封面/头像全裂、作品打不开）。之前一直没暴露，是因为 App 端走的是另一条分支（`ORIGIN` 在原生壳里等于线上域名），而网页端只是没人认真点过下载。

另外 `version.json` 的 `fetch` 也走 `fileUrl`，同样 404 → 页面显示 v2.0.5 其实是**静态兜底数组**兜住的（`RELEASES[0]`），不是真的读到了服务器版本号。

### 修法

`frontend/src/utils/format.js` L73-L88：

```js
if (ORIGIN) return ORIGIN                       // 原生壳：线上绝对源，保持不变
const base = import.meta.env.VITE_API_BASE || ''
if (!/^https?:\/\//i.test(base)) return ''      // 相对基址（/api/v1）→ 同源，返回空串
try { return new URL(base).origin } catch (e) { return '' }
```

- 相对基址（生产 `/api/v1`）→ 返回 `''`，`fileUrl` 生成相对路径 `/downloads/x.apk`、`/avatars/x.png` → 由 nginx 直出 ✔
- 绝对基址（开发 `http://127.0.0.1:8084/api/v1`）→ 仍返回其 origin ✔
- 原生壳 → `ORIGIN`（线上域名）分支在前，行为不变 ✔

顺手修了第二个同源问题：`frontend/vite.config.js` 加

```js
base: mode === 'user' ? '/user/' : '/',
```

`dist-user` 是挂在 `/user/` 下的（nginx `location ^~ /user/` → `dist-user/`），但产物里资源写的是 `/assets/index-*.js`，会被主站 `dist/assets` 抢走 → 入口 chunk 404，`/user/` 一直是坏的。现在产物是 `/user/assets/...`，实测 4 个资源全 200。

### 影响面

| 位置 | 修前 | 修后 |
| --- | --- | --- |
| 官网下载链接 | `http://127.0.0.1:8090/downloads/*.apk` → 404 JSON | `/downloads/*.apk` → 200，3,864,431 B |
| 官网 `version.json` | 404，靠静态数组兜底 | 真正读到服务器版本号 |
| 封面 / 头像 | `http://127.0.0.1:8090/covers|avatars/*`（远程全裂） | 相对路径，nginx 直出 |
| 沙箱运行页 | `http://127.0.0.1:8090/api/v1/run/*`（远程打不开） | `/api/v1/run/*`，且**会经过 nginx 的 `sub_filter`**（虚拟按键桥只有 8084 这条链才注入） |
| App（Capacitor） | 正常 | 正常（`ORIGIN` 分支未动） |

### 验证

- 产物自检：`grep -rl "127.0.0.1:8090" dist/assets/*.js` 只剩 `AppState` 里的一句**报错文案**（"无法连接后端服务（127.0.0.1:8090 可能尚未启动）"），`format-*.js` 已为 0；chunk 引用完整性 dangling = 0（dist / dist-user 各 64 个文件）。
- HTTP：`/` → `index-BG7EI2uG.js`；`/downloads/coc-app-2.0.5.apk` → 200 / 3,864,431 B / `application/vnd.android.package-archive`；`/user/` 与 `/user/assets/*` 全 200；公网 index 已切新 hash。

### 教训

**构建产物里绝不能出现回环地址当资源前缀。** 以后每次 `vite build` 完都要跑一遍：

```bash
grep -rl "127.0.0.1:8090" frontend/dist/assets/*.js
grep -rl "127.0.0.1:8090" frontend/dist-user/assets/*.js
```

除 `AppState` 的文案外不该有命中。另外「页面显示对了」不代表数据源对了——这次 v2.0.5 能显示，是静态兜底骗过了眼睛，要额外确认 `version.json` 请求本身是不是 200。

---

## 2026-11-19 · 手机端全屏虚拟按键（双摇杆 + 自定义按键 + 设置模式）（v2.0.5）

> 本条属于 **COC 五期测试** 的改动，已随 **2.0.5 / versionCode 15** 打包发布。

### 改动目的

2.0.4 让手机端能全屏横屏跑 HTML 作品了，但**触摸屏没有键盘**——凡是靠 `keydown` 操作的作品（`WASD` 走位、空格跳、数字键放技能）在手机上一律玩不了。本次为手机端全屏界面补上一套**虚拟按键层**：

1. 两个摇杆，分别映射 **WASD** 与 **方向键**；两者**独立映射、互不干扰**（真正的双指同时操作，不是「一次只能动一个」）。
2. 可**自定义新增虚拟按键**，字段规则：空格 → `space`、回车 → `enter`、`ctrl` / `shift` 保持原名，其余数字键与字母键按字符本身写（`1`、`9`、`z`、`x`…）。
3. 独立的**虚拟按键设置模式**：摇杆只需切换显示/隐藏，按键才需要新建；设置模式下可**拖动**摇杆与按键调整位置，且**拖动/点击一律不触发按键**（否则摆布局的时候人物已经在乱跑了）。
4. 选中某个按键或摇杆后，用**滑条**调它的**透明度**与**尺寸**。
5. 退出设置模式**自动落盘**到本地存储，下次开启虚拟按键自动还原上次的方案。

### 技术难点与方案

作品跑在 `sandbox="allow-scripts"` 的 iframe 里（**故意不给 `allow-same-origin`**，这是安全边界，不能为了虚拟按键放开）。父页面因此**拿不到 `contentDocument`，也无法向内部 `dispatchEvent`**。最终走三层接力：

```
触摸 → VirtualPad（父页面）
      → window.postMessage({__coc:'key',type:'down',key,code,keyCode}) → 作品 iframe
      → coc-key-bridge.js 合成 KeyboardEvent → dispatch 到 document.activeElement
```

| 环节 | 做法与坑 |
| --- | --- |
| 把脚本送进 iframe | `sandbox` 不能放宽，只能由**服务端注入**。nginx 在 `location ~ ^/api/v1/run/` 用 `sub_filter` 把 `<script src="/coc-key-bridge.js">` 插到 `</body>` 前 |
| 注入为什么不生效 | `sub_filter` **无法处理压缩响应体**。必须 `proxy_set_header Accept-Encoding ""` 让上游返回明文 HTML（否则一行都匹配不到，且没有任何报错） |
| `keyCode` 设不上 | `new KeyboardEvent()` 构造参数里没有 `keyCode` / `which`，只能 `Object.defineProperty(ev, 'keyCode', {get:()=>kc})` 强行改。很多老作品判断的就是 `e.keyCode` |
| 双指同时按摇杆 | 单指方案用全局一个 `pointerId`，第二根手指按下会让第一个摇杆停更且**方向键永远卡住**。改为 `joyPid = {joyA, joyB}` 各自记录，同一摇杆被新手指重按时先释放旧手指留下的方向键 |
| 松手收不到 `pointerup` | 手指滑出元素边界就丢事件。用 `setPointerCapture(e.pointerId)` 把后续事件锁给元素 |
| 坐标系错位 | 画面整体 `rotate(90°)` 后，屏幕位移和舞台坐标不是同一套。`toStage(dx,dy)` 按 `rot` 做一次旋转换算，否则摇杆方向会跟着转 |
| 设置模式误触 | `.vpad` 默认 `pointer-events:none`，只有 `.is-edit` 时才 `auto`；设置模式下 `onDown` 只写 `drag` 状态、**不进 `press()`**，从根上杜绝拖动触发按键 |
| 关了再开丢配置 | `togglePad` 原先在 `nextTick` 里写 `scheme.on`，此时组件已卸载、什么都没存。改为**先**写 `scheme.on = next` **再**翻 `padOn`，并让 `onMounted` 兜底置 `true` |

### 具体改动

| 文件 | 行号 | 改动说明 |
| --- | --- | --- |
| `frontend/src/utils/vkeys.js` | 全文（164 行） | **新增**。字段表 + 键值解析 + 方案持久化：`VK_STORAGE_KEY='coc_vpad_v1'`（L14）、`ALIASES`（L16，`space`/`enter`/`ctrl`/`shift`/`alt`/`tab`/`esc`/方向键）、`normalizeField`（L52）、`resolveKey`（L63，产出 `key`/`code`/`keyCode`）、`labelOf`（L77）、`JOY_DEFS`（L83，`joyA`=WASD、`joyB`=方向键）、`QUICK_FIELDS`（L99，面板快捷芯片）、`defaultScheme`（L101）、`loadScheme`（L119，逐字段回填 + 夹取边界 + 按键上限 12 个）、`saveScheme`（L157） |
| `frontend/src/components/VirtualPad.vue` | 全文（540 行） | **新增**。虚拟按键层：摇杆层（L4-L22）、自定义按键（L23-L33）、设置面板（L36-L100）。指针处理 `onDown`（L210）/`onMove`（L237）/`onUp`（L254）；摇杆方向计算 `updateJoy`（L265，阈值 ±0.35，diff 出本次要按下/松开的方向）；多指隔离 `joyPid`（L125）；引用计数 `counts`（L132，同一字段被多个入口按住时不会提前松开）；增删键 `addField`（L304）/`removeBtn`（L335）；恢复默认 `resetScheme`（L342）；透明度/尺寸滑条（L88、L92，透明 0.15-1 步进 .05、尺寸 44-180）；`defineExpose({releaseAll, scheme})` |
| `frontend/src/views/ProjectDetail.vue` | L85-L92 | 把 `<VirtualPad>` 放进 **`.stage` 内部**——这样它跟着画面一起旋转，坐标天然等于作品的坐标系 |
| 同上 | L109-L117 | 全屏侧栏新增两个按钮（仅 `isNarrow` 显示）：「虚拟按键 / 隐藏按键」、「按键设置 / 完成设置」，选中态高亮 `.is-on` |
| 同上 | L367、L370 | 引入 `VirtualPad`、`resolveKey`、`loadScheme` |
| 同上 | L466-L474 | `padEditing` / `padOn`（初始值取自 `loadScheme().on`）、`padVisible = isFull && canRun && isNarrow && padOn` |
| 同上 | L481-L517 | `sendKey(field,down)` 向 iframe `postMessage`；`pingBridge()` 探活（1.5s 无 `pong` 才提示桥接不可用）；`onBridgeMsg` 处理 `pong` 与 `blur`（作品失焦 → `releaseAll` 防卡键） |
| 同上 | L519-L546 | `togglePad` / `togglePadEdit` / `setPadEditing`；进设置模式与开按键时各 ping 一次 |
| 同上 | L912、L953、L979、L1001 | 重载 iframe、退出全屏、`fullscreenchange`、按 `Esc` 统一先 `releaseAll()` + 退出设置模式 |
| 同上 | L1010-L1013 | watch `frameKey`（重载后重新 ping）、watch `isFull`（进全屏 800ms 后 ping） |
| 同上 | L1020、L1034 | 挂载/卸载 `window.addEventListener('message', onBridgeMsg)` |
| `E:/programs/nginx/html/coc-key-bridge.js` | 全文（71 行） | **新增**。注入到每个运行页的桥接脚本：收 `{__coc:'key'}` 消息 → 合成 `keydown`/`keyup` → `defineProperty` 改 `keyCode`/`which` → dispatch 到 `document.activeElement \|\| body`；收 `ping` 回 `pong`；`window.blur` 时向父页面报 `blur` |
| `E:/programs/nginx/conf/sites/coc.conf` | `location ~ ^/api/v1/run/` | 新增 `proxy_set_header Accept-Encoding "";` + `sub_filter_once on;` + `sub_filter '</body>' '<script src="/coc-key-bridge.js"></script></body>';` |
| 同上 | 新增 `location = /coc-key-bridge.js` | `alias` 到 HTML 目录、`application/javascript`、`max-age=300`，并带 `coc-sec.conf` 安全头 |
| `frontend/src/utils/platform.js` | L27 | `APP_BUILD` 2.0.4 → **v2.0.5** |

### 字段规则（用户可输入的内容）

| 输入 | 等价按键 | 说明 |
| --- | --- | --- |
| `space` | 空格（key `' '` / code `Space` / 32） | 也接受「空格」「空格键」 |
| `enter` | 回车（13） | 也接受「回车」「回车键」 |
| `ctrl` / `shift` | 原样 | 别名 `control` / `shiftleft` 等自动归位 |
| 单字母 `a`-`z` | `KeyA`-`KeyZ` | `keyCode` = 大写字符码 |
| 单个数字 `0`-`9` | `Digit0`-`Digit9` | 小键盘请用 `numpad1` 形式 |
| 其他 | 方向键 `up/down/left/right`、`alt`、`tab`、`esc`、`f1`-`f12` | 大小写不敏感 |

### 验证方式（无头 Edge + CDP 双指触摸，脚本在 `E:\Server Data\tmp\coc-verify\verify205*.cjs`）

| 场景 | 结果 |
| --- | --- |
| 390×844 Android UA 进全屏 | 舞台 796×390，`matrix(0,1,−1,0,390,0)`（旋转 90°），桥接 `pong` 正常 |
| 侧栏点「虚拟按键」 | `.vpad` 出现，默认 1 个摇杆（joyA） |
| 「按键设置」→ 点快捷芯片 `space` | 新增 1 个 `.vkbtn` |
| 设置模式下拖动摇杆 | `18%,72% → 10.46%,59.18%`，**期间按键日志为空**（不触发按键 ✔） |
| 两个滑条 | 透明度、尺寸均存在且可拖 |
| 点「完成」 | 面板消失，localStorage `{on:true, buttons:[{field:'space',…}], joyB:false}` |
| 空格键按下/松开 | `down ' ' / Space / 32` → `up`，作品侧收到成对事件 |
| 摇杆推左上 | 同时 `down a(65)` + `down s(83)`，松手全部释放 |
| 开启 joyB 后双指同时操作 | **`w` 与 `ArrowRight` 同时按下、同时释放**（两摇杆互不干扰 ✔） |
| 退出全屏 | 虚拟按键层 0 个、全屏态归零、`errs: []` |
| 桌面 1280×900 回归 | 侧栏只有「重载 / 左旋 / 右旋 / 新窗口 / 退出」，`.vpad` 数量 0（桌面端零影响 ✔） |

### 部署依赖（重要）

虚拟按键**不是纯前端功能**，缺了下面任何一项，手机上点摇杆都不会有反应：

1. `E:/programs/nginx/html/coc-key-bridge.js` 必须存在；
2. `coc.conf` 的 `location ~ ^/api/v1/run/` 必须带 `Accept-Encoding ""` + `sub_filter`；
3. 改完 nginx 配置要 `nginx -t` 后 `Restart-Service nginx`；
4. 自检：`curl https://collectionofcreations.uk/api/v1/run/<id>/ | tail -c 200`，能看到 `<script src="/coc-key-bridge.js"></script>` 即为注入成功。

若桥接不可用，App 侧会在进全屏后 1.5s 给出提示（`pingBridge` 无 `pong`），不会静默失效。

### 备注

- 只有「全屏 + 可运行 HTML + 窄屏 + 用户开启按键」四个条件同时成立才渲染虚拟按键层，普通浏览与桌面端完全不受影响。
- 方案存在 `localStorage['coc_vpad_v1']`，仅本机有效，不跟随账号；清缓存会回到默认布局。
- 按键数量上限 12 个，避免误加到糊满屏幕。
- 详细状态与版本记录见 `docs/PROJECT-STATUS-20261115.md` 第十一章。

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

## 2026-11-19 · 设置页新增 Bug 反馈渠道 + 管理后台受理

### 改动目的

用户此前没有统一的 Bug 反馈入口。本次在「设置」页新增反馈表单（登录用户可提交），新增 `bug_feedback` 表（用户 ID + 反馈内容 + 状态码），管理后台新增「Bug 反馈」页，可按状态筛选未受理 / 已处理，并将单条反馈标记为已处理。

### 具体改动

| 文件 | 行号 | 改动说明 |
| --- | --- | --- |
| `backend/src/main/resources/db/migration/V3__bug_feedback.sql` | L1-L14 | **新增迁移**：`bug_feedback` 表（`bug_id` / `user_id` / `content VARCHAR(1000)` / `status TINYINT 0未受理 1已处理` / `created_at`），含状态索引、用户索引与外键 |
| `backend/.../mapper/BugFeedbackMapper.java` | L1-L39 | **新增**：插入反馈、按 ID 查询、管理端分页列表（联表取昵称/QQ）、按状态计数、标记已处理 |
| `backend/.../service/BugFeedbackService.java` | L1-L80 | **新增**：提交校验（必填、≤1000 字）、管理端列表（`status` 可选）、标记已处理（不存在/已处理时返回业务错误） |
| `backend/.../controller/InteractionController.java` | L27-L35、L85-L90 | 注入服务并新增 `POST /api/v1/bug-feedback` |
| `backend/.../controller/AdminController.java` | L15、L28-L34、L158-L170 | 新增 `GET /api/v1/admin/bug-feedback`（可按 `status` 过滤）与 `POST /api/v1/admin/bug-feedback/{bug_id}/handle` |
| `backend/.../auth/AuthInterceptor.java` | L44 | 将 `POST:/api/v1/bug-feedback` 加入必须登录白名单 |
| `frontend/src/api/index.js` | L45-L46、L103-L104 | 新增 `interactApi.bugFeedback`、`adminApi.bugFeedbacks` / `handleBugFeedback` |
| `frontend/src/views/Settings.vue` | L63-L85、L187-L206、L313 | 新增「问题反馈」卡片：多行输入（1000 字上限、字数统计、`data-field` 兜底）+ 提交按钮 |
| `frontend/src/views/admin/AdminBugFeedback.vue` | L1-L108 | **新增管理页**：未受理 / 已处理 / 全部筛选、用户信息、反馈内容、状态、时间、标记已处理 |
| `frontend/src/router/index.js` | L52 | 新增 `/admin/bug-feedback` 管理路由 |
| `frontend/src/layouts/ConsoleLayout.vue` | L87 | 管理后台「用户」分组新增「Bug 反馈」菜单 |

### 备注

- 后端已通过 `mvn -DskipTests compile` 编译校验；数据库迁移由 Flyway 在后端下次启动时自动执行，本次未重启生产服务、未部署。
- 反馈只记录用户 ID、内容与状态，不采集设备信息；管理端列表联表显示提交人昵称与 QQ。
- 本次同属 **COC 五期测试** 过程中的小改动，仍未执行前端构建与 APK 打包。

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
