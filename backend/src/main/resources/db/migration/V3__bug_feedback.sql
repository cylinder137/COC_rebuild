-- ---------------------------------------------------------------------
-- Bug 反馈：用户在设置页提交的问题反馈，管理员在后台按状态受理
-- ---------------------------------------------------------------------
CREATE TABLE `bug_feedback` (
  `bug_id`     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
  `user_id`    BIGINT        NOT NULL                COMMENT '提交用户',
  `content`    VARCHAR(1000) NOT NULL                COMMENT '反馈内容',
  `status`     TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0未受理 1已处理',
  `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (`bug_id`),
  KEY `idx_bug_status` (`status`, `created_at`),
  KEY `idx_bug_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_bug_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bug 反馈';
