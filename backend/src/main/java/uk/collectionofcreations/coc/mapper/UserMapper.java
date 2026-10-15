package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 用户 / 开发者申请相关 SQL。
 */
@Mapper
public interface UserMapper {

    /** 认证用：含 token_version / status */
    @Select("SELECT user_id, qq_account, nickname, avatar_path, type, status, token_version, "
            + "must_change_password, temp_password_expires_at, reward_points, created_at "
            + "FROM `user` WHERE user_id = #{user_id}")
    Map<String, Object> findAuthById(@Param("user_id") long userId);

    /** 登录用（含密码哈希） */
    @Select("SELECT user_id, qq_account, nickname, avatar_path, type, status, token_version, password_hash, "
            + "must_change_password, temp_password_expires_at, cancelled_at, cancel_reason "
            + "FROM `user` WHERE qq_account = #{qq_account}")
    Map<String, Object> findByQq(@Param("qq_account") String qqAccount);

    @Select("SELECT COUNT(*) FROM `user` WHERE qq_account = #{qq_account}")
    long countByQq(@Param("qq_account") String qqAccount);

    @Insert("INSERT INTO `user` (qq_account, password_hash, nickname, type, status, created_at, updated_at) "
            + "VALUES (#{qq_account}, #{password_hash}, #{nickname}, #{type}, #{status}, NOW(), NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "user_id", before = false, resultType = Long.class)
    int insertUser(Map<String, Object> param);

    @Update("UPDATE `user` SET last_login_at = NOW() WHERE user_id = #{user_id}")
    int touchLastLogin(@Param("user_id") long userId);

    @Update("UPDATE `user` SET password_hash = #{password_hash}, must_change_password = 0, "
            + "temp_password_expires_at = NULL, token_version = token_version + 1, updated_at = NOW() "
            + "WHERE user_id = #{user_id}")
    int updatePassword(Map<String, Object> param);

    @Update("UPDATE `user` SET status = #{status}, token_version = token_version + 1, updated_at = NOW() "
            + "WHERE user_id = #{user_id}")
    int updateStatus(Map<String, Object> param);

    @Update("UPDATE `user` SET password_hash = #{password_hash}, must_change_password = 1, "
            + "temp_password_expires_at = DATE_ADD(NOW(), INTERVAL 24 HOUR), token_version = token_version + 1, "
            + "updated_at = NOW() WHERE user_id = #{user_id}")
    int resetPassword(Map<String, Object> param);

    @Update("UPDATE `user` SET status = 3, cancelled_at = NOW(), cancel_reason = #{cancel_reason}, "
            + "token_version = token_version + 1, updated_at = NOW() WHERE user_id = #{user_id}")
    int cancelUser(Map<String, Object> param);

    /** 举报被采纳后给举报人加正反馈积分 */
    @Update("UPDATE `user` SET reward_points = reward_points + #{points} WHERE user_id = #{user_id}")
    int addRewardPoints(Map<String, Object> param);

    @Select("SELECT reward_points FROM `user` WHERE user_id = #{user_id}")
    Integer findRewardPoints(@Param("user_id") long userId);

    /** 个人主页（已注销账号昵称回退） */
    @Select("SELECT user_id, qq_account, nickname, avatar_path, type, status, "
            + "CASE WHEN status = 3 THEN '用户已注销' ELSE nickname END AS display_name, "
            + "CASE WHEN status = 3 THEN 1 ELSE 0 END AS is_cancelled, created_at "
            + "FROM `user` WHERE user_id = #{user_id}")
    Map<String, Object> findPublicProfile(@Param("user_id") long userId);

    // ---------------- 开发者申请 ----------------

    @Insert("INSERT INTO developer_profile (user_id, contact, apply_status, reason, applied_at) "
            + "VALUES (#{user_id}, #{contact}, #{apply_status}, #{reason}, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "developer_profile_id",
            before = false, resultType = Long.class)
    int insertDevProfile(Map<String, Object> param);

    @Select("SELECT dp.*, u.qq_account, u.nickname, CASE WHEN u.status = 3 THEN '用户已注销' ELSE u.nickname END AS display_name "
            + "FROM developer_profile dp JOIN `user` u ON u.user_id = dp.user_id WHERE dp.user_id = #{user_id}")
    Map<String, Object> findDevProfileByUser(@Param("user_id") long userId);

    @Select("<script>"
            + "SELECT dp.developer_profile_id, dp.user_id, dp.contact, dp.apply_status, dp.reason, dp.applied_at, "
            + "dp.reviewed_at, dp.reviewer_id, dp.contribution_value, u.qq_account, u.nickname, u.status AS user_status "
            + "FROM developer_profile dp JOIN `user` u ON u.user_id = dp.user_id "
            + "<where><if test='apply_status != null'> dp.apply_status = #{apply_status}</if></where> "
            + "ORDER BY dp.applied_at DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> listDevApplications(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM developer_profile dp "
            + "<where><if test='apply_status != null'> dp.apply_status = #{apply_status}</if></where></script>")
    long countDevApplications(Map<String, Object> param);

    @Select("SELECT dp.developer_profile_id, dp.user_id, dp.apply_status FROM developer_profile dp "
            + "WHERE dp.developer_profile_id = #{developer_profile_id}")
    Map<String, Object> findDevApplicationById(@Param("developer_profile_id") long id);

    @Update("UPDATE developer_profile SET apply_status = #{apply_status}, reviewed_at = NOW(), "
            + "reviewer_id = #{reviewer_id}, reason = #{reason} WHERE developer_profile_id = #{developer_profile_id}")
    int updateDevProfileStatus(Map<String, Object> param);

    // ---------------- 管理员统计 ----------------

    @Select("SELECT COUNT(*) FROM `user` WHERE type = 2 AND status <> 3")
    long countDevelopers();

    @Select("SELECT COUNT(*) FROM `user` WHERE type = 3")
    long countAdmins();

    @Select("SELECT COUNT(*) FROM `user` WHERE status = 3")
    long countCancelled();

    @Select("<script>SELECT user_id, qq_account, nickname, type, status, reward_points, created_at, last_login_at, "
            + "cancelled_at, cancel_reason FROM `user` WHERE 1=1 "
            + "<if test='status != null'> AND status = #{status}</if>"
            + "<if test='type != null'> AND type = #{type}</if>"
            + " ORDER BY user_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> adminListUsers(Map<String, Object> param);
}
