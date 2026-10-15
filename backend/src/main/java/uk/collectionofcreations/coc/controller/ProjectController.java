package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.service.CommentService;
import uk.collectionofcreations.coc.service.ProjectService;
import uk.collectionofcreations.coc.service.UploadService;

import java.util.List;
import java.util.Map;

/**
 * 项目内容：列表 / 详情 / 浏览量 / 统计折线 / 运行地址 / 评论树 / 投稿。
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService projectService;
    private final CommentService commentService;
    private final UploadService uploadService;

    public ProjectController(ProjectService projectService, CommentService commentService, UploadService uploadService) {
        this.projectService = projectService;
        this.commentService = commentService;
        this.uploadService = uploadService;
    }

    /** 项目列表（访客可读，只看已发布） */
    @GetMapping("/projects")
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(projectService.list(query));
    }

    /** 项目详情 */
    @GetMapping("/projects/{project_id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable("project_id") long projectId) {
        return ApiResponse.ok(projectService.detail(projectId));
    }

    /** 记一次浏览（访客也可，不写用户维度） */
    @PostMapping("/projects/{project_id}/view")
    public ApiResponse<Map<String, Object>> view(@PathVariable("project_id") long projectId) {
        return ApiResponse.ok(projectService.recordView(projectId));
    }

    /** 统计折线：range=2（近 48 小时，走小时表）/ 7 / 15 / 30 */
    @GetMapping("/projects/{project_id}/stats")
    public ApiResponse<List<Map<String, Object>>> stats(@PathVariable("project_id") long projectId,
                                                        @RequestParam(value = "range", defaultValue = "7") int range) {
        return ApiResponse.ok(projectService.stats(projectId, range));
    }

    /** 运行地址（沙箱 iframe 用，返回绝对 URL） */
    @GetMapping("/projects/{project_id}/run")
    public ApiResponse<Map<String, Object>> run(@PathVariable("project_id") long projectId) {
        Map<String, Object> data = projectService.runInfo(projectId);
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        data.put("url", base + "/api/v1/run/" + projectId + "/");
        return ApiResponse.ok(data);
    }

    /** 评论树（楼中楼一层） */
    @GetMapping("/projects/{project_id}/comments")
    public ApiResponse<Map<String, Object>> comments(@PathVariable("project_id") long projectId,
                                                     @RequestParam Map<String, Object> query) {
        int page = Params.page(query);
        int size = Params.size(query, 20, 50);
        return ApiResponse.ok(commentService.tree(projectId, page, size));
    }

    /** 新建草稿（登录） */
    @PostMapping("/projects")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(projectService.createDraft(body));
    }

    /** 修改草稿（登录，仅作者，状态 0/4） */
    @PutMapping("/projects/{project_id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable("project_id") long projectId,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(projectService.updateDraft(projectId, body));
    }

    /** 提交：html 待审 / scratch、structure 免审直发 */
    @PostMapping("/projects/{project_id}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable("project_id") long projectId) {
        return ApiResponse.ok(projectService.submit(projectId));
    }

    /** 手动重新触发打包（scratch，作者或管理员） */
    @PostMapping("/projects/{project_id}/build/retry")
    public ApiResponse<Map<String, Object>> retryBuild(@PathVariable("project_id") long projectId) {
        return ApiResponse.ok(uploadService.retryBuild(projectId));
    }
}
