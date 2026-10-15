package uk.collectionofcreations.coc.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 在线连接注册表：userId -> sessions。
 */
@Component
public class WsRegistry {

    private final Map<Long, Set<WebSocketSession>> online = new ConcurrentHashMap<>();

    public void add(long userId, WebSocketSession session) {
        online.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(long userId, WebSocketSession session) {
        Set<WebSocketSession> set = online.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                online.remove(userId);
            }
        }
    }

    public boolean isOnline(long userId) {
        Set<WebSocketSession> set = online.get(userId);
        return set != null && !set.isEmpty();
    }

    /** 给指定用户的所有连接推送文本消息 */
    public void sendToUser(long userId, String text) {
        Set<WebSocketSession> set = online.get(userId);
        if (set == null) {
            return;
        }
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(text));
                    }
                }
            } catch (Exception ignored) {
                // 单条推送失败不影响其他连接
            }
        }
    }
}
