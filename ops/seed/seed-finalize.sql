-- =====================================================================
-- coc-platform : seed finalize
-- Runs AFTER assets/views.generated.sql (which fills project_view_daily /
-- hourly), so the counters it derives are consistent.
-- =====================================================================

SET NAMES utf8mb4;

-- view_count_total = 近 30 天按天浏览之和（折线图与「热度」排序都用它）
UPDATE project p
SET p.view_count_total = COALESCE(
      (SELECT SUM(v.view_count) FROM project_view_daily v WHERE v.project_id = p.project_id), 0)
WHERE p.project_id BETWEEN 200 AND 299;

-- 评论数按实际明细回写（含楼中楼）
UPDATE project p
SET p.comment_count = COALESCE(
      (SELECT COUNT(*) FROM comment c WHERE c.project_id = p.project_id AND c.is_deleted = 0), 0)
WHERE p.project_id BETWEEN 200 AND 299;

-- 幂等校验：下面 3 列应为 0，后两列应为 8 / 30 / 48
SELECT
  (SELECT COUNT(*) FROM project WHERE project_id BETWEEN 201 AND 206 AND (title IS NULL OR title = '')) AS projects_without_title,
  (SELECT COUNT(*) FROM project_file WHERE project_file_id BETWEEN 201 AND 206 AND (storage_path IS NULL OR file_size = 0)) AS files_without_path,
  (SELECT COUNT(*) FROM project_version WHERE project_id BETWEEN 201 AND 206 AND created_by IS NULL) AS versions_without_creator,
  (SELECT COUNT(*) FROM comment WHERE project_id = 201 AND is_deleted = 0) AS comments_on_201,
  (SELECT COUNT(*) FROM project_view_daily WHERE project_id = 201) AS daily_rows_201,
  (SELECT COUNT(*) FROM project_view_hourly WHERE project_id = 201) AS hourly_rows_201;
