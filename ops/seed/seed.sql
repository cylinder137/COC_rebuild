-- =====================================================================
-- 造物集 coc-platform : 演示 / 试运行种子数据（幂等）
--
-- 目标：让重构后的站点一启动就有「像真的」的内容 —— 管理员、开发者、
--       待审申请、分类标签、6 个演示项目（含 1 个可交互 HTML 小游戏）、
--       近 30 天 / 近 48 小时浏览曲线、点赞收藏评论（含楼中楼）、
--       项目群 IM、通知、举报、找回密码申请。
--
-- 幂等：所有 INSERT 都带 ON DUPLICATE KEY UPDATE（显式主键），
--       可以反复执行；浏览统计表用 (project_id, stat_date|stat_hour) 主键。
--
-- 执行顺序（run-seed.cmd 会自动拼接）：
--   assets/files.generated.sql  -> SET @f201_size / @f201_sha / @f201_path ...
--   assets/views.generated.sql  -> project_view_daily / project_view_hourly
--   seed.sql                    -> 本文件（最后，因为要汇总 view_count_total）
--
-- 全部数据都是「本机试运行」用途，密码统一 Coc@2026（见 ops/README-ports.md）
-- =====================================================================

SET NAMES utf8mb4;

-- BCrypt hash of Coc@2026 (bcryptjs, cost 10) —— 由 ops\seed\gen-hash.cmd 生成
SET @pw := '$2a$10$9ZvvZ4soWFNllxvmhLRZcusmntlxyiqx02.rhqcVGK9sxWPQ8D6ki';

-- ---------------------------------------------------------------------
-- ID 规划（刻意避开 user_id 1 —— 后端自测会话已经占用 1 号「自测管理员」）
--   user            101..104
--   developer_profile 101..103
--   category        101..105      tag 101..108
--   project         201..206      project_version / project_file 201..206
--   comment         201..208      im_session 201..202  im_message 201..207
-- ---------------------------------------------------------------------

-- ============ 1. 用户（管理员 + 开发者 + 1 个待审开发者） ============
INSERT INTO `user`
  (user_id, qq_account, password_hash, nickname, avatar_path, type, status, token_version, reward_points, last_login_at, created_at) VALUES
  (101, '3254328344', @pw, '造物集管理员',  NULL, 3, 1, 0,  0, DATE_SUB(NOW(), INTERVAL 2 HOUR),  DATE_SUB(NOW(), INTERVAL 45 DAY)),
  (102, '10001',      @pw, '星野工作室',    NULL, 2, 1, 0,  5, DATE_SUB(NOW(), INTERVAL 5 HOUR),  DATE_SUB(NOW(), INTERVAL 40 DAY)),
  (103, '10002',      @pw, '像素小工坊',    NULL, 2, 1, 0,  0, DATE_SUB(NOW(), INTERVAL 26 HOUR), DATE_SUB(NOW(), INTERVAL 33 DAY)),
  (104, '10003',      @pw, '想投稿的萌新',  NULL, 2, 0, 0,  0, NULL,                                DATE_SUB(NOW(), INTERVAL 4 DAY))
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash), nickname = VALUES(nickname),
  type = VALUES(type), status = VALUES(status);

