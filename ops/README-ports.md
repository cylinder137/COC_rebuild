# 造物集（coc-platform）· 运维与试运行说明

本目录是**本机试运行**的运维入口。所有脚本都是幂等的，可反复执行。

> 只动 `ops\` 与 `packer\`；`backend\` / `frontend\` / `contract\` 未做任何修改。

---

## 1. 端口与启动顺序

| 端口 | 服务 | 启动脚本 | 日志 |
| --- | --- | --- | --- |
| 3306 | MySQL 8.0（**系统既有**，不是本项目起的） | — | — |
| 8090 | 后端 Spring Boot（Web REST + WS） | `start-backend.cmd` | `logs\backend.log` |
| 8085 | 前端主站（Vite dev，`zone=main`） | `start-frontend.cmd` | `logs\frontend.log` |
| 8086 | 前端用户内容区（Vite dev，`--mode user`） | `start-frontend-user.cmd` | `logs\frontend-user.log` |
| 8091 | 打包服务（Node，Scratch → 单文件 HTML） | `start-packer.cmd` | `logs\packer.log` |

**首次部署顺序**

```cmd
ops\init-db.cmd                 :: 1. 建库 coc_platform（utf8mb4 / utf8mb4_0900_ai_ci）
ops\start-backend.cmd           :: 2. 起后端，Flyway 会自动执行 V1__init.sql 建表
ops\seed\run-seed.cmd           :: 3. 导演示数据（会先清掉后端自测残留，再灌种子）
ops\start-packer.cmd            :: 4. 起打包服务
ops\start-frontend.cmd          :: 5. 主站        http://127.0.0.1:8085
ops\start-frontend-user.cmd     :: 6. 用户内容区  http://127.0.0.1:8086
```

**后台常驻的起法（重要）**

三个长驻服务都配了 `.vbs`，用 `wscript.exe` 脱离当前会话拉起：

```powershell
cd E:\personalProjects\coc-platform\ops
wscript.exe start-backend.vbs
wscript.exe start-frontend.vbs
wscript.exe start-frontend-user.vbs
wscript.exe start-packer.vbs
```

> ⚠️ **不要用 `Start-Process` 起长驻服务**：它的子进程挂在当前 shell 会话下，
> 会话被回收时会连带被杀。`start-*.cmd` 本身是前台阻塞的（负责跑 java/node/npm），
> 必须由 `.vbs`（或 `Invoke-CimMethod Win32_Process Create`）来脱离会话。

**停止**

```cmd
ops\stop-all.cmd          :: 停 8090 / 8085 / 8086 / 8091（先温和 taskkill，3 秒后仍在则 /F）
ops\stop-all.cmd 8091     :: 只停打包服务
```

> 该脚本只处理这 4 个端口，**不会碰** nginx(80)、MySQL(3306)、cloudflared、
> 以及旧造物集的 8080/8081/8082/8083。

---

## 2. 数据库密码的处理（安全约定）

- 密码只存在于 `C:\Users\Administrator\.tinker\db_password.txt`（**不在仓库里**）。
- `init-db.cmd` / `run-seed.cmd`：由 `_mycnf.ps1` 把密码写进临时 `%TEMP%\coc-my.cnf`，
  以 `mysql --defaults-extra-file=` 传入，用完立即删除。
  → 密码**不进命令行参数**（不会被 `tasklist`/进程列表看到）、**不进仓库**。
- `start-backend.cmd`：由 `_dbpw.ps1` 读出后放进环境变量 `COC_DB_PASSWORD`
  （`application.yml` 里就是 `${COC_DB_PASSWORD:}`），同样不落盘、不回显。
- 支持密码文件里带引号（会自动去掉首尾的 `'` / `"`）；但**密码里不要含 `"` 或换行**。

---

## 3. 测试账号（仅本机试运行用）

密码统一 **`Coc@2026`**（BCrypt，cost 10，由 `seed\gen-hash.cmd` 生成）。

| QQ 号 | 角色 | 类型/状态 | 说明 |
| --- | --- | --- | --- |
| `3254328344` | 平台管理员 | `type=3, status=1` | 管理后台、审核、下架 |
| `10001` | 开发者「星野工作室」 | `type=2, status=1` | 有已发布项目 |
| `10002` | 开发者「像素小工坊」 | `type=2, status=1` | 有已发布 + 待审 + 已下架项目 |
| `10003` | 待审开发者申请 | `type=2, status=0` | 用来验证「开发者申请审核」流程 |

改密码后重新生成哈希：

```cmd
ops\seed\gen-hash.cmd MyNewPass sql     :: 输出 SET @pw := '...'; 粘进 seed.sql 顶部
```

> ⚠️ **这些账号只用于本机试运行，上线前必须全部删除或改密。**
> 该库是全新的 `coc_platform`，与旧站数据无任何继承关系。

---

## 4. 种子数据说明（`seed\` 目录）

