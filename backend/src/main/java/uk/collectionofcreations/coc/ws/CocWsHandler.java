package uk.collectionofcreations.coc.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import uk.collectionofcreations.coc.auth.JwtUtil;
import uk.collectionofcreations.coc.mapper.UserMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IM WebSocket 处理器：/ws?token=xxx
 * <p>协议（JSON）：
 * <ul>
 *   <li>客户端 → 服务端：{"type":"hello"} / {"type":"ping"}</li>
 *   <li>服务端 → 客户端：{"type":"hello","user_id":..} / {"type":"pong"} /
 *       {"type":"message","session_id":..,"message":{...}}</li>
 * </ul>
 */
@Component
public class CocWsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CocWsHandler.class);
    private static final String ATTR_USER_ID = "userId";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final WsRegistry registry;
    private final ObjectMapper objectMapper;

    public CocWsHandler(JwtUtil jwtUtil, UserMapper userMapper, WsRegistry registry, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryParam(session.getUri() == null ? null : session.getUri().getQuery(), "token");
        Long userId = null;
        if (token != null) {
            Claims claims = jwtUtil.parse(token);
            if (claims != null) {
                long uid = Long.parseLong(claims.getSubject());
                int tv = claims.get("tv") == null ? -1 : ((Number) claims.get("tv")).intValue();
                Map<String, Object> user = userMapper.findAuthById(uid);
                if (user != null
                        && ((Number) user.get("token_version")).intValue() == tv
                        && ((Number) user.get("status")).intValue() != 3
                        && ((Number) user.get("status")).intValue() != 2) {
                    userId = uid;
                }
            }
        }
        if (userId == null) {
            log.info("WS 拒绝未登录连接: {}", session.getId());
            session.close(new CloseStatus(4001, "invalid token"));
            return;
        }
        session.getAttributes().put(ATTR_USER_ID, userId);
        registry.add(userId, session);

        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("type", "hello");
        hello.put("user_id", userId);
        hello.put("online", registry.isOnline(userId));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(hello)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String type = null;
        try {
            Map<?, ?> m = objectMapper.readValue(payload, Map.class);
            Object t = m.get("type");
            type = t == null ? null : String.valueOf(t);
        } catch (Exception ignored) {
            // 非 JSON 一律忽略
        }
        if ("ping".equals(type)) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        } else if ("hello".equals(type)) {
            Object uid = session.getAttributes().get(ATTR_USER_ID);
            session.sendMessage(new TextMessage("{\"type\":\"hello\",\"user_id\":" + uid + "}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object uid = session.getAttributes().get(ATTR_USER_ID);
        if (uid instanceof Long id) {
            registry.remove(id, session);
        }
    }

    private String queryParam(String query, String name) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && name.equals(kv.substring(0, i))) {
                return java.net.URLDecoder.decode(kv.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
