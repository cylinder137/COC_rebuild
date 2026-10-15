package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

import java.util.List;
import java.util.Map;

/** 操作审计日志 */
@Mapper
public interface LogMapper {

    @Insert("INSERT INTO operation_log (admin_id, action, target_type, target_id, detail, ip, created_at) "
            + "VALUES (#{admin_id}, #{action}, #{target_type}, #{target_id}, #{detail}, #{ip}, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "log_id", before = false, resultType = Long.class)
    int insertLog(Map<String, Object> param);

    @Select("<script>SELECT log_id, admin_id, action, target_type, target_id, detail, ip, created_at "
            + "FROM operation_log WHERE 1=1 "
            + "<if test='action != null'> AND action = #{action}</if>"
            + "<if test='target_type != null'> AND target_type = #{target_type}</if>"
            + " ORDER BY log_id DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> listLogs(Map<String, Object> param);
}
