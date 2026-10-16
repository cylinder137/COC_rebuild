-- ---------------------------------------------------------------------
-- V2：找回密码申请支持「驳回」（保留申请记录，不物理删除）
--   status 语义扩展：0 待处理 / 1 已办结（已生成临时密码） / 2 已驳回
-- ---------------------------------------------------------------------

ALTER TABLE `password_reset_apply`
  ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '驳回理由（会通知申请人）' AFTER `temp_password`;

ALTER TABLE `password_reset_apply`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1已办结 2已驳回';
