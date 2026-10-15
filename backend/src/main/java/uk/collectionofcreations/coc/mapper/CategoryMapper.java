package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 分类 / 标签 SQL */
@Mapper
public interface CategoryMapper {

    @Select("SELECT category_id, name, slug, sort_order, status FROM category "
            + "WHERE status = 1 ORDER BY sort_order ASC, category_id ASC")
    List<Map<String, Object>> listEnabled();

    @Select("SELECT category_id, name, slug, sort_order, status FROM category ORDER BY sort_order ASC, category_id ASC")
    List<Map<String, Object>> listAll();

    @Insert("INSERT INTO category (name, slug, sort_order, status) VALUES (#{name}, #{slug}, #{sort_order}, 1)")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "category_id", before = false, resultType = Long.class)
    int insertCategory(Map<String, Object> param);

    @Update("UPDATE category SET name = #{name}, sort_order = #{sort_order}, status = #{status} WHERE category_id = #{category_id}")
    int updateCategory(Map<String, Object> param);

    // ---------------- 标签 ----------------

    @Select("SELECT tag_id, name FROM tag WHERE name = #{name}")
    Map<String, Object> findTagByName(@Param("name") String name);

    @Insert("INSERT IGNORE INTO tag (name) VALUES (#{name})")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "tag_id", before = false, resultType = Long.class)
    int insertTagIgnore(Map<String, Object> param);

    @Insert("INSERT IGNORE INTO project_tag (project_id, tag_id) VALUES (#{project_id}, #{tag_id})")
    int insertProjectTag(Map<String, Object> param);

    @Select("SELECT t.tag_id, t.name FROM project_tag pt JOIN tag t ON t.tag_id = pt.tag_id "
            + "WHERE pt.project_id = #{project_id}")
    List<Map<String, Object>> listTagsByProject(@Param("project_id") long projectId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM project_tag WHERE project_id = #{project_id}")
    int deleteProjectTags(@Param("project_id") long projectId);
}
