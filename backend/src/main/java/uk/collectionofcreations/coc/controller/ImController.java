package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.service.ImService;

import java.util.List;
import java.util.Map;

/**
 * IM：会话列表 / 消息（cursor 分页 20）/ 发送 / 已读 / 未读汇总。
 * <p>实时推送走 WebSocket：ws://host:8090/ws?token=xxx
 */
@RestController
@RequestMapping("/api/v1/im")
public class ImController {

    private final ImService imService;

    public ImController(ImService imService) {
        this.imService = imService;
    }

    /** 会话列表（单聊 + 我参与的项目群） */
    @GetMapping("/conversations")
    public ApiResponse<List<Map<String, Object>>> conversations() {
        return ApiResponse.ok(imService.conversations());
    }

    /** 打开/创建单聊会话 */
    @PostMapping("/conversations/direct")
    public ApiResponse<Map<String, Object>> openDirect(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(imService.openDirect(body));
    }

    /** 消息列表：cursor 分页，每页 20 */
    @GetMapping("/conversations/{im_session_id}/messages")
    public ApiResponse<Map<String, Object>> messages(@PathVariable("im_session_id") long sessionId,
                                                     @RequestParam(value = "cursor", required = false) Long cursor) {
        return ApiResponse.ok(imService.messages(sessionId, cursor));
    }

    /** 发送消息 */
    @PostMapping("/conversations/{im_session_id}/messages")
    public ApiResponse<Map<String, Object>> send(@PathVariable("im_session_id") long sessionId,
                                                 @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(imService.send(sessionId, body));
    }

    /** 已读上报 */
    @PostMapping("/conversations/{im_session_id}/read")
    public ApiResponse<Map<String, Object>> read(@PathVariable("im_session_id") long sessionId) {
        return ApiResponse.ok(imService.read(sessionId));
    }

    /** 未读汇总 */
    @GetMapping("/unread")
    public ApiResponse<Map<String, Object>> unread() {
        return ApiResponse.ok(imService.unread());
    }
}
