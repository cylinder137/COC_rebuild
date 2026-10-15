package uk.collectionofcreations.coc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.auth.JwtUtil;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.PasswordResetMapper;
import uk.collectionofcreations.coc.mapper.UserMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证相关业务：开发者申请、登录、改密、找回申请。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final PasswordResetMapper passwordResetMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, PasswordResetMapper passwordResetMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordResetMapper = passwordResetMapper;
        this.jwtUtil = jwtUtil;
    }

    /** 开发者申请：建 user(status=0) + developer_profile(0)，需管理员审核通过后才能登录 */
    @Transactional
    public Map<String, Object> devApply(Map<String, Object> body) {
        String qq = Params.requireStr(body, "qq_account", "QQ号");
        String password = Params.requireStr(body, "password", "密码");
        String nickname = Params.requireStr(body, "nickname", "昵称");
        String contact = Params.str(body, "contact");

        if (!qq.matches("\\d{5,15}")) {
            throw BizException.badRequest("QQ号须为 5-15 位数字");
        }
        if (password.length() < 6 || password.length() > 64) {
            throw BizException.badRequest("密码长度须为 6-64 位");
        }
        if (nickname.length() > 50) {
            throw BizException.badRequest("昵称过长");
        }
        if (userMapper.countByQq(qq) > 0) {
            throw new BizException(1002, "该 QQ 号已提交过申请或已注册", 409);
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("qq_account", qq);
        user.put("password_hash", encoder.encode(password));
        user.put("nickname", nickname);
        user.put("type", 2);
        user.put("status", 0);
        userMapper.insertUser(user);
        Long userId = ((Number) user.get("user_id")).longValue();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user_id", userId);
        profile.put("contact", contact);
        profile.put("apply_status", 0);
        profile.put("reason", "开发者申请");
        userMapper.insertDevProfile(profile);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("qq_account", qq);
        data.put("status", 0);
        data.put("apply_status", 0);
        data.put("message", "申请已提交，等待管理员审核通过后即可登录");
        return data;
    }

    /** 登录：仅 status=1 可登录；0 审核中、2 禁用、3 已注销 */
    @Transactional
    public Map<String, Object> login(Map<String, Object> body) {
        String qq = Params.requireStr(body, "qq_account", "QQ号");
        String password = Params.requireStr(body, "password", "密码");

        Map<String, Object> user = userMapper.findByQq(qq);
        if (user == null || !encoder.matches(password, String.valueOf(user.get("password_hash")))) {
            throw new BizException(1001, "账号或密码错误", 401);
        }
        int status = ((Number) user.get("status")).intValue();
        if (status == 0) {
            throw new BizException(1003, "账号审核中，请等待管理员审核", 403);
        }
        if (status == 2) {
            throw new BizException(1004, "账号已被禁用，请联系管理员", 403);
        }
        if (status == 3) {
            throw new BizException(1005, "账号已注销", 403);
        }
        int mustChange = ((Number) user.get("must_change_password")).intValue();
        if (mustChange == 1 && isTempExpired(user.get("temp_password_expires_at"))) {
            throw new BizException(1006, "临时密码已过期（24 小时有效），请重新联系管理员", 403);
        }

        long userId = ((Number) user.get("user_id")).longValue();
        int type = ((Number) user.get("type")).intValue();
        int tv = ((Number) user.get("token_version")).intValue();
        String token = jwtUtil.createToken(userId, type, tv);
        userMapper.touchLastLogin(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("must_change_password", mustChange == 1);
        data.put("user", briefUser(user));
        return data;
    }

    /** 当前登录用户 */
    public Map<String, Object> me() {
        Long uid = AuthContext.requireUserId();
        Map<String, Object> user = userMapper.findAuthById(uid);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>(briefUser(user));
        data.put("is_admin", ((Number) user.get("type")).intValue() == 3);
        data.put("must_change_password", ((Number) user.get("must_change_password")).intValue() == 1);
        Map<String, Object> profile = userMapper.findDevProfileByUser(uid);
        if (profile != null) {
            data.put("apply_status", profile.get("apply_status"));
        }
        return data;
    }

    /** 改密：临时密码首登必须改；改密后 token_version+1，返回新 token */
    @Transactional
    public Map<String, Object> changePassword(Map<String, Object> body) {
        Long uid = AuthContext.requireUserId();
        String oldPwd = Params.requireStr(body, "old_password", "原密码");
        String newPwd = Params.requireStr(body, "new_password", "新密码");
        if (newPwd.length() < 6 || newPwd.length() > 64) {
            throw BizException.badRequest("新密码长度须为 6-64 位");
        }
        if (newPwd.equals(oldPwd)) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        Map<String, Object> user = userMapper.findAuthById(uid);
        Map<String, Object> full = userMapper.findByQq(String.valueOf(user.get("qq_account")));
        if (full == null || !encoder.matches(oldPwd, String.valueOf(full.get("password_hash")))) {
            throw BizException.badRequest("原密码不正确");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("user_id", uid);
        p.put("password_hash", encoder.encode(newPwd));
        userMapper.updatePassword(p);

        Map<String, Object> after = userMapper.findAuthById(uid);
        int type = ((Number) after.get("type")).intValue();
        int tv = ((Number) after.get("token_version")).intValue();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtUtil.createToken(uid, type, tv));
        data.put("must_change_password", false);
        data.put("message", "密码修改成功，旧登录凭证已失效");
        return data;
    }

    /** 忘记密码 → 提交找回申请（管理员人工处理） */
    @Transactional
    public Map<String, Object> forgotPassword(Map<String, Object> body) {
        String qq = Params.requireStr(body, "qq_account", "QQ号");
        String contact = Params.str(body, "contact");
        String note = Params.str(body, "note");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("qq_account", qq);
        p.put("contact", contact);
        p.put("note", note);
        passwordResetMapper.insertApply(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apply_id", p.get("apply_id"));
        data.put("message", "找回申请已提交，请联系管理员 QQ 3254328344（或用注册时的 QQ 号私聊）核验身份");
        return data;
    }

    private boolean isTempExpired(Object expiresAt) {
        if (expiresAt == null) {
            return true;
        }
        try {
            LocalDateTime t = (expiresAt instanceof java.sql.Timestamp ts)
                    ? ts.toLocalDateTime()
                    : LocalDateTime.parse(String.valueOf(expiresAt).replace(' ', 'T'));
            return t.isBefore(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("解析临时密码有效期失败: {}", expiresAt);
            return false;
        }
    }

    private Map<String, Object> briefUser(Map<String, Object> user) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("user_id", user.get("user_id"));
        u.put("qq_account", user.get("qq_account"));
        u.put("nickname", user.get("nickname"));
        u.put("avatar_path", user.get("avatar_path"));
        u.put("type", user.get("type"));
        return u;
    }
}
