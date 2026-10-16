package uk.collectionofcreations.coc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 找回密码申请 / 协议版本 */
@Mapper
public interface PasswordResetMapper {

    @Insert("INSERT INTO password_reset_apply (qq_account, contact, note, status, applied_at) "
            + "VALUES (#{qq_account}, #{contact}, #{note}, 0, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "apply_id", before = false, resultType = Long.class)
    int insertApply(Map<String, Object> param);

    @Select("<script>SELECT a.apply_id, a.qq_account, a.contact, a.note, a.status, a.temp_password, a.reject_reason, "
            + "a.applied_at, a.handled_by, a.handled_at, u.user_id, u.nickname, u.status AS user_status "
            + "FROM password_reset_apply a LEFT JOIN `user` u ON u.qq_account = a.qq_account "
            + "<where><if test='status != null'> a.status = #{status}</if></where>"
            + " ORDER BY a.apply_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> listApplies(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM password_reset_apply "
            + "<where><if test='status != null'> status = #{status}</if></where></script>")
    long countApplies(Map<String, Object> param);

    @Update("UPDATE password_reset_apply SET status = 1, temp_password = #{temp_password}, "
            + "handled_by = #{handled_by}, handled_at = NOW() WHERE apply_id = #{apply_id}")
    int markHandled(Map<String, Object> param);

    /** 驳回找回申请：status 置 2，保留记录（日志缓存，不物理删除） */
    @Update("UPDATE password_reset_apply SET status = 2, reject_reason = #{reject_reason}, "
            + "handled_by = #{handled_by}, handled_at = NOW() WHERE apply_id = #{apply_id} AND status = 0")
    int markRejected(Map<String, Object> param);

    @Select("SELECT apply_id, qq_account, contact, note, status, temp_password, reject_reason, "
            + "applied_at, handled_by, handled_at FROM password_reset_apply WHERE apply_id = #{apply_id}")
    Map<String, Object> findApply(@Param("apply_id") long applyId);

    @Select("SELECT agreement_version_id, version_no, title, content_path, is_current, published_at "
            + "FROM agreement_version WHERE is_current = 1 ORDER BY published_at DESC LIMIT 1")
    Map<String, Object> findCurrentAgreement();

    @Select("SELECT agreement_version_id, version_no, title, content_path, is_current, published_at "
            + "FROM agreement_version ORDER BY agreement_version_id DESC")
    List<Map<String, Object>> listAgreements();

    @Select("SELECT agreement_version_id FROM agreement_version WHERE is_current = 1 LIMIT 1")
    Long findCurrentAgreementId();
}
