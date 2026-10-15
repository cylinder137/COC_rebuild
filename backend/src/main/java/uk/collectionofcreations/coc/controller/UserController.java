package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.InteractionMapper;
import uk.collectionofcreations.coc.service.ProjectService;
import uk.collectionofcreations.coc.service.UserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人主页 / 我的作品 / 我的收藏。
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;
    private final ProjectService projectService;
    private final InteractionMapper interactionMapper;

    public UserController(UserService userService, ProjectService projectService, InteractionMapper interactionMapper) {
        this.userService = userService;
        this.projectService = projectService;
        this.interactionMapper = interactionMapper;
    }

    /** 我的作品（登录） */
    @GetMapping("/users/me/projects")
    public ApiResponse<Map<String, Object>> myProjects(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(projectService.myProjects(query));
    }

    /** 我的收藏（登录） */
    @GetMapping("/users/me/favorites")
    public ApiResponse<Map<String, Object>> myFavorites(@RequestParam Map<String, Object> query) {
        long uid = AuthContext.requireUserId();
        int page = Params.page(query);
        int size = Params.size(query, 12, 60);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("user_id", uid);
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = interactionMapper.listMyFavorites(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", interactionMapper.countMyFavorites(uid));
        data.put("page", page);
        data.put("size", size);
        return ApiResponse.ok(data);
    }

    /** 关注状态（访客可读） */
    @GetMapping("/users/{user_id}/follow-status")
    public ApiResponse<Map<String, Object>> followStatus(@PathVariable("user_id") long userId) {
        return ApiResponse.ok(userService.followStatus(userId));
    }

    /** 个人主页（访客可读） */
    @GetMapping("/users/{user_id}")
    public ApiResponse<Map<String, Object>> profile(@PathVariable("user_id") long userId) {
        return ApiResponse.ok(userService.profile(userId));
    }
}
