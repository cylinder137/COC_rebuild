# coc-platform 部署说明（给部署方 / Rivet）

> 本文件由 Tinker 于 2026-10-15 编写，配套本地已验收通过的 MVP 版本（`56100ef` + `d6a535a`）。
> 本地验收结论：**端到端 18/18 通过**（详见 `ops/e2e_upload_flow.py` 输出）。
> 本机试运行端口：后端 **8090** / 主站 dev **8085** / 用户内容区 dev **8086** / 打包服务 **8091**。

---

## 1. 仓库结构（单仓多模块）

```
coc-platform/
├─ backend/    Spring Boot 3.5 + Java 21 + MyBatis + Flyway（端口 8090，含 WebSocket /ws）
├─ frontend/   Vue3 + Vite + Element Plus + Pinia（构建为静态站点；dev 8085/8086）
├─ packer/     Node 常驻服务（端口 8091，@turbowarp/packager 3.13.0，sb2/sb3 → 单文件 HTML）
├─ ops/        建库 / 种子数据 / 启停脚本 / 验收脚本（Windows 批处理）
├─ contract/   api-mvp.md —— 前后端唯一接口契约（字段口径以此为准）
└─ .data/      运行时数据（上传源文件 / 打包产物 / 封面 / 头像；**不入库不入仓**）
```

## 2. 运行环境

| 组件 | 版本要求 | 说明 |
| --- | --- | --- |
| JDK | **21**（已在 21.0.9 验证） | 后端编译与运行 |
| Maven | 3.9+ | 后端打包 |
| Node.js | 20+（已在 24.20.0 验证） | 前端构建 + 打包服务 |
| MySQL | **8.0+**（8.4 亦可） | 新库 `coc_platform`，utf8mb4 / InnoDB |
| nginx | 1.28（本机在用） | 静态站点 + `/api` `/ws` 反代 + 沙箱子域 |
| cloudflared | 现有隧道 | 域名对外（本机无公网 IP） |

## 3. 构建

```bash
# 后端（产物 backend/target/coc-platform-backend-0.1.0.jar）
cd backend && mvn -DskipTests clean package

# 前端（主站 → dist/；用户内容区 → dist-user/）
cd frontend && npm ci && npm run build && npm run build:user

# 打包服务（依赖装了就能跑；容器/服务器首次需 npm ci）
cd packer && npm ci
```

## 4. 配置（**不要把这些值提交进仓库**）

### 4.1 后端环境变量 / `application.yml`

| 键 | 本地试运行值 | 生产必须改成 |
| --- | --- | --- |
| `COC_DB_PASSWORD` | 从 `.tinker/db_password.txt` 注入 | 生产库密码（走环境变量/密钥管理） |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/coc_platform?...` | 生产库地址（库名保持 `coc_platform`） |
| `coc.storage.root` | `E:/personalProjects/coc-platform/.data` | **服务器路径**，设计文档口径为 `E:\Server Data\coc-platform` |
| `coc.jwt.secret` | ⚠️ `coc-platform-dev-secret-key-...`（**开发明文，上线务必替换**） | 随机 64 字节以上，走环境变量 |
| `coc.packer.url` | `http://127.0.0.1:8091/pack` | 同机则不变 |
| `coc.cors.allowed-origins` | `8085/8086 localhost` | 生产域名（如 `https://collectionofcreations.uk`） |
| `server.port` | 8090 | 不变（与契约一致） |

- **数据库迁移自动执行**：Flyway `V1__init.sql`（26 张表）在应用启动时建表；生产首次启动只需保证库存在 + 账号有权限（`init-db.cmd` 建库逻辑见 `ops/`）。
- 上传限制：单文件 200 MB（`spring.servlet.multipart`），与设计文档的 Cloudflare 免费版 100 MiB/请求上限**不冲突**（前端分片 8–32 MB）。

### 4.2 前端构建期变量（`.env.production` / CI 注入）

```
VITE_API_BASE=https://collectionofcreations.uk/api/v1
VITE_WS_BASE=wss://collectionofcreations.uk
VITE_ZONE=main        # 用户内容区构建用 user
```

> 注意：前端是**构建期注入**，改域名要重新 build，不能只改服务器文件。

### 4.3 打包服务

- 监听 8091，被后端调用（后端超时 4 秒；超时会写 `build_task` 待处理，可后台重试`/build/retry`）。
- 单文件 sb3 打包实测 **~34 ms**；损坏/非法文件自动降级为「占位播放器 HTML」并返回 `fallback:true`，**不会阻塞投稿流程**。

## 5. 生产启动方式（与本地不同！）

