# coc-platform MVP 契约（试运行版 · 2026-10-15）

> 依据：桌面设计文档 v1.3（三份拍板 10/11/12 为准，冲突以 12 > 11 > 10 排序）
> 本文件是**前后端唯一的对接契约**：字段名、路径、结构以此为准，不要各自发明。
> 目标：**能跑起来的试运行版**（不是完整 32 表全功能，二期再补）。

## 0. 铁律（照抄）

- **零继承**：全新库 `coc_platform`、全新代码，不复用旧 `zaowuji` 任何表/代码（旧站只作视觉参考，主色 `#4F6EF7`）
- **身份三态**：匿名访客（**不写库、无账号行**）⊂ 开发者（`user.type=2`，QQ 号 + 密码登录）⊂ 管理员（`user.type=3`）
- 访客**只能**浏览页面/详情/列表、运行项目、看公开图表、搜索；评论/点赞/收藏/举报/下载源码/私信/投稿**一律要求登录**
- 项目状态：`0 草稿 / 1 待审 / 2 已发布 / 3 已下架 / 4 已拒绝`，删除用 `is_deleted=1` 表达
  - `audit_mode`：`html`=`manual`（需管理员审核）；`scratch`、`mcstructure`/`structure` = `auto`（**免审直发 2**）
- 浏览量：**只按天/小时存计数**（`project_view_daily` / `project_view_hourly`），无用户维度、无明细；折线**读表动态计算**
- 举报正反馈：处理结果通知 + `reporter_reward_points` 加分
- 密码：BCrypt 不可逆；找回 = **联系管理员 QQ 3254328344** → 管理员生成**一次性临时密码**（24h、首登强制改密、记审计）
- 端口：**主站 8085 / 用户内容区 8086 / 后端 8090 / 打包服务 8091**（8082 已占用，勿用）
- 存储根：本机试运行用 `E:\personalProjects\coc-platform\.data\`（结构照 04 §9）
- 统一响应包装：`{"code":0,"message":"ok","data":{...}}`；出错 `code!=0` + `message`；HTTP 状态码尽量语义化（401/403/404）

## 1. 目录划分（各子任务只动自己那一块）

```
E:\personalProjects\coc-platform\
├─ backend\      9030... → Spring Boot 3.5 + Java 21 + MyBatis + Flyway（子会话 A）
├─ frontend\     Vue3 + Vite + Element Plus + Pinia（子会话 B）
├─ packer\       Node 打包服务 :8091（子会话 C）
├─ ops\          初始化库 / 种子数据 / 启停脚本 / 端口说明（子会话 C）
├─ contract\     本文件
└─ .data\        运行时上传/产物目录
```

## 2. MVP 表（Flyway `V1__init.sql` 建这些即可，共 23 张）

1. `user`(user_id PK, qq_account UNIQUE, password_hash, nickname, avatar_path, type TINYINT(2开发者/3管理员), status(0待审/1正常/2禁用/3已注销), token_version, last_login_at, created_at, updated_at, cancelled_at, cancel_reason)
2. `developer_profile`(developer_profile_id PK, user_id FK, contact, apply_status(0待审/1通过/2驳回), reason, applied_at, reviewed_at, reviewer_id, contribution_value INT DEFAULT 0)
3. `category`(category_id PK, name, slug UNIQUE, sort_order, status)
4. `tag`(tag_id PK, name UNIQUE), 5. `project_tag`(project_tag_id PK, project_id, tag_id)
6. `project`(project_id PK, author_id FK, title, summary, description MEDIUMTEXT, project_type ENUM('html','scratch','structure'), category_id, cover_path, status, audit_mode, is_deleted, down_reason, delete_reason, like_count, comment_count, favorite_count, view_count_total, published_at, created_at, updated_at)
7. `project_version`(project_version_id PK, project_id FK, version_no INT, changelog, created_by, created_at)
8. `project_file`(project_file_id PK, project_version_id FK, file_type('source','build','cover'), file_name, file_size BIGINT, sha256 CHAR(64), storage_path VARCHAR(512), scan_status, scan_detail, build_status(0无需/1待打包/2成功/3失败), build_path, created_at)
9. `project_review`(project_review_id PK, project_version_id FK, admin_id, action('approve','reject','down'), reason, created_at)
10. `comment`(comment_id PK, project_id FK, user_id FK, parent_comment_id NULL, root_comment_id NULL, content TEXT, like_count, is_deleted, created_at)
11. `comment_like`(comment_like_id PK, comment_id FK, user_id FK, created_at, UNIQUE(comment_id,user_id))
12. `project_like`(project_like_id PK, project_id, user_id, created_at, UNIQUE(project_id,user_id))
13. `project_favorite`(project_favorite_id PK, project_id, user_id, created_at, UNIQUE(project_id,user_id))
14. `project_view_daily`(project_id, stat_date DATE, view_count INT, PK(project_id,stat_date))
15. `project_view_hourly`(project_id, stat_hour DATETIME, view_count INT, PK(project_id,stat_hour))
16. `report`(report_id PK, reporter_id FK, target_type('project','comment','user'), target_id, reason_type, detail, status(0待处理/1已处理/2已驳回), handler_id, handle_result, handled_at, created_at)
17. `follow`(follow_id PK, follower_id, followee_id, created_at, UNIQUE(follower_id,followee_id))
18. `im_session`(im_session_id PK, kind TINYINT(1单聊/2项目群), project_id NULL, user_a_id, user_b_id NULL, title, last_message_at, last_message_preview, created_at)
19. `im_message`(im_message_id PK, session_id FK, sender_id, content TEXT, content_type(1文本/2图片/3文件), client_msg_id VARCHAR(64), is_deleted, created_at)
20. `im_session_member`(im_session_member_id PK, session_id FK, user_id FK, last_read_message_id BIGINT, joined_at, UNIQUE(session_id,user_id))
21. `notification`(notification_id PK, user_id FK, type, title, content, link, is_read, created_at)
22. `operation_log`(log_id PK, admin_id, action, target_type, target_id, detail, ip, created_at)
23. `upload_session`(upload_session_id PK, user_id, file_name, file_size, sha256, chunk_size, total_chunks, uploaded_chunks JSON, status(0进行中/1已完成/2已取消), storage_path, created_at, updated_at)
    + `build_task`(build_task_id PK, project_file_id, status(0待处理/1进行中/2成功/3失败), log_path, output_path, created_at, finished_at)
    + `password_reset_apply`(apply_id PK, qq_account, contact, note, status(0待处理/1已办结), temp_password, applied_at, handled_by, handled_at)
    + `agreement_version`(agreement_version_id PK, version_no, title, content_path, is_current, published_at)

> 以上括号里已给出核心字段；**具体 DDL 由后端子会话补全**（含索引、外键、utf8mb4、InnoDB、注释）。多出的 3 张辅助表算 MVP 的一部分。

## 3. 接口清单（MVP · 前缀 `/api/v1`）

### 3.1 认证（公开）
| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/dev-apply` | 公开 | 开发者申请：`{qq_account, password, nickname, contact}` → 建 user(status=0)+developer_profile(0) |
| POST | `/api/v1/auth/login` | 公开 | `{qq_account, password}` → `{token, user:{user_id,qq_account,nickname,type,avatar_path}}`；status=0 报「审核中」、3 报「已注销」 |
| GET | `/api/v1/auth/me` | 登录 | 当前用户 `+is_admin` |
| POST | `/api/v1/auth/change-password` | 登录 | `{old_password,new_password}`；**临时密码首登必须改密**（`must_change_password` 标记） |
| POST | `/api/v1/auth/password/forgot` | 公开 | `{qq_account, contact, note}` → `{apply_id}` |
| GET | `/api/v1/system/health` | 公开 | `{status, db, time}` |

