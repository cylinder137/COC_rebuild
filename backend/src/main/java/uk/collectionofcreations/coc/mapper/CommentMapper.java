package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 评论 / 评论点赞 */
@Mapper
public interface CommentMapper {

    /** 根评论（parent_comment_id IS NULL）分页 */
    @Select("SELECT c.comment_id, c.project_id, c.user_id, c.parent_comment_id, c.root_comment_id, c.content, "
            + "c.like_count, c.created_at, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS user_nickname, "
            + "u.avatar_path AS user_avatar, u.status AS user_status "
            + "FROM comment c JOIN `user` u ON u.user_id = c.user_id "
            + "WHERE c.project_id = #{project_id} AND c.is_deleted = 0 AND c.parent_comment_id IS NULL "
            + "ORDER BY c.created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listRootComments(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM comment WHERE project_id = #{project_id} AND is_deleted = 0 AND parent_comment_id IS NULL")
    long countRootComments(@Param("project_id") long projectId);

    /** 某批根评论下的全部回复（楼中楼固定一层） */
    @Select("<script>SELECT c.comment_id, c.project_id, c.user_id, c.parent_comment_id, c.root_comment_id, c.content, "
            + "c.like_count, c.created_at, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS user_nickname, "
            + "u.avatar_path AS user_avatar, u.status AS user_status "
            + "FROM comment c JOIN `user` u ON u.user_id = c.user_id "
            + "WHERE c.is_deleted = 0 AND c.root_comment_id IN "
            + "<foreach collection='root_ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY c.created_at ASC</script>")
    List<Map<String, Object>> listReplies(Map<String, Object> param);

    @Select("SELECT comment_id, project_id, user_id, parent_comment_id, root_comment_id, content, like_count, "
            + "is_deleted, created_at FROM comment WHERE comment_id = #{comment_id}")
    Map<String, Object> findById(@Param("comment_id") long commentId);

    /** 单条评论（带作者昵称，用于发表评论后的回显） */
    @Select("SELECT c.comment_id, c.project_id, c.user_id, c.parent_comment_id, c.root_comment_id, c.content, "
            + "c.like_count, c.created_at, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS user_nickname, "
            + "u.avatar_path AS user_avatar FROM comment c JOIN `user` u ON u.user_id = c.user_id "
            + "WHERE c.comment_id = #{comment_id}")
    Map<String, Object> findDetailById(@Param("comment_id") long commentId);

    @Insert("INSERT INTO comment (project_id, user_id, parent_comment_id, root_comment_id, content, like_count, "
            + "is_deleted, created_at) VALUES (#{project_id}, #{user_id}, #{parent_comment_id}, #{root_comment_id}, "
            + "#{content}, 0, 0, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "comment_id", before = false, resultType = Long.class)
    int insertComment(Map<String, Object> param);

    @Update("UPDATE comment SET like_count = GREATEST(like_count + #{delta}, 0) WHERE comment_id = #{comment_id}")
    int incLikeCount(Map<String, Object> param);

    @Update("UPDATE comment SET is_deleted = 1 WHERE comment_id = #{comment_id}")
    int softDelete(@Param("comment_id") long commentId);

    @Select("SELECT comment_count FROM comment WHERE project_id = #{project_id} AND is_deleted = 0")
    List<Integer> unusedDummy(@Param("project_id") long projectId);

    // ---------------- 点赞 ----------------

    @Select("SELECT comment_like_id FROM comment_like WHERE comment_id = #{comment_id} AND user_id = #{user_id}")
    Long findLike(@Param("comment_id") long commentId, @Param("user_id") long userId);

    @Insert("INSERT IGNORE INTO comment_like (comment_id, user_id, created_at) VALUES (#{comment_id}, #{user_id}, NOW())")
    int insertLike(Map<String, Object> param);

    @org.apache.ibatis.annotations.Delete("DELETE FROM comment_like WHERE comment_id = #{comment_id} AND user_id = #{user_id}")
    int deleteLike(@Param("comment_id") long commentId, @Param("user_id") long userId);

    /** 当前登录用户在给定评论集合中已点赞的评论ID */
    @Select("<script>SELECT comment_id FROM comment_like WHERE user_id = #{user_id} AND comment_id IN "
            + "<foreach collection='comment_ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> likedCommentIds(Map<String, Object> param);

    /** 管理端评论列表 */
    @Select("<script>SELECT c.comment_id, c.project_id, c.content, c.like_count, c.is_deleted, c.created_at, "
            + "c.user_id, CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS user_nickname, p.title AS project_title "
            + "FROM comment c JOIN `user` u ON u.user_id = c.user_id JOIN project p ON p.project_id = c.project_id "
            + "WHERE 1 = 1 <if test='project_id != null'> AND c.project_id = #{project_id}</if>"
            + " ORDER BY c.comment_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> adminListComments(Map<String, Object> param);
}
