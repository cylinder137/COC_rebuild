-- =====================================================================
-- coc-platform : remove the backend self-test fixtures
--
-- The backend sibling session created throw-away accounts while self-testing
--   qq 900000 (selftest-admin), 900001 (selftestdev), 900002 (selftestdev2)
-- plus their project "selftest-html-project", comments, notifications,
-- reports, dev applications, IM sessions and view rows.
--
-- This script removes EXACTLY those rows (matched by the 9000xx account range
-- and by title LIKE 'selftest%'), and nothing else. It is safe to run twice and
-- safe to run when there is nothing to clean.
--
-- It is invoked automatically by run-seed.cmd before the demo seed is loaded
-- (set COC_SEED_KEEP_SELFTEST=1 to skip it).
-- =====================================================================

SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_st_users (user_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_st_users (user_id)
  SELECT user_id FROM `user` WHERE qq_account LIKE '9000%';

CREATE TEMPORARY TABLE tmp_st_projects (project_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_st_projects (project_id)
  SELECT p.project_id FROM project p
   WHERE p.author_id IN (SELECT user_id FROM tmp_st_users)
      OR p.title LIKE 'selftest%';

CREATE TEMPORARY TABLE tmp_st_versions (project_version_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_st_versions (project_version_id)
  SELECT project_version_id FROM project_version
   WHERE project_id IN (SELECT project_id FROM tmp_st_projects);

CREATE TEMPORARY TABLE tmp_st_files (project_file_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_st_files (project_file_id)
  SELECT project_file_id FROM project_file
   WHERE project_version_id IN (SELECT project_version_id FROM tmp_st_versions);

-- RESTRICT references must go before the rows they point at
DELETE FROM project_review
 WHERE admin_id IN (SELECT user_id FROM tmp_st_users)
    OR project_version_id IN (SELECT project_version_id FROM tmp_st_versions);

DELETE FROM build_task
 WHERE project_file_id IN (SELECT project_file_id FROM tmp_st_files);

-- cascades: project_version / project_file / project_tag / comment /
--           project_like / project_favorite / project_view_* / im_session
DELETE FROM project WHERE project_id IN (SELECT project_id FROM tmp_st_projects);

DELETE FROM comment          WHERE user_id IN (SELECT user_id FROM tmp_st_users);
DELETE FROM comment_like     WHERE user_id IN (SELECT user_id FROM tmp_st_users);
DELETE FROM project_like     WHERE user_id IN (SELECT user_id FROM tmp_st_users);
DELETE FROM project_favorite WHERE user_id IN (SELECT user_id FROM tmp_st_users);
-- (JOIN form: MySQL cannot reference a TEMPORARY table twice in one statement)
DELETE r FROM report r
  JOIN tmp_st_users u ON r.reporter_id = u.user_id OR r.handler_id = u.user_id;
DELETE FROM notification     WHERE user_id IN (SELECT user_id FROM tmp_st_users);
DELETE d FROM developer_profile d
  JOIN tmp_st_users u ON d.user_id = u.user_id OR d.reviewer_id = u.user_id;
DELETE f FROM follow f
  JOIN tmp_st_users u ON f.follower_id = u.user_id OR f.followee_id = u.user_id;
DELETE FROM upload_session   WHERE user_id IN (SELECT user_id FROM tmp_st_users);
DELETE FROM operation_log    WHERE admin_id IN (SELECT user_id FROM tmp_st_users);
DELETE s FROM im_session s
  JOIN tmp_st_users u ON s.user_a_id = u.user_id OR s.user_b_id = u.user_id;
DELETE FROM password_reset_apply
 WHERE qq_account IN (SELECT qq_account FROM `user` WHERE user_id IN (SELECT user_id FROM tmp_st_users));

DELETE FROM `user` WHERE user_id IN (SELECT user_id FROM tmp_st_users);

-- the self-test tag, but only if no surviving project still uses it
DELETE FROM tag
 WHERE name = 'selftest'
   AND tag_id NOT IN (SELECT tag_id FROM project_tag);

-- verification: every value must be 0
SELECT
  (SELECT COUNT(*) FROM `user` WHERE qq_account LIKE '9000%')                       AS selftest_users_left,
  (SELECT COUNT(*) FROM project WHERE title LIKE 'selftest%')                       AS selftest_projects_left,
  (SELECT COUNT(*) FROM project_version WHERE project_id IN (SELECT project_id FROM tmp_st_projects)) AS selftest_versions_left,
  (SELECT COUNT(*) FROM comment WHERE user_id IN (SELECT user_id FROM tmp_st_users)) AS selftest_comments_left;