### 3.2 内容（访客可读）
| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/projects` | 公开 | `page,size,category_id,project_type,keyword,sort(latest\|hot)` → `{list,total,page,size}` |
| GET | `/api/v1/projects/{project_id}` | 公开 | 详情：项目 + 作者 + 文件摘要 + 当前状态；**访客看得到已发布(2)的**；作者本人可见自己的任意状态；管理员可见全部 |
| POST | `/api/v1/projects/{project_id}/view` | 公开 | 计数：`project_view_daily`/`hourly` 累加 + `view_count_total+1` |
| GET | `/api/v1/projects/{project_id}/stats?range=2\|7\|15\|30` | 公开 | 折线：`[{date,views,comments}]`（**动态计算**；range=2 走小时表） |
| GET | `/api/v1/projects/{project_id}/run` | 公开 | `{mode:'html'\|'scratch', url, sandbox:true}` → 运行地址（沙箱 iframe 用） |
| GET | `/api/v1/projects/{project_id}/comments?page&size=20` | 公开 | 评论树（楼中楼一层）：`{list:[{comment_id,user:{...},content,like_count,is_author,created_at,replies:[...]}],total}` |
| GET | `/api/v1/categories` | 公开 | 分类列表 |
| GET | `/api/v1/users/{user_id}` | 公开 | 个人主页：昵称/头像/关注数/作品数 + 已发布作品列表 |
| GET | `/api/v1/users/{user_id}/follow-status` | 公开 | `{following:bool, followers:int}` |

### 3.3 互动（必须登录）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/comments` | `{project_id, content, parent_comment_id?}` → 新评论；作者发言带 `is_author:true` |
| POST | `/api/v1/comments/{comment_id}/like` | 切换点赞 → `{liked, like_count}` |
| POST | `/api/v1/projects/{project_id}/like` | 切换点赞 → `{liked, like_count}` |
| POST | `/api/v1/projects/{project_id}/favorite` | 切换收藏 → `{favorited, favorite_count}` |
| POST | `/api/v1/users/{user_id}/follow` | 切换关注 → `{following, followers}` |
| POST | `/api/v1/reports` | `{target_type,target_id,reason_type,detail}` → `{report_id}`（提交给正反馈积分） |
| GET | `/api/v1/reports/me?page&size` | 我的举报：`{list:[{report_id,target,status,handle_result,created_at}],total,reward_points}`（**诉求 #11：正反馈**） |
| POST | `/api/v1/reports/me/read` | 标记已读处理结果 |

