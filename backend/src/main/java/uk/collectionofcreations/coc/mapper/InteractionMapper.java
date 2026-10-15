package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 互动：项目点赞 / 收藏 / 关注 */
@Mapper
public interface InteractionMapper {

    // ---- 项目点赞 ----

    @Select("SELECT project_like_id FROM project_like WHERE project_id = #{project_id} AND user_id = #{user_id}")
    Long findProjectLike(@Param("project_id") long projectId, @Param("user_id") long userId);

    @Insert("INSERT IGNORE INTO project_like (project_id, user_id, created_at) VALUES (#{project_id}, #{user_id}, NOW())")
    int insertProjectLike(Map<String, Object> param);

    @Delete("DELETE FROM project_like WHERE project_id = #{project_id} AND user_id = #{user_id}")
    int deleteProjectLike(@Param("project_id") long projectId, @Param("user_id") long userId);

    @Select("SELECT COUNT(*) FROM project_like WHERE project_id = #{project_id}")
    long countProjectLikes(@Param("project_id") long projectId);

    // ---- 项目收藏 ----

    @Select("SELECT project_favorite_id FROM project_favorite WHERE project_id = #{project_id} AND user_id = #{user_id}")
    Long findProjectFavorite(@Param("project_id") long projectId, @Param("user_id") long userId);

    @Insert("INSERT IGNORE INTO project_favorite (project_id, user_id, created_at) VALUES (#{project_id}, #{user_id}, NOW())")
    int insertProjectFavorite(Map<String, Object> param);

    @Delete("DELETE FROM project_favorite WHERE project_id = #{project_id} AND user_id = #{user_id}")
    int deleteProjectFavorite(@Param("project_id") long projectId, @Param("user_id") long userId);

    @Select("SELECT COUNT(*) FROM project_favorite WHERE project_id = #{project_id}")
    long countProjectFavorites(@Param("project_id") long projectId);

    /** 我的收藏（含项目摘要） */
    @Select("SELECT p.project_id, p.title, p.summary, p.cover_path, p.project_type, p.category_id, p.like_count, "
            + "p.favorite_count, p.view_count_total, p.comment_count, f.created_at AS favorited_at "
            + "FROM project_favorite f JOIN project p ON p.project_id = f.project_id "
            + "WHERE f.user_id = #{user_id} AND p.status = 2 AND p.is_deleted = 0 "
            + "ORDER BY f.project_favorite_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listMyFavorites(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM project_favorite f JOIN project p ON p.project_id = f.project_id "
            + "WHERE f.user_id = #{user_id} AND p.status = 2 AND p.is_deleted = 0")
    long countMyFavorites(@Param("user_id") long userId);

    // ---- 关注 ----

    @Select("SELECT follow_id FROM follow WHERE follower_id = #{follower_id} AND followee_id = #{followee_id}")
    Long findFollow(@Param("follower_id") long followerId, @Param("followee_id") long followeeId);

    @Insert("INSERT IGNORE INTO follow (follower_id, followee_id, created_at) VALUES (#{follower_id}, #{followee_id}, NOW())")
    int insertFollow(Map<String, Object> param);

    @Delete("DELETE FROM follow WHERE follower_id = #{follower_id} AND followee_id = #{followee_id}")
    int deleteFollow(@Param("follower_id") long followerId, @Param("followee_id") long followeeId);

    @Select("SELECT COUNT(*) FROM follow WHERE followee_id = #{user_id}")
    long countFollowers(@Param("user_id") long userId);

    @Select("SELECT COUNT(*) FROM follow WHERE follower_id = #{user_id}")
    long countFollowing(@Param("user_id") long userId);
}
