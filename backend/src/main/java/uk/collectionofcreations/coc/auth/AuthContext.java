package uk.collectionofcreations.coc.auth;

import uk.collectionofcreations.coc.common.BizException;

import java.util.Map;

/**
 * 当前登录用户上下文（ThreadLocal，由 AuthInterceptor 写入）。
 * 匿名访客 = null（不写库、无记录）。
 */
public final class AuthContext {

    private static final ThreadLocal<Map<String, Object>> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Map<String, Object> user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Map<String, Object> getUser() {
        return HOLDER.get();
    }

    public static Long userId() {
        Map<String, Object> u = HOLDER.get();
        if (u == null || u.get("user_id") == null) {
            return null;
        }
        return ((Number) u.get("user_id")).longValue();
    }

    public static boolean isLoggedIn() {
        return userId() != null;
    }

    public static boolean isAdmin() {
        Map<String, Object> u = HOLDER.get();
        if (u == null || u.get("type") == null) {
            return false;
        }
        return ((Number) u.get("type")).intValue() == 3;
    }

    /** 必须登录，否则 401 */
    public static Long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw BizException.unauthorized("请先登录");
        }
        return id;
    }

    /** 必须管理员，否则 403 */
    public static Long requireAdmin() {
        Long id = requireUserId();
        if (!isAdmin()) {
            throw BizException.forbidden("需要管理员权限");
        }
        return id;
    }

    public static String nickname() {
        Map<String, Object> u = HOLDER.get();
        return u == null ? null : (String) u.get("nickname");
    }
}
