package uk.collectionofcreations.coc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.ImMapper;
import uk.collectionofcreations.coc.mapper.NotificationMapper;
import uk.collectionofcreations.coc.mapper.UserMapper;
import uk.collectionofcreations.coc.ws.WsRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 业务：会话列表、消息（cursor 分页每页 20）、发送、已读、未读汇总 + WebSocket 推送。
 */
@Service
public class ImService {

    /** 每页固定 20 条（契约 §3.5） */
    public static final int PAGE_SIZE = 20;

    private final ImMapper imMapper;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final WsRegistry wsRegistry;
    private final ObjectMapper objectMapper;

    public ImService(ImMapper imMapper, UserMapper userMapper, NotificationMapper notificationMapper,
                     WsRegistry wsRegistry, ObjectMapper objectMapper) {
        this.imMapper = imMapper;
        this.userMapper = userMapper;
        this.notificationMapper = notificationMapper;
        this.wsRegistry = wsRegistry;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> conversations() {
        long uid = AuthContext.requireUserId();
        return imMapper.listSessionsForUser(uid);
    }

    /** 打开/创建单聊会话（契约未列，前端需要入口） */
    @Transactional
    public Map<String, Object> openDirect(Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        long peer = Params.requireLng(body, "user_id", "对方用户ID");
        if (peer == uid) {
            throw BizException.badRequest("不能和自己私聊");
        }
        if (userMapper.findPublicProfile(peer) == null) {
            throw BizException.notFound("对方用户不存在");
        }
        Map<String, Object> session = imMapper.findDirectSession(uid, peer);
        long sessionId;
        if (session == null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("kind", 1);
            s.put("project_id", null);
            s.put("user_a_id", uid);
            s.put("user_b_id", peer);
            s.put("title", null);
            imMapper.insertSession(s);
            sessionId = ((Number) s.get("im_session_id")).longValue();
            addMember(sessionId, uid);
            addMember(sessionId, peer);
        } else {
            sessionId = ((Number) session.get("im_session_id")).longValue();
            addMember(sessionId, uid);
            addMember(sessionId, peer);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("im_session_id", sessionId);
        data.put("kind", 1);
        data.put("peer_user_id", peer);
        return data;
    }

    /** 消息列表：cursor 分页，每页 20，返回 next_cursor */
    public Map<String, Object> messages(long sessionId, Long cursor) {
        long uid = AuthContext.requireUserId();
        requireMember(sessionId, uid);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("session_id", sessionId);
        q.put("cursor", cursor);
        q.put("limit", PAGE_SIZE);
        List<Map<String, Object>> rows = imMapper.listMessages(q);
        Long nextCursor = null;
        if (rows.size() == PAGE_SIZE) {
            Object last = rows.get(rows.size() - 1).get("im_message_id");
            nextCursor = ((Number) last).longValue();
        }
        List<Map<String, Object>> ordered = new ArrayList<>(rows);
        Collections.reverse(ordered);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", ordered);
        data.put("page_size", PAGE_SIZE);
        data.put("next_cursor", nextCursor);
        data.put("has_more", nextCursor != null);
        return data;
    }

    /** 发送消息（落库 + WS 推送给会话其他成员 + 站内通知） */
    @Transactional
    public Map<String, Object> send(long sessionId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        requireMember(sessionId, uid);
        String content = Params.requireStr(body, "content", "消息内容");
        if (content.length() > 2000) {
            throw BizException.badRequest("消息过长（<=2000 字）");
        }
        Integer contentType = Params.integer(body, "content_type", 1);
        String clientMsgId = Params.str(body, "client_msg_id");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("session_id", sessionId);
        msg.put("sender_id", uid);
        msg.put("content", content);
        msg.put("content_type", contentType);
        msg.put("client_msg_id", clientMsgId);
        imMapper.insertMessage(msg);
        long messageId = ((Number) msg.get("im_message_id")).longValue();

        String preview = content.length() > 60 ? content.substring(0, 60) : content;
        Map<String, Object> sp = new LinkedHashMap<>();
        sp.put("im_session_id", sessionId);
        sp.put("preview", preview);
        imMapper.updateSessionPreview(sp);

        Map<String, Object> read = new LinkedHashMap<>();
        read.put("session_id", sessionId);
        read.put("user_id", uid);
        read.put("message_id", messageId);
        imMapper.updateLastRead(read);

        Map<String, Object> message = imMapper.findMessageById(messageId);
        pushToMembers(sessionId, uid, message);
        return message;
    }

    /** 已读上报 */
    @Transactional
    public Map<String, Object> read(long sessionId) {
        long uid = AuthContext.requireUserId();
        requireMember(sessionId, uid);
        long max = imMapper.maxMessageId(sessionId);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("session_id", sessionId);
        p.put("user_id", uid);
        p.put("message_id", max);
        imMapper.updateLastRead(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("im_session_id", sessionId);
        data.put("last_read_message_id", max);
        return data;
    }

    /** 未读汇总 */
    public Map<String, Object> unread() {
        long uid = AuthContext.requireUserId();
        List<Map<String, Object>> sessions = imMapper.listSessionsForUser(uid);
        List<Map<String, Object>> bySession = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> s : sessions) {
            long unread = s.get("unread_count") == null ? 0 : ((Number) s.get("unread_count")).longValue();
            if (unread > 0) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("im_session_id", s.get("im_session_id"));
                x.put("unread_count", unread);
                bySession.add(x);
                total += unread;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_unread", total);
        data.put("sessions", bySession);
        return data;
    }

    private void pushToMembers(long sessionId, long senderId, Map<String, Object> message) {
        List<Long> members = imMapper.listMemberIds(sessionId);
        String payload;
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "message");
            frame.put("session_id", sessionId);
            frame.put("message", message);
            payload = objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            return;
        }
        String senderName = String.valueOf(message.get("sender_nickname"));
        for (Long m : members) {
            if (m == senderId) {
                continue;
            }
            wsRegistry.sendToUser(m, payload);
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("user_id", m);
            n.put("type", "im");
            n.put("title", senderName + " 给你发了一条消息");
            n.put("content", String.valueOf(message.get("content")));
            n.put("link", "/im/" + sessionId);
            notificationMapper.insert(n);
        }
    }

    private void addMember(long sessionId, long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", sessionId);
        m.put("user_id", userId);
        imMapper.addMember(m);
    }

    private void requireMember(long sessionId, long uid) {
        Map<String, Object> session = imMapper.findSessionById(sessionId);
        if (session == null) {
            throw BizException.notFound("会话不存在");
        }
        if (imMapper.isMember(sessionId, uid) == 0) {
            long a = session.get("user_a_id") == null ? -1 : ((Number) session.get("user_a_id")).longValue();
            long b = session.get("user_b_id") == null ? -1 : ((Number) session.get("user_b_id")).longValue();
            if (uid != a && uid != b) {
                throw BizException.forbidden("你不在该会话中");
            }
            addMember(sessionId, uid);
        }
    }
}
