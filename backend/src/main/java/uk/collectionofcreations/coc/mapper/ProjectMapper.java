package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 项目 / 版本 / 文件 / 审核 相关 SQL */
@Mapper
public interface ProjectMapper {

    // ---------------- 列表 / 详情 ----------------

    @Select("<script>"
            + "SELECT p.project_id, p.title, p.summary, p.cover_path, p.project_type, p.category_id, p.status, "
            + "p.like_count, p.favorite_count, p.comment_count, p.view_count_total, p.published_at, p.created_at, "
            + "p.author_id, CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS author_nickname, "
            + "u.avatar_path AS author_avatar, c.name AS category_name "
            + "FROM project p JOIN `user` u ON u.user_id = p.author_id "
            + "LEFT JOIN category c ON c.category_id = p.category_id "
            + "WHERE p.status = 2 AND p.is_deleted = 0 "
            + "<if test='category_id != null'> AND p.category_id = #{category_id}</if>"
            + "<if test='project_type != null and project_type != \"\"'> AND p.project_type = #{project_type}</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND (p.title LIKE CONCAT('%', #{keyword}, '%') "
            + "OR p.summary LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<choose>"
            + "  <when test='sort != null and sort == \"hot\"'> ORDER BY p.view_count_total DESC, p.like_count DESC, p.project_id DESC</when>"
            + "  <otherwise> ORDER BY p.published_at DESC, p.project_id DESC</otherwise>"
            + "</choose>"
            + " LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> listPublished(Map<String, Object> param);

    @Select("<script>"
            + "SELECT COUNT(*) FROM project p WHERE p.status = 2 AND p.is_deleted = 0 "
            + "<if test='category_id != null'> AND p.category_id = #{category_id}</if>"
            + "<if test='project_type != null and project_type != \"\"'> AND p.project_type = #{project_type}</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND (p.title LIKE CONCAT('%', #{keyword}, '%') "
            + "OR p.summary LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "</script>")
    long countPublished(Map<String, Object> param);

    @Select("SELECT p.*, CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS author_nickname, "
            + "u.avatar_path AS author_avatar, u.status AS author_status, c.name AS category_name "
            + "FROM project p JOIN `user` u ON u.user_id = p.author_id "
            + "LEFT JOIN category c ON c.category_id = p.category_id "
            + "WHERE p.project_id = #{project_id}")
    Map<String, Object> findProjectById(@Param("project_id") long projectId);

    @Insert("INSERT INTO project (author_id, title, summary, description, project_type, category_id, cover_path, "
            + "status, audit_mode, is_deleted, created_at, updated_at) "
            + "VALUES (#{author_id}, #{title}, #{summary}, #{description}, #{project_type}, #{category_id}, "
            + "#{cover_path}, #{status}, #{audit_mode}, 0, NOW(), NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "project_id", before = false, resultType = Long.class)
    int insertProject(Map<String, Object> param);

    @Update("UPDATE project SET title = #{title}, summary = #{summary}, description = #{description}, "
            + "project_type = #{project_type}, category_id = #{category_id}, "
            + "cover_path = COALESCE(#{cover_path}, cover_path), updated_at = NOW() "
            + "WHERE project_id = #{project_id}")
    int updateProjectBasic(Map<String, Object> param);

    @Update("UPDATE project SET status = 2, published_at = IFNULL(published_at, NOW()), "
            + "reject_reason = NULL, down_reason = NULL, updated_at = NOW() WHERE project_id = #{project_id}")
    int publishProject(@Param("project_id") long projectId);

    /** 提交审核：0 草稿 / 4 已拒绝 → 1 待审 */
    @Update("UPDATE project SET status = 1, reject_reason = NULL, updated_at = NOW() WHERE project_id = #{project_id}")
    int submitForReview(@Param("project_id") long projectId);

    @Update("UPDATE project SET status = 4, reject_reason = #{reason}, updated_at = NOW() WHERE project_id = #{project_id}")
    int rejectProject(Map<String, Object> param);

    @Update("UPDATE project SET status = 3, down_reason = #{reason}, updated_at = NOW() WHERE project_id = #{project_id}")
    int downProject(Map<String, Object> param);

    @Update("UPDATE project SET status = 2, down_reason = NULL, updated_at = NOW() WHERE project_id = #{project_id} AND status = 3")
    int restoreProject(@Param("project_id") long projectId);

    @Update("UPDATE project SET is_deleted = 1, delete_reason = #{reason}, updated_at = NOW() WHERE project_id = #{project_id}")
    int softDeleteProject(Map<String, Object> param);

    @Update("UPDATE project SET view_count_total = view_count_total + 1 WHERE project_id = #{project_id}")
    int incViewCount(@Param("project_id") long projectId);

    @Update("UPDATE project SET like_count = GREATEST(like_count + #{delta}, 0) WHERE project_id = #{project_id}")
    int incLikeCount(Map<String, Object> param);

    @Update("UPDATE project SET favorite_count = GREATEST(favorite_count + #{delta}, 0) WHERE project_id = #{project_id}")
    int incFavoriteCount(Map<String, Object> param);

    @Update("UPDATE project SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE project_id = #{project_id}")
    int incCommentCount(Map<String, Object> param);

    // ---------------- 我的作品 / 管理端列表 ----------------

    @Select("<script>"
            + "SELECT p.project_id, p.title, p.summary, p.cover_path, p.project_type, p.category_id, p.status, "
            + "p.audit_mode, p.like_count, p.favorite_count, p.comment_count, p.view_count_total, "
            + "p.reject_reason, p.down_reason, p.is_deleted, p.published_at, p.created_at, p.updated_at, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS author_nickname, p.author_id "
            + "FROM project p JOIN `user` u ON u.user_id = p.author_id "
            + "WHERE p.is_deleted = 0 "
            + "<if test='author_id != null'> AND p.author_id = #{author_id}</if>"
            + "<if test='status != null'> AND p.status = #{status}</if>"
            + "<if test='project_type != null and project_type != \"\"'> AND p.project_type = #{project_type}</if>"
            + " ORDER BY p.project_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> listMine(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM project p WHERE p.is_deleted = 0 "
            + "<if test='author_id != null'> AND p.author_id = #{author_id}</if>"
            + "<if test='status != null'> AND p.status = #{status}</if>"
            + "<if test='project_type != null and project_type != \"\"'> AND p.project_type = #{project_type}</if>"
            + "</script>")
    long countMine(Map<String, Object> param);

    @Select("<script>"
            + "SELECT p.project_id, p.title, p.summary, p.project_type, p.category_id, p.status, p.audit_mode, "
            + "p.is_deleted, p.reject_reason, p.down_reason, p.delete_reason, p.author_id, p.view_count_total, "
            + "p.like_count, p.comment_count, p.favorite_count, p.published_at, p.created_at, p.updated_at, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS author_nickname, u.qq_account AS author_qq "
            + "FROM project p JOIN `user` u ON u.user_id = p.author_id WHERE 1 = 1 "
            + "<if test='status != null'> AND p.status = #{status}</if>"
            + "<if test='include_deleted == null or include_deleted == false'> AND p.is_deleted = 0</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND p.title LIKE CONCAT('%', #{keyword}, '%')</if>"
            + " ORDER BY p.project_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> adminListProjects(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM project p WHERE 1 = 1 "
            + "<if test='status != null'> AND p.status = #{status}</if>"
            + "<if test='include_deleted == null or include_deleted == false'> AND p.is_deleted = 0</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND p.title LIKE CONCAT('%', #{keyword}, '%')</if>"
            + "</script>")
    long adminCountProjects(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM project WHERE is_deleted = 0")
    long countActive();

    @Select("SELECT COUNT(*) FROM project WHERE status = #{status} AND is_deleted = 0")
    long countByStatus(@Param("status") int status);

    // ---------------- 版本 ----------------

    @Select("SELECT project_version_id, project_id, version_no, changelog, created_by, created_at "
            + "FROM project_version WHERE project_id = #{project_id} ORDER BY version_no DESC LIMIT 1")
    Map<String, Object> findLatestVersion(@Param("project_id") long projectId);

    @Select("SELECT project_version_id, project_id, version_no, changelog, created_by, created_at "
            + "FROM project_version WHERE project_version_id = #{project_version_id}")
    Map<String, Object> findVersionById(@Param("project_version_id") long versionId);

    @Select("SELECT IFNULL(MAX(version_no), 0) FROM project_version WHERE project_id = #{project_id}")
    int maxVersionNo(@Param("project_id") long projectId);

    @Insert("INSERT INTO project_version (project_id, version_no, changelog, created_by, created_at) "
            + "VALUES (#{project_id}, #{version_no}, #{changelog}, #{created_by}, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "project_version_id", before = false, resultType = Long.class)
    int insertVersion(Map<String, Object> param);

    // ---------------- 文件 ----------------

    @Insert("INSERT INTO project_file (project_version_id, file_type, file_name, file_size, sha256, storage_path, "
            + "scan_status, build_status, build_path, created_at) "
            + "VALUES (#{project_version_id}, #{file_type}, #{file_name}, #{file_size}, #{sha256}, #{storage_path}, "
            + "#{scan_status}, #{build_status}, #{build_path}, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "project_file_id", before = false, resultType = Long.class)
    int insertFile(Map<String, Object> param);

    @Select("SELECT project_file_id, project_version_id, file_type, file_name, file_size, sha256, storage_path, "
            + "scan_status, build_status, build_path, created_at FROM project_file WHERE project_file_id = #{project_file_id}")
    Map<String, Object> findFileById(@Param("project_file_id") long fileId);

    @Select("SELECT pf.project_file_id, pf.project_version_id, pf.file_type, pf.file_name, pf.file_size, pf.sha256, "
            + "pf.storage_path, pf.build_status, pf.build_path, pf.created_at "
            + "FROM project_file pf "
            + "JOIN project_version pv ON pv.project_version_id = pf.project_version_id "
            + "JOIN project p ON p.project_id = pv.project_id "
            + "WHERE pf.sha256 = #{sha256} AND pf.file_type = 'source' AND p.is_deleted = 0 "
            + "ORDER BY pf.project_file_id ASC LIMIT 1")
    Map<String, Object> findFileBySha256(@Param("sha256") String sha256);

    @Select("SELECT pf.project_file_id, pf.project_version_id, pf.file_type, pf.file_name, pf.file_size, pf.build_status, "
            + "pf.build_path, pf.created_at FROM project_file pf WHERE pf.project_version_id = #{project_version_id}")
    List<Map<String, Object>> listFilesByVersion(@Param("project_version_id") long versionId);

    @Select("SELECT pf.project_file_id, pf.project_version_id, pf.file_type, pf.file_name, pf.file_size, pf.sha256, "
            + "pf.storage_path, pf.build_status, pf.build_path, pv.version_no "
            + "FROM project_file pf JOIN project_version pv ON pv.project_version_id = pf.project_version_id "
            + "WHERE pv.project_id = #{project_id} ORDER BY pv.version_no DESC, pf.project_file_id ASC")
    List<Map<String, Object>> listFilesByProject(@Param("project_id") long projectId);

    @Update("UPDATE project_file SET build_status = #{build_status}, build_path = #{build_path} WHERE project_file_id = #{project_file_id}")
    int updateFileBuild(Map<String, Object> param);

    @Select("SELECT project_file_id, project_version_id, file_type, file_name, sha256, storage_path "
            + "FROM project_file WHERE project_version_id = #{project_version_id} AND file_type = #{file_type} LIMIT 1")
    Map<String, Object> findFileByVersionAndType(Map<String, Object> param);

    // ---------------- 审核记录 ----------------

    @Insert("INSERT INTO project_review (project_version_id, admin_id, action, reason, created_at) "
            + "VALUES (#{project_version_id}, #{admin_id}, #{action}, #{reason}, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "project_review_id", before = false, resultType = Long.class)
    int insertReview(Map<String, Object> param);

    @Select("SELECT pr.project_review_id, pr.project_version_id, pr.admin_id, pr.action, pr.reason, pr.created_at "
            + "FROM project_review pr WHERE pr.project_version_id = #{project_version_id} ORDER BY pr.project_review_id DESC")
    List<Map<String, Object>> listReviews(@Param("project_version_id") long versionId);

    /** 审核队列：待审项目 */
    @Select("SELECT p.project_id, p.title, p.project_type, p.status, p.created_at, p.author_id, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS author_nickname, "
            + "(SELECT pv.project_version_id FROM project_version pv WHERE pv.project_id = p.project_id "
            + " ORDER BY pv.version_no DESC LIMIT 1) AS latest_version_id "
            + "FROM project p JOIN `user` u ON u.user_id = p.author_id "
            + "WHERE p.status = 1 AND p.is_deleted = 0 ORDER BY p.created_at ASC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listReviewQueue(Map<String, Object> param);

    @Delete("DELETE FROM project WHERE project_id = #{project_id}")
    int hardDeleteProject(@Param("project_id") long projectId);
}
