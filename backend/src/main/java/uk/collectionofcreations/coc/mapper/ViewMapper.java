package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 浏览量计数（按天 / 按小时，无用户维度） */
@Mapper
public interface ViewMapper {

    @Insert("INSERT INTO project_view_daily (project_id, stat_date, view_count) VALUES (#{project_id}, CURDATE(), 1) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + 1")
    int upsertDaily(@Param("project_id") long projectId);

    @Insert("INSERT INTO project_view_hourly (project_id, stat_hour, view_count) "
            + "VALUES (#{project_id}, DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), 1) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + 1")
    int upsertHourly(@Param("project_id") long projectId);

    /** 最近 N 天的每日浏览量 */
    @Select("SELECT DATE_FORMAT(stat_date, '%Y-%m-%d') AS stat_key, view_count AS cnt FROM project_view_daily "
            + "WHERE project_id = #{project_id} AND stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)")
    List<Map<String, Object>> dailyViews(Map<String, Object> param);

    /** 最近 N 小时的每小时浏览量 */
    @Select("SELECT DATE_FORMAT(stat_hour, '%Y-%m-%d %H:00') AS stat_key, view_count AS cnt FROM project_view_hourly "
            + "WHERE project_id = #{project_id} AND stat_hour >= DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), "
            + "INTERVAL #{hours} HOUR)")
    List<Map<String, Object>> hourlyViews(Map<String, Object> param);

    /** 最近 N 天的评论数（动态计算，不落统计表） */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS stat_key, COUNT(*) AS cnt FROM comment "
            + "WHERE project_id = #{project_id} AND is_deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')")
    List<Map<String, Object>> dailyComments(Map<String, Object> param);

    /** 最近 N 小时的评论数 */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:00') AS stat_key, COUNT(*) AS cnt FROM comment "
            + "WHERE project_id = #{project_id} AND is_deleted = 0 "
            + "AND created_at >= DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), INTERVAL #{hours} HOUR) "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:00')")
    List<Map<String, Object>> hourlyComments(Map<String, Object> param);

    /** 今日全平台浏览量 */
    @Select("SELECT IFNULL(SUM(view_count), 0) FROM project_view_daily WHERE stat_date = CURDATE()")
    long todayViewsTotal();
}