### 3.4 投稿与文件（必须登录 = 开发者）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/projects` | 建草稿 `{title,summary,description,project_type,category_id,tags[]}` → `{project_id}` |
| PUT | `/api/v1/projects/{project_id}` | 改草稿（仅作者、仅 status in 0/4） |
| POST | `/api/v1/uploads/init` | `{file_name,file_size,sha256,chunk_size?}` → `{upload_session_id, chunk_size, total_chunks, uploaded_chunks:[], instant:bool, project_file_id?}`（**秒传**：sha256 命中已有文件则直接返回） |
| POST | `/api/v1/uploads/{upload_session_id}/parts` | multipart：`part_number` + `chunk` → `{uploaded_chunks:[...]}`（**支持乱序+续传**） |
| GET | `/api/v1/uploads/{upload_session_id}` | 查询已上传分片（断点续传） |
| POST | `/api/v1/uploads/{upload_session_id}/complete` | 校验 sha256 → 落盘 `storage_path` → 建 `project_file` → 返回 `{project_file_id, storage_path, build_task_id?}`（scratch 触发 8091 打包） |
| POST | `/api/v1/projects/{project_id}/submit` | 提交：`html`→status=1 待审；`scratch`/`structure`→**直接 2 已发布** |
| GET | `/api/v1/users/me/projects?status=&page&size` | 我的作品（含下架状态、拒绝原因） |
| GET | `/api/v1/files/{project_file_id}/content` | **下载源码（仅登录**；访客 401/403）+ `operation_log` 记审计 |
| POST | `/api/v1/projects/{project_id}/build/retry` | 重新触发打包（管理员/作者） |

### 3.5 私信与项目群（必须登录）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/im/conversations` | 会话列表（单聊 + 我参与的项目群） |
| GET | `/api/v1/im/conversations/{kind}/{id}/messages?cursor=&limit=20` | **按 20 条游标分页**；`kind`: 1 单聊 / 2 项目群 |
| POST | `/api/v1/im/conversations/{kind}/{id}/messages` | 发消息 `{content, client_msg_id}` |
| POST | `/api/v1/im/conversations/{kind}/{id}/read` | 标记已读 |
| GET | `/api/v1/im/unread` | 未读汇总 |
| WS | `/ws?token=` | 连接后 `{"type":"hello"}`；服务端推 `{"type":"message","session_id":..,"message":{...}}`；心跳 `ping/pong` |

