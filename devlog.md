# COC 项目开发日志（devlog）

本文件用于追踪、追述 COC 平台按「期」推进的优化改动内容：每完成一次优化改动，就在本文件新增一条记录，写清改动目的、涉及文件与关键行号，方便后续开发、交接和版本回归时快速回溯。记录按时间倒序排列，最新改动放在最前面。

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
