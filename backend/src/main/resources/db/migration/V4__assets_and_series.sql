-- =====================================================================
-- V4 · 五期：项目系列 / 素材系列 / 素材库
--   1) 移除 mcstructure（structure）板块：仅停止前端入口与新建，
--      project_type 枚举保留 'structure'，历史项目数据不丢、详情页仍可访问。
--   2) 新增「系列」：项目系列只装项目，素材系列只装素材，二者独立。
--   3) 新增「素材」：替代原结构文件板块，支持图片 / GIF / 视频 / 音频 / JSON，
--      直链形如 /media/{asset_id}/{file_name}（nginx 直出存储目录）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. project_series 项目系列
-- ---------------------------------------------------------------------
CREATE TABLE `project_series` (
  `series_id`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '系列ID',
  `user_id`    BIGINT       NOT NULL                COMMENT '创建者（user.user_id）',
  `name`       VARCHAR(80)  NOT NULL                COMMENT '系列名称',
  `intro`      VARCHAR(255) DEFAULT NULL            COMMENT '系列简介',
  `is_public`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否公开：0仅自己可见 1公开',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`series_id`),
  UNIQUE KEY `uk_ps_user_name` (`user_id`, `name`),
  KEY `idx_ps_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_ps_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目系列（一个系列含多个项目）';

-- ---------------------------------------------------------------------
-- 2. asset_series 素材系列
-- ---------------------------------------------------------------------
CREATE TABLE `asset_series` (
  `series_id`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '系列ID',
  `user_id`    BIGINT       NOT NULL                COMMENT '创建者（user.user_id）',
  `name`       VARCHAR(80)  NOT NULL                COMMENT '系列名称',
  `intro`      VARCHAR(255) DEFAULT NULL            COMMENT '系列简介',
  `is_public`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否公开：0仅自己可见 1公开',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`series_id`),
  UNIQUE KEY `uk_as_user_name` (`user_id`, `name`),
  KEY `idx_as_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_as_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='素材系列（一个系列含多个素材）';

-- ---------------------------------------------------------------------
-- 3. project.series_id 归属项目系列（可空；删系列时置空，项目不会跟着没）
-- ---------------------------------------------------------------------
ALTER TABLE `project`
  ADD COLUMN `series_id` BIGINT DEFAULT NULL COMMENT '所属项目系列（可空）' AFTER `category_id`,
  ADD KEY `idx_project_series` (`series_id`, `status`, `is_deleted`),
  ADD CONSTRAINT `fk_project_series` FOREIGN KEY (`series_id`) REFERENCES `project_series` (`series_id`) ON DELETE SET NULL;

-- ---------------------------------------------------------------------
-- 4. asset 素材
--    storage_path 形如 assets/{asset_id}/{file_name}（相对 coc.storage.root）
--    file_name 保留原名以便直链可读：/media/{asset_id}/{file_name}
-- ---------------------------------------------------------------------
CREATE TABLE `asset` (
  `asset_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '素材ID',
  `user_id`      BIGINT       NOT NULL                COMMENT '上传者（user.user_id）',
  `series_id`    BIGINT       DEFAULT NULL            COMMENT '所属素材系列（可空）',
  `name`         VARCHAR(120) NOT NULL                COMMENT '素材展示名',
  `file_name`    VARCHAR(255) NOT NULL                COMMENT '实际文件名（直链末段）',
  `asset_type`   ENUM('image','gif','video','audio','json') NOT NULL COMMENT '素材类型',
  `mime`         VARCHAR(120) NOT NULL DEFAULT ''     COMMENT 'MIME',
  `file_size`    BIGINT       NOT NULL DEFAULT 0      COMMENT '字节数',
  `sha256`       CHAR(64)     DEFAULT NULL            COMMENT 'sha256',
  `storage_path` VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '相对存储路径 assets/{id}/{file}',
  `is_public`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否公开：0仅自己可见 1对所有人可见',
  `is_deleted`   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否删除',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`asset_id`),
  KEY `idx_asset_user` (`user_id`, `is_deleted`, `created_at`),
  KEY `idx_asset_series` (`series_id`, `is_deleted`, `created_at`),
  KEY `idx_asset_public` (`is_public`, `is_deleted`, `created_at`),
  KEY `idx_asset_type` (`asset_type`, `is_public`, `is_deleted`),
  KEY `idx_asset_sha` (`sha256`),
  CONSTRAINT `fk_asset_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_asset_series` FOREIGN KEY (`series_id`) REFERENCES `asset_series` (`series_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目素材（图片/动图/视频/音频/JSON）';

-- ---------------------------------------------------------------------
-- 5. upload_session.kind：区分「项目源码」与「素材」两类上传
--    两条链路复用同一套分片协议（init / parts / complete），
--    只在 complete 时按 kind 分流到 project_file 或 asset。
-- ---------------------------------------------------------------------
ALTER TABLE `upload_session`
  ADD COLUMN `kind` ENUM('project','asset') NOT NULL DEFAULT 'project' COMMENT '上传目标：project项目源码 asset素材' AFTER `project_id`;