- **前端不要再起 Vite dev server**：`dist/` 与 `dist-user/` 交给 nginx 静态托管（本地 dev 的 8085/8086 只是开发用）。
- 后端 + 打包服务需要常驻。Windows 服务器可直接用 `ops/start-backend.vbs`、`ops/start-packer.vbs`（内部用 `wscript.exe` 脱离会话拉起，自带崩溃重拉）；
  Linux 建议 systemd 两个 unit。
- 目录准备（首次）：

```
E:\Server Data\coc-platform\
  ├─ projects\{project_id}\v{n}\source\   源码（用户上传，2.38MB 级文件）
  ├─ projects\{project_id}\v{n}\build\    打包产物（scratch → index.html，沙箱子域直出）
  ├─ covers\   avatars\   im\{yyyyMM}\   agreements\
  └─ uploads\tmp\{yyyyMMdd}\    uploads\staging\{user_id}\
```

- 运行时必须可写该目录；`uploads/**` **不要**映射到 nginx（设计文档明确要求）。

## 6. nginx 反代要点（设计文档 §9）

```nginx
# 主站
location /api/ { proxy_pass http://127.0.0.1:8090/api/; proxy_set_header X-Real-IP $remote_addr; }
location /ws   { proxy_pass http://127.0.0.1:8090/ws; proxy_http_version 1.1;
                 proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade";
                 proxy_read_timeout 300s; access_log off; }
location /covers/  { proxy_pass http://127.0.0.1:8090/covers/; }   # 也要放行（后端静态映射）
location /avatars/ { proxy_pass http://127.0.0.1:8090/avatars/; }
location / { root <前端 dist 目录>; try_files $uri $uri/ /index.html; }

# 沙箱子域 p1.collectionofcreations.uk：独立 server 块
#   root 指向 projects/**/build 产物目录（只读）
#   无 cookie、无 API、禁止列目录
#   页面内 iframe 使用 sandbox="allow-scripts"（**绝不能加 allow-same-origin**）
```

- 分片上传注意：Cloudflare 免费版单请求上限 **100 MiB**、超时 **125 s** → 前端分片保持 ≤ 32 MB 且并发 1，配合 keep-alive 复用连接。
- **`/admin` 建议加 Cloudflare Access**（设计文档待办项）。

## 7. 首次上线初始化

1. 建库：`CREATE DATABASE coc_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`
2. 启动后端（Flyway 自动建 26 张表）
3. 初始化最小数据（生产建议只跑这两样，**不要**灌演示项目）：
   - 管理员账号（`type=3, status=1`，BCrypt 密码）
   - 分类若干（`category`）
   - 当前协议版本（`agreement_version`）
   - 脚本参考 `ops/seed/`（`gen-hash.js` 生成 BCrypt；`seed.sql` 里有演示数据的完整示例，可裁掉 `project` 相关段落）
4. 上线后立刻：**改管理员初始密码**、**替换 JWT secret**、确认 `.data` 目录权限、确认 8091 与 8090 只监听内网。

## 8. 健康检查 / 验收

```bash
curl -s http://127.0.0.1:8090/api/v1/system/health   # {"code":0,...,"data":{"status":"ok","db":"ok"}}
curl -s http://127.0.0.1:8091/health                 # {"status":"ok","packager":"turbowarp"}
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8090/api/v1/run/<已发布项目id>/   # 应 200
```

- 全链路验收脚本（需 Node/Python 环境与测试账号）：`ops/e2e_upload_flow.py`、`ops/smoke_integration.py`
- 验收要点：访客能浏览 + 能直接运行项目；访客写操作（评论/点赞/举报/下载源码）全部 **401**；html 项目进待审、scratch/structure 免审直发。

## 9. 已知未做（二期，不影响本次上线）

- 32 张表只落地 26 张（协议/日程/成就等辅助表按设计文档二期补）
- `structure`（mcstructure）仅元数据卡，**没有 3D 预览**（设计文档方案：deepslate 锁 0.25.1，二期做）
- 浏览量无防刷（无 IP/UA 维度）；上传文件未接病毒扫描（`scan_status` 恒为 1）
- 无单元测试（只有端到端脚本）；Element Plus / ECharts 全量引入，包体偏大
- 浏览器端 `structure`/`scratch` 的移动端适配未细调

## 10. 安全提醒（上线前逐条确认）

- [ ] `coc.jwt.secret` 换成生产随机值（**当前仓库里的值是开发用明文**）
- [ ] 数据库密码只走环境变量，不进仓库、不进日志
- [ ] `/admin` 加 Cloudflare Access 或 IP 白名单
- [ ] 沙箱 iframe **不得**加 `allow-same-origin`；沙箱子域与主站必须不同域
- [ ] `.data` 目录只允许后端进程读写，不对外开放
- [ ] 管理员初始账号密码已修改；测试账号（`10001`/`10002`/`10003`）在生产**删除或改名**
