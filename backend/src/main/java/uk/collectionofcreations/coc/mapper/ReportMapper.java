package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 举报 */
@Mapper
public interface ReportMapper {

    @Insert("INSERT INTO report (reporter_id, target_type, target_id, reason_type, detail, status, reporter_read, created_at) "
            + "VALUES (#{reporter_id}, #{target_type}, #{target_id}, #{reason_type}, #{detail}, 0, 0, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "report_id", before = false, resultType = Long.class)
    int insertReport(Map<String, Object> param);

    @Select("SELECT report_id, reporter_id, target_type, target_id, reason_type, detail, status, handler_id, "
            + "handle_result, handled_at, reporter_read, reward_points, created_at FROM report WHERE report_id = #{report_id}")
    Map<String, Object> findById(@Param("report_id") long reportId);

    /** 我的举报（带目标摘要） */
    @Select("<script>SELECT r.report_id, r.target_type, r.target_id, r.reason_type, r.detail, r.status, "
            + "r.handle_result, r.handled_at, r.reporter_read, r.reward_points, r.created_at, "
            + "CASE r.target_type WHEN 'project' THEN (SELECT p.title FROM project p WHERE p.project_id = r.target_id) "
            + "WHEN 'comment' THEN (SELECT c.content FROM comment c WHERE c.comment_id = r.target_id) ELSE NULL END AS target_summary "
            + "FROM report r WHERE r.reporter_id = #{reporter_id} ORDER BY r.report_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> listMine(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM report WHERE reporter_id = #{reporter_id}")
    long countMine(@Param("reporter_id") long reporterId);

    @Update("UPDATE report SET reporter_read = 1 WHERE reporter_id = #{reporter_id} AND status <> 0")
    int markMineRead(@Param("reporter_id") long reporterId);

    @Select("SELECT COUNT(*) FROM report WHERE reporter_id = #{reporter_id} AND status <> 0 AND reporter_read = 0")
    long countUnreadMine(@Param("reporter_id") long reporterId);

    /** 管理端列表 */
    @Select("<script>SELECT r.report_id, r.reporter_id, r.target_type, r.target_id, r.reason_type, r.detail, r.status, "
            + "r.handler_id, r.handle_result, r.handled_at, r.created_at, "
            + "CASE WHEN ru.status = 3 THEN '用户已注销' ELSE ru.nickname END AS reporter_nickname, "
            + "CASE r.target_type WHEN 'project' THEN (SELECT p.title FROM project p WHERE p.project_id = r.target_id) "
            + "WHEN 'comment' THEN (SELECT c.content FROM comment c WHERE c.comment_id = r.target_id) ELSE NULL END AS target_summary "
            + "FROM report r JOIN `user` ru ON ru.user_id = r.reporter_id "
            + "<where><if test='status != null'> r.status = #{status}</if></where> "
            + "ORDER BY r.report_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> adminList(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM report r <where><if test='status != null'> r.status = #{status}</if></where></script>")
    long adminCount(Map<String, Object> param);

    @Update("UPDATE report SET status = #{status}, handle_result = #{handle_result}, handler_id = #{handler_id}, "
            + "handled_at = NOW(), reward_points = #{reward_points} WHERE report_id = #{report_id}")
    int handle(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM report WHERE status = 0")
    long countPending();
}