| 文件 | 作用 |
| --- | --- |
| `seed.sql` | 主种子（用户/分类/标签/6 个项目/评论/IM/通知/举报…），幂等 |
| `seed-finalize.sql` | 汇总 `view_count_total`、回写 `comment_count`、幂等自检 |
| `clean-selftest.sql` / `clean-selftest.cmd` | 清掉后端自测残留（`qq 9000xx` 账号及其项目等） |
| `gen-hash.js` / `gen-hash.cmd` | 生成 BCrypt 密码哈希 |
| `gen-covers.js` | 生成 6 张渐变封面 PNG（无依赖，自带 PNG 编码） |
| `gen-demo-files.js` | 生成 6 个演示项目的**真实源码文件**（自建 zip 写 sb3） |
| `gen-views.js` | 生成近 30 天 / 近 48 小时浏览曲线（确定性的伪随机，可重复） |
| `run-seed.cmd` | 一键：生成资源 → 等表就绪 → 清自测残留 → 灌种子 → 打印行数 |
| `assets\files.generated.sql` | 自动生成：源码文件的真实 `storage_path` / `file_size` / `sha256` |
| `assets\views.generated.sql` | 自动生成：浏览统计 INSERT |
| `assets\demo\` | 演示源码模板（HTML 小游戏等） |

**幂等性**：所有 INSERT 都是显式主键 + `ON DUPLICATE KEY UPDATE`（浏览表用
`(project_id, stat_date/stat_hour)` 主键），`run-seed.cmd` 可**反复执行**。
结尾会打出自检行（应为 `0 0 0 8 30 48`）。

**表还没建怎么办**：`run-seed.cmd` 会轮询 `SHOW TABLES LIKE 'project'`，
最多等 **15 分钟**（每 20 秒一次）。表由后端首次启动时 Flyway 建。

### 6 个演示项目

| id | 标题 | 类型 | 状态 | 审核 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 201 | 弹跳星球大冒险 | html | 2 已发布 | manual | **真实可交互 Canvas 打砖块小游戏**（约 240 行，`.data\projects\201\v1\source\index.html`） |
| 202 | 粒子烟花秀 | html | 1 待审 | manual | 待审队列演示（Canvas 粒子） |
| 203 | 小猫走迷宫 | scratch | 2 已发布 | auto | sb3 源码 + **真实 Turbowarp 打包产物** |
| 204 | 打地鼠小游戏 | scratch | 2 已发布 | auto | 同上 |
| 205 | 中世纪小酒馆建筑 | structure | 2 已发布 | auto | `.mcstructure`，结构类不需要打包 |
| 206 | 跳转测试页面 | html | 3 已下架 | manual | 带 `down_reason`，配套 1 条举报/下架通知 |

另外：6 个分类、8 个标签、8 条评论（含 1 条楼中楼）、1 个项目群 + 1 个单聊
（共 7 条 IM 消息）、3 条通知、2 条举报、1 条找回密码申请、2 条打包任务记录。

**浏览数据**：给 4 个已发布项目灌了**近 30 天按天** + **近 48 小时按小时**的
浏览曲线（带周末/昼夜波动），`view_count_total` 由按天数据汇总而来。

### 产物落盘位置（`.data\`）

```
.data\projects\{project_id}\v{n}\source\     源码（index.html / project.sb3 / *.mcstructure）
.data\projects\{project_id}\v{n}\build\      打包产物（index.html，存在则优先作为运行目录）
.data\covers\                                封面占位图 cover-01..06.png
```

> 运行目录规则（后端 `FileService.resolveRunRoot`）：**优先 `build\index.html`，否则 `source\`**。

---

## 5. 已知坑 / 排障

| 现象 | 原因 / 处理 |
| --- | --- |
| `init-db.cmd` 报连不上 MySQL | 密码文件不对或 MySQL 没起：`Get-Service *mysql*` |
| `run-seed.cmd` 一直打点等表 | 后端没起来 → Flyway 没跑。先 `start-backend.cmd`，看 `logs\backend.log` |
| 种子报 `foreign key constraint fails` | 拼接顺序被改动了。顺序必须是 `files.generated.sql → seed.sql → views.generated.sql → seed-finalize.sql` |
| 封面图 404（前端显示渐变占位） | **后端目前没有 `/covers/**` 静态路由**，`fileUrl()` 会拼成 `http://127.0.0.1:8090/covers/xxx.png`。图已在 `.data\covers\`，需后端补一个静态映射才能显示（属后端范围，未改） |
| 端口已占用但脚本说"nothing to do" | 这是**设计行为**（幂等）。要重启先 `stop-all.cmd` |
| 中文在 cmd 窗口里显示乱码 | 控制台代码页问题，库里存的是正确的 UTF-8（可用 `SELECT HEX(title)` 验证） |
| 前端 `strictPort` 起不来 | 8085/8086 被占：`stop-all.cmd 8085` |
| `npm install` 很慢 | 首次需要在 `frontend\` 装依赖，脚本会自动装 |

---

## 6. 自测记录（2026-10-15）

- `init-db.cmd` → `coc_platform` 已建，`utf8mb4 / utf8mb4_0900_ai_ci` ✔
- `run-seed.cmd` → 成功，自检 `0 0 0 8 30 48`；**再跑一次仍成功**（幂等）✔
- `clean-selftest.cmd` → 后端自测残留清零（`0 / 0 / 0 / 0`），并删除 `.data\projects\2` ✔
- 打包服务 `/health` → `{"status":"ok","packager":"turbowarp",...}` ✔
- `POST /pack` → 真打包成功（1.83 MB，服务端 34 ms / 15 ms），远小于后端 4 秒超时 ✔

> 自测结束后已停掉**本会话自己启动的**打包服务（8091）。
> 8090 上是后端兄弟会话的实例，**未做任何干预**。
