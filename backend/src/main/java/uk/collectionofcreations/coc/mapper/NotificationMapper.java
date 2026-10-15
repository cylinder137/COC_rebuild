package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 站内通知 */
@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notification (user_id, type, title, content, link, is_read, created_at) "
            + "VALUES (#{user_id}, #{type}, #{title}, #{content}, #{link}, 0, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "notification_id", before = false, resultType = Long.class)
    int insert(Map<String, Object> param);

    @Select("SELECT notification_id, user_id, type, title, content, link, is_read, created_at FROM notification "
            + "WHERE user_id = #{user_id} ORDER BY notification_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listMine(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{user_id}")
    long countMine(@Param("user_id") long userId);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{user_id} AND is_read = 0")
    long countUnread(@Param("user_id") long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE notification_id = #{notification_id} AND user_id = #{user_id}")
    int markRead(Map<String, Object> param);

    @Update("UPDATE notification SET is_read = 1 WHERE user_id = #{user_id}")
    int markAllRead(@Param("user_id") long userId);
}