-- ============ 2. 开发者申请（2 个已通过 + 1 个待审） ============
INSERT INTO developer_profile
  (developer_profile_id, user_id, contact, apply_status, reason, applied_at, reviewed_at, reviewer_id, contribution_value) VALUES
  (101, 102, 'QQ 10001', 1, '独立开发者，做了几个 Canvas 小游戏想投稿。', DATE_SUB(NOW(), INTERVAL 39 DAY), DATE_SUB(NOW(), INTERVAL 38 DAY), 101, 120),
  (102, 103, 'QQ 10002 / pixelfarm@example.com', 1, '主要做 Scratch 和像素风建筑，希望加入。', DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 31 DAY), 101, 65),
  (103, 104, 'QQ 10003', 0, '你好，我很喜欢这个站，想把自己的第一个小游戏发上来试试！', DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
  contact = VALUES(contact), apply_status = VALUES(apply_status), reason = VALUES(reason),
  reviewed_at = VALUES(reviewed_at), reviewer_id = VALUES(reviewer_id), contribution_value = VALUES(contribution_value);

-- ============ 3. 分类 ============
INSERT INTO category (category_id, name, slug, sort_order, status) VALUES
  (101, '游戏',     'game',        1, 1),
  (102, '动画',     'animation',   2, 1),
  (103, '工具',     'tool',        3, 1),
  (104, '互动艺术', 'interactive', 4, 1),
  (105, '教程演示', 'tutorial',    5, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), slug = VALUES(slug), sort_order = VALUES(sort_order), status = VALUES(status);

-- ============ 4. 标签 ============
INSERT INTO tag (tag_id, name) VALUES
  (101, 'Canvas'), (102, '物理弹跳'), (103, '粒子特效'), (104, 'Scratch'),
  (105, '像素风'), (106, '我的世界'), (107, '小游戏'), (108, '新手作品')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============ 5. 演示项目（6 个） ============
-- 201 html 已发布（真实可交互 Canvas 游戏，源码在 .data/projects/201/v1/source/index.html）
-- 202 html 待审（status=1，走人工审核队列）
-- 203 scratch 已发布（audit_mode=auto，免审直发）
-- 204 scratch 已发布（audit_mode=auto）
-- 205 structure 已发布（audit_mode=auto）
-- 206 html 已下架（status=3 + down_reason）
INSERT INTO project
  (project_id, author_id, title, summary, description, project_type, category_id, cover_path, status, audit_mode,
   is_deleted, down_reason, reject_reason, delete_reason,
   like_count, comment_count, favorite_count, view_count_total, published_at, created_at, updated_at) VALUES
  (201, 102, '弹跳星球大冒险',
   '经典打砖块 + 行星光环，鼠标或方向键操控，越往后关卡越硬。',
   '用纯 Canvas 写的单文件小游戏：挡板接球打砖块，球是带着光环的小行星，撞击会爆出粒子。\n\n玩法：\n- 移动鼠标（或 ← → / A D）控制挡板\n- 空格或点击发球\n- P 暂停，球掉光游戏结束，按 Enter 重开\n\n技术点：子步进物理防穿透、按命中位置计算反弹角度（可以打出「弧线球」）、砖块按血量改变亮度。',
   'html', 101, 'covers/cover-01.png', 2, 'manual', 0, NULL, NULL, NULL,
   42, 8, 17, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 27 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (202, 103, '粒子烟花秀',
   '点击画面就放一朵烟花，全屏粒子加色混合，夜里看很舒服。',
   '纯 Canvas 粒子烟花：点击 / 触摸任意位置绽开一朵随机配色的烟花，另外每 70 帧自动放一朵。\n使用 globalCompositeOperation = "lighter" 做辉光叠加，粒子带重力与阻尼。',
   'html', 104, 'covers/cover-02.png', 1, 'manual', 0, NULL, NULL, NULL,
   0, 0, 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR)),
  (203, 102, '小猫走迷宫',
   '用 Scratch 做的迷宫小游戏：键盘控制小猫，收集小鱼干走到终点。',
   'Scratch 作品（.sb3 源码 + 服务端打包成单文件 HTML）。\n第 1 关先做基础移动与碰撞，后续版本会加计时和排行榜。',
   'scratch', 101, 'covers/cover-03.png', 2, 'auto', 0, NULL, NULL, NULL,
   31, 3, 12, 0, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (204, 103, '打地鼠小游戏',
   'Scratch 打地鼠，30 秒内敲到越多越好，越快出现越快。',
   'Scratch 作品：随机位置冒出的地鼠 + 计时 + 分数变量，节奏会随着分数提高而加快。',
   'scratch', 101, 'covers/cover-04.png', 2, 'auto', 0, NULL, NULL, NULL,
   24, 0, 9, 0, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (205, 102, '中世纪小酒馆建筑',
   '我的世界结构文件：一间带二层露台的小酒馆，可直接导入世界。',
   '结构文件（.mcstructure）说明：\n- 尺寸约 21 × 13 × 17\n- 含二楼露台、吧台、酒桶堆、壁炉与灯笼\n- 建议放在村庄边缘或码头旁\n\n注意：本条目为结构类项目，不需要打包，前端直接提供结构文件下载。',
   'structure', 104, 'covers/cover-05.png', 2, 'auto', 0, NULL, NULL, NULL,
   19, 2, 14, 0, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (206, 103, '跳转测试页面（已下架）',
   '一个带站外跳转的测试页面，因为违规已经被管理员下架。',
   '本条目用于验证「下架」链路的演示数据：状态为已下架，带下架原因，作者会收到通知。\n源码仍保留在服务器上供管理员查看。',
   'html', 103, 'covers/cover-06.png', 3, 'manual', 0,
   '页面包含站外跳转链接，违反投稿规范，请整改后重新提交。', NULL, NULL,
   2, 0, 0, 0, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY))
ON DUPLICATE KEY UPDATE
  title = VALUES(title), summary = VALUES(summary), description = VALUES(description),
  project_type = VALUES(project_type), category_id = VALUES(category_id), cover_path = VALUES(cover_path),
  status = VALUES(status), audit_mode = VALUES(audit_mode), down_reason = VALUES(down_reason),
  like_count = VALUES(like_count), comment_count = VALUES(comment_count),
  favorite_count = VALUES(favorite_count), published_at = VALUES(published_at);

-- ============ 6. 项目分类标签关联 ============
INSERT INTO project_tag (project_tag_id, project_id, tag_id) VALUES
  (201, 201, 101), (202, 201, 102), (203, 201, 103), (204, 201, 107),
  (205, 202, 101), (206, 202, 103), (207, 202, 108),
  (208, 203, 104), (209, 203, 107),
  (210, 204, 104), (211, 204, 107),
  (212, 205, 106), (213, 205, 105),
  (214, 206, 108)
ON DUPLICATE KEY UPDATE project_id = VALUES(project_id), tag_id = VALUES(tag_id);

-- ============ 7. 项目版本 ============
INSERT INTO project_version (project_version_id, project_id, version_no, changelog, created_by, created_at) VALUES
  (201, 201, 1, '首个版本：12 关砖块 + 弧线球 + 粒子爆炸', 102, DATE_SUB(NOW(), INTERVAL 26 DAY)),
  (202, 202, 1, '首次投稿，等待审核',                      103, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
  (203, 203, 1, 'v1：基础移动与碰撞',                      102, DATE_SUB(NOW(), INTERVAL 18 DAY)),
  (204, 204, 1, 'v1：计时 + 分数变量',                     103, DATE_SUB(NOW(), INTERVAL 11 DAY)),
  (205, 205, 1, 'v1：结构文件首发',                        102, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (206, 206, 1, 'v1（已下架）',                            103, DATE_SUB(NOW(), INTERVAL 20 DAY))
ON DUPLICATE KEY UPDATE changelog = VALUES(changelog);

-- ============ 8. 项目文件（storage_path 取 gen-demo-files.js 生成的真实路径 / 大小 / sha256） ============
INSERT INTO project_file
  (project_file_id, project_version_id, file_type, file_name, file_size, sha256, storage_path,
   scan_status, scan_detail, build_status, build_path, created_at) VALUES
  (201, 201, 'source', 'index.html', COALESCE(@f201_size, 0), COALESCE(@f201_sha, NULL), COALESCE(@f201_path, NULL),
   1, '静态扫描通过', 0, NULL, DATE_SUB(NOW(), INTERVAL 26 DAY)),
  (202, 202, 'source', 'index.html', COALESCE(@f202_size, 0), COALESCE(@f202_sha, NULL), COALESCE(@f202_path, NULL),
   1, '静态扫描通过', 0, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
  (203, 203, 'source', 'project.sb3', COALESCE(@f203_size, 0), COALESCE(@f203_sha, NULL), COALESCE(@f203_path, NULL),
   1, '静态扫描通过', 2, 'E:/personalProjects/coc-platform/.data/projects/203/v1/build/index.html', DATE_SUB(NOW(), INTERVAL 18 DAY)),
  (204, 204, 'source', 'project.sb3', COALESCE(@f204_size, 0), COALESCE(@f204_sha, NULL), COALESCE(@f204_path, NULL),
   1, '静态扫描通过', 2, 'E:/personalProjects/coc-platform/.data/projects/204/v1/build/index.html', DATE_SUB(NOW(), INTERVAL 11 DAY)),
  (205, 205, 'source', 'tavern.mcstructure', COALESCE(@f205_size, 0), COALESCE(@f205_sha, NULL), COALESCE(@f205_path, NULL),
   1, '结构文件，无需打包', 0, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (206, 206, 'source', 'index.html', COALESCE(@f206_size, 0), COALESCE(@f206_sha, NULL), COALESCE(@f206_path, NULL),
   2, '命中规则：包含站外跳转链接', 0, NULL, DATE_SUB(NOW(), INTERVAL 20 DAY))
ON DUPLICATE KEY UPDATE
  file_name = VALUES(file_name), file_size = VALUES(file_size), sha256 = VALUES(sha256),
  storage_path = VALUES(storage_path), scan_status = VALUES(scan_status), scan_detail = VALUES(scan_detail),
  build_status = VALUES(build_status), build_path = VALUES(build_path);

-- ============ 9. 审核记录 ============
INSERT INTO project_review (project_review_id, project_version_id, admin_id, action, reason, created_at) VALUES
  (201, 201, 101, 'approve', '内容健康、代码自包含，通过。', DATE_SUB(NOW(), INTERVAL 25 DAY)),
  (202, 206, 101, 'down',    '包含站外跳转链接，违反投稿规范。', DATE_SUB(NOW(), INTERVAL 15 DAY))
ON DUPLICATE KEY UPDATE action = VALUES(action), reason = VALUES(reason);

-- ============ 10. 评论（201..207 主楼 + 208 楼中楼 + 209..213 其他项目） ============
INSERT INTO comment (comment_id, project_id, user_id, parent_comment_id, root_comment_id, content, like_count, is_deleted, created_at) VALUES
  (201, 201, 103, NULL, NULL, '球撞到砖块爆粒子的手感真不错！不过我打到第 5 关就手忙脚乱了 😂', 6, 0, DATE_SUB(NOW(), INTERVAL 20 DAY)),
  (202, 201, 101, NULL, NULL, '已通过审核。子步进物理那几行写得很规范，欢迎继续投稿。', 3, 0, DATE_SUB(NOW(), INTERVAL 19 DAY)),
  (203, 201, 103, NULL, NULL, '挡板边缘接球能打出很刁的弧线，这个细节太加分了。', 4, 0, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (204, 201, 101, NULL, NULL, '建议：后面可以加个「最高分」本地存储，重开还能看到纪录。', 2, 0, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (205, 201, 103, NULL, NULL, '手机上也能玩，触摸拖动挡板挺顺的。', 1, 0, DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (206, 201, 101, NULL, NULL, '第七关的砖块血量厚，慢慢磨吧 😄', 0, 0, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (207, 201, 103, NULL, NULL, '蹲一个后续关卡更新！', 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (208, 201, 101, 201, 201, '哈哈正常，先练手感，第 5 关开始才是真正的地狱。', 2, 0, DATE_SUB(NOW(), INTERVAL 18 DAY)),
  (209, 203, 103, NULL, NULL, '第 3 关的转角太窄了，小猫容易卡在墙角。', 3, 0, DATE_SUB(NOW(), INTERVAL 14 DAY)),
  (210, 203, 101, NULL, NULL, '自动审核通过，代码扫描无异常。', 1, 0, DATE_SUB(NOW(), INTERVAL 13 DAY)),
  (211, 203, 102, NULL, NULL, '下个版本把转角改宽一点，多谢反馈！', 2, 0, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (212, 205, 103, NULL, NULL, '结构导入后和原版地形对不齐，是我放置高度的问题吗？', 2, 0, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (213, 205, 102, NULL, NULL, '建议先把地面铲平到 y=64 再放，酒馆的底座是按 64 做的。', 4, 0, DATE_SUB(NOW(), INTERVAL 7 DAY))
ON DUPLICATE KEY UPDATE content = VALUES(content), like_count = VALUES(like_count);

-- ============ 11. 评论点赞 ============
INSERT INTO comment_like (comment_like_id, comment_id, user_id) VALUES
  (201, 201, 101), (202, 201, 102), (203, 203, 102),
  (204, 209, 102), (205, 213, 103)
ON DUPLICATE KEY UPDATE comment_id = VALUES(comment_id), user_id = VALUES(user_id);

-- ============ 12. 项目点赞 / 收藏（计数与明细保持同量级，明细不求穷尽） ============
INSERT INTO project_like (project_like_id, project_id, user_id, created_at) VALUES
  (201, 201, 103, DATE_SUB(NOW(), INTERVAL 20 DAY)),
  (202, 201, 101, DATE_SUB(NOW(), INTERVAL 19 DAY)),
  (203, 203, 103, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  (204, 203, 101, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (205, 204, 102, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (206, 205, 103, DATE_SUB(NOW(), INTERVAL 6 DAY))
ON DUPLICATE KEY UPDATE project_id = VALUES(project_id), user_id = VALUES(user_id);

INSERT INTO project_favorite (project_favorite_id, project_id, user_id, created_at) VALUES
  (201, 201, 103, DATE_SUB(NOW(), INTERVAL 17 DAY)),
  (202, 201, 101, DATE_SUB(NOW(), INTERVAL 14 DAY)),
  (203, 205, 103, DATE_SUB(NOW(), INTERVAL 7 DAY))
ON DUPLICATE KEY UPDATE project_id = VALUES(project_id), user_id = VALUES(user_id);

-- ============ 13. IM：1 个项目群 + 1 个单聊（方便前端联调两个入口） ============
INSERT INTO im_session
  (im_session_id, kind, project_id, user_a_id, user_b_id, title, last_message_at, last_message_preview, created_at) VALUES
  (201, 2, 201, 102, NULL, '弹跳星球大冒险 · 项目群', DATE_SUB(NOW(), INTERVAL 40 MINUTE), '那我等你更新，先把最高分贴上', DATE_SUB(NOW(), INTERVAL 24 DAY)),
  (202, 1, NULL, 102, 103, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR), '结构文件的尺寸我写在简介里了', DATE_SUB(NOW(), INTERVAL 9 DAY))
ON DUPLICATE KEY UPDATE title = VALUES(title), last_message_at = VALUES(last_message_at), last_message_preview = VALUES(last_message_preview);

INSERT INTO im_message (im_message_id, session_id, sender_id, content, content_type, client_msg_id, is_deleted, created_at) VALUES
  (201, 201, 102, '大家好，我把「弹跳星球大冒险」提交上来了，有问题随时说。', 1, 'seed-grp-1', 0, DATE_SUB(NOW(), INTERVAL 24 DAY)),
  (202, 201, 103, '玩了半小时，第 6 关太狠了。挡板是不是越到后面越窄？', 1, 'seed-grp-2', 0, DATE_SUB(NOW(), INTERVAL 23 DAY)),
  (203, 201, 102, '不是挡板变窄，是球速会随关卡提升，视觉上显得窄 🙂', 1, 'seed-grp-3', 0, DATE_SUB(NOW(), INTERVAL 23 DAY)),
  (204, 201, 101, '已审核通过，欢迎各位继续在本群反馈。', 1, 'seed-grp-4', 0, DATE_SUB(NOW(), INTERVAL 19 DAY)),
  (205, 201, 103, '那我等你更新，先把最高分贴上', 1, 'seed-grp-5', 0, DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
  (206, 202, 103, '你那间中世纪酒馆，结构占地多少格？', 1, 'seed-dm-1', 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
  (207, 202, 102, '结构文件的尺寸我写在简介里了', 1, 'seed-dm-2', 0, DATE_SUB(NOW(), INTERVAL 3 HOUR))
ON DUPLICATE KEY UPDATE content = VALUES(content), is_deleted = VALUES(is_deleted);

INSERT INTO im_session_member (im_session_member_id, session_id, user_id, last_read_message_id, joined_at) VALUES
  (201, 201, 102, 205, DATE_SUB(NOW(), INTERVAL 24 DAY)),
  (202, 201, 103, 204, DATE_SUB(NOW(), INTERVAL 24 DAY)),
  (203, 201, 101, 205, DATE_SUB(NOW(), INTERVAL 19 DAY)),
  (204, 202, 102, 207, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (205, 202, 103, 206, DATE_SUB(NOW(), INTERVAL 9 DAY))
ON DUPLICATE KEY UPDATE last_read_message_id = VALUES(last_read_message_id);

-- ============ 14. 通知 ============
INSERT INTO notification (notification_id, user_id, type, title, content, link, is_read, created_at) VALUES
  (201, 102, 'review_result',   '项目已通过审核', '《弹跳星球大冒险》已通过审核并发布。', '/projects/201', 1, DATE_SUB(NOW(), INTERVAL 25 DAY)),
  (202, 103, 'review_result',   '项目已下架',     '《跳转测试页面（已下架）》因包含站外跳转链接被下架。', '/projects/206', 0, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  (203, 102, 'system',          '欢迎回来',       '本机试运行环境已就绪，演示数据已导入。', NULL, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR))
ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), is_read = VALUES(is_read);

-- ============ 15. 举报（1 条待处理，给管理后台留活儿） ============
INSERT INTO report
  (report_id, reporter_id, target_type, target_id, reason_type, detail, status, handler_id, handle_result, handled_at, reporter_read, reward_points, created_at) VALUES
  (201, 103, 'project', 206, '站外跳转', '该页面点开就跳到别的网站，感觉不太对。', 0, NULL, NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 16 DAY)),
  (202, 102, 'comment', 201, '无关内容', '测试用举报（已处理，用于验证正反馈积分）。', 1, 101, '经核实不成立，感谢反馈。', DATE_SUB(NOW(), INTERVAL 10 DAY), 1, 5, DATE_SUB(NOW(), INTERVAL 11 DAY))
ON DUPLICATE KEY UPDATE status = VALUES(status), handle_result = VALUES(handle_result);

-- ============ 16. 关注 ============
INSERT INTO follow (follow_id, follower_id, followee_id, created_at) VALUES
  (201, 103, 102, DATE_SUB(NOW(), INTERVAL 22 DAY)),
  (202, 101, 102, DATE_SUB(NOW(), INTERVAL 18 DAY))
ON DUPLICATE KEY UPDATE follower_id = VALUES(follower_id), followee_id = VALUES(followee_id);

-- ============ 17. 找回密码申请（待处理） ============
INSERT INTO password_reset_apply (apply_id, qq_account, contact, note, status, applied_at, handled_by, handled_at) VALUES
  (201, '10002', 'QQ 10002', '换了电脑，之前的密码忘了，求重置。', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL)
ON DUPLICATE KEY UPDATE status = VALUES(status), note = VALUES(note);

-- ============ 18. 协议版本 ============
INSERT INTO agreement_version (agreement_version_id, version_no, title, content_path, is_current, published_at) VALUES
  (201, 'v1.0', '造物集用户协议', 'agreements/v1.0.md', 1, DATE_SUB(NOW(), INTERVAL 60 DAY))
ON DUPLICATE KEY UPDATE title = VALUES(title), is_current = VALUES(is_current);

-- ============ 19. 打包任务（Scratch 项目的历史记录） ============
INSERT INTO build_task (build_task_id, project_file_id, status, log_path, output_path, message, created_at, finished_at) VALUES
  (201, 203, 2, 'E:/personalProjects/coc-platform/.data/projects/203/v1/build/pack.log',
   'E:/personalProjects/coc-platform/.data/projects/203/v1/build/index.html', '打包成功', DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY)),
  (202, 204, 2, 'E:/personalProjects/coc-platform/.data/projects/204/v1/build/pack.log',
   'E:/personalProjects/coc-platform/.data/projects/204/v1/build/index.html', '打包成功', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY))
ON DUPLICATE KEY UPDATE status = VALUES(status), output_path = VALUES(output_path), message = VALUES(message);

-- ============ 20/21. 汇总与校验 ============
-- （结尾的 view_count_total 汇总 + 幂等校验在 seed-finalize.sql，
--   因为要在 views.generated.sql 灌完浏览数据之后再跑）
