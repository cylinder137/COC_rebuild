package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.service.CommentService;
import uk.collectionofcreations.coc.service.InteractionService;
import uk.collectionofcreations.coc.service.ReportService;

import java.util.Map;

/**
 * 互动：评论、评论点赞、项目点赞/收藏、关注、举报（全部要求登录）。
 */
@RestController
@RequestMapping("/api/v1")
public class InteractionController {

    private final CommentService commentService;
    private final InteractionService interactionService;
    private final ReportService reportService;

    public InteractionController(CommentService commentService, InteractionService interactionService,
                                 ReportService reportService) {
        this.commentService = commentService;
        this.interactionService = interactionService;
        this.reportService = reportService;
    }

    // ---- 评论 ----

    @PostMapping("/comments")
    public ApiResponse<Map<String, Object>> createComment(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(commentService.create(body));
    }

    @PostMapping("/comments/{comment_id}/like")
    public ApiResponse<Map<String, Object>> likeComment(@PathVariable("comment_id") long commentId) {
        return ApiResponse.ok(commentService.toggleLike(commentId));
    }

    // ---- 项目点赞 / 收藏 ----

    @PostMapping("/projects/{project_id}/like")
    public ApiResponse<Map<String, Object>> likeProject(@PathVariable("project_id") long projectId) {
        return ApiResponse.ok(interactionService.toggleProjectLike(projectId));
    }

    @PostMapping("/projects/{project_id}/favorite")
    public ApiResponse<Map<String, Object>> favoriteProject(@PathVariable("project_id") long projectId) {
        return ApiResponse.ok(interactionService.toggleProjectFavorite(projectId));
    }

    // ---- 关注 ----

    @PostMapping("/users/{user_id}/follow")
    public ApiResponse<Map<String, Object>> follow(@PathVariable("user_id") long userId) {
        return ApiResponse.ok(interactionService.toggleFollow(userId));
    }

    // ---- 举报 ----

    @PostMapping("/reports")
    public ApiResponse<Map<String, Object>> report(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(reportService.create(body));
    }

    @GetMapping("/reports/me")
    public ApiResponse<Map<String, Object>> myReports(@org.springframework.web.bind.annotation.RequestParam Map<String, Object> query) {
        return ApiResponse.ok(reportService.mine(query));
    }

    @PostMapping("/reports/me/read")
    public ApiResponse<Map<String, Object>> markReportsRead() {
        return ApiResponse.ok(reportService.markRead());
    }
}
