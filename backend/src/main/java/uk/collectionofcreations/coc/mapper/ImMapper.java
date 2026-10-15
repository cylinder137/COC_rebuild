package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** IM：会话 / 消息 / 成员 */
@Mapper
public interface ImMapper {

    /** 我参与的会话列表（含单聊对端信息与未读数） */
    @Select("SELECT s.im_session_id, s.kind, s.project_id, s.user_a_id, s.user_b_id, s.title, "
            + "s.last_message_at, s.last_message_preview, m.last_read_message_id, "
            + "(SELECT COUNT(*) FROM im_message im WHERE im.session_id = s.im_session_id AND im.is_deleted = 0 "
            + " AND im.im_message_id > IFNULL(m.last_read_message_id, 0) AND im.sender_id <> #{user_id}) AS unread_count, "
            + "CASE WHEN s.kind = 1 THEN (SELECT CASE WHEN ou.status = 3 THEN '用户已注销' ELSE ou.nickname END "
            + "  FROM `user` ou WHERE ou.user_id = IF(s.user_a_id = #{user_id}, s.user_b_id, s.user_a_id)) "
            + "  ELSE s.title END AS display_title, "
            + "CASE WHEN s.kind = 1 THEN (SELECT ou.avatar_path FROM `user` ou "
            + "  WHERE ou.user_id = IF(s.user_a_id = #{user_id}, s.user_b_id, s.user_a_id)) ELSE NULL END AS display_avatar, "
            + "CASE WHEN s.kind = 1 THEN IF(s.user_a_id = #{user_id}, s.user_b_id, s.user_a_id) ELSE NULL END AS peer_user_id "
            + "FROM im_session s JOIN im_session_member m ON m.session_id = s.im_session_id AND m.user_id = #{user_id} "
            + "ORDER BY IFNULL(s.last_message_at, s.created_at) DESC")
    List<Map<String, Object>> listSessionsForUser(@Param("user_id") long userId);

    @Select("SELECT im_session_id, kind, project_id, user_a_id, user_b_id, title, last_message_at, "
            + "last_message_preview, created_at FROM im_session WHERE im_session_id = #{im_session_id}")
    Map<String, Object> findSessionById(@Param("im_session_id") long sessionId);

    @Select("SELECT im_session_id, kind, project_id, user_a_id, user_b_id, title FROM im_session "
            + "WHERE kind = 1 AND ((user_a_id = #{user_id} AND user_b_id = #{peer_id}) "
            + "OR (user_a_id = #{peer_id} AND user_b_id = #{user_id})) LIMIT 1")
    Map<String, Object> findDirectSession(@Param("user_id") long userId, @Param("peer_id") long peerId);

    @Select("SELECT im_session_id, kind, project_id, user_a_id, user_b_id, title FROM im_session "
            + "WHERE kind = 2 AND project_id = #{project_id} LIMIT 1")
    Map<String, Object> findGroupSessionByProject(@Param("project_id") long projectId);

    @Insert("INSERT INTO im_session (kind, project_id, user_a_id, user_b_id, title, created_at) "
            + "VALUES (#{kind}, #{project_id}, #{user_a_id}, #{user_b_id}, #{title}, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "im_session_id", before = false, resultType = Long.class)
    int insertSession(Map<String, Object> param);

    @Insert("INSERT IGNORE INTO im_session_member (session_id, user_id, last_read_message_id, joined_at) "
            + "VALUES (#{session_id}, #{user_id}, 0, NOW())")
    int addMember(Map<String, Object> param);

    @Select("SELECT user_id FROM im_session_member WHERE session_id = #{session_id}")
    List<Long> listMemberIds(@Param("session_id") long sessionId);

    @Select("SELECT COUNT(*) FROM im_session_member WHERE session_id = #{session_id} AND user_id = #{user_id}")
    long isMember(@Param("session_id") long sessionId, @Param("user_id") long userId);

    @Insert("INSERT INTO im_message (session_id, sender_id, content, content_type, client_msg_id, is_deleted, created_at) "
            + "VALUES (#{session_id}, #{sender_id}, #{content}, #{content_type}, #{client_msg_id}, 0, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "im_message_id", before = false, resultType = Long.class)
    int insertMessage(Map<String, Object> param);

    @Select("SELECT m.im_message_id, m.session_id, m.sender_id, m.content, m.content_type, m.client_msg_id, "
            + "m.created_at, CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS sender_nickname, "
            + "u.avatar_path AS sender_avatar "
            + "FROM im_message m JOIN `user` u ON u.user_id = m.sender_id WHERE m.im_message_id = #{im_message_id}")
    Map<String, Object> findMessageById(@Param("im_message_id") long messageId);

    /** 游标分页：每页 limit（默认 20）条，cursor = 上一页最小 message_id */
    @Select("<script>SELECT m.im_message_id, m.session_id, m.sender_id, m.content, m.content_type, m.client_msg_id, "
            + "m.created_at, CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS sender_nickname, "
            + "u.avatar_path AS sender_avatar "
            + "FROM im_message m JOIN `user` u ON u.user_id = m.sender_id "
            + "WHERE m.session_id = #{session_id} AND m.is_deleted = 0 "
            + "<if test='cursor != null'> AND m.im_message_id &lt; #{cursor}</if> "
            + "ORDER BY m.im_message_id DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> listMessages(Map<String, Object> param);

    @Select("SELECT IFNULL(MAX(im_message_id), 0) FROM im_message WHERE session_id = #{session_id}")
    long maxMessageId(@Param("session_id") long sessionId);

    @Update("UPDATE im_session_member SET last_read_message_id = GREATEST(last_read_message_id, #{message_id}) "
            + "WHERE session_id = #{session_id} AND user_id = #{user_id}")
    int updateLastRead(Map<String, Object> param);

    @Select("SELECT IFNULL(MAX(last_read_message_id), 0) FROM im_session_member "
            + "WHERE session_id = #{session_id} AND user_id = #{user_id}")
    Long findLastRead(@Param("session_id") long sessionId, @Param("user_id") long userId);

    @Update("UPDATE im_session SET last_message_at = NOW(), last_message_preview = #{preview} "
            + "WHERE im_session_id = #{im_session_id}")
    int updateSessionPreview(Map<String, Object> param);

    @Select("SELECT IFNULL(SUM(unread), 0) FROM ("
            + " SELECT (SELECT COUNT(*) FROM im_message im WHERE im.session_id = m.session_id AND im.is_deleted = 0 "
            + "   AND im.im_message_id > IFNULL(m.last_read_message_id, 0) AND im.sender_id <> #{user_id}) AS unread "
            + " FROM im_session_member m WHERE m.user_id = #{user_id}) t")
    long countUnreadTotal(@Param("user_id") long userId);
}
