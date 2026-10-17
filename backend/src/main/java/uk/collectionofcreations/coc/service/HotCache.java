package uk.collectionofcreations.coc.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简 TTL 内存缓存 —— 给「读多写少」的公共接口用（首页作品流、项目排行、分类表）。
 * <p>设计取舍：项目规模小、机器有 32G 内存，直接空间换时间；不引第三方缓存依赖，
 * 用 ConcurrentHashMap + 过期时间戳即可。写入方（投稿 / 审核 / 删除 / 下架）调用
 * {@link #invalidate(String)} 主动清掉相关前缀，保证用户提交后立刻能看到。
 * <p>注意：只缓存「与登录用户无关」的公共数据，别把带 user 维度的响应塞进来。
 */
public final class HotCache {

    private HotCache() {
    }

    private static final class Entry {
        final Object value;
        final long expireAt;

        Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private static final Map<String, Entry> MAP = new ConcurrentHashMap<>();
    /** 键上限：超过就整体清空（极端情况下退化为无缓存，不会吃爆内存） */
    private static final int MAX_KEYS = 5000;

    public static Object get(String key) {
        Entry e = MAP.get(key);
        if (e == null) {
            return null;
        }
        if (e.expireAt < System.currentTimeMillis()) {
            MAP.remove(key);
            return null;
        }
        return e.value;
    }

    public static void put(String key, Object value, long ttlMillis) {
        if (MAP.size() > MAX_KEYS) {
            MAP.clear();
        }
        MAP.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    /** 清掉指定前缀的所有键，例如 invalidate("projects:") */
    public static void invalidate(String prefix) {
        MAP.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clear() {
        MAP.clear();
    }

    public static int size() {
        return MAP.size();
    }
}
