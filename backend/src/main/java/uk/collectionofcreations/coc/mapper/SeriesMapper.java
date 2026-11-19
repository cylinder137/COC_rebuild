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

/**
 * 系列（项目系列 / 素材系列）。
 * <p>两张表结构完全相同、语义只差「子项是项目还是素材」，所以这里用一份 SQL：
 * 表名走 {@code ${table}} / {@code ${child}} 占位，取值**只能**来自
 * {@link uk.collectionofcreations.coc.service.SeriesService.Kind} 的白名单，
 * 不接受任何前端传入的字符串，避免 SQL 拼接风险。
 */
@Mapper
public interface SeriesMapper {

    @Insert("INSERT INTO ${table} (user_id, name, intro, is_public, created_at, updated_at) "
            + "VALUES (#{user_id}, #{name}, #{intro}, #{is_public}, NOW(), NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "series_id", before = false, resultType = Long.class)
    int insert(Map<String, Object> param);

    @Select("SELECT series_id, user_id, name, intro, is_public, created_at, updated_at "
            + "FROM ${table} WHERE series_id = #{series_id}")
    Map<String, Object> findById(Map<String, Object> param);

    /** 某人名下的系列 + 每个系列的条目数（项目系列数项目，素材系列数素材） */
    @Select("SELECT s.series_id, s.user_id, s.name, s.intro, s.is_public, s.created_at, s.updated_at, "
            + "(SELECT COUNT(*) FROM ${child} c WHERE c.series_id = s.series_id AND c.is_deleted = 0) AS item_count "
            + "FROM ${table} s WHERE s.user_id = #{user_id} ORDER BY s.series_id DESC")
    List<Map<String, Object>> listByUser(Map<String, Object> param);

    /** 公开系列（用于个人主页展示某人成体系的作品/素材） */
    @Select("SELECT s.series_id, s.user_id, s.name, s.intro, s.is_public, s.created_at, "
            + "(SELECT COUNT(*) FROM ${child} c WHERE c.series_id = s.series_id AND c.is_deleted = 0) AS item_count "
            + "FROM ${table} s WHERE s.user_id = #{user_id} AND s.is_public = 1 ORDER BY s.series_id DESC")
    List<Map<String, Object>> listPublicByUser(Map<String, Object> param);

    @Update("<script>UPDATE ${table} SET updated_at = NOW()"
            + "<if test='name != null'>, name = #{name}</if>"
            + "<if test='intro != null'>, intro = #{intro}</if>"
            + "<if test='is_public != null'>, is_public = #{is_public}</if>"
            + " WHERE series_id = #{series_id} AND user_id = #{user_id}</script>")
    int update(Map<String, Object> param);

    @Update("UPDATE ${table} SET name = #{name}, intro = #{intro}, is_public = #{is_public}, updated_at = NOW() "
            + "WHERE series_id = #{series_id} AND user_id = #{user_id}")
    int updateFull(Map<String, Object> param);

    @Delete("DELETE FROM ${table} WHERE series_id = #{series_id} AND user_id = #{user_id}")
    int delete(Map<String, Object> param);

    /** 解绑子项：删系列时把项目/素材的 series_id 置空，子项本身不删 */
    @Update("UPDATE ${child} SET series_id = NULL WHERE series_id = #{series_id}")
    int detachChildren(Map<String, Object> param);

    @Update("UPDATE ${child} SET series_id = #{series_id} WHERE ${idCol} = #{id} AND user_id = #{user_id}")
    int attachChild(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM ${table} WHERE user_id = #{user_id}")
    long countByUser(Map<String, Object> param);

    @Select("SELECT series_id, user_id, name FROM ${table} WHERE user_id = #{user_id} AND name = #{name}")
    Map<String, Object> findByUserName(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM ${child} WHERE series_id = #{series_id} AND is_deleted = 0")
    long countChildren(@Param("child") String child, @Param("series_id") long seriesId);
}