### 3.6 管理端（`type=3`，全部记 `operation_log`）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/projects?status=&include_deleted=&page&size` | 项目管理列表 |
| GET | `/api/v1/admin/projects/{project_id}/storage` | **存储路径 + 文件清单（仅管理员可见，写审计）** |
| POST | `/api/v1/admin/reviews/{project_version_id}/approve` / `/reject` | 审核（`{reason}`） |
| POST | `/api/v1/admin/projects/{project_id}/down` / `/restore` | 下架（代码保留）/ 恢复 |
| DELETE | `/api/v1/admin/projects/{project_id}` | 删除（含服务器留存代码，物理删 + `operation_log`） |
| DELETE | `/api/v1/admin/comments/{comment_id}` | 删任意评论（**作者本人无此权限**） |
| GET | `/api/v1/admin/developer-applications?status=` | 开发者申请列表 |
| POST | `/api/v1/admin/developer-applications/{id}/approve` / `/reject` | 通过（`user.status=1`）/ 驳回 |
| GET | `/api/v1/admin/reports?status=` | 举报列表 |
| POST | `/api/v1/admin/reports/{report_id}/handle` | `{action:'resolve'\|'reject', result}` → 通知举报人 + 加分 |
| GET | `/api/v1/admin/password-resets` | 找回申请 |
| POST | `/api/v1/admin/users/{user_id}/reset-password` | 生成**一次性临时密码**（24h、首登改密、审计） |
| POST | `/api/v1/admin/users/{user_id}/cancel` | 注销账号（软删 status=3，作品保留、作者名显示「用户已注销」） |
| GET | `/api/v1/admin/stats/platform` | 平台统计（用户数/项目数/待审数/今日浏览） |

## 4. 前端页面（MVP，路由口径照 01 册）

| 路由 | 页面 | 权限 | 要点 |
| --- | --- | --- | --- |
| `/` | 首页 | 访客 | 项目卡片流 + 分类/类型筛选 + 搜索 + 排序（最新/最热） |
| `/projects/:id` | 项目详情 | 访客 | **左：沙箱 iframe 运行区（可全屏）+ 右/下：评论区常驻**（#9）、点赞/收藏/举报；访客点互动 → 弹登录 |
| `/login` | 登录 | 公开 | 单页登录（QQ 号 + 密码），按 `type` 动态落地 |
| `/dev-apply` | 开发者申请 | 公开 | QQ 号 + 密码 + 昵称 + 联系方式；协议弹窗 |
| `/forgot` | 联系管理员 | 公开 | 醒目展示 **QQ 3254328344** + 提交找回申请 |
| `/upload` | 投稿向导 | 开发者 | 类型选择 → 信息填写 → **分片上传（进度/续传）** → 提交审核/免审直发 |
| `/me/projects` | 我的作品 | 开发者 | 状态徽标（待审/已发布/已下架/已拒绝+原因） |
| `/me/dashboard` | 数据看板 | 开发者 | 浏览量/评论量折线，**2/7/15/30 天切换** |
| `/me/reports` | 我的举报 | 开发者 | 处理进度 + 正反馈积分（#11） |
| `/users/:id` | 个人主页 | 访客 | 头像/昵称/关注数/作品列表 + 关注按钮 |
| `/messages` | 消息中心 | 开发者 | 单聊 + 项目群双形态、**20 条分页触顶加载**、未读红点 |
| `/admin` 及子页 | 管理后台 | 管理员 | 审核队列 / 项目管理（**含存储路径查看**）/ 评论管理 / 举报处理 / 开发者申请 / 找回申请 / 注销账号 / 平台统计 |
| `/403` `/404` | 错误页 | 公开 | — |

## 5. 运行与端口（试运行）

| 服务 | 端口 | 启动方式 |
| --- | --- | --- |
| 后端（Spring Boot） | **8090** | `ops\start-backend.cmd`（jar/`mvn spring-boot:run`，日志 `ops\logs\backend.log`） |
| 主站前端（Vite） | **8085** | `ops\start-frontend.cmd`（`npm run dev -- --port 8085`） |
| 用户内容区（Vite） | **8086** | `ops\start-frontend-user.cmd`（同代码 `--mode user --port 8086`） |
| 打包服务（Node） | **8091** | `ops\start-packer.cmd` |
| MySQL | 3306 | 已存在；新库 `coc_platform`（utf8mb4 / InnoDB），账号 `root`，密码从 `.tinker\db_password.txt` 读，**不要写进任何仓库文件** |

## 6. 交付验收（试运行要能点）

1. 匿名访客打开 8085 → 首页有种子项目 → 点进详情 → **沙箱内能真的运行**（html 项目内嵌页面可交互）
2. 开发者登录（种子账号）→ 投稿（上传一个 html/zip）→ 我的作品看到状态 → 管理员审核通过 → 首页可见
3. 详情页点赞/收藏/评论，数据看板折线（2/7/15/30 天切换）出图
4. 访客点评论/点赞 → 弹登录（**不写库**）
5. 管理员后台：审核 / 下架 / 删除 / 删评 / 看存储路径 / 处理举报 / 重置密码 / 注销账号
6. 消息中心：两个账号互发私信，**20 条分页加载**；项目群消息能收发
