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

/** Bug 反馈 */
@Mapper
public interface BugFeedbackMapper {

    @Insert("INSERT INTO bug_feedback (user_id, content, status, created_at) "
            + "VALUES (#{user_id}, #{content}, 0, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "bug_id", before = false, resultType = Long.class)
    int insertFeedback(Map<String, Object> param);

    @Select("SELECT bug_id, user_id, content, status, created_at FROM bug_feedback WHERE bug_id = #{bug_id}")
    Map<String, Object> findById(@Param("bug_id") long bugId);

    /** 管理端列表（带提交人信息） */
    @Select("<script>SELECT b.bug_id, b.user_id, b.content, b.status, b.created_at, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS user_nickname, u.qq_account "
            + "FROM bug_feedback b JOIN `user` u ON u.user_id = b.user_id "
            + "<where><if test='status != null'> b.status = #{status}</if></where> "
            + "ORDER BY b.bug_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> adminList(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM bug_feedback b "
            + "<where><if test='status != null'> b.status = #{status}</if></where></script>")
    long adminCount(Map<String, Object> param);

    @Update("UPDATE bug_feedback SET status = 1 WHERE bug_id = #{bug_id}")
    int markHandled(@Param("bug_id") long bugId);

    /** 管理端编辑：content / status 二选一或同时改（动态 set） */
    @Update("<script>UPDATE bug_feedback "
            + "<set><if test='content != null'> content = #{content},</if>"
            + "<if test='status != null'> status = #{status},</if></set> "
            + "WHERE bug_id = #{bug_id}</script>")
    int updateById(Map<String, Object> param);

    /** 管理端删除 */
    @Delete("DELETE FROM bug_feedback WHERE bug_id = #{bug_id}")
    int deleteById(@Param("bug_id") long bugId);
}
