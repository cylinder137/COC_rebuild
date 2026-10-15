-- =====================================================================
-- 造物集（coc-platform）MVP 初始化脚本  V1
-- 依据：contract/api-mvp.md §2（唯一口径）；细节参考设计 03-db.md
-- 约定：utf8mb4 / InnoDB / 主键命名 <实体>_id / 无种子数据（种子由 ops 子会话负责）
-- 零继承：全新库 coc_platform，不复用旧 zaowuji 任何表
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. user 用户（只有 开发者 type=2 / 管理员 type=3，匿名访客不留任何记录）
-- ---------------------------------------------------------------------
CREATE TABLE `user` (
  `user_id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `qq_account`           VARCHAR(20)  NOT NULL                COMMENT '登录账号（QQ号，全站唯一）',
  `password_hash`        VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码哈希（不可逆）',
  `nickname`             VARCHAR(50)  NOT NULL                COMMENT '昵称（全站展示）',
  `avatar_path`          VARCHAR(500) DEFAULT NULL            COMMENT '头像相对路径（avatars/...）',
  `type`                 TINYINT      NOT NULL DEFAULT 2      COMMENT '账号类型：2开发者 3管理员',
  `status`               TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0待审 1正常 2禁用 3已注销',
  `token_version`        INT          NOT NULL DEFAULT 0      COMMENT '令牌版本（改密/重置/注销时+1，旧 JWT 立即失效）',
  `must_change_password` TINYINT      NOT NULL DEFAULT 0      COMMENT '是否强制改密：1是（管理员重置后的一次性临时密码）',
  `temp_password_expires_at` DATETIME DEFAULT NULL            COMMENT '临时密码有效期（重置后 24 小时）',
  `reward_points`        INT          NOT NULL DEFAULT 0      COMMENT '举报正反馈积分（诉求#11）',
  `last_login_at`        DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
  `cancelled_at`         DATETIME     DEFAULT NULL            COMMENT '注销时间',
  `cancel_reason`        VARCHAR(255) DEFAULT NULL            COMMENT '注销原因',
  `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_qq_account` (`qq_account`),
  KEY `idx_user_type_status` (`type`, `status`),
  KEY `idx_user_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表（开发者/管理员，访客不入库）';

-- ---------------------------------------------------------------------
-- 2. developer_profile 开发者申请
-- ---------------------------------------------------------------------
CREATE TABLE `developer_profile` (
  `developer_profile_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '开发者申请ID',
  `user_id`              BIGINT       NOT NULL                COMMENT '申请人（user）',
  `contact`              VARCHAR(100) DEFAULT NULL            COMMENT '联系方式',
  `apply_status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '申请状态：0待审 1通过 2驳回',
  `reason`               VARCHAR(255) DEFAULT NULL            COMMENT '申请说明 / 驳回原因',
  `applied_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `reviewed_at`          DATETIME     DEFAULT NULL            COMMENT '审核时间',
  `reviewer_id`          BIGINT       DEFAULT NULL            COMMENT '审核管理员（user_id）',
  `contribution_value`   INT          NOT NULL DEFAULT 0      COMMENT '贡献值',
  PRIMARY KEY (`developer_profile_id`),
  UNIQUE KEY `uk_dev_profile_user` (`user_id`),
  KEY `idx_dev_profile_status` (`apply_status`, `applied_at`),
  CONSTRAINT `fk_dev_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dev_profile_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `user` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='开发者申请/资料表';

-- ---------------------------------------------------------------------
-- 3. category 分类
-- ---------------------------------------------------------------------
CREATE TABLE `category` (
  `category_id` BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name`        VARCHAR(50) NOT NULL                COMMENT '分类名',
  `slug`        VARCHAR(50) NOT NULL                COMMENT '英文标识（唯一）',
  `sort_order`  INT         NOT NULL DEFAULT 0      COMMENT '排序（小在前）',
  `status`      TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：0停用 1启用',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_category_slug` (`slug`),
  KEY `idx_category_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目分类';

-- ---------------------------------------------------------------------
-- 4. tag 标签
-- ---------------------------------------------------------------------
CREATE TABLE `tag` (
  `tag_id`     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name`       VARCHAR(30) NOT NULL                COMMENT '标签名（唯一）',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签';

-- ---------------------------------------------------------------------
-- 6. project 项目
-- ---------------------------------------------------------------------
CREATE TABLE `project` (
  `project_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `author_id`        BIGINT       NOT NULL                COMMENT '作者（user.user_id）',
  `title`            VARCHAR(100) NOT NULL                COMMENT '项目标题',
  `summary`          VARCHAR(255) DEFAULT NULL            COMMENT '一句话简介',
  `description`      MEDIUMTEXT                           COMMENT '详细介绍',
  `project_type`     ENUM('html','scratch','structure') NOT NULL COMMENT '项目类型：html / scratch / structure',
  `category_id`      BIGINT       DEFAULT NULL            COMMENT '分类',
  `cover_path`       VARCHAR(500) DEFAULT NULL            COMMENT '封面相对路径',
  `status`           TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0草稿 1待审 2已发布 3已下架 4已拒绝',
  `audit_mode`       ENUM('manual','auto') NOT NULL DEFAULT 'manual' COMMENT '审核模式：manual需人工审核(html) / auto免审直发(scratch、structure)',
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '是否删除：0否 1是（删除用本字段表达）',
  `down_reason`      VARCHAR(255) DEFAULT NULL            COMMENT '下架原因',
  `reject_reason`    VARCHAR(255) DEFAULT NULL            COMMENT '拒绝原因',
  `delete_reason`    VARCHAR(255) DEFAULT NULL            COMMENT '删除原因（管理员）',
  `like_count`       INT          NOT NULL DEFAULT 0      COMMENT '点赞数',
  `comment_count`    INT          NOT NULL DEFAULT 0      COMMENT '评论数（含回复）',
  `favorite_count`   INT          NOT NULL DEFAULT 0      COMMENT '收藏数',
  `view_count_total` BIGINT       NOT NULL DEFAULT 0      COMMENT '累计浏览量',
  `published_at`     DATETIME     DEFAULT NULL            COMMENT '首次发布时间',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`project_id`),
  KEY `idx_project_author` (`author_id`, `is_deleted`, `status`, `created_at`),
  KEY `idx_project_feed` (`status`, `is_deleted`, `published_at`),
  KEY `idx_project_type` (`project_type`, `status`, `is_deleted`, `published_at`),
  KEY `idx_project_category` (`category_id`, `status`, `is_deleted`, `published_at`),
  KEY `idx_project_hot` (`status`, `is_deleted`, `view_count_total`),
  KEY `idx_project_review_queue` (`status`, `created_at`),
  CONSTRAINT `fk_project_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`) ON DELETE SET NULL,
  CONSTRAINT `chk_project_status` CHECK (`status` IN (0,1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目主表';

-- ---------------------------------------------------------------------
-- 5. project_tag 项目-标签关联（放在 project 之后，避免外键前向引用）
-- ---------------------------------------------------------------------
CREATE TABLE `project_tag` (
  `project_tag_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目标签关联ID',
  `project_id`     BIGINT NOT NULL COMMENT '项目',
  `tag_id`         BIGINT NOT NULL COMMENT '标签',
  PRIMARY KEY (`project_tag_id`),
  UNIQUE KEY `uk_project_tag` (`project_id`, `tag_id`),
  KEY `idx_project_tag_tag` (`tag_id`),
  CONSTRAINT `fk_pt_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pt_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目-标签关联';

-- ---------------------------------------------------------------------
-- 7. project_version 项目版本
-- ---------------------------------------------------------------------
CREATE TABLE `project_version` (
  `project_version_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `project_id`         BIGINT       NOT NULL                COMMENT '所属项目',
  `version_no`         INT          NOT NULL                COMMENT '版本号（项目内从 1 递增）',
  `changelog`          VARCHAR(500) DEFAULT NULL            COMMENT '版本说明',
  `created_by`         BIGINT       NOT NULL                COMMENT '创建人（user_id）',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`project_version_id`),
  UNIQUE KEY `uk_pv_project_version` (`project_id`, `version_no`),
  CONSTRAINT `fk_pv_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pv_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目版本';

-- ---------------------------------------------------------------------
-- 8. project_file 项目文件（源码/构建产物/封面）
-- ---------------------------------------------------------------------
CREATE TABLE `project_file` (
  `project_file_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `project_version_id` BIGINT       NOT NULL                COMMENT '所属版本',
  `file_type`          ENUM('source','build','cover') NOT NULL DEFAULT 'source' COMMENT '文件类型：source源码 build构建产物 cover封面',
  `file_name`          VARCHAR(255) NOT NULL                COMMENT '原始文件名',
  `file_size`          BIGINT       NOT NULL DEFAULT 0      COMMENT '字节数',
  `sha256`             CHAR(64)     DEFAULT NULL            COMMENT 'sha256（用于秒传/完整性校验）',
  `storage_path`       VARCHAR(512) DEFAULT NULL            COMMENT '服务器存储绝对路径',
  `scan_status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '扫描状态：0未扫 1通过 2可疑',
  `scan_detail`        VARCHAR(255) DEFAULT NULL            COMMENT '扫描说明',
  `build_status`       TINYINT      NOT NULL DEFAULT 0      COMMENT '打包状态：0无需 1待打包 2成功 3失败',
  `build_path`         VARCHAR(512) DEFAULT NULL            COMMENT '构建产物绝对路径',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`project_file_id`),
  KEY `idx_pf_version` (`project_version_id`, `file_type`),
  KEY `idx_pf_sha256` (`sha256`),
  CONSTRAINT `fk_pf_version` FOREIGN KEY (`project_version_id`) REFERENCES `project_version` (`project_version_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目文件';

-- ---------------------------------------------------------------------
-- 9. project_review 审核记录
-- ---------------------------------------------------------------------
CREATE TABLE `project_review` (
  `project_review_id`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
  `project_version_id` BIGINT       NOT NULL                COMMENT '被审核版本',
  `admin_id`           BIGINT       NOT NULL                COMMENT '审核管理员',
  `action`             ENUM('approve','reject','down') NOT NULL COMMENT '动作：通过/驳回/下架',
  `reason`             VARCHAR(255) DEFAULT NULL            COMMENT '原因',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`project_review_id`),
  KEY `idx_review_version` (`project_version_id`),
  CONSTRAINT `fk_review_version` FOREIGN KEY (`project_version_id`) REFERENCES `project_version` (`project_version_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_review_admin` FOREIGN KEY (`admin_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目审核记录';

-- ---------------------------------------------------------------------
-- 10. comment 评论（楼中楼一层）
-- ---------------------------------------------------------------------
CREATE TABLE `comment` (
  `comment_id`        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `project_id`        BIGINT   NOT NULL                COMMENT '所属项目',
  `user_id`           BIGINT   NOT NULL                COMMENT '评论人',
  `parent_comment_id` BIGINT   DEFAULT NULL            COMMENT '父评论（回复目标）',
  `root_comment_id`   BIGINT   DEFAULT NULL            COMMENT '根评论（楼中楼固定一层）',
  `content`           TEXT     NOT NULL                COMMENT '评论内容',
  `like_count`        INT      NOT NULL DEFAULT 0      COMMENT '点赞数',
  `is_deleted`        TINYINT  NOT NULL DEFAULT 0      COMMENT '是否删除：0否 1是',
  `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`comment_id`),
  KEY `idx_comment_project` (`project_id`, `is_deleted`, `created_at`),
  KEY `idx_comment_root` (`root_comment_id`, `created_at`),
  KEY `idx_comment_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_comment_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目评论';

-- ---------------------------------------------------------------------
-- 11. comment_like 评论点赞
-- ---------------------------------------------------------------------
CREATE TABLE `comment_like` (
  `comment_like_id` BIGINT   NOT NULL AUTO_INCREMENT COMMENT '评论点赞ID',
  `comment_id`      BIGINT   NOT NULL COMMENT '评论',
  `user_id`         BIGINT   NOT NULL COMMENT '用户',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`comment_like_id`),
  UNIQUE KEY `uk_comment_like` (`comment_id`, `user_id`),
  KEY `idx_comment_like_user` (`user_id`),
  CONSTRAINT `fk_cl_comment` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`comment_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cl_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论点赞';

-- ---------------------------------------------------------------------
-- 12. project_like 项目点赞
-- ---------------------------------------------------------------------
CREATE TABLE `project_like` (
  `project_like_id` BIGINT   NOT NULL AUTO_INCREMENT COMMENT '项目点赞ID',
  `project_id`      BIGINT   NOT NULL COMMENT '项目',
  `user_id`         BIGINT   NOT NULL COMMENT '用户',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`project_like_id`),
  UNIQUE KEY `uk_project_like` (`project_id`, `user_id`),
  KEY `idx_project_like_user` (`user_id`),
  CONSTRAINT `fk_pl_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pl_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目点赞';

-- ---------------------------------------------------------------------
-- 13. project_favorite 项目收藏
-- ---------------------------------------------------------------------
CREATE TABLE `project_favorite` (
  `project_favorite_id` BIGINT   NOT NULL AUTO_INCREMENT COMMENT '项目收藏ID',
  `project_id`          BIGINT   NOT NULL COMMENT '项目',
  `user_id`             BIGINT   NOT NULL COMMENT '用户',
  `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`project_favorite_id`),
  UNIQUE KEY `uk_project_favorite` (`project_id`, `user_id`),
  KEY `idx_project_favorite_user` (`user_id`),
  CONSTRAINT `fk_pf2_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pf2_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目收藏';

-- ---------------------------------------------------------------------
-- 14. project_view_daily 按天浏览计数
-- ---------------------------------------------------------------------
CREATE TABLE `project_view_daily` (
  `project_id` BIGINT NOT NULL COMMENT '项目',
  `stat_date`  DATE   NOT NULL COMMENT '统计日期',
  `view_count` INT    NOT NULL DEFAULT 0 COMMENT '当日浏览量',
  PRIMARY KEY (`project_id`, `stat_date`),
  CONSTRAINT `fk_pvd_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目按天浏览计数（无用户维度）';

-- ---------------------------------------------------------------------
-- 15. project_view_hourly 按小时浏览计数（range=2 天折线用）
-- ---------------------------------------------------------------------
CREATE TABLE `project_view_hourly` (
  `project_id` BIGINT   NOT NULL COMMENT '项目',
  `stat_hour`  DATETIME NOT NULL COMMENT '统计小时（整点）',
  `view_count` INT      NOT NULL DEFAULT 0 COMMENT '该小时浏览量',
  PRIMARY KEY (`project_id`, `stat_hour`),
  CONSTRAINT `fk_pvh_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目按小时浏览计数';

-- ---------------------------------------------------------------------
-- 16. report 举报
-- ---------------------------------------------------------------------
CREATE TABLE `report` (
  `report_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '举报ID',
  `reporter_id`    BIGINT       NOT NULL                COMMENT '举报人',
  `target_type`    ENUM('project','comment','user') NOT NULL COMMENT '举报对象类型',
  `target_id`      BIGINT       NOT NULL                COMMENT '举报对象ID',
  `reason_type`    VARCHAR(50)  NOT NULL                COMMENT '举报原因分类',
  `detail`         VARCHAR(500) DEFAULT NULL            COMMENT '补充说明',
  `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0待处理 1已处理成立 2已驳回',
  `handler_id`     BIGINT       DEFAULT NULL            COMMENT '处理管理员',
  `handle_result`  VARCHAR(500) DEFAULT NULL            COMMENT '处理结果（通知举报人）',
  `handled_at`     DATETIME     DEFAULT NULL            COMMENT '处理时间',
  `reporter_read`  TINYINT      NOT NULL DEFAULT 0      COMMENT '举报人是否已读处理结果',
  `reward_points`  INT          NOT NULL DEFAULT 0      COMMENT '本次举报奖励积分',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  PRIMARY KEY (`report_id`),
  KEY `idx_report_reporter` (`reporter_id`, `created_at`),
  KEY `idx_report_status` (`status`, `created_at`),
  KEY `idx_report_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_handler` FOREIGN KEY (`handler_id`) REFERENCES `user` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报';

-- ---------------------------------------------------------------------
-- 17. follow 关注关系
-- ---------------------------------------------------------------------
CREATE TABLE `follow` (
  `follow_id`   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关注ID',
  `follower_id` BIGINT   NOT NULL COMMENT '关注者',
  `followee_id` BIGINT   NOT NULL COMMENT '被关注者',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`follow_id`),
  UNIQUE KEY `uk_follow` (`follower_id`, `followee_id`),
  KEY `idx_follow_followee` (`followee_id`),
  CONSTRAINT `fk_follow_follower` FOREIGN KEY (`follower_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_follow_followee` FOREIGN KEY (`followee_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系';

-- ---------------------------------------------------------------------
-- 18. im_session 会话（1 单聊 / 2 项目群）
-- ---------------------------------------------------------------------
CREATE TABLE `im_session` (
  `im_session_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `kind`                 TINYINT      NOT NULL COMMENT '会话类型：1单聊 2项目群',
  `project_id`           BIGINT       DEFAULT NULL COMMENT '项目群对应的项目',
  `user_a_id`            BIGINT       NOT NULL COMMENT '单聊用户A（项目群=创建人）',
  `user_b_id`            BIGINT       DEFAULT NULL COMMENT '单聊用户B（项目群为空）',
  `title`                VARCHAR(100) DEFAULT NULL COMMENT '会话标题/项目群名',
  `last_message_at`      DATETIME     DEFAULT NULL COMMENT '最后消息时间',
  `last_message_preview` VARCHAR(200) DEFAULT NULL COMMENT '最后消息摘要',
  `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`im_session_id`),
  KEY `idx_im_session_kind` (`kind`, `last_message_at`),
  KEY `idx_im_session_project` (`project_id`),
  KEY `idx_im_session_a` (`user_a_id`),
  KEY `idx_im_session_b` (`user_b_id`),
  CONSTRAINT `fk_im_session_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM 会话';

-- ---------------------------------------------------------------------
-- 19. im_message 消息
-- ---------------------------------------------------------------------
CREATE TABLE `im_message` (
  `im_message_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID（游标分页用）',
  `session_id`    BIGINT       NOT NULL COMMENT '会话',
  `sender_id`     BIGINT       NOT NULL COMMENT '发送人',
  `content`       TEXT         NOT NULL COMMENT '内容',
  `content_type`  TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1文本 2图片 3文件',
  `client_msg_id` VARCHAR(64)  DEFAULT NULL COMMENT '客户端消息ID（幂等/去重）',
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`im_message_id`),
  KEY `idx_im_message_session` (`session_id`, `im_message_id`),
  KEY `idx_im_message_client` (`client_msg_id`),
  CONSTRAINT `fk_im_message_session` FOREIGN KEY (`session_id`) REFERENCES `im_session` (`im_session_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_im_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM 消息';

-- ---------------------------------------------------------------------
-- 20. im_session_member 会话成员
-- ---------------------------------------------------------------------
CREATE TABLE `im_session_member` (
  `im_session_member_id` BIGINT   NOT NULL AUTO_INCREMENT COMMENT '会话成员ID',
  `session_id`           BIGINT   NOT NULL COMMENT '会话',
  `user_id`              BIGINT   NOT NULL COMMENT '成员',
  `last_read_message_id` BIGINT   NOT NULL DEFAULT 0 COMMENT '已读到的消息ID',
  `joined_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`im_session_member_id`),
  UNIQUE KEY `uk_im_member` (`session_id`, `user_id`),
  KEY `idx_im_member_user` (`user_id`),
  CONSTRAINT `fk_im_member_session` FOREIGN KEY (`session_id`) REFERENCES `im_session` (`im_session_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_im_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM 会话成员';

-- ---------------------------------------------------------------------
-- 21. notification 通知
-- ---------------------------------------------------------------------
CREATE TABLE `notification` (
  `notification_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id`         BIGINT       NOT NULL COMMENT '接收人',
  `type`            VARCHAR(30)  NOT NULL COMMENT '通知类型（report_handled / review_result / system ...）',
  `title`           VARCHAR(100) NOT NULL COMMENT '标题',
  `content`         VARCHAR(500) DEFAULT NULL COMMENT '内容',
  `link`            VARCHAR(255) DEFAULT NULL COMMENT '跳转链接',
  `is_read`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`notification_id`),
  KEY `idx_notification_user` (`user_id`, `is_read`, `created_at`),
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';

-- ---------------------------------------------------------------------
-- 22. operation_log 操作审计
-- ---------------------------------------------------------------------
CREATE TABLE `operation_log` (
  `log_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `admin_id`    BIGINT       DEFAULT NULL COMMENT '操作人（管理员或登录下载者）',
  `action`      VARCHAR(50)  NOT NULL COMMENT '动作标识',
  `target_type` VARCHAR(30)  DEFAULT NULL COMMENT '对象类型',
  `target_id`   BIGINT       DEFAULT NULL COMMENT '对象ID',
  `detail`      VARCHAR(1000) DEFAULT NULL COMMENT '详情',
  `ip`          VARCHAR(45)  DEFAULT NULL COMMENT '来源IP',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_log_admin` (`admin_id`, `created_at`),
  KEY `idx_log_action` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作审计日志';

-- ---------------------------------------------------------------------
-- 23. upload_session 分片上传会话
-- ---------------------------------------------------------------------
CREATE TABLE `upload_session` (
  `upload_session_id`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '上传会话ID',
  `user_id`            BIGINT       NOT NULL COMMENT '上传人',
  `project_id`         BIGINT       DEFAULT NULL COMMENT '关联项目（可空，草稿上传时提供）',
  `file_name`          VARCHAR(255) NOT NULL COMMENT '文件名',
  `file_size`          BIGINT       NOT NULL DEFAULT 0 COMMENT '文件总字节数',
  `sha256`             CHAR(64)     DEFAULT NULL COMMENT '文件 sha256（秒传/完整性校验）',
  `chunk_size`         INT          NOT NULL DEFAULT 8388608 COMMENT '分片大小',
  `total_chunks`       INT          NOT NULL DEFAULT 0 COMMENT '分片总数',
  `uploaded_chunks`    JSON         DEFAULT NULL COMMENT '已上传分片号数组（支持乱序/续传）',
  `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0进行中 1已完成 2已取消',
  `storage_path`       VARCHAR(512) DEFAULT NULL COMMENT '暂存目录/最终落盘路径',
  `instant_ref_project_file_id` BIGINT DEFAULT NULL COMMENT '秒传命中的已有文件ID',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`upload_session_id`),
  KEY `idx_upload_user` (`user_id`, `created_at`),
  KEY `idx_upload_sha256` (`sha256`, `file_size`),
  CONSTRAINT `fk_upload_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分片上传会话';

-- ---------------------------------------------------------------------
-- 24. build_task 打包任务
-- ---------------------------------------------------------------------
CREATE TABLE `build_task` (
  `build_task_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '打包任务ID',
  `project_file_id` BIGINT       NOT NULL COMMENT '源文件',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1进行中 2成功 3失败',
  `log_path`        VARCHAR(512) DEFAULT NULL COMMENT '日志路径',
  `output_path`     VARCHAR(512) DEFAULT NULL COMMENT '产物路径',
  `message`         VARCHAR(500) DEFAULT NULL COMMENT '备注（如打包服务不可用）',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finished_at`     DATETIME     DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`build_task_id`),
  KEY `idx_build_task_file` (`project_file_id`),
  KEY `idx_build_task_status` (`status`, `created_at`),
  CONSTRAINT `fk_build_task_file` FOREIGN KEY (`project_file_id`) REFERENCES `project_file` (`project_file_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打包任务';

-- ---------------------------------------------------------------------
-- 25. password_reset_apply 找回密码申请（联系管理员）
-- ---------------------------------------------------------------------
CREATE TABLE `password_reset_apply` (
  `apply_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `qq_account`    VARCHAR(20)  NOT NULL COMMENT '申请找回的账号（QQ号）',
  `contact`       VARCHAR(100) DEFAULT NULL COMMENT '联系方式',
  `note`          VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1已办结',
  `temp_password` VARCHAR(50)  DEFAULT NULL COMMENT '管理员生成的一次性临时密码（仅管理员可见）',
  `applied_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `handled_by`    BIGINT       DEFAULT NULL COMMENT '处理管理员',
  `handled_at`    DATETIME     DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`apply_id`),
  KEY `idx_pra_status` (`status`, `applied_at`),
  KEY `idx_pra_qq` (`qq_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='找回密码申请';

-- ---------------------------------------------------------------------
-- 26. agreement_version 协议版本
-- ---------------------------------------------------------------------
CREATE TABLE `agreement_version` (
  `agreement_version_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '协议版本ID',
  `version_no`           VARCHAR(20)  NOT NULL COMMENT '版本号（如 v1.0）',
  `title`                VARCHAR(100) NOT NULL COMMENT '协议标题',
  `content_path`         VARCHAR(500) DEFAULT NULL COMMENT '协议内容路径（相对 .data）',
  `is_current`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否当前版本',
  `published_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`agreement_version_id`),
  UNIQUE KEY `uk_agreement_version_no` (`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户协议版本';
