package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 分片上传会话 / 打包任务 */
@Mapper
public interface UploadMapper {

    @Insert("INSERT INTO upload_session (user_id, project_id, file_name, file_size, sha256, chunk_size, "
            + "total_chunks, uploaded_chunks, status, storage_path, instant_ref_project_file_id, created_at, updated_at) "
            + "VALUES (#{user_id}, #{project_id}, #{file_name}, #{file_size}, #{sha256}, #{chunk_size}, "
            + "#{total_chunks}, #{uploaded_chunks}, #{status}, #{storage_path}, #{instant_ref_project_file_id}, NOW(), NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "upload_session_id", before = false, resultType = Long.class)
    int insertSession(Map<String, Object> param);

    @Select("SELECT upload_session_id, user_id, project_id, file_name, file_size, sha256, chunk_size, total_chunks, "
            + "uploaded_chunks, status, storage_path, instant_ref_project_file_id, created_at, updated_at "
            + "FROM upload_session WHERE upload_session_id = #{upload_session_id}")
    Map<String, Object> findSession(@Param("upload_session_id") long uploadSessionId);

    @Update("UPDATE upload_session SET uploaded_chunks = #{uploaded_chunks}, updated_at = NOW() "
            + "WHERE upload_session_id = #{upload_session_id}")
    int updateChunks(Map<String, Object> param);

    @Update("UPDATE upload_session SET status = #{status}, storage_path = #{storage_path}, updated_at = NOW() "
            + "WHERE upload_session_id = #{upload_session_id}")
    int updateSessionDone(Map<String, Object> param);

    @Update("UPDATE upload_session SET project_id = #{project_id}, updated_at = NOW() "
            + "WHERE upload_session_id = #{upload_session_id}")
    int updateSessionProject(Map<String, Object> param);

    // ---------------- 打包任务 ----------------

    @Insert("INSERT INTO build_task (project_file_id, status, log_path, output_path, message, created_at, finished_at) "
            + "VALUES (#{project_file_id}, #{status}, #{log_path}, #{output_path}, #{message}, NOW(), #{finished_at})")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "build_task_id", before = false, resultType = Long.class)
    int insertBuildTask(Map<String, Object> param);

    @Update("UPDATE build_task SET status = #{status}, output_path = #{output_path}, message = #{message}, "
            + "finished_at = NOW() WHERE build_task_id = #{build_task_id}")
    int updateBuildTask(Map<String, Object> param);

    @Select("SELECT build_task_id, project_file_id, status, log_path, output_path, message, created_at, finished_at "
            + "FROM build_task WHERE project_file_id = #{project_file_id} ORDER BY build_task_id DESC LIMIT 1")
    Map<String, Object> findBuildTaskByFile(@Param("project_file_id") long projectFileId);

    @Select("SELECT bt.build_task_id, bt.project_file_id, bt.status, bt.message, bt.created_at FROM build_task bt "
            + "WHERE bt.status = 0 ORDER BY bt.build_task_id ASC LIMIT #{limit}")
    List<Map<String, Object>> listPendingTasks(Map<String, Object> param);
}
