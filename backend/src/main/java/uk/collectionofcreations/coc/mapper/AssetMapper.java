package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 素材（图片 / GIF / 视频 / 音频 / JSON） */
@Mapper
public interface AssetMapper {

    @Insert("INSERT INTO asset (user_id, series_id, name, file_name, asset_type, mime, file_size, sha256, "
            + "storage_path, is_public, is_deleted, created_at, updated_at) "
            + "VALUES (#{user_id}, #{series_id}, #{name}, #{file_name}, #{asset_type}, #{mime}, #{file_size}, "
            + "#{sha256}, #{storage_path}, #{is_public}, 0, NOW(), NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "asset_id", before = false, resultType = Long.class)
    int insert(Map<String, Object> param);

    @Update("UPDATE asset SET storage_path = #{storage_path}, file_size = #{file_size}, mime = #{mime}, updated_at = NOW() "
            + "WHERE asset_id = #{asset_id}")
    int updateStored(Map<String, Object> param);

    @Select("SELECT asset_id, user_id, series_id, name, file_name, asset_type, mime, file_size, sha256, "
            + "storage_path, is_public, is_deleted, created_at, updated_at FROM asset WHERE asset_id = #{asset_id}")
    Map<String, Object> findById(@Param("asset_id") long assetId);

    /** 素材广场：公开且未删除 */
    @Select("<script>"
            + "SELECT a.asset_id, a.user_id, a.series_id, a.name, a.file_name, a.asset_type, a.mime, a.file_size, "
            + "a.storage_path, a.is_public, a.created_at, "
            + "CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS author_nickname, u.avatar_path AS author_avatar, "
            + "s.name AS series_name "
            + "FROM asset a JOIN `user` u ON u.user_id = a.user_id "
            + "LEFT JOIN asset_series s ON s.series_id = a.series_id "
            + "WHERE a.is_deleted = 0 AND a.is_public = 1 "
            + "<if test='asset_type != null and asset_type != \"\"'> AND a.asset_type = #{asset_type}</if>"
            + "<if test='series_id != null'> AND a.series_id = #{series_id}</if>"
            + "<if test='user_id != null'> AND a.user_id = #{user_id}</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND (a.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.file_name LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + " ORDER BY a.asset_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> listPublic(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM asset a WHERE a.is_deleted = 0 AND a.is_public = 1 "
            + "<if test='asset_type != null and asset_type != \"\"'> AND a.asset_type = #{asset_type}</if>"
            + "<if test='series_id != null'> AND a.series_id = #{series_id}</if>"
            + "<if test='user_id != null'> AND a.user_id = #{user_id}</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND (a.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.file_name LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "</script>")
    long countPublic(Map<String, Object> param);

    /** 我的素材：包含未公开 */
    @Select("<script>"
            + "SELECT a.asset_id, a.user_id, a.series_id, a.name, a.file_name, a.asset_type, a.mime, a.file_size, "
            + "a.storage_path, a.is_public, a.created_at, s.name AS series_name "
            + "FROM asset a LEFT JOIN asset_series s ON s.series_id = a.series_id "
            + "WHERE a.user_id = #{user_id} AND a.is_deleted = 0 "
            + "<if test='asset_type != null and asset_type != \"\"'> AND a.asset_type = #{asset_type}</if>"
            + "<if test='series_id != null'> AND a.series_id = #{series_id}</if>"
            + "<if test='no_series == true'> AND a.series_id IS NULL</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND a.name LIKE CONCAT('%', #{keyword}, '%')</if>"
            + " ORDER BY a.asset_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> listMine(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM asset a WHERE a.user_id = #{user_id} AND a.is_deleted = 0 "
            + "<if test='asset_type != null and asset_type != \"\"'> AND a.asset_type = #{asset_type}</if>"
            + "<if test='series_id != null'> AND a.series_id = #{series_id}</if>"
            + "<if test='no_series == true'> AND a.series_id IS NULL</if>"
            + "<if test='keyword != null and keyword != \"\"'> AND a.name LIKE CONCAT('%', #{keyword}, '%')</if>"
            + "</script>")
    long countMine(Map<String, Object> param);

    @Update("<script>UPDATE asset SET name = #{name}, updated_at = NOW()"
            + "<if test='is_public != null'>, is_public = #{is_public}</if>"
            + "<if test='clear_series == true'>, series_id = NULL</if>"
            + "<if test='series_id != null'>, series_id = #{series_id}</if>"
            + " WHERE asset_id = #{asset_id} AND user_id = #{user_id}</script>")
    int updateMeta(Map<String, Object> param);

    @Update("UPDATE asset SET is_deleted = 1, updated_at = NOW() WHERE asset_id = #{asset_id} AND user_id = #{user_id}")
    int softDelete(Map<String, Object> param);

    @Select("SELECT COUNT(*) FROM asset WHERE user_id = #{user_id} AND is_deleted = 0")
    long countByUser(@Param("user_id") long userId);

    @Select("SELECT COUNT(*) FROM asset WHERE user_id = #{user_id} AND is_deleted = 0 AND is_public = 1")
    long countPublicByUser(@Param("user_id") long userId);
}
