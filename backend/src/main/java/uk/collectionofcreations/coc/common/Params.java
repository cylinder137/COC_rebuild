package uk.collectionofcreations.coc.common;

import java.util.Map;

/**
 * 请求体（Map）取值辅助：统一空值处理，避免各 controller 重复判空。
 */
public final class Params {

    private Params() {
    }

    public static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    public static String str(Map<String, Object> body, String key, String def) {
        String s = str(body, key);
        return s == null ? def : s;
    }

    public static String requireStr(Map<String, Object> body, String key, String label) {
        String s = str(body, key);
        if (s == null) {
            throw BizException.badRequest(label + "不能为空");
        }
        return s;
    }

    public static Long lng(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest(label(key) + "格式不正确");
        }
    }

    public static Long requireLng(Map<String, Object> body, String key, String label) {
        Long v = lng(body, key);
        if (v == null) {
            throw BizException.badRequest(label + "不能为空");
        }
        return v;
    }

    public static Integer integer(Map<String, Object> body, String key, Integer def) {
        Object v = body == null ? null : body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean bool(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    /** 分页：page 从 1 开始 */
    public static int page(Map<String, Object> q) {
        int p = integer(q, "page", 1);
        return p < 1 ? 1 : p;
    }

    public static int size(Map<String, Object> q, int def, int max) {
        int s = integer(q, "size", def);
        if (s < 1) {
            s = def;
        }
        return Math.min(s, max);
    }

    public static int offset(int page, int size) {
        return (page - 1) * size;
    }

    private static String label(String key) {
        return key;
    }
}
