package uk.collectionofcreations.coc.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.service.AdminService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端（全部要求管理员，路径 /api/v1/admin/**）。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ---- 项目 ----

    @GetMapping("/projects")
    public ApiResponse<Map<String, Object>> projects(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(adminService.listProjects(query));
    }

    /** 待审队列 */
    @GetMapping("/reviews/queue")
    public ApiResponse<Map<String, Object>> reviewQueue(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(adminService.reviewQueue(query));
    }

    /** 审核通过 */
    @PostMapping("/reviews/{project_version_id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable("project_version_id") long versionId,
                                                    @RequestBody(required = false) Map<String, Object> body,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(adminService.approve(versionId,
                body == null ? null : Params.str(body, "reason"), FileController.clientIp(request)));
    }

    /** 审核驳回 */
    @PostMapping("/reviews/{project_version_id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable("project_version_id") long versionId,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(adminService.reject(versionId,
                body == null ? null : Params.str(body, "reason"), FileController.clientIp(request)));
    }

    /** 下架 */
    @PostMapping("/projects/{project_id}/down")
    public ApiResponse<Map<String, Object>> down(@PathVariable("project_id") long projectId,
                                                 @RequestBody(required = false) Map<String, Object> body,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(adminService.down(projectId,
                body == null ? null : Params.str(body, "reason"), FileController.clientIp(request)));
    }

    /** 恢复上架 */
    @PostMapping("/projects/{project_id}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable("project_id") long projectId,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(adminService.restore(projectId, FileController.clientIp(request)));
    }

    /** 删除（软删 DB + 物理删代码 + 审计） */
    @DeleteMapping("/projects/{project_id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable("project_id") long projectId,
                                                   @RequestParam(value = "reason", required = false) String reason,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(adminService.delete(projectId, reason, FileController.clientIp(request)));
    }

    /** 服务器绝对存储路径（仅管理员，写审计） */
    @GetMapping("/projects/{project_id}/storage")
    public ApiResponse<Map<String, Object>> storage(@PathVariable("project_id") long projectId,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(adminService.projectStorage(projectId, FileController.clientIp(request)));
    }

    // ---- 评论 ----

    @GetMapping("/comments")
    public ApiResponse<List<Map<String, Object>>> comments(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(adminService.listComments(query));
    }

    /** 删除任意评论 */
    @DeleteMapping("/comments/{comment_id}")
    public ApiResponse<Map<String, Object>> deleteComment(@PathVariable("comment_id") long commentId,
                                                          HttpServletRequest request) {
        return ApiResponse.ok(adminService.deleteComment(commentId, FileController.clientIp(request)));
    }

    // ---- 开发者申请 ----

    @GetMapping("/developer-applications")
    public ApiResponse<Map<String, Object>> devApplications(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(adminService.devApplications(query));
    }

    @PostMapping("/developer-applications/{developer_profile_id}/approve")
    public ApiResponse<Map<String, Object>> approveDev(@PathVariable("developer_profile_id") long id,
                                                       @RequestBody(required = false) Map<String, Object> body,
                                                       HttpServletRequest request) {
        return ApiResponse.ok(adminService.approveDeveloper(id,
                body == null ? null : Params.str(body, "reason"), FileController.clientIp(request)));
    }

    @PostMapping("/developer-applications/{developer_profile_id}/reject")
    public ApiResponse<Map<String, Object>> rejectDev(@PathVariable("developer_profile_id") long id,
                                                      @RequestBody(required = false) Map<String, Object> body,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(adminService.rejectDeveloper(id,
                body == null ? null : Params.str(body, "reason"), FileController.clientIp(request)));
    }

    // ---- 举报 ----

    @GetMapping("/reports")
    public ApiResponse<Map<String, Object>> reports(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(adminService.reports(query));
    }

    /** 处理举报：action=resolve/reject，结果通知举报人（resolve 加正反馈积分） */
    @PostMapping("/reports/{report_id}/handle")
    public ApiResponse<Map<String, Object>> handleReport(@PathVariable("report_id") long reportId,
                                                         @RequestBody Map<String, Object> body,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(adminService.handleReport(reportId, Params.str(body, "action", "resolve"),
                Params.str(body, "result"), FileController.clientIp(request)));
    }

    // ---- 找回密码 / 注销 ----

    @GetMapping("/password-resets")
    public ApiResponse<Map<String, Object>> passwordResets(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(adminService.passwordResets(query));
    }

    /** 生成一次性临时密码（24h 有效，首登强制改密，旧 token 立即失效） */
    @PostMapping("/users/{user_id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable("user_id") long userId,
                                                          @RequestBody(required = false) Map<String, Object> body,
                                                          HttpServletRequest request) {
        Long applyId = body == null ? null : Params.lng(body, "apply_id");
        return ApiResponse.ok(adminService.resetPassword(userId, applyId, FileController.clientIp(request)));
    }

    /** 注销账号（软删 status=3；作品保留，作者名回退「用户已注销」） */
    @PostMapping("/users/{user_id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable("user_id") long userId,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(adminService.cancelAccount(userId,
                body == null ? null : Params.str(body, "reason"), FileController.clientIp(request)));
    }

    // ---- 统计 / 日志 ----

    @GetMapping("/stats/platform")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(adminService.platformStats());
    }

    @GetMapping("/logs")
    public ApiResponse<List<Map<String, Object>>> logs(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(adminService.logs(query));
    }

    /** 用户列表（便于管理员按 QQ 号找人重置密码） */
    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam Map<String, Object> query) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("status", Params.integer(query, "status", null));
        q.put("type", Params.integer(query, "type", null));
        q.put("limit", Params.size(query, 50, 200));
        q.put("offset", Params.offset(Params.page(query), Params.size(query, 50, 200)));
        return ApiResponse.ok(adminService.listUsers(q));
    }
}
