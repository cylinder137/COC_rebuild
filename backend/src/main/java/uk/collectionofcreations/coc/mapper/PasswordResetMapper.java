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

    @Select("<script>SELECT apply_id, qq_account, contact, note, status, temp_password, applied_at, handled_by, handled_at "
            + "FROM password_reset_apply "
            + "<where><if test='status != null'> status = #{status}</if></where>"
            + " ORDER BY apply_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> listApplies(Map<String, Object> param);

    @Select("<script>SELECT COUNT(*) FROM password_reset_apply "
            + "<where><if test='status != null'> status = #{status}</if></where></script>")
    long countApplies(Map<String, Object> param);

    @Update("UPDATE password_reset_apply SET status = 1, temp_password = #{temp_password}, "
            + "handled_by = #{handled_by}, handled_at = NOW() WHERE apply_id = #{apply_id}")
    int markHandled(Map<String, Object> param);

    @Select("SELECT agreement_version_id, version_no, title, content_path, is_current, published_at "
            + "FROM agreement_version WHERE is_current = 1 ORDER BY published_at DESC LIMIT 1")
    Map<String, Object> findCurrentAgreement();

    @Select("SELECT agreement_version_id, version_no, title, content_path, is_current, published_at "
            + "FROM agreement_version ORDER BY agreement_version_id DESC")
    List<Map<String, Object>> listAgreements();

    @Select("SELECT agreement_version_id FROM agreement_version WHERE is_current = 1 LIMIT 1")
    Long findCurrentAgreementId();
}
